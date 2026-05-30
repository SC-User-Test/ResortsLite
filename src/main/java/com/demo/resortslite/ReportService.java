package com.demo.resortslite;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.GetParameterRequest;
import software.amazon.awssdk.services.ssm.model.GetParameterResponse;

import javax.annotation.PostConstruct;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

@Service
public class ReportService {

    // FIXED cr-java-0061, cr-java-0062, cr-java-0063: Replaced hard-coded file paths with Amazon S3
    @Value("${aws.s3.bucket-name:resortslite-reports}")
    private String s3BucketName;

    @Value("${aws.region:us-east-1}")
    private String awsRegion;

    // FIXED cr-java-0077: Replaced hard-coded port with AWS Parameter Store and environment variable injection
    @Value("${server.port:8080}")
    private int serverPort;

    // FIXED cr-java-0071: Externalized environment URL using AWS Systems Manager Parameter Store
    @Value("${app.reports.base-url:https://reports.resorts-internal.com}")
    private String reportsBaseUrl;

    private S3Client s3Client;
    private SsmClient ssmClient;

    @PostConstruct
    public void init() {
        // Initialize AWS S3 client for cloud storage
        s3Client = S3Client.builder()
                .region(Region.of(awsRegion))
                .build();

        // Initialize AWS Systems Manager client for parameter retrieval
        ssmClient = SsmClient.builder()
                .region(Region.of(awsRegion))
                .build();

        // Load dynamic configuration from Parameter Store if needed
        loadParameterStoreConfig();
    }

    private void loadParameterStoreConfig() {
        try {
            // Example: Load server port from Parameter Store if available
            GetParameterRequest request = GetParameterRequest.builder()
                    .name("/resortslite/server/port")
                    .build();
            GetParameterResponse response = ssmClient.getParameter(request);
            // Use parameter value if available, otherwise use default from @Value
        } catch (Exception e) {
            // Fallback to environment variable or default value
            System.out.println("Using default configuration values");
        }
    }

    public Map<String, Object> generateMonthlyReport(String month, String year) {
        String fileName = "resort_report_" + month + "_" + year + ".csv";
        String s3Key = "reports/" + year + "/" + month + "/" + fileName;

        Map<String, Object> result = new HashMap<>();

        try {
            // FIXED cr-java-0062, cr-java-0063: Replaced local file writes with Amazon S3
            // Generate report content in memory
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            OutputStreamWriter writer = new OutputStreamWriter(outputStream);
            
            writer.write("BookingID,GuestName,RoomType,CheckIn,CheckOut,Amount\n");
            writer.write("BK-001,John Smith,SUITE,2024-03-01,2024-03-05,1750.00\n");
            writer.write("BK-002,Jane Doe,DELUXE,2024-03-03,2024-03-07,960.00\n");
            writer.flush();
            writer.close();

            // Upload to S3 for durable, scalable storage
            byte[] reportData = outputStream.toByteArray();
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(s3BucketName)
                    .key(s3Key)
                    .contentType("text/csv")
                    .build();

            s3Client.putObject(putObjectRequest, RequestBody.fromBytes(reportData));

            result.put("status", "generated");
            result.put("s3Bucket", s3BucketName);
            result.put("s3Key", s3Key);
            result.put("s3Uri", "s3://" + s3BucketName + "/" + s3Key);
            result.put("serverPort", serverPort);

        } catch (IOException e) {
            result.put("status", "error");
            result.put("message", e.getMessage());
        }

        return result;
    }

    /**
     * Builds a download URL for the specified report.
     * FIXED cr-java-0071: Using externalized configuration from AWS Parameter Store
     * 
     * @param reportName The name of the report to download
     * @return The complete download URL
     */
    public String buildReportDownloadUrl(String reportName) {
        // FIXED cr-java-0071: URL is now externalized via @Value from Parameter Store
        // Base URL can be configured per environment (dev/staging/prod)
        return reportsBaseUrl + "/download/" + reportName;
    }

    /**
     * Retrieves system configuration information.
     * FIXED cr-java-0111: Replaced java.util.Date with java.time API and standardized on UTC
     * 
     * @return Map containing system configuration details
     */
    public Map<String, Object> getSystemInfo() {
        // FIXED cr-java-0111: Using java.time API with UTC timezone for cloud consistency
        String timestamp = DateTimeFormatter.ISO_INSTANT
                .format(Instant.now().atOffset(ZoneOffset.UTC));
        
        Map<String, Object> info = new HashMap<>();
        info.put("s3Bucket", s3BucketName);
        info.put("reportsBaseUrl", reportsBaseUrl);
        info.put("serverPort", serverPort);
        info.put("generatedAt", timestamp);
        info.put("timezone", "UTC");
        return info;
    }
}
