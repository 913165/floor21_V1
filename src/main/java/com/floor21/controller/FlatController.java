package com.floor21.controller;

import com.floor21.service.FlatService;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@RequiredArgsConstructor
public class FlatController {

    private final FlatService flatService;

    public record StatusBody(String status) {}

    @PostMapping(value = "/flats/{id}/status", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public Map<String, String> updateStatus(@PathVariable UUID id, @RequestBody StatusBody body) {
        flatService.updateStatus(id, body.status());
        return Map.of("ok", "true");
    }
}
