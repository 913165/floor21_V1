package com.floor21.controller;

import com.floor21.entity.Slab;
import com.floor21.service.BuildingService;
import com.floor21.service.SlabService;
import java.beans.PropertyEditorSupport;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
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
@RequestMapping("/slabs")
@RequiredArgsConstructor
public class SlabController {

    private final SlabService slabService;
    private final BuildingService buildingService;

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
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("pageTitle", "Slabs");
        model.addAttribute("slabs", slabService.list());
        return "slabs/list";
    }

    @GetMapping("/new")
    public String form(Model model) {
        model.addAttribute("pageTitle", "New Slab");
        model.addAttribute("slab", new Slab());
        model.addAttribute("buildings", buildingService.listForTenant());
        return "slabs/form";
    }

    @GetMapping("/{id}/edit")
    public String edit(@PathVariable UUID id, Model model) {
        model.addAttribute("pageTitle", "Edit Slab");
        model.addAttribute("slab", slabService.get(id));
        model.addAttribute("buildings", buildingService.listForTenant());
        return "slabs/form";
    }

    @PostMapping("/save")
    public String save(@ModelAttribute Slab slab, RedirectAttributes ra) {
        slabService.save(slab);
        ra.addFlashAttribute("successMessage", "Slab saved");
        return "redirect:/slabs";
    }
}
