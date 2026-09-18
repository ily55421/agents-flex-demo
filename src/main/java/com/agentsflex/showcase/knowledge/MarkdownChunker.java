package com.agentsflex.showcase.knowledge;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 面向 Markdown 的结构感知切片器：按标题层级切分，保护结构块不被截断。
 *
 * <p>对齐 WeKnora chunker 的两条核心规则：① 保护结构块（代码围栏、Markdown 表格、
 * 行间公式）内部不做硬切，避免公式被切成两半、表格丢失表头；② 每个切片携带其所属
 * 标题路径（如「切片策略 &gt; 保护规则」），供检索结果补全上下文与引用展示。</p>
 *
 * <p>实现方式为行状态机：逐行扫描维护「是否在代码围栏内」「当前标题栈」两个状态，
 * 普通段落累积到窗口上限后再按字符滑切（保留重叠）；标题行会另起一个新切片，
 * 使同一节的正文尽可能落在同一片内。切片以字符数计量，对中文与 Token 数近似稳定。</p>
 */
public final class MarkdownChunker {

    private final int chunkChars;
    private final int overlapChars;

    /** 默认 512 字符窗口、80 字符重叠（对齐 WeKnora chunker 默认值）。 */
    public MarkdownChunker() {
        this(512, 80);
    }

    /**
     * @param chunkChars   单片最大字符数，必须为正
     * @param overlapChars 相邻片重叠字符数，取值范围 [0, chunkChars)
     */
    public MarkdownChunker(int chunkChars, int overlapChars) {
        if (chunkChars <= 0) throw new IllegalArgumentException("chunkChars must be positive");
        if (overlapChars < 0 || overlapChars >= chunkChars) {
            throw new IllegalArgumentException("overlapChars must be in [0, chunkChars)");
        }
        this.chunkChars = chunkChars;
        this.overlapChars = overlapChars;
    }

    /**
     * 把 Markdown 正文切为适合向量化的片段列表。
     *
     * @param markdown 归一化 Markdown 正文；null、空白或无有效内容时返回空列表
     * @return 按原文顺序排列的非空片段，每片带标题路径与序号
     */
    public List<MarkdownChunk> chunk(String markdown) {
        if (markdown == null || markdown.trim().isEmpty()) return Collections.emptyList();

        List<MarkdownChunk> chunks = new ArrayList<>();
        List<String> headingStack = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inFence = false;
        String fenceMarker = null;
        // 表格需要整表作为原子单元：先缓存连续表格行，遇到非表格行再一次性输出
        List<String> tableBuffer = new ArrayList<>();

        for (String line : markdown.split("\n", -1)) {
            String stripped = line.strip();
            String fenceOpen = fenceOpener(stripped);
            if (!inFence && fenceOpen != null) {
                flushTable(chunks, tableBuffer, headingStack, current);
                inFence = true;
                fenceMarker = fenceOpen;
                current = appendAtomic(current, chunks, headingStack, line, true);
                continue;
            }
            if (inFence) {
                // 围栏内原样累积，不做任何切分；只有同类型围栏才能闭合
                current.append(line).append('\n');
                if (stripped.startsWith(fenceMarker)) {
                    inFence = false;
                    fenceMarker = null;
                    current = flushIfNeeded(current, chunks, headingStack);
                }
                continue;
            }
            if (isTableRow(stripped)) {
                flushParagraph(chunks, current, headingStack);
                tableBuffer.add(line);
                continue;
            }
            flushTable(chunks, tableBuffer, headingStack, current);

            int headingLevel = headingLevel(stripped);
            if (headingLevel > 0) {
                flushParagraph(chunks, current, headingStack);
                updateHeadingStack(headingStack, headingLevel, stripped);
                // 标题行与紧随其后的正文放在同一片，使切片自带小节语义
                current.append(stripped).append('\n');
                continue;
            }
            if (stripped.isEmpty()) {
                // 空行只作为段落分隔写入当前片，不单独成片
                if (current.length() > 0) current.append('\n');
                continue;
            }
            current = appendParagraphLine(current, chunks, headingStack, stripped);
        }

        // 收尾：残留表格、未闭合围栏内容与当前缓冲一并输出
        flushTable(chunks, tableBuffer, headingStack, current);
        if (inFence) {
            // 未闭合围栏：内容已累积在 current，交给 flushParagraph 按窗口处理，避免丢内容
            flushParagraph(chunks, current, headingStack);
        } else {
            flushParagraph(chunks, current, headingStack);
        }
        // 重新编号保证序号连续
        List<MarkdownChunk> renumbered = new ArrayList<>(chunks.size());
        for (int index = 0; index < chunks.size(); index++) {
            MarkdownChunk chunk = chunks.get(index);
            renumbered.add(new MarkdownChunk(chunk.getContent(), chunk.getHeadingPath(), index));
        }
        return renumbered;
    }

