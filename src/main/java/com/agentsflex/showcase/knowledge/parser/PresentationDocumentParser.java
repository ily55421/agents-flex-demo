package com.agentsflex.showcase.knowledge.parser;

import org.apache.poi.hslf.usermodel.HSLFSlideShow;
import org.apache.poi.sl.usermodel.Slide;
import org.apache.poi.sl.usermodel.SlideShow;
import org.apache.poi.sl.usermodel.TextShape;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 演示文稿解析器：pptx / ppt，每页转为「页码标题 + 形状文本」。
 *
 * <p>幻灯片标题形状优先输出，其余文本框按顺序追加，使同页内容在切片时保持在一起；
 * 页码写入标题路径（如「演示文稿 > Slide 3」），便于引用定位到具体页。</p>
 */
@Component
public class PresentationDocumentParser implements DocumentParser {

    /** 单页最大文本块数，防止异常文件产生超大单页。 */
    private static final int MAX_SHAPES_PER_SLIDE = 200;

    @Override
    public List<String> extensions() {
        return Arrays.asList("pptx", "ppt");
    }

    @Override
    public String name() {
        return "演示文稿解析器";
    }

    @Override
    public ParsedDocument parse(String fileName, byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            throw new DocumentParseException("文件内容为空：" + fileName);
        }
        boolean legacy = fileName != null && fileName.toLowerCase(Locale.ROOT).endsWith(".ppt");
        StringBuilder markdown = new StringBuilder();
        int slideCount;
        try {
            slideCount = legacy ? renderLegacy(bytes, markdown) : renderOoxml(bytes, markdown);
        } catch (Exception error) {
            throw new DocumentParseException("演示文稿解析失败（文件可能损坏）：" + fileName
                    + "，" + rootMessage(error), error);
        }
        if (markdown.toString().strip().isEmpty()) {
            throw new DocumentParseException("演示文稿中没有可入库的文本（可能全部为图片）：" + fileName);
        }
        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("parser", name());
        metadata.put("slideCount", String.valueOf(slideCount));
        return new ParsedDocument(ParsedDocument.normalize(markdown.toString()), metadata);
    }

    /** 解析 pptx。 */
    private static int renderOoxml(byte[] bytes, StringBuilder markdown) throws Exception {
        try (InputStream in = new ByteArrayInputStream(bytes);
             XMLSlideShow slideShow = new XMLSlideShow(in)) {
            return renderSlides(slideShow, markdown);
        }
    }

    /** 解析旧版 ppt。 */
    private static int renderLegacy(byte[] bytes, StringBuilder markdown) throws Exception {
        try (InputStream in = new ByteArrayInputStream(bytes);
             HSLFSlideShow slideShow = new HSLFSlideShow(in)) {
            return renderSlides(slideShow, markdown);
        }
    }

    /** 通用渲染：两类 SlideShow 实现共享同一套形状遍历逻辑。 */
    private static int renderSlides(SlideShow<?, ?> slideShow, StringBuilder markdown) {
        List<? extends Slide<?, ?>> slides = slideShow.getSlides();
        for (int index = 0; index < slides.size(); index++) {
            Slide<?, ?> slide = slides.get(index);
            StringBuilder body = new StringBuilder();
            int shapes = 0;
            for (Object shape : slide.getShapes()) {
                if (shapes >= MAX_SHAPES_PER_SLIDE) break;
                if (!(shape instanceof TextShape textShape)) continue;
                String text = textShape.getText();
                if (text == null || text.strip().isEmpty()) continue;
                shapes++;
                body.append(text.strip().replaceAll("[\\r\\n]+", "\n")).append("\n\n");
            }
            if (body.length() == 0) continue;
            markdown.append("## Slide ").append(index + 1).append("\n\n").append(body).append('\n');
        }
        return slides.size();
    }

    /** 取最底层异常消息，压成单行。 */
    private static String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        String raw = current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
        String text = raw.replaceAll("\\s+", " ").trim();
        return text.length() > 120 ? text.substring(0, 120) + "..." : text;
    }
}
