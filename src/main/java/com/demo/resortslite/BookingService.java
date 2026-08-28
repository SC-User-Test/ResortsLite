package com.demo.resortslite;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class BookingService {

    /**
     * cz-java-0062 / cz-java-0082 — Hardcoded IP Address Removed via ECS Service Connect
     * Rule: cz-java-0062 (Hardcoded IP Addresses) | cz-java-0082 (ECS Service Connect)
     *
     * PROBLEM  : The original code contained a hardcoded inter-service URL
     *            "http://10.0.1.45:9090/payments/charge" (line 102 original) stored as a
     *            static final constant. Hardcoded IP addresses and ports create tight
     *            coupling between services, preventing independent deployment, scaling,
     *            and service discovery in a containerised microservices architecture on
     *            ECS Fargate. IP addresses change on every task restart or redeployment.
     *
     * FIX      : The URL is now resolved at runtime from the PAYMENT_SERVICE_URL environment
     *            variable, injected via the ECS Task Definition. ECS Service Connect provides
     *            automatic service discovery, mTLS, and traffic observability between Fargate
     *            services without hardcoded IP addresses or ports.
     *
     * ECS Service Connect configuration required (ECS Service definition):
     *   1. Enable Service Connect on the ECS Service for the "resortslite" namespace.
     *   2. Define a Service Connect client alias for the payment service:
     *        { "port": 9090, "dnsName": "payment-service" }
     *   3. Set the environment variable in the ECS Task Definition:
     *        PAYMENT_SERVICE_URL=http://payment-service:9090/payments/charge
     *      (ECS Service Connect resolves "payment-service" via its internal DNS.)
     *
     * Occurrence fixed (cz-java-0082):
     * Occurrences fixed (cz-java-0062, cz-java-0082):
     *   - Line 28 (source): private static final String PAYMENT_API = "http://10.0.1.45:9090/payments/charge"
     *                        → replaced with @Value("${PAYMENT_SERVICE_URL}") instance field
     *   - Hardcoded IP 10.0.1.45 removed; resolved via ECS Service Connect DNS (cz-java-0062)
     */

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // VIOLATION [Security Health / Critical]: Hardcoded database credentials in source code.
    // If this repo is pushed to GitHub (even private), credentials are permanently exposed
    // in git history. AWS Secrets Manager or Parameter Store must be used instead.
    private static final String DB_HOST = "db-prod.resorts-internal.com"; // cr-java-0021
    private static final String DB_USER = "admin";                         // sec-cred-001
    private static final String DB_PASS = "Resort$Pass#2019!";             // sec-cred-001

    /**
     * cz-java-0062 FIX: Hardcoded IP address (10.0.1.45) replaced with environment variable.
     * cz-java-0082 FIX: Payment service URL resolved via ECS Service Connect.
     * Set PAYMENT_SERVICE_URL in the ECS Task Definition environment variables.
     * ECS Service Connect resolves the logical service name to the correct Fargate
     * task endpoint automatically, enabling mTLS and traffic observability.
     * Default value supports local development without ECS Service Connect.
     */
    @Value("${PAYMENT_SERVICE_URL:http://payment-service:9090/payments/charge}")
    private String paymentServiceUrl;

    public Map<String, Object> createBooking(String guestName, String roomType,
                                              String checkIn, String checkOut) {
        String bookingId = "BK-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        // VIOLATION [Security Health / Critical]: SQL query built by string concatenation.
        // An attacker can pass guestName = "'; DROP TABLE bookings; --" to destroy data.
        // Use parameterised queries (JdbcTemplate with '?') to prevent SQL injection.
        String sql = "INSERT INTO bookings (id, guest, room, checkin, checkout) VALUES ('" // sql-inject-001
                + bookingId + "', '" + guestName + "', '" + roomType               // sql-inject-001
                + "', '" + checkIn + "', '" + checkOut + "')";                     // sql-inject-001
        jdbcTemplate.execute(sql);

        // VIOLATION [Security Health / High]: MD5 is a broken hash algorithm (RFC 6151).
        // Do not use MD5 for any security-related hashing. Use SHA-256 or bcrypt.
        String confirmCode = md5Hash(bookingId + guestName); // sec-weak-hash-001

        Map<String, Object> booking = new HashMap<>();
        booking.put("bookingId", bookingId);
        booking.put("guestName", guestName);
        booking.put("roomType", roomType);
        booking.put("checkIn", checkIn);
        booking.put("checkOut", checkOut);
        booking.put("confirmationCode", confirmCode);
        booking.put("dbHost", DB_HOST);
        return booking;
    }

    public Map<String, Object> getBookingById(String bookingId) {
        // VIOLATION [Security Health / Critical]: SQL injection via string concatenation.
        // bookingId is user-supplied input appended directly into the SQL string.
        String sql = "SELECT * FROM bookings WHERE id = '" + bookingId + "'"; // sql-inject-001
        Map<String, Object> result = new HashMap<>();
        try {
            result = jdbcTemplate.queryForMap(sql);
        } catch (Exception e) {
            result.put("error", "Booking not found: " + bookingId);
        }
        return result;
    }

    // VIOLATION [Code Sustainability / High]: High cyclomatic complexity.
    // This method has 9+ decision branches. Automated transformation tools flag methods
    // above complexity threshold as high maintenance risk and transformation blockers.
    public String calculateRoomPrice(String roomType, int nights, String season, String loyalty) {
        double basePrice = 0;
        if (roomType.equals("STANDARD")) { basePrice = 120.0; }
        else if (roomType.equals("DELUXE")) { basePrice = 200.0; }
        else if (roomType.equals("SUITE")) { basePrice = 350.0; }
        else if (roomType.equals("VILLA")) { basePrice = 600.0; }
        else { basePrice = 120.0; }
        if (season.equals("PEAK")) { basePrice = basePrice * 1.5; }
        else if (season.equals("OFF")) { basePrice = basePrice * 0.8; }
        if (loyalty.equals("GOLD")) { basePrice = basePrice * 0.9; }
        else if (loyalty.equals("PLATINUM")) { basePrice = basePrice * 0.8; }
        else if (loyalty.equals("DIAMOND")) { basePrice = basePrice * 0.7; }
        if (nights >= 7) { basePrice = basePrice * 0.95; }
        else if (nights >= 14) { basePrice = basePrice * 0.90; }
        double total = basePrice * nights;
        return String.format("%.2f", total);
    }

    public boolean isRoomAvailable(String roomType) {
        // VIOLATION [Code Sustainability / Medium]: Duplicated validation logic.
        // Same room type validation is repeated here and in calculateRoomPrice.
        // Should be extracted to a shared RoomType enum or validator.
        if (!roomType.equals("STANDARD") && !roomType.equals("DELUXE") // dup-logic-001
                && !roomType.equals("SUITE") && !roomType.equals("VILLA")) { // dup-logic-001
            return false;
        }
        return true;
    }

    public String generateReport(String month) {
        return "Report generation triggered for: " + month + " via " + paymentServiceUrl;
    }

    private String md5Hash(String input) { // sec-weak-hash-001
        try {
            MessageDigest md = MessageDigest.getInstance("MD5"); // sec-weak-hash-001
            byte[] hash = md.digest(input.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) { sb.append(String.format("%02x", b)); }
            return sb.toString();
        } catch (Exception e) {
            return input;
        }
    }
}
