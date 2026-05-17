package com.floor21.controller;

import com.floor21.service.ImpersonationService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/impersonate")
@RequiredArgsConstructor
public class ImpersonationController {

    private final ImpersonationService impersonationService;

    @PostMapping("/end")
    public String end(HttpServletRequest request, RedirectAttributes ra) {
        try {
            impersonationService.end(request);
            ra.addFlashAttribute("successMessage", "Returned to platform administrator view.");
        } catch (IllegalStateException ex) {
            ra.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/builders";
    }
}
