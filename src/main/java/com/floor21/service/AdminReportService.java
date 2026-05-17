package com.floor21.service;

import com.floor21.entity.Building;
import com.floor21.entity.Builder;
import com.floor21.repository.BuildingRepository;
import com.floor21.repository.BuilderRepository;
import com.floor21.repository.FlatRepository;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminReportService {

    private static final DateTimeFormatter FILE_TS =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmm").withZone(ZoneId.systemDefault());

    private final BuilderRepository builderRepository;
    private final BuildingRepository buildingRepository;
    private final FlatRepository flatRepository;
    private final PlatformAuditService auditService;

    @Transactional(readOnly = true)
    public ResponseEntity<Resource> exportBuilders() throws IOException {
        auditService.log("REPORT_EXPORT", "report", "builders", null, null);
        StringWriter w = new StringWriter();
        w.append("company_name,email,city,active,buildings,last_login,created_at\n");
        for (Builder b : builderRepository.findAllTenantsOrderByCompanyNameAsc()) {
            long buildings = buildingRepository.countByBuilder_Id(b.getId());
            w.append(csv(b.getCompanyName()))
                    .append(',')
                    .append(csv(b.getEmail()))
                    .append(',')
                    .append(csv(b.getCity()))
                    .append(',')
                    .append(String.valueOf(Boolean.TRUE.equals(b.getActive())))
                    .append(',')
                    .append(String.valueOf(buildings))
                    .append(',')
                    .append(csv(formatInstant(b.getLastLoginAt())))
                    .append(',')
                    .append(csv(formatInstant(b.getCreatedAt())))
                    .append('\n');
        }
        return csvResponse("floor21-builders-" + FILE_TS.format(Instant.now()) + ".csv", w.toString());
    }

    @Transactional(readOnly = true)
    public ResponseEntity<Resource> exportBuildings() throws IOException {
        auditService.log("REPORT_EXPORT", "report", "buildings", null, null);
        StringWriter w = new StringWriter();
        w.append("builder,building_name,city,total_floors,flats_per_floor,active\n");
        for (Building b : buildingRepository.findAllForPlatformAdminOrderByBuilderAndName()) {
            w.append(csv(b.getBuilder().getCompanyName()))
                    .append(',')
                    .append(csv(b.getBuildingName()))
                    .append(',')
                    .append(csv(b.getCity()))
                    .append(',')
                    .append(String.valueOf(b.getTotalFloors()))
                    .append(',')
                    .append(String.valueOf(b.getFlatsPerFloor()))
                    .append(',')
                    .append(String.valueOf(Boolean.TRUE.equals(b.getActive())))
                    .append('\n');
        }
        return csvResponse("floor21-buildings-" + FILE_TS.format(Instant.now()) + ".csv", w.toString());
    }

    @Transactional(readOnly = true)
    public ResponseEntity<Resource> exportInventory() throws IOException {
        auditService.log("REPORT_EXPORT", "report", "inventory", null, null);
        long total = flatRepository.count();
        long booked = flatRepository.countAllByStatus("BOOKED");
        long available = flatRepository.countAllByStatus("AVAILABLE");
        StringWriter w = new StringWriter();
        w.append("metric,value\n");
        w.append("total_flats,").append(String.valueOf(total)).append('\n');
        w.append("booked_flats,").append(String.valueOf(booked)).append('\n');
        w.append("available_flats,").append(String.valueOf(available)).append('\n');
        return csvResponse("floor21-inventory-" + FILE_TS.format(Instant.now()) + ".csv", w.toString());
    }

    private static ResponseEntity<Resource> csvResponse(String filename, String body) {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        Resource resource = new ByteArrayResource(bytes);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(resource);
    }

    private static String csv(String value) {
        if (value == null) {
            return "";
        }
        String escaped = value.replace("\"", "\"\"");
        if (escaped.contains(",") || escaped.contains("\"") || escaped.contains("\n")) {
            return "\"" + escaped + "\"";
        }
        return escaped;
    }

    private static String formatInstant(Instant instant) {
        return instant != null ? instant.toString() : "";
    }
}
