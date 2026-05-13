package com.floor21.controller;

import com.floor21.entity.Slab;
import com.floor21.repository.BuilderRepository;
import com.floor21.repository.BuildingRepository;
import com.floor21.service.SlabService;
import java.beans.PropertyEditorSupport;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
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
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/builder-pricing-slabs")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class AdminBuilderPricingSlabController {

    private final SlabService slabService;
    private final BuilderRepository builderRepository;
    private final BuildingRepository buildingRepository;

    @InitBinder("slab")
    public void slabBinder(WebDataBinder binder) {
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
        binder.registerCustomEditor(
                UUID.class,
                "builder.id",
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
    public String list(Model model) {
        model.addAttribute("pageTitle", "Builder rate slabs (Floor21 admin)");
        model.addAttribute("slabs", slabService.listAllForPlatformAdmin());
        return "slabs/list";
    }

    @GetMapping("/new")
    public String form(Model model) {
        model.addAttribute("pageTitle", "New rate slab");
        model.addAttribute("slab", new Slab());
        model.addAttribute("builders", builderRepository.findAllByOrderByCompanyNameAsc());
        model.addAttribute("buildings", buildingRepository.findAllForPlatformAdminOrderByBuilderAndName());
        return "slabs/form";
    }

    @GetMapping("/{id}/edit")
    public String edit(@PathVariable UUID id, Model model) {
        model.addAttribute("pageTitle", "Edit rate slab");
        model.addAttribute("slab", slabService.getForPlatformAdmin(id));
        model.addAttribute("builders", builderRepository.findAllByOrderByCompanyNameAsc());
        model.addAttribute("buildings", buildingRepository.findAllForPlatformAdminOrderByBuilderAndName());
        return "slabs/form";
    }

    @PostMapping("/save")
    public String save(@ModelAttribute Slab slab, RedirectAttributes ra) {
        try {
            slabService.saveForPlatformAdmin(slab);
            ra.addFlashAttribute("successMessage", "Rate slab saved");
        } catch (IllegalArgumentException ex) {
            ra.addFlashAttribute("errorMessage", ex.getMessage());
            if (slab.getId() == null) {
                return "redirect:/admin/builder-pricing-slabs/new";
            }
            return "redirect:/admin/builder-pricing-slabs/" + slab.getId() + "/edit";
        }
        return "redirect:/admin/builder-pricing-slabs";
    }
}
