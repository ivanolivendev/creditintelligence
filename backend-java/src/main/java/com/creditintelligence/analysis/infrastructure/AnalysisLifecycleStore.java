package com.creditintelligence.analysis.infrastructure;

import com.creditintelligence.analysis.application.AnalysisLifecyclePort;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AnalysisLifecycleStore implements AnalysisLifecyclePort {
    private final AnalysisRepository repository;
    private final JdbcTemplate jdbc;

    public AnalysisLifecycleStore(AnalysisRepository repository, JdbcTemplate jdbc) {
        this.repository = repository;
        this.jdbc = jdbc;
    }

    public Optional<AnalysisRecord> findByIdAndProposal(UUID id, UUID proposalId) {
        return repository.findByIdAndProposalId(id, proposalId).map(this::toRecord);
    }

    public Optional<AnalysisRecord> findById(UUID id) {
        return repository.findById(id).map(this::toRecord);
    }

    public Optional<AnalysisRecord> findByFingerprint(UUID proposalId, String fingerprint) {
        return repository.findByProposalIdAndInputFingerprint(proposalId, fingerprint).map(this::toRecord);
    }

    public Optional<AnalysisRecord> lock(UUID id) {
        return repository.findForUpdate(id).map(this::toRecord);
    }

    public AnalysisRecord create(UUID id, UUID proposalId, UUID lease, String fingerprint, String snapshotHash,
            UUID policyId, OffsetDateTime startedAt) {
        var entity = new AnalysisEntity(id, proposalId, lease, fingerprint, snapshotHash, policyId, startedAt);
        return toRecord(repository.saveAndFlush(entity));
    }

    public AnalysisRecord retry(AnalysisRecord record, UUID lease, OffsetDateTime startedAt) {
        var entity = repository.findById(record.id()).orElseThrow();
        entity.retry(lease, startedAt);
        return toRecord(repository.saveAndFlush(entity));
    }

    public void complete(UUID id, UUID lease, OffsetDateTime at) {
        var entity = current(id, lease);
        entity.complete(at);
        repository.saveAndFlush(entity);
    }

    public void fail(UUID id, UUID lease, String status, String code, OffsetDateTime at) {
        var entity = current(id, lease);
        entity.fail(status, code, at);
        repository.saveAndFlush(entity);
    }

    public Optional<RequestBinding> binding(UUID proposalId, String key) {
        return jdbc.query("SELECT input_fingerprint, analysis_id FROM analysis_request_key "
                        + "WHERE proposal_id = ? AND request_key = ?",
                (rs, row) -> new RequestBinding(rs.getString(1).trim(), rs.getObject(2, UUID.class)),
                proposalId, key).stream().findFirst();
    }

    public void bind(UUID proposalId, String key, String fingerprint, UUID analysisId, OffsetDateTime createdAt) {
        jdbc.update("INSERT INTO analysis_request_key "
                        + "(proposal_id, request_key, input_fingerprint, analysis_id, created_at) "
                        + "VALUES (?, ?, ?, ?, ?)",
                proposalId, key, fingerprint, analysisId, createdAt);
    }

    private AnalysisEntity current(UUID id, UUID lease) {
        var entity = repository.findForUpdate(id).orElseThrow();
        if (!lease.equals(entity.getLeaseToken())) {
            throw new IllegalStateException("Analysis attempt is no longer current");
        }
        return entity;
    }

    private AnalysisRecord toRecord(AnalysisEntity entity) {
        return new AnalysisRecord(entity.getId(), entity.getProposalId(), entity.getStatus(), entity.getAttempt(),
                entity.getLeaseToken(), entity.getInputFingerprint(), entity.getDocumentSnapshotHash(),
                entity.getPolicyVersionId(), entity.getStartedAt(), entity.getCompletedAt(), entity.getErrorCode());
    }
}
