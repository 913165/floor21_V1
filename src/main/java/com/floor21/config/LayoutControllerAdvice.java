package com.floor21.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/** Exposes request path for layout fragments (sidebar active state). */
@ControllerAdvice
public class LayoutControllerAdvice {

    @ModelAttribute("navServletPath")
    public String navServletPath(HttpServletRequest request) {
        if (request == null) {
            return "";
        }
        String path = request.getServletPath();
        return path != null ? path : "";
    }
}
