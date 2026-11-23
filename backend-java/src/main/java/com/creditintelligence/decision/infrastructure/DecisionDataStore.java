package com.creditintelligence.decision.infrastructure;

import com.creditintelligence.decision.application.DecisionDataPort;
import com.creditintelligence.decision.domain.DecisionOutcome;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class DecisionDataStore implements DecisionDataPort {
    private final DecisionRepository repository;
    private final JdbcTemplate jdbc;

    public DecisionDataStore(DecisionRepository repository, JdbcTemplate jdbc) {
        this.repository = repository;
        this.jdbc = jdbc;
    }
    public boolean existsByAnalysisId(UUID analysisId) { return repository.existsByAnalysisId(analysisId); }
    public DecisionRecord save(DecisionRecord decision) {
        var saved = repository.saveAndFlush(new DecisionEntity(decision.id(), decision.proposalId(),
                decision.analysisId(), decision.analystId(), decision.outcome().name(), decision.reason(),
                decision.snapshotHash(), decision.createdAt()));
        return toRecord(saved);
    }
    public List<DecisionRecord> list(UUID proposalId) {
        return repository.findByProposalIdOrderByCreatedAtAsc(proposalId).stream().map(this::toRecord).toList();
    }
    public void audit(UUID proposalId, UUID analysisId, DecisionOutcome outcome, String actor,
            String correlationId, OffsetDateTime createdAt) {
        jdbc.update("""
                INSERT INTO audit_record
                (id, aggregate_type, aggregate_id, action, actor_id, correlation_id, metadata_json, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), "PROPOSAL", proposalId, "DECISION_RECORDED", actor, correlationId,
                "{\"analysisId\":\"" + analysisId + "\",\"outcome\":\"" + outcome.name() + "\"}", createdAt);
    }
    private DecisionRecord toRecord(DecisionEntity entity) {
        return new DecisionRecord(entity.getId(), entity.getProposalId(), entity.getAnalysisId(), entity.getAnalystId(),
                DecisionOutcome.valueOf(entity.getOutcome()), entity.getReason(), entity.getDocumentSnapshotHash(),
                entity.getCreatedAt());
    }
}
