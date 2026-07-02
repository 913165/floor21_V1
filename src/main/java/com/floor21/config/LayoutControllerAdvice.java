package com.floor21.config;

import com.floor21.security.Floor21UserPrincipal;
import com.floor21.security.ImpersonationSession;
import com.floor21.service.AccountService;
import com.floor21.service.TenantVaultConfigService;
import com.floor21.service.VaultAccessService;
import com.floor21.util.FlatUnitTypes;
import com.floor21.util.ResidentialBhkTypes;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import java.time.Duration;
import java.util.List;

/** Exposes request path and signed-in account labels for layout fragments. */
@ControllerAdvice
@RequiredArgsConstructor
public class LayoutControllerAdvice {

    private final AccountService accountService;
    private final VaultAccessService vaultAccessService;
    private final TenantVaultConfigService tenantVaultConfigService;

    @Value("${server.servlet.session.timeout:3600s}")
    private Duration serverSessionTimeout;

    @ModelAttribute("sessionTimeoutSeconds")
    public long sessionTimeoutSeconds() {
        return serverSessionTimeout.toSeconds();
    }

    @ModelAttribute("residentialBhkTypes")
    public List<String> residentialBhkTypes() {
        return ResidentialBhkTypes.all();
    }

    @ModelAttribute("perFloorLayoutBhkTypes")
    public List<String> perFloorLayoutBhkTypes() {
        return ResidentialBhkTypes.perFloorLayout();
    }

    @ModelAttribute("flatAmenityTypes")
    public List<String> flatAmenityTypes() {
        return FlatUnitTypes.amenityTypes();
    }

    @ModelAttribute("flatAdminUnitTypes")
    public List<String> flatAdminUnitTypes() {
        return FlatUnitTypes.allForAdminSelect();
    }

    @ModelAttribute("turboCacheControl")
    public String turboCacheControl() {
        return null;
    }

    @ModelAttribute("navServletPath")
    public String navServletPath(HttpServletRequest request) {
        if (request == null) {
            return "";
        }
        String path = request.getServletPath();
        return path != null ? path : "";
    }

    /** Theme key for sidebar/topbar chrome: overview, property, sales, receipts, slabs, banking, vault, expenses, platform. */
    @ModelAttribute("navArea")
    public String navArea(HttpServletRequest request) {
        return resolveNavArea(navServletPath(request));
    }

    @ModelAttribute("navAreaLabel")
    public String navAreaLabel(HttpServletRequest request) {
        return resolveNavAreaLabel(resolveNavArea(navServletPath(request)));
    }

    @ModelAttribute("vaultMenuVisible")
    public boolean vaultMenuVisible() {
        return vaultAccessService.canCurrentUserAccessVault();
    }

    @ModelAttribute("tenantVaultConfigVisible")
    public boolean tenantVaultConfigVisible() {
        return tenantVaultConfigService.canCurrentUserManageTenantVaultConfig();
    }

    static String resolveNavArea(String path) {
        if (path == null) {
            path = "";
        }
        if (path.startsWith("/admin")) {
            return "platform";
        }
        if (path.startsWith("/vault")) {
            return "vault";
        }
        if (path.startsWith("/docs-locker")) {
            return "vault";
        }
        if (path.startsWith("/settings/vault-access")) {
            return "platform";
        }
        if (path.startsWith("/expenses")) {
            return "expenses";
        }
        if (path.startsWith("/bank-accounts")) {
            return "banking";
        }
        if (path.startsWith("/bookings/payment-schedule")
                || path.startsWith("/bookings/allottee-ledger")) {
            return "slabs";
        }
        if (path.startsWith("/receipts")) {
            return "receipts";
        }
        if (path.startsWith("/buildings") || path.startsWith("/clients")) {
            return "property";
        }
        if (path.startsWith("/bookings")
                || path.startsWith("/brokers")
                || path.startsWith("/cancellations")) {
            return "sales";
        }
        if (path.startsWith("/dashboard") || path.startsWith("/profile")) {
            return "overview";
        }
        return "overview";
    }

    static String resolveNavAreaLabel(String area) {
        return switch (area) {
            case "property" -> "Property";
            case "sales" -> "Sales";
            case "receipts" -> "Receipts";
            case "slabs" -> "Payment schedule";
            case "banking" -> "Banking";
            case "vault" -> "Vault";
            case "expenses" -> "Expenses";
            case "platform" -> "Platform admin";
            default -> "Home";
        };
    }

    @ModelAttribute("navAccountLabel")
    public String navAccountLabel(Authentication authentication) {
        if (!isSignedIn(authentication)) {
            return "";
        }
        try {
            return accountService.currentDisplayName();
        } catch (Exception ignored) {
            return authentication.getName();
        }
    }

    @ModelAttribute("navAccountEmail")
    public String navAccountEmail(Authentication authentication) {
        if (!isSignedIn(authentication)) {
            return "";
        }
        return authentication.getName();
    }

    @ModelAttribute
    public void impersonationFlags(HttpServletRequest request, org.springframework.ui.Model model) {
        HttpSession session = request != null ? request.getSession(false) : null;
        if (session != null && Boolean.TRUE.equals(session.getAttribute(ImpersonationSession.ACTIVE))) {
            model.addAttribute("impersonationActive", true);
            model.addAttribute(
                    "impersonationBuilderName", session.getAttribute(ImpersonationSession.BUILDER_NAME));
            model.addAttribute(
                    "impersonationStaffName", session.getAttribute(ImpersonationSession.STAFF_NAME));
        }
    }

    @ModelAttribute("navAccountInitial")
    public String navAccountInitial(Authentication authentication) {
        if (!isSignedIn(authentication)) {
            return "";
        }
        String label = navAccountLabel(authentication);
        if (label == null || label.isBlank()) {
            String email = authentication.getName();
            return email != null && !email.isBlank()
                    ? String.valueOf(Character.toUpperCase(email.charAt(0)))
                    : "?";
        }
        return String.valueOf(Character.toUpperCase(label.trim().charAt(0)));
    }

    private static boolean isSignedIn(Authentication authentication) {
        return authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken)
                && authentication.getPrincipal() instanceof Floor21UserPrincipal;
    }
}
