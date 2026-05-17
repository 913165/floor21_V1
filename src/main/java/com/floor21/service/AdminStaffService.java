package com.floor21.service;

import com.floor21.entity.Builder;
import com.floor21.entity.User;
import com.floor21.repository.BuilderRepository;
import com.floor21.repository.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminStaffService {

    private static final String ROLE_EXECUTIVE = "EXECUTIVE";

    private final UserRepository userRepository;
    private final BuilderRepository builderRepository;
    private final PasswordEncoder passwordEncoder;
    private final PlatformAuditService auditService;

    @Transactional(readOnly = true)
    public Builder requireTenantBuilder(UUID builderId) {
        return builderRepository
                .findById(builderId)
                .filter(b -> !b.isPlatformAdmin())
                .orElseThrow(() -> new IllegalArgumentException("Builder not found."));
    }

    @Transactional(readOnly = true)
    public List<User> listStaff(UUID builderId) {
        requireTenantBuilder(builderId);
        return userRepository.findByBuilder_IdOrderByFullNameAsc(builderId);
    }

    @Transactional(readOnly = true)
    public User getStaff(UUID builderId, UUID userId) {
        return userRepository
                .findById(userId)
                .filter(u -> u.getBuilder().getId().equals(builderId))
                .orElseThrow(() -> new IllegalArgumentException("Staff member not found."));
    }

    @Transactional
    public User save(UUID builderId, User form, String rawPassword) {
        Builder builder = requireTenantBuilder(builderId);
        User entity;
        if (form.getId() == null) {
            if (rawPassword == null || rawPassword.isBlank()) {
                throw new IllegalArgumentException("Password is required for new staff.");
            }
            if (userRepository.existsByBuilder_IdAndEmailIgnoreCase(builderId, form.getEmail())) {
                throw new IllegalArgumentException("Email is already used for this builder.");
            }
            entity = new User();
            entity.setBuilder(builder);
            entity.setCreatedAt(Instant.now());
            entity.setRole(ROLE_EXECUTIVE);
            entity.setPasswordHash(passwordEncoder.encode(rawPassword));
        } else {
            entity = getStaff(builderId, form.getId());
            if (userRepository.existsByBuilder_IdAndEmailIgnoreCaseAndIdNot(
                    builderId, form.getEmail(), entity.getId())) {
                throw new IllegalArgumentException("Email is already used for this builder.");
            }
            if (rawPassword != null && !rawPassword.isBlank()) {
                entity.setPasswordHash(passwordEncoder.encode(rawPassword));
            }
        }
        entity.setFullName(form.getFullName().trim());
        entity.setEmail(form.getEmail().trim().toLowerCase(Locale.ROOT));
        entity.setActive(form.getActive() != null ? form.getActive() : true);
        User saved = userRepository.save(entity);
        auditService.log(
                form.getId() == null ? "STAFF_CREATED" : "STAFF_UPDATED",
                "user",
                saved.getId().toString(),
                builderId,
                saved.getEmail());
        return saved;
    }
}
