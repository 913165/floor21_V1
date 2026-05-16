package com.floor21.service;

import com.floor21.entity.Builder;
import com.floor21.repository.BuilderRepository;
import com.floor21.security.TenantContext;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class VaultPinService {

    private static final Pattern PIN_PATTERN = Pattern.compile("^\\d{4,8}$");

    private final BuilderRepository builderRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public boolean hasPinConfigured() {
        String hash = findBuilder().getVaultPinHash();
        return hash != null && !hash.isBlank();
    }

    @Transactional(readOnly = true)
    public boolean verifyPin(String pin) {
        Builder builder = findBuilder();
        String hash = builder.getVaultPinHash();
        if (hash == null || hash.isBlank()) {
            return false;
        }
        validatePinFormat(pin);
        return passwordEncoder.matches(pin, hash);
    }

    @Transactional
    public void setPin(String pin) {
        validatePinFormat(pin);
        Builder builder = findBuilder();
        builder.setVaultPinHash(passwordEncoder.encode(pin));
        builderRepository.save(builder);
    }

    @Transactional
    public void changePin(String currentPin, String newPin) {
        if (!verifyPin(currentPin)) {
            throw new IllegalArgumentException("Current vault PIN is incorrect.");
        }
        if (currentPin.equals(newPin)) {
            throw new IllegalArgumentException("New PIN must be different from the current PIN.");
        }
        setPin(newPin);
    }

    public static void validatePinFormat(String pin) {
        if (pin == null || !PIN_PATTERN.matcher(pin.trim()).matches()) {
            throw new IllegalArgumentException("Vault PIN must be 4–8 digits.");
        }
    }

    private Builder findBuilder() {
        UUID builderId = TenantContext.requireBuilderId();
        return builderRepository.findById(builderId).orElseThrow();
    }
}
