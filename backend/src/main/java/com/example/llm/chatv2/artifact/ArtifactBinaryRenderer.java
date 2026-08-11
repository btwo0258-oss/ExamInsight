package com.example.llm.chatv2.artifact;

import com.example.llm.chatv2.artifact.ArtifactDraftRepository.ArtifactRow;
import com.example.llm.chatv2.artifact.ArtifactModels.Type;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.sl.usermodel.TextParagraph;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextBox;
import org.apache.poi.xslf.usermodel.XSLFTextParagraph;
import org.apache.poi.xslf.usermodel.XSLFTextRun;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
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
            for (String line : markdown.replace("\r\n", "\n").split("\n", -1)) {
                appendMarkdownLine(document, line);
            }
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

    private void appendMarkdownLine(XWPFDocument document, String line) {
        XWPFParagraph paragraph = document.createParagraph();
        String text = line;
        int level = headingLevel(line);
        if (level > 0) {
            text = line.substring(level).trim();
            paragraph.setStyle("Heading" + Math.min(level, 6));
        } else if (line.startsWith("- ") || line.startsWith("* ")) {
            text = line.substring(2).trim();
            paragraph.setIndentationLeft(360);
        } else if (line.matches("^\\d+\\.\\s+.*")) {
            text = line.replaceFirst("^\\d+\\.\\s+", "");
            paragraph.setIndentationLeft(360);
        }
        if (text.isBlank()) {
            return;
        }
        XWPFRun run = paragraph.createRun();
        run.setText(stripInlineMarkdown(text));
        if (level == 1) {
            paragraph.setAlignment(ParagraphAlignment.CENTER);
            run.setBold(true);
            run.setFontSize(20);
        } else if (level > 1) {
            run.setBold(true);
            run.setFontSize(Math.max(12, 18 - level));
        } else {
            run.setFontSize(11);
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
            for (Object rawBullet : bullets) {
                String bullet = rawBullet == null ? "" : rawBullet.toString().trim();
                if (bullet.isEmpty()) continue;
                XSLFTextParagraph paragraph = bodyBox.addNewTextParagraph();
                paragraph.setBullet(true);
                paragraph.setTextAlign(TextParagraph.TextAlign.LEFT);
                XSLFTextRun run = paragraph.addNewTextRun();
                run.setText(bullet);
                run.setFontSize(20d);
                run.setFontColor(new Color(40, 40, 40));
            }
        }
    }

    private int headingLevel(String line) {
        int count = 0;
        while (count < line.length() && line.charAt(count) == '#') count++;
        return count > 0 && count <= 6 && count < line.length() && line.charAt(count) == ' '
                ? count : 0;
    }

    private String stripInlineMarkdown(String value) {
        return value.replace("**", "").replace("__", "").replace("`", "");
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
