package com.demo.resortslite;

import com.demo.resortslite.storage.GcsStorageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Service
public class ReportService {

    @Autowired
    private GcsStorageService gcsStorageService;

    // FIXED blocker-2 (cz-java-0057): Replaced hardcoded absolute path with GCS storage
    // Old: private static final String REPORT_BASE_PATH = "/var/legacy/reports/";
    // Now using Google Cloud Storage for container-compatible file access

    // FIXED blocker-3 (cz-java-0057): Replaced Windows-style absolute path with GCS storage
    // Old: private static final String BACKUP_PATH = "C:\\ResortBackups\\nightly\\";
    // Now using Google Cloud Storage for cross-platform compatibility

    // FIXED blocker-11 (cz-java-0061): Externalized port configuration using Spring properties
    // Old: private static final int SERVER_PORT = 8080;
    // Now using @Value annotation to read from application.properties with environment variable support
    @Value("${server.port}")
    private int serverPort;

    public Map<String, Object> generateMonthlyReport(String month, String year) {
        String fileName = "resort_report_" + month + "_" + year + ".csv";

        Map<String, Object> result = new HashMap<>();

        try {
            // FIXED blocker-2 & blocker-3 (cz-java-0057): Using GCS instead of local file system
            // Build CSV content in memory
            StringBuilder csvContent = new StringBuilder();
            csvContent.append("BookingID,GuestName,RoomType,CheckIn,CheckOut,Amount\n");
            csvContent.append("BK-001,John Smith,SUITE,2024-03-01,2024-03-05,1750.00\n");
            csvContent.append("BK-002,Jane Doe,DELUXE,2024-03-03,2024-03-07,960.00\n");

            // Upload to GCS
            String gcsPath = gcsStorageService.uploadFile(fileName, csvContent.toString());

            result.put("status", "generated");
            result.put("path", gcsPath);
            result.put("serverPort", serverPort); // FIXED blocker-11: Using externalized port

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
        // VIOLATION cr-java-0088 [Cloud Compatibility / Mandatory]: Plain HTTP URL
        // hardcoded for report download. Cloud security standards enforce HTTPS.
        return "http://reports.resorts-internal.com:8080/download/" + reportName; // cr-java-0088
    }

    public Map<String, Object> getSystemInfo() { // doc-missing-001
        String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        Map<String, Object> info = new HashMap<>();
        
        // FIXED blocker-2 & blocker-3 (cz-java-0057): Returning GCS bucket info instead of local paths
        info.put("storageType", "Google Cloud Storage");
        info.put("bucketName", "${GCS_BUCKET_NAME}");
        
        // FIXED blocker-11 (cz-java-0061): Using externalized port configuration
        info.put("serverPort", serverPort);
        info.put("generatedAt", timestamp);
        return info;
    }
}
