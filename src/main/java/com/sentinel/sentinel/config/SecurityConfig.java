package com.sentinel.sentinel.config;

import java.io.IOException;
import java.io.InputStream;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.converter.RsaKeyConverters;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

@Configuration
public class SecurityConfig {

    @Bean
    // jwt properties are loaded from application.yml and injecteded into this method by Spring Boot.
    // The JwtProperties record is a simple data holder for the issuer, audience, and public key location.
    // Public key is read, converted to RSAPublicKey, and used to create a NimbusJwtDecoder that validates JWTs.
    // the decoder is returned as a Spring bean and used by Spring Security to validate incoming JWTs in requests.
    // The spring application starts, scans for @configuration classes, finds this SecurityConfig class, and calls the jwtDecoder() method to create the JwtDecoder bean.
    // this bean is returned to spring security, which uses it to validate JWTs in incoming requests.
    public JwtDecoder jwtDecoder(JwtProperties properties) throws IOException {
        // getInputStream() opens the public.pem file. Open files use operating-system resources, so they should always be closed.
        // we are getting hold of the InputStream in a try-with-resources block, which automatically closes it when the block ends.
        // The input stream is the public key in PEM format, which is a Base64-encoded representation of the key with header and footer lines.
        try (InputStream publicKeyStream = properties.publicKeyLocation().getInputStream()) {
            // Converts the PEM-encoded public key into an RSAPublicKey object that can be used to verify JWT signatures.
            Converter<InputStream, RSAPublicKey> converter = RsaKeyConverters.x509();
            RSAPublicKey publicKey = converter.convert(publicKeyStream);
            // decoder splits the JWT into its three parts, decodes the header and payload, verifies the signature using the public key, and returns a Jwt object if valid.
            // If invalid like expired, wrong issuer, wrong audience, or signature mismatch, it throws an exception and Spring Security rejects the request.
            NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(publicKey)
                    .signatureAlgorithm(SignatureAlgorithm.RS256)
                    .build();
            // using oauth 2 token validators to validate the claims in the JWT payload.
            // The issuer and timestamp validator checks that the "iss" claim matches the expected issuer and that the token is not expired.
            // The audience validator checks that the "aud" claim contains the expected audience.
            // The expiration required validator ensures that the "exp" claim is present.
            // All these validators are combined into a DelegatingOAuth2TokenValidator, which is set on the decoder.
            OAuth2TokenValidator<Jwt> issuerAndTimestampValidator =
                    JwtValidators.createDefaultWithIssuer(properties.issuer());
            OAuth2TokenValidator<Jwt> audienceValidator =
                    new JwtClaimValidator<List<String>>(
                            "aud",
                            audience -> audience != null && audience.contains(properties.audience()));
            OAuth2TokenValidator<Jwt> expirationRequiredValidator =
                    new JwtClaimValidator<Instant>("exp", expiration -> expiration != null);
            // The decoder can only hold one validator, so we wrap all three validators in a DelegatingOAuth2TokenValidator that runs them in sequence.
            decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                    issuerAndTimestampValidator,
                    audienceValidator,
                    expirationRequiredValidator));
            return decoder;
        }
    }

    @Bean
    // Here we convert a claim in the JWT payload into a Spring Security authority.
    // The "roles" claim is a list of strings like ["ingest", "analyst"].
    // We want to convert each role into a GrantedAuthority with the prefix "ROLE_". For example, "ingest" becomes "ROLE_ingest".
    // This allows us to use Spring Security's hasRole() method in our security configuration.
    // This function is called during docker compose up, when the Spring application context is being initialized and the SecurityConfig class is being processed.
    // It stores the authentication converter bean in the application context, which is later injected into the securityFilterChain() method to configure JWT authentication.
    // The jwtAuthenticationConverter() method is called by Spring Security when processing incoming requests with JWTs.
    // It uses the converter to extract authorities from the JWT and create an Authentication object for the security context.
    // Rules are created in the securityFilterChain() when the app starts up.
    // The converter is used to extract the "roles" claim from the JWT and convert it into Spring Security authorities
    // which are then used to enforce access control based on roles.
    // When the events api is called it checks if teh role is "ingest" and if the alerts api is called it checks if the role is "analyst".
    // returns the converter that extracts the "roles" claim from the JWT and converts it into Spring Security authorities with the prefix "ROLE_".
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authoritiesConverter = new JwtGrantedAuthoritiesConverter();
        authoritiesConverter.setAuthoritiesClaimName("roles");
        authoritiesConverter.setAuthorityPrefix("ROLE_");

        JwtAuthenticationConverter authenticationConverter = new JwtAuthenticationConverter();
        authenticationConverter.setJwtGrantedAuthoritiesConverter(authoritiesConverter);
        return authenticationConverter;
    }

    @Bean
    // Spring container calls securityFilterChain() to create the security filter chain bean,
    // which is used by Spring Security to enforce security rules on incoming requests.
    // Postmans ends the request to the API, tomcat on 8080 receives the request, and Spring Security intercepts it before it reaches the controller.
    // only if the request matches the rules defined in securityFilterChain() will it be allowed to proceed to the controller.
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtAuthenticationConverter jwtAuthenticationConverter) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.POST, "/api/v1/events").hasRole("ingest")
                        .requestMatchers("/api/v1/alerts/**").hasRole("analyst")
                        .anyRequest().authenticated())
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)));
        return http.build(); // builds the security filter chain and returns it to Spring Security, which uses it to enforce the rules on incoming requests.
    }
}
