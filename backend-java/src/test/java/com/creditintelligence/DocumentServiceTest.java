package com.creditintelligence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.creditintelligence.document.application.DocumentService;
import com.creditintelligence.document.application.DocumentSnapshotRef;
import com.creditintelligence.document.domain.DocumentType;
import com.creditintelligence.document.infrastructure.DocumentDataStore;
import com.creditintelligence.document.infrastructure.DocumentEntity;
import com.creditintelligence.document.infrastructure.DocumentRepository;
import com.creditintelligence.document.infrastructure.LocalDocumentStorage;
import com.creditintelligence.proposal.application.ProposalService;
import com.creditintelligence.proposal.application.ProposalView;
import com.creditintelligence.shared.errors.ConflictException;
import com.creditintelligence.shared.errors.InvalidDocumentException;
import com.creditintelligence.shared.errors.PayloadTooLargeException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.UUID;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

class DocumentServiceTest {
    private final ProposalService proposals = mock(ProposalService.class);
    private final DocumentRepository documents = mock(DocumentRepository.class);
    private final TransactionTemplate transactions = new TransactionTemplate(new StorageTestTransactionManager());
    private final UUID proposalId = UUID.randomUUID();
    @TempDir Path storageRoot;
    private LocalDocumentStorage storage;
    private DocumentService service;

    @BeforeEach
    void setUp() {
        storage = spy(new LocalDocumentStorage(storageRoot.toString()));
        service = new DocumentService(proposals, new DocumentDataStore(documents), storage);
    }

    @Test
    void rejectsMissingPdfContentAndDoesNotTouchPersistence() {
        assertThatThrownBy(() -> service.upload(proposalId, DocumentType.IDENTIFICATION, "application/pdf", new byte[0]))
                .isInstanceOf(InvalidDocumentException.class)
                .hasMessage("A nonempty application/pdf file is required");

        verifyNoInteractions(proposals, documents, storage);
    }

