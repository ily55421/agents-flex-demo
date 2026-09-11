package com.agentsflex.showcase.knowledge;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 切片器的段落边界、滑窗重叠与空输入行为验证。
 */
class TextChunkerTest {

    /**
     * 短段落原样保留，长段落按窗口滑切且相邻片段存在重叠。
     */
    @Test
    void splitsParagraphsAndOverlapsLongWindows() {
        TextChunker chunker = new TextChunker(100, 20);
        StringBuilder longBuilder = new StringBuilder();
        for (int index = 0; index < 250; index++) {
            longBuilder.append('甲');
        }
        String longParagraph = longBuilder.toString();
        List<String> chunks = chunker.chunk("第一段内容。\n\n" + longParagraph + "\n\n第三段内容。");

        assertThat(chunks).hasSizeGreaterThanOrEqualTo(5);
        assertThat(chunks.get(0)).isEqualTo("第一段内容。");
        assertThat(chunks.get(1)).hasSize(100);
        // 相邻滑窗片段应存在 20 字符重叠：前一片尾部与后一片头部一致
        assertThat(chunks.get(1).substring(80)).isEqualTo(chunks.get(2).substring(0, 20));
        assertThat(chunks.get(chunks.size() - 1)).endsWith("第三段内容。");
    }

    /**
     * 空输入与纯空白输入返回空列表。
     */
    @Test
    void returnsEmptyForBlankInput() {
        TextChunker chunker = new TextChunker();
        assertThat(chunker.chunk(null)).isEmpty();
        assertThat(chunker.chunk("   \n\n  \t ")).isEmpty();
    }

    /**
     * 重叠不小于窗口或窗口非正数属于非法构造。
     */
    @Test
    void rejectsInvalidWindowConfiguration() {
        assertThatThrownBy(() -> new TextChunker(0, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TextChunker(100, 100))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
