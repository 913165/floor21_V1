package com.floor21.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.floor21.entity.Builder;
import com.floor21.entity.User;
import com.floor21.entity.UserProjectAssignment;
import com.floor21.repository.UserProjectAssignmentRepository;
import com.floor21.repository.UserRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReceiptPrintServiceSignatoryTest {

    @Mock private UserProjectAssignmentRepository userProjectAssignmentRepository;
    @Mock private UserRepository userRepository;
    @Mock private BookingOwnerService bookingOwnerService;

    private ReceiptPrintService service;
    private Builder builder;

    @AfterEach
  void clearSecurityContext() {
    org.springframework.security.core.context.SecurityContextHolder.clearContext();
  }

  @BeforeEach
    void setUp() {
        service =
                new ReceiptPrintService(
                        userProjectAssignmentRepository, userRepository, bookingOwnerService);
        builder = new Builder();
        builder.setId(UUID.randomUUID());
        builder.setCompanyName("La Vesta");
    }

  @Test
  void signatoryPrefersCurrentUserLegalCompanyName() {
    User admin = userWithCompany("Admin Co");
    User current = userWithCompany("PANKAJA DEVELOPERS");
    current.setEmail("pgupta1387@gmail.com");
    when(userProjectAssignmentRepository.findByBuilder_IdWithUser(builder.getId()))
        .thenReturn(
            List.of(
                assignment(admin, StaffBuildingAccessService.ROLE_BUILDER_ADMIN),
                assignment(current, StaffBuildingAccessService.ROLE_EXECUTIVE)));
    org.springframework.security.core.context.SecurityContextHolder.getContext()
        .setAuthentication(
            new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                new com.floor21.security.Floor21UserPrincipal(
                    builder.getId(),
                    current.getId(),
                    "pgupta1387@gmail.com",
                    "x",
                    false,
                    new org.springframework.security.core.userdetails.User(
                        "pgupta1387@gmail.com", "x", List.of())),
                null,
                List.of()));

    assertThat(service.signatoryCompanyForBuilder(builder)).isEqualTo("PANKAJA DEVELOPERS");
  }

  @Test
  void signatoryUsesBuilderAdminLegalCompanyNameNotProjectName() {
        User admin = userWithCompany("Kumar Realtors Pvt Ltd");
        when(userProjectAssignmentRepository.findByBuilder_IdWithUser(builder.getId()))
                .thenReturn(List.of(assignment(admin, StaffBuildingAccessService.ROLE_BUILDER_ADMIN)));

        assertThat(service.signatoryCompanyForBuilder(builder)).isEqualTo("Kumar Realtors Pvt Ltd");
    }

    @Test
    void signatoryFallsBackToOtherProjectUserWhenBuilderAdminHasNoLegalName() {
        User admin = userWithCompany(null);
        User executive = userWithCompany("Pankaja Developers");
        when(userProjectAssignmentRepository.findByBuilder_IdWithUser(builder.getId()))
                .thenReturn(
                        List.of(
                                assignment(admin, StaffBuildingAccessService.ROLE_BUILDER_ADMIN),
                                assignment(executive, StaffBuildingAccessService.ROLE_EXECUTIVE)));

        assertThat(service.signatoryCompanyForBuilder(builder)).isEqualTo("Pankaja Developers");
    }

    @Test
    void signatoryDoesNotUseProjectNameWhenUsersExistWithoutLegalNames() {
        User admin = userWithCompany("");
        when(userProjectAssignmentRepository.findByBuilder_IdWithUser(builder.getId()))
                .thenReturn(List.of(assignment(admin, StaffBuildingAccessService.ROLE_BUILDER_ADMIN)));

        assertThat(service.signatoryCompanyForBuilder(builder)).isEqualTo("—");
    }

    @Test
    void signatoryUsesBuilderCompanyNameOnlyWhenNoProjectUsers() {
        when(userProjectAssignmentRepository.findByBuilder_IdWithUser(builder.getId()))
                .thenReturn(List.of());

        assertThat(service.signatoryCompanyForBuilder(builder)).isEqualTo("La Vesta");
    }

    private static User userWithCompany(String companyName) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setCompanyName(companyName);
        return user;
    }

    private static UserProjectAssignment assignment(User user, String role) {
        UserProjectAssignment row = new UserProjectAssignment();
        row.setUser(user);
        row.setRole(role);
        return row;
    }
}
