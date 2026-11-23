package com.creditintelligence.analysis.application;

import com.creditintelligence.analysis.domain.IncomeConsistencyRule;
import com.creditintelligence.analysis.domain.FinancialEngine;
import com.creditintelligence.analysis.domain.FinancialEngine.Scenario;
import com.creditintelligence.bureau.application.BureauService;
import com.creditintelligence.document.application.DocumentService;
import com.creditintelligence.document.application.DocumentSnapshotRef;
import com.creditintelligence.integration.documentintelligence.PythonDocumentClient;
import com.creditintelligence.policy.application.PolicyService;
import com.creditintelligence.policy.application.PolicySnapshot;
import com.creditintelligence.proposal.application.ProposalService;
import com.creditintelligence.shared.errors.ConflictException;
import com.creditintelligence.shared.errors.NotFoundException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.Duration;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.client.RestClientException;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class AnalysisService {
    private final TransactionTemplate transactions;
    private final AnalysisLifecyclePort lifecycle;
    private final AnalysisDataPort data;
    private final ProposalService proposals;
    private final DocumentService documents;
    private final PolicyService policies;
    private final PythonDocumentClient python;
    private final AIExplanationService explanations;
    private final BureauService bureau;
    private final IncomeConsistencyRule incomeRule = new IncomeConsistencyRule();
    private final FinancialEngine financialEngine = new FinancialEngine();
    private final ExtractionContractValidator extractionValidator = new ExtractionContractValidator();
    private final FinancialProfileAssembler profileAssembler = new FinancialProfileAssembler();

    public AnalysisService(TransactionTemplate transactions, AnalysisLifecyclePort lifecycle,
            AnalysisDataPort data, ProposalService proposals, DocumentService documents,
            PolicyService policies, PythonDocumentClient python, AIExplanationService explanations,
            BureauService bureau) {
        this.transactions = transactions;
        this.lifecycle = lifecycle;
        this.data = data;
        this.proposals = proposals;
        this.documents = documents;
        this.policies = policies;
        this.python = python;
        this.explanations = explanations;
        this.bureau = bureau;
    }

    public AnalysisView analyze(UUID proposalId, String correlationId, String idempotencyKey) {
        return analyze(proposalId, correlationId, idempotencyKey, null);
    }

    public AnalysisView analyze(UUID proposalId, String correlationId, String idempotencyKey,
            Scenario requestedScenario) {
        if (idempotencyKey != null && !idempotencyKey.matches("[A-Za-z0-9._:-]{8,120}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid Idempotency-Key");
        }
        var start = Objects.requireNonNull(transactions.execute(status ->
                start(proposalId, idempotencyKey, requestedScenario)));
        if (!start.fresh()) return dossier(proposalId, start.id());
        var completed = false;
        try {
            var response = python.process(start.id(), start.leaseToken(), start.documents(),
                    start.policy().version(), correlationId);
            transactions.executeWithoutResult(status -> complete(start, response));
            completed = true;
        } catch (InvalidExtractionException ex) {
            transactions.executeWithoutResult(status -> fail(start, "FAILED_PERMANENT", "INVALID_EXTRACTION_RESPONSE"));
        } catch (RestClientException ex) {
            transactions.executeWithoutResult(status -> fail(start, "FAILED_RETRYABLE", "DOCUMENT_INTELLIGENCE_UNAVAILABLE"));
        } catch (ConflictException ex) {
            // Another request acquired a newer lease before this attempt completed.
        }
        if (completed) explanations.generate(start.id(), start.policy(), correlationId);
        return dossier(proposalId, start.id());
    }

    public AnalysisView dossier(UUID proposalId, UUID analysisId) {
        return Objects.requireNonNull(transactions.execute(status -> {
            var analysis = lifecycle.findByIdAndProposal(analysisId, proposalId)
                    .orElseThrow(() -> new NotFoundException("Analysis not found"));
            var proposal = proposals.get(proposalId);
            var policy = policies.get(analysis.policyVersionId());
            var explanation = explanations.get(analysisId);
            return new AnalysisView(analysis.id(), proposalId, analysis.status(), analysis.attempt(),
                    analysis.snapshotHash().trim(), policy.version(), policy.checksum(),
                    proposal.declaredMonthlyIncome(), proposal.currency(), policy.incomeTolerancePercent(),
                    policy.requiredDocumentTypes(), data.snapshot(analysisId),
                    data.fields(analysisId), data.findings(analysisId), explanation.status(), explanation,
                    analysis.errorCode(), analysis.startedAt(), analysis.completedAt(),
                    data.financialAssessment(analysisId));
        }));
    }

    private Start start(UUID proposalId, String idempotencyKey, Scenario requestedScenario) {
        var proposal = proposals.lockForMutation(proposalId);
        if ("APPROVED".equals(proposal.status()) || "REJECTED".equals(proposal.status())) {
            throw new ConflictException("Finalized proposal cannot start a new analysis");
        }
        var snapshot = documents.snapshot(proposalId);
        var financial = snapshot.stream().anyMatch(document -> document.period() != null);
        var bureauSnapshot = financial ? bureau.latest(proposalId) : null;
        if (financial && !hasCompleteFinancialCoverage(snapshot)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Financial analysis requires three matching payslip and bank statement periods");
        }
        if (financial && (bureauSnapshot == null || bureauSnapshot.id() == null
                || !"AVAILABLE".equals(bureauSnapshot.report().status()))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A successful bureau consultation is required before financial analysis");
        }
        var policy = financial ? policies.financial() : policies.current();
        if (!financial && requestedScenario != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Loan scenario requires financial documents");
        }
        var scenario = financial ? requestedScenario == null
                ? new Scenario(null, policy.simulationTermMonths(), policy.simulationMonthlyInterestRate(),
                        "BRL", "MANUAL_DEMO") : requestedScenario : null;
        if (scenario != null && (scenario.termMonths() < 1 || scenario.termMonths() > 600
                || scenario.monthlyInterestRate() == null || scenario.monthlyInterestRate().signum() < 0
                || scenario.monthlyInterestRate().compareTo(BigDecimal.ONE) > 0
                || !"BRL".equals(scenario.currency())
                || scenario.principalRequested() != null && scenario.principalRequested().signum() < 0)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid loan scenario");
        }
        var snapshotText = snapshot.stream()
                .sorted(Comparator.comparing(document -> document.type().name() + ":" + document.period()))
                .map(document -> document.id() + ":" + document.version() + ":" + document.sha256()
                        + (financial ? ":" + document.period() : ""))
                .reduce("", (left, right) -> left + "\n" + right);
        var snapshotHash = sha256(snapshotText);
        var fingerprint = sha256(proposalId + ":" + proposal.declaredMonthlyIncome() + ":"
                + policy.checksum() + ":" + snapshotHash + (scenario == null ? ""
                        : ":" + scenario.principalRequested() + ":" + scenario.termMonths() + ":"
                                + scenario.monthlyInterestRate().stripTrailingZeros().toPlainString()
                                + ":" + scenario.currency() + ":" + scenario.pricingSource()
                                + (bureauSnapshot.id() == null ? ""
                                        : ":bureau:" + bureauSnapshot.id())));
        if (idempotencyKey != null) {
            var binding = lifecycle.binding(proposalId, idempotencyKey);
            if (binding.isPresent()) {
                var boundKey = binding.get();
                if (!fingerprint.equals(boundKey.fingerprint())) {
                    throw new ConflictException("Idempotency-Key belongs to a different input");
                }
                var bound = lifecycle.findById(boundKey.analysisId())
                        .orElseThrow(() -> new IllegalStateException("Idempotency binding without analysis"));
                return reuseOrRetry(bound, snapshot, policy, scenario, financial, bureauSnapshot);
            }
        }
        var existing = lifecycle.findByFingerprint(proposalId, fingerprint);
        if (existing.isPresent()) {
            if (idempotencyKey != null) bindKey(proposalId, idempotencyKey, fingerprint, existing.get().id());
            return reuseOrRetry(existing.get(), snapshot, policy, scenario, financial, bureauSnapshot);
        }
        var id = UUID.randomUUID();
        var lease = UUID.randomUUID();
        lifecycle.create(id, proposalId, lease, fingerprint, snapshotHash, policy.id(), OffsetDateTime.now(ZoneOffset.UTC));
        data.saveSnapshot(id, snapshot);
        if (idempotencyKey != null) bindKey(proposalId, idempotencyKey, fingerprint, id);
        return new Start(id, lease, snapshot, policy, scenario, financial, bureauSnapshot, true);
    }

    private boolean hasCompleteFinancialCoverage(List<DocumentSnapshotRef> snapshot) {
        var payslips = new HashSet<String>();
        var statements = new HashSet<String>();
        for (var document : snapshot) {
            if (document.period() == null) continue;
            if (document.type().name().equals("PAYSLIP")) payslips.add(document.period());
            if (document.type().name().equals("BANK_STATEMENT")) statements.add(document.period());
        }
        return payslips.size() == 3 && statements.size() == 3 && payslips.equals(statements);
    }

    private Start reuseOrRetry(AnalysisLifecyclePort.AnalysisRecord analysis, List<DocumentSnapshotRef> snapshot,
            PolicySnapshot policy, Scenario scenario, boolean financial,
            BureauService.Snapshot bureauSnapshot) {
        var expired = "RUNNING".equals(analysis.status())
                && Duration.between(analysis.startedAt(), OffsetDateTime.now(ZoneOffset.UTC)).toSeconds() >= 60;
        if ("FAILED_RETRYABLE".equals(analysis.status()) || expired) {
            var lease = UUID.randomUUID();
            lifecycle.retry(analysis, lease, OffsetDateTime.now(ZoneOffset.UTC));
            return new Start(analysis.id(), lease, snapshot, policy, scenario,
                    financial, bureauSnapshot, true);
        }
        return new Start(analysis.id(), analysis.leaseToken(), snapshot, policy,
                scenario, financial, bureauSnapshot, false);
    }

    private void bindKey(UUID proposalId, String key, String fingerprint, UUID analysisId) {
        lifecycle.bind(proposalId, key, fingerprint, analysisId, OffsetDateTime.now(ZoneOffset.UTC));
    }

    private void complete(Start start, PythonDocumentClient.ProcessResponse response) {
        var analysis = runningAttempt(start);
        var extraction = extractionValidator.validate(response, start.id(), start.leaseToken(),
                start.financial(), start.documents());
        var fields = extraction.fields();
        for (var field : fields) data.saveField(start.id(), field);
        if (start.financial()) {
            var profile = profileAssembler.assemble(start.documents(), extraction, start.bureauSnapshot().report());
            var assessment = financialEngine.assess(profile,
                    new FinancialEngine.Policy(start.policy().commitmentLimitRatio(),
                            start.policy().salaryMatchTolerancePercent(),
                            start.policy().incomeStabilityWarningRatio(), start.policy().version()),
                    start.scenario());
            data.saveFinancialAssessment(start.id(), start.policy().id(),
                    start.bureauSnapshot().id(), profile, assessment);
            for (var finding : assessment.findings()) {
                data.saveFinding(start.id(), new FindingView(finding.code(), FinancialEngine.RULE_VERSION,
                        finding.result(), finding.details(),
                        finding.evidenceRefs().isEmpty() ? null : finding.evidenceRefs().getFirst()));
            }
            var completeFactualIncome = profile.incomePeriods().size() == 3
                    && profile.incomePeriods().stream().map(FinancialEngine.IncomePeriod::period).distinct().count() == 3
                    && profile.incomePeriods().stream().allMatch(income -> income.net() != null);
            var declaredResult = incomeRule.evaluate(proposals.get(analysis.proposalId())
                    .declaredMonthlyIncome(), completeFactualIncome ? assessment.averageNetIncome() : null,
                    start.policy().incomeTolerancePercent(),
                    profile.incomePeriods().stream().map(FinancialEngine.IncomePeriod::netEvidenceRef)
                            .filter(Objects::nonNull).findFirst().orElse(null));
            data.saveFinding(start.id(), new FindingView(IncomeConsistencyRule.CODE,
                    IncomeConsistencyRule.VERSION, declaredResult.result(), declaredResult.details(),
                    declaredResult.evidenceRef()));
            lifecycle.complete(start.id(), start.leaseToken(), OffsetDateTime.now(ZoneOffset.UTC));
            return;
        }
        var income = fields.stream().filter(field -> "monthlyIncome".equals(field.fieldName()))
                .findFirst().orElse(null);
        var factualIncome = income != null && "EXTRACTED".equals(income.extractionStatus())
                ? new BigDecimal(income.value()) : null;
        var proposal = proposals.get(analysis.proposalId());
        var result = incomeRule.evaluate(proposal.declaredMonthlyIncome(), factualIncome,
                start.policy().incomeTolerancePercent(), income == null ? null : income.evidenceRef());
        data.saveFinding(start.id(), new FindingView(IncomeConsistencyRule.CODE,
                IncomeConsistencyRule.VERSION, result.result(), result.details(), result.evidenceRef()));
        var presentTypes = start.documents().stream().map(document -> document.type().name()).collect(
                java.util.stream.Collectors.toSet());
        var missingTypes = start.policy().requiredDocumentTypes().stream()
                .filter(required -> !presentTypes.contains(required)).toList();
        data.saveFinding(start.id(), new FindingView("REQUIRED_DOCUMENTS", "1.0",
                missingTypes.isEmpty() ? "PASS" : "FAIL",
                missingTypes.isEmpty() ? "Required documents present" : "Missing: " + missingTypes, null));
        lifecycle.complete(start.id(), start.leaseToken(), OffsetDateTime.now(ZoneOffset.UTC));
    }

    private AnalysisLifecyclePort.AnalysisRecord runningAttempt(Start start) {
        var analysis = lifecycle.lock(start.id()).orElseThrow(() -> new NotFoundException("Analysis not found"));
        if (!"RUNNING".equals(analysis.status()) || !start.leaseToken().equals(analysis.leaseToken())) {
            throw new ConflictException("Analysis attempt is no longer current");
        }
        return analysis;
    }

    private void fail(Start start, String failureStatus, String errorCode) {
        var analysis = lifecycle.lock(start.id()).orElse(null);
        if (analysis != null && "RUNNING".equals(analysis.status())
                && start.leaseToken().equals(analysis.leaseToken())) {
            lifecycle.fail(start.id(), start.leaseToken(), failureStatus, errorCode, OffsetDateTime.now(ZoneOffset.UTC));
        }
    }

    private String sha256(String value) {
        try {
            var bytes = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    private record Start(UUID id, UUID leaseToken, List<DocumentSnapshotRef> documents,
            PolicySnapshot policy, Scenario scenario, boolean financial,
            BureauService.Snapshot bureauSnapshot, boolean fresh) {}
    public UUID bureauConsultationId(UUID analysisId) {
        return data.bureauConsultationId(analysisId);
    }
}
