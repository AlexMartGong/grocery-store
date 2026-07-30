package com.amx.grocerystore.core.storage;

import com.amx.grocerystore.core.web.exception.InvalidFileException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.IOException;
import java.io.UncheckedIOException;

@Slf4j
@Service
@RequiredArgsConstructor
public class R2StorageService implements StorageService {

    private final S3Client s3Client;
    private final R2StorageProperties properties;

    @Override
    public String uploadFile(MultipartFile file, String objectKey) {
        if (file == null || file.isEmpty()) {
            throw InvalidFileException.emptyFile();
        }
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(properties.bucketName())
                .key(objectKey)
                .contentType(file.getContentType())
                .build();
        try {
            s3Client.putObject(request,
                    RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read upload stream for " + objectKey, e);
        }
        return properties.publicUrl() + "/" + objectKey;
    }

    @Override
    public void deleteFile(String objectKey) {
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(properties.bucketName())
                    .key(objectKey)
                    .build());
        } catch (S3Exception e) {
            // best-effort: a missing/unreachable object must not fail the business operation
            log.warn("Could not delete storage object {}: {}", objectKey, e.getMessage());
        }
    }
}
