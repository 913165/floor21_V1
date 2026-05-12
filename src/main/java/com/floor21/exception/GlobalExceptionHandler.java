package com.floor21.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.servlet.ModelAndView;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ModelAndView notFound(ResourceNotFoundException ex) {
        ModelAndView mv = new ModelAndView("error/404");
        mv.addObject("message", ex.getMessage());
        return mv;
    }

    @ExceptionHandler(UnauthorizedTenantException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ModelAndView forbidden(UnauthorizedTenantException ex) {
        ModelAndView mv = new ModelAndView("error/403");
        mv.addObject("message", ex.getMessage());
        return mv;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ModelAndView badRequest(IllegalArgumentException ex) {
        ModelAndView mv = new ModelAndView("error/general");
        mv.addObject("message", ex.getMessage());
        mv.setStatus(HttpStatus.BAD_REQUEST);
        return mv;
    }
}
