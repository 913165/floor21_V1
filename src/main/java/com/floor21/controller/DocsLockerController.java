package com.floor21.controller;

import com.floor21.entity.DocsLockerDocument;
import com.floor21.repository.BookingRepository;
import com.floor21.security.DocsLockerSession;
import com.floor21.security.TenantContext;
import com.floor21.service.BuildingService;
import com.floor21.service.DocsLockerDocumentService;
import com.floor21.service.DocsLockerPinService;
import jakarta.servlet.http.HttpSession;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/docs-locker")
@RequiredArgsConstructor
public class DocsLockerController {

    private final DocsLockerDocumentService documentService;
    private final DocsLockerPinService docsLockerPinService;
    private final BookingRepository bookingRepository;
    private final BuildingService buildingService;

    @Value("${floor21.vault.unlock-timeout-minutes:15}")
    private int unlockTimeoutMinutes;

    @GetMapping
    public String list(Model model, HttpSession session) {
        model.addAttribute("pageTitle", "Documents");
        model.addAttribute("unlockTimeoutMinutes", unlockTimeoutMinutes);
        model.addAttribute(
                "docsLockerUnlocked",
                DocsLockerSession.isUnlocked(
                        session, TenantContext.requireBuilderId(), Duration.ofMinutes(unlockTimeoutMinutes)));
        List<DocsLockerDocument> documents = documentService.listForCurrentBuilder();
        model.addAttribute("documents", documents);
        model.addAttribute("buildings", buildingService.listForVault());
        model.addAttribute("bookings", bookingRepository.findActiveForPaymentSchedule(TenantContext.requireBuilderId()));
        model.addAttribute("docsPinConfigured", docsLockerPinService.hasPinConfigured());
        return "docs-locker/list";
    }

    @PostMapping("/upload")
    public String upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String notes,
            @RequestParam(required = false) UUID bookingId,
            RedirectAttributes ra) {
        try {
            documentService.upload(file, title, notes, bookingId);
            ra.addFlashAttribute("successMessage", "Document uploaded.");
        } catch (IllegalArgumentException ex) {
            ra.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/docs-locker";
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> download(@PathVariable UUID id) {
        DocsLockerDocument doc = documentService.getDocument(id);
        Resource resource = documentService.loadFile(id);
        String filename = doc.getOriginalFilename() != null ? doc.getOriginalFilename() : "document";
        MediaType mediaType = MediaType.APPLICATION_OCTET_STREAM;
        if (doc.getContentType() != null && !doc.getContentType().isBlank()) {
            mediaType = MediaType.parseMediaType(doc.getContentType());
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(mediaType)
                .body(resource);
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable UUID id, RedirectAttributes ra) {
        try {
            documentService.delete(id);
            ra.addFlashAttribute("successMessage", "Document removed.");
        } catch (Exception ex) {
            ra.addFlashAttribute("errorMessage", "Could not remove document.");
        }
        return "redirect:/docs-locker";
    }

    @PostMapping("/lock")
    public String lock(HttpSession session, RedirectAttributes ra) {
        DocsLockerSession.clear(session);
        ra.addFlashAttribute("successMessage", "Locked.");
        return "redirect:/vault/unlock";
    }

    @GetMapping("/change-pin")
    public String changePinForm() {
        return "redirect:/vault/pins";
    }

    @PostMapping("/change-pin")
    public String changePinSubmit() {
        return "redirect:/vault/pins";
    }
}
