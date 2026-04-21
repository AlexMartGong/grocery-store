package com.amx.grocerystore.core.security;

import com.amx.grocerystore.core.web.dto.ErrorResponse;
import org.jspecify.annotations.NonNull;
import tools.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(@NonNull HttpServletRequest request,
                         @NonNull HttpServletResponse response,
                         @NonNull AuthenticationException ex) throws IOException {
        String code = switch (ex) {
            case AccountInactiveException aix -> aix.getCode();
            default -> {
                String msg = ex.getMessage();
                yield msg == null || msg.isBlank() ? "unauthenticated" : msg;
            }
        };
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), ErrorResponse.of(code));
    }
}
