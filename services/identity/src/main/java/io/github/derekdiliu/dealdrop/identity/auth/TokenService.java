package io.github.derekdiliu.dealdrop.identity.auth;

import io.github.derekdiliu.dealdrop.identity.user.UserAccount;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
class TokenService {

    private final JwtEncoder encoder;
    private final JwtProperties properties;

    TokenService(JwtEncoder encoder, JwtProperties properties) {
        this.encoder = encoder;
        this.properties = properties;
    }

    TokenResponse issue(UserAccount account, RefreshTokenService.Issued refreshToken) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .subject(account.id().toString())
                .issuedAt(now)
                .expiresAt(now.plus(properties.ttl()))
                .claim("email", account.email())
                .claim("roles", List.of(account.role().name()))
                .build();
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).build();
        String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new TokenResponse(token, "Bearer", properties.ttl().toSeconds(), refreshToken.token(),
                refreshToken.expiresIn());
    }
}
