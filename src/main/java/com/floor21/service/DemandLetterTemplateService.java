package com.floor21.service;

import com.floor21.entity.Builder;
import com.floor21.repository.BuilderRepository;
import com.floor21.security.TenantContext;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.openxml4j.opc.PackagePart;
import org.apache.poi.openxml4j.opc.PackagePartName;
import org.apache.poi.openxml4j.opc.PackageRelationship;
import org.apache.poi.openxml4j.opc.PackagingURIHelper;
import org.apache.poi.openxml4j.opc.TargetMode;
import org.apache.poi.util.Units;
import org.apache.poi.xwpf.model.XWPFHeaderFooterPolicy;
import org.apache.poi.xwpf.usermodel.IBody;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFFooter;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFPicture;
import org.apache.poi.xwpf.usermodel.XWPFPictureData;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTBody;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTHdrFtr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTHdrFtrRef;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSectPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STHdrFtr;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class DemandLetterTemplateService {

    private static final String WEB_PREFIX = "media/demand-letter-templates/";

    private final BuilderRepository builderRepository;

    @Value("${floor21.upload-root}")
    private String uploadRoot;

    @Transactional(readOnly = true)
    public boolean hasHeader(UUID builderId) {
        return resolvePhysicalPath(findBuilder(builderId).getDemandLetterHeaderPath()).isPresent();
    }

    @Transactional(readOnly = true)
    public boolean hasFooter(UUID builderId) {
        return resolvePhysicalPath(findBuilder(builderId).getDemandLetterFooterPath()).isPresent();
    }

    @Transactional(readOnly = true)
    public XWPFDocument openBaseDocument(UUID builderId) throws IOException {
        Optional<Path> headerPath = resolvePhysicalPath(findBuilder(builderId).getDemandLetterHeaderPath());
        if (headerPath.isPresent()) {
            return new XWPFDocument(Files.newInputStream(headerPath.get()));
        }
        return new XWPFDocument();
    }

    public void applyFooterTemplate(XWPFDocument target, UUID builderId) throws IOException {
        Optional<Path> footerPath = resolvePhysicalPath(findBuilder(builderId).getDemandLetterFooterPath());
        if (footerPath.isEmpty()) {
            return;
        }
        try (XWPFDocument footerDoc = new XWPFDocument(Files.newInputStream(footerPath.get()))) {
            XWPFHeaderFooterPolicy policy = target.getHeaderFooterPolicy();
            if (policy == null) {
                policy = target.createHeaderFooterPolicy();
            }
            XWPFFooter destFooter = policy.getFooter(XWPFHeaderFooterPolicy.DEFAULT);
            if (destFooter == null) {
                destFooter = policy.createFooter(XWPFHeaderFooterPolicy.DEFAULT);
            }

            if (!footerDoc.getFooterList().isEmpty()) {
                cloneFooterPart(footerDoc.getFooterList().get(0), destFooter, target);
            } else if (hasBodyContent(footerDoc)) {
                // Footer saved in the main document body instead of Insert → Footer.
                clearFooter(destFooter);
                copyBodyElements(footerDoc, footerDoc, target, destFooter);
            }
            ensureDefaultFooterReference(target, destFooter);
        }
    }

    private static boolean hasBodyContent(XWPFDocument doc) {
        return !doc.getParagraphs().isEmpty() || !doc.getTables().isEmpty();
    }

    /** Copy footer XML and embedded images/shapes from the template footer part. */
    private static void cloneFooterPart(XWPFFooter srcFooter, XWPFFooter destFooter, XWPFDocument destDoc)
            throws IOException {
        OPCPackage srcPack = srcFooter.getPackage();
        OPCPackage destPack = destDoc.getPackage();
        PackagePart srcPart = srcFooter.getPackagePart();
        PackagePart destPart = destFooter.getPackagePart();

        clearPartRelationships(destPart);

        Map<String, String> relIdMap = new HashMap<>();
        for (PackageRelationship rel : srcPart.getRelationships()) {
            if (rel.getTargetMode() == TargetMode.EXTERNAL) {
                continue;
            }
            PackagePartName srcTargetName;
            try {
                srcTargetName =
                        PackagingURIHelper.createPartName(
                                PackagingURIHelper.resolvePartUri(srcPart.getPartName(), rel.getTargetURI()));
            } catch (Exception ex) {
                continue;
            }
            PackagePart srcRelated = srcPack.getPart(srcTargetName);
            if (srcRelated == null) {
                continue;
            }

            PackagePartName destTargetName = uniquePartName(destPack, srcTargetName);
            PackagePart destRelated =
                    destPack.containPart(destTargetName)
                            ? destPack.getPart(destTargetName)
                            : destPack.createPart(destTargetName, srcRelated.getContentType());
            copyPartBytes(srcRelated, destRelated);
            PackageRelationship newRel =
                    destPart.addRelationship(
                            destTargetName, TargetMode.INTERNAL, rel.getRelationshipType());
            relIdMap.put(rel.getId(), newRel.getId());
        }

        String footerXml;
        try (InputStream in = srcPart.getInputStream()) {
            footerXml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        footerXml = remapRelationshipIds(footerXml, relIdMap);

        try (OutputStream out = destPart.getOutputStream()) {
            out.write(footerXml.getBytes(StandardCharsets.UTF_8));
        }
        try (InputStream in = new ByteArrayInputStream(footerXml.getBytes(StandardCharsets.UTF_8))) {
            destFooter.setHeaderFooter(CTHdrFtr.Factory.parse(in));
        } catch (Exception ex) {
            throw new IOException("Failed to apply footer template.", ex);
        }
    }

    private static PackagePartName uniquePartName(OPCPackage destPack, PackagePartName preferred)
            throws Exception {
        if (!destPack.containPart(preferred)) {
            return preferred;
        }
        String name = preferred.getName();
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        String ext = dot > 0 ? name.substring(dot) : "";
        for (int i = 1; i < 100; i++) {
            PackagePartName candidate =
                    PackagingURIHelper.createPartName(base + "-" + i + ext);
            if (!destPack.containPart(candidate)) {
                return candidate;
            }
        }
        return PackagingURIHelper.createPartName(base + "-" + UUID.randomUUID() + ext);
    }

    private static void copyPartBytes(PackagePart src, PackagePart dest) throws IOException {
        try (InputStream in = src.getInputStream(); OutputStream out = dest.getOutputStream()) {
            in.transferTo(out);
        }
    }

    private static void clearPartRelationships(PackagePart part) {
        List<String> relIds = new ArrayList<>();
        for (PackageRelationship rel : part.getRelationships()) {
            relIds.add(rel.getId());
        }
        for (String relId : relIds) {
            part.removeRelationship(relId);
        }
    }

    private static String remapRelationshipIds(String xml, Map<String, String> relIdMap) {
        String remapped = xml;
        for (Map.Entry<String, String> entry : relIdMap.entrySet()) {
            String oldId = entry.getKey();
            String newId = entry.getValue();
            remapped = remapped.replace("r:embed=\"" + oldId + "\"", "r:embed=\"" + newId + "\"");
            remapped = remapped.replace("r:link=\"" + oldId + "\"", "r:link=\"" + newId + "\"");
            remapped = remapped.replace("r:id=\"" + oldId + "\"", "r:id=\"" + newId + "\"");
        }
        return remapped;
    }

    /** Word only renders footers that are referenced from the document section properties. */
    private static void ensureDefaultFooterReference(XWPFDocument doc, XWPFFooter footer) {
        if (footer == null) {
            return;
        }
        String footerRelId = doc.getRelationId(footer);
        if (footerRelId == null || footerRelId.isBlank()) {
            return;
        }
        CTBody body = doc.getDocument().getBody();
        if (body == null) {
            return;
        }
        CTSectPr sectPr = body.isSetSectPr() ? body.getSectPr() : body.addNewSectPr();
        for (CTHdrFtrRef existing : sectPr.getFooterReferenceList()) {
            if (footerRelId.equals(existing.getId())) {
                return;
            }
        }
        CTHdrFtrRef ref = sectPr.addNewFooterReference();
        ref.setType(STHdrFtr.DEFAULT);
        ref.setId(footerRelId);
    }

    @Transactional
    public void saveHeader(UUID builderId, MultipartFile file) {
        Builder builder = findBuilder(builderId);
        assertCanManage(builderId);
        validateDocx(file);
        String webPath = storeTemplate(builderId, "header.docx", file);
        builder.setDemandLetterHeaderPath(webPath);
        builderRepository.save(builder);
    }

    @Transactional
    public void saveFooter(UUID builderId, MultipartFile file) {
        Builder builder = findBuilder(builderId);
        assertCanManage(builderId);
        validateDocx(file);
        String webPath = storeTemplate(builderId, "footer.docx", file);
        builder.setDemandLetterFooterPath(webPath);
        builderRepository.save(builder);
    }

    private Builder findBuilder(UUID builderId) {
        return builderRepository
                .findById(builderId)
                .filter(b -> !b.isPlatformAdmin())
                .orElseThrow(() -> new IllegalArgumentException("Project not found."));
    }

    private void assertCanManage(UUID builderId) {
        UUID tenant = TenantContext.getBuilderIdOrNull();
        if (tenant != null && !tenant.equals(builderId)) {
            throw new IllegalArgumentException("You cannot change templates for another project.");
        }
    }

    private static void validateDocx(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Choose a Word document (.docx) to upload.");
        }
        String name = file.getOriginalFilename() != null ? file.getOriginalFilename().toLowerCase() : "";
        if (!name.endsWith(".docx")) {
            throw new IllegalArgumentException("Upload a .docx file (Word document).");
        }
    }

    private String storeTemplate(UUID builderId, String filename, MultipartFile file) {
        Path base = Paths.get(uploadRoot).toAbsolutePath().normalize();
        Path builderRoot = base.resolve("demand-letter-templates").resolve(builderId.toString()).normalize();
        Path target = builderRoot.resolve(filename).normalize();
        if (!target.startsWith(base.resolve("demand-letter-templates"))) {
            throw new IllegalArgumentException("Invalid upload path.");
        }
        try {
            Files.createDirectories(builderRoot);
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            throw new IllegalStateException("Could not store demand letter template.", ex);
        }
        return WEB_PREFIX + builderId + "/" + filename;
    }

    private Optional<Path> resolvePhysicalPath(String webPath) {
        if (webPath == null || webPath.isBlank() || !webPath.startsWith(WEB_PREFIX)) {
            return Optional.empty();
        }
        String rel = webPath.substring(WEB_PREFIX.length());
        Path base = Paths.get(uploadRoot).toAbsolutePath().normalize();
        Path file = base.resolve("demand-letter-templates").resolve(rel).normalize();
        Path root = base.resolve("demand-letter-templates").normalize();
        if (!file.startsWith(root) || !Files.isRegularFile(file)) {
            return Optional.empty();
        }
        return Optional.of(file);
    }

    private static void clearFooter(XWPFFooter footer) {
        while (!footer.getParagraphs().isEmpty()) {
            footer.removeParagraph(footer.getParagraphs().get(footer.getParagraphs().size() - 1));
        }
        while (!footer.getTables().isEmpty()) {
            footer.removeTable(footer.getTables().get(footer.getTables().size() - 1));
        }
    }

    private static void copyBodyElements(XWPFDocument srcDoc, IBody srcBody, XWPFDocument destDoc, IBody destBody) {
        for (IBodyElement element : srcBody.getBodyElements()) {
            if (element instanceof XWPFParagraph srcPara) {
                XWPFParagraph destPara = createParagraph(destBody);
                copyParagraph(srcDoc, srcPara, destDoc, destPara);
            } else if (element instanceof XWPFTable srcTable) {
                copyTable(srcDoc, srcTable, destDoc, destBody);
            }
        }
    }

    private static XWPFParagraph createParagraph(IBody body) {
        if (body instanceof XWPFFooter footer) {
            return footer.createParagraph();
        }
        return body.getXWPFDocument().createParagraph();
    }

    private static void copyParagraph(
            XWPFDocument srcDoc, XWPFParagraph src, XWPFDocument destDoc, XWPFParagraph dest) {
        if (src.getAlignment() != null) {
            dest.setAlignment(src.getAlignment());
        }
        dest.setIndentationLeft(src.getIndentationLeft());
        dest.setIndentationRight(src.getIndentationRight());
        for (XWPFRun srcRun : src.getRuns()) {
            XWPFRun destRun = dest.createRun();
            String text = srcRun.text();
            if (text != null) {
                destRun.setText(text);
            }
            destRun.setBold(srcRun.isBold());
            destRun.setItalic(srcRun.isItalic());
            if (srcRun.getFontSize() > 0) {
                destRun.setFontSize(srcRun.getFontSize());
            }
            if (srcRun.getFontFamily() != null) {
                destRun.setFontFamily(srcRun.getFontFamily());
            }
            for (XWPFPicture picture : srcRun.getEmbeddedPictures()) {
                copyPicture(srcDoc, destDoc, destRun, picture);
            }
        }
    }

    private static void copyPicture(
            XWPFDocument srcDoc, XWPFDocument destDoc, XWPFRun destRun, XWPFPicture picture) {
        try {
            XWPFPictureData data = picture.getPictureData();
            if (data == null) {
                return;
            }
            int width = Units.toEMU(120);
            int height = Units.toEMU(40);
            if (picture.getCTPicture().getSpPr() != null
                    && picture.getCTPicture().getSpPr().getXfrm() != null
                    && picture.getCTPicture().getSpPr().getXfrm().getExt() != null) {
                width = (int) picture.getCTPicture().getSpPr().getXfrm().getExt().getCx();
                height = (int) picture.getCTPicture().getSpPr().getXfrm().getExt().getCy();
            }
            try (InputStream in = new ByteArrayInputStream(data.getData())) {
                destRun.addPicture(in, data.getPictureType(), data.getFileName(), width, height);
            }
        } catch (Exception ignored) {
            // Best-effort: footer text still copies if images fail.
        }
    }

    private static void copyTable(XWPFDocument srcDoc, XWPFTable srcTable, XWPFDocument destDoc, IBody destBody) {
        if (srcTable.getNumberOfRows() == 0 || srcTable.getRow(0) == null) {
            return;
        }
        int cols = srcTable.getRow(0).getTableCells().size();
        XWPFTable destTable =
                destBody instanceof XWPFFooter footer
                        ? footer.createTable(srcTable.getNumberOfRows(), cols)
                        : destDoc.createTable(srcTable.getNumberOfRows(), cols);
        for (int r = 0; r < srcTable.getNumberOfRows(); r++) {
            XWPFTableRow srcRow = srcTable.getRow(r);
            XWPFTableRow destRow = destTable.getRow(r);
            if (destRow == null) {
                continue;
            }
            for (int c = 0; c < srcRow.getTableCells().size(); c++) {
                XWPFTableCell srcCell = srcRow.getCell(c);
                XWPFTableCell destCell = destRow.getCell(c);
                if (srcCell == null || destCell == null) {
                    continue;
                }
                while (destCell.getParagraphs().size() > 0) {
                    destCell.removeParagraph(0);
                }
                for (XWPFParagraph srcPara : srcCell.getParagraphs()) {
                    XWPFParagraph destPara = destCell.addParagraph();
                    copyParagraph(srcDoc, srcPara, destDoc, destPara);
                }
            }
        }
    }
}