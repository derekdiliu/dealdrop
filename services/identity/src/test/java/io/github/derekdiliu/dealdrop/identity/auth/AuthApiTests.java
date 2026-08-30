package io.github.derekdiliu.dealdrop.identity.auth;

import com.jayway.jsonpath.JsonPath;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jwt.SignedJWT;
import io.github.derekdiliu.dealdrop.identity.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AuthApiTests {

    private static final String PASSWORD = "correct-horse-battery";

    @Autowired
    private MockMvc mvc;

    @Test
    void registerLoginAndFetchProfile() throws Exception {
        String email = uniqueEmail();
        register(email, PASSWORD, "Aroha")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.displayName").value("Aroha"))
                .andExpect(jsonPath("$.role").value("CUSTOMER"))
                .andExpect(jsonPath("$", not(hasKey("passwordHash"))));

        String token = loginForToken(email, PASSWORD);

        SignedJWT jwt = SignedJWT.parse(token);
        assertThat(jwt.getHeader().getKeyID()).isNotBlank();
        assertThat(jwt.getJWTClaimsSet().getIssuer()).isEqualTo("dealdrop-identity");
        assertThat(jwt.getJWTClaimsSet().getStringListClaim("roles")).containsExactly("CUSTOMER");

        mvc.perform(get("/api/identity/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(jwt.getJWTClaimsSet().getSubject()))
                .andExpect(jsonPath("$.email").value(email));
    }

    @Test
    void emailIsCaseInsensitiveAndUnique() throws Exception {
        String email = uniqueEmail();
        register(email, PASSWORD, "Tama").andExpect(status().isCreated());

        register(email.toUpperCase(), PASSWORD, "Tama again")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("An account with this email already exists."));

        loginForToken(email.toUpperCase(), PASSWORD);
    }

    @Test
    void rejectsInvalidRegistration() throws Exception {
        register("not-an-email", PASSWORD, "Mere").andExpect(status().isBadRequest());
        register(uniqueEmail(), "short", "Mere").andExpect(status().isBadRequest());
        register(uniqueEmail(), PASSWORD, " ").andExpect(status().isBadRequest());
    }

    @Test
    void wrongPasswordAndUnknownEmailGetTheSameResponse() throws Exception {
        String email = uniqueEmail();
        register(email, PASSWORD, "Hemi").andExpect(status().isCreated());

        String wrongPassword = login(email, "wrong-password")
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();
        String unknownEmail = login(uniqueEmail(), PASSWORD)
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        assertThat(unknownEmail).isEqualTo(wrongPassword);
    }

    @Test
    void profileRequiresValidToken() throws Exception {
        mvc.perform(get("/api/identity/me")).andExpect(status().isUnauthorized());

        mvc.perform(get("/api/identity/me").header("Authorization", "Bearer " + tokenSignedByAnotherKey()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void jwksPublishesOnlyThePublicKey() throws Exception {
        mvc.perform(get(JwksController.PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keys.length()").value(1))
                .andExpect(jsonPath("$.keys[0].kty").value("RSA"))
                .andExpect(jsonPath("$.keys[0].n").exists())
                .andExpect(jsonPath("$.keys[0]", not(hasKey("d"))))
                .andExpect(jsonPath("$.keys[0]", not(hasKey("p"))));
    }

    private ResultActions register(String email, String password, String displayName) throws Exception {
        return mvc.perform(post("/api/identity/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email": "%s", "password": "%s", "displayName": "%s"}
                        """.formatted(email, password, displayName)));
    }

    private ResultActions login(String email, String password) throws Exception {
        return mvc.perform(post("/api/identity/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email": "%s", "password": "%s"}
                        """.formatted(email, password)));
    }

    private String loginForToken(String email, String password) throws Exception {
        String body = login(email, password)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(3600))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.accessToken");
    }

    private static String tokenSignedByAnotherKey() throws Exception {
        RSAKey otherKey = new RSAKeyGenerator(2048).keyIDFromThumbprint(true).generate();
        NimbusJwtEncoder encoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(otherKey)));
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("dealdrop-identity")
                .subject(UUID.randomUUID().toString())
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(600))
                .claim("roles", List.of("ADMIN"))
                .build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(SignatureAlgorithm.RS256).build(), claims))
                .getTokenValue();
    }

    private static String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@example.co.nz";
    }
}
