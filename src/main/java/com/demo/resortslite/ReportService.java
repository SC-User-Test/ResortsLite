package com.demo.resortslite;

import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

@Service
public class ReportService {

    // GCS bucket name and report prefix are externalised to environment variables /
    // application properties — no hard-coded file-system paths remain.
    // Replaces: private static final String REPORT_BASE_PATH = "/var/legacy/reports/";  (cr-java-0063)
    // Replaces: private static final String BACKUP_PATH = "C:\\ResortBackups\\nightly\\"; (cr-java-0063)
    @Value("${gcs.bucket.name:resorts-lite-reports}")
    private String gcsBucketName;

    // cr-java-0071: Report download base URL externalised to environment variable /
    // application property. Sensitive endpoints can be stored in GCP Secret Manager
    // and injected via the sm:// property source; non-sensitive ones use plain env vars.
    // Replaces: "http://reports.resorts-internal.com:8080/download/" (hard-coded URL)
    @Value("${app.report.download.base-url:https://reports.resorts-internal.com/download}")
    private String reportDownloadBaseUrl;

    @Value("${gcs.report.prefix:reports/}")
    private String gcsReportPrefix;

    @Value("${gcs.backup.prefix:backups/nightly/}")
    private String gcsBackupPrefix;

    // cr-java-0077 FIX: Hard-coded port replaced with environment-variable-backed property.
    // GCP Cloud Run injects the PORT environment variable at runtime; other orchestrators
    // (GKE, Cloud Run Jobs) follow the same convention. The default (8080) is used only
    // for local development when PORT is not set.
    // Replaces: private static final int SERVER_PORT = 8080; (cr-java-0077)
    @Value("${server.port:${PORT:8080}}")
    private int serverPort;

    /**
     * Generates a monthly resort report and uploads it to Google Cloud Storage.
     * <p>
     * Replaces the previous local file-system write operations (cr-java-0063):
     *   - new File(REPORT_BASE_PATH)          (line 37 in original source)
     *   - reportDir.mkdirs()                  (line 39 in original source)
     *   - new FileWriter(fullPath)            (line 42 in original source — primary violation)
     *   - writer.write(...)                   (lines 43-45 in original source)
     * All data is now written directly to Google Cloud Storage using the GCS Java SDK,
     * ensuring durability across container restarts and horizontal scaling events.
     * </p>
     *
     * @param month two-digit month string (e.g. "03")
     * @param year  four-digit year string  (e.g. "2024")
     * @return result map containing status and GCS object URI
     */
    public Map<String, Object> generateMonthlyReport(String month, String year) {
        String fileName = "resort_report_" + month + "_" + year + ".csv";
        // GCS object name replaces the former absolute local path (cr-java-0063)
        String gcsObjectName = gcsReportPrefix + fileName;

        Map<String, Object> result = new HashMap<>();

        try {
            // Build CSV content in memory — no local File / FileWriter needed (cr-java-0063 fix)
            StringBuilder csvContent = new StringBuilder();
            csvContent.append("BookingID,GuestName,RoomType,CheckIn,CheckOut,Amount\n");
            csvContent.append("BK-001,John Smith,SUITE,2024-03-01,2024-03-05,1750.00\n");
            csvContent.append("BK-002,Jane Doe,DELUXE,2024-03-03,2024-03-07,960.00\n");

            // Upload to GCS using the GCS Java SDK
            // Replaces: new File(REPORT_BASE_PATH)  [line 37 in original source] (cr-java-0063)
            // Replaces: reportDir.mkdirs()           [line 39 in original source] (cr-java-0063)
            // Replaces: new FileWriter(fullPath)     [line 42 in original source] (cr-java-0063)
            Storage storage = StorageOptions.getDefaultInstance().getService();
            BlobId blobId = BlobId.of(gcsBucketName, gcsObjectName);
            BlobInfo blobInfo = BlobInfo.newBuilder(blobId)
                    .setContentType("text/csv")
                    .build();
            storage.create(blobInfo, csvContent.toString().getBytes(StandardCharsets.UTF_8));

            String gcsUri = "gs://" + gcsBucketName + "/" + gcsObjectName;
            result.put("status", "generated");
            result.put("path", gcsUri);
            result.put("serverPort", serverPort);

        } catch (Exception e) {
            result.put("status", "error");
            result.put("message", e.getMessage());
        }

        return result;
    }

    // VIOLATION [Code Sustainability / Medium]: No JavaDoc or method documentation.
    // Missing documentation is flagged across all public methods in the codebase.
    // This increases onboarding time and transformation risk for automated tools.
    public String buildReportDownloadUrl(String reportName) { // doc-missing-001
        // cr-java-0071 FIX: Hard-coded environment URL replaced with externalised configuration.
        // The base URL is now injected from 'app.report.download.base-url', which is set via
        // the APP_REPORT_DOWNLOAD_BASE_URL environment variable or GCP Secret Manager (sm:// prefix).
        return reportDownloadBaseUrl + "/" + reportName;
    }

    /**
     * Returns system information including GCS paths (replaces local file-system paths).
     * <p>
     * Addresses cr-java-0063: REPORT_BASE_PATH and BACKUP_PATH were hard-coded absolute
     * file-system paths; they are now GCS URIs derived from externalised configuration.
     * </p>
     * <p>
     * cr-java-0111 FIX: Timestamp is now generated in UTC using {@link ZonedDateTime} with
     * {@link ZoneOffset#UTC}, replacing the server-local {@code SimpleDateFormat} / {@code new Date()}
     * pattern that caused timezone inconsistencies across distributed cloud instances.
     * </p>
     *
     * @return map of system metadata
     */
    public Map<String, Object> getSystemInfo() { // doc-missing-001
        // cr-java-0111 FIX: Standardise on UTC — eliminates server-local timezone dependency.
        // ZonedDateTime.now(ZoneOffset.UTC) is safe for distributed cloud environments where
        // each container/instance may run in a different OS timezone.
        String timestamp = ZonedDateTime.now(ZoneOffset.UTC)
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        Map<String, Object> info = new HashMap<>();
        // GCS URIs replace the former hard-coded local paths (cr-java-0063)
        info.put("reportPath", "gs://" + gcsBucketName + "/" + gcsReportPrefix);
        info.put("backupPath", "gs://" + gcsBucketName + "/" + gcsBackupPrefix);
        info.put("serverPort", serverPort);
        info.put("generatedAt", timestamp);
        return info;
    }
}
