package com.floor21.controller;

import com.floor21.entity.MilestoneSampleTemplate;
import com.floor21.security.Floor21UserPrincipal;
import com.floor21.service.MilestoneSampleTemplateService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
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
@RequestMapping("/admin/milestone-sample-templates")
@RequiredArgsConstructor
public class MilestoneSampleTemplateController {

    private final MilestoneSampleTemplateService milestoneSampleTemplateService;

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','BUILDER_ADMIN','EXECUTIVE')")
    public String list(Model model) {
        model.addAttribute("pageTitle", "Sample Milestone Templates");
        model.addAttribute("templates", milestoneSampleTemplateService.listAll());
        model.addAttribute("canManage", isPlatformAdmin());
        return "admin/milestone-samples/list";
    }

    @GetMapping("/{id}/download")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','BUILDER_ADMIN','EXECUTIVE')")
    public ResponseEntity<byte[]> download(@PathVariable UUID id) {
        MilestoneSampleTemplate template = milestoneSampleTemplateService.get(id);
        ContentDisposition disposition =
                ContentDisposition.attachment().filename(template.getFileName()).build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(template.getFileContent());
    }

    @PostMapping("/upload")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String upload(
            @RequestParam String name,
            @RequestParam(required = false) String description,
            @RequestParam("file") MultipartFile file,
            RedirectAttributes ra) {
        try {
            milestoneSampleTemplateService.saveUpload(name, description, file);
            ra.addFlashAttribute("successMessage", "Sample template uploaded.");
        } catch (IllegalArgumentException ex) {
            ra.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/milestone-sample-templates";
    }

    @PostMapping("/{id}/delete")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String delete(@PathVariable UUID id, RedirectAttributes ra) {
        milestoneSampleTemplateService.delete(id);
        ra.addFlashAttribute("successMessage", "Sample template removed.");
        return "redirect:/admin/milestone-sample-templates";
    }

    private static boolean isPlatformAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null
                && auth.getPrincipal() instanceof Floor21UserPrincipal principal
                && principal.isSuperAdmin();
    }
}
