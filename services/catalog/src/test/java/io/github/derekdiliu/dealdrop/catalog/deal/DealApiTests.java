package io.github.derekdiliu.dealdrop.catalog.deal;

import com.jayway.jsonpath.JsonPath;
import io.github.derekdiliu.dealdrop.catalog.TestJwt;
import io.github.derekdiliu.dealdrop.catalog.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, TestJwt.class})
class DealApiTests {

    private static final ZoneId AUCKLAND = ZoneId.of("Pacific/Auckland");
    private static final String ADMIN = TestJwt.bearer("ADMIN");

    @Autowired
    private MockMvc mvc;

    @Autowired
    private DealRepository repository;

    @Test
    void draftIsHiddenUntilPublishedWithAnImage() throws Exception {
        String id = createDeal(dealJson(null, hoursFromNow(-1), hoursFromNow(24)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.gstCents").value(1043))
                .andReturn().getResponse().getContentAsString();
        String dealId = JsonPath.read(id, "$.id");

        mvc.perform(get("/api/catalog/deals")).andExpect(jsonPath("$[*].id", not(hasItem(dealId))));
        mvc.perform(get("/api/catalog/deals/" + dealId)).andExpect(status().isNotFound());

        mvc.perform(admin(post("/api/catalog/admin/deals/" + dealId + "/publish")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Add an image before publishing."));

        String imageKey = uploadImage();
        mvc.perform(admin(put("/api/catalog/admin/deals/" + dealId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(dealJson(imageKey, hoursFromNow(-1), hoursFromNow(24))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imageKey").value(imageKey));

        mvc.perform(admin(post("/api/catalog/admin/deals/" + dealId + "/publish")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"));

        mvc.perform(get("/api/catalog/deals")).andExpect(jsonPath("$[*].id", hasItem(dealId)));
        mvc.perform(get("/api/catalog/deals/" + dealId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("LIVE"))
                .andExpect(jsonPath("$.currency").value("NZD"))
                .andExpect(jsonPath("$.priceCents").value(7999))
                .andExpect(jsonPath("$.imageUrl").value(TestcontainersConfiguration.PUBLIC_BASE_URL + "/" + imageKey));
    }

    @Test
    void publishedDealsAreLocked() throws Exception {
        String dealId = publishedDeal(hoursFromNow(1), hoursFromNow(24));

        mvc.perform(admin(put("/api/catalog/admin/deals/" + dealId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(dealJson(null, hoursFromNow(1), hoursFromNow(24))))
                .andExpect(status().isConflict());
        mvc.perform(admin(delete("/api/catalog/admin/deals/" + dealId))).andExpect(status().isConflict());
        mvc.perform(admin(post("/api/catalog/admin/deals/" + dealId + "/publish"))).andExpect(status().isConflict());
    }

    @Test
    void storefrontShowsUpcomingAndLiveButNotEndedDeals() throws Exception {
        String upcoming = publishedDeal(hoursFromNow(2), hoursFromNow(26));
        Deal ended = new Deal(new DealDetails("Ended deal", "Gone", "deals/old.jpg", 5000, 2500, 10, 1,
                Instant.now().minus(Duration.ofDays(2)), Instant.now().minus(Duration.ofDays(1))));
        ended.publish();
        repository.save(ended);

        mvc.perform(get("/api/catalog/deals"))
                .andExpect(jsonPath("$[*].id", hasItem(upcoming)))
                .andExpect(jsonPath("$[*].id", not(hasItem(ended.getId().toString()))));
        mvc.perform(get("/api/catalog/deals/" + upcoming)).andExpect(jsonPath("$.state").value("UPCOMING"));
        mvc.perform(get("/api/catalog/deals/" + ended.getId())).andExpect(jsonPath("$.state").value("ENDED"));
    }

    @Test
    void rejectsInconsistentDeals() throws Exception {
        String start = hoursFromNow(1);
        String end = hoursFromNow(5);
        createDeal(dealJson(null, start, end).replace("\"priceCents\": 7999", "\"priceCents\": 20000"))
                .andExpect(status().isBadRequest());
        createDeal(dealJson(null, start, end).replace("\"perCustomerLimit\": 2", "\"perCustomerLimit\": 500"))
                .andExpect(status().isBadRequest());
        createDeal(dealJson(null, end, start)).andExpect(status().isBadRequest());
        // A local time without an offset is ambiguous around daylight saving changes.
        createDeal(dealJson(null, "2026-11-20T12:00:00", end)).andExpect(status().isBadRequest());
        createDeal(dealJson("deals/never-uploaded.jpg", start, end))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Image not found. Upload it before attaching it to a deal."));
    }

    @Test
    void uploadsAreRestrictedToSmallImages() throws Exception {
        requestUpload("image/gif", 1000).andExpect(status().isBadRequest());
        requestUpload("image/png", 5 * 1024 * 1024 + 1).andExpect(status().isBadRequest());
        requestUpload("image/png", 1000)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.key", startsWith("deals/")))
                .andExpect(jsonPath("$.headers.Content-Type").value("image/png"));
    }

    @Test
    void backOfficeRequiresAdminRole() throws Exception {
        mvc.perform(get("/api/catalog/admin/deals")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/catalog/admin/deals").header("Authorization", TestJwt.bearer("CUSTOMER")))
                .andExpect(status().isForbidden());
        mvc.perform(admin(get("/api/catalog/admin/deals")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.size").value(20));
    }

    private String publishedDeal(String startsAt, String endsAt) throws Exception {
        String body = createDeal(dealJson(uploadImage(), startsAt, endsAt))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(body, "$.id");
        mvc.perform(admin(post("/api/catalog/admin/deals/" + id + "/publish"))).andExpect(status().isOk());
        return id;
    }

    /** Requests a presigned URL and uploads through it, as a browser would. */
    private String uploadImage() throws Exception {
        byte[] image = new byte[2048];
        String body = requestUpload("image/jpeg", image.length)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        HttpResponse<Void> upload = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create(JsonPath.read(body, "$.uploadUrl")))
                        .header("Content-Type", "image/jpeg")
                        .PUT(HttpRequest.BodyPublishers.ofByteArray(image))
                        .build(),
                HttpResponse.BodyHandlers.discarding());
        assertThat(upload.statusCode()).isEqualTo(200);
        return JsonPath.read(body, "$.key");
    }

    private ResultActions requestUpload(String contentType, long contentLength) throws Exception {
        return mvc.perform(admin(post("/api/catalog/admin/images"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"contentType": "%s", "contentLength": %d}
                        """.formatted(contentType, contentLength)));
    }

    private ResultActions createDeal(String json) throws Exception {
        return mvc.perform(admin(post("/api/catalog/admin/deals"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private static MockHttpServletRequestBuilder admin(MockHttpServletRequestBuilder request) {
        return request.header("Authorization", ADMIN);
    }

    private static String dealJson(String imageKey, String startsAt, String endsAt) {
        return """
                {
                  "title": "Merino throw %s",
                  "description": "100%% NZ merino, made in Canterbury.",
                  "imageKey": %s,
                  "rrpCents": 15900,
                  "priceCents": 7999,
                  "stock": 50,
                  "perCustomerLimit": 2,
                  "startsAt": "%s",
                  "endsAt": "%s"
                }
                """.formatted(UUID.randomUUID(), imageKey == null ? "null" : "\"" + imageKey + "\"", startsAt, endsAt);
    }

    private static String hoursFromNow(int hours) {
        return OffsetDateTime.now(AUCKLAND).plusHours(hours).truncatedTo(ChronoUnit.SECONDS).toString();
    }
}
