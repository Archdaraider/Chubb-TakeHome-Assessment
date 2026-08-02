package com.archdaraider.chubb.claims.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import com.archdaraider.chubb.claims.claim.application.ClaimsQueryService;
import com.archdaraider.chubb.claims.claim.domain.ClaimStatus;
import com.archdaraider.chubb.claims.claim.domain.ClaimType;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles({"test", "demo"})
class DemoDataTest {
  @Autowired private DemoData demoData;
  @Autowired private ClaimsQueryService queries;
  @Autowired private JdbcClient jdbc;

  @BeforeEach
  void clearDatabase() {
    jdbc.sql("delete from outbox_messages").update();
    jdbc.sql("delete from claim_timeline").update();
    jdbc.sql("delete from claims").update();
  }

  @Test
  void twoSeedCallsCreateOneFictionalDemoSet() {
    demoData.seed();
    demoData.seed();

    var queue = queries.workQueue(null, null);
    assertThat(queue).hasSize(3);
    assertThat(queue)
        .anySatisfy(
            item -> {
              assertThat(item.claimType()).isEqualTo(ClaimType.MOTOR);
              assertThat(item.market()).isEqualTo("SG");
              assertThat(item.status()).isEqualTo(ClaimStatus.SUBMITTED);
              assertThat(item.estimatedLoss()).isEqualByComparingTo(new BigDecimal("1800.00"));
              assertThat(item.currency()).isEqualTo("SGD");
            })
        .anySatisfy(
            item -> {
              assertThat(item.claimType()).isEqualTo(ClaimType.PROPERTY);
              assertThat(item.market()).isEqualTo("AU");
              assertThat(item.status()).isEqualTo(ClaimStatus.UNDER_REVIEW);
              assertThat(item.estimatedLoss()).isEqualByComparingTo(new BigDecimal("6500.00"));
              assertThat(item.currency()).isEqualTo("AUD");
            })
        .anySatisfy(
            item -> {
              assertThat(item.claimType()).isEqualTo(ClaimType.PROPERTY);
              assertThat(item.market()).isEqualTo("SG");
              assertThat(item.status()).isEqualTo(ClaimStatus.MORE_INFORMATION_REQUIRED);
              assertThat(item.estimatedLoss()).isEqualByComparingTo(new BigDecimal("12000.00"));
              assertThat(item.currency()).isEqualTo("SGD");
            });
    assertThat(queue)
        .allSatisfy(item -> assertThat(item.claimantId()).startsWith("demo-claimant-"));
    assertThat(queue)
        .filteredOn(item -> item.assigneeId() != null)
        .allSatisfy(item -> assertThat(item.assigneeId()).startsWith("demo-officer-"));
    assertThat(rowCount("claim_timeline")).isEqualTo(8);
    assertThat(rowCount("outbox_messages")).isEqualTo(8);
  }

  private long rowCount(String table) {
    return jdbc.sql("select count(*) from " + table).query(Long.class).single();
  }
}
