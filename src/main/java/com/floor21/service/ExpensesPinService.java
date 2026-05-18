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
public class ExpensesPinService {

    private static final Pattern PIN_PATTERN = Pattern.compile("^\\d{4,8}$");

    /** Shown on expenses unlock / PIN screens. */
    public static final String PIN_REQUIREMENTS_HINT = "4–8 digits, numbers only (not your login or vault PIN).";

    private final BuilderRepository builderRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public boolean hasPinConfigured() {
        String hash = findBuilder().getExpensesPinHash();
        return hash != null && !hash.isBlank();
    }

    @Transactional(readOnly = true)
    public boolean verifyPin(String pin) {
        Builder builder = findBuilder();
        String hash = builder.getExpensesPinHash();
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
        builder.setExpensesPinHash(passwordEncoder.encode(pin));
        builderRepository.save(builder);
    }

    @Transactional
    public void changePin(String currentPin, String newPin) {
        if (!verifyPin(currentPin)) {
            throw new IllegalArgumentException("Current expenses PIN is incorrect.");
        }
        if (currentPin.equals(newPin)) {
            throw new IllegalArgumentException("New PIN must be different from the current PIN.");
        }
        setPin(newPin);
    }

    @Transactional
    public void resetPinWithAccountPassword(String accountPassword, String newPin) {
        verifyAccountPassword(accountPassword);
        validatePinFormat(newPin);
        if (hasPinConfigured() && verifyPin(newPin)) {
            throw new IllegalArgumentException("New PIN must be different from the current expenses PIN.");
        }
        setPin(newPin);
    }

    public void verifyAccountPassword(String accountPassword) {
        if (accountPassword == null || accountPassword.isBlank()) {
            throw new IllegalArgumentException("Enter your account password.");
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof Floor21UserPrincipal principal)) {
            throw new IllegalStateException("You must be signed in to reset the expenses PIN.");
        }
        if (!passwordEncoder.matches(accountPassword, principal.getPassword())) {
            throw new IllegalArgumentException("Account password is incorrect.");
        }
    }

    public static void validatePinFormat(String pin) {
        if (pin == null || !PIN_PATTERN.matcher(pin.trim()).matches()) {
            throw new IllegalArgumentException("Expenses PIN must be 4–8 digits.");
        }
    }

    private Builder findBuilder() {
        UUID builderId = TenantContext.requireBuilderId();
        return builderRepository.findById(builderId).orElseThrow();
    }
}
