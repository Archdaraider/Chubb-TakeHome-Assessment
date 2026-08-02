package com.archdaraider.chubb.claims.bootstrap;

import com.archdaraider.chubb.claims.claim.application.ApplyClaimActionCommand;
import com.archdaraider.chubb.claims.claim.application.AssignClaimCommand;
import com.archdaraider.chubb.claims.claim.application.ClaimStore;
import com.archdaraider.chubb.claims.claim.application.ClaimsCommandService;
import com.archdaraider.chubb.claims.claim.application.SubmitClaimCommand;
import com.archdaraider.chubb.claims.claim.domain.ClaimAction;
import com.archdaraider.chubb.claims.claim.domain.ClaimType;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("demo")
public class DemoData implements ApplicationRunner {
  private final ClaimsCommandService commands;
  private final ClaimStore claimStore;
  private final Clock clock;

  public DemoData(ClaimsCommandService commands, ClaimStore claimStore, Clock clock) {
    this.commands = commands;
    this.claimStore = claimStore;
    this.clock = clock;
  }

  @Override
  @Transactional
  public void run(ApplicationArguments arguments) {
    seed();
  }

  @Transactional
  public void seed() {
    if (claimStore.hasAny()) {
      return;
    }

    var now = clock.instant();
    commands.submit(
        new SubmitClaimCommand(
            "demo-claimant-motor",
            ClaimType.MOTOR,
            "SG",
            now.minus(2, ChronoUnit.DAYS),
            "Fictional parked vehicle bumper damage",
            new BigDecimal("1800.00"),
            "SGD"));

    var australia =
        commands.submit(
            new SubmitClaimCommand(
                "demo-claimant-au-property",
                ClaimType.PROPERTY,
                "AU",
                now.minus(4, ChronoUnit.DAYS),
                "Fictional apartment ceiling water damage",
                new BigDecimal("6500.00"),
                "AUD"));
    commands.assign(new AssignClaimCommand(australia.id(), "demo-officer-au"));
    commands.apply(
        new ApplyClaimActionCommand(
            australia.id(), ClaimAction.START_REVIEW, "demo-officer-au", null));

    var singapore =
        commands.submit(
            new SubmitClaimCommand(
                "demo-claimant-sg-property",
                ClaimType.PROPERTY,
                "SG",
                now.minus(3, ChronoUnit.DAYS),
                "Fictional kitchen pipe and cabinet damage",
                new BigDecimal("12000.00"),
                "SGD"));
    commands.assign(new AssignClaimCommand(singapore.id(), "demo-officer-sg"));
    commands.apply(
        new ApplyClaimActionCommand(
            singapore.id(), ClaimAction.START_REVIEW, "demo-officer-sg", null));
    commands.apply(
        new ApplyClaimActionCommand(
            singapore.id(),
            ClaimAction.REQUEST_MORE_INFORMATION,
            "demo-officer-sg",
            "provide a fictional repair estimate"));
  }
}
