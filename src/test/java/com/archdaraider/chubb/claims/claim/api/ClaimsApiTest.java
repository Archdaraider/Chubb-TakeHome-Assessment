package com.archdaraider.chubb.claims.claim.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@ActiveProfiles("test")
class ClaimsApiTest {
  @Autowired private WebApplicationContext context;
  @Autowired private JdbcClient jdbc;

  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    jdbc.sql("delete from outbox_messages").update();
    jdbc.sql("delete from claim_timeline").update();
    jdbc.sql("delete from claims").update();
    mvc = MockMvcBuilders.webAppContextSetup(context).build();
  }

  @Test
  void completesTheClaimWorkflowOverHttp() throws Exception {
    var location = submitClaim("claimant-101", "2500.00", "SGD");

    mvc.perform(get(location))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.claim.status").value("submitted"))
        .andExpect(jsonPath("$.timeline[0].eventType").value("claim_submitted"));
    mvc.perform(
            post(location + "/assignment")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"officerId\":\"officer-7\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.assigneeId").value("officer-7"));
    apply(location, "startReview", "officer-7", null)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("underReview"));
    apply(location, "requestMoreInformation", "officer-7", "send repair invoice")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("moreInformationRequired"));
    mvc.perform(
            post(location + "/information")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                                        {
                                          "claimantId":"claimant-101",
                                          "information":"invoice reference inv-22"
                                        }
                                        """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("moreInformationRequired"));
    apply(location, "resumeReview", "officer-7", null)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("underReview"));
    apply(location, "approve", "officer-7", "covered")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("approved"))
        .andExpect(jsonPath("$.decisionReason").value("covered"));
    mvc.perform(get(location))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.timeline.length()").value(7));
  }

  @Test
  void filtersQueueAndSeparatesExposureCurrencies() throws Exception {
    var reviewed = submitClaim("claimant-101", "2500.00", "SGD");
    mvc.perform(
            post(reviewed + "/assignment")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"officerId\":\"officer-7\"}"))
        .andExpect(status().isOk());
    apply(reviewed, "startReview", "officer-7", null).andExpect(status().isOk());
    submitClaim("claimant-202", "500.00", "AUD");

    mvc.perform(
            get("/work-queue")
                .queryParam("status", "underReview")
                .queryParam("assigneeId", "officer-7"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].status").value("underReview"))
        .andExpect(jsonPath("$[0].assigneeId").value("officer-7"));
    mvc.perform(get("/exposure").queryParam("market", "SG"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2))
        .andExpect(jsonPath("$[0].currency").value("AUD"))
        .andExpect(jsonPath("$[0].amount").value(500.0))
        .andExpect(jsonPath("$[0].claimCount").value(1))
        .andExpect(jsonPath("$[1].currency").value("SGD"))
        .andExpect(jsonPath("$[1].amount").value(2500.0))
        .andExpect(jsonPath("$[1].claimCount").value(1));
  }

  @Test
  void exposureCombinesMarketsWhenTheMarketIsMissingOrBlank() throws Exception {
    submitClaim("claimant-101", "SG", "100.00", "SGD");
    submitClaim("claimant-202", "AU", "200.00", "SGD");

    assertCombinedExposure(get("/exposure"));
    assertCombinedExposure(get("/exposure").queryParam("market", " "));
    mvc.perform(get("/exposure").queryParam("market", "SG"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].currency").value("SGD"))
        .andExpect(jsonPath("$[0].amount").value(100.0))
        .andExpect(jsonPath("$[0].claimCount").value(1));
  }

  @Test
  void invalidMarketReturnsStableInputProblem() throws Exception {
    mvc.perform(get("/exposure").queryParam("market", "SGP"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("query_invalid"));
  }

  @Test
  void badSubmissionReturnsStableValidationProblem() throws Exception {
    mvc.perform(
            post("/claims")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                                        {
                                          "claimantId":"claimant-101",
                                          "type":"motor",
                                          "market":"SG",
                                          "incidentAt":"2026-08-01T12:00:00Z",
                                          "description":"short",
                                          "estimatedLoss":2500.00,
                                          "currency":"SGD"
                                        }
                                        """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("description_invalid"))
        .andExpect(jsonPath("$.status").value(400));
  }

  @Test
  void unknownClaimReturnsNotFoundProblem() throws Exception {
    mvc.perform(get("/claims/{id}", UUID.randomUUID()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("claim_not_found"));
  }

  @Test
  void invalidTransitionReturnsConflictProblem() throws Exception {
    var location = submitClaim("claimant-101", "2500.00", "SGD");
    mvc.perform(
            post(location + "/assignment")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"officerId\":\"officer-7\"}"))
        .andExpect(status().isOk());

    apply(location, "approve", "officer-7", "too early")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("claim_transition_invalid"));
  }

  @Test
  void wrongOfficerReturnsConflictProblem() throws Exception {
    var location = submitClaim("claimant-101", "2500.00", "SGD");
    mvc.perform(
            post(location + "/assignment")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"officerId\":\"officer-7\"}"))
        .andExpect(status().isOk());

    apply(location, "startReview", "officer-8", null)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("claim_officer_mismatch"));
  }

  @Test
  void badQueryEnumReturnsStableInputProblem() throws Exception {
    mvc.perform(get("/work-queue").queryParam("status", "waiting"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("query_invalid"));
  }

  @Test
  void openApiListsEveryPublicRoute() throws Exception {
    mvc.perform(get("/v3/api-docs"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.paths['/claims']").exists())
        .andExpect(jsonPath("$.paths['/claims/{claimId}']").exists())
        .andExpect(jsonPath("$.paths['/claims/{claimId}/assignment']").exists())
        .andExpect(jsonPath("$.paths['/claims/{claimId}/actions']").exists())
        .andExpect(jsonPath("$.paths['/claims/{claimId}/information']").exists())
        .andExpect(jsonPath("$.paths['/work-queue']").exists())
        .andExpect(jsonPath("$.paths['/exposure']").exists());
  }

  private String submitClaim(String claimantId, String loss, String currency) throws Exception {
    return submitClaim(claimantId, "SG", loss, currency);
  }

  private String submitClaim(String claimantId, String market, String loss, String currency)
      throws Exception {
    var body =
        """
                {
                  "claimantId":"%s",
                  "type":"motor",
                  "market":"%s",
                  "incidentAt":"2026-08-01T12:00:00Z",
                  "description":"Rear bumper was damaged",
                  "estimatedLoss":%s,
                  "currency":"%s"
                }
                """
            .formatted(claimantId, market, loss, currency);
    return mvc.perform(post("/claims").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isCreated())
        .andExpect(header().string("Location", org.hamcrest.Matchers.startsWith("/claims/")))
        .andExpect(jsonPath("$.status").value("submitted"))
        .andReturn()
        .getResponse()
        .getHeader("Location");
  }

  private void assertCombinedExposure(
      org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request)
      throws Exception {
    mvc.perform(request)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].currency").value("SGD"))
        .andExpect(jsonPath("$[0].amount").value(300.0))
        .andExpect(jsonPath("$[0].claimCount").value(2));
  }

  private org.springframework.test.web.servlet.ResultActions apply(
      String location, String action, String officerId, String reason) throws Exception {
    var reasonJson = reason == null ? "null" : "\"" + reason + "\"";
    var body =
        """
                {
                  "action":"%s",
                  "officerId":"%s",
                  "reason":%s
                }
                """
            .formatted(action, officerId, reasonJson);
    return mvc.perform(
        post(location + "/actions").contentType(MediaType.APPLICATION_JSON).content(body));
  }
}
