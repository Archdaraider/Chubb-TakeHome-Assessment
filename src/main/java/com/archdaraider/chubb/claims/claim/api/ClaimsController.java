package com.archdaraider.chubb.claims.claim.api;

import com.archdaraider.chubb.claims.claim.api.ClaimRequests.ActionRequest;
import com.archdaraider.chubb.claims.claim.api.ClaimRequests.AssignmentRequest;
import com.archdaraider.chubb.claims.claim.api.ClaimRequests.InformationRequest;
import com.archdaraider.chubb.claims.claim.api.ClaimRequests.SubmitClaimRequest;
import com.archdaraider.chubb.claims.claim.api.ClaimResponses.ClaimDetailsResponse;
import com.archdaraider.chubb.claims.claim.api.ClaimResponses.ClaimResponse;
import com.archdaraider.chubb.claims.claim.api.ClaimResponses.ExposureResponse;
import com.archdaraider.chubb.claims.claim.api.ClaimResponses.WorkQueueResponse;
import com.archdaraider.chubb.claims.claim.application.ApplyClaimActionCommand;
import com.archdaraider.chubb.claims.claim.application.AssignClaimCommand;
import com.archdaraider.chubb.claims.claim.application.ClaimsCommandService;
import com.archdaraider.chubb.claims.claim.application.ClaimsQueryService;
import com.archdaraider.chubb.claims.claim.application.ProvideInformationCommand;
import com.archdaraider.chubb.claims.claim.application.SubmitClaimCommand;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ClaimsController {
  private final ClaimsCommandService commands;
  private final ClaimsQueryService queries;
  private final ClaimApiMapper mapper;

  public ClaimsController(
      ClaimsCommandService commands, ClaimsQueryService queries, ClaimApiMapper mapper) {
    this.commands = commands;
    this.queries = queries;
    this.mapper = mapper;
  }

  @PostMapping("/claims")
  public ResponseEntity<ClaimResponse> submit(@Valid @RequestBody SubmitClaimRequest request) {
    var snapshot =
        commands.submit(
            new SubmitClaimCommand(
                request.claimantId(),
                mapper.claimType(request.type()),
                request.market(),
                request.incidentAt(),
                request.description(),
                request.estimatedLoss(),
                request.currency()));
    return ResponseEntity.created(URI.create("/claims/" + snapshot.id()))
        .body(mapper.claim(snapshot));
  }

  @GetMapping("/claims/{claimId}")
  public ClaimDetailsResponse get(@PathVariable UUID claimId) {
    return mapper.details(queries.get(claimId));
  }

  @PostMapping("/claims/{claimId}/assignment")
  public ClaimResponse assign(
      @PathVariable UUID claimId, @Valid @RequestBody AssignmentRequest request) {
    return mapper.claim(commands.assign(new AssignClaimCommand(claimId, request.officerId())));
  }

  @PostMapping("/claims/{claimId}/actions")
  public ClaimResponse apply(
      @PathVariable UUID claimId, @Valid @RequestBody ActionRequest request) {
    return mapper.claim(
        commands.apply(
            new ApplyClaimActionCommand(
                claimId, mapper.action(request.action()), request.officerId(), request.reason())));
  }

  @PostMapping("/claims/{claimId}/information")
  public ClaimResponse provideInformation(
      @PathVariable UUID claimId, @Valid @RequestBody InformationRequest request) {
    return mapper.claim(
        commands.provideInformation(
            new ProvideInformationCommand(claimId, request.claimantId(), request.information())));
  }

  @GetMapping("/work-queue")
  public List<WorkQueueResponse> workQueue(
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String assigneeId) {
    return queries.workQueue(mapper.queryStatus(status), assigneeId).stream()
        .map(mapper::queueItem)
        .toList();
  }

  @GetMapping("/exposure")
  public List<ExposureResponse> exposure(@RequestParam(required = false) String market) {
    return queries.exposure(mapper.queryMarket(market)).stream().map(mapper::exposure).toList();
  }
}
