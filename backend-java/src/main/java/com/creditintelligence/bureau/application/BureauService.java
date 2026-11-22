package com.creditintelligence.bureau.application;

import com.creditintelligence.proposal.application.ProposalService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Service
public class BureauService {
    private final CreditBureauPort provider;
    private final ProposalService proposals;
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final TransactionTemplate transactions;

    public BureauService(CreditBureauPort provider, ProposalService proposals, JdbcTemplate jdbc,
            ObjectMapper json, TransactionTemplate transactions) {
        this.provider = provider;
        this.proposals = proposals;
        this.jdbc = jdbc;
        this.json = json;
        this.transactions = transactions;
    }

    public Consultation consult(UUID proposalId, String caseName) {
        if (!List.of("COMPLETE", "CLEAN", "UNAVAILABLE").contains(caseName)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown synthetic bureau case");
        }
        var proposal = proposals.get(proposalId);
        if ("APPROVED".equals(proposal.status()) || "REJECTED".equals(proposal.status())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Finalized proposal cannot be enriched");
        }
        // External I/O occurs before the short insert transaction.
        var report = provider.consult(new CreditBureauPort.BureauQuery(proposalId, caseName,
                "SYNTHETIC_DOCUMENTARY_PRE_ANALYSIS"));
        var id = UUID.randomUUID();
        var stored = new BureauReport(report.provider(), report.product(), report.environment(),
                report.status(), report.consultedAt(), report.responseVersion(), id.toString(),
                report.score(), report.restrictions(), report.limitations());
        var serialized = serialize(stored);
        transactions.executeWithoutResult(status -> {
            // Serialize a consultation with document/decision mutations on the proposal row.
            var current = proposals.lockForMutation(proposalId);
            if ("APPROVED".equals(current.status()) || "REJECTED".equals(current.status())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Finalized proposal cannot be enriched");
            }
            jdbc.update("""
                    INSERT INTO bureau_consultation
                    (id, proposal_id, provider, environment, status, response_version,
                     report_json, report_sha256, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?)
                    """, id, proposalId, stored.provider(), stored.environment(), stored.status(),
                    stored.responseVersion(), serialized, sha256(serialized),
                    OffsetDateTime.now(ZoneOffset.UTC));
        });
        return new Consultation(id, stored);
    }

    public Snapshot latest(UUID proposalId) {
        proposals.get(proposalId);
        try {
            return jdbc.queryForObject("""
                    SELECT id, report_json::text FROM bureau_consultation
                    WHERE proposal_id = ? ORDER BY created_at DESC, id DESC LIMIT 1
                    """, (rs, row) -> {
                try {
                    return new Snapshot(rs.getObject(1, UUID.class),
                            json.readValue(rs.getString(2), BureauReport.class));
                } catch (JsonProcessingException ex) {
                    throw new IllegalStateException("Stored bureau report is invalid", ex);
                }
            }, proposalId);
        } catch (EmptyResultDataAccessException ex) {
            return new Snapshot(null, new BureauReport("NONE", "CREDIT_BUREAU", "MOCK",
                    "NOT_REQUESTED", null, null, null, null, null,
                    List.of("Nenhuma consulta de bureau foi solicitada.")));
        }
    }

    private String serialize(BureauReport report) {
        try {
            return json.writeValueAsString(report);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Bureau report serialization failed", ex);
        }
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    public record Consultation(UUID consultationId, BureauReport report) {}
    public record Snapshot(UUID id, BureauReport report) {}
}
