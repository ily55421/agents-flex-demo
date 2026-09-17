package com.agentsflex.showcase.knowledge;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 知识库 embedding 设置的本地持久化：最近一次应用的向量模型配置 + 用户自建预设。
 *
 * <p>数据落在单个 JSON 文件（默认 ./data/knowledge-settings.json）。API Key 以明文
 * 保存：本项目是本机演示应用，浏览器端“我的档案”本就以明文 localStorage 存放 Key，
 * 两处安全等级一致；文件回显仅面向同一本机前端，用于重启后免重填快速重连。</p>
 */
@Component
public class KnowledgeSettingsStore {

    private final Path path;
    private final ObjectMapper mapper = new ObjectMapper();
    private final Object lock = new Object();

    /**
     * @param properties 提供设置文件路径（agents-flex.knowledge.settings-path）
     */
    public KnowledgeSettingsStore(KnowledgeProperties properties) {
        this.path = Path.of(properties.getSettingsPath());
    }

    /**
     * @return 最近一次应用的向量模型配置（endpoint/apiKey/model/searchMode）；从未应用过返回 {@code null}
     */
    public Map<String, String> applied() {
        synchronized (lock) {
            Map<String, Object> settings = read();
            Object applied = settings.get("applied");
            return applied instanceof Map ? asStringMap(applied) : null;
        }
    }

    /**
     * 记录最近一次应用的向量模型配置（连接失败也保存意图，重启后自动重试）。
     *
     * @param endpoint OpenAI 兼容服务根地址
     * @param apiKey   服务密钥，可空
     * @param model    向量模型名
     * @param mode     检索模式名
     */
    public void saveApplied(String endpoint, String apiKey, String model, String mode) {
        synchronized (lock) {
            Map<String, Object> settings = read();
            Map<String, String> applied = new LinkedHashMap<>();
            applied.put("endpoint", endpoint);
            applied.put("apiKey", apiKey == null ? "" : apiKey);
            applied.put("model", model);
            applied.put("searchMode", mode == null || mode.isBlank() ? "HYBRID" : mode);
            applied.put("savedAt", String.valueOf(System.currentTimeMillis()));
            settings.put("applied", applied);
            write(settings);
        }
    }

    /**
     * @return 用户自建预设清单；每项含 name/endpoint/model/apiKey
     */
    public List<Map<String, String>> presets() {
        synchronized (lock) {
            return readPresets(read());
        }
    }

    /**
     * 新增或同名覆盖一个用户预设。
     *
     * @param name     预设名称（唯一键）
     * @param endpoint 服务地址
     * @param model    向量模型名
     * @param apiKey   服务密钥，可空
     */
    public void upsertPreset(String name, String endpoint, String model, String apiKey) {
        synchronized (lock) {
            Map<String, Object> settings = read();
            List<Map<String, String>> presets = readPresets(settings);
            Map<String, String> preset = new LinkedHashMap<>();
            preset.put("name", name);
            preset.put("endpoint", endpoint);
            preset.put("model", model);
            preset.put("apiKey", apiKey == null ? "" : apiKey);
            presets.removeIf(existing -> name.equals(existing.get("name")));
            presets.add(preset);
            settings.put("presets", presets);
            write(settings);
        }
    }

    /**
     * @param name 预设名称
     * @return 是否确实删除了同名预设
     */
    public boolean deletePreset(String name) {
        synchronized (lock) {
            Map<String, Object> settings = read();
            List<Map<String, String>> presets = readPresets(settings);
            boolean removed = presets.removeIf(existing -> name.equals(existing.get("name")));
            if (removed) {
                settings.put("presets", presets);
                write(settings);
            }
            return removed;
        }
    }

    /**
     * 读取整个设置文件；文件缺失或损坏时返回空结构，不阻断启动（损坏的旧设置被忽略）。
     */
    private Map<String, Object> read() {
        if (!Files.exists(path)) return new LinkedHashMap<>();
        try {
            Map<String, Object> raw = mapper.readValue(path.toFile(),
                    new TypeReference<Map<String, Object>>() {
                    });
            return raw == null ? new LinkedHashMap<>() : raw;
        } catch (Exception error) {
            return new LinkedHashMap<>();
        }
    }

    /**
     * 原子写入设置文件：先写临时文件再移动，避免写一半被读取或进程退出损坏。
     */
    private void write(Map<String, Object> settings) {
        try {
            Path parent = path.toAbsolutePath().getParent();
            if (parent != null) Files.createDirectories(parent);
            Path temp = path.resolveSibling(path.getFileName() + ".tmp");
            Files.writeString(temp, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(settings));
            try {
                Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException error) {
            throw new IllegalStateException("保存知识库设置失败：" + error.getMessage(), error);
        }
    }

    /**
     * 从设置中取出合法的预设条目；缺关键字段或类型不对的条目直接丢弃。
     */
    private static List<Map<String, String>> readPresets(Map<String, Object> settings) {
        List<Map<String, String>> presets = new ArrayList<>();
        if (settings.get("presets") instanceof List<?> raw) {
            for (Object item : raw) {
                if (!(item instanceof Map)) continue;
                Map<String, String> preset = asStringMap(item);
                if (preset.get("name") == null || preset.get("name").isBlank()) continue;
                if (preset.get("endpoint") == null || preset.get("endpoint").isBlank()) continue;
                presets.add(preset);
            }
        }
        return presets;
    }

    /**
     * 把无类型的 JSON 对象规整为字符串映射，避免后续取值时类型判断散落各处。
     */
    private static Map<String, String> asStringMap(Object raw) {
        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : ((Map<?, ?>) raw).entrySet()) {
            if (entry.getValue() != null) result.put(String.valueOf(entry.getKey()),
                    String.valueOf(entry.getValue()));
        }
        return result;
    }
}
