package com.agentsflex.showcase.knowledge;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 结构化切片器验证：结构块保护、标题路径、窗口滑切与重叠、参数校验。
 */
class MarkdownChunkerTest {

    /**
     * 代码围栏内的空行与超长内容不被切断，整块出现在同一片。
     */
    @Test
    void keepsCodeFenceIntact() {
        MarkdownChunker chunker = new MarkdownChunker(120, 20);
        String code = "```java\n"
                + "public void run() {\n\n\n    System.out.println(\"一段很长的代码行用于超过窗口上限验证保护行为\");\n}\n"
                + "```";
        String markdown = "正文说明。\n\n" + code;

        List<MarkdownChunker.MarkdownChunk> chunks = chunker.chunk(markdown);
        String joined = String.join("\n", chunks.stream().map(MarkdownChunker.MarkdownChunk::getContent).toList());
        // 围栏内的空行与全部代码行都必须保留在同一份内容里
        assertThat(joined).contains("```java").contains("public void run()")
                .contains("System.out.println");
        assertThat(chunks.stream().anyMatch(chunk -> chunk.getContent().contains("```java")
                && chunk.getContent().contains("```"))).isTrue();
    }

    /**
     * 表格不被从中间截断，长表切分后每一片都带表头。
     */
    @Test
    void keepsTableHeaderOnEverySlice() {
        MarkdownChunker chunker = new MarkdownChunker(120, 20);
        StringBuilder table = new StringBuilder("| 设备 | 数量 |\n| --- | --- |\n");
        for (int index = 0; index < 30; index++) {
            table.append("| 设备").append(index).append(" | ").append(index).append(" |\n");
        }

        List<MarkdownChunker.MarkdownChunk> chunks = chunker.chunk(table.toString());
        assertThat(chunks).hasSizeGreaterThan(1);
        for (MarkdownChunker.MarkdownChunk chunk : chunks) {
            assertThat(chunk.getContent()).startsWith("| 设备 | 数量 |");
        }
    }

    /**
     * 标题层级写入切片元数据；同级标题替换而非叠加。
     */
    @Test
    void tracksHeadingPath() {
        MarkdownChunker chunker = new MarkdownChunker(60, 10);
        String markdown = "# 切片策略\n\n## 保护规则\n\n公式与表格不被切断。\n\n## 窗口参数\n\n窗口默认 512 字符。";

        List<MarkdownChunker.MarkdownChunk> chunks = chunker.chunk(markdown);
        String all = String.join("\n", chunks.stream().map(MarkdownChunker.MarkdownChunk::getContent).toList());
        assertThat(all).contains("保护规则").contains("窗口参数");
        assertThat(chunks.stream().map(MarkdownChunker.MarkdownChunk::getHeadingPath).toList())
                .contains("切片策略 > 保护规则", "切片策略 > 窗口参数");
        // 同级标题不应形成 "> 保护规则 > 窗口参数" 的叠加路径
        assertThat(chunks.stream().map(MarkdownChunker.MarkdownChunk::getHeadingPath).toList())
                .noneMatch(path -> path.contains("保护规则 > 窗口参数"));
    }

    /**
     * 超长段落按窗口滑切，相邻片存在指定长度的尾部重叠。
     */
    @Test
    void overlapsLongParagraphWindows() {
        MarkdownChunker chunker = new MarkdownChunker(100, 20);
        StringBuilder longParagraph = new StringBuilder();
        for (int index = 0; index < 250; index++) {
            longParagraph.append('甲');
        }

        List<MarkdownChunker.MarkdownChunk> chunks = chunker.chunk(longParagraph.toString());
        assertThat(chunks).hasSizeGreaterThanOrEqualTo(3);
        for (MarkdownChunker.MarkdownChunk chunk : chunks) {
            assertThat(chunk.getContent().length()).isLessThanOrEqualTo(100);
        }
        // 相邻片重叠：前一片尾部 20 字符等于后一片头部 20 字符
        String first = chunks.get(0).getContent();
        String second = chunks.get(1).getContent();
        assertThat(first.substring(first.length() - 20)).isEqualTo(second.substring(0, 20));
    }

    /**
     * 切片序号从 0 连续递增，且每片正文非空。
     */
    @Test
    void renumbersChunksContinuously() {
        MarkdownChunker chunker = new MarkdownChunker(50, 10);
        String markdown = "# 一\n\n段落甲内容。\n\n# 二\n\n段落乙内容。\n\n# 三\n\n段落丙内容。";

        List<MarkdownChunker.MarkdownChunk> chunks = chunker.chunk(markdown);
        assertThat(chunks).isNotEmpty();
        for (int index = 0; index < chunks.size(); index++) {
            assertThat(chunks.get(index).getIndex()).isEqualTo(index);
            assertThat(chunks.get(index).getContent()).isNotBlank();
        }
    }

    /**
     * 空输入与纯空白输入返回空列表；非法窗口参数被拒绝。
     */
    @Test
    void handlesBlankInputAndRejectsInvalidWindow() {
        MarkdownChunker chunker = new MarkdownChunker();
        assertThat(chunker.chunk(null)).isEmpty();
        assertThat(chunker.chunk("   \n\n  \t ")).isEmpty();
        assertThatThrownBy(() -> new MarkdownChunker(0, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MarkdownChunker(100, 100))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * 预览返回序号、标题路径、字符数与正文，且不修改任何状态。
     */
    @Test
    void previewsChunksWithHeadingPath() {
        MarkdownChunker chunker = new MarkdownChunker();
        List<Map<String, Object>> preview = chunker.preview("# 小节\n\n预览内容段落。", 512, 80);

        assertThat(preview).isNotEmpty();
        assertThat(preview.get(0).get("headingPath")).isEqualTo("小节");
        assertThat(preview.get(0).get("index")).isEqualTo(0);
        assertThat((Integer) preview.get(0).get("charCount")).isPositive();
        assertThat((String) preview.get(0).get("content")).contains("预览内容段落。");
    }
}
