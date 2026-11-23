package com.creditintelligence.document.infrastructure;

import com.creditintelligence.document.application.DocumentDataPort;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class DocumentDataStore implements DocumentDataPort {
    private final DocumentRepository repository;

    public DocumentDataStore(DocumentRepository repository) { this.repository = repository; }

    public int findMaxVersion(UUID proposalId, String type) { return repository.findMaxVersion(proposalId, type); }
    public int findMaxVersionForPeriod(UUID proposalId, String type, String period) {
        return repository.findMaxVersionForPeriod(proposalId, type, period);
    }
    public DocumentRecord save(DocumentRecord document) {
        return toRecord(repository.save(new DocumentEntity(document.id(), document.proposalId(), document.type(),
                document.objectKey(), document.sha256(), document.version(), document.uploadedAt(), document.status(),
                document.period())));
    }
    public List<DocumentRecord> list(UUID proposalId) {
        return repository.findByProposalIdOrderByTypeAscDocumentVersionAsc(proposalId).stream().map(this::toRecord).toList();
    }
    private DocumentRecord toRecord(DocumentEntity entity) {
        return new DocumentRecord(entity.getId(), entity.getProposalId(), entity.getType(), entity.getObjectKey(),
                entity.getSha256(), entity.getDocumentVersion(), entity.getUploadedAt(), entity.getStatus(),
                entity.getPeriodKey());
    }
}