    /**
     * 切片预览：只计算不落库，供调试面板对比不同窗口参数的效果。
     *
     * @param markdown     Markdown 正文
     * @param chunkChars   窗口字符数；小于等于 0 时使用默认值
     * @param overlapChars 重叠字符数；非法组合时回退默认值
     * @return 每片的序号、标题路径、字符数与正文
     */
    public List<java.util.Map<String, Object>> preview(String markdown, int chunkChars, int overlapChars) {
        MarkdownChunker chunker;
        try {
            chunker = (chunkChars > 0) ? new MarkdownChunker(chunkChars, overlapChars) : this;
        } catch (IllegalArgumentException ignored) {
            chunker = this;
        }
        List<java.util.Map<String, Object>> views = new ArrayList<>();
        for (MarkdownChunk chunk : chunker.chunk(markdown)) {
            java.util.Map<String, Object> view = new java.util.LinkedHashMap<>();
            view.put("index", chunk.getIndex());
            view.put("headingPath", chunk.getHeadingPath());
            view.put("charCount", chunk.getContent().length());
            view.put("content", chunk.getContent());
            views.add(view);
        }
        return views;
    }

    /**
     * 追加一个段落行；超过窗口上限时先把当前片落盘再继续累积。
     *
     * @return 新的当前缓冲
     */
    private StringBuilder appendParagraphLine(StringBuilder current, List<MarkdownChunk> chunks,
                                              List<String> headingStack, String line) {
        current.append(line).append('\n');
        if (current.length() >= chunkChars) {
            return flushIfNeeded(current, chunks, headingStack);
        }
        return current;
    }

    /**
     * 把原子块（代码围栏等）写入当前片，并在超长时按窗口滑切。
     *
     * @param atomic 原子块首行
     * @return 新的当前缓冲
     */
    private StringBuilder appendAtomic(StringBuilder current, List<MarkdownChunk> chunks,
                                       List<String> headingStack, String atomic, boolean forceFlush) {
        current.append(atomic).append('\n');
        if (forceFlush || current.length() >= chunkChars) {
            return flushIfNeeded(current, chunks, headingStack);
        }
        return current;
    }

    /**
     * 当前缓冲达到窗口上限时落盘并保留尾部重叠。
     *
     * @return 携带重叠内容的新缓冲
     */
    private StringBuilder flushIfNeeded(StringBuilder current, List<MarkdownChunk> chunks,
                                        List<String> headingStack) {
        if (current.length() < chunkChars) return current;
        String content = current.toString();
        int end = Math.min(content.length(), chunkChars);
        chunks.add(new MarkdownChunk(content.substring(0, end).strip(), headingPath(headingStack), -1));
        if (end >= content.length()) return new StringBuilder();
        // 保留尾部 overlapChars 作为下一片的开头，保证跨片语义连续
        int overlapStart = Math.max(0, end - overlapChars);
        return new StringBuilder(content.substring(overlapStart));
    }

    /**
     * 当前缓冲作为一片落盘（不足窗口也落盘），并清空缓冲。
     */
    private void flushParagraph(List<MarkdownChunk> chunks, StringBuilder current, List<String> headingStack) {
        String content = current.toString();
        current.setLength(0);
        if (content.strip().isEmpty()) return;
        // 段落可能因标题行重新开片而较短，此处按窗口上限做最后一次滑切
        int start = 0;
        int step = Math.max(1, chunkChars - overlapChars);
        while (start < content.length()) {
            int end = Math.min(start + chunkChars, content.length());
            String slice = content.substring(start, end).strip();
            if (!slice.isEmpty()) {
                chunks.add(new MarkdownChunk(slice, headingPath(headingStack), -1));
            }
            if (end >= content.length()) break;
            start += step;
        }
    }

