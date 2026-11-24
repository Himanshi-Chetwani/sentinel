package com.sentinel.sentinel.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CurrentCustomerTest {

    @Test
    void returnsCustomerIdFromJwt() {
        Jwt jwt = Jwt.withTokenValue("t")
                .header("alg", "none")
                .claim("customer_id", "acme")
                .build();

        assertEquals("acme", CurrentCustomer.from(jwt));
    }

    @Test
    void rejectsMissingCustomerId() {
        Jwt jwt = Jwt.withTokenValue("t")
                .header("alg", "none")
                .claim("sub", "x")
                .build();

        assertThrows(AccessDeniedException.class, () -> CurrentCustomer.from(jwt));
    }

    @Test
    void rejectsBlankCustomerId() {
        Jwt jwt = Jwt.withTokenValue("t")
                .header("alg", "none")
                .claim("customer_id", " ")
                .build();

        assertThrows(AccessDeniedException.class, () -> CurrentCustomer.from(jwt));
    }

    @Test
    void rejectsCustomerIdWithUnexpectedType() {
        Jwt jwt = Jwt.withTokenValue("t")
                .header("alg", "none")
                .claim("customer_id", 123)
                .build();

        assertThrows(AccessDeniedException.class, () -> CurrentCustomer.from(jwt));
    }
}
