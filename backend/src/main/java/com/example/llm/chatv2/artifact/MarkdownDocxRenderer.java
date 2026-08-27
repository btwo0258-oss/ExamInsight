package com.example.llm.chatv2.artifact;

import org.apache.poi.xwpf.usermodel.*;
import org.commonmark.ext.gfm.tables.*;
import org.commonmark.node.*;
import org.commonmark.parser.Parser;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.*;

import java.math.BigInteger;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/** Word-native output shared by download and the DOCX reader; no HTML screenshots or remote fetches. */
final class MarkdownDocxRenderer {
    private static final String W = "http://schemas.openxmlformats.org/wordprocessingml/2006/main";
    private static final int PAGE_WIDTH = 11906;
    private static final int PAGE_HEIGHT = 16838;
    private static final int MARGIN = 1360;
    private static final int CONTENT_WIDTH = PAGE_WIDTH - 2 * MARGIN;
    private final XWPFDocument document;
    private final XWPFNumbering numbering;
    private int headingOffset;

    MarkdownDocxRenderer(XWPFDocument document) {
        this.document = document;
        this.numbering = document.createNumbering();
    }

    void render(String markdown) throws Exception {
        configurePageAndStyles();
        Node root = Parser.builder().extensions(List.of(TablesExtension.create())).build().parse(markdown);
        Node first = root.getFirstChild();
        if (first instanceof Heading heading && heading.getLevel() == 1) {
            headingOffset = 1;
            XWPFParagraph title = paragraph("Title", false);
            inlineChildren(first, title, false, false, null, 0);
            first = first.getNext();
        }
        for (Node node = first; node != null; node = node.getNext()) renderBlock(node, false, 0, 0);
    }

    private void configurePageAndStyles() throws Exception {
        CTSectPr section = document.getDocument().getBody().addNewSectPr();
        CTPageSz size = section.addNewPgSz();
        size.setW(BigInteger.valueOf(PAGE_WIDTH));
        size.setH(BigInteger.valueOf(PAGE_HEIGHT));
        CTPageMar margins = section.addNewPgMar();
        margins.setTop(BigInteger.valueOf(MARGIN));
        margins.setBottom(BigInteger.valueOf(MARGIN));
        margins.setLeft(BigInteger.valueOf(MARGIN));
        margins.setRight(BigInteger.valueOf(MARGIN));
        margins.setHeader(BigInteger.valueOf(560));
        margins.setFooter(BigInteger.valueOf(560));

        // Compact reference rhythm, with product-specific A4/monochrome/CJK overrides.
        // Explicit styles ensure Word, docx-preview and other readers see the same geometry.
        String styles = "<w:styles xmlns:w=\"" + W + "\">" + """
                <w:docDefaults><w:rPrDefault><w:rPr>
                  <w:rFonts w:ascii="Calibri" w:hAnsi="Calibri" w:eastAsia="Microsoft YaHei"/>
                  <w:sz w:val="22"/><w:color w:val="202020"/>
                  <w:lang w:val="en-US" w:eastAsia="zh-CN"/>
                </w:rPr></w:rPrDefault><w:pPrDefault><w:pPr>
                  <w:spacing w:before="0" w:after="120" w:line="300" w:lineRule="auto"/>
                  <w:widowControl/>
                </w:pPr></w:pPrDefault></w:docDefaults>
                """
                + style("Normal", "Normal", 22, false, 0, 120, "", "")
                + style("Title", "Title", 44, true, 0, 360,
                    "<w:jc w:val=\"center\"/><w:keepNext/><w:keepLines/>", "")
                + style("Heading1", "heading 1", 32, true, 360, 200, headingProperties(0), "")
                + style("Heading2", "heading 2", 26, true, 280, 140, headingProperties(1), "")
                + style("Heading3", "heading 3", 24, true, 200, 100, headingProperties(2), "")
                + style("Heading4", "heading 4", 22, true, 180, 100, headingProperties(3), "")
                + style("Heading5", "heading 5", 22, true, 160, 80, headingProperties(4), "")
                + style("Heading6", "heading 6", 22, true, 160, 80, headingProperties(5), "")
                + style("ListParagraph", "List Paragraph", 22, false, 0, 80, "", "")
                + style("Quote", "Quote", 22, false, 120, 160,
                    "<w:ind w:left=\"300\" w:right=\"160\"/><w:pBdr><w:left w:val=\"single\" w:sz=\"16\" w:space=\"8\" w:color=\"B9BDC4\"/></w:pBdr>", "")
                + style("CodeBlock", "Code Block", 19, false, 80, 80,
                    "<w:ind w:left=\"160\" w:right=\"160\"/><w:shd w:fill=\"F2F3F5\"/>",
                    "<w:rFonts w:ascii=\"Consolas\" w:hAnsi=\"Consolas\" w:eastAsia=\"Microsoft YaHei\"/>")
                + style("TableBody", "Table Body", 20, false, 40, 40, "", "")
                + style("TableHeader", "Table Header", 20, true, 40, 40, "", "")
                + "</w:styles>";
        document.createStyles().setStyles(StylesDocument.Factory.parse(styles).getStyles());
    }

