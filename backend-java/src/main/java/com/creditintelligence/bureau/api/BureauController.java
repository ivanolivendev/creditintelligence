package com.creditintelligence.bureau.api;

import com.creditintelligence.bureau.application.BureauService;
import java.util.UUID;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;

@RestController
@RequestMapping("/api/v1/proposals/{proposalId}/bureau-consultations")
public class BureauController {
    private final BureauService service;

    public BureauController(BureauService service) {
        this.service = service;
    }

    @PostMapping
    public BureauService.Consultation consult(@PathVariable UUID proposalId,
            @Valid @RequestBody Request request) {
        return service.consult(proposalId, request.caseName());
    }

    @GetMapping("/latest")
    public BureauService.Snapshot latest(@PathVariable UUID proposalId) {
        return service.latest(proposalId);
    }

    public record Request(@Pattern(regexp = "COMPLETE|CLEAN|UNAVAILABLE",
            message = "caseName must be COMPLETE, CLEAN or UNAVAILABLE") String caseName) {}
}
