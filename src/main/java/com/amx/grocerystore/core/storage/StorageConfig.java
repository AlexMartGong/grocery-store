package com.amx.grocerystore.core.storage;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

import java.net.URI;

@Configuration
@EnableConfigurationProperties(R2StorageProperties.class)
public class StorageConfig {

    @Bean
    public S3Client s3Client(R2StorageProperties properties) {
        return S3Client.builder()
                .endpointOverride(URI.create(
                        "https://" + properties.accountId() + ".r2.cloudflarestorage.com"))
                // R2 ignores the region but the SDK requires one
                .region(Region.of("auto"))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(
                                properties.accessKeyId(), properties.secretAccessKey())))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(true)
                        .build())
                .build();
    }
}
