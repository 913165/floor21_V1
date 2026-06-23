package com.floor21.service;

import com.floor21.entity.Booking;
import com.floor21.entity.Builder;
import com.floor21.entity.DocsLockerDocument;
import com.floor21.entity.User;
import com.floor21.exception.ResourceNotFoundException;
import com.floor21.repository.BookingRepository;
import com.floor21.repository.BuilderRepository;
import com.floor21.repository.DocsLockerDocumentRepository;
import com.floor21.repository.UserRepository;
import com.floor21.security.Floor21UserPrincipal;
import com.floor21.security.TenantContext;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class DocsLockerDocumentService {

    private static final String WEB_PREFIX = "media/docs-locker/";
    private static final Set<String> ALLOWED_EXT =
            Set.of(".pdf", ".doc", ".docx", ".jpg", ".jpeg", ".png", ".webp");

    private final DocsLockerDocumentRepository documentRepository;
    private final BuilderRepository builderRepository;
    private final BookingRepository bookingRepository;
    private final UserRepository userRepository;

    @Value("${floor21.upload-root}")
    private String uploadRoot;

    @Transactional(readOnly = true)
    public List<DocsLockerDocument> listForCurrentBuilder() {
        return documentRepository.findByBuilder_IdOrderByCreatedAtDesc(TenantContext.requireBuilderId());
    }

    @Transactional
    public DocsLockerDocument upload(
            MultipartFile file, String title, String notes, UUID bookingId) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Choose a document to upload.");
        }
        UUID builderId = TenantContext.requireBuilderId();
        Builder builder =
                builderRepository
                        .findById(builderId)
                        .orElseThrow(() -> new ResourceNotFoundException("Builder not found"));
        Booking booking = resolveBooking(bookingId, builderId);
        String originalName = sanitizeFilename(file.getOriginalFilename());
        String ext = extension(originalName);
        if (!ALLOWED_EXT.contains(ext)) {
            throw new IllegalArgumentException(
                    "Allowed types: PDF, Word (.doc/.docx), and images (JPG, PNG, WebP).");
        }
        UUID docId = UUID.randomUUID();
        String storedName = docId + ext;
        String webPath = WEB_PREFIX + builderId + "/" + storedName;
        Path physical = resolvePhysicalPath(webPath)
                .orElseThrow(() -> new IllegalStateException("Invalid storage path."));
        try {
            Files.createDirectories(physical.getParent());
            Files.copy(file.getInputStream(), physical, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            throw new IllegalStateException("Could not save document.", ex);
        }
        DocsLockerDocument doc = new DocsLockerDocument();
        doc.setBuilder(builder);
        doc.setBooking(booking);
        doc.setTitle(blankToNull(title));
        doc.setNotes(blankToNull(notes));
        doc.setOriginalFilename(originalName);
        doc.setStoragePath(webPath);
        doc.setContentType(file.getContentType());
        doc.setFileSizeBytes(file.getSize());
        doc.setUploadedBy(currentStaffUser().orElse(null));
        return documentRepository.save(doc);
    }

    @Transactional(readOnly = true)
    public DocsLockerDocument getDocument(UUID documentId) {
        return requireDocument(documentId);
    }

    @Transactional(readOnly = true)
    public Resource loadFile(UUID documentId) {
        DocsLockerDocument doc = requireDocument(documentId);
        Path path =
                resolvePhysicalPath(doc.getStoragePath())
                        .orElseThrow(() -> new ResourceNotFoundException("Document file not found"));
        if (!Files.isRegularFile(path)) {
            throw new ResourceNotFoundException("Document file not found");
        }
        return new FileSystemResource(path);
    }

    @Transactional
    public void delete(UUID documentId) {
        DocsLockerDocument doc = requireDocument(documentId);
        resolvePhysicalPath(doc.getStoragePath()).ifPresent(this::deleteQuietly);
        documentRepository.delete(doc);
    }

    private DocsLockerDocument requireDocument(UUID documentId) {
        return documentRepository
                .findByIdAndBuilder_Id(documentId, TenantContext.requireBuilderId())
                .orElseThrow(() -> new ResourceNotFoundException("Document not found"));
    }

    private Booking resolveBooking(UUID bookingId, UUID builderId) {
        if (bookingId == null) {
            return null;
        }
        return bookingRepository
                .findByIdAndBuilder_Id(bookingId, builderId)
                .orElseThrow(() -> new IllegalArgumentException("Booking not found."));
    }

    private Optional<User> currentStaffUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof Floor21UserPrincipal principal)) {
            return Optional.empty();
        }
        UUID staffUserId = principal.getStaffUserId();
        if (staffUserId == null) {
            return Optional.empty();
        }
        return userRepository.findById(staffUserId);
    }

    private Optional<Path> resolvePhysicalPath(String webPath) {
        if (webPath == null || webPath.isBlank() || webPath.contains("..")) {
            return Optional.empty();
        }
        String relative = webPath.startsWith("media/") ? webPath.substring("media/".length()) : webPath;
        Path base = Paths.get(uploadRoot).toAbsolutePath().normalize();
        Path resolved = base.resolve(relative).normalize();
        if (!resolved.startsWith(base)) {
            return Optional.empty();
        }
        return Optional.of(resolved);
    }

    private static String sanitizeFilename(String name) {
        if (name == null || name.isBlank()) {
            return "document";
        }
        String cleaned = Paths.get(name).getFileName().toString().trim();
        return cleaned.isBlank() ? "document" : cleaned;
    }

    private static String extension(String filename) {
        int dot = filename.lastIndexOf('.');
        if (dot < 0) {
            return "";
        }
        return filename.substring(dot).toLowerCase(Locale.ROOT);
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // best effort
        }
    }
}
