package com.example.llm.chatv2.artifact;

import com.example.llm.chatv2.artifact.ArtifactDraftRepository.ArtifactRow;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.sl.usermodel.TextParagraph;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextBox;
import org.apache.poi.xslf.usermodel.XSLFTextParagraph;
import org.apache.poi.xslf.usermodel.XSLFTextRun;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.geom.Rectangle2D;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;

@Component
public class ArtifactBinaryRenderer {
    private final ObjectMapper objectMapper;

    public ArtifactBinaryRenderer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public RenderedArtifact render(ArtifactRow draft) {
        return switch (draft.type()) {
            case DOCUMENT -> renderDocument(draft);
            case PRESENTATION -> renderPresentation(draft);
            case MINDMAP -> renderMindMap(draft);
            case IMAGE -> throw new IllegalArgumentException("Image artifacts are confirmed during generation");
        };
    }

    private RenderedArtifact renderDocument(ArtifactRow draft) {
        String markdown = requiredString(draft.content(), "markdown");
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            new MarkdownDocxRenderer(document).render(markdown);
            document.write(output);
            return new RenderedArtifact(fileName(draft.title(), ".docx"),
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    output.toByteArray(), "document");
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to render document artifact", exception);
        }
    }

    @SuppressWarnings("unchecked")
    private RenderedArtifact renderPresentation(ArtifactRow draft) {
        Object rawSlides = draft.content().get("slides");
        if (!(rawSlides instanceof List<?> slides) || slides.isEmpty()) {
            throw new IllegalArgumentException("Presentation requires at least one slide");
        }
        try (XMLSlideShow show = new XMLSlideShow();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            show.setPageSize(new Dimension(1_280, 720));
            for (Object rawSlide : slides) {
                if (!(rawSlide instanceof Map<?, ?> slide)) {
                    throw new IllegalArgumentException("Invalid presentation slide");
                }
                appendSlide(show, (Map<String, Object>) slide);
            }
            show.write(output);
            return new RenderedArtifact(fileName(draft.title(), ".pptx"),
                    "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                    output.toByteArray(), "presentation");
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to render presentation artifact", exception);
        }
    }

    private RenderedArtifact renderMindMap(ArtifactRow draft) {
        try {
            byte[] bytes = objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsBytes(draft.content());
            return new RenderedArtifact(fileName(draft.title(), ".mindmap.json"),
                    "application/vnd.examinsight.mindmap+json", bytes, "mindmap");
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to render mind-map artifact", exception);
        }
    }

    private void appendSlide(XMLSlideShow show, Map<String, Object> slideData) {
        XSLFSlide slide = show.createSlide();
        slide.getBackground().setFillColor(Color.WHITE);

        XSLFTextBox titleBox = slide.createTextBox();
        titleBox.setAnchor(new Rectangle2D.Double(70, 45, 1_140, 95));
        XSLFTextParagraph titleParagraph = titleBox.addNewTextParagraph();
        XSLFTextRun titleRun = titleParagraph.addNewTextRun();
        titleRun.setText(String.valueOf(slideData.getOrDefault("title", "Untitled")));
        titleRun.setFontSize(30d);
        titleRun.setBold(true);
        titleRun.setFontColor(Color.BLACK);

        XSLFTextBox bodyBox = slide.createTextBox();
        bodyBox.setAnchor(new Rectangle2D.Double(95, 170, 1_080, 470));
        Object rawBullets = slideData.get("bullets");
        if (rawBullets instanceof List<?> bullets) {
            List<String> bulletTexts = bullets.stream()
                    .map(rawBullet -> rawBullet == null ? "" : rawBullet.toString().trim())
                    .filter(bullet -> !bullet.isEmpty())
                    .toList();
            int longestBullet = bulletTexts.stream().mapToInt(String::length).max().orElse(0);
            // Keep dense or unusually long drafts readable inside the fixed 16:9
            // slide canvas instead of allowing the generated preview to clip the
            // lower lines. The editor still preserves the original text.
            double bodyFontSize = Math.max(11d, Math.min(20d,
                    21d - Math.max(0, bulletTexts.size() - 8) * 0.55d
                            - Math.max(0, longestBullet - 55) * 0.045d));
            for (String bullet : bulletTexts) {
                XSLFTextParagraph paragraph = bodyBox.addNewTextParagraph();
                paragraph.setBullet(true);
                paragraph.setTextAlign(TextParagraph.TextAlign.LEFT);
                XSLFTextRun run = paragraph.addNewTextRun();
                run.setText(bullet);
                run.setFontSize(bodyFontSize);
                run.setFontColor(new Color(40, 40, 40));
            }
        }
    }

    private String requiredString(Map<String, Object> values, String key) {
        Object value = values.get(key);
        if (!(value instanceof String text) || text.isBlank()) {
            throw new IllegalArgumentException("Artifact field is required: " + key);
        }
        return text;
    }

    private String fileName(String title, String suffix) {
        String safe = title == null ? "artifact" : title.trim()
                .replaceAll("[\\\\/:*?\"<>|]", "_");
        if (safe.isBlank()) safe = "artifact";
        int maximumTitleLength = Math.max(1, 255 - suffix.length());
        if (safe.length() > maximumTitleLength) safe = safe.substring(0, maximumTitleLength);
        return safe.endsWith(suffix) ? safe : safe + suffix;
    }

    public record RenderedArtifact(String fileName, String mimeType, byte[] bytes, String generationLabel) {
        public RenderedArtifact {
            bytes = bytes == null ? new byte[0] : bytes.clone();
        }

        @Override
        public byte[] bytes() {
            return bytes.clone();
        }
    }
}
