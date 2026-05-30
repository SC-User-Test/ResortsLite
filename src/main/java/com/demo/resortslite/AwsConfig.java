package com.demo.resortslite;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.ssm.SsmClient;

/**
 * AWS SDK Configuration for cloud-native services.
 * FIXED cr-java-0061, cr-java-0062, cr-java-0063: Configures S3 client for cloud storage
 * FIXED cr-java-0069: Configures Secrets Manager client for credential management
 * FIXED cr-java-0071: Configures Systems Manager client for parameter store access
 */
@Configuration
public class AwsConfig {

    @Value("${aws.region:us-east-1}")
    private String awsRegion;

    /**
     * S3 Client bean for cloud storage operations.
     * Replaces local file system dependencies with Amazon S3.
     */
    @Bean
    public S3Client s3Client() {
        return S3Client.builder()
                .region(Region.of(awsRegion))
                .build();
    }

    /**
     * Secrets Manager Client bean for secure credential storage.
     * Replaces hardcoded credentials with AWS Secrets Manager.
     */
    @Bean
    public SecretsManagerClient secretsManagerClient() {
        return SecretsManagerClient.builder()
                .region(Region.of(awsRegion))
                .build();
    }

    /**
     * Systems Manager Client bean for parameter store access.
     * Enables externalized configuration via AWS Parameter Store.
     */
    @Bean
    public SsmClient ssmClient() {
        return SsmClient.builder()
                .region(Region.of(awsRegion))
                .build();
    }
}
