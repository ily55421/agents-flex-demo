package com.agentsflex.showcase.observability;

import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SpanExporter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 收集单个 Run 导出的 OpenTelemetry Span。
 * CopyOnWriteArrayList 允许 Agent 执行线程写入、HTTP 查询线程同时读取；snapshot 按开始时间排序，
 * 让前端可以稳定重建父子树和时间顺序。
 */
public final class InMemorySpanExporter implements SpanExporter {

    private final List<SpanData> spans = new CopyOnWriteArrayList<>();

    /**
     * 接收 SDK 批量导出的 Span，并追加到当前 Run 的线程安全集合。
     *
     * @param values 本轮结束的 SpanData
     * @return 已完成的成功结果，不阻塞 Agent 执行线程
     */
    @Override
    public CompletableResultCode export(Collection<SpanData> values) {
        if (values != null) spans.addAll(values);
        return CompletableResultCode.ofSuccess();
    }

    /**
     * 创建按开始时间排序的防御性副本，供 HTTP 查询线程稳定构建 Span 树。
     *
     * @return 不与 exporter 内部集合共享结构的 Span 列表
     */
    public List<SpanData> snapshot() {
        List<SpanData> copy = new ArrayList<>(spans);
        copy.sort(Comparator.comparingLong(SpanData::getStartEpochNanos));
        return copy;
    }

    /**
     * @return 内存 exporter 无缓冲操作，直接返回成功结果
     */
    @Override
    public CompletableResultCode flush() {
        return CompletableResultCode.ofSuccess();
    }

    /**
     * @return 内存 exporter 无外部连接，关闭时直接返回成功结果
     */
    @Override
    public CompletableResultCode shutdown() {
        return CompletableResultCode.ofSuccess();
    }
}
