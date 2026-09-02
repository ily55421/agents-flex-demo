package com.agentsflex.showcase.observability;

import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.metrics.Aggregation;
import io.opentelemetry.sdk.metrics.InstrumentType;
import io.opentelemetry.sdk.metrics.data.AggregationTemporality;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.metrics.export.AggregationTemporalitySelector;
import io.opentelemetry.sdk.metrics.export.DefaultAggregationSelector;
import io.opentelemetry.sdk.metrics.export.MetricExporter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 保存单个 Run 最近一次 Agents-Flex OpenTelemetry Metric 导出。
 * 指标采用 cumulative temporality，因此每轮导出替换旧快照，避免前端把多个累计值重复相加。
 */
public final class InMemoryMetricExporter implements MetricExporter {

    private final List<MetricData> metrics = new CopyOnWriteArrayList<>();
    private final AggregationTemporalitySelector temporality =
            AggregationTemporalitySelector.alwaysCumulative();
    private final DefaultAggregationSelector aggregation =
            DefaultAggregationSelector.getDefault();

    /**
     * 用本轮累计 Metric 替换旧快照，防止 UI 对累计值重复求和。
     *
     * @param values SDK 本轮导出的 MetricData
     * @return 已完成的成功结果
     */
    @Override
    public CompletableResultCode export(Collection<MetricData> values) {
        metrics.clear();
        if (values != null) metrics.addAll(values);
        return CompletableResultCode.ofSuccess();
    }

    /**
     * @return 最近一次 Metric 导出的防御性副本
     */
    public List<MetricData> snapshot() {
        return new ArrayList<>(metrics);
    }

    /**
     * 告知 SDK 本 exporter 对所有 instrument 使用累计时序。
     *
     * @param instrumentType OTel instrument 类型
     * @return cumulative aggregation temporality
     */
    @Override
    public AggregationTemporality getAggregationTemporality(InstrumentType instrumentType) {
        return temporality.getAggregationTemporality(instrumentType);
    }

    /**
     * 委托 OTel 默认选择器决定各 instrument 的聚合方式。
     *
     * @param instrumentType OTel instrument 类型
     * @return SDK 推荐的默认 Aggregation
     */
    @Override
    public Aggregation getDefaultAggregation(InstrumentType instrumentType) {
        return aggregation.getDefaultAggregation(instrumentType);
    }

    /**
     * @return 指标已保存在内存中，无额外 flush 操作
     */
    @Override
    public CompletableResultCode flush() {
        return CompletableResultCode.ofSuccess();
    }

    /**
     * @return exporter 不持有外部资源，关闭时直接成功
     */
    @Override
    public CompletableResultCode shutdown() {
        return CompletableResultCode.ofSuccess();
    }
}
