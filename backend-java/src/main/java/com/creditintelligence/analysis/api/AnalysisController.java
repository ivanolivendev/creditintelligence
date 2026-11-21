package com.creditintelligence.analysis.api;

import com.creditintelligence.analysis.application.AnalysisService;
import com.creditintelligence.analysis.application.AnalysisView;
import com.creditintelligence.analysis.domain.FinancialEngine.Scenario;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestBody;

@RestController
@RequestMapping("/api/v1/proposals/{proposalId}")
public class AnalysisController {
    private final AnalysisService service;
    private final ObjectMapper json;

    public AnalysisController(AnalysisService service, ObjectMapper json) {
        this.service = service;
        this.json = json;
    }

    @PostMapping("/analyses")
    public AnalysisView analyze(@PathVariable UUID proposalId, @RequestAttribute String correlationId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody(required = false) String body) {
        Scenario scenario = null;
        if (body != null && !body.isBlank()) {
            try {
                scenario = json.readValue(body, Scenario.class);
            } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
                throw new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.BAD_REQUEST, "Invalid loan scenario JSON");
            }
        }
        return service.analyze(proposalId, correlationId, idempotencyKey, scenario);
    }

    @GetMapping("/analyses/{analysisId}")
    public AnalysisView get(@PathVariable UUID proposalId, @PathVariable UUID analysisId) {
        return service.dossier(proposalId, analysisId);
    }

    @GetMapping("/dossier")
    public AnalysisView dossier(@PathVariable UUID proposalId, @RequestParam UUID analysisId) {
        return service.dossier(proposalId, analysisId);
    }
}
