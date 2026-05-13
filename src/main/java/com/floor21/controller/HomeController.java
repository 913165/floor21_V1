package com.floor21.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** Maps the servlet context root (e.g. {@code /floor21/}) to the dashboard. */
@Controller
public class HomeController {

    @GetMapping("/")
    public String contextRoot() {
        return "redirect:/dashboard";
    }
}
