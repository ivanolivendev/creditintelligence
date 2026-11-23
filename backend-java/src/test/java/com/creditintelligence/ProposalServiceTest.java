package com.creditintelligence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.creditintelligence.proposal.application.ProposalService;
import com.creditintelligence.proposal.application.ProposalDataPort;
import com.creditintelligence.proposal.infrastructure.ApplicantEntity;
import com.creditintelligence.proposal.infrastructure.ApplicantRepository;
import com.creditintelligence.proposal.infrastructure.ProposalDataStore;
import com.creditintelligence.proposal.infrastructure.ProposalEntity;
import com.creditintelligence.proposal.infrastructure.ProposalRepository;
import com.creditintelligence.shared.errors.ConflictException;
import com.creditintelligence.shared.errors.NotFoundException;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class ProposalServiceTest {
    private final ProposalDataPort data = mock(ProposalDataPort.class);
    private final ProposalService service = new ProposalService(data);

    @Test
    void createsProposalWithTrimmedApplicantDataAndReusesExistingApplicant() {
        var applicant = new ProposalDataPort.ApplicantData(UUID.randomUUID(), "Maria Silva", "DEMO-001");
        when(data.findApplicant("DEMO-001")).thenReturn(Optional.of(applicant));
        when(data.saveProposal(any(), any(), any(), any(), any(), any(), any())).thenAnswer(invocation ->
                new ProposalDataPort.ProposalData(invocation.getArgument(0), applicant,
                        invocation.getArgument(2), invocation.getArgument(3), invocation.getArgument(4),
                        invocation.getArgument(5)));

        var result = service.create(" Maria Silva ", " DEMO-001 ", new BigDecimal("8000.00"), "BRL");

        assertThat(result.applicant().id()).isEqualTo(applicant.id());
        assertThat(result.applicant().name()).isEqualTo("Maria Silva");
        assertThat(result.applicant().syntheticIdentifier()).isEqualTo("DEMO-001");
        assertThat(result.status()).isEqualTo("DRAFT");
        assertThat(result.declaredMonthlyIncome()).isEqualByComparingTo("8000.00");
        verify(data).findApplicant("DEMO-001");
        verify(data, never()).saveApplicant(any(), any(), any());
    }

    @Test
    void rejectsIdentifierReuseForDifferentApplicantName() {
        var applicant = new ProposalDataPort.ApplicantData(UUID.randomUUID(), "Another Person", "DEMO-001");
        when(data.findApplicant("DEMO-001")).thenReturn(Optional.of(applicant));

        assertThatThrownBy(() -> service.create("Maria Silva", "DEMO-001",
                new BigDecimal("8000.00"), "BRL"))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Synthetic identifier belongs to a different applicant name");
        verify(data, never()).saveProposal(any(), any(), any(), any(), any(), any(), any());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" "})
    void generatesSyntheticIdentifierWhenNoneIsProvided(String identifier) {
        when(data.findApplicant(any(String.class))).thenReturn(Optional.empty());
        when(data.saveApplicant(any(), any(), any())).thenAnswer(invocation ->
                new ProposalDataPort.ApplicantData(invocation.getArgument(0), invocation.getArgument(1),
                        invocation.getArgument(2)));
        when(data.saveProposal(any(), any(), any(), any(), any(), any(), any())).thenAnswer(invocation ->
                new ProposalDataPort.ProposalData(invocation.getArgument(0), invocation.getArgument(1),
                        invocation.getArgument(2), invocation.getArgument(3), invocation.getArgument(4),
                        invocation.getArgument(5)));

        var result = service.create(" Maria Silva ", identifier, new BigDecimal("8000.00"), "BRL");

        assertThat(result.applicant().syntheticIdentifier()).matches("DEMO-[0-9a-f]{12}");
        assertThat(result.applicant().name()).isEqualTo("Maria Silva");
    }

    @Test
    void appliesDecisionStatusToLockedProposal() {
        var proposalId = UUID.randomUUID();
        var applicant = new ProposalDataPort.ApplicantData(UUID.randomUUID(), "Maria Silva", "DEMO-001");
        var proposal = new ProposalDataPort.ProposalData(proposalId, applicant, new BigDecimal("8000.00"),
                "BRL", "DRAFT", OffsetDateTime.now());
        when(data.lockProposal(proposalId)).thenReturn(Optional.of(proposal));

        service.applyDecisionStatus(proposalId, "APPROVED");

        verify(data).lockProposal(proposalId);
        verify(data).updateStatus(org.mockito.ArgumentMatchers.eq(proposalId), org.mockito.ArgumentMatchers.eq("APPROVED"), any());
    }

    @Test
    void rejectsAllReadsAndUpdatesOfAnUnknownProposal() {
        var missing = UUID.randomUUID();
        assertThatThrownBy(() -> service.get(missing)).isInstanceOf(NotFoundException.class)
                .hasMessage("Proposal not found");
        assertThatThrownBy(() -> service.lockForMutation(missing)).isInstanceOf(NotFoundException.class)
                .hasMessage("Proposal not found");
        assertThatThrownBy(() -> service.applyDecisionStatus(missing, "APPROVED")).isInstanceOf(NotFoundException.class)
                .hasMessage("Proposal not found");
        verify(data, never()).updateStatus(any(), any(), any());
    }

    @Test
    void persistsNewApplicantAndReflectsDecisionsInProposalHistoryThroughTheDataAdapter() {
        var applicants = mock(ApplicantRepository.class);
        var proposals = mock(ProposalRepository.class);
        var persisted = new ProposalService(new ProposalDataStore(applicants, proposals));
        var applicant = org.mockito.ArgumentCaptor.forClass(ApplicantEntity.class);
        when(applicants.save(any(ApplicantEntity.class))).thenAnswer(invocation -> {
            var stored = invocation.getArgument(0, ApplicantEntity.class);
            when(applicants.findById(stored.getId())).thenReturn(Optional.of(stored));
            return stored;
        });
        when(proposals.save(any(ProposalEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var created = persisted.create(" Synthetic Applicant ", "DEMO-CONTRACT", new BigDecimal("8000.00"), "BRL");

        verify(applicants).save(applicant.capture());
        assertThat(created.applicant().id()).isEqualTo(applicant.getValue().getId()).isNotNull();
        assertThat(created.applicant().name()).isEqualTo("Synthetic Applicant");
        assertThat(created.applicant().syntheticIdentifier()).isEqualTo("DEMO-CONTRACT");
        assertThat(created.declaredMonthlyIncome()).isEqualByComparingTo("8000.00");
        assertThat(created.currency()).isEqualTo("BRL");
        assertThat(created.status()).isEqualTo("DRAFT");
        assertThat(created.createdAt()).isNotNull();
        var saved = org.mockito.ArgumentCaptor.forClass(ProposalEntity.class);
        verify(proposals).save(saved.capture());
        var stored = saved.getValue();
        assertThat(created.id()).isEqualTo(stored.getId()).isNotNull();
        when(proposals.findById(created.id())).thenReturn(Optional.of(stored));
        when(proposals.findForUpdate(created.id())).thenReturn(Optional.of(stored));
        when(proposals.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(stored));
        when(applicants.findBySyntheticIdentifier("DEMO-CONTRACT")).thenReturn(Optional.of(applicant.getValue()));

        var second = persisted.create("Synthetic Applicant", "DEMO-CONTRACT", new BigDecimal("6000.00"), "BRL");
        assertThat(second.applicant()).isEqualTo(created.applicant());
        assertThat(second.id()).isNotEqualTo(created.id());
        verify(applicants).save(any(ApplicantEntity.class));
        assertThat(persisted.get(created.id())).isEqualTo(created);
        assertThat(persisted.lockForMutation(created.id())).isEqualTo(created);

        persisted.applyDecisionStatus(created.id(), "MORE_INFO_REQUIRED");

        assertThat(persisted.list()).singleElement().satisfies(view -> {
            assertThat(view.id()).isEqualTo(created.id());
            assertThat(view.status()).isEqualTo("MORE_INFO_REQUIRED");
            assertThat(view.createdAt()).isEqualTo(created.createdAt());
        });
    }
}
