package com.floor21.service;

import com.floor21.entity.Building;
import com.floor21.entity.Flat;
import com.floor21.exception.ResourceNotFoundException;
import com.floor21.repository.BuildingRepository;
import com.floor21.repository.FlatRepository;
import com.floor21.security.TenantContext;
import com.floor21.util.ParkingFloorConfigUtil;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
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
    private final FlatRepository flatRepository;

    @Value("${floor21.upload-root}")
    private String uploadRoot;

    @Transactional
    public void savePlans(UUID buildingId, MultipartFile plan1Bhk, MultipartFile plan2Bhk, MultipartFile plan3Bhk) {
        Building building = requireBuildingForAccess(buildingId);

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

    @Transactional
    public void saveParkingLayoutImage(UUID buildingId, int floorNumber, MultipartFile image) {
        if (floorNumber < 1) {
            throw new IllegalArgumentException("Invalid floor number.");
        }
        Building building = requireBuildingForAccess(buildingId);
        Path base = Paths.get(uploadRoot).toAbsolutePath().normalize();
        Path buildingDir = base.resolve("buildings").resolve(buildingId.toString());
        String previous = ParkingFloorConfigUtil.layoutImagePath(building, floorNumber);
        String slotLabel = "parking-floor-" + floorNumber;
        String webPath = storeOne(buildingDir, buildingId, slotLabel, image, previous, base);
        ParkingFloorConfigUtil.setLayoutImagePath(building, floorNumber, webPath);
        buildingRepository.save(building);
    }

    @Transactional
    public String storeFlatLayoutImage(UUID flatId, MultipartFile image) {
        Flat flat =
                flatRepository
                        .findById(flatId)
                        .orElseThrow(() -> new ResourceNotFoundException("Flat not found"));
        UUID buildingId = flat.getBuilding().getId();
        requireBuildingForAccess(buildingId);
        Path base = Paths.get(uploadRoot).toAbsolutePath().normalize();
        Path buildingRoot = base.resolve("buildings").resolve(buildingId.toString()).normalize();
        Path flatsDir = buildingRoot.resolve("flats").normalize();
        String ext = validateExtension(image.getOriginalFilename());
        String filename = flatId + ext;
        try {
            Files.createDirectories(flatsDir);
            Path target = flatsDir.resolve(filename).normalize();
            if (!target.startsWith(buildingRoot)) {
                throw new IllegalArgumentException("Invalid upload path");
            }
            String previous = flat.getLayoutImagePath();
            if (previous != null && previous.startsWith(WEB_PREFIX)) {
                deletePhysical(previous, base);
            }
            Files.copy(image.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new IllegalStateException("Could not store flat layout image", e);
        }
        return WEB_PREFIX + buildingId + "/flats/" + flatId + ext;
    }

    public void deleteStoredWebPath(String webPath) {
        if (webPath == null || webPath.isBlank()) {
            return;
        }
        Path base = Paths.get(uploadRoot).toAbsolutePath().normalize();
        deletePhysical(webPath, base);
    }

    private Building requireBuildingForAccess(UUID buildingId) {
        UUID tenantId = TenantContext.getBuilderIdOrNull();
        if (tenantId != null) {
            Building building =
                    buildingRepository
                            .findByIdAndBuilder_Id(buildingId, tenantId)
                            .orElseThrow(() -> new ResourceNotFoundException("Building not found"));
            if (!TenantContext.canAccessBuilding(buildingId)) {
                throw new ResourceNotFoundException("Building not found");
            }
            return building;
        }
        return buildingRepository
                .findByIdWithBuilder(buildingId)
                .filter(b -> b.getBuilder() != null && !b.getBuilder().isPlatformAdmin())
                .orElseThrow(() -> new ResourceNotFoundException("Building not found"));
    }

    /** Removes uploaded floor-plan files for a building (best-effort). */
    public void deleteAllForBuilding(UUID buildingId) {
        Path base = Paths.get(uploadRoot).toAbsolutePath().normalize();
        Path buildingDir = base.resolve("buildings").resolve(buildingId.toString()).normalize();
        Path buildingsRoot = base.resolve("buildings").normalize();
        if (!buildingDir.startsWith(buildingsRoot) || !Files.isDirectory(buildingDir)) {
            return;
        }
        try {
            try (var walk = Files.walk(buildingDir)) {
                walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException ignored) {
                        // best-effort cleanup
                    }
                });
            }
        } catch (IOException ignored) {
            // best-effort cleanup
        }
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
