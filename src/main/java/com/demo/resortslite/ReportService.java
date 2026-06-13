package com.demo.resortslite;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Service
public class ReportService {

    @Autowired
    private S3StorageService s3StorageService;

    // FIXED blocker-2 (cz-java-0057): Replaced hardcoded absolute path with S3 storage
    // Reports now stored in Amazon S3 instead of local filesystem
    // No longer dependent on OS-specific paths

    // FIXED blocker-3 (cz-java-0057): Removed Windows-style absolute path
    // All file operations now use S3 for cross-platform compatibility

    // FIXED blocker-11 (cz-java-0061): Externalized port configuration
    // Port now read from environment variable via Spring Boot properties
    @Value("${server.port:8080}")
    private int serverPort;

    public Map<String, Object> generateMonthlyReport(String month, String year) {
        String fileName = "resort_report_" + month + "_" + year + ".csv";
        String s3Key = "reports/" + fileName;

        Map<String, Object> result = new HashMap<>();

        try {
            // FIXED blocker-2,3 (cz-java-0057): Writing to S3 instead of local filesystem
            // Create report content in memory
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            OutputStreamWriter writer = new OutputStreamWriter(outputStream);
            
            writer.write("BookingID,GuestName,RoomType,CheckIn,CheckOut,Amount\n");
            writer.write("BK-001,John Smith,SUITE,2024-03-01,2024-03-05,1750.00\n");
            writer.write("BK-002,Jane Doe,DELUXE,2024-03-03,2024-03-07,960.00\n");
            writer.flush();
            writer.close();

            // Upload to S3
            s3StorageService.uploadFile(s3Key, outputStream.toByteArray());

            result.put("status", "generated");
            result.put("path", s3StorageService.getFileUrl(s3Key));
            result.put("serverPort", serverPort); // FIXED blocker-11: Using externalized port

        } catch (IOException e) {
            result.put("status", "error");
            result.put("message", e.getMessage());
        }

        return result;
    }

    public String buildReportDownloadUrl(String reportName) {
        // Using S3 URL instead of hardcoded HTTP URL
        return s3StorageService.getFileUrl("reports/" + reportName);
    }

    public Map<String, Object> getSystemInfo() {
        String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        Map<String, Object> info = new HashMap<>();
        // FIXED blocker-2,3 (cz-java-0057): Using S3 bucket instead of local paths
        info.put("reportStorage", "S3 Bucket");
        info.put("serverPort", serverPort); // FIXED blocker-11: Using externalized port
        info.put("generatedAt", timestamp);
        return info;
    }
}
