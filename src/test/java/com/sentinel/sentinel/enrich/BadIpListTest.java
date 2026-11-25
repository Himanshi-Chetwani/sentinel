package com.sentinel.sentinel.enrich;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BadIpListTest {

    private final BadIpList badIpList = new BadIpList(JsonMapper.builder().build());

    @Test
    void containsKnownBadIpsAndRejectsUnknownIps() {
        assertTrue(badIpList.contains("203.0.113.7"));
        assertTrue(badIpList.contains("198.51.100.9"));
        assertFalse(badIpList.contains("10.0.0.5"));
        assertFalse(badIpList.contains(null));
    }
}
