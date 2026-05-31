package com.floor21.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

/** Permanent redirects from renamed /admin/builders URLs to /admin/projects. */
@Controller
public class AdminProjectsLegacyRedirectController {

    @RequestMapping("/admin/builders")
    public String redirectRoot(HttpServletRequest request) {
        return redirect(request);
    }

    @RequestMapping("/admin/builders/**")
    public String redirectNested(HttpServletRequest request) {
        return redirect(request);
    }

    private static String redirect(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String target = uri.replace("/admin/builders", "/admin/projects");
        String query = request.getQueryString();
        if (query != null && !query.isBlank()) {
            target = target + "?" + query;
        }
        return "redirect:" + target;
    }
}
