package os.assurance.eu.api.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import os.assurance.eu.api.tenant.TenantEntity;
import os.assurance.eu.api.tenant.TenantJpaRepository;
import os.assurance.eu.api.tenant.UserEntity;
import os.assurance.eu.api.tenant.UserJpaRepository;
import os.assurance.eu.api.tenant.UserRole;
import os.assurance.eu.api.auth.JwtService;

@SpringBootTest
@AutoConfigureMockMvc
class AuditEventPagingApiTest {
  @Autowired MockMvc mockMvc;
  @Autowired ObjectMapper objectMapper;
  @Autowired TenantJpaRepository tenants;
  @Autowired UserJpaRepository users;
  @Autowired JwtService jwtService;

  @Test
  void listsNewestFirstWithinTheLimitAndPagesBackWithBefore() throws Exception {
    UUID tenantId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    tenants.save(new TenantEntity(tenantId, "Paging " + tenantId, "starter", "EU", Instant.now()));
    users.save(new UserEntity(userId, tenantId, "paging-" + tenantId + "@example.com", UserRole.ADMIN, Instant.now()));
    String bearer = "Bearer " + jwtService.issueAccessToken(userId, tenantId, UserRole.ADMIN);
    for (int i = 1; i <= 3; i++) {
      mockMvc.perform(post("/api/v1/audit-events")
              .header("Authorization", bearer)
              .contentType(MediaType.APPLICATION_JSON)
              .content("{\"eventType\":\"note.n" + i + "\",\"resourceType\":\"note\",\"resourceId\":\"" + i + "\",\"payload\":{}}"))
          .andExpect(status().isCreated());
      Thread.sleep(5);
    }

    JsonNode first = list(bearer, "?limit=2");
    assertThat(first).hasSize(2);
    assertThat(first.get(0).get("eventType").asText()).isEqualTo("manual.note.n3");
    assertThat(first.get(1).get("eventType").asText()).isEqualTo("manual.note.n2");

    JsonNode older = list(bearer, "?limit=2&before=" + first.get(1).get("createdAt").asText());
    assertThat(older).hasSize(1);
    assertThat(older.get(0).get("eventType").asText()).isEqualTo("manual.note.n1");

    // Asking for more than the cap is clamped, not refused.
    assertThat(list(bearer, "?limit=100000")).hasSize(3);
  }

  private JsonNode list(String bearer, String query) throws Exception {
    String body = mockMvc.perform(get("/api/v1/audit-events" + query).header("Authorization", bearer))
        .andExpect(status().isOk())
        .andReturn().getResponse().getContentAsString();
    return objectMapper.readTree(body);
  }
}
