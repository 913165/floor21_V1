package com.floor21.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.floor21.dto.AdminBuilderRow;
import com.floor21.entity.Builder;
import com.floor21.repository.BankRepository;
import com.floor21.repository.BookingRepository;
import com.floor21.repository.BuildingRepository;
import com.floor21.repository.BrokerRepository;
import com.floor21.repository.BuilderExpenseRepository;
import com.floor21.repository.BuilderRepository;
import com.floor21.repository.CancellationRepository;
import com.floor21.repository.ClientRepository;
import com.floor21.repository.ExtraExpenseRepository;
import com.floor21.repository.FlatRepository;
import com.floor21.repository.PaymentSlabTemplateRepository;
import com.floor21.repository.PlatformAuditLogRepository;
import com.floor21.repository.ReceiptRepository;
import com.floor21.repository.SlabRepository;
import com.floor21.repository.UserBuildingVaultAccessRepository;
import com.floor21.repository.UserProjectAssignmentRepository;
import com.floor21.repository.UserRepository;
import com.floor21.repository.VaultBookingProfileRepository;
import com.floor21.repository.VaultEntryRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;

@ExtendWith(MockitoExtension.class)
class PlatformAdminServiceProjectsPageTest {

    @Mock private BuilderRepository builderRepository;
    @Mock private BuildingRepository buildingRepository;
    @Mock private FlatRepository flatRepository;
    @Mock private BookingRepository bookingRepository;
    @Mock private UserRepository userRepository;
    @Mock private SlabRepository slabRepository;
    @Mock private ClientRepository clientRepository;
    @Mock private BrokerRepository brokerRepository;
    @Mock private BankRepository bankRepository;
    @Mock private ReceiptRepository receiptRepository;
    @Mock private VaultEntryRepository vaultEntryRepository;
    @Mock private BuilderExpenseRepository builderExpenseRepository;
    @Mock private CancellationRepository cancellationRepository;
    @Mock private ExtraExpenseRepository extraExpenseRepository;
    @Mock private VaultBookingProfileRepository vaultBookingProfileRepository;
    @Mock private PaymentSlabTemplateRepository paymentSlabTemplateRepository;
    @Mock private UserProjectAssignmentRepository userProjectAssignmentRepository;
    @Mock private PlatformAuditLogRepository auditLogRepository;
    @Mock private PlatformAuditService auditService;
    @Mock private StaffBuildingAccessService staffBuildingAccessService;
    @Mock private UserBuildingVaultAccessRepository userBuildingVaultAccessRepository;

    private PlatformAdminService service;

    @BeforeEach
    void setUp() {
        service =
                new PlatformAdminService(
                        builderRepository,
                        buildingRepository,
                        flatRepository,
                        bookingRepository,
                        userRepository,
                        slabRepository,
                        clientRepository,
                        brokerRepository,
                        bankRepository,
                        receiptRepository,
                        vaultEntryRepository,
                        builderExpenseRepository,
                        cancellationRepository,
                        extraExpenseRepository,
                        vaultBookingProfileRepository,
                        paymentSlabTemplateRepository,
                        userProjectAssignmentRepository,
                        auditLogRepository,
                        auditService,
                        staffBuildingAccessService,
                        userBuildingVaultAccessRepository);
    }

