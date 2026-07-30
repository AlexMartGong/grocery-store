package com.amx.grocerystore.core.storage;

import org.springframework.web.multipart.MultipartFile;

public interface StorageService {

    /**
     * Uploads the file under the given object key and returns its public URL.
     */
    String uploadFile(MultipartFile file, String objectKey);

    /**
     * Deletes the object; missing keys are ignored (idempotent, best-effort).
     */
    void deleteFile(String objectKey);
}