    @Test
    void rejectsNonPdfContentTypeAndSignature() {
        assertThatThrownBy(() -> service.upload(proposalId, DocumentType.IDENTIFICATION, "text/plain", "%PDF-".getBytes()))
                .isInstanceOf(InvalidDocumentException.class)
                .hasMessage("A nonempty application/pdf file is required");
        assertThatThrownBy(() -> service.upload(proposalId, DocumentType.IDENTIFICATION, "application/pdf", "plain".getBytes()))
                .isInstanceOf(InvalidDocumentException.class)
                .hasMessage("File does not have a PDF signature");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"2026-13", "2026-1"})
    void rejectsInvalidFinancialPeriodBeforeReadingProposal(String period) {
        assertThatThrownBy(() -> service.upload(proposalId, DocumentType.PAYSLIP, period,
                "application/pdf", "%PDF-".getBytes()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("A valid YYYY-MM period is required");
        verifyNoInteractions(proposals, documents, storage);
    }

    @Test
    void rejectsPeriodForIdentityDocument() {
        assertThatThrownBy(() -> service.upload(proposalId, DocumentType.IDENTIFICATION, "2026-10",
                "application/pdf", "%PDF-".getBytes()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Period is only supported for financial documents");
        verifyNoInteractions(proposals, documents, storage);
    }

    @ParameterizedTest
    @ValueSource(strings = {"%PDF", "xPDF-", "%xDF-", "%PxF-", "%PDx-", "%PDFx"})
    void rejectsAFileWithoutTheCompletePdfSignature(String contents) {
        assertThatThrownBy(() -> service.upload(proposalId, DocumentType.IDENTIFICATION, "application/pdf",
                contents.getBytes(StandardCharsets.US_ASCII)))
                .isInstanceOf(InvalidDocumentException.class).hasMessage("File does not have a PDF signature");
        verifyNoInteractions(proposals, documents, storage);
    }

    @Test
    void distinguishesUnreadablePdfFromPdfWithoutSelectableText() throws IOException {
        var blank = pdf(null);
        assertThatThrownBy(() -> service.upload(proposalId, DocumentType.IDENTIFICATION, "application/pdf", blank))
                .isInstanceOf(InvalidDocumentException.class).hasMessage("PDF must contain selectable text");
        assertThatThrownBy(() -> service.upload(proposalId, DocumentType.IDENTIFICATION, "application/pdf",
                "%PDF-".getBytes(StandardCharsets.US_ASCII)))
                .isInstanceOf(InvalidDocumentException.class).hasMessage("PDF could not be read");
        verifyNoInteractions(proposals, documents, storage);
    }

    @Test
    void enforcesTheInclusiveTenMebibyteLimitBeforeParsing() {
        int limit = 10 * 1024 * 1024;
        assertThatThrownBy(() -> service.upload(proposalId, DocumentType.IDENTIFICATION, "application/pdf",
                new byte[limit])).isInstanceOf(InvalidDocumentException.class)
                .hasMessage("File does not have a PDF signature");
        assertThatThrownBy(() -> service.upload(proposalId, DocumentType.IDENTIFICATION, "application/pdf",
                new byte[limit + 1])).isInstanceOf(PayloadTooLargeException.class)
                .hasMessage("Document exceeds the 10 MiB limit");
        verifyNoInteractions(proposals, documents, storage);
    }

    @ParameterizedTest
    @ValueSource(strings = {"APPROVED", "REJECTED"})
    void refusesValidPdfForFinalizedProposal(String status) throws IOException {
        stubProposal(status);
        var document = pdf("Synthetic income statement");

        assertThatThrownBy(() -> service.upload(proposalId, DocumentType.IDENTIFICATION, "application/pdf", document))
                .isInstanceOf(ConflictException.class).hasMessage("Finalized proposal cannot receive new documents");
        verifyNoInteractions(documents, storage);
    }

    @Test
    void preservesVersionHistoryAndSnapshotsOnlyTheLatestDocumentForEachTypeAndPeriod() throws Exception {
        stubProposal("DRAFT");
        var history = new ArrayList<DocumentEntity>();
        when(documents.save(any(DocumentEntity.class))).thenAnswer(call -> {
            var row = call.getArgument(0, DocumentEntity.class);
            history.add(row);
            return row;
        });
        when(documents.findByProposalIdOrderByTypeAscDocumentVersionAsc(proposalId)).thenReturn(history);
        when(documents.findMaxVersion(proposalId, "IDENTIFICATION")).thenReturn(0, 1);
        when(documents.findMaxVersionForPeriod(proposalId, "PAYSLIP", "2026-09")).thenReturn(0, 1);
        when(documents.findMaxVersionForPeriod(proposalId, "PAYSLIP", "2026-10")).thenReturn(0);
        var original = pdf("Original synthetic document");
        var replacement = pdf("Replacement synthetic document");

        var identityV1 = transactions.execute(status -> service.upload(proposalId, DocumentType.IDENTIFICATION,
                "application/pdf", original));
        var identityV2 = transactions.execute(status -> service.upload(proposalId, DocumentType.IDENTIFICATION,
                "APPLICATION/PDF", replacement));
        var septemberV1 = transactions.execute(status -> service.upload(proposalId, DocumentType.PAYSLIP,
                "2026-09", "application/pdf", original));
        var octoberV1 = transactions.execute(status -> service.upload(proposalId, DocumentType.PAYSLIP,
                "2026-10", "application/pdf", original));
        var septemberV2 = transactions.execute(status -> service.upload(proposalId, DocumentType.PAYSLIP,
                "2026-09", "application/pdf", replacement));

        assertThat(identityV1.version()).isEqualTo(1);
        assertThat(identityV2.version()).isEqualTo(2);
        assertThat(septemberV1.version()).isEqualTo(1);
        assertThat(septemberV2.version()).isEqualTo(2);
        assertThat(octoberV1.version()).isEqualTo(1);
        assertThat(identityV2.proposalId()).isEqualTo(proposalId);
        assertThat(identityV2.sha256()).isEqualTo(digest(replacement)).isNotEqualTo(identityV1.sha256());
        assertThat(identityV2.uploadedAt()).isNotNull();
        assertThat(identityV2.status()).isEqualTo("AVAILABLE");
        assertThat(storageRoot.resolve(proposalId + "/" + identityV1.id() + ".pdf")).hasBinaryContent(original);
        assertThat(storageRoot.resolve(proposalId + "/" + identityV2.id() + ".pdf")).hasBinaryContent(replacement);
        assertThat(service.list(proposalId)).containsExactly(identityV1, identityV2, septemberV1, octoberV1, septemberV2);
        assertThat(service.snapshot(proposalId)).containsExactly(
                new DocumentSnapshotRef(identityV2.id(), proposalId, DocumentType.IDENTIFICATION, 2,
                        proposalId + "/" + identityV2.id() + ".pdf", digest(replacement), null),
                new DocumentSnapshotRef(septemberV2.id(), proposalId, DocumentType.PAYSLIP, 2,
                        proposalId + "/" + septemberV2.id() + ".pdf", digest(replacement), "2026-09"),
                new DocumentSnapshotRef(octoberV1.id(), proposalId, DocumentType.PAYSLIP, 1,
                        proposalId + "/" + octoberV1.id() + ".pdf", digest(original), "2026-10"));
    }

    @Test
    void savesNoMetadataWhenStoringThePdfFails() throws IOException {
        stubProposal("DRAFT");
        doThrow(new IllegalStateException("Storage unavailable")).when(storage).put(anyString(), any());
        var document = pdf("Synthetic identity");

        assertThatThrownBy(() -> transactions.execute(status -> service.upload(proposalId, DocumentType.IDENTIFICATION,
                "application/pdf", document))).isInstanceOf(IllegalStateException.class).hasMessage("Storage unavailable");
        verify(documents, never()).save(any());
        assertThat(storageRoot.resolve(proposalId.toString())).doesNotExist();
    }

    @Test
    void removesTheObjectWhenMetadataPersistenceFails() throws IOException {
        stubProposal("DRAFT");
        var document = pdf("Synthetic identity");
        when(documents.save(any(DocumentEntity.class))).thenAnswer(call -> {
            var row = call.getArgument(0, DocumentEntity.class);
            assertThat(storageRoot.resolve(row.getObjectKey())).hasBinaryContent(document);
            throw new IllegalStateException("Metadata unavailable");
        });

        assertThatThrownBy(() -> transactions.execute(status -> service.upload(proposalId, DocumentType.IDENTIFICATION,
                "application/pdf", document))).isInstanceOf(IllegalStateException.class).hasMessage("Metadata unavailable");
        assertThat(storageRoot.resolve(proposalId.toString())).isEmptyDirectory();
    }

    private void stubProposal(String status) {
        var proposal = new ProposalView(proposalId, null, new BigDecimal("8000.00"), "BRL", status, OffsetDateTime.now());
        when(proposals.lockForMutation(proposalId)).thenReturn(proposal);
        when(proposals.get(proposalId)).thenReturn(proposal);
    }

    private static byte[] pdf(String text) throws IOException {
        try (var pdf = new PDDocument(); var output = new ByteArrayOutputStream()) {
            var page = new PDPage();
            pdf.addPage(page);
            if (text != null) {
                try (var content = new PDPageContentStream(pdf, page)) {
                    content.beginText();
                    content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                    content.newLineAtOffset(72, 720);
                    content.showText(text);
                    content.endText();
                }
            }
            pdf.save(output);
            return output.toByteArray();
        }
    }

    private static String digest(byte[] content) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
    }

    private static class StorageTestTransactionManager extends AbstractPlatformTransactionManager {
        @Override protected Object doGetTransaction() { return new Object(); }
        @Override protected void doBegin(Object transaction, TransactionDefinition definition) {}
        @Override protected void doCommit(DefaultTransactionStatus status) {}
        @Override protected void doRollback(DefaultTransactionStatus status) {}
    }
}
