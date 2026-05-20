package com.floor21.controller;

import com.floor21.dto.FlatAdminUpdateDto;
import com.floor21.dto.FlatMergeDto;
import com.floor21.dto.FlatPartnerAssignDto;
import com.floor21.entity.Flat;
import com.floor21.service.FlatService;
import com.floor21.service.PartnerFlatAllocationService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@RequiredArgsConstructor
public class FlatController {

    private final FlatService flatService;
    private final PartnerFlatAllocationService partnerFlatAllocationService;

    public record StatusBody(String status) {}

    @PostMapping(value = "/flats/{id}/status", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public Map<String, String> updateStatus(@PathVariable UUID id, @RequestBody StatusBody body) {
        flatService.updateStatus(id, body.status());
        return Map.of("ok", "true");
    }

    @PostMapping(value = "/flats/{id}/partner", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @ResponseBody
    public ResponseEntity<?> assignPartner(@PathVariable UUID id, @RequestBody FlatPartnerAssignDto body) {
        try {
            String name = partnerFlatAllocationService.assignPartnerToFlat(id, body.partnerUserId());
            return ResponseEntity.ok(
                    Map.of(
                            "ok",
                            true,
                            "partnerUserId",
                            body.partnerUserId() != null ? body.partnerUserId() : "",
                            "partnerName",
                            name != null ? name : ""));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    @PostMapping(value = "/flats/{id}/details", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @ResponseBody
    public ResponseEntity<?> updateDetails(@PathVariable UUID id, @Valid @RequestBody FlatAdminUpdateDto body) {
        try {
            Flat flat = flatService.updateFlatAsPlatformAdmin(id, body);
            return ResponseEntity.ok(flatResponse(flat));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    @DeleteMapping("/flats/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @ResponseBody
    public ResponseEntity<?> deleteFlat(@PathVariable UUID id) {
        try {
            flatService.deleteFlatAsPlatformAdmin(id);
            return ResponseEntity.ok(Map.of("ok", "true"));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    @PostMapping(value = "/flats/{id}/merge", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @ResponseBody
    public ResponseEntity<?> mergeFlats(@PathVariable UUID id, @Valid @RequestBody FlatMergeDto body) {
        try {
            Flat flat = flatService.mergeFlatsAsPlatformAdmin(id, body);
            return ResponseEntity.ok(flatResponse(flat));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    @GetMapping(value = "/flats/{id}/merge-candidates", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @ResponseBody
    public List<Map<String, Object>> mergeCandidates(@PathVariable UUID id) {
        return flatService.listMergeCandidatesOnFloor(id).stream()
                .map(
                        f ->
                                Map.<String, Object>of(
                                        "id", f.id(),
                                        "flatNumber", f.flatNumber(),
                                        "bhkType", f.bhkType(),
                                        "status", f.status()))
                .toList();
    }

    private static Map<String, Object> flatResponse(Flat flat) {
        return Map.of(
                "id", flat.getId(),
                "flatNumber", flat.getFlatNumber(),
                "bhkType", flat.getBhkType(),
                "areaSqft", flat.getAreaSqft(),
                "basePrice", flat.getBasePrice(),
                "status", flat.getStatus(),
                "floorNumber", flat.getFloorNumber());
    }
}
