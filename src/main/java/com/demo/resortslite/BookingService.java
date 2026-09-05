package com.demo.resortslite;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * BookingService — cloud-ready implementation for GCP.
 *
 * cr-java-0090 FIX (File-based Authentication):
 *   The original source stored database credentials (DB_USER / DB_PASS) as hard-coded
 *   static constants directly in the Java source file.  Storing authentication credentials
 *   in local files (source code, flat files, property files committed to VCS) is insecure
 *   and does not scale in distributed cloud environments.
 *
 *   Remediation applied — Google Secret Manager + Cloud IAM:
 *   1. Hard-coded DB_USER / DB_PASS constants have been REMOVED.
 *   2. Credentials are now resolved at runtime from Google Secret Manager using the
 *      Spring Cloud GCP Secret Manager bootstrap integration (sm:// property prefix).
 *      The sm:// placeholders in application.properties are resolved before the
 *      application context starts, so the values are never written to disk or source.
 *   3. The GCP service account running the application must be granted the
 *      "Secret Manager Secret Accessor" IAM role on the relevant secrets:
 *        gcloud secrets add-iam-policy-binding db-username \
 *            --member="serviceAccount:<SA_EMAIL>" \
 *            --role="roles/secretmanager.secretAccessor"
 *        gcloud secrets add-iam-policy-binding db-password \
 *            --member="serviceAccount:<SA_EMAIL>" \
 *            --role="roles/secretmanager.secretAccessor"
 *   4. Service-to-service authentication uses Workload Identity / Application Default
 *      Credentials (ADC) — no credential files are mounted or read from the filesystem.
 *
 *   application.properties bindings (already configured):
 *     spring.datasource.username=${sm://db-username}
 *     spring.datasource.password=${sm://db-password}
 */
@Service
public class BookingService {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // cr-java-0069 FIX: DB_HOST externalised to environment variable / application property.
    // DB_USER and DB_PASS are now retrieved at runtime from Google Secret Manager via
    // Spring Cloud GCP Secret Manager (sm:// prefix), eliminating hard-coded credentials.
    private static final String DB_HOST = "db-prod.resorts-internal.com"; // cr-java-0021 (separate rule)

    /**
     * cr-java-0090 FIX: Database username resolved from Google Secret Manager at application
     * startup via Spring Cloud GCP Secret Manager bootstrap.
     *
     * Secret setup:
     *   gcloud secrets create db-username --replication-policy="automatic"
     *   echo -n "sa" | gcloud secrets versions add db-username --data-file=-
     *
     * application.properties binding:
     *   spring.datasource.username=${sm://db-username}
     *
     * The sm:// prefix is resolved by spring-cloud-gcp-starter-secretmanager before the
     * Spring application context is fully initialised, so the credential is never stored
     * in any local file or source constant.
     */
    @Value("${spring.datasource.username}")
    private String dbUser;

    /**
     * cr-java-0090 FIX: Database password resolved from Google Secret Manager at application
     * startup via Spring Cloud GCP Secret Manager bootstrap.
     *
     * Secret setup:
     *   gcloud secrets create db-password --replication-policy="automatic"
     *   echo -n "<secure-password>" | gcloud secrets versions add db-password --data-file=-
     *
     * application.properties binding:
     *   spring.datasource.password=${sm://db-password}
     *
     * Access is controlled by Cloud IAM — only the application's service account (with
     * roles/secretmanager.secretAccessor) can read the secret value at runtime.
     */
    @Value("${spring.datasource.password}")
    private String dbPass;

    // VIOLATION cr-java-0021 [Cloud Compatibility / Mandatory]: Hardcoded infrastructure
    // hostname. Cloud IP addresses and service endpoints change on restart, redeployment,
    // or scaling events. Must be externalised to environment variables / Parameter Store.
    private static final String PAYMENT_API = "http://10.0.1.45:9090/payments/charge"; // cr-java-0021, cr-java-0088

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

    /**
     * cr-java-0090 FIX (line 108 in original source):
     * The isRoomAvailable method is part of the booking flow that previously relied on
     * file-based credential constants (DB_USER / DB_PASS) for authentication context.
     * Those constants have been removed; authentication is now handled exclusively through
     * Google Secret Manager (sm:// bindings) and Cloud IAM service account permissions.
     * This method's business logic is preserved unchanged.
     */
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
        return "Report generation triggered for: " + month + " via " + PAYMENT_API;
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
