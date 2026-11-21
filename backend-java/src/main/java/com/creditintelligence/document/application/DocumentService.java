package com.creditintelligence.document.application;

import com.creditintelligence.document.domain.DocumentType;
import com.creditintelligence.document.infrastructure.DocumentEntity;
import com.creditintelligence.document.infrastructure.DocumentRepository;
import com.creditintelligence.proposal.application.ProposalService;
import com.creditintelligence.shared.errors.InvalidDocumentException;
import com.creditintelligence.shared.errors.PayloadTooLargeException;
import com.creditintelligence.shared.errors.ConflictException;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.LinkedHashMap;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.stream.Collectors;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class DocumentService {
    private static final int MAX_BYTES = 10 * 1024 * 1024;
    private final ProposalService proposals;
    private final DocumentRepository documents;
    private final DocumentStorage storage;

    public DocumentService(ProposalService proposals, DocumentRepository documents, DocumentStorage storage) {
        this.proposals = proposals;
        this.documents = documents;
        this.storage = storage;
    }

    @Transactional
    public DocumentView upload(UUID proposalId, DocumentType type, String contentType, byte[] content) {
        return upload(proposalId, type, null, contentType, content);
    }

    @Transactional
    public DocumentView upload(UUID proposalId, DocumentType type, String period,
            String contentType, byte[] content) {
        if (type == DocumentType.PAYSLIP || type == DocumentType.BANK_STATEMENT) {
            try {
                if (period == null || !period.matches("\\d{4}-\\d{2}")) throw new IllegalArgumentException();
                YearMonth.parse(period);
            } catch (DateTimeParseException | IllegalArgumentException ex) {
                throw new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.BAD_REQUEST, "A valid YYYY-MM period is required");
            }
        } else if (period != null) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "Period is only supported for financial documents");
        }
        validate(contentType, content);
        var proposal = proposals.lockForMutation(proposalId);
        if ("APPROVED".equals(proposal.status()) || "REJECTED".equals(proposal.status())) {
            throw new ConflictException("Finalized proposal cannot receive new documents");
        }
        var nextVersion = period == null ? documents.findMaxVersion(proposalId, type.name()) + 1
                : documents.findMaxVersionForPeriod(proposalId, type.name(), period) + 1;
        var id = UUID.randomUUID();
        var key = proposalId + "/" + id + ".pdf";
        storage.put(key, content);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    storage.delete(key);
                }
            }
        });
        var digest = sha256(content);
        var document = documents.save(new DocumentEntity(id, proposalId, type.name(), key, digest,
                nextVersion, OffsetDateTime.now(ZoneOffset.UTC), "AVAILABLE", period));
        return toView(document);
    }

    @Transactional(readOnly = true)
    public List<DocumentView> list(UUID proposalId) {
        proposals.get(proposalId);
        return documents.findByProposalIdOrderByTypeAscDocumentVersionAsc(proposalId)
                .stream().map(this::toView).toList();
    }

    @Transactional(readOnly = true)
    public List<DocumentSnapshotRef> snapshot(UUID proposalId) {
        proposals.get(proposalId);
        var latestByType = documents.findByProposalIdOrderByTypeAscDocumentVersionAsc(proposalId).stream()
                .collect(Collectors.toMap(document -> document.getType() + ":" + document.getPeriodKey(), document -> document,
                        (older, newer) -> newer, LinkedHashMap::new));
        return latestByType.values().stream()
                .map(document -> new DocumentSnapshotRef(document.getId(), proposalId,
                        DocumentType.valueOf(document.getType()), document.getDocumentVersion(),
                        document.getObjectKey(), document.getSha256(), document.getPeriodKey()))
                .toList();
    }

    private void validate(String contentType, byte[] content) {
        if (content == null || content.length == 0 || !"application/pdf".equalsIgnoreCase(contentType)) {
            throw new InvalidDocumentException("A nonempty application/pdf file is required");
        }
        if (content.length > MAX_BYTES) {
            throw new PayloadTooLargeException("Document exceeds the 10 MiB limit");
        }
        if (content.length < 5 || content[0] != '%' || content[1] != 'P' || content[2] != 'D'
                || content[3] != 'F' || content[4] != '-') {
            throw new InvalidDocumentException("File does not have a PDF signature");
        }
        try (var pdf = Loader.loadPDF(content)) {
            if (pdf.getNumberOfPages() < 1 || new PDFTextStripper().getText(pdf).isBlank()) {
                throw new InvalidDocumentException("PDF must contain selectable text");
            }
        } catch (IOException ex) {
            throw new InvalidDocumentException("PDF could not be read");
        }
    }

    private String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    private DocumentView toView(DocumentEntity document) {
        return new DocumentView(document.getId(), document.getProposalId(),
                DocumentType.valueOf(document.getType()), document.getDocumentVersion(),
                document.getSha256(), document.getUploadedAt(), document.getStatus(), document.getPeriodKey());
    }
}
