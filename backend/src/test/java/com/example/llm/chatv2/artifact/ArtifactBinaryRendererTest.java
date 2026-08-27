package com.example.llm.chatv2.artifact;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ArtifactBinaryRendererTest {
    private final ArtifactBinaryRenderer renderer = new ArtifactBinaryRenderer(new ObjectMapper());
    private static final String GUIDE = """
            # AI 助手使用指南（测试版）

            欢迎使用 AI 助手！本文档旨在帮助您快速了解如何与 AI 进行高效互动。

            ## 1. 基本功能

            - **问答解答**：您可以向我提出任何领域的知识问题。
            - **内容创作**：我可以帮助您撰写文章、邮件、报告等。
            - **代码辅助**：提供代码编写、调试和解释服务。

            ## 2. 提问技巧

            为了获得更好的回答效果，建议您：

            1. 提供清晰的背景信息。
            2. 明确您的具体需求。
            3. 指定输出的格式（如表格、列表、Markdown 等）。

            ## 3. 注意事项

            > **提示**：AI 生成的内容仅供参考，重要决策请结合实际情况核实。

            祝您使用愉快！
            """;

    @Test
    void exportsRealPageStylesAndNumberingInsteadOfStrippedMarkdown() throws Exception {
        byte[] bytes = renderer.render(draft(GUIDE)).bytes();
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(bytes))) {
            var section = document.getDocument().getBody().getSectPr();
            assertThat(section.getPgSz().getW().toString()).isEqualTo("11906");
            assertThat(section.getPgSz().getH().toString()).isEqualTo("16838");
            assertThat(section.getPgMar().getLeft().toString()).isEqualTo("1360");
            assertThat(document.getStyles().getStyle("Normal")).isNotNull();
            assertThat(document.getStyles().getStyle("Heading1")).isNotNull();
            assertThat(document.getParagraphs().get(0).getStyle()).isEqualTo("Title");
            var listItems = document.getParagraphs().stream().filter(p -> p.getNumID() != null).toList();
            assertThat(listItems).hasSize(6);
            assertThat(document.getNumbering().getAbstractNums()).hasSize(2);
            assertThat(document.getParagraphs()).anyMatch(p -> "Quote".equals(p.getStyle()) && p.getText().startsWith("提示"));
            assertThat(document.getParagraphs().stream().flatMap(p -> p.getRuns().stream()))
                    .anyMatch(run -> run.isBold() && run.text().equals("问答解答"));
            assertThat(document.getParagraphs().stream().map(XWPFParagraph::getText))
                    .noneMatch(text -> text.startsWith("#") || text.startsWith(">") || text.contains("**"));
        }
        writeQaFile("generated-guide.docx", bytes);
    }

    @Test
    void preservesNestedListsCodeTablesAndSafeLinks() throws Exception {
        String markdown = """
                # 文档结构验收

                正文包含 **重点**、*强调*、`inline_code` 和 [官方文档](https://example.com/docs)。

                ## 列表与连续说明

                3. 第三步
                4. 第四步
                   - 子步骤一
                   - 子步骤二

                独立段落。

                1. 新列表从一开始

                ## 代码示例

                ```ts
                const value = "**保留代码里的符号**";
                if (count < 3) {
                  console.log(value);
                }
                ```

                ## 功能对照

                | 功能 | 说明 | 状态 |
                | :--- | :--- | :---: |
                | 文档预览 | 标题、列表、引用与代码应保持完整 | 已完成 |
                | 下载 | 下载后使用 Word 查看相同的排版 | 可用 |

                > 引用内容应有清楚的缩进和分隔，不应显示 Markdown 的大于号。

                [安全降级](javascript:alert%281%29)
                """;
        byte[] bytes = renderer.render(draft(markdown)).bytes();
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(bytes))) {
            assertThat(document.getParagraphs()).anyMatch(p -> BigInteger.ONE.equals(p.getNumIlvl()));
            assertThat(document.getNumbering().getAbstractNums().get(0).getCTAbstractNum().getLvlArray(0)
                    .getStart().getVal()).isEqualTo(BigInteger.valueOf(3));
            assertThat(document.getNumbering().getAbstractNums().get(2).getCTAbstractNum().getLvlArray(0)
                    .getStart().getVal()).isEqualTo(BigInteger.ONE);
            assertThat(document.getParagraphs()).anyMatch(p -> "CodeBlock".equals(p.getStyle())
                    && p.getText().contains("**保留代码里的符号**"));
            assertThat(document.getParagraphs()).anyMatch(p -> p.getText().equals("  console.log(value);"));
            assertThat(document.getHyperlinks()).hasSize(1);
            assertThat(document.getHyperlinks()[0].getURL()).isEqualTo("https://example.com/docs");
            assertThat(document.getTables()).hasSize(1);
            var table = document.getTables().get(0);
            assertThat(table.getNumberOfRows()).isEqualTo(3);
            assertThat(table.getRow(0).isRepeatHeader()).isTrue();
            assertThat(table.getWidth()).isEqualTo(9186);
            int gridWidth = table.getCTTbl().getTblGrid().getGridColList().stream()
                    .mapToInt(col -> Integer.parseInt(col.getW().toString())).sum();
            assertThat(gridWidth).isEqualTo(table.getWidth());
            for (var row : table.getRows()) {
                assertThat(row.getTableCells().stream().mapToInt(cell -> cell.getWidth()).sum()).isEqualTo(table.getWidth());
            }
            assertThat(table.getRow(1).getCell(1).getText()).contains("标题、列表、引用");
        }
        writeQaFile("generated-structures.docx", bytes);
    }

    private ArtifactDraftRepository.ArtifactRow draft(String markdown) {
        Instant now = Instant.now();
        return new ArtifactDraftRepository.ArtifactRow(1L, "ARTIFACT", 1L, 1L, "CONVERSATION", 1L,
                "RUN", 1L, ArtifactModels.Type.DOCUMENT, "DRAFT", "文档预览验收", 1,
                Map.of("markdown", markdown), 1, null, null, null, null, 0, now, now, null);
    }

    private void writeQaFile(String name, byte[] bytes) throws Exception {
        String output = System.getProperty("docx.qa.directory");
        if (output == null) return;
        Path directory = Path.of(output);
        Files.createDirectories(directory);
        Files.write(directory.resolve(name), bytes);
    }
}
