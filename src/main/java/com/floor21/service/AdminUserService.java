package com.floor21.service;

import com.floor21.dto.PlatformUserView;
import com.floor21.entity.Builder;
import com.floor21.entity.User;
import com.floor21.entity.UserProjectAssignment;
import com.floor21.repository.BuilderRepository;
import com.floor21.repository.UserRepository;
import com.floor21.util.UserContactFields;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminUserService {

    private final BuilderRepository builderRepository;
    private final UserRepository userRepository;
    private final StaffBuildingAccessService staffBuildingAccessService;
    private final UserProjectAssignmentService userProjectAssignmentService;
    private final PasswordEncoder passwordEncoder;
    private final PlatformAuditService auditService;

    @Transactional(readOnly = true)
    public List<PlatformUserView> listAllUsers() {
        List<PlatformUserView> rows = new ArrayList<>();
        Set<UUID> seen = new LinkedHashSet<>();
        for (User user : userRepository.findByBuilderIsNullOrderByFullNameAsc()) {
            if (userProjectAssignmentService.hasAnyMembership(user.getId())) {
                continue;
            }
            rows.add(PlatformUserView.unassigned(user));
            seen.add(user.getId());
        }
        for (User user : userRepository.findAllByOrderByFullNameAsc()) {
            if (seen.contains(user.getId())) {
                continue;
            }
            List<UserProjectAssignment> memberships = userProjectAssignmentService.listMemberships(user.getId());
            if (memberships.isEmpty()) {
                continue;
            }
            rows.add(toPlatformUserView(user, memberships));
        }
        rows.sort(
                Comparator.comparing(PlatformUserView::builderCompanyName, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(PlatformUserView::fullName, String.CASE_INSENSITIVE_ORDER));
        return rows;
    }

    @Transactional(readOnly = true)
    public List<User> listUsersAvailableForProject(UUID builderId) {
        return userProjectAssignmentService.listUsersAvailableForProject(builderId);
    }

    @Transactional(readOnly = true)
    public List<Builder> listTenantBuilders() {
        return builderRepository.findAllTenantsOrderByCompanyNameAsc();
    }

    @Transactional(readOnly = true)
    public User requireUser(UUID userId) {
        return userRepository
                .findById(userId)
                .filter(u -> u.getBuilder() == null || !u.getBuilder().isPlatformAdmin())
                .orElseThrow(() -> new IllegalArgumentException("User not found."));
    }

    @Transactional
    public User savePlatformUser(User form, String rawPassword) {
        User entity;
        boolean created = form.getId() == null;
        String password = requirePassword(rawPassword, created, form.getId());
        if (created) {
            if (userRepository.existsByEmailIgnoreCase(form.getEmail())) {
                throw new IllegalArgumentException("Email is already used by another user.");
            }
            entity = new User();
            entity.setBuilder(null);
            entity.setCreatedAt(Instant.now());
            entity.setRole(StaffBuildingAccessService.ROLE_EXECUTIVE);
            entity.setPasswordHash(passwordEncoder.encode(password));
            entity.setAdminVisiblePassword(password);
        } else {
            entity = requireUser(form.getId());
            if (userProjectAssignmentService.hasAnyMembership(entity.getId())) {
                throw new IllegalArgumentException(
                        "This user is linked to one or more projects. Edit role and layout access from Projects → Partners.");
            }
            if (userRepository.existsByEmailIgnoreCaseAndIdNot(form.getEmail(), entity.getId())) {
                throw new IllegalArgumentException("Email is already used by another user.");
            }
            if (!password.equals(entity.getAdminVisiblePassword())) {
                entity.setPasswordHash(passwordEncoder.encode(password));
            }
            entity.setAdminVisiblePassword(password);
        }
        entity.setFullName(form.getFullName().trim());
        entity.setEmail(form.getEmail().trim().toLowerCase(Locale.ROOT));
        entity.setActive(form.getActive() != null ? form.getActive() : true);
        UserContactFields.applyFromForm(entity, form);
        User saved = userRepository.save(entity);
        auditService.log(
                created ? "USER_CREATED" : "USER_UPDATED",
                "user",
                saved.getId().toString(),
                null,
                saved.getEmail());
        return saved;
    }

    private PlatformUserView toPlatformUserView(User user, List<UserProjectAssignment> memberships) {
        String projects =
                memberships.stream()
                        .map(a -> a.getBuilder().getCompanyName())
                        .distinct()
                        .sorted(String.CASE_INSENSITIVE_ORDER)
                        .collect(Collectors.joining(", "));
        Set<String> roles =
                memberships.stream().map(UserProjectAssignment::getRole).collect(Collectors.toCollection(LinkedHashSet::new));
        String roleLabel = roles.size() == 1 ? roles.iterator().next() : "Multiple";
        List<String> buildingAccess = new ArrayList<>();
        for (UserProjectAssignment membership : memberships) {
            buildingAccess.addAll(
                    staffBuildingAccessService.describeBuildingAccess(
                            user.getId(), membership.getBuilder().getId()));
        }
        buildingAccess = buildingAccess.stream().distinct().toList();
        return PlatformUserView.from(user, projects, roleLabel, buildingAccess);
    }

    private static String requirePassword(String rawPassword, boolean created, UUID userId) {
        if (rawPassword == null || rawPassword.isBlank()) {
            if (created) {
                throw new IllegalArgumentException("Password is required.");
            }
            throw new IllegalArgumentException("Password is required.");
        }
        String trimmed = rawPassword.trim();
        if (trimmed.length() < 6) {
            throw new IllegalArgumentException("Password must be at least 6 characters.");
        }
        return trimmed;
    }
}
