package com.floor21.service;

import com.floor21.entity.MilestoneSampleTemplate;
import com.floor21.exception.ResourceNotFoundException;
import com.floor21.repository.MilestoneSampleTemplateRepository;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class MilestoneSampleTemplateService {

    private static final long MAX_BYTES = 5L * 1024 * 1024;

    private final MilestoneSampleTemplateRepository repository;
    private final RateSlabExcelService rateSlabExcelService;

    public List<MilestoneSampleTemplate> listAll() {
        return repository.findAllByOrderBySortOrderAscNameAsc();
    }

    public MilestoneSampleTemplate get(UUID id) {
        return repository
                .findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Sample template not found"));
    }

    @Transactional
    public MilestoneSampleTemplate saveUpload(String name, String description, MultipartFile file) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Enter a template name.");
        }
        validateExcelFile(file);
        byte[] content = readFileBytes(file);
        validateMilestoneExcel(content);
        Instant now = Instant.now();
        MilestoneSampleTemplate entity = new MilestoneSampleTemplate();
        entity.setName(name.trim());
        entity.setDescription(description != null ? description.trim() : null);
        entity.setFileName(sanitizeFileName(file.getOriginalFilename()));
        entity.setFileContent(content);
        entity.setSortOrder(nextSortOrder());
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        return repository.save(entity);
    }

    @Transactional
    public void delete(UUID id) {
        if (!repository.existsById(id)) {
            throw new ResourceNotFoundException("Sample template not found");
        }
        repository.deleteById(id);
    }

    @Transactional
    public void seedDefaultsIfEmpty() throws IOException {
        if (repository.count() > 0) {
            return;
        }
        Instant now = Instant.now();
        saveSeed(
                "Standard 5-stage",
                "Common booking, agreement, plinth, slab, and possession milestones totalling 100%.",
                "milestone_sample_standard_5_stage.xlsx",
                RateSlabExcelService.STANDARD_FIVE_STAGE_ROWS,
                1,
                now);
        saveSeed(
                "Construction-linked (10 stages)",
                "Extended schedule aligned to typical construction progress from booking through possession.",
                "milestone_sample_construction_10_stage.xlsx",
                RateSlabExcelService.CONSTRUCTION_TEN_STAGE_ROWS,
                2,
                now);
        saveSeed(
                "Front-loaded booking",
                "Higher upfront payments with smaller instalments during construction.",
                "milestone_sample_front_loaded.xlsx",
                RateSlabExcelService.FRONT_LOADED_ROWS,
                3,
                now);
    }

    private void saveSeed(
            String name, String description, String fileName, Object[][] rows, int sortOrder, Instant now)
            throws IOException {
        MilestoneSampleTemplate entity = new MilestoneSampleTemplate();
        entity.setName(name);
        entity.setDescription(description);
        entity.setFileName(fileName);
        entity.setFileContent(rateSlabExcelService.buildTemplate(rows));
        entity.setSortOrder(sortOrder);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        repository.save(entity);
    }

    private int nextSortOrder() {
        return repository.findAllByOrderBySortOrderAscNameAsc().stream()
                        .mapToInt(t -> t.getSortOrder() != null ? t.getSortOrder() : 0)
                        .max()
                        .orElse(0)
                + 1;
    }

    private static void validateExcelFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Choose an Excel file (.xlsx or .xls) to upload.");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new IllegalArgumentException("File is too large (max 5 MB).");
        }
        String name = file.getOriginalFilename() != null ? file.getOriginalFilename().toLowerCase(Locale.ROOT) : "";
        if (!name.endsWith(".xlsx") && !name.endsWith(".xls")) {
            throw new IllegalArgumentException("Only Excel files (.xlsx or .xls) are supported.");
        }
    }

    private static byte[] readFileBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException ex) {
            throw new IllegalArgumentException("Could not read the Excel file: " + ex.getMessage());
        }
    }

    private void validateMilestoneExcel(byte[] content) {
        List<com.floor21.dto.RateSlabImportRow> rows;
        try {
            rows = rateSlabExcelService.parse(content);
        } catch (IOException ex) {
            throw new IllegalArgumentException("Could not read the Excel file: " + ex.getMessage());
        }
        if (rows.isEmpty()) {
            throw new IllegalArgumentException(
                    "No milestone rows found. Use columns Slab Name and Percent (%) in the sample format.");
        }
    }

    private static String sanitizeFileName(String original) {
        if (original == null || original.isBlank()) {
            return "milestone_sample.xlsx";
        }
        String base = original.replace("\\", "/");
        int slash = base.lastIndexOf('/');
        if (slash >= 0) {
            base = base.substring(slash + 1);
        }
        base = base.replaceAll("[^a-zA-Z0-9._-]", "_");
        if (base.isBlank()) {
            return "milestone_sample.xlsx";
        }
        if (!base.toLowerCase(Locale.ROOT).endsWith(".xlsx") && !base.toLowerCase(Locale.ROOT).endsWith(".xls")) {
            return base + ".xlsx";
        }
        return base;
    }
}
