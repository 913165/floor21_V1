package com.floor21.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.floor21.dto.PlatformUserView;
import com.floor21.entity.User;
import com.floor21.repository.BookingRepository;
import com.floor21.repository.BuilderRepository;
import com.floor21.repository.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AdminUserServiceUsersPageTest {

    @Mock private BuilderRepository builderRepository;
    @Mock private UserRepository userRepository;
    @Mock private BookingRepository bookingRepository;
    @Mock private StaffBuildingAccessService staffBuildingAccessService;
    @Mock private UserProjectAssignmentService userProjectAssignmentService;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private PlatformAuditService auditService;

    private AdminUserService service;

    @BeforeEach
    void setUp() {
        service =
                new AdminUserService(
                        builderRepository,
                        userRepository,
                        bookingRepository,
                        staffBuildingAccessService,
                        userProjectAssignmentService,
                        passwordEncoder,
                        auditService);
    }

    @Test
    void listUsersPage_includesPlatformAdmins() {
        com.floor21.entity.Builder superAdmin = new com.floor21.entity.Builder();
        superAdmin.setId(UUID.randomUUID());
        superAdmin.setCompanyName("Floor21 Platform");
        superAdmin.setEmail("super@floor21.com");
        superAdmin.setActive(true);
        superAdmin.setPlatformAdmin(true);
        superAdmin.setCreatedAt(Instant.parse("2024-01-01T00:00:00Z"));

        when(builderRepository.findAllPlatformAdminsOrderByEmailAsc()).thenReturn(List.of(superAdmin));
        when(userRepository.findByBuilderIsNullOrderByFullNameAsc()).thenReturn(List.of());
        when(userRepository.findAllByOrderByFullNameAsc()).thenReturn(List.of());

        Page<PlatformUserView> page =
                service.listUsersPage(0, 25, "email", "asc", null, null, null);

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent().get(0).email()).isEqualTo("super@floor21.com");
        assertThat(page.getContent().get(0).role()).isEqualTo("Platform admin");
        assertThat(page.getContent().get(0).platformAdminAccount()).isTrue();
    }

    @Test
    void listUsersPage_sortsByCompanyNameAndPaginates() {
        when(builderRepository.findAllPlatformAdminsOrderByEmailAsc()).thenReturn(List.of());
        User zulu = unassignedUser("Zulu Co", "Zulu User");
        User alpha = unassignedUser("Alpha Co", "Alpha User");
        when(userRepository.findByBuilderIsNullOrderByFullNameAsc()).thenReturn(List.of(zulu, alpha));
        when(userRepository.findAllByOrderByFullNameAsc()).thenReturn(List.of(zulu, alpha));
        when(userProjectAssignmentService.hasAnyMembership(any())).thenReturn(false);

        Page<PlatformUserView> page = service.listUsersPage(0, 25, "companyName", "asc", null, null, null);

        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent().get(0).companyName()).isEqualTo("Alpha Co");
        assertThat(page.getContent().get(1).companyName()).isEqualTo("Zulu Co");

        Page<PlatformUserView> page2 = service.listUsersPage(0, 5, "companyName", "asc", null, null, null);
        assertThat(page2.getSize()).isEqualTo(5);
        assertThat(page2.getTotalPages()).isEqualTo(1);
    }

    @Test
    void deleteUnassignedUser_removesUserWithNoProjectMembership() {
        User user = unassignedUser("Alpha Co", "Alpha User");
        when(userRepository.findById(user.getId())).thenReturn(java.util.Optional.of(user));
        when(userProjectAssignmentService.hasAnyMembership(user.getId())).thenReturn(false);
        when(bookingRepository.countByExecutive_Id(user.getId())).thenReturn(0L);

        service.deleteUnassignedUser(user.getId(), "admin@floor21.com");

        verify(userRepository).delete(user);
    }

    @Test
    void deleteUnassignedUser_rejectsUserAssignedToProject() {
        User user = unassignedUser("Alpha Co", "Alpha User");
        when(userRepository.findById(user.getId())).thenReturn(java.util.Optional.of(user));
        when(userProjectAssignmentService.hasAnyMembership(user.getId())).thenReturn(true);

        assertThatThrownBy(() -> service.deleteUnassignedUser(user.getId(), "admin@floor21.com"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("assigned to a project");
    }

    @Test
    void listUsersPage_filtersBySearchAndActive() {
        when(builderRepository.findAllPlatformAdminsOrderByEmailAsc()).thenReturn(List.of());
        User active = unassignedUser("Alpha Co", "Alpha User");
        User inactive = unassignedUser("Beta Co", "Beta User");
        inactive.setActive(false);
        when(userRepository.findByBuilderIsNullOrderByFullNameAsc()).thenReturn(List.of(active, inactive));
        when(userRepository.findAllByOrderByFullNameAsc()).thenReturn(List.of(active, inactive));
        when(userProjectAssignmentService.hasAnyMembership(any())).thenReturn(false);

        Page<PlatformUserView> searchPage =
                service.listUsersPage(0, 25, "companyName", "asc", "alpha", null, null);
        assertThat(searchPage.getTotalElements()).isEqualTo(1);
        assertThat(searchPage.getContent().get(0).fullName()).isEqualTo("Alpha User");

        Page<PlatformUserView> activePage =
                service.listUsersPage(0, 25, "companyName", "asc", null, true, null);
        assertThat(activePage.getTotalElements()).isEqualTo(1);
        assertThat(activePage.getContent().get(0).companyName()).isEqualTo("Alpha Co");
    }

    private static User unassignedUser(String company, String fullName) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setFullName(fullName);
        user.setCompanyName(company);
        user.setEmail(fullName.toLowerCase().replace(' ', '.') + "@example.com");
        user.setRole("EXECUTIVE");
        user.setActive(true);
        user.setCreatedAt(Instant.parse("2024-01-01T00:00:00Z"));
        return user;
    }
}
