package com.demo.resortslite;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;

import javax.annotation.PostConstruct;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * S3 Storage Service for containerized file operations.
 * Replaces local file system access with AWS S3 object storage.
 */
@Service
public class S3StorageService {

    @Value("${aws.s3.bucket-name}")
    private String bucketName;

    @Value("${aws.s3.region}")
    private String region;

    private S3Client s3Client;

    @PostConstruct
    public void init() {
        this.s3Client = S3Client.builder()
                .region(Region.of(region))
                .build();
    }

    /**
     * Upload content to S3 bucket
     * @param key S3 object key (file path)
     * @param content Content to upload
     * @return S3 object URL
     */
    public String uploadFile(String key, byte[] content) {
        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build();

        s3Client.putObject(putObjectRequest, RequestBody.fromBytes(content));
        return String.format("s3://%s/%s", bucketName, key);
    }

    /**
     * Upload text content to S3 bucket
     * @param key S3 object key (file path)
     * @param content Text content to upload
     * @return S3 object URL
     */
    public String uploadTextFile(String key, String content) {
        return uploadFile(key, content.getBytes());
    }

    /**
     * Download file from S3 bucket
     * @param key S3 object key (file path)
     * @return File content as byte array
     */
    public byte[] downloadFile(String key) throws IOException {
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build();

        try (InputStream inputStream = s3Client.getObject(getObjectRequest);
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[1024];
            int length;
            while ((length = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, length);
            }
            return outputStream.toByteArray();
        }
    }

    /**
     * Generate S3 object key from file path
     * @param fileName File name
     * @return S3 object key
     */
    public String generateS3Key(String fileName) {
        return "reports/" + fileName;
    }

    /**
     * Get S3 bucket name
     * @return Configured S3 bucket name
     */
    public String getBucketName() {
        return bucketName;
    }
}
