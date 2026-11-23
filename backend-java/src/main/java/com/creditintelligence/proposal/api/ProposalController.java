package com.creditintelligence.proposal.api;

import com.creditintelligence.proposal.application.ProposalService;
import com.creditintelligence.proposal.application.ProposalView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/proposals")
public class ProposalController {
    private final ProposalService service;

    public ProposalController(ProposalService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<ProposalView> create(@Valid @RequestBody CreateProposalRequest request) {
        var result = service.create(request.applicant().name(), request.applicant().syntheticIdentifier(),
                request.declaredMonthlyIncome(), request.currency());
        return ResponseEntity.created(URI.create("/api/v1/proposals/" + result.id())).body(result);
    }

    @GetMapping
    public java.util.List<ProposalView> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    public ProposalView get(@PathVariable UUID id) {
        return service.get(id);
    }

    public record CreateProposalRequest(
            @NotNull @Valid ApplicantRequest applicant,
            @NotNull @DecimalMin("0.00") @Digits(integer = 17, fraction = 2) BigDecimal declaredMonthlyIncome,
            @NotBlank @Pattern(regexp = "BRL") String currency) {}

    public record ApplicantRequest(
            @NotBlank @Size(max = 160) String name,
            @Pattern(regexp = "DEMO-[A-Za-z0-9-]{1,32}") String syntheticIdentifier) {}
}
