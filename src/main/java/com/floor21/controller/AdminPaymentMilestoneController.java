package com.floor21.controller;

import com.floor21.entity.Building;
import com.floor21.entity.PaymentSlabTemplate;
import com.floor21.repository.BuildingRepository;
import com.floor21.service.PaymentMilestoneExcelService;
import com.floor21.service.PaymentSlabTemplateService;
import java.beans.PropertyEditorSupport;
import java.io.IOException;
import java.util.Collections;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/payment-milestones")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class AdminPaymentMilestoneController {

    private final PaymentSlabTemplateService paymentSlabTemplateService;
    private final PaymentMilestoneExcelService paymentMilestoneExcelService;
    private final BuildingRepository buildingRepository;

    @InitBinder("template")
    public void templateBinder(WebDataBinder binder) {
        binder.registerCustomEditor(
                UUID.class,
                "building.id",
                new PropertyEditorSupport() {
                    @Override
                    public void setAsText(String text) {
                        if (text == null || text.isBlank()) {
                            setValue(null);
                        } else {
                            setValue(UUID.fromString(text));
                        }
                    }
                });
    }

    @GetMapping
    public String list(@RequestParam(required = false) UUID buildingId, Model model) {
        model.addAttribute("pageTitle", "Payment milestones");
        model.addAttribute("buildings", buildingRepository.findAllForPlatformAdminOrderByBuilderAndName());
        model.addAttribute("selectedBuildingId", buildingId);
        if (buildingId != null) {
            model.addAttribute("templates", paymentSlabTemplateService.listForBuildingAdmin(buildingId));
            buildingRepository
                    .findByIdWithBuilder(buildingId)
                    .ifPresent(b -> model.addAttribute("selectedBuilding", b));
        } else {
            model.addAttribute("templates", Collections.emptyList());
        }
        return "admin/payment-milestones/list";
    }

    @GetMapping("/new")
    public String form(@RequestParam UUID buildingId, Model model) {
        model.addAttribute("pageTitle", "New payment milestone");
        model.addAttribute("buildings", buildingRepository.findAllForPlatformAdminOrderByBuilderAndName());
        model.addAttribute("selectedBuildingId", buildingId);
        buildingRepository.findByIdWithBuilder(buildingId).ifPresent(b -> model.addAttribute("selectedBuilding", b));
        PaymentSlabTemplate template = new PaymentSlabTemplate();
        Building buildingRef = new Building();
        buildingRef.setId(buildingId);
        template.setBuilding(buildingRef);
        model.addAttribute("template", template);
        return "admin/payment-milestones/form";
    }

    @GetMapping("/{id}/edit")
    public String edit(@PathVariable UUID id, @RequestParam UUID buildingId, Model model) {
        model.addAttribute("pageTitle", "Edit payment milestone");
        model.addAttribute("buildings", buildingRepository.findAllForPlatformAdminOrderByBuilderAndName());
        model.addAttribute("selectedBuildingId", buildingId);
        buildingRepository.findByIdWithBuilder(buildingId).ifPresent(b -> model.addAttribute("selectedBuilding", b));
        model.addAttribute("template", paymentSlabTemplateService.getForBuildingAdmin(id, buildingId));
        return "admin/payment-milestones/form";
    }

    @PostMapping("/save")
    public String save(
            @ModelAttribute PaymentSlabTemplate template,
            @RequestParam UUID buildingId,
            RedirectAttributes ra) {
        paymentSlabTemplateService.saveForBuildingAdmin(template, buildingId);
        ra.addFlashAttribute("successMessage", "Payment milestone saved");
        return "redirect:/admin/payment-milestones?buildingId=" + buildingId;
    }

    @GetMapping("/import-template")
    public ResponseEntity<byte[]> downloadImportTemplate() throws IOException {
        byte[] body = paymentMilestoneExcelService.buildImportTemplate();
        ContentDisposition disposition =
                ContentDisposition.attachment().filename("payment_milestones_template.xlsx").build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(body);
    }

    @PostMapping("/import")
    public String importExcel(
            @RequestParam UUID buildingId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(name = "replace", defaultValue = "false") boolean replaceExisting,
            RedirectAttributes ra) {
        try {
            int imported = paymentMilestoneExcelService.importForBuilding(buildingId, file, replaceExisting);
            ra.addFlashAttribute(
                    "successMessage",
                    "Imported " + imported + " milestone" + (imported == 1 ? "" : "s") + " from Excel.");
        } catch (IllegalArgumentException ex) {
            ra.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/payment-milestones?buildingId=" + buildingId;
    }
}
