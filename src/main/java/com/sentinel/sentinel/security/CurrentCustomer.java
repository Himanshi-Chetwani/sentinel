package com.sentinel.sentinel.security;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;

public final class CurrentCustomer {

    private CurrentCustomer() {
    }

    public static String from(Jwt jwt) {
        Object claim = jwt.getClaim("customer_id");
        if (!(claim instanceof String customerId) || customerId.isBlank()) {
            throw new AccessDeniedException("Token has no customer_id");
        }
        return customerId;
    }
}
