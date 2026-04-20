package com.amx.grocerystore.core.web.dto;

import java.util.Map;

public record ErrorResponse(
        String error,
        String message,
        Map<String, String> fields
) {

    public static ErrorResponse of(String error) {
        return new ErrorResponse(error, null, null);
    }

    public static ErrorResponse validation(Map<String, String> fields) {
        return new ErrorResponse("validation_failed", null, fields);
    }
}
