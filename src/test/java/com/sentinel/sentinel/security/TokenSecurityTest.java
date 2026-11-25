package com.sentinel.sentinel.security;

import com.sentinel.sentinel.config.JwtProperties;
import com.sentinel.sentinel.config.SecurityConfig;
import com.sentinel.sentinel.ingest.dto.EventBatch;
import com.sentinel.sentinel.ingest.dto.IngestResponse;
import com.sentinel.sentinel.ingest.service.IngestService;
import com.sentinel.sentinel.support.TestTokenIssuer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
// Starts the web layer of the application, but does not start the full application context. This is useful for testing the web layer in isolation, without starting the entire application context. It will auto-configure MockMvc, which allows you to perform HTTP requests and assert the results.
// It also allows you to specify which controllers to include in the test, so you can focus on testing a specific controller or set of controllers.
// It enables testing of the web layer without starting a real HTTP server, which makes the tests faster and more focused on the controller logic.
// JWT Properties is a configuration properties class that holds the JWT-related properties, such as issuer, audience, and public key location. By enabling configuration properties for JwtProperties, Spring Boot will automatically bind the properties defined in the application.yml file to the JwtProperties class, making it available for injection into the test class.
@WebMvcTest
@Import(SecurityConfig.class)
@EnableConfigurationProperties(JwtProperties.class)
class TokenSecurityTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private IngestService ingestService;

    private static TestTokenIssuer trustedIssuer;
    private static TestTokenIssuer untrustedIssuer;

    @BeforeAll
    // runs before all tests in the class.
    // It is used to set up shared resources or configurations that are needed for the tests.
    // In this case, it initializes two TestTokenIssuer instances: one with a trusted private key loaded from a PEM file and another with an untrusted private key generated on the fly.
    // These issuers are used to create JWTs for testing authentication and authorization scenarios.

    static void setUpIssuers() throws Exception {
        trustedIssuer = new TestTokenIssuer(
                TestTokenIssuer.loadPrivateKey(Path.of("keys/private.pem")));

        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(2048);
        untrustedIssuer = new TestTokenIssuer(
                (RSAPrivateKey) keyPairGenerator.generateKeyPair().getPrivate());
    }

    // Sends a posy request to the /api/v1/events endpoint with an optional JWT token in the Authorization header.
    // It returns the ResultActions object, which allows for further assertions on the response.
    private ResultActions postEvents(String token) throws Exception {
        var request = post("/api/v1/events")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"events":[{"type":"LOGIN","user":"alice","sourceIp":"192.0.2.21",
                        "success":true,"timestamp":"2026-11-23T09:00:00Z"}]}
                        """);
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        return mvc.perform(request);
    }

    @Test
    // Tests that a request without a JWT token results in an HTTP 401 Unauthorized response.
    void noTokenIsUnauthorized() throws Exception {
        postEvents(null).andExpect(status().isUnauthorized());
    }

    @Test
    // Tests that a request with a tampered JWT signature by splitting the token into header, payload, and signature.
        // modifying the signature by replacing the first character, and sending it results in an HTTP 401 Unauthorized response.
    void tamperedSignatureIsUnauthorized() throws Exception {
        String token = trustedIssuer.issue("acme-ingest", "acme", List.of("ingest"));
        String[] parts = token.split("\\.", -1);
        char replacement = parts[2].charAt(0) == 'A' ? 'B' : 'A';
        parts[2] = replacement + parts[2].substring(1);

        postEvents(String.join(".", parts)).andExpect(status().isUnauthorized());
    }

    @Test
    // Tests that a request with a JWT token signed by an untrusted issuer results in an HTTP 401 Unauthorized response.
    void wrongSigningKeyIsUnauthorized() throws Exception {
        String token = untrustedIssuer.issue("acme-ingest", "acme", List.of("ingest"));

        postEvents(token).andExpect(status().isUnauthorized());
    }

    @Test
    // Tests that a request with an expired JWT token results in an HTTP 401 Unauthorized response.
    void expiredTokenIsUnauthorized() throws Exception {
        String token = trustedIssuer.issue(
                "acme-ingest",
                "acme",
                List.of("ingest"),
                "http://sentinel-idp",
                "sentinel-api",
                true);

        postEvents(token).andExpect(status().isUnauthorized());
    }

    @Test
    // Tests that a request with a JWT token containing the wrong role results in an HTTP 403 Forbidden response.
    void wrongRoleIsForbidden() throws Exception {
        String token = trustedIssuer.issue("acme-analyst", "acme", List.of("analyst"));

        postEvents(token).andExpect(status().isForbidden());
    }

    @Test
    // Tests that a request with a JWT token containing the correct role results in a successful response (not 401 or 403).
    void ingestRolePassesSecurity() throws Exception {
        String token = trustedIssuer.issue("acme-ingest", "acme", List.of("ingest"));
        int responseStatus = postEvents(token).andReturn().getResponse().getStatus();

        assertNotEquals(401, responseStatus, "expected authentication to pass");
        assertNotEquals(403, responseStatus, "expected authorization to pass");
    }

    @Test
    void topLevelCustomerIdIsRejected() throws Exception {
        String token = trustedIssuer.issue("acme-ingest", "acme", List.of("ingest"));

        mvc.perform(post("/api/v1/events")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerId":"globex","events":[{"type":"LOGIN","user":"alice",
                                 "sourceIp":"192.0.2.21","success":true,
                                 "timestamp":"2026-11-23T09:00:00Z"}]}
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(ingestService);
    }

    @Test
    void customerAlwaysComesFromToken() throws Exception {
        String token = trustedIssuer.issue("acme-ingest", "acme", List.of("ingest"));
        when(ingestService.ingest(eq("acme"), any(EventBatch.class)))
                .thenReturn(new IngestResponse(1, 0, List.of()));

        mvc.perform(post("/api/v1/events")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"events":[{"type":"LOGIN","user":"alice",
                                 "sourceIp":"192.0.2.21","success":true,
                                 "timestamp":"2026-11-23T09:00:00Z","customerId":"globex"}]}
                                """))
                .andExpect(status().isOk());

        verify(ingestService).ingest(eq("acme"), any(EventBatch.class));
    }
}