    private String style(String id, String name, int size, boolean bold, int before, int after,
                         String paragraphProperties, String runProperties) {
        return "<w:style w:type=\"paragraph\" w:styleId=\"" + id + "\""
                + (id.equals("Normal") ? " w:default=\"1\"" : "") + "><w:name w:val=\"" + name + "\"/>"
                + (id.equals("Normal") ? "" : "<w:basedOn w:val=\"Normal\"/>")
                + "<w:next w:val=\"Normal\"/><w:qFormat/><w:pPr><w:spacing w:before=\"" + before
                + "\" w:after=\"" + after + "\" w:line=\"300\" w:lineRule=\"auto\"/>"
                + paragraphProperties + "</w:pPr><w:rPr><w:sz w:val=\"" + size + "\"/>"
                + (bold ? "<w:b/>" : "") + "<w:color w:val=\"202020\"/>"
                + runProperties + "</w:rPr></w:style>";
    }

    private String headingProperties(int level) {
        return "<w:keepNext/><w:keepLines/><w:outlineLvl w:val=\"" + level + "\"/>";
    }

    private XWPFParagraph paragraph(String style, boolean quote) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setStyle(quote && style.equals("Normal") ? "Quote" : style);
        if (quote && !style.equals("Normal")) paragraph.setIndentationLeft(300);
        return paragraph;
    }

    private void renderBlock(Node node, boolean quote, int listDepth, int depth) throws Exception {
        checkDepth(depth);
        if (node instanceof Heading heading) {
            XWPFParagraph paragraph = paragraph("Heading" + Math.max(1, heading.getLevel() - headingOffset), quote);
            inlineChildren(node, paragraph, false, false, null, 0);
        } else if (node instanceof Paragraph) {
            inlineChildren(node, paragraph("Normal", quote), false, false, null, 0);
        } else if (node instanceof BulletList || node instanceof OrderedList) {
            renderList(node, quote, listDepth, depth + 1);
        } else if (node instanceof BlockQuote) {
            for (Node child = node.getFirstChild(); child != null; child = child.getNext()) {
                renderBlock(child, true, listDepth, depth + 1);
            }
        } else if (node instanceof FencedCodeBlock code) {
            codeBlock(code.getLiteral(), quote);
        } else if (node instanceof IndentedCodeBlock code) {
            codeBlock(code.getLiteral(), quote);
        } else if (node instanceof TableBlock) {
            renderTable(node);
        } else if (node instanceof ThematicBreak) {
            XWPFParagraph rule = paragraph("Normal", quote);
            rule.setBorderBottom(Borders.SINGLE);
        } else if (node instanceof HtmlBlock html) {
            // Literal text only: rendering a document must never execute or fetch model-supplied HTML.
            codeBlock(html.getLiteral(), quote);
        } else {
            for (Node child = node.getFirstChild(); child != null; child = child.getNext()) {
                renderBlock(child, quote, listDepth, depth + 1);
            }
        }
    }

    private void renderList(Node list, boolean quote, int listDepth, int depth) throws Exception {
        if (listDepth > 8) throw new IllegalArgumentException("Document lists exceed the supported nesting depth");
        boolean ordered = list instanceof OrderedList;
        int start = ordered ? ((OrderedList) list).getMarkerStartNumber() : 1;
        BigInteger numId = createNumbering(ordered, start, listDepth);
        for (Node item = list.getFirstChild(); item != null; item = item.getNext()) {
            boolean first = true;
            for (Node block = item.getFirstChild(); block != null; block = block.getNext()) {
                if (block instanceof Paragraph) {
                    XWPFParagraph paragraph = paragraph("ListParagraph", quote);
                    if (first) {
                        paragraph.setNumID(numId);
                        paragraph.setNumILvl(BigInteger.valueOf(listDepth));
                    } else paragraph.setIndentationLeft((listDepth + 1) * 540);
                    inlineChildren(block, paragraph, false, false, null, 0);
                    first = false;
                } else renderBlock(block, quote, listDepth + 1, depth + 1);
            }
        }
    }

    private BigInteger createNumbering(boolean ordered, int start, int depth) throws Exception {
        BigInteger abstractId = BigInteger.valueOf(numbering.getAbstractNums().size());
        String definition = "<w:abstractNum xmlns:w=\"" + W + "\" w:abstractNumId=\"" + abstractId + "\">"
                + "<w:multiLevelType w:val=\"multilevel\"/><w:lvl w:ilvl=\"" + depth + "\">"
                + "<w:start w:val=\"" + Math.max(1, start) + "\"/><w:numFmt w:val=\"" + (ordered ? "decimal" : "bullet")
                + "\"/><w:lvlText w:val=\"" + (ordered ? "%" + (depth + 1) + "." : "•") + "\"/>"
                + "<w:lvlJc w:val=\"left\"/><w:pPr><w:tabs><w:tab w:val=\"num\" w:pos=\"" + ((depth + 1) * 540)
                + "\"/></w:tabs><w:ind w:left=\"" + ((depth + 1) * 540) + "\" w:hanging=\"270\"/></w:pPr>"
                + "<w:rPr><w:rFonts w:ascii=\"Calibri\" w:hAnsi=\"Calibri\"/></w:rPr></w:lvl></w:abstractNum>";
        CTAbstractNum abstractNum = NumberingDocument.Factory.parse(
                "<w:numbering xmlns:w=\"" + W + "\">" + definition + "</w:numbering>")
                .getNumbering().getAbstractNumArray(0);
        numbering.addAbstractNum(new XWPFAbstractNum(abstractNum));
        return numbering.addNum(abstractId);
    }

    private void codeBlock(String literal, boolean quote) {
        String code = literal.endsWith("\n") ? literal.substring(0, literal.length() - 1) : literal;
        // A paragraph per code line allows long blocks to paginate without a clipped fixed-height box.
        for (String line : code.split("\n", -1)) {
            XWPFParagraph paragraph = paragraph("CodeBlock", quote);
            paragraph.setSpacingBefore(0);
            paragraph.setSpacingAfter(0);
            paragraph.createRun().setText(line);
        }
    }

    private void renderTable(Node tableNode) {
        List<Node> rows = new ArrayList<>();
        for (Node section = tableNode.getFirstChild(); section != null; section = section.getNext()) {
            for (Node row = section.getFirstChild(); row != null; row = row.getNext()) rows.add(row);
        }
        if (rows.isEmpty()) return;
        int columns = children(rows.get(0)).size();
        if (columns == 0 || columns > 20) throw new IllegalArgumentException("Invalid document table column count");
        int[] widths = tableWidths(rows, columns);
        XWPFTable table = document.createTable(rows.size(), columns);
        table.setWidth(CONTENT_WIDTH);
        table.setTableAlignment(TableRowAlign.LEFT);
        table.setCellMargins(80, 120, 80, 120);
        table.setInsideHBorder(XWPFTable.XWPFBorderType.SINGLE, 4, 0, "D7D9DD");
        table.setInsideVBorder(XWPFTable.XWPFBorderType.SINGLE, 4, 0, "D7D9DD");
        table.setTopBorder(XWPFTable.XWPFBorderType.SINGLE, 4, 0, "D7D9DD");
        table.setBottomBorder(XWPFTable.XWPFBorderType.SINGLE, 4, 0, "D7D9DD");
        table.setLeftBorder(XWPFTable.XWPFBorderType.SINGLE, 4, 0, "D7D9DD");
        table.setRightBorder(XWPFTable.XWPFBorderType.SINGLE, 4, 0, "D7D9DD");
        table.getCTTbl().getTblPr().addNewTblLayout().setType(STTblLayoutType.FIXED);
        CTTblWidth indent = table.getCTTbl().getTblPr().addNewTblInd();
        indent.setType(STTblWidth.DXA);
        indent.setW(BigInteger.valueOf(120));
        CTTblGrid grid = table.getCTTbl().getTblGrid();
        if (grid == null) grid = table.getCTTbl().addNewTblGrid();
        while (grid.sizeOfGridColArray() > 0) grid.removeGridCol(0);
        for (int width : widths) grid.addNewGridCol().setW(BigInteger.valueOf(width));
        for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
            List<Node> cells = children(rows.get(rowIndex));
            boolean header = rows.get(rowIndex).getParent() instanceof TableHead;
            XWPFTableRow row = table.getRow(rowIndex);
            if (header) row.setRepeatHeader(true);
            for (int column = 0; column < columns; column++) {
                XWPFTableCell cell = row.getCell(column);
                cell.setWidth(String.valueOf(widths[column]));
                cell.setVerticalAlignment(XWPFTableCell.XWPFVertAlign.CENTER);
                if (header) cell.setColor("F2F3F5");
                XWPFParagraph paragraph = cell.getParagraphs().get(0);
                paragraph.setStyle(header ? "TableHeader" : "TableBody");
                if (column < cells.size()) {
                    TableCell source = (TableCell) cells.get(column);
                    if (source.getAlignment() != null) paragraph.setAlignment(switch (source.getAlignment()) {
                        case CENTER -> ParagraphAlignment.CENTER;
                        case RIGHT -> ParagraphAlignment.RIGHT;
                        default -> ParagraphAlignment.LEFT;
                    });
                    inlineChildren(source, paragraph, false, false, null, 0);
                }
            }
        }
        // Word requires a body paragraph after a trailing table; also separates following blocks.
        XWPFParagraph gap = paragraph("Normal", false);
        gap.setSpacingAfter(80);
        gap.setSpacingBefore(0);
    }

    private int[] tableWidths(List<Node> rows, int columns) {
        double[] weights = new double[columns];
        for (int column = 0; column < columns; column++) {
            weights[column] = 8;
            for (Node row : rows) {
                List<Node> cells = children(row);
                if (column < cells.size()) weights[column] = Math.max(weights[column], Math.min(36, textLength(cells.get(column))));
            }
        }
        double total = java.util.Arrays.stream(weights).sum();
        int[] widths = new int[columns];
        int remaining = CONTENT_WIDTH;
        for (int column = 0; column < columns; column++) {
            widths[column] = column == columns - 1 ? remaining : (int) Math.floor(CONTENT_WIDTH * weights[column] / total);
            remaining -= widths[column];
        }
        return widths;
    }

    private int textLength(Node node) {
        int length = 0;
        var pending = new java.util.ArrayDeque<Node>();
        pending.push(node);
        while (!pending.isEmpty()) {
            Node current = pending.pop();
            if (current instanceof Text text) length += text.getLiteral().length();
            for (Node child = current.getFirstChild(); child != null; child = child.getNext()) pending.push(child);
        }
        return length;
    }

    private List<Node> children(Node node) {
        List<Node> children = new ArrayList<>();
        for (Node child = node.getFirstChild(); child != null; child = child.getNext()) children.add(child);
        return children;
    }

    private void inlineChildren(Node node, XWPFParagraph paragraph, boolean bold, boolean italic, String url, int depth) {
        checkDepth(depth);
        for (Node child = node.getFirstChild(); child != null; child = child.getNext()) {
            if (child instanceof Text text) run(paragraph, text.getLiteral(), bold, italic, url, false);
            else if (child instanceof Code code) run(paragraph, code.getLiteral(), bold, italic, url, true);
            else if (child instanceof StrongEmphasis) inlineChildren(child, paragraph, true, italic, url, depth + 1);
            else if (child instanceof Emphasis) inlineChildren(child, paragraph, bold, true, url, depth + 1);
            else if (child instanceof Link link) inlineChildren(child, paragraph, bold, italic, safeLink(link.getDestination()), depth + 1);
            else if (child instanceof HardLineBreak) paragraph.createRun().addBreak();
            else if (child instanceof SoftLineBreak) run(paragraph, " ", bold, italic, url, false);
            else if (child instanceof HtmlInline html) run(paragraph, html.getLiteral(), bold, italic, null, false);
            else inlineChildren(child, paragraph, bold, italic, url, depth + 1);
        }
    }

    private void run(XWPFParagraph paragraph, String text, boolean bold, boolean italic, String url, boolean code) {
        XWPFRun run = url == null ? paragraph.createRun() : paragraph.createHyperlinkRun(url);
        run.setText(text);
        if (bold) run.setBold(true);
        if (italic) run.setItalic(true);
        if (url != null) run.setUnderline(UnderlinePatterns.SINGLE);
        if (code) {
            run.setFontFamily("Consolas");
            run.setFontSize(10);
            run.getCTR().getRPr().addNewShd().setFill("F2F3F5");
        }
    }

    private String safeLink(String destination) {
        try {
            String scheme = URI.create(destination).getScheme();
            return scheme != null && List.of("https", "http", "mailto").contains(scheme.toLowerCase(java.util.Locale.ROOT))
                    ? destination : null;
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private void checkDepth(int depth) {
        if (depth > 64) throw new IllegalArgumentException("Document structure is nested too deeply");
    }
}
