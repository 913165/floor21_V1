package com.floor21.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "builder_id")
    private Builder builder;

    @Column(name = "full_name", nullable = false, length = 200)
    private String fullName;

    @Column(nullable = false, length = 150)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    /** Plaintext copy of last admin-set login password (platform Users screen only). */
    @Column(name = "admin_visible_password", length = 255)
    private String adminVisiblePassword;

    @Column(nullable = false, length = 50)
    private String role;

    @Column(name = "is_active")
    private Boolean active = true;

    /** Builder admins only: Floor21 admin can enable Vault menu and pages per user. */
    @Column(name = "vault_access_enabled", nullable = false)
    private Boolean vaultAccessEnabled = false;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "pan_number", length = 20)
    private String panNumber;

    @Column(name = "tan_number", length = 20)
    private String tanNumber;

    @Column(name = "gst_number", length = 20)
    private String gstNumber;

    @Column(name = "mobile_number", length = 20)
    private String mobileNumber;

    @Column(columnDefinition = "TEXT")
    private String address;

    @Column(name = "address_state", length = 100)
    private String addressState;

    @Column(name = "address_pin", length = 6)
    private String addressPin;
}
