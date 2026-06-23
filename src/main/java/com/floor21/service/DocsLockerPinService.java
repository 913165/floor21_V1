package com.floor21.service;

import com.floor21.entity.Builder;
import com.floor21.repository.BuilderRepository;
import com.floor21.security.Floor21UserPrincipal;
import com.floor21.security.TenantContext;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DocsLockerPinService {

    private static final Pattern PIN_PATTERN = Pattern.compile("^\\d{4,8}$");

    private final BuilderRepository builderRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public boolean hasPinConfigured() {
        String hash = findBuilder().getDocsLockerPinHash();
        return hash != null && !hash.isBlank();
    }

    @Transactional(readOnly = true)
    public boolean verifyPin(String pin) {
        Builder builder = findBuilder();
        String hash = builder.getDocsLockerPinHash();
        if (hash == null || hash.isBlank()) {
            return false;
        }
        validatePinFormat(pin);
        return passwordEncoder.matches(pin, hash);
    }

    @Transactional
    public void setPin(String pin) {
        validatePinFormat(pin);
        assertDistinctFromPin2(pin);
        Builder builder = findBuilder();
        builder.setDocsLockerPinHash(passwordEncoder.encode(pin));
        builderRepository.save(builder);
    }

    @Transactional
    public void changePin(String currentPin, String newPin) {
        if (!verifyPin(currentPin)) {
            throw new IllegalArgumentException("Current PIN1 is incorrect.");
        }
        if (currentPin.equals(newPin)) {
            throw new IllegalArgumentException("New PIN must be different from the current PIN1.");
        }
        setPin(newPin);
    }

    @Transactional
    public void resetPinWithAccountPassword(String accountPassword, String newPin) {
        verifyAccountPassword(accountPassword);
        validatePinFormat(newPin);
        assertDistinctFromPin2(newPin);
        if (hasPinConfigured() && verifyPin(newPin)) {
            throw new IllegalArgumentException("New PIN must be different from the current PIN1.");
        }
        Builder builder = findBuilder();
        builder.setDocsLockerPinHash(passwordEncoder.encode(newPin));
        builderRepository.save(builder);
    }

    public void verifyAccountPassword(String accountPassword) {
        if (accountPassword == null || accountPassword.isBlank()) {
            throw new IllegalArgumentException("Enter your account password.");
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof Floor21UserPrincipal principal)) {
            throw new IllegalStateException("You must be signed in to change PIN1.");
        }
        if (!passwordEncoder.matches(accountPassword, principal.getPassword())) {
            throw new IllegalArgumentException("Account password is incorrect.");
        }
    }

    public static void validatePinFormat(String pin) {
        if (pin == null || !PIN_PATTERN.matcher(pin.trim()).matches()) {
            throw new IllegalArgumentException("PIN1 must be 4–8 digits.");
        }
    }

    private void assertDistinctFromPin2(String pin) {
        String pin2Hash = findBuilder().getVaultPinHash();
        if (pin2Hash != null && !pin2Hash.isBlank() && passwordEncoder.matches(pin, pin2Hash)) {
            throw new IllegalArgumentException("PIN1 must be different from PIN2.");
        }
    }

    private Builder findBuilder() {
        UUID builderId = TenantContext.requireBuilderId();
        return builderRepository.findById(builderId).orElseThrow();
    }
}
