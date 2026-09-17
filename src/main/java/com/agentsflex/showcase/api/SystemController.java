package com.agentsflex.showcase.api;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 优雅停机端点：Windows 上无法向 Java 进程发送 Ctrl+C，强杀（Stop-Process）不会触发
 * JVM shutdown hook，导致 RogueMemory 的 dirty 标记残留、下次启动隔离知识库数据文件。
 * 该端点在新线程中延迟调用 {@link Runtime#exit(int)}，确保请求先返回、再由 JVM
 * shutdown hook 走 {@code @PreDestroy} 释放 mmap 并清除 dirty 标记。
 */
@RestController
@RequestMapping("/api/system")
public class SystemController {

    /**
     * 触发应用优雅退出：先返回响应，300ms 后由 JVM shutdown hook 关闭 Spring 容器。
     *
     * @return 停机确认
     */
    @PostMapping("/shutdown")
    public Map<String, Object> shutdown() {
        new Thread(() -> {
            try {
                Thread.sleep(300);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            Runtime.getRuntime().exit(0);
        }, "graceful-shutdown").start();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("shuttingDown", true);
        result.put("message", "应用即将退出，已触发优雅停机钩子");
        return result;
    }
}
