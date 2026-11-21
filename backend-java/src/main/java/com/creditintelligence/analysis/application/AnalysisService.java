package com.creditintelligence.analysis.application;

import com.creditintelligence.analysis.domain.IncomeConsistencyRule;
import com.creditintelligence.analysis.domain.FinancialEngine;
import com.creditintelligence.analysis.domain.FinancialEngine.Scenario;
import com.creditintelligence.bureau.application.BureauService;
import com.creditintelligence.analysis.infrastructure.AnalysisDataStore;
import com.creditintelligence.analysis.infrastructure.AnalysisEntity;
import com.creditintelligence.analysis.infrastructure.AnalysisRepository;
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
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.client.RestClientException;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class AnalysisService {
    private final TransactionTemplate transactions;
    private final AnalysisRepository analyses;
    private final AnalysisDataStore data;
    private final ProposalService proposals;
    private final DocumentService documents;
    private final PolicyService policies;
    private final PythonDocumentClient python;
    private final AIExplanationService explanations;
    private final JdbcTemplate jdbc;
    private final BureauService bureau;
    private final IncomeConsistencyRule incomeRule = new IncomeConsistencyRule();
    private final FinancialEngine financialEngine = new FinancialEngine();

    public AnalysisService(TransactionTemplate transactions, AnalysisRepository analyses,
            AnalysisDataStore data, ProposalService proposals, DocumentService documents,
            PolicyService policies, PythonDocumentClient python, AIExplanationService explanations,
            JdbcTemplate jdbc, BureauService bureau) {
        this.transactions = transactions;
        this.analyses = analyses;
        this.data = data;
        this.proposals = proposals;
        this.documents = documents;
        this.policies = policies;
        this.python = python;
        this.explanations = explanations;
        this.jdbc = jdbc;
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
            var analysis = analyses.findByIdAndProposalId(analysisId, proposalId)
                    .orElseThrow(() -> new NotFoundException("Analysis not found"));
            var proposal = proposals.get(proposalId);
            var policy = policies.get(analysis.getPolicyVersionId());
            var explanation = explanations.get(analysisId);
            return new AnalysisView(analysis.getId(), proposalId, analysis.getStatus(), analysis.getAttempt(),
                    analysis.getDocumentSnapshotHash().trim(), policy.version(), policy.checksum(),
                    proposal.declaredMonthlyIncome(), proposal.currency(), policy.incomeTolerancePercent(),
                    policy.requiredDocumentTypes(), data.snapshot(analysisId),
                    data.fields(analysisId), data.findings(analysisId), explanation.status(), explanation,
                    analysis.getErrorCode(), analysis.getStartedAt(), analysis.getCompletedAt(),
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
            var bindings = jdbc.query("""
                    SELECT input_fingerprint, analysis_id FROM analysis_request_key
                    WHERE proposal_id = ? AND request_key = ?
                    """, (rs, row) -> new RequestBinding(rs.getString(1).trim(),
                    rs.getObject(2, UUID.class)), proposalId, idempotencyKey);
            if (!bindings.isEmpty()) {
                var binding = bindings.getFirst();
                if (!fingerprint.equals(binding.fingerprint())) {
                    throw new ConflictException("Idempotency-Key belongs to a different input");
                }
                var bound = analyses.findById(binding.analysisId())
                        .orElseThrow(() -> new IllegalStateException("Idempotency binding without analysis"));
                return reuseOrRetry(bound, snapshot, policy, scenario, financial, bureauSnapshot);
            }
        }
        var existing = analyses.findByProposalIdAndInputFingerprint(proposalId, fingerprint);
        if (existing.isPresent()) {
            if (idempotencyKey != null) bindKey(proposalId, idempotencyKey, fingerprint, existing.get().getId());
            return reuseOrRetry(existing.get(), snapshot, policy, scenario, financial, bureauSnapshot);
        }
        var id = UUID.randomUUID();
        var lease = UUID.randomUUID();
        analyses.saveAndFlush(new AnalysisEntity(id, proposalId, lease, fingerprint, snapshotHash,
                policy.id(), OffsetDateTime.now(ZoneOffset.UTC)));
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

    private Start reuseOrRetry(AnalysisEntity analysis, List<DocumentSnapshotRef> snapshot,
            PolicySnapshot policy, Scenario scenario, boolean financial,
            BureauService.Snapshot bureauSnapshot) {
        var expired = "RUNNING".equals(analysis.getStatus())
                && Duration.between(analysis.getStartedAt(), OffsetDateTime.now(ZoneOffset.UTC)).toSeconds() >= 60;
        if ("FAILED_RETRYABLE".equals(analysis.getStatus()) || expired) {
            var lease = UUID.randomUUID();
            analysis.retry(lease, OffsetDateTime.now(ZoneOffset.UTC));
            analyses.saveAndFlush(analysis);
            return new Start(analysis.getId(), lease, snapshot, policy, scenario,
                    financial, bureauSnapshot, true);
        }
        return new Start(analysis.getId(), analysis.getLeaseToken(), snapshot, policy,
                scenario, financial, bureauSnapshot, false);
    }

    private void bindKey(UUID proposalId, String key, String fingerprint, UUID analysisId) {
        jdbc.update("""
                INSERT INTO analysis_request_key
                (proposal_id, request_key, input_fingerprint, analysis_id, created_at)
                VALUES (?, ?, ?, ?, ?)
                """, proposalId, key, fingerprint, analysisId, OffsetDateTime.now(ZoneOffset.UTC));
    }

    private void complete(Start start, PythonDocumentClient.ProcessResponse response) {
        var analysis = runningAttempt(start);
        var extraction = validateResponse(start, response);
        var fields = extraction.fields();
        for (var field : fields) data.saveField(start.id(), field);
        if (start.financial()) {
            var profile = assembleFinancialProfile(start, extraction);
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
            var declaredResult = incomeRule.evaluate(proposals.get(analysis.getProposalId())
                    .declaredMonthlyIncome(), assessment.averageNetIncome(),
                    start.policy().incomeTolerancePercent(),
                    profile.incomePeriods().stream().map(FinancialEngine.IncomePeriod::netEvidenceRef)
                            .filter(Objects::nonNull).findFirst().orElse(null));
            data.saveFinding(start.id(), new FindingView(IncomeConsistencyRule.CODE,
                    IncomeConsistencyRule.VERSION, declaredResult.result(), declaredResult.details(),
                    declaredResult.evidenceRef()));
            analysis.complete(OffsetDateTime.now(ZoneOffset.UTC));
            return;
        }
        var income = fields.stream().filter(field -> "monthlyIncome".equals(field.fieldName()))
                .findFirst().orElse(null);
        var factualIncome = income != null && "EXTRACTED".equals(income.extractionStatus())
                ? new BigDecimal(income.value()) : null;
        var proposal = proposals.get(analysis.getProposalId());
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
        analysis.complete(OffsetDateTime.now(ZoneOffset.UTC));
    }

    private ValidatedExtraction validateResponse(Start start, PythonDocumentClient.ProcessResponse response) {
        if (response == null || !(start.financial() ? "2.0" : "1.0").equals(response.contractVersion())
                || !start.id().equals(response.analysisId())
                || !start.leaseToken().equals(response.attemptId())
                || response.documentResults() == null
                || response.documentResults().size() != start.documents().size()) {
            throw new InvalidExtractionException("Invalid extraction contract");
        }
        Set<UUID> seen = new HashSet<>();
        List<FieldView> fields = new ArrayList<>();
        List<FinancialEngine.BankTransaction> bankTransactions = new ArrayList<>();
        for (var documentResult : response.documentResults()) {
            if (documentResult == null || documentResult.fields() == null
                    || !seen.add(documentResult.documentId())) {
                throw new InvalidExtractionException("Duplicate or missing document result");
            }
            var bound = start.documents().stream()
                    .filter(document -> document.id().equals(documentResult.documentId())
                            && document.version() == documentResult.version())
                    .findFirst().orElseThrow(() -> new InvalidExtractionException("Document outside snapshot"));
            for (var field : documentResult.fields()) fields.add(validateField(bound, field));
            if (documentResult.transactions() != null) {
                for (var item : documentResult.transactions()) {
                    bankTransactions.add(validateTransaction(bound, item));
                }
            }
        }
        if (bankTransactions.stream().map(FinancialEngine.BankTransaction::id).distinct().count()
                != bankTransactions.size()) throw new InvalidExtractionException("Duplicate transaction ID");
        return new ValidatedExtraction(fields, bankTransactions);
    }

    private FieldView validateField(DocumentSnapshotRef bound, PythonDocumentClient.ExtractedField field) {
        if (field == null || !bound.id().equals(field.documentId()) || bound.version() != field.version()
                || !allowedField(bound, field.fieldName())
                || !Set.of("EXTRACTED", "MISSING", "AMBIGUOUS", "INVALID").contains(field.extractionStatus())) {
            throw new InvalidExtractionException("Field outside snapshot or schema");
        }
        if ("EXTRACTED".equals(field.extractionStatus())) {
            var expectedRef = bound.id() + ":v" + bound.version() + ":p" + field.page()
                    + ":" + field.fieldName();
            var monetary = Set.of("monthlyIncome", "grossSalary", "netSalary",
                    "payrollDeduction").contains(field.fieldName());
            if (field.value() == null || (monetary && !"BRL".equals(field.currency()))
                    || (!monetary && field.currency() != null) || field.page() == null
                    || field.page() < 1 || field.snippet() == null || field.snippet().isBlank()
                    || !expectedRef.equals(field.evidenceRef())) {
                throw new InvalidExtractionException("Invalid income evidence");
            }
            try {
                if (monetary && new BigDecimal(field.value()).signum() < 0) {
                    throw new NumberFormatException();
                }
                if (!monetary && !field.value().equals(bound.period())) {
                    throw new InvalidExtractionException("Document period differs from metadata");
                }
            } catch (NumberFormatException ex) {
                throw new InvalidExtractionException("Invalid monetary value");
            }
        } else if (field.value() != null || field.evidenceRef() != null) {
            throw new InvalidExtractionException("Uncertain field cannot contain fact or evidence");
        }
        return new FieldView(field.documentId(), field.version(), field.fieldName(), field.value(),
                field.currency(), field.extractionStatus(), field.evidenceRef(), field.page(), field.snippet());
    }

    private boolean allowedField(DocumentSnapshotRef bound, String name) {
        return switch (bound.type()) {
            case INCOME_PROOF -> "monthlyIncome".equals(name);
            case PAYSLIP -> Set.of("competence", "grossSalary", "netSalary",
                    "payrollDeduction").contains(name);
            case BANK_STATEMENT -> "statementPeriod".equals(name);
            default -> false;
        };
    }

    private FinancialEngine.BankTransaction validateTransaction(DocumentSnapshotRef bound,
            PythonDocumentClient.ExtractedTransaction item) {
        if (item == null || bound.type() != com.creditintelligence.document.domain.DocumentType.BANK_STATEMENT
                || !bound.id().equals(item.documentId()) || bound.version() != item.version()
                || item.page() == null || item.page() < 1 || item.transactionId() == null
                || !item.transactionId().matches("tx[1-9][0-9]*") || item.snippet() == null
                || item.snippet().isBlank() || item.descriptionOriginal() == null
                || item.descriptionOriginal().isBlank() || !"BRL".equals(item.currency())
                || !Set.of("CREDIT", "DEBIT").contains(item.direction())
                || !Set.of("POSSIBLE_SALARY", "RECURRING_PAYMENT", "UNKNOWN")
                        .contains(item.classification())
                || !(bound.id() + ":v" + bound.version() + ":p" + item.page() + ":"
                        + item.transactionId()).equals(item.evidenceRef())) {
            throw new InvalidExtractionException("Invalid bank transaction evidence");
        }
        try {
            var amount = new BigDecimal(item.amount());
            var date = LocalDate.parse(item.postedAt());
            if (amount.signum() < 0 || !item.postedAt().startsWith(bound.period())
                    || !date.toString().equals(item.postedAt())) {
                throw new InvalidExtractionException("Invalid bank transaction value or period");
            }
            return new FinancialEngine.BankTransaction(bound.id() + ":" + item.transactionId(),
                    bound.period(), item.postedAt(), item.direction(), amount,
                    item.classification(), item.evidenceRef(), item.descriptionOriginal());
        } catch (NumberFormatException | java.time.DateTimeException ex) {
            throw new InvalidExtractionException("Invalid bank transaction value or date");
        }
    }

    private FinancialEngine.FinancialProfile assembleFinancialProfile(Start start,
            ValidatedExtraction extraction) {
        List<FinancialEngine.IncomePeriod> incomes = new ArrayList<>();
        List<String> limitations = new ArrayList<>();
        for (var document : start.documents()) {
            if (document.type() != com.creditintelligence.document.domain.DocumentType.PAYSLIP) continue;
            var own = extraction.fields().stream().filter(f -> f.documentId().equals(document.id())).toList();
            var net = own.stream().filter(f -> "netSalary".equals(f.fieldName())).findFirst().orElse(null);
            var gross = own.stream().filter(f -> "grossSalary".equals(f.fieldName())).findFirst().orElse(null);
            var deduction = own.stream().filter(f -> "payrollDeduction".equals(f.fieldName()))
                    .findFirst().orElse(null);
            if (net == null || !"EXTRACTED".equals(net.extractionStatus())) {
                limitations.add("Net salary not factual for period " + document.period());
            }
            incomes.add(new FinancialEngine.IncomePeriod(document.period(), monetaryValue(gross),
                    monetaryValue(net), monetaryValue(deduction), net == null ? null : net.evidenceRef(),
                    deduction == null ? null : deduction.evidenceRef()));
        }
        for (var document : start.documents()) {
            if (document.type() != com.creditintelligence.document.domain.DocumentType.BANK_STATEMENT) continue;
            var period = extraction.fields().stream().filter(f -> f.documentId().equals(document.id())
                    && "statementPeriod".equals(f.fieldName())).findFirst().orElse(null);
            if (period == null || !"EXTRACTED".equals(period.extractionStatus())) {
                limitations.add("Statement period not factual for " + document.period());
            }
        }
        return new FinancialEngine.FinancialProfile(incomes, extraction.transactions(), limitations,
                start.bureauSnapshot().report());
    }

    private BigDecimal monetaryValue(FieldView field) {
        return field == null || !"EXTRACTED".equals(field.extractionStatus())
                ? null : new BigDecimal(field.value());
    }

    private AnalysisEntity runningAttempt(Start start) {
        var analysis = analyses.findForUpdate(start.id()).orElseThrow(() -> new NotFoundException("Analysis not found"));
        if (!"RUNNING".equals(analysis.getStatus()) || !start.leaseToken().equals(analysis.getLeaseToken())) {
            throw new ConflictException("Analysis attempt is no longer current");
        }
        return analysis;
    }

    private void fail(Start start, String failureStatus, String errorCode) {
        var analysis = analyses.findForUpdate(start.id()).orElse(null);
        if (analysis != null && "RUNNING".equals(analysis.getStatus())
                && start.leaseToken().equals(analysis.getLeaseToken())) {
            analysis.fail(failureStatus, errorCode, OffsetDateTime.now(ZoneOffset.UTC));
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
    private record ValidatedExtraction(List<FieldView> fields,
            List<FinancialEngine.BankTransaction> transactions) {}
    private record RequestBinding(String fingerprint, UUID analysisId) {}
}
