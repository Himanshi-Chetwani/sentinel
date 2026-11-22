package com.sentinel.sentinel.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.Resource;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "sentinel.security.jwt")
public record JwtProperties(
        @NotBlank String issuer,
        @NotBlank String audience,
        Resource publicKeyLocation) {
}
