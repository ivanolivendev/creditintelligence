package com.creditintelligence.analysis.application;

import com.creditintelligence.analysis.domain.FinancialEngine;
import com.creditintelligence.document.application.DocumentSnapshotRef;
import java.util.List;
import java.util.UUID;

/** Persistence boundary used by analysis application services. */
public interface AnalysisDataPort {
    void saveFinancialAssessment(UUID analysisId, UUID policyId, UUID bureauConsultationId,
            FinancialEngine.FinancialProfile profile, FinancialEngine.Assessment assessment);

    FinancialEngine.Assessment financialAssessment(UUID analysisId);

    List<String> transactionReferences(UUID analysisId);

    UUID bureauConsultationId(UUID analysisId);

    void saveSnapshot(UUID analysisId, List<DocumentSnapshotRef> documents);

    void saveField(UUID analysisId, FieldView field);

    void saveFinding(UUID analysisId, FindingView finding);

    List<SnapshotView> snapshot(UUID analysisId);

    List<FieldView> fields(UUID analysisId);

    List<FindingView> findings(UUID analysisId);
}
