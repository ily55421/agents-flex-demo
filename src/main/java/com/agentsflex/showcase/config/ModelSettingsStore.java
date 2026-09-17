package com.agentsflex.showcase.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 聊天模型连接设置的本地持久化：最近一次应用的模型连接配置。
 *
 * <p>数据落在单个 JSON 文件（默认 ./data/model-settings.json）。API Key 以明文保存：
 * 本项目是本机演示应用，与知识库设置（knowledge-settings.json）安全等级一致；
 * 目的是后端重启后自动恢复模型连接，用户不必每次重启都到「模型配置」页重新应用。</p>
 */
@Component
public class ModelSettingsStore {

    private final Path path;
    private final ObjectMapper mapper = new ObjectMapper();
    private final Object lock = new Object();

    /**
     * @param properties 提供设置文件路径（agents-flex.model.settings-path）
     */
    public ModelSettingsStore(ModelProperties properties) {
        this.path = Path.of(properties.getSettingsPath());
    }

    /**
     * @return 最近一次应用的模型连接字段；从未应用过返回 {@code null}
     */
    public Map<String, Object> applied() {
        synchronized (lock) {
            Map<String, Object> settings = read();
            Object applied = settings.get("applied");
            return applied instanceof Map ? asStringKeyMap(applied) : null;
        }
    }

    /**
     * 记录最近一次应用的模型连接字段（连接失败也保存意图，重启后自动重试恢复）。
     *
     * @param fields UpdateModelRequest 的字段快照
     */
    public void saveApplied(Map<String, Object> fields) {
        synchronized (lock) {
            Map<String, Object> settings = new LinkedHashMap<>();
            settings.put("applied", fields);
            settings.put("savedAt", System.currentTimeMillis());
            write(settings);
        }
    }

    private Map<String, Object> read() {
        try {
            if (!Files.exists(path)) {
                return Map.of();
            }
            return mapper.readValue(path.toFile(), new TypeReference<Map<String, Object>>() {
            });
        } catch (IOException error) {
            return Map.of();
        }
    }

    private void write(Map<String, Object> settings) {
        try {
            Files.createDirectories(path.getParent());
            Path temp = path.resolveSibling(path.getFileName() + ".tmp");
            mapper.writerWithDefaultPrettyPrinter().writeValue(temp.toFile(), settings);
            try {
                Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException error) {
                Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException error) {
            // 持久化失败不阻断应用：仅失去重启恢复能力，连接本身已生效
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asStringKeyMap(Object value) {
        return (Map<String, Object>) value;
    }
}
