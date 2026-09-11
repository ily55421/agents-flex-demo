package com.agentsflex.showcase.knowledge;

import java.util.ArrayList;
import java.util.List;

/**
 * 面向中文文本的轻量切片器：先按空行分段，超长段落再按固定窗口滑切。
 *
 * <p>切片以字符数计量（对中文与 Token 数近似稳定），窗口默认 500 字符、
 * 相邻片段重叠 50 字符，保证跨段语义不被硬切断。</p>
 */
public final class TextChunker {

    private final int chunkChars;
    private final int overlapChars;

    /**
     * @param chunkChars   单片最大字符数，必须大于重叠字符数
     * @param overlapChars 相邻片重叠字符数
     */
    public TextChunker(int chunkChars, int overlapChars) {
        if (chunkChars <= 0) throw new IllegalArgumentException("chunkChars must be positive");
        if (overlapChars < 0 || overlapChars >= chunkChars) {
            throw new IllegalArgumentException("overlapChars must be in [0, chunkChars)");
        }
        this.chunkChars = chunkChars;
        this.overlapChars = overlapChars;
    }

    /** 默认 500 字符窗口、50 字符重叠，适合市场研究类段落文本。 */
    public TextChunker() {
        this(500, 50);
    }

    /**
     * 把原始文本切为适合向量化的片段列表。
     *
     * @param text 原始文本；null、空白或无有效内容时返回空列表
     * @return 按原文顺序排列的非空片段
     */
    public List<String> chunk(String text) {
        List<String> chunks = new ArrayList<>();
        if (text == null || text.trim().isEmpty()) return chunks;
        for (String paragraph : text.split("\\R\\s*\\R")) {
            String normalized = paragraph.trim();
            if (normalized.isEmpty()) continue;
            if (normalized.length() <= chunkChars) {
                chunks.add(normalized);
            } else {
                int step = chunkChars - overlapChars;
                for (int start = 0; start < normalized.length(); start += step) {
                    int end = Math.min(start + chunkChars, normalized.length());
                    chunks.add(normalized.substring(start, end));
                    if (end == normalized.length()) break;
                }
            }
        }
        return chunks;
    }
}
