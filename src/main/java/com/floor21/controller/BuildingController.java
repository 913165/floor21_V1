package com.floor21.controller;

import com.floor21.dto.BuildingConfigDto;
import com.floor21.entity.Building;
import com.floor21.service.BuildingService;
import com.floor21.service.FlatService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/buildings")
@RequiredArgsConstructor
public class BuildingController {

    private final BuildingService buildingService;
    private final FlatService flatService;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("pageTitle", "Buildings");
        model.addAttribute("buildings", buildingService.listForTenant());
        return "buildings/list";
    }

    @GetMapping("/new")
    public String createForm(Model model) {
        model.addAttribute("pageTitle", "New Building");
        model.addAttribute("building", new Building());
        return "buildings/form";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable UUID id, Model model) {
        model.addAttribute("pageTitle", "Edit Building");
        model.addAttribute("building", buildingService.getForTenant(id));
        return "buildings/form";
    }

    @PostMapping("/save")
    public String save(@ModelAttribute Building building, RedirectAttributes ra) {
        Building saved = buildingService.save(building);
        ra.addFlashAttribute("successMessage", "Building saved");
        return "redirect:/buildings/" + saved.getId() + "/flats";
    }

    @GetMapping("/{id}/flats")
    public String flatGrid(@PathVariable UUID id, Model model) {
        Building b = buildingService.getForTenant(id);
        BuildingConfigDto cfg = new BuildingConfigDto();
        cfg.setTotalFloors(b.getTotalFloors());
        cfg.setParkingFloors(b.getParkingFloors() != null ? b.getParkingFloors() : 0);
        cfg.setFlatsPerFloor(b.getFlatsPerFloor());
        cfg.setBhk1PerFloor(b.getBhk1PerFloor() != null ? b.getBhk1PerFloor() : 0);
        cfg.setBhk2PerFloor(b.getBhk2PerFloor() != null ? b.getBhk2PerFloor() : 0);
        cfg.setBhk3PerFloor(b.getBhk3PerFloor() != null ? b.getBhk3PerFloor() : 0);
        model.addAttribute("pageTitle", "Flat Grid — " + b.getBuildingName());
        model.addAttribute("building", b);
        model.addAttribute("floors", flatService.getGridData(id));
        model.addAttribute("config", cfg);
        return "buildings/flat-grid";
    }

    @GetMapping("/{id}/flats/data")
    @ResponseBody
    public Object flatData(@PathVariable UUID id) {
        return flatService.getGridData(id);
    }

    @PostMapping("/{id}/flats/generate")
    public String generate(
            @PathVariable UUID id,
            @Valid @ModelAttribute("config") BuildingConfigDto config,
            BindingResult br,
            RedirectAttributes ra) {
        if (br.hasErrors()) {
            ra.addFlashAttribute("errorMessage", "Invalid configuration");
            return "redirect:/buildings/" + id + "/flats";
        }
        flatService.generateFlats(id, config);
        ra.addFlashAttribute("successMessage", "Flats generated");
        return "redirect:/buildings/" + id + "/flats";
    }
}
