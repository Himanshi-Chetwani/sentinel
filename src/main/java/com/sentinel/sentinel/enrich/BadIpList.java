package com.sentinel.sentinel.enrich;

import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Set;

@Component
public class BadIpList {

    private final Set<String> badIps;

    public BadIpList(ObjectMapper objectMapper) {
        try (var input = new ClassPathResource("bad-ips.json").getInputStream()) {
            this.badIps = Set.copyOf(objectMapper.readValue(input, new TypeReference<Set<String>>() {}));
        } catch (IOException | JacksonException exception) {
            throw new IllegalStateException("Failed to load bad IP list from classpath: bad-ips.json", exception);
        }
    }

    public boolean contains(String ip) {
        return ip != null && badIps.contains(ip);
    }
}
