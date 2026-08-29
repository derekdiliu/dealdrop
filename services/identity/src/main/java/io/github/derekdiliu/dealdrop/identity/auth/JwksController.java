package io.github.derekdiliu.dealdrop.identity.auth;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Publishes the public signing key so other services can verify tokens.
 */
@RestController
class JwksController {

    static final String PATH = "/.well-known/jwks.json";

    private final Map<String, Object> jwks;

    JwksController(RSAKey signingKey) {
        this.jwks = new JWKSet(signingKey.toPublicJWK()).toJSONObject();
    }

    @GetMapping(PATH)
    Map<String, Object> jwks() {
        return jwks;
    }
}
