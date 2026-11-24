package com.sentinel.sentinel.security;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;

public final class CurrentCustomer {

    private CurrentCustomer() {
    }

    // This function will be called from the apis where it will get the token from the request and extract the customer_id claim from it. If the claim is missing or invalid, it will throw an AccessDeniedException.
    public static String from(Jwt jwt) {
        Object claim = jwt.getClaim("customer_id");
        if (!(claim instanceof String customerId) || customerId.isBlank()) {
            throw new AccessDeniedException("Token has no customer_id");
        }
        return customerId;
    }
}
