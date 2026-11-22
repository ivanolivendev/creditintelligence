package com.creditintelligence.document.infrastructure;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DocumentRepository extends JpaRepository<DocumentEntity, UUID> {
    @Query("select coalesce(max(d.documentVersion), 0) from DocumentEntity d where d.proposalId = :proposalId and d.type = :type")
    int findMaxVersion(@Param("proposalId") UUID proposalId, @Param("type") String type);

    @Query("select coalesce(max(d.documentVersion), 0) from DocumentEntity d where d.proposalId = :proposalId and d.type = :type and d.periodKey = :period")
    int findMaxVersionForPeriod(@Param("proposalId") UUID proposalId, @Param("type") String type,
            @Param("period") String period);

    List<DocumentEntity> findByProposalIdOrderByTypeAscDocumentVersionAsc(UUID proposalId);
}
