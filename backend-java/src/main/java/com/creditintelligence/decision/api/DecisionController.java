package com.creditintelligence.decision.api;

import com.creditintelligence.decision.application.DecisionService;
import com.creditintelligence.decision.application.DecisionView;
import com.creditintelligence.decision.domain.DecisionOutcome;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/proposals/{proposalId}/decisions")
public class DecisionController {
    private final DecisionService service;

    public DecisionController(DecisionService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<DecisionView> decide(@PathVariable UUID proposalId,
            @Valid @RequestBody DecisionRequest request, @RequestAttribute String correlationId) {
        var result = service.decide(proposalId, request.analysisId(), request.documentSnapshotHash(),
                request.outcome(), request.reason(), correlationId);
        return ResponseEntity.created(URI.create("/api/v1/proposals/" + proposalId + "/decisions/" + result.id()))
                .body(result);
    }

    @GetMapping
    public List<DecisionView> list(@PathVariable UUID proposalId) {
        return service.list(proposalId);
    }

    public record DecisionRequest(@NotNull UUID analysisId,
            @NotBlank @Pattern(regexp = "[0-9a-f]{64}") String documentSnapshotHash,
            @NotNull DecisionOutcome outcome,
            @NotBlank @Size(max = 1000) String reason) {}
}
