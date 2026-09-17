package com.agentsflex.showcase.knowledge.parser;

import java.util.List;

/**
 * 文档解析器：把受支持格式的字节流解析为统一的 Markdown 正文与元数据。
 *
 * <p>对齐 WeKnora docreader 的产物契约：所有格式最终归一为 Markdown，使下游切片器
 * 只需处理一种输入形态。实现必须无状态且线程安全（上传可能并发触发解析）。</p>
 */
public interface DocumentParser {

    /**
     * @return 该解析器负责的文件扩展名（小写、不含点号），如 "pdf"、"docx"
     */
    List<String> extensions();

    /**
     * @return 解析器展示名，用于前端格式提示与状态展示
     */
    String name();

    /**
     * 解析文档。
     *
     * @param fileName 原始文件名（用于标题推断与格式判定）
     * @param bytes    文件字节流
     * @return 解析产物（归一化 Markdown + 元数据）
     * @throws DocumentParseException 解析失败时抛出，消息为可操作的中文提示
     */
    ParsedDocument parse(String fileName, byte[] bytes) throws DocumentParseException;
}
