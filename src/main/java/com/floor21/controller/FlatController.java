package com.floor21.controller;

import com.floor21.dto.FlatAdminUpdateDto;
import com.floor21.dto.FlatMergeCandidateDto;
import com.floor21.dto.FlatMergeDto;
import com.floor21.dto.FloorMergeSplitResult;
import com.floor21.dto.FlatPartnerAssignDto;
import com.floor21.dto.ParkingLinkDto;
import com.floor21.entity.Flat;
import com.floor21.service.FlatService;
import com.floor21.service.PartnerFlatAllocationService;
import com.floor21.util.FlatUnitTypes;
import jakarta.validation.Valid;
import java.util.LinkedHashMap;
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
            return ResponseEntity.ok(Map.of("ok", true, "deleted", true));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    @PostMapping("/flats/{id}/activation")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @ResponseBody
    public ResponseEntity<?> toggleActivation(@PathVariable UUID id) {
        try {
            Flat flat = flatService.toggleFlatActivationAsPlatformAdmin(id);
            return ResponseEntity.ok(
                    Map.of(
                            "ok", true,
                            "status", flat.getStatus(),
                            "active", !"CANCELLED".equals(flat.getStatus())));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    @GetMapping("/flats/{id}/linked-parking")
    @ResponseBody
    public ResponseEntity<?> linkedParking(@PathVariable UUID id) {
        try {
            return ResponseEntity.ok(flatService.listLinkedParkingForResidentialFlat(id));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    @PostMapping(value = "/flats/{id}/parking-link", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @ResponseBody
    public ResponseEntity<?> linkParkingToResidential(
            @PathVariable UUID id, @RequestBody ParkingLinkDto body) {
        try {
            return ResponseEntity.ok(flatService.linkParkingToResidential(id, body));
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

    @PostMapping(value = "/flats/{id}/split-duplex")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @ResponseBody
    public ResponseEntity<?> splitDuplex(@PathVariable UUID id) {
        try {
            Flat flat = flatService.splitDuplexAsPlatformAdmin(id);
            return ResponseEntity.ok(flatResponse(flat));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    @PostMapping(value = "/flats/{id}/split-merge")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @ResponseBody
    public ResponseEntity<?> splitMergedFlat(@PathVariable UUID id) {
        try {
            FloorMergeSplitResult result = flatService.splitMergedFlatAsPlatformAdmin(id);
            Map<String, Object> map = flatResponse(result.keep());
            map.put("restoredFlatId", result.restored().getId());
            map.put("restoredFlatNumber", result.restored().getFlatNumber());
            map.put(
                    "message",
                    "Restored flat "
                            + result.restored().getFlatNumber()
                            + ". Both units are separate again.");
            return ResponseEntity.ok(map);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    @GetMapping(value = "/flats/{id}/merge-candidates", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @ResponseBody
    public List<FlatMergeCandidateDto> mergeCandidates(@PathVariable UUID id) {
        return flatService.listMergeCandidates(id);
    }

    private static Map<String, Object> flatResponse(Flat flat) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", flat.getId());
        map.put("flatNumber", flat.getFlatNumber());
        map.put("bhkType", flat.getBhkType());
        map.put("areaSqft", flat.getAreaSqft());
        map.put("carpetAreaSqft", flat.getCarpetAreaSqft());
        map.put("balconyAreaSqft", flat.getBalconyAreaSqft());
        map.put("basePrice", flat.getBasePrice());
        map.put("status", flat.getStatus());
        map.put("floorNumber", flat.getFloorNumber());
        map.put("parking", Boolean.TRUE.equals(flat.getParking()));
        map.put("amenity", FlatUnitTypes.isAmenityCode(flat.getBhkType()));
        map.put("duplexPrimary", FlatUnitTypes.isDuplexPrimary(flat));
        map.put("duplexSecondary", FlatUnitTypes.isDuplexSecondary(flat));
        map.put(
                "duplexPartnerFlatId",
                FlatUnitTypes.isDuplexPrimary(flat)
                        ? flat.getDuplexSecondaryFlatId()
                        : flat.getDuplexPrimaryFlatId());
        map.put("mergePrimary", FlatUnitTypes.isMergePrimary(flat));
        map.put("mergeSecondary", FlatUnitTypes.isMergeAbsorbed(flat));
        map.put("mergePartnerFlatId", flat.getMergedAbsorbedFlatId() != null ? flat.getMergedAbsorbedFlatId() : flat.getMergedIntoFlatId());
        map.put("mergeAbsorbedFlatId", flat.getMergedAbsorbedFlatId());
        return map;
    }
}
