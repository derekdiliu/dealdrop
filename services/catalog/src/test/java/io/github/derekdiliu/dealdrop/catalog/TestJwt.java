package io.github.derekdiliu.dealdrop.catalog;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Replaces the JWKS lookup with a local key, so tests send real signed tokens through the
 * service's real role mapping without a running identity service.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestJwt {

    private static final RSAKey KEY = generateKey();

    @Bean
    JwtDecoder jwtDecoder() throws JOSEException {
        return NimbusJwtDecoder.withPublicKey(KEY.toRSAPublicKey()).build();
    }

    public static String bearer(String... roles) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("dealdrop-identity")
                .subject(UUID.randomUUID().toString())
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(600))
                .claim("roles", List.of(roles))
                .build();
        String token = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(KEY)))
                .encode(JwtEncoderParameters.from(JwsHeader.with(SignatureAlgorithm.RS256).build(), claims))
                .getTokenValue();
        return "Bearer " + token;
    }

    private static RSAKey generateKey() {
        try {
            return new RSAKeyGenerator(2048).generate();
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
    }
}