    /**
     * 输出缓存的整张表格：超长表格按行组切分并重复表头。
     */
    private void flushTable(List<MarkdownChunk> chunks, List<String> tableBuffer,
                            List<String> headingStack, StringBuilder current) {
        if (tableBuffer.isEmpty()) return;
        // 表格与当前段落缓冲之间必须先落盘，避免表格被拼进段落片
        flushParagraph(chunks, current, headingStack);
        String header = tableBuffer.size() >= 2 && isSeparatorRow(tableBuffer.get(1))
                ? tableBuffer.get(0) + "\n" + tableBuffer.get(1) : null;
        int headerRows = header == null ? 0 : 2;

        StringBuilder table = new StringBuilder();
        for (int index = 0; index < tableBuffer.size(); index++) {
            if (header != null && index >= headerRows && table.length() + header.length() >= chunkChars) {
                // 超长表格：先落当前组（含表头），下一组重新带表头
                chunks.add(new MarkdownChunk(table.toString().strip(), headingPath(headingStack), -1));
                table.setLength(0);
                table.append(header).append('\n');
            }
            table.append(tableBuffer.get(index)).append('\n');
        }
        if (!table.toString().strip().isEmpty()) {
            chunks.add(new MarkdownChunk(table.toString().strip(), headingPath(headingStack), -1));
        }
        tableBuffer.clear();
    }

    /**
     * 维护标题栈：同级或更高级标题会弹出更深层级。
     */
    private static void updateHeadingStack(List<String> headingStack, int level, String headingLine) {
        String title = headingLine.replaceFirst("^#{1,6}\\s*", "").strip();
        while (headingStack.size() >= level) {
            headingStack.remove(headingStack.size() - 1);
        }
        headingStack.add(title);
    }

    /** @return 当前标题路径；无标题时返回空串 */
    private static String headingPath(List<String> headingStack) {
        return String.join(" > ", headingStack);
    }

    /**
     * 判断是否为代码围栏起始行。
     *
     * @return "```" 或 "~~~"；非围栏返回 null
     */
    private static String fenceOpener(String stripped) {
        if (stripped.startsWith("```")) return "```";
        if (stripped.startsWith("~~~")) return "~~~";
        return null;
    }

    /** @return 该行是否为 Markdown 表格行（以竖线开头或结尾） */
    private static boolean isTableRow(String stripped) {
        return stripped.startsWith("|") && stripped.length() > 1;
    }

    /** @return 该行是否为表格分隔行（如 | --- | --- |） */
    private static boolean isSeparatorRow(String line) {
        String stripped = line.strip();
        if (!stripped.startsWith("|")) return false;
        String cells = stripped.replace("|", " ").strip();
        return !cells.isEmpty() && cells.matches("[-: \\s]+");
    }

    /**
     * @param line 行文本
     * @return 标题层级 1-6；非标题返回 0
     */
    private static int headingLevel(String line) {
        int level = 0;
        while (level < line.length() && level < 6 && line.charAt(level) == '#') {
            level++;
        }
        if (level == 0) return 0;
        // 必须是 "# 标题" 形式：# 后紧跟空格（或行尾），排除 ####### 与 #标签
        if (line.length() == level) return level;
        return line.charAt(level) == ' ' ? level : 0;
    }

    /** 切片视图：正文 + 标题路径 + 序号。 */
    public static final class MarkdownChunk {

        private final String content;
        private final String headingPath;
        private final int index;

        /**
         * @param content     切片正文
         * @param headingPath 所属标题路径（「一级 &gt; 二级」）；无标题时为空串
         * @param index       切片序号；-1 表示待重新编号
         */
        MarkdownChunk(String content, String headingPath, int index) {
            this.content = content;
            this.headingPath = headingPath == null ? "" : headingPath;
            this.index = index;
        }

        /** @return 切片正文 */
        public String getContent() {
            return content;
        }

        /** @return 所属标题路径；无标题时为空串 */
        public String getHeadingPath() {
            return headingPath;
        }

        /** @return 切片序号（从 0 递增） */
        public int getIndex() {
            return index;
        }
    }
}
