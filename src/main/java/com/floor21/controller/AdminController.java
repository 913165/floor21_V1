package com.floor21.controller;

import com.floor21.entity.Building;
import com.floor21.entity.Builder;
import com.floor21.repository.BuilderRepository;
import com.floor21.service.BuildingService;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/builders")
@RequiredArgsConstructor
public class AdminController {

    private final BuilderRepository builderRepository;
    private final BuildingService buildingService;
    private final PasswordEncoder passwordEncoder;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("pageTitle", "Builders (Admin)");
        model.addAttribute("builders", builderRepository.findAllByOrderByCompanyNameAsc());
        return "admin/builders/list";
    }

    @GetMapping("/new")
    public String form(Model model) {
        model.addAttribute("pageTitle", "New Builder");
        model.addAttribute("builder", new Builder());
        return "admin/builders/form";
    }

    @GetMapping("/{id}/edit")
    public String edit(@PathVariable UUID id, Model model) {
        model.addAttribute("pageTitle", "Edit Builder");
        model.addAttribute("builder", builderRepository.findById(id).orElseThrow());
        return "admin/builders/form";
    }

    @PostMapping("/save")
    public String save(
            @ModelAttribute Builder form,
            @RequestParam(required = false) String rawPassword,
            RedirectAttributes ra) {
        Builder entity;
        if (form.getId() == null) {
            if (rawPassword == null || rawPassword.isBlank()) {
                ra.addFlashAttribute("errorMessage", "Password is required for new builders");
                return "redirect:/admin/builders/new";
            }
            entity = new Builder();
            entity.setCreatedAt(Instant.now());
            entity.setPlatformAdmin(false);
        } else {
            entity = builderRepository.findById(form.getId()).orElseThrow();
        }
        entity.setCompanyName(form.getCompanyName());
        entity.setEmail(form.getEmail());
        entity.setPhone(form.getPhone());
        entity.setCity(form.getCity());
        entity.setAddress(form.getAddress());
        entity.setLogoUrl(form.getLogoUrl());
        entity.setActive(form.getActive() != null ? form.getActive() : true);
        if (rawPassword != null && !rawPassword.isBlank()) {
            entity.setPasswordHash(passwordEncoder.encode(rawPassword));
        } else if (form.getId() == null) {
            ra.addFlashAttribute("errorMessage", "Password is required");
            return "redirect:/admin/builders/new";
        }
        entity.setUpdatedAt(Instant.now());
        builderRepository.save(entity);
        ra.addFlashAttribute("successMessage", "Builder saved");
        return "redirect:/admin/builders";
    }

    @GetMapping("/{builderId}/buildings/new")
    public String newBuilding(@PathVariable UUID builderId, Model model) {
        Builder builder = builderRepository.findById(builderId).orElseThrow();
        model.addAttribute("pageTitle", "New building — " + builder.getCompanyName());
        model.addAttribute("building", new Building());
        model.addAttribute("builderLabel", builder.getCompanyName() + " (" + builder.getEmail() + ")");
        model.addAttribute("formAction", "/admin/builders/" + builderId + "/buildings/save");
        model.addAttribute("cancelHref", "/admin/builders/" + builderId + "/edit");
        return "buildings/form";
    }

    @PostMapping("/{builderId}/buildings/save")
    public String saveBuilding(
            @PathVariable UUID builderId,
            @ModelAttribute Building building,
            RedirectAttributes ra) {
        try {
            Building saved = buildingService.createForBuilder(builderId, building);
            ra.addFlashAttribute(
                    "successMessage",
                    "Building \""
                            + saved.getBuildingName()
                            + "\" created for "
                            + saved.getBuilder().getCompanyName()
                            + ". The builder can open it under Buildings to generate flats.");
            return "redirect:/admin/builders/" + builderId + "/edit";
        } catch (IllegalArgumentException ex) {
            ra.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:/admin/builders/" + builderId + "/buildings/new";
        }
    }
}
