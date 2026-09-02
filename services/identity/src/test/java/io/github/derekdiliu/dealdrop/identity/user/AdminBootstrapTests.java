package io.github.derekdiliu.dealdrop.identity.user;

import com.jayway.jsonpath.JsonPath;
import com.nimbusds.jwt.SignedJWT;
import io.github.derekdiliu.dealdrop.identity.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "dealdrop.admin.email=Ops@Dealdrop.example",
        "dealdrop.admin.password=admin-password-123"
})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AdminBootstrapTests {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository users;

    @Test
    void configuredAdminCanLogInWithAdminRole() throws Exception {
        String body = mvc.perform(post("/api/identity/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "ops@dealdrop.example", "password": "admin-password-123"}
                                """))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        SignedJWT jwt = SignedJWT.parse(JsonPath.<String>read(body, "$.accessToken"));
        assertThat(jwt.getJWTClaimsSet().getStringListClaim("roles")).containsExactly("ADMIN");
    }

    @Test
    void bootstrapIsIdempotentAndNeverPromotesCustomers() {
        userService.ensureAdmin("ops@dealdrop.example", "admin-password-123");
        assertThat(users.findAll()).filteredOn(u -> u.getEmail().equals("ops@dealdrop.example")).hasSize(1);

        userService.register("customer@example.co.nz", "customer-password", "Customer");
        userService.ensureAdmin("customer@example.co.nz", "whatever-password");
        assertThat(users.findByEmail("customer@example.co.nz")).get()
                .extracting(User::getRole).isEqualTo(Role.CUSTOMER);
    }
}
