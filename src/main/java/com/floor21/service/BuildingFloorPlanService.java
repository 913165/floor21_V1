package com.floor21.service;

import com.floor21.entity.Building;
import com.floor21.exception.ResourceNotFoundException;
import com.floor21.repository.BuildingRepository;
import com.floor21.security.TenantContext;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class BuildingFloorPlanService {

    private static final String WEB_PREFIX = "media/buildings/";
    private static final Set<String> ALLOWED_EXT = Set.of(".png", ".jpg", ".jpeg", ".webp", ".gif");

    private final BuildingRepository buildingRepository;

    @Value("${floor21.upload-root}")
    private String uploadRoot;

    @Transactional
    public void savePlans(UUID buildingId, MultipartFile plan1Bhk, MultipartFile plan2Bhk, MultipartFile plan3Bhk) {
        UUID builderId = TenantContext.requireBuilderId();
        Building building =
                buildingRepository
                        .findByIdAndBuilder_Id(buildingId, builderId)
                        .orElseThrow(() -> new ResourceNotFoundException("Building not found"));

        Path base = Paths.get(uploadRoot).toAbsolutePath().normalize();
        Path buildingDir = base.resolve("buildings").resolve(buildingId.toString());

        if (plan1Bhk != null && !plan1Bhk.isEmpty()) {
            building.setFloorPlan1Bhk(storeOne(buildingDir, buildingId, "1BHK", plan1Bhk, building.getFloorPlan1Bhk(), base));
        }
        if (plan2Bhk != null && !plan2Bhk.isEmpty()) {
            building.setFloorPlan2Bhk(storeOne(buildingDir, buildingId, "2BHK", plan2Bhk, building.getFloorPlan2Bhk(), base));
        }
        if (plan3Bhk != null && !plan3Bhk.isEmpty()) {
            building.setFloorPlan3Bhk(storeOne(buildingDir, buildingId, "3BHK", plan3Bhk, building.getFloorPlan3Bhk(), base));
        }

        buildingRepository.save(building);
    }

    private String storeOne(
            Path buildingDir,
            UUID buildingId,
            String slotLabel,
            MultipartFile file,
            String previousWebPath,
            Path uploadBase) {
        String ext = validateExtension(file.getOriginalFilename());
        Path buildingRoot = uploadBase.resolve("buildings").resolve(buildingId.toString()).normalize();
        try {
            Files.createDirectories(buildingDir);
            String filename = slotLabel + ext;
            Path target = buildingDir.resolve(filename).normalize();
            if (!target.startsWith(buildingRoot)) {
                throw new IllegalArgumentException("Invalid upload path");
            }
            if (previousWebPath != null && previousWebPath.startsWith(WEB_PREFIX)) {
                deletePhysical(previousWebPath, uploadBase);
            }
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new IllegalStateException("Could not store floor plan", e);
        }
        return WEB_PREFIX + buildingId + "/" + slotLabel + ext;
    }

    private void deletePhysical(String webPath, Path uploadBase) {
        String rel = webPath.substring(WEB_PREFIX.length());
        Path file = uploadBase.resolve("buildings").resolve(rel).normalize();
        Path buildingsRoot = uploadBase.resolve("buildings").normalize();
        if (!file.startsWith(buildingsRoot)) {
            return;
        }
        try {
            Files.deleteIfExists(file);
        } catch (IOException ignored) {
            // best-effort cleanup
        }
    }

    /**
     * Resolves a stored web path (e.g. {@code media/buildings/{id}/2BHK.png}) to an on-disk file under
     * {@code floor21.upload-root}. Returns empty if the path is invalid or the file is missing.
     */
    public Optional<Resource> loadAsResource(String webPath) {
        Path path = resolveToExistingPath(webPath);
        if (path == null) {
            return Optional.empty();
        }
        return Optional.of(new FileSystemResource(path));
    }

    private Path resolveToExistingPath(String webPath) {
        if (webPath == null || !webPath.startsWith(WEB_PREFIX)) {
            return null;
        }
        String rel = webPath.substring(WEB_PREFIX.length());
        Path base = Paths.get(uploadRoot).toAbsolutePath().normalize();
        Path candidate = base.resolve("buildings").resolve(rel).normalize();
        Path buildingsRoot = base.resolve("buildings").normalize();
        if (!candidate.startsWith(buildingsRoot)) {
            return null;
        }
        if (!Files.isRegularFile(candidate)) {
            return null;
        }
        return candidate;
    }

    private static String validateExtension(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new IllegalArgumentException("File name is required");
        }
        int dot = originalFilename.lastIndexOf('.');
        if (dot < 0 || dot == originalFilename.length() - 1) {
            throw new IllegalArgumentException("File must have an extension (PNG, JPEG, WebP, or GIF)");
        }
        String ext = originalFilename.substring(dot).toLowerCase(Locale.ROOT);
        if (!ALLOWED_EXT.contains(ext)) {
            throw new IllegalArgumentException("Allowed types: PNG, JPEG, WebP, GIF");
        }
        return ext;
    }
}
