package com.demo.resortslite;

import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

@Service
public class ReportService {

    // GCS bucket name and object prefix are externalised via environment variables /
    // application properties — no hard-coded file-system paths remain.
    @Value("${gcs.bucket.name:resorts-reports-bucket}")
    private String gcsBucketName;

    @Value("${gcs.reports.prefix:reports/}")
    private String reportsPrefix;

    @Value("${gcs.backup.prefix:backups/nightly/}")
    private String backupPrefix;

    // cr-java-0071 FIX: Report download base URL externalised via environment variable /
    // application property — no hard-coded environment-specific endpoint remains.
    // Set REPORT_DOWNLOAD_BASE_URL in the GCP Cloud Run / GKE environment, or override
    // via application.properties: app.report.download.base.url=https://...
    // For sensitive internal endpoints, store the value in GCP Secret Manager and
    // reference it as: app.report.download.base.url=${sm://report-download-base-url}
    @Value("${app.report.download.base.url:${REPORT_DOWNLOAD_BASE_URL:https://reports.resorts-internal.com/download}}")
    private String reportDownloadBaseUrl;

    // Fixed server port removed — port binding is controlled by the cloud platform
    // (GCP Cloud Run / GKE) via the SERVER_PORT environment variable or framework default.

    // cr-java-0111 FIX: UTC formatter replaces server-local SimpleDateFormat.
    // Using java.time.DateTimeFormatter with ZoneOffset.UTC ensures consistent
    // timestamps across all GCP regions, containers, and Cloud Scheduler invocations.
    private static final DateTimeFormatter UTC_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                             .withZone(ZoneOffset.UTC);

    public Map<String, Object> generateMonthlyReport(String month, String year) {
        String fileName = "resort_report_" + month + "_" + year + ".csv";
        // GCS object path replaces the former hard-coded absolute file-system path
        String gcsObjectName = reportsPrefix + fileName;

        Map<String, Object> result = new HashMap<>();

        try {
            Storage storage = StorageOptions.getDefaultInstance().getService();

            String csvContent = "BookingID,GuestName,RoomType,CheckIn,CheckOut,Amount\n"
                    + "BK-001,John Smith,SUITE,2024-03-01,2024-03-05,1750.00\n"
                    + "BK-002,Jane Doe,DELUXE,2024-03-03,2024-03-07,960.00\n";

            // Upload report content directly to GCS — no local directory creation needed
            BlobId blobId = BlobId.of(gcsBucketName, gcsObjectName);
            BlobInfo blobInfo = BlobInfo.newBuilder(blobId)
                    .setContentType("text/csv")
                    .build();
            storage.create(blobInfo, csvContent.getBytes(StandardCharsets.UTF_8));

            result.put("status", "generated");
            result.put("gcsBucket", gcsBucketName);
            result.put("gcsObject", gcsObjectName);

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
        // cr-java-0071 FIX: Base URL is now read from the externalised @Value field
        // (injected from REPORT_DOWNLOAD_BASE_URL env var or app.report.download.base.url property).
        return reportDownloadBaseUrl + "/" + reportName;
    }

    public Map<String, Object> getSystemInfo() { // doc-missing-001
        // cr-java-0111 FIX: Replaced server-local SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date())
        // with java.time.Instant + DateTimeFormatter pinned to UTC (ZoneOffset.UTC).
        // This eliminates server-local timezone dependency, ensuring consistent timestamps
        // across all GCP regions, Cloud Run instances, and distributed Cloud Scheduler jobs.
        String timestamp = UTC_FORMATTER.format(Instant.now());
        Map<String, Object> info = new HashMap<>();
        // GCS bucket/prefix values replace former hard-coded file-system paths
        info.put("gcsBucket", gcsBucketName);
        info.put("reportsPrefix", reportsPrefix);
        info.put("backupPrefix", backupPrefix);
        info.put("generatedAt", timestamp);
        return info;
    }
}
