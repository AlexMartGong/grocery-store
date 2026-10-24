package com.amx.grocerystore.core.storage;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.s3.S3Client;

import static org.assertj.core.api.Assertions.assertThatCode;

class StorageConfigTest {

    private final StorageConfig storageConfig = new StorageConfig();

    @Test
    void s3Client_r2Unconfigured_buildsWithoutFailingBoot() {
        R2StorageProperties blank = new R2StorageProperties("", "", "", "ventacore-products", "");

        assertThatCode(() -> {
            try (S3Client client = storageConfig.s3Client(blank)) {
                // building is the assertion
            }
        }).doesNotThrowAnyException();
    }

    @Test
    void s3Client_r2NullProperties_buildsWithoutFailingBoot() {
        R2StorageProperties unset = new R2StorageProperties(null, null, null, null, null);

        assertThatCode(() -> {
            try (S3Client client = storageConfig.s3Client(unset)) {
                // building is the assertion
            }
        }).doesNotThrowAnyException();
    }
}
