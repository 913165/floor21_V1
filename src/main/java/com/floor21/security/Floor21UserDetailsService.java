package com.floor21.security;

import com.floor21.entity.Builder;
import com.floor21.entity.User;
import com.floor21.entity.UserProjectAssignment;
import com.floor21.repository.BuilderRepository;
import com.floor21.repository.UserRepository;
import com.floor21.service.UserProjectAssignmentService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class Floor21UserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;
    private final BuilderRepository builderRepository;
    private final UserProjectAssignmentService userProjectAssignmentService;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return userRepository
                .findFirstByEmailIgnoreCaseAndActiveTrue(username)
                .map(this::staffPrincipal)
                .or(() -> builderRepository.findByEmailIgnoreCase(username).map(this::builderPrincipal))
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
    }

    private UserDetails staffPrincipal(User staff) {
        UserProjectAssignment membership =
                userProjectAssignmentService
                        .resolvePrimaryMembership(staff.getId())
                        .orElseThrow(
                                () -> new UsernameNotFoundException("User is not assigned to a project yet."));
        UUID builderId = membership.getBuilder().getId();
        String role = "ROLE_" + membership.getRole();
        var delegate =
                new org.springframework.security.core.userdetails.User(
                        staff.getEmail(),
                        staff.getPasswordHash(),
                        List.of(new SimpleGrantedAuthority(role)));
        return new Floor21UserPrincipal(
                builderId, staff.getId(), staff.getEmail(), staff.getPasswordHash(), false, delegate);
    }

    private UserDetails builderPrincipal(Builder builder) {
        if (builder.getEmail() == null
                || builder.getEmail().isBlank()
                || builder.getPasswordHash() == null
                || builder.getPasswordHash().isBlank()) {
            throw new UsernameNotFoundException("No login on project record; use a user account.");
        }
        boolean active = Boolean.TRUE.equals(builder.getActive());
        if (!active) {
            throw new UsernameNotFoundException("Inactive builder");
        }
        if (builder.isPlatformAdmin()) {
            var delegate =
                    new org.springframework.security.core.userdetails.User(
                            builder.getEmail(),
                            builder.getPasswordHash(),
                            List.of(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN")));
            return new Floor21UserPrincipal(null, null, builder.getEmail(), builder.getPasswordHash(), true, delegate);
        }
        UUID builderId = builder.getId();
        var delegate =
                new org.springframework.security.core.userdetails.User(
                        builder.getEmail(),
                        builder.getPasswordHash(),
                        List.of(new SimpleGrantedAuthority("ROLE_BUILDER_ADMIN")));
        return new Floor21UserPrincipal(builderId, null, builder.getEmail(), builder.getPasswordHash(), false, delegate);
    }
}
