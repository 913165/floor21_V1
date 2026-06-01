package com.floor21.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.floor21.dto.PlatformUserView;
import com.floor21.entity.User;
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
                        staffBuildingAccessService,
                        userProjectAssignmentService,
                        passwordEncoder,
                        auditService);
    }

    @Test
    void listUsersPage_sortsByCompanyNameAndPaginates() {
        User zulu = unassignedUser("Zulu Co", "Zulu User");
        User alpha = unassignedUser("Alpha Co", "Alpha User");
        when(userRepository.findByBuilderIsNullOrderByFullNameAsc()).thenReturn(List.of(zulu, alpha));
        when(userRepository.findAllByOrderByFullNameAsc()).thenReturn(List.of(zulu, alpha));
        when(userProjectAssignmentService.hasAnyMembership(any())).thenReturn(false);

        Page<PlatformUserView> page = service.listUsersPage(0, 25, "companyName", "asc");

        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent().get(0).companyName()).isEqualTo("Alpha Co");
        assertThat(page.getContent().get(1).companyName()).isEqualTo("Zulu Co");

        Page<PlatformUserView> page2 = service.listUsersPage(0, 5, "companyName", "asc");
        assertThat(page2.getSize()).isEqualTo(5);
        assertThat(page2.getTotalPages()).isEqualTo(1);
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
