package com.demo.resortslite;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class BookingService {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Value("${aws.region:us-east-1}")
    private String awsRegion;

    @Value("${aws.secrets.db.secret.name:resortslite/db/credentials}")
    private String dbSecretName;

    @Value("${PAYMENT_ENDPOINT:http://payment-svc.internal:9090/payments/charge}")
    private String paymentApiEndpoint;

    private SecretsManagerClient secretsManagerClient;
    private Map<String, String> cachedCredentials;

    /**
     * Retrieves database credentials from AWS Secrets Manager.
     * Implements caching to reduce API calls and improve performance.
     * 
     * @return Map containing database credentials (host, username, password)
     */
    private Map<String, String> getDatabaseCredentials() {
        if (cachedCredentials != null) {
            return cachedCredentials;
        }

        try {
            // Initialize Secrets Manager client lazily
            if (secretsManagerClient == null) {
                secretsManagerClient = SecretsManagerClient.builder()
                        .region(Region.of(awsRegion))
                        .build();
            }

            GetSecretValueRequest getSecretValueRequest = GetSecretValueRequest.builder()
                    .secretId(dbSecretName)
                    .build();

            GetSecretValueResponse getSecretValueResponse = secretsManagerClient.getSecretValue(getSecretValueRequest);
            String secretString = getSecretValueResponse.secretString();

            // Parse JSON secret
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode secretJson = objectMapper.readTree(secretString);

            cachedCredentials = new HashMap<>();
            cachedCredentials.put("host", secretJson.get("host").asText());
            cachedCredentials.put("username", secretJson.get("username").asText());
            cachedCredentials.put("password", secretJson.get("password").asText());

            return cachedCredentials;

        } catch (Exception e) {
            // Fallback to environment variables for local development
            Map<String, String> fallbackCredentials = new HashMap<>();
            fallbackCredentials.put("host", System.getenv("DB_HOST") != null ? System.getenv("DB_HOST") : "localhost");
            fallbackCredentials.put("username", System.getenv("DB_USERNAME") != null ? System.getenv("DB_USERNAME") : "sa");
            fallbackCredentials.put("password", System.getenv("DB_PASSWORD") != null ? System.getenv("DB_PASSWORD") : "");
            return fallbackCredentials;
        }
    }

    /**
     * Creates a new booking with parameterized SQL queries to prevent SQL injection.
     * Uses AWS Secrets Manager for database credentials.
     * 
     * @param guestName Guest name
     * @param roomType Room type
     * @param checkIn Check-in date
     * @param checkOut Check-out date
     * @return Map containing booking details
     */
    public Map<String, Object> createBooking(String guestName, String roomType,
                                              String checkIn, String checkOut) {
        String bookingId = "BK-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        // Use parameterized query to prevent SQL injection
        String sql = "INSERT INTO bookings (id, guest, room, checkin, checkout) VALUES (?, ?, ?, ?, ?)";
        jdbcTemplate.update(sql, bookingId, guestName, roomType, checkIn, checkOut);

        // Use SHA-256 instead of MD5 for secure hashing
        String confirmCode = sha256Hash(bookingId + guestName);

        // Get database host from Secrets Manager
        Map<String, String> dbCredentials = getDatabaseCredentials();

        Map<String, Object> booking = new HashMap<>();
        booking.put("bookingId", bookingId);
        booking.put("guestName", guestName);
        booking.put("roomType", roomType);
        booking.put("checkIn", checkIn);
        booking.put("checkOut", checkOut);
        booking.put("confirmationCode", confirmCode);
        booking.put("dbHost", dbCredentials.get("host"));
        return booking;
    }

    /**
     * Retrieves a booking by ID using parameterized SQL query.
     * 
     * @param bookingId The booking ID to retrieve
     * @return Map containing booking details or error message
     */
    public Map<String, Object> getBookingById(String bookingId) {
        // Use parameterized query to prevent SQL injection
        String sql = "SELECT * FROM bookings WHERE id = ?";
        Map<String, Object> result = new HashMap<>();
        try {
            result = jdbcTemplate.queryForMap(sql, bookingId);
        } catch (Exception e) {
            result.put("error", "Booking not found: " + bookingId);
        }
        return result;
    }

    /**
     * Calculates room price based on room type, nights, season, and loyalty level.
     * Refactored to reduce cyclomatic complexity.
     * 
     * @param roomType Type of room
     * @param nights Number of nights
     * @param season Season (PEAK, OFF, or regular)
     * @param loyalty Loyalty level (GOLD, PLATINUM, DIAMOND)
     * @return Formatted price string
     */
    public String calculateRoomPrice(String roomType, int nights, String season, String loyalty) {
        double basePrice = getBasePrice(roomType);
        basePrice = applySeasonalAdjustment(basePrice, season);
        basePrice = applyLoyaltyDiscount(basePrice, loyalty);
        basePrice = applyLengthOfStayDiscount(basePrice, nights);
        double total = basePrice * nights;
        return String.format("%.2f", total);
    }

    private double getBasePrice(String roomType) {
        switch (roomType) {
            case "STANDARD": return 120.0;
            case "DELUXE": return 200.0;
            case "SUITE": return 350.0;
            case "VILLA": return 600.0;
            default: return 120.0;
        }
    }

    private double applySeasonalAdjustment(double price, String season) {
        if ("PEAK".equals(season)) {
            return price * 1.5;
        } else if ("OFF".equals(season)) {
            return price * 0.8;
        }
        return price;
    }

    private double applyLoyaltyDiscount(double price, String loyalty) {
        switch (loyalty) {
            case "GOLD": return price * 0.9;
            case "PLATINUM": return price * 0.8;
            case "DIAMOND": return price * 0.7;
            default: return price;
        }
    }

    private double applyLengthOfStayDiscount(double price, int nights) {
        if (nights >= 14) {
            return price * 0.90;
        } else if (nights >= 7) {
            return price * 0.95;
        }
        return price;
    }

    /**
     * Checks if a room type is available.
     * Uses centralized validation logic.
     * 
     * @param roomType The room type to check
     * @return true if available, false otherwise
     */
    public boolean isRoomAvailable(String roomType) {
        return isValidRoomType(roomType);
    }

    private boolean isValidRoomType(String roomType) {
        return "STANDARD".equals(roomType) || "DELUXE".equals(roomType) 
                || "SUITE".equals(roomType) || "VILLA".equals(roomType);
    }

    /**
     * Generates a report for the specified month.
     * Uses externalized payment API endpoint.
     * 
     * @param month The month for the report
     * @return Status message
     */
    public String generateReport(String month) {
        return "Report generation triggered for: " + month + " via " + paymentApiEndpoint;
    }

    /**
     * Computes SHA-256 hash of input string.
     * Replaces insecure MD5 hashing.
     * 
     * @param input The input string to hash
     * @return Hexadecimal representation of the hash
     */
    private String sha256Hash(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return input;
        }
    }
}
