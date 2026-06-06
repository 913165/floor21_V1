package com.floor21.controller;

import com.floor21.entity.Client;
import com.floor21.repository.BuilderRepository;
import com.floor21.security.Floor21UserPrincipal;
import com.floor21.service.ClientService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
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
    private final BuilderRepository builderRepository;

    @GetMapping
    public String list(
            Model model,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) UUID projectId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Page<Client> clientPage = clientService.listPage(page, size, q, projectId);
        model.addAttribute("pageTitle", "Clients");
        model.addAttribute("clientPage", clientPage);
        model.addAttribute("clients", clientPage.getContent());
        model.addAttribute("pageSize", clientPage.getSize());
        model.addAttribute("pageSizeOptions", List.of(10, 25, 50));
        model.addAttribute("q", q);
        if (isPlatformAdmin()) {
            model.addAttribute("platformAdminView", true);
            model.addAttribute("projects", builderRepository.findAllTenantsOrderByCompanyNameAsc());
            model.addAttribute("filterProjectId", projectId);
        }
        return "clients/list";
    }

    @GetMapping("/search")
    public String searchFragment(
            Model model,
            @RequestParam String q,
            @RequestParam(required = false) UUID projectId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Page<Client> clientPage = clientService.listPage(page, size, q, projectId);
        model.addAttribute("clientPage", clientPage);
        model.addAttribute("clients", clientPage.getContent());
        model.addAttribute("pageSize", clientPage.getSize());
        if (isPlatformAdmin()) {
            model.addAttribute("platformAdminView", true);
            model.addAttribute("filterProjectId", projectId);
        }
        return "clients/list :: clientRows";
    }

    @GetMapping("/new")
    @PreAuthorize("hasAnyRole('BUILDER_ADMIN','EXECUTIVE')")
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
    @PreAuthorize("hasAnyRole('BUILDER_ADMIN','EXECUTIVE')")
    public String editForm(@PathVariable UUID id, Model model) {
        model.addAttribute("pageTitle", "Edit Client");
        model.addAttribute("client", clientService.get(id));
        return "clients/form";
    }

    @PostMapping("/save")
    @PreAuthorize("hasAnyRole('BUILDER_ADMIN','EXECUTIVE')")
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

    private static boolean isPlatformAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null
                && auth.getPrincipal() instanceof Floor21UserPrincipal principal
                && principal.isSuperAdmin();
    }
}
