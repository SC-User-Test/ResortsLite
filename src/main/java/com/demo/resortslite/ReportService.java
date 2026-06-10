package com.demo.resortslite;

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
    private S3StorageService s3StorageService;

    @Value("${server.port}")
    private int serverPort;

    @Value("${app.payment.endpoint}")
    private String paymentEndpoint;

    // FIXED blocker-2 (cz-java-0057): Replaced absolute file path with S3 storage
    // Original: private static final String REPORT_BASE_PATH = "/var/legacy/reports/";

    // FIXED blocker-3 (cz-java-0057): Replaced Windows absolute path with S3 storage
    // Original: private static final String BACKUP_PATH = "C:\\ResortBackups\\nightly\\";

    // FIXED blocker-11 (cz-java-0061): Externalized port configuration to environment variable
    // Original: private static final int SERVER_PORT = 8080;
    // Now using @Value("${server.port}") for dynamic port binding

    public Map<String, Object> generateMonthlyReport(String month, String year) {
        String fileName = "resort_report_" + month + "_" + year + ".csv";
        
        // FIXED blocker-2 & blocker-3 (cz-java-0057): Using S3 for file storage instead of local filesystem
        String s3Key = s3StorageService.generateS3Key(fileName);

        Map<String, Object> result = new HashMap<>();

        try {
            // Generate CSV content
            StringBuilder csvContent = new StringBuilder();
            csvContent.append("BookingID,GuestName,RoomType,CheckIn,CheckOut,Amount\n");
            csvContent.append("BK-001,John Smith,SUITE,2024-03-01,2024-03-05,1750.00\n");
            csvContent.append("BK-002,Jane Doe,DELUXE,2024-03-03,2024-03-07,960.00\n");

            // Upload to S3 instead of writing to local filesystem
            String s3Uri = s3StorageService.uploadTextFile(s3Key, csvContent.toString());

            result.put("status", "generated");
            result.put("path", s3Uri);
            result.put("s3Bucket", s3StorageService.getBucketName());
            result.put("s3Key", s3Key);
            // FIXED blocker-11 (cz-java-0061): Using externalized port configuration
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
        // VIOLATION cr-java-0088 [Cloud Compatibility / Mandatory]: Plain HTTP URL
        // hardcoded for report download. Cloud security standards enforce HTTPS.
        // FIXED blocker-11 (cz-java-0061): Using externalized port configuration
        return "http://reports.resorts-internal.com:" + serverPort + "/download/" + reportName; // cr-java-0088
    }

    public Map<String, Object> getSystemInfo() { // doc-missing-001
        String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        Map<String, Object> info = new HashMap<>();
        // FIXED blocker-2 & blocker-3 (cz-java-0057): Replaced file paths with S3 configuration
        info.put("storageType", "S3");
        info.put("s3Bucket", s3StorageService.getBucketName());
        // FIXED blocker-11 (cz-java-0061): Using externalized port configuration
        info.put("serverPort", serverPort);
        info.put("generatedAt", timestamp);
        return info;
    }
}
