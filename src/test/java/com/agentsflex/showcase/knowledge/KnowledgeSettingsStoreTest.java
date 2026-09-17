package com.agentsflex.showcase.knowledge;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * KnowledgeSettingsStore 的落盘验证：应用配置与自建预设的读写、同名覆盖、
 * 删除与损坏文件容错；全部在临时目录进行，不触碰 ./data。
 */
class KnowledgeSettingsStoreTest {

    @TempDir
    Path tempDir;

    private KnowledgeSettingsStore settingsStore;
    private Path settingsFile;

    @BeforeEach
    void setUp() {
        settingsFile = tempDir.resolve("nested").resolve("settings.json");
        settingsStore = store();
    }

    /** 每个用例重建 Store，模拟一次进程重启后的重新读取。 */
    private KnowledgeSettingsStore store() {
        KnowledgeProperties properties = new KnowledgeProperties();
        properties.setSettingsPath(settingsFile.toString().replace('\\', '/'));
        return new KnowledgeSettingsStore(properties);
    }

    /**
     * 应用配置保存后可重新读出；模拟重启（新建 Store 实例）后仍然存在。
     */
    @Test
    void appliedConfigSurvivesRestart() {
        settingsStore.saveApplied("http://192.168.31.214:8081/v1", "", "bge-m3", "HYBRID");

        Map<String, String> applied = store().applied();
        assertThat(applied).isNotNull();
        assertThat(applied.get("endpoint")).isEqualTo("http://192.168.31.214:8081/v1");
        assertThat(applied.get("apiKey")).isEmpty();
        assertThat(applied.get("model")).isEqualTo("bge-m3");
        assertThat(applied.get("searchMode")).isEqualTo("HYBRID");
    }

    /**
     * 从未应用过向量模型时返回 null，而不是空 Map。
     */
    @Test
    void returnsNullWhenNothingApplied() {
        assertThat(settingsStore.applied()).isNull();
    }

    /**
     * 预设同名覆盖只保留一条；删除后清单不再包含；重启后仍可读出。
     */
    @Test
    void upsertAndDeletePresets() {
        settingsStore.upsertPreset("内网 Xinference", "http://192.168.31.214:8081/v1", "bge-m3", "");
        settingsStore.upsertPreset("内网 Xinference", "http://192.168.31.214:9997/v1", "bge-m3", "sk-1");
        settingsStore.upsertPreset("云端备用", "https://dashscope.aliyuncs.com/compatible-mode/v1",
                "text-embedding-v3", "sk-2");

        List<Map<String, String>> presets = store().presets();
        assertThat(presets).hasSize(2);
        assertThat(presets.get(0).get("endpoint")).isEqualTo("http://192.168.31.214:9997/v1");
        assertThat(presets.get(0).get("apiKey")).isEqualTo("sk-1");

        assertThat(settingsStore.deletePreset("内网 Xinference")).isTrue();
        assertThat(settingsStore.deletePreset("内网 Xinference")).isFalse();
        assertThat(store().presets()).hasSize(1);
    }

    /**
     * 设置文件损坏时返回空结构而不是抛异常，保证知识库启动不受影响。
     */
    @Test
    void toleratesCorruptedSettingsFile() throws Exception {
        settingsStore.upsertPreset("预设", "http://127.0.0.1:18888/v1", "bge-m3", "");
        Files.writeString(settingsFile, "{not valid json");

        KnowledgeSettingsStore restarted = store();
        assertThat(restarted.presets()).isEmpty();
        assertThat(restarted.applied()).isNull();
    }
}
