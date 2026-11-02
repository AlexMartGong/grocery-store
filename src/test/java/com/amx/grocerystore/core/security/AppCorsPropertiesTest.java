package com.amx.grocerystore.core.security;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The refresh cookie needs {@code allowCredentials=true}, which forbids wildcard origins. */
class AppCorsPropertiesTest {

    private static AppCorsProperties props(List<String> origins, boolean allowCredentials) {
        return new AppCorsProperties(origins, List.of("GET", "POST"), List.of("Authorization", "Content-Type"),
                List.of("Authorization"), allowCredentials, 3600);
    }

    @Test
    void credentialsWithWildcardOrigin_failsFast() {
        assertThatThrownBy(() -> props(List.of("*"), true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("wildcard");
        assertThatThrownBy(() -> props(List.of("https://*.example.mx"), true))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void credentialsWithExplicitHttpsOrigins_accepted() {
        AppCorsProperties props = props(List.of("https://app.example.mx", "http://localhost:5173"), true);

        assertThat(props.allowCredentials()).isTrue();
        assertThat(props.allowedOrigins()).containsExactly("https://app.example.mx", "http://localhost:5173");
    }

    @Test
    void plainHttpPublicOrigin_rejected() {
        assertThatThrownBy(() -> props(List.of("http://app.example.mx"), true))
                .isInstanceOf(IllegalStateException.class);
    }
}
