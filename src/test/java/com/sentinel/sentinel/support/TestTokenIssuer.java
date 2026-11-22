package com.sentinel.sentinel.support;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.UUID;

public class TestTokenIssuer {
    private static final String DEFAULT_ISSUER = "http://sentinel-idp";
    private static final String DEFAULT_AUDIENCE = "sentinel-api";

    private final RSAPrivateKey privateKey;
    public TestTokenIssuer(RSAPrivateKey privateKey){
        this.privateKey = privateKey;
    }
    // loads the RSA private key
    public static RSAPrivateKey loadPrivateKey(Path pemFile) throws IOException, GeneralSecurityException {
        // Reads the entire PEM file into the string
        String pem = Files.readString(pemFile);
        // removes the PEM header and footer lines and all whitespace, leaving only the Base64 key data
        String base64 = pem.replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        // decodes the Base64 text into the raw binary (DER) key bytes
        byte[] bytes = Base64.getDecoder().decode(base64);
        // Convert bytes into an RSA private key
        // KeyFactory.getInstance("RSA"): Gets a Java key factory capable of creating RSA keys.
        // PKCS8EncodedKeySpec(bytes): Tells Java that the bytes represent a PKCS#8 private key.
        // generatePrivate(): Converts those bytes into a Java PrivateKey object.
        // (RSAPrivateKey): Casts it to the RSA-specific private key interface.
        // Why PKCS#8? The private key generated indicates a pkcs#8 format
        return (RSAPrivateKey)
                KeyFactory.getInstance("RSA")
                        .generatePrivate(new PKCS8EncodedKeySpec(bytes));
    }

    // A JWT is a signed token made of three Base64URL parts: header.payload.signature. Each key-value pair in the payload is a claim.
    // The header says which algorithm signed the token (here RS256).
    // A payload has the claim stored
    // The signature lets the receiver check the token wasn't changed and was issued by the private-key holder.
    // Whenever a request reaches the API with a JWT, Spring security validates the token and checks its claims
    // iss → Who issued the token?
    // aud → Who should accept the token?
    // sub → Who is calling the API?
    // iat → When was it issued?
    // exp → When does it expire?
    // jti → Which specific token is this?
    // customer_id → Which customer does the caller belong to?
    // roles → What is the caller allowed to do?
    // Difference between caller and subject
    // Caller: The mobile app making the API request.
    // Subject (sub): Person B, the user represented by the token. Sub identifies the individual user represented by the token.
    // customer_id identifies the organization they belong to.
    //    Imagine Acme is a customer of Sentinel.
    //    Acme has three employees, each using a mobile app that authenticates through Google and accesses Sentinel:
    //    Person A uses the mobile app → sub = person-A
    //    Person B uses the mobile app → sub = person-B
    //    Person C uses the mobile app → sub = person-C
    //    All three belong to Acme, so their tokens have
    //    customer_id = acme
    public String issue(String subject, String customerId, List<String> roles) throws JOSEException {
        return issue(subject, customerId, roles, DEFAULT_ISSUER, DEFAULT_AUDIENCE, false);
    }


    // If you don't want to use default issuer and audience, you can specify them. This is useful for testing how the API reacts to tokens with unexpected values.
    public String issue(
            String subject,
            String customerId,
            List<String> roles,
            String issuer,
            String audience,
            boolean expired) throws JOSEException {
        Instant now = Instant.now();
        // JWTClaimsSet represents this collection of claims.
        // The builder pattern is used to create a JWTClaimsSet object with the specified claims.
        // The claims are added to the JWTClaimsSet using the builder pattern, which allows for a fluent and readable way to construct the claims set.
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                // Issuer: Identifies who created and issued the JWT.
                .issuer(issuer)
                // Audience: Identifies which API is intended to receive this JWT.
                .audience(audience)
                // Subject: Identifies who is making the request (the caller/client).
                .subject(subject)
                // Issued At: Records when the JWT was created.
                .issueTime(Date.from(now))
                // Expiration: Defines when the JWT becomes invalid.
                .expirationTime(expired
                        ? Date.from(now.minus(1, ChronoUnit.HOURS))
                        : Date.from(now.plus(1, ChronoUnit.HOURS)))
                // JWT ID: A unique identifier for this token, used to distinguish tokens and support revocation.
                .jwtID(UUID.randomUUID().toString())
                // Customer ID: Identifies which customer (tenant) the caller belongs to.
                // a tenant is an organization, team, or user group that leases access to cloud services
                .claim("customer_id", customerId)
                // Customer ID: Identifies which customer (tenant) the caller belongs to.
                .claim("roles", roles)
                // A caller could have several roles. A JSON array (["ingest"]) is also what Spring's authorities converter reads cleanly later on.
                // Roles: Defines the caller's roles, which determine what they are authorized to do.
                .build();
        JWSHeader header = new JWSHeader(JWSAlgorithm.RS256);
        SignedJWT jwt = new SignedJWT(header, claims);
        jwt.sign(new RSASSASigner(privateKey));
        return jwt.serialize();
    }

    public static void main(String[] args) throws Exception {
        // Initialize default values for the JWT claims and configuration.
        // Subject is null so we can generate a default subject later if one isn't provided.
        String subject = null;
        String customerId = "acme";
        String role = "ingest";
        String issuer = DEFAULT_ISSUER;
        String audience = DEFAULT_AUDIENCE;
        // By default, generate a valid, non-expired token.
        boolean expired = false;
        // Default location of the RSA private key used to sign the JWT.
        Path privateKeyPath = Path.of("keys/private.pem");
        // Tracks the number of positional arguments supplied by the user.
        // Positional arguments are arguments without flags such as --customer or --role.
        int positionalIndex = 0;
        // Iterate through all command-line arguments supplied when running the program.
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--customer", "--customer-id" -> {
                    customerId = args[++i];
                }
                case "--role" -> {
                    role = args[++i];
                }
                case "--sub" -> {
                    subject = args[++i];
                }
                case "--expired" -> {
                    expired = true;
                }
                case "--iss" -> {
                    issuer = args[++i];
                }
                case "--aud" -> {
                    audience = args[++i];
                }
                case "--wrong-key", "--private-key" -> {
                    privateKeyPath = Path.of(args[++i]);
                }
                default -> {
                    if (args[i].startsWith("--")) {
                        throw new IllegalArgumentException("Unknown argument: " + args[i]);
                    }
                    if (positionalIndex == 0) {
                        customerId = args[i];
                    } else if (positionalIndex == 1) {
                        role = args[i];
                    } else if (positionalIndex == 2) {
                        subject = args[i];
                    } else {
                        throw new IllegalArgumentException("Unexpected argument: " + args[i]);
                    }
                    positionalIndex++;
                }
            }
        }

        if (subject == null) {
            subject = customerId + "-" + role;
        }

        RSAPrivateKey key = loadPrivateKey(privateKeyPath);
        TestTokenIssuer tokenIssuer = new TestTokenIssuer(key);
        System.out.print(tokenIssuer.issue(
                subject,
                customerId,
                List.of(role),
                issuer,
                audience,
                expired));
    }
}
