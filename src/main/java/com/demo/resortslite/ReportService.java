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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

@Service
public class ReportService {

    @Value("${aws.region:us-east-1}")
    private String awsRegion;

    @Value("${aws.s3.bucket.name:resorts-lite-reports}")
    private String s3BucketName;

    @Value("${SERVER_PORT:8080}")
    private String serverPort;

    private S3Client s3Client;
    private SsmClient ssmClient;

    /**
     * Generates a monthly report and stores it in Amazon S3.
     * Replaces local file system operations with cloud-native S3 storage.
     * 
     * @param month The month for the report
     * @param year The year for the report
     * @return Map containing report generation status and S3 location
     */
    public Map<String, Object> generateMonthlyReport(String month, String year) {
        String fileName = "resort_report_" + month + "_" + year + ".csv";
        
        Map<String, Object> result = new HashMap<>();

        try {
            // Initialize S3 client lazily
            if (s3Client == null) {
                s3Client = S3Client.builder()
                        .region(Region.of(awsRegion))
                        .build();
            }

            // Generate report content in memory instead of writing to local file system
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            OutputStreamWriter writer = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8);
            
            writer.write("BookingID,GuestName,RoomType,CheckIn,CheckOut,Amount\n");
            writer.write("BK-001,John Smith,SUITE,2024-03-01,2024-03-05,1750.00\n");
            writer.write("BK-002,Jane Doe,DELUXE,2024-03-03,2024-03-07,960.00\n");
            writer.flush();
            writer.close();

            // Upload to S3 instead of writing to local file system
            byte[] reportContent = outputStream.toByteArray();
            String s3Key = "reports/" + year + "/" + month + "/" + fileName;
            
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(s3BucketName)
                    .key(s3Key)
                    .contentType("text/csv")
                    .build();

            s3Client.putObject(putObjectRequest, RequestBody.fromBytes(reportContent));

            result.put("status", "generated");
            result.put("s3Bucket", s3BucketName);
            result.put("s3Key", s3Key);
            result.put("s3Uri", "s3://" + s3BucketName + "/" + s3Key);
            result.put("serverPort", serverPort);

        } catch (IOException e) {
            result.put("status", "error");
            result.put("message", "Failed to generate report: " + e.getMessage());
        } catch (Exception e) {
            result.put("status", "error");
            result.put("message", "Failed to upload to S3: " + e.getMessage());
        }

        return result;
    }

    /**
     * Builds a report download URL using configuration from AWS Systems Manager Parameter Store.
     * Replaces hard-coded URLs with externalized configuration.
     * 
     * @param reportName The name of the report to download
     * @return The download URL retrieved from Parameter Store
     */
    public String buildReportDownloadUrl(String reportName) {
        try {
            // Initialize SSM client lazily
            if (ssmClient == null) {
                ssmClient = SsmClient.builder()
                        .region(Region.of(awsRegion))
                        .build();
            }

            // Retrieve report service URL from Parameter Store
            GetParameterRequest parameterRequest = GetParameterRequest.builder()
                    .name("/resortslite/report/service/url")
                    .withDecryption(false)
                    .build();

            GetParameterResponse parameterResponse = ssmClient.getParameter(parameterRequest);
            String baseUrl = parameterResponse.parameter().value();
            
            return baseUrl + "/download/" + reportName;
            
        } catch (Exception e) {
            // Fallback to environment variable if Parameter Store is not available
            String baseUrl = System.getenv("REPORT_SERVICE_URL");
            if (baseUrl == null || baseUrl.isEmpty()) {
                baseUrl = "https://reports.resorts-internal.com:" + serverPort;
            }
            return baseUrl + "/download/" + reportName;
        }
    }

    /**
     * Retrieves system information with cloud-native configuration.
     * Uses UTC timezone for consistency across distributed cloud environments.
     * 
     * @return Map containing system information
     */
    public Map<String, Object> getSystemInfo() {
        // Use java.time API with UTC timezone for cloud consistency
        String timestamp = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                .withZone(ZoneOffset.UTC)
                .format(Instant.now());
        
        Map<String, Object> info = new HashMap<>();
        info.put("s3Bucket", s3BucketName);
        info.put("awsRegion", awsRegion);
        info.put("serverPort", serverPort);
        info.put("generatedAt", timestamp + " UTC");
        info.put("storageType", "Amazon S3");
        return info;
    }
}
