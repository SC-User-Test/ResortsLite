package com.demo.resortslite;

import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.security.keyvault.secrets.SecretClient;
import com.azure.security.keyvault.secrets.SecretClientBuilder;
import com.azure.security.keyvault.secrets.models.KeyVaultSecret;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Cloud-ready booking service with Azure Key Vault integration for secrets management
 * and Azure Active Directory for authentication.
 * 
 * Fixes applied:
 * - cr-java-0069: Migrated hard-coded credentials to Azure Key Vault
 * - cr-java-0090: Migrated file-based authentication to Azure Active Directory with Spring Security
 */
@Service
public class BookingService {

    @Autowired
    private JdbcTemplate jdbcTemplate;
    
    // Azure Key Vault configuration
    @Value("${azure.keyvault.uri:#{environment.AZURE_KEYVAULT_URI}}")
    private String keyVaultUri;
    
    private SecretClient secretClient;

    /**
     * Retrieves database credentials from Azure Key Vault.
     * Replaces hard-coded credentials with secure, centralized secret management.
     * 
     * @param secretName Name of the secret in Key Vault
     * @return Secret value
     */
    private String getSecretFromKeyVault(String secretName) {
        try {
            if (secretClient == null) {
                secretClient = new SecretClientBuilder()
                    .vaultUrl(keyVaultUri)
                    .credential(new DefaultAzureCredentialBuilder().build())
                    .buildClient();
            }
            
            KeyVaultSecret secret = secretClient.getSecret(secretName);
            return secret.getValue();
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to retrieve secret from Azure Key Vault: " + secretName, e);
        }
    }

    /**
     * Creates a new booking with secure credential management.
     * Database credentials are retrieved from Azure Key Vault at runtime.
     * 
     * @param guestName Guest name
     * @param roomType Room type
     * @param checkIn Check-in date
     * @param checkOut Check-out date
     * @return Booking details map
     */
    public Map<String, Object> createBooking(String guestName, String roomType,
                                              String checkIn, String checkOut) {
        String bookingId = "BK-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        // Use parameterized query to prevent SQL injection
        String sql = "INSERT INTO bookings (id, guest, room, checkin, checkout) VALUES (?, ?, ?, ?, ?)";
        jdbcTemplate.update(sql, bookingId, guestName, roomType, checkIn, checkOut);

        // Use SHA-256 instead of MD5 for secure hashing
        String confirmCode = sha256Hash(bookingId + guestName);

        // Retrieve database host from Azure Key Vault
        String dbHost = getSecretFromKeyVault("db-host");

        Map<String, Object> booking = new HashMap<>();
        booking.put("bookingId", bookingId);
        booking.put("guestName", guestName);
        booking.put("roomType", roomType);
        booking.put("checkIn", checkIn);
        booking.put("checkOut", checkOut);
        booking.put("confirmationCode", confirmCode);
        booking.put("dbHost", dbHost);
        return booking;
    }

    /**
     * Retrieves booking by ID using parameterized query.
     * Prevents SQL injection vulnerabilities.
     * 
     * @param bookingId Booking ID
     * @return Booking details map
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
     * Calculates room price based on multiple factors.
     * 
     * @param roomType Type of room
     * @param nights Number of nights
     * @param season Season (PEAK, OFF, etc.)
     * @param loyalty Loyalty tier
     * @return Formatted price string
     */
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

    /**
     * Checks if a room type is available.
     * 
     * @param roomType Room type to check
     * @return true if available, false otherwise
     */
    public boolean isRoomAvailable(String roomType) {
        if (!roomType.equals("STANDARD") && !roomType.equals("DELUXE")
                && !roomType.equals("SUITE") && !roomType.equals("VILLA")) {
            return false;
        }
        return true;
    }

    /**
     * Generates report with secure configuration.
     * Payment API endpoint is retrieved from Azure Key Vault.
     * 
     * @param month Report month
     * @return Status message
     */
    public String generateReport(String month) {
        String paymentApi = getSecretFromKeyVault("payment-api-endpoint");
        return "Report generation triggered for: " + month + " via " + paymentApi;
    }

    /**
     * Authenticates user using Azure Active Directory.
     * Replaces file-based authentication with cloud-native identity management.
     * 
     * @return Authentication status
     */
    public Map<String, Object> authenticateUser() {
        Map<String, Object> authResult = new HashMap<>();
        
        try {
            // Get authentication from Spring Security context (integrated with Azure AD)
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            
            if (authentication != null && authentication.isAuthenticated()) {
                authResult.put("authenticated", true);
                authResult.put("username", authentication.getName());
                authResult.put("authorities", authentication.getAuthorities());
                authResult.put("authProvider", "Azure Active Directory");
            } else {
                authResult.put("authenticated", false);
                authResult.put("message", "User not authenticated");
            }
            
        } catch (Exception e) {
            authResult.put("authenticated", false);
            authResult.put("error", "Authentication failed: " + e.getMessage());
        }
        
        return authResult;
    }

    /**
     * Secure hash function using SHA-256.
     * Replaces insecure MD5 hashing.
     * 
     * @param input Input string to hash
     * @return SHA-256 hash as hex string
     */
    private String sha256Hash(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes());
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