    @Test
    void listBuildersPage_sortsByLastActivityAndPaginates() {
        Builder older = tenant("Older", Instant.parse("2024-01-01T00:00:00Z"), null);
        Builder newer = tenant("Newer", Instant.parse("2024-06-01T00:00:00Z"), Instant.parse("2025-01-01T00:00:00Z"));
        when(builderRepository.findAllTenantsOrderByCompanyNameAsc()).thenReturn(List.of(older, newer));
        stubRowCounts();

        Page<AdminBuilderRow> page = service.listBuildersPage(0, 25, "lastActivity", "desc", null, null);

        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent().get(0).companyName()).isEqualTo("Newer");
        assertThat(page.getContent().get(1).companyName()).isEqualTo("Older");
    }

    @Test
    void listBuildersPage_filtersBySearchAndActive() {
        Builder activeMumbai =
                tenant("Skyline Mumbai", Instant.parse("2024-01-01T00:00:00Z"), null);
        activeMumbai.setCity("Mumbai");
        activeMumbai.setActive(true);
        Builder inactivePune =
                tenant("Horizon Pune", Instant.parse("2024-02-01T00:00:00Z"), null);
        inactivePune.setCity("Pune");
        inactivePune.setActive(false);
        when(builderRepository.findAllTenantsOrderByCompanyNameAsc())
                .thenReturn(List.of(activeMumbai, inactivePune));
        stubRowCounts();

        Page<AdminBuilderRow> searchPage = service.listBuildersPage(0, 25, "companyName", "asc", "mumbai", null);
        assertThat(searchPage.getTotalElements()).isEqualTo(1);
        assertThat(searchPage.getContent().get(0).companyName()).isEqualTo("Skyline Mumbai");

        Page<AdminBuilderRow> activePage = service.listBuildersPage(0, 25, "companyName", "asc", null, true);
        assertThat(activePage.getTotalElements()).isEqualTo(1);
        assertThat(activePage.getContent().get(0).companyName()).isEqualTo("Skyline Mumbai");

        Page<AdminBuilderRow> combinedPage =
                service.listBuildersPage(0, 25, "companyName", "asc", "pune", false);
        assertThat(combinedPage.getTotalElements()).isEqualTo(1);
        assertThat(combinedPage.getContent().get(0).companyName()).isEqualTo("Horizon Pune");
    }

    @Test
    void listBuildersPage_filtersByProjectId() {
        Builder first = tenant("Alpha", Instant.parse("2024-01-01T00:00:00Z"), null);
        Builder second = tenant("Beta", Instant.parse("2024-02-01T00:00:00Z"), null);
        when(builderRepository.findAllTenantsOrderByCompanyNameAsc()).thenReturn(List.of(first, second));
        stubRowCounts();

        Page<AdminBuilderRow> page =
                service.listBuildersPage(0, 25, "companyName", "asc", null, null, first.getId());

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent().get(0).companyName()).isEqualTo("Alpha");
    }

    @Test
    void deleteProject_purgesVaultEntriesBeforeBuilderDelete() {
        Builder project = tenant("Empty Project", Instant.parse("2024-01-01T00:00:00Z"), null);
        when(builderRepository.findById(project.getId())).thenReturn(Optional.of(project));
        when(buildingRepository.countByBuilder_Id(project.getId())).thenReturn(0L);
        when(flatRepository.countByBuilder_Id(project.getId())).thenReturn(0L);
        when(bookingRepository.countActiveByBuilder(project.getId())).thenReturn(0L);
        when(userProjectAssignmentRepository.findByBuilder_IdWithUser(project.getId())).thenReturn(List.of());
        when(userRepository.findByBuilder_IdOrderByFullNameAsc(project.getId())).thenReturn(List.of());

        service.deleteProject(project.getId(), "super@floor21.com");

        verify(vaultEntryRepository).deleteByBuilder_Id(project.getId());
        verify(builderExpenseRepository).deleteByBuilder_Id(project.getId());
        verify(builderRepository).delete(project);
    }

    private Builder tenant(String name, Instant createdAt, Instant updatedAt) {
        Builder b = new Builder();
        b.setId(UUID.randomUUID());
        b.setCompanyName(name);
        b.setPlatformAdmin(false);
        b.setActive(true);
        b.setCreatedAt(createdAt);
        b.setUpdatedAt(updatedAt);
        return b;
    }

    private void stubRowCounts() {
        when(buildingRepository.findFirstByBuilder_IdOrderByBuildingNameAsc(any())).thenReturn(Optional.empty());
        when(buildingRepository.countByBuilder_Id(any())).thenReturn(0L);
        when(userProjectAssignmentRepository.countByBuilder_Id(any())).thenReturn(0L);
    }
}
