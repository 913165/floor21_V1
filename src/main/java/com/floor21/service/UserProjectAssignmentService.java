package com.floor21.service;

import com.floor21.entity.Builder;
import com.floor21.entity.User;
import com.floor21.entity.UserProjectAssignment;
import com.floor21.repository.UserProjectAssignmentRepository;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserProjectAssignmentService {

    private final UserProjectAssignmentRepository assignmentRepository;

    @Transactional(readOnly = true)
    public boolean hasMembership(UUID userId, UUID builderId) {
        return assignmentRepository.existsByUser_IdAndBuilder_Id(userId, builderId);
    }

    @Transactional(readOnly = true)
    public boolean hasAnyMembership(UUID userId) {
        return assignmentRepository.findByUser_IdWithBuilder(userId).stream().findAny().isPresent();
    }

    @Transactional(readOnly = true)
    public String getRole(UUID userId, UUID builderId) {
        return assignmentRepository
                .findByUser_IdAndBuilder_Id(userId, builderId)
                .map(UserProjectAssignment::getRole)
                .orElseThrow(() -> new IllegalArgumentException("User is not linked to this project."));
    }

    @Transactional(readOnly = true)
    public Optional<UserProjectAssignment> resolvePrimaryMembership(UUID userId) {
        return assignmentRepository.findFirstByUser_IdOrderByBuilder_CompanyNameAsc(userId);
    }

    @Transactional(readOnly = true)
    public List<UserProjectAssignment> listMemberships(UUID userId) {
        return assignmentRepository.findByUser_IdWithBuilder(userId);
    }

    @Transactional(readOnly = true)
    public List<UserProjectAssignment> listForProject(UUID builderId) {
        return assignmentRepository.findByBuilder_IdWithUser(builderId);
    }

    @Transactional(readOnly = true)
    public List<User> listUsersAvailableForProject(UUID builderId) {
        return assignmentRepository.findUsersNotOnProject(builderId);
    }

    @Transactional(readOnly = true)
    public long countUsersAvailableForProject(UUID builderId) {
        return assignmentRepository.countUsersNotOnProject(builderId);
    }

    @Transactional(readOnly = true)
    public List<User> searchUsersAvailableForProject(UUID builderId, String q, int limit) {
        String term = q == null ? "" : q.trim();
        return assignmentRepository.searchUsersNotOnProject(
                builderId, term, PageRequest.of(0, limit));
    }

    @Transactional(readOnly = true)
    public List<User> listActiveUsersForProject(UUID builderId) {
        return assignmentRepository.findByBuilder_IdWithUser(builderId).stream()
                .map(UserProjectAssignment::getUser)
                .filter(u -> u.getActive() == null || u.getActive())
                .toList();
    }

    @Transactional(readOnly = true)
    public long countForProject(UUID builderId) {
        return assignmentRepository.countByBuilder_Id(builderId);
    }

    @Transactional
    public void removeMembership(UUID builderId, UUID userId) {
        if (!hasMembership(userId, builderId)) {
            throw new IllegalArgumentException("Partner not found on this project.");
        }
        assignmentRepository.deleteByUser_IdAndBuilder_Id(userId, builderId);
    }

    @Transactional
    public UserProjectAssignment saveMembership(UUID builderId, User user, Builder builder, String role) {
        String normalizedRole = StaffBuildingAccessService.normalizeRole(role);
        UserProjectAssignment assignment =
                assignmentRepository
                        .findByUser_IdAndBuilder_Id(user.getId(), builderId)
                        .orElseGet(() -> new UserProjectAssignment(user, builder, normalizedRole));
        assignment.setRole(normalizedRole);
        return assignmentRepository.save(assignment);
    }

    @Transactional(readOnly = true)
    public Set<UUID> listProjectIdsForUser(UUID staffUserId, UUID builderLoginId) {
        if (staffUserId != null) {
            return listMemberships(staffUserId).stream()
                    .map(a -> a.getBuilder().getId())
                    .collect(Collectors.toSet());
        }
        if (builderLoginId != null) {
            return Set.of(builderLoginId);
        }
        return Set.of();
    }

    @Transactional(readOnly = true)
    public boolean canUserAccessProject(UUID staffUserId, UUID builderLoginId, UUID projectId) {
        if (staffUserId != null) {
            return hasMembership(staffUserId, projectId);
        }
        if (builderLoginId != null) {
            return builderLoginId.equals(projectId);
        }
        return false;
    }

    @Transactional(readOnly = true)
    public String formatProjectNames(UUID userId) {
        List<UserProjectAssignment> memberships = listMemberships(userId);
        if (memberships.isEmpty()) {
            return "—";
        }
        return memberships.stream()
                .map(a -> a.getBuilder().getCompanyName())
                .distinct()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .reduce((a, b) -> a + ", " + b)
                .orElse("—");
    }
}
