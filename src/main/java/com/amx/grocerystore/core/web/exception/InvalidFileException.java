package com.amx.grocerystore.core.web.exception;

import lombok.Getter;

@Getter
public class InvalidFileException extends RuntimeException {

    private final String code;

    public InvalidFileException(String code, String message) {
        super(message);
        this.code = code;
    }

    public static InvalidFileException emptyFile() {
        return new InvalidFileException("file_required",
                "A non-empty file is required.");
    }

    public static InvalidFileException imageTypeNotAllowed() {
        return new InvalidFileException("image_type_not_allowed",
                "Image type not allowed. Accepted: image/jpeg, image/png, image/webp.");
    }

    public static InvalidFileException imageTooLarge() {
        return new InvalidFileException("image_too_large",
                "Image exceeds the maximum allowed size of 2MB.");
    }
}
