package com.floor21.service;

import com.floor21.dto.AccountProfileView;
import com.floor21.entity.Builder;
import com.floor21.entity.User;
import com.floor21.repository.BuilderRepository;
import com.floor21.repository.UserRepository;
import com.floor21.security.Floor21UserPrincipal;
import com.floor21.security.TenantContext;
import com.floor21.util.IndianStates;
import com.floor21.util.UserContactFields;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AccountService {

    private static final int MIN_PASSWORD_LENGTH = 8;

    private final UserRepository userRepository;
    private final BuilderRepository builderRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserDetailsService userDetailsService;
    private final VaultPinService vaultPinService;
    private final VaultAccessService vaultAccessService;
    private final UserProjectAssignmentService userProjectAssignmentService;

    @Transactional(readOnly = true)
    public String currentDisplayName() {
        Floor21UserPrincipal principal = requirePrincipal();
        if (principal.isSuperAdmin()) {
            return "Floor21 Admin";
        }
        return userRepository
                .findFirstByEmailIgnoreCaseAndActiveTrue(principal.getEmail())
                .map(User::getFullName)
                .orElseGet(
                        () ->
                                builderRepository
                                        .findByEmailIgnoreCase(principal.getEmail())
                                        .map(Builder::getCompanyName)
                                        .orElse(principal.getEmail()));
    }

    @Transactional(readOnly = true)
    public AccountProfileView currentProfile() {
        Floor21UserPrincipal principal = requirePrincipal();
        String email = principal.getEmail();

        if (principal.isSuperAdmin()) {
            Builder admin =
                    builderRepository
                            .findByEmailIgnoreCase(email)
                            .orElseThrow(() -> new IllegalStateException("Account not found"));
            return new AccountProfileView(
                    "Floor21 Admin",
                    email,
                    "Platform administrator",
                    null,
                    false,
                    true,
                    false,
                    false,
                    false,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    false);
        }

        var staff = userRepository.findFirstByEmailIgnoreCaseAndActiveTrue(email);
        if (staff.isPresent()) {
            User user = staff.get();
            UUID contextBuilderId = TenantContext.getBuilderIdOrNull();
            UUID builderId =
                    contextBuilderId != null
                            ? contextBuilderId
                            : userProjectAssignmentService
                                    .resolvePrimaryMembership(user.getId())
                                    .map(m -> m.getBuilder().getId())
                                    .orElse(null);
            Builder project =
                    builderId != null
                            ? builderRepository.findById(builderId).orElse(null)
                            : null;
            String role =
                    builderId != null
                            ? userProjectAssignmentService.getRole(user.getId(), builderId)
                            : user.getRole();
            String projectName =
                    project != null
                            ? project.getCompanyName()
                            : userProjectAssignmentService.formatProjectNames(user.getId());
            boolean vaultAccess = vaultAccessService.canCurrentUserAccessVault();
            boolean builderAdminRole =
                    StaffBuildingAccessService.ROLE_BUILDER_ADMIN.equals(role);
            return new AccountProfileView(
                    user.getFullName(),
                    email,
                    formatRole(role),
                    projectName,
                    builderAdminRole,
                    false,
                    vaultAccess && vaultPinService.hasPinConfigured(),
                    false,
                    vaultAccess,
                    user.getCompanyName(),
                    user.getPanNumber(),
                    user.getGstNumber(),
                    user.getTanNumber(),
                    user.getMobileNumber(),
                    user.getAddress(),
                    user.getAddressState(),
                    user.getAddressPin(),
                    project != null ? project.getPhone() : null,
                    project != null ? project.getAddress() : null,
                    project != null ? project.getCity() : null,
                    true);
        }

        Builder builder =
                builderRepository
                        .findByEmailIgnoreCase(email)
                        .orElseThrow(() -> new IllegalStateException("Account not found"));
        boolean vaultAccess = vaultAccessService.canCurrentUserAccessVault();
        boolean vaultConfigured =
                vaultAccess && principal.getBuilderId() != null && vaultPinService.hasPinConfigured();
        return new AccountProfileView(
                builder.getCompanyName(),
                email,
                "Builder administrator",
                builder.getCompanyName(),
                true,
                false,
                vaultConfigured,
                false,
                vaultAccess,
                builder.getCompanyName(),
                null,
                null,
                null,
                builder.getPhone(),
                builder.getAddress(),
                null,
                null,
                builder.getPhone(),
                builder.getAddress(),
                builder.getCity(),
                false);
    }

    @Transactional(readOnly = true)
    public User companyProfileForm() {
        return copyCompanyFields(requireStaffUser());
    }

    @Transactional
    public void updateCompanyProfile(User form) {
        User entity = requireStaffUser();
        UserContactFields.applyFromForm(entity, form);
        userRepository.save(entity);
    }

    private User requireStaffUser() {
        Floor21UserPrincipal principal = requirePrincipal();
        if (principal.isSuperAdmin()) {
            throw new IllegalStateException("Platform admin account cannot edit company profile here.");
        }
        return userRepository
                .findFirstByEmailIgnoreCaseAndActiveTrue(principal.getEmail())
                .orElseThrow(
                        () ->
                                new IllegalStateException(
                                        "Company profile is only editable for staff accounts. Contact Floor21 admin to update project tax details."));
    }

    private static User copyCompanyFields(User user) {
        User form = new User();
        form.setCompanyName(user.getCompanyName());
        form.setPanNumber(user.getPanNumber());
        form.setTanNumber(user.getTanNumber());
        form.setGstNumber(user.getGstNumber());
        form.setMobileNumber(user.getMobileNumber());
        form.setAddress(user.getAddress());
        form.setAddressState(user.getAddressState());
        form.setAddressPin(user.getAddressPin());
        return form;
    }

    @Transactional
    public void changePassword(String currentPassword, String newPassword, String confirmPassword) {
        if (currentPassword == null || currentPassword.isBlank()) {
            throw new IllegalArgumentException("Enter your current password.");
        }
        if (newPassword == null || newPassword.isBlank()) {
            throw new IllegalArgumentException("Enter a new password.");
        }
        if (newPassword.length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalArgumentException("New password must be at least " + MIN_PASSWORD_LENGTH + " characters.");
        }
        if (!newPassword.equals(confirmPassword)) {
            throw new IllegalArgumentException("New password and confirmation do not match.");
        }
        if (currentPassword.equals(newPassword)) {
            throw new IllegalArgumentException("New password must be different from the current password.");
        }

        Floor21UserPrincipal principal = requirePrincipal();
        if (!passwordEncoder.matches(currentPassword, principal.getPassword())) {
            throw new IllegalArgumentException("Current password is incorrect.");
        }

        String encoded = passwordEncoder.encode(newPassword);
        String email = principal.getEmail();

        if (principal.isSuperAdmin()) {
            Builder admin =
                    builderRepository
                            .findByEmailIgnoreCase(email)
                            .orElseThrow(() -> new IllegalStateException("Account not found"));
            admin.setPasswordHash(encoded);
            builderRepository.save(admin);
        } else if (userRepository.findFirstByEmailIgnoreCaseAndActiveTrue(email).isPresent()) {
            User user =
                    userRepository
                            .findFirstByEmailIgnoreCaseAndActiveTrue(email)
                            .orElseThrow(() -> new IllegalStateException("Account not found"));
            user.setPasswordHash(encoded);
            userRepository.save(user);
        } else {
            Builder builder =
                    builderRepository
                            .findByEmailIgnoreCase(email)
                            .orElseThrow(() -> new IllegalStateException("Account not found"));
            builder.setPasswordHash(encoded);
            builderRepository.save(builder);
        }

        refreshAuthentication(email);
    }

    private void refreshAuthentication(String email) {
        UserDetails updated = userDetailsService.loadUserByUsername(email);
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        UsernamePasswordAuthenticationToken token =
                new UsernamePasswordAuthenticationToken(
                        updated, updated.getPassword(), updated.getAuthorities());
        if (auth != null) {
            token.setDetails(auth.getDetails());
        }
        SecurityContextHolder.getContext().setAuthentication(token);
    }

    private static Floor21UserPrincipal requirePrincipal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof Floor21UserPrincipal principal)) {
            throw new IllegalStateException("You must be signed in.");
        }
        return principal;
    }

    private static String formatRole(String role) {
        if (role == null || role.isBlank()) {
            return "Staff";
        }
        String normalized = role.trim().toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(normalized.charAt(0)) + normalized.substring(1);
    }
}
