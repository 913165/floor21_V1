package com.floor21.security;

import java.util.UUID;
import lombok.Getter;
import org.springframework.security.core.userdetails.UserDetails;

@Getter
public class Floor21UserPrincipal implements UserDetails {

    public static final String SESSION_BUILDER_ID = "FLOOR21_BUILDER_ID";

    private final UUID builderId;
    /** Set when the account is a row in {@code users}; null for builder-table login. */
    private final UUID staffUserId;
    private final String email;
    private final String password;
    private final boolean superAdmin;
    private final org.springframework.security.core.userdetails.User delegate;

    public Floor21UserPrincipal(
            UUID builderId,
            UUID staffUserId,
            String email,
            String password,
            boolean superAdmin,
            org.springframework.security.core.userdetails.User delegate) {
        this.builderId = builderId;
        this.staffUserId = staffUserId;
        this.email = email;
        this.password = password;
        this.superAdmin = superAdmin;
        this.delegate = delegate;
    }

    @Override
    public java.util.Collection<? extends org.springframework.security.core.GrantedAuthority> getAuthorities() {
        return delegate.getAuthorities();
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return delegate.isAccountNonExpired();
    }

    @Override
    public boolean isAccountNonLocked() {
        return delegate.isAccountNonLocked();
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return delegate.isCredentialsNonExpired();
    }

    @Override
    public boolean isEnabled() {
        return delegate.isEnabled();
    }
}
