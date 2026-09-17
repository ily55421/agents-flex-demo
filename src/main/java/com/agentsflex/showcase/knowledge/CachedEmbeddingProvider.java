package com.agentsflex.showcase.knowledge;

import com.yomahub.roguemap.embedding.EmbeddingProvider;
import com.yomahub.roguemap.embedding.UniversalEmbeddingProvider;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 带持久化缓存的 EmbeddingProvider 装饰器。
 *
 * <p>RogueMemory 在 add()（入库/启动重建）与 search()（查询向量化）内部都会调用
 * embed()。本类把每次向量计算按 (签名, 文本哈希) 落到 {@link KnowledgeVectorCache}：
 * 命中则零网络返回；未命中才请求真实服务并回填。应用重启后的全量重建因此退化为
 * 本地读，embedding 服务短时抖动也不再拖垮启动。</p>
 *
 * <p>getDimension() 原样委托：HNSW 索引构建依赖该值，且 configure() 在装配本装饰器
 * 之前已对真实服务做过探测，维度必然有效。</p>
 */
public class CachedEmbeddingProvider implements EmbeddingProvider {

    private final UniversalEmbeddingProvider delegate;
    private final KnowledgeVectorCache cache;
    private final String signature;

    /**
     * @param delegate  真实 embedding 客户端（已被 configure() 探测过维度）
     * @param signature embedding 签名（endpoint|model），缓存命名空间
     * @param cache     向量缓存；为 null 时退化为直连（测试场景）
     */
    public CachedEmbeddingProvider(UniversalEmbeddingProvider delegate, String signature,
                                   KnowledgeVectorCache cache) {
        this.delegate = delegate;
        this.signature = signature;
        this.cache = cache;
    }

    @Override
    public float[] embed(String text) {
        if (text == null || signature == null || cache == null) {
            return delegate.embed(text);
        }
        String hash = sha256(text);
        float[] cached = cache.loadVector(signature, hash);
        if (cached != null && cached.length > 0) {
            return cached;
        }
        float[] vector = delegate.embed(text);
        try {
            cache.saveVector(signature, hash, vector);
        } catch (RuntimeException ignored) {
            // 缓存写失败只损失一次加速机会，不影响本次向量化结果。
        }
        return vector;
    }

    @Override
    public int getDimension() {
        return delegate.getDimension();
    }

    /**
     * 文本 SHA-256 十六进制串；与签名共同唯一定位一份向量。
     */
    private static String sha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16))
                        .append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (Exception error) {
            throw new IllegalStateException("SHA-256 不可用", error);
        }
    }
}
