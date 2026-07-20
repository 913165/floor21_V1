package com.floor21.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/** Public marketing pages linked from the login site header (no auth). */
@Controller
public class PublicSiteController {

    @GetMapping("/about")
    public String about(Model model) {
        model.addAttribute("publicPage", "about");
        model.addAttribute("pageTitle", "About us");
        return "public/about";
    }

    @GetMapping("/features")
    public String features(Model model) {
        model.addAttribute("publicPage", "features");
        model.addAttribute("pageTitle", "Features");
        return "public/features";
    }

    @GetMapping("/privacy")
    public String privacy(Model model) {
        model.addAttribute("publicPage", "privacy");
        model.addAttribute("pageTitle", "Privacy");
        return "public/privacy";
    }

    @GetMapping("/contact")
    public String contact(Model model) {
        model.addAttribute("publicPage", "contact");
        model.addAttribute("pageTitle", "Contact");
        return "public/contact";
    }
}
