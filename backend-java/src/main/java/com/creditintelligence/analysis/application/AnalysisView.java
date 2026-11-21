package com.creditintelligence.analysis.application;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import com.creditintelligence.analysis.domain.FinancialEngine.Assessment;

public record AnalysisView(UUID analysisId, UUID proposalId, String status, int attempt,
        String documentSnapshotHash, String policyVersion, String policyChecksum,
        BigDecimal declaredMonthlyIncome, String currency, BigDecimal incomeTolerancePercent,
        List<String> requiredDocumentTypes, List<SnapshotView> documents,
        List<FieldView> extractedFields, List<FindingView> findings,
        String aiExplanationStatus, AIExplanationView aiExplanation,
        String errorCode, OffsetDateTime startedAt,
        OffsetDateTime completedAt, Assessment financialAssessment) {}
