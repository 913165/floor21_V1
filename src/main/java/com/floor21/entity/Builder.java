package com.floor21.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "builders")
public class Builder {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "company_name", nullable = false, length = 200)
    private String companyName;

    @Column(nullable = false, unique = true, length = 150)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    /** BCrypt (or delegating) hash of the vault PIN; separate from login password. */
    @Column(name = "vault_pin_hash", length = 255)
    private String vaultPinHash;

    /** Hash of the expenses hub PIN; separate from login and vault PIN. */
    @Column(name = "expenses_pin_hash", length = 255)
    private String expensesPinHash;

    @Column(length = 20)
    private String phone;

    @Column(name = "logo_url", length = 500)
    private String logoUrl;

    @Column(columnDefinition = "TEXT")
    private String address;

    @Column(length = 100)
    private String city;

    @Column(name = "is_active")
    private Boolean active = true;

    @Column(name = "is_platform_admin", nullable = false)
    private boolean platformAdmin;

    /** When false, no staff user sees the Vault menu for this builder. */
    @Column(name = "vault_enabled", nullable = false)
    private Boolean vaultEnabled = true;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;
}
