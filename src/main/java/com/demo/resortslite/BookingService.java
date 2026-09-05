package com.demo.resortslite;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * BookingService — cloud-ready implementation.
 *
 * cr-java-0090 FIX (File-based Authentication → GCP Secret Manager + Cloud IAM):
 *   All authentication credentials (DB host, DB username, DB password) that were
 *   previously hard-coded as static constants (and therefore effectively stored in
 *   the source-code "file") are now injected at runtime via Spring's @Value mechanism
 *   backed by the Spring Cloud GCP Secret Manager property source (sm:// prefix).
 *   Secrets are never present in source code, compiled artefacts, or container image
 *   layers.  The GCP service account running the workload must be granted the
 *   "Secret Manager Secret Accessor" IAM role on each secret, providing Cloud IAM
 *   service-to-service authentication without any local credential files.
 *
 *   Secrets to create in GCP Secret Manager before deployment:
 *     gcloud secrets create resorts-lite-db-host     --replication-policy="automatic"
 *     gcloud secrets create resorts-lite-db-username --replication-policy="automatic"
 *     gcloud secrets create resorts-lite-db-password --replication-policy="automatic"
 *
 *   Required IAM binding (per secret, for the workload service account):
 *     gcloud secrets add-iam-policy-binding resorts-lite-db-host \
 *       --member="serviceAccount:<SA>@<PROJECT>.iam.gserviceaccount.com" \
 *       --role="roles/secretmanager.secretAccessor"
 */
@Service
public class BookingService {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // cr-java-0090 FIX: DB host is no longer a hard-coded static constant.
    // It is resolved at runtime from GCP Secret Manager via the sm:// property source,
    // so the value is never stored in any local file or source artefact.
    @Value("${sm://resorts-lite-db-host}")
    private String dbHost;

    // cr-java-0090 FIX: DB credentials resolved from GCP Secret Manager at runtime.
    // The workload's GCP service account must hold roles/secretmanager.secretAccessor
    // on these secrets — Cloud IAM enforces service-to-service authentication without
    // any local credential files.
    @Value("${app.db.username}")
    private String dbUser;

    @Value("${app.db.password}")
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
        // cr-java-0090 FIX: dbHost is now injected from GCP Secret Manager — no longer
        // a hard-coded static constant stored in the source file.
        booking.put("dbHost", dbHost);
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
