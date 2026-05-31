package com.floor21.controller;

import com.floor21.entity.Client;
import com.floor21.service.ClientService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/clients")
@RequiredArgsConstructor
public class ClientController {

    private final ClientService clientService;

    @GetMapping
    public String list(Model model, @RequestParam(required = false) String q) {
        model.addAttribute("pageTitle", "Clients");
        model.addAttribute("clients", q != null && !q.isBlank() ? clientService.search(q) : clientService.list());
        model.addAttribute("q", q);
        return "clients/list";
    }

    @GetMapping("/search")
    public String searchFragment(Model model, @RequestParam String q) {
        model.addAttribute("clients", clientService.search(q));
        return "clients/list :: clientRows";
    }

    @GetMapping("/new")
    public String createForm(Model model) {
        model.addAttribute("pageTitle", "New Client");
        model.addAttribute("client", new Client());
        return "clients/form";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable UUID id, Model model) {
        model.addAttribute("pageTitle", "Client");
        model.addAttribute("client", clientService.get(id));
        model.addAttribute("clientBuildings", clientService.listBuildingsForActiveBookings(id));
        return "clients/detail";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable UUID id, Model model) {
        model.addAttribute("pageTitle", "Edit Client");
        model.addAttribute("client", clientService.get(id));
        return "clients/form";
    }

    @PostMapping("/save")
    public String save(
            @Valid @ModelAttribute("client") Client client,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes ra) {
        if (bindingResult.hasErrors()) {
            model.addAttribute(
                    "pageTitle", client.getId() == null ? "New Client" : "Edit Client");
            model.addAttribute(
                    "errorMessage", bindingResult.getAllErrors().getFirst().getDefaultMessage());
            return "clients/form";
        }
        clientService.save(client);
        ra.addFlashAttribute("successMessage", "Client saved");
        return "redirect:/clients";
    }
}
