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
                        "https://" + orPlaceholder(properties.accountId()) + ".r2.cloudflarestorage.com"))
                // R2 ignores the region but the SDK requires one
                .region(Region.of("auto"))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(
                                orPlaceholder(properties.accessKeyId()),
                                orPlaceholder(properties.secretAccessKey()))))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(true)
                        .build())
                .build();
    }

    /**
     * The app must boot without R2 configured (uploads then fail at request
     * time), but {@link AwsBasicCredentials} rejects blank keys and newer SDK
     * releases reject a host-less endpoint at build time — substitute a
     * placeholder so bean creation never fails on an empty account id or
     * credentials.
     */
    private static String orPlaceholder(String value) {
        return (value == null || value.isBlank()) ? "unconfigured" : value;
    }
}
