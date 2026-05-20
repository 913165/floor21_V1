package com.floor21.service;

import com.floor21.dto.PlatformUserView;
import com.floor21.entity.Builder;
import com.floor21.entity.User;
import com.floor21.repository.BuilderRepository;
import com.floor21.repository.UserRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminUserService {

    private final BuilderRepository builderRepository;
    private final UserRepository userRepository;
    private final StaffBuildingAccessService staffBuildingAccessService;

    @Transactional(readOnly = true)
    public List<PlatformUserView> listAllUsers() {
        List<PlatformUserView> rows = new ArrayList<>();
        for (Builder builder : builderRepository.findAllTenantsOrderByCompanyNameAsc()) {
            for (User user : userRepository.findByBuilder_IdOrderByFullNameAsc(builder.getId())) {
                rows.add(
                        PlatformUserView.from(
                                user,
                                builder,
                                staffBuildingAccessService.describeBuildingAccess(user.getId())));
            }
        }
        rows.sort(
                Comparator.comparing(PlatformUserView::builderCompanyName, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(PlatformUserView::fullName, String.CASE_INSENSITIVE_ORDER));
        return rows;
    }

    @Transactional(readOnly = true)
    public List<Builder> listTenantBuilders() {
        return builderRepository.findAllTenantsOrderByCompanyNameAsc();
    }

    @Transactional(readOnly = true)
    public User requireUser(UUID userId) {
        return userRepository
                .findById(userId)
                .filter(u -> u.getBuilder() != null && !u.getBuilder().isPlatformAdmin())
                .orElseThrow(() -> new IllegalArgumentException("User not found."));
    }
}
