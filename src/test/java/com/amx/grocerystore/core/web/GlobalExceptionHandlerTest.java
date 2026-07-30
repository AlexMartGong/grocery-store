package com.amx.grocerystore.core.web;

import com.amx.grocerystore.core.web.exception.InvalidFileException;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders
                .standaloneSetup(new ThrowingController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void badCredentials_returns401InvalidCredentials() throws Exception {
        mvc.perform(post("/throw/bad-credentials"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("invalid_credentials"));
    }

    @Test
    void disabled_returns403WithMessageCode() throws Exception {
        mvc.perform(post("/throw/disabled"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("store_inactive"));
    }

    @Test
    void authentication_returns401Unauthenticated() throws Exception {
        mvc.perform(post("/throw/authentication"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("unauthenticated"));
    }

    @Test
    void accessDenied_returns403Forbidden() throws Exception {
        mvc.perform(post("/throw/access-denied"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("forbidden"));
    }

    @Test
    void validation_returns400WithFields() throws Exception {
        mvc.perform(post("/throw/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("validation_failed"))
                .andExpect(jsonPath("$.fields.name").exists());
    }

    @Test
    void invalidFile_returns400WithCode() throws Exception {
        mvc.perform(post("/throw/invalid-file"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("image_type_not_allowed"));
    }

    @Test
    void maxUploadSize_returns400ImageTooLarge() throws Exception {
        mvc.perform(post("/throw/max-upload-size"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("image_too_large"));
    }

    @RestController
    @RequestMapping("/throw")
    static class ThrowingController {

        @PostMapping("/invalid-file")
        public void invalidFile() {
            throw InvalidFileException.imageTypeNotAllowed();
        }

        @PostMapping("/max-upload-size")
        public void maxUploadSize() {
            throw new MaxUploadSizeExceededException(2 * 1024 * 1024);
        }

        @PostMapping("/bad-credentials")
        public void badCredentials() {
            throw new BadCredentialsException("invalid_credentials");
        }

        @PostMapping("/disabled")
        public void disabled() {
            throw new DisabledException("store_inactive");
        }

        @PostMapping("/authentication")
        public void authentication() {
            throw new InsufficientAuthenticationException("invalid_token");
        }

        @PostMapping("/access-denied")
        public void accessDenied() {
            throw new AccessDeniedException("nope");
        }

        @PostMapping("/validate")
        public void validate(@RequestBody @jakarta.validation.Valid Payload body) {
        }

        record Payload(@NotBlank String name) {
        }
    }
}
