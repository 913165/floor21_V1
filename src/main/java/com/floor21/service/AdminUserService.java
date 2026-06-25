package com.floor21.service;

import com.floor21.dto.AssignableUserOption;
import com.floor21.dto.PlatformUserView;
import com.floor21.entity.Builder;
import com.floor21.entity.User;
import com.floor21.entity.UserProjectAssignment;
import com.floor21.repository.BookingRepository;
import com.floor21.repository.BuilderRepository;
import com.floor21.repository.PartnerFlatAssignmentRepository;
import com.floor21.repository.UserBuildingAssignmentRepository;
import com.floor21.repository.UserBuildingVaultAccessRepository;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminUserService {

    public static final int USERS_DEFAULT_PAGE_SIZE = 10;
    public static final int USERS_MAX_PAGE_SIZE = 100;

    private static final Set<String> USERS_SORT_FIELDS =
            Set.of(
                    "fullName",
                    "companyName",
                    "email",
                    "project",
                    "role",
                    "active",
                    "lastLoginAt",
                    "createdAt");

    private final BuilderRepository builderRepository;
    private final UserRepository userRepository;
    private final BookingRepository bookingRepository;
    private final UserBuildingAssignmentRepository userBuildingAssignmentRepository;
    private final PartnerFlatAssignmentRepository partnerFlatAssignmentRepository;
    private final UserBuildingVaultAccessRepository userBuildingVaultAccessRepository;
    private final StaffBuildingAccessService staffBuildingAccessService;
    private final UserProjectAssignmentService userProjectAssignmentService;
    private final PasswordEncoder passwordEncoder;
    private final PlatformAuditService auditService;

    @Transactional(readOnly = true)
    public Page<PlatformUserView> listUsersPage(
            int page, int size, String sort, String dir, String search, Boolean activeFilter, UUID projectId) {
        String sortKey = normalizeUsersSort(sort);
        boolean ascending = normalizeUsersSortAscending(sortKey, dir);
        int safeSize = Math.min(Math.max(size, 5), USERS_MAX_PAGE_SIZE);
        int safePage = Math.max(page, 0);

        List<PlatformUserView> filtered =
                loadAllUserRows().stream()
                        .filter(row -> matchesUserSearch(row, search))
                        .filter(row -> matchesUserActiveFilter(row, activeFilter))
                        .filter(row -> matchesUserProjectFilter(row, projectId))
                        .toList();
        List<PlatformUserView> sorted = new ArrayList<>(filtered);
        sorted.sort(comparatorForUsersSort(sortKey, ascending));

        int total = sorted.size();
        int from = Math.min(safePage * safeSize, total);
        int to = Math.min(from + safeSize, total);
        List<PlatformUserView> slice = from < to ? sorted.subList(from, to) : List.of();
        return new PageImpl<>(slice, PageRequest.of(safePage, safeSize), total);
    }

    static boolean matchesUserSearch(PlatformUserView row, String search) {
        if (search == null || search.isBlank()) {
            return true;
        }
        String q = search.trim().toLowerCase();
        return fieldContains(row.fullName(), q)
                || fieldContains(row.companyName(), q)
                || fieldContains(row.email(), q)
                || fieldContains(row.builderCompanyName(), q)
                || fieldContains(row.role(), q)
                || row.buildingAccess().stream().anyMatch(access -> fieldContains(access, q));
    }

    static boolean matchesUserActiveFilter(PlatformUserView row, Boolean activeFilter) {
        if (activeFilter == null) {
            return true;
        }
        boolean active = row.active() != null && row.active();
        return active == activeFilter;
    }

    static boolean matchesUserProjectFilter(PlatformUserView row, UUID projectId) {
        if (projectId == null) {
            return true;
        }
        return row.projectIds().contains(projectId);
    }

    private static boolean fieldContains(String value, String q) {
        return value != null && value.toLowerCase().contains(q);
    }

    private List<PlatformUserView> loadAllUserRows() {
        List<PlatformUserView> rows = new ArrayList<>();
        for (Builder platformAdmin : builderRepository.findAllPlatformAdminsOrderByEmailAsc()) {
            rows.add(PlatformUserView.fromPlatformAdmin(platformAdmin));
        }
        Set<UUID> seen = new LinkedHashSet<>();
        for (User user : userRepository.findAllByOrderByFullNameAsc()) {
            if (user.getBuilder() != null && user.getBuilder().isPlatformAdmin()) {
                continue;
            }
            if (seen.contains(user.getId())) {
                continue;
            }
            List<UserProjectAssignment> memberships = userProjectAssignmentService.listMemberships(user.getId());
            if (memberships.isEmpty()) {
                rows.add(PlatformUserView.unassigned(user, canSuperAdminDeleteUser(user.getId())));
            } else {
                rows.add(toPlatformUserView(user, memberships));
            }
            seen.add(user.getId());
        }
        return rows;
    }

    @Transactional(readOnly = true)
    public boolean canSuperAdminDeleteUser(UUID userId) {
        if (userRepository.findById(userId).filter(this::isStaffUserRecord).isEmpty()) {
            return false;
        }
        if (userProjectAssignmentService.hasAnyMembership(userId)) {
            return false;
        }
        if (userBuildingAssignmentRepository.countByUser_Id(userId) > 0) {
            return false;
        }
        if (partnerFlatAssignmentRepository.countByUser_Id(userId) > 0) {
            return false;
        }
        if (userBuildingVaultAccessRepository.countByUser_Id(userId) > 0) {
            return false;
        }
        return bookingRepository.countByExecutive_Id(userId) == 0;
    }

    public static String normalizeUsersSort(String sort) {
        if (sort == null || sort.isBlank()) {
            return "companyName";
        }
        String key = sort.trim();
        return USERS_SORT_FIELDS.contains(key) ? key : "companyName";
    }

    public static boolean normalizeUsersSortAscending(String sortKey, String dir) {
        if (dir != null && !dir.isBlank()) {
            return "asc".equalsIgnoreCase(dir.trim());
        }
        return switch (sortKey) {
            case "fullName", "companyName", "email", "project", "role" -> true;
            default -> false;
        };
    }

    private static Comparator<PlatformUserView> comparatorForUsersSort(String sortKey, boolean ascending) {
        Comparator<PlatformUserView> comparator =
                switch (sortKey) {
                    case "fullName" ->
                            Comparator.comparing(
                                    PlatformUserView::fullName,
                                    Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
                    case "email" ->
                            Comparator.comparing(
                                    PlatformUserView::email,
                                    Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
                    case "project" ->
                            Comparator.comparing(
                                    PlatformUserView::builderCompanyName,
                                    Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
                    case "role" ->
                            Comparator.comparing(
                                    PlatformUserView::role,
                                    Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
                    case "active" ->
                            Comparator.comparing(
                                    PlatformUserView::active,
                                    Comparator.nullsLast(Comparator.naturalOrder()));
                    case "lastLoginAt" ->
                            Comparator.comparing(
                                    PlatformUserView::lastLoginAt,
                                    Comparator.nullsLast(Comparator.naturalOrder()));
                    case "createdAt" ->
                            Comparator.comparing(
                                    PlatformUserView::createdAt,
                                    Comparator.nullsLast(Comparator.naturalOrder()));
                    default ->
                            Comparator.comparing(
                                    PlatformUserView::companyName,
                                    Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
                };
        return ascending ? comparator : comparator.reversed();
    }

    @Transactional(readOnly = true)
    public List<User> listUsersAvailableForProject(UUID builderId) {
        return userProjectAssignmentService.listUsersAvailableForProject(builderId);
    }

    @Transactional(readOnly = true)
    public long countUsersAvailableForProject(UUID builderId) {
        return userProjectAssignmentService.countUsersAvailableForProject(builderId);
    }

    @Transactional(readOnly = true)
    public List<AssignableUserOption> searchUsersAvailableForProject(UUID builderId, String q, int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), 50);
        String term = q == null ? "" : q.trim();
        return userProjectAssignmentService.searchUsersAvailableForProject(builderId, term, safeLimit).stream()
                .map(AssignableUserOption::from)
                .toList();
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
    public void deleteUnassignedUser(UUID userId, String actor) {
        User user = requireUser(userId);
        if (!canSuperAdminDeleteUser(userId)) {
            throw new IllegalArgumentException(buildDeleteBlockedMessage(userId));
        }
        String email = user.getEmail();
        userRepository.delete(user);
        auditService.log("USER_DELETED", "user", userId.toString(), null, actor + " deleted " + email);
    }

    private String buildDeleteBlockedMessage(UUID userId) {
        if (userProjectAssignmentService.hasAnyMembership(userId)) {
            return "Cannot delete a user who is assigned to a project. Remove them from the project first.";
        }
        if (userBuildingAssignmentRepository.countByUser_Id(userId) > 0) {
            return "Cannot delete this user because they still have building access assigned.";
        }
        if (partnerFlatAssignmentRepository.countByUser_Id(userId) > 0) {
            return "Cannot delete this user because they are assigned to partner flats.";
        }
        if (userBuildingVaultAccessRepository.countByUser_Id(userId) > 0) {
            return "Cannot delete this user because they still have vault access assigned.";
        }
        if (bookingRepository.countByExecutive_Id(userId) > 0) {
            return "Cannot delete this user because they are linked to bookings.";
        }
        return "This user cannot be deleted.";
    }

    private boolean isStaffUserRecord(User user) {
        return user.getBuilder() == null || !user.getBuilder().isPlatformAdmin();
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
            boolean assignedToProject = userProjectAssignmentService.hasAnyMembership(entity.getId());
            if (assignedToProject) {
                applyAssignedUserProfileUpdate(entity, form, password);
            } else {
                if (userRepository.existsByEmailIgnoreCaseAndIdNot(form.getEmail(), entity.getId())) {
                    throw new IllegalArgumentException("Email is already used by another user.");
                }
                if (!password.equals(entity.getAdminVisiblePassword())) {
                    entity.setPasswordHash(passwordEncoder.encode(password));
                }
                entity.setAdminVisiblePassword(password);
                entity.setFullName(form.getFullName().trim());
                entity.setEmail(form.getEmail().trim().toLowerCase(Locale.ROOT));
                entity.setActive(form.getActive() != null ? form.getActive() : true);
                UserContactFields.applyFromForm(entity, form);
            }
        }
        if (created) {
            entity.setFullName(form.getFullName().trim());
            entity.setEmail(form.getEmail().trim().toLowerCase(Locale.ROOT));
            entity.setActive(form.getActive() != null ? form.getActive() : true);
            UserContactFields.applyFromForm(entity, form);
        }
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
        List<UUID> projectIds =
                memberships.stream()
                        .map(a -> a.getBuilder().getId())
                        .distinct()
                        .toList();
        return PlatformUserView.from(user, projects, roleLabel, buildingAccess, projectIds);
    }

    /** Profile fields only; name, email, and role stay as-is (change those under Projects → Partners). */
    private void applyAssignedUserProfileUpdate(User entity, User form, String password) {
        if (!password.equals(entity.getAdminVisiblePassword())) {
            entity.setPasswordHash(passwordEncoder.encode(password));
        }
        entity.setAdminVisiblePassword(password);
        entity.setFullName(form.getFullName().trim());
        entity.setCompanyName(form.getCompanyName().trim());
        UserContactFields.applyFromForm(entity, form);
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
