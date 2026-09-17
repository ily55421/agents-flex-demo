# -*- coding: utf-8 -*-
# 验证 Agent 对话消息渲染升级：助手消息按 Markdown 渲染（表格/代码块/标题），
# ```mermaid 代码块渲染为图表 SVG；流式输出与终态 RunResult 不回归。
import sys
import io

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')

from playwright.sync_api import sync_playwright

BASE = 'http://localhost:5173'
PROMPT = ('不要调用任何工具，直接回答。严格按此 Markdown 作答：'
          '一个二级标题「## 渲染测试」；一个两列表格（表头 站名/电压，两行数据）；'
          '一行 `行内代码` 示例；最后输出一个 ```mermaid 代码块，内容是 graph TD; A-->B; B-->C;。')

with sync_playwright() as p:
    browser = p.chromium.launch(headless=True)
    page = browser.new_page(viewport={'width': 1680, 'height': 950})
    errors = []
    page.on('console', lambda m: errors.append(m.text) if m.type == 'error' else None)
    page.goto(BASE)
    page.wait_for_load_state('networkidle')
    page.wait_for_timeout(1500)

    # 1. 选择第一个可运行 Agent
    select = page.locator('#workspace-agent-select')
    select.wait_for(state='visible', timeout=10000)
    page.wait_for_timeout(600)
    options = select.locator('option')
    assert options.count() > 1, '智能体列表为空'
    first_value = options.nth(1).get_attribute('value')
    select.select_option(value=first_value)
    page.wait_for_timeout(400)
    print('[1] 选择 Agent: OK ->', options.nth(1).inner_text())

    # 2. 发送要求 Markdown + mermaid 的消息
    textarea = page.locator('textarea[aria-label="输入对话内容"]')
    assert textarea.is_enabled(), '输入框不可用（模型未配置或未选 Agent）'
    textarea.fill(PROMPT)
    textarea.press('Enter')
    print('[2] 消息已发送，等待流式渲染…')

    # 3. 流式阶段应出现 Markdown 容器（消息或流式区二选一先到）
    page.wait_for_selector('.md-body', timeout=30000)
    print('[3] Markdown 容器出现: OK')

    # 4. 等待 Run 终态（result-panel 或停止流式），最长 4 分钟
    page.wait_for_selector('.result-panel', timeout=240000)
    page.wait_for_timeout(1200)
    print('[4] Run 终态到达: OK ->', page.locator('.result-panel strong').inner_text())

    # 5. 助手消息 Markdown 元素断言
    message_md = page.locator('.chat-message.message-assistant .md-body')
    assert message_md.count() >= 1, '助手消息未走 Markdown 渲染'
    last_md = message_md.last
    tables = last_md.locator('table').count()
    headings = last_md.locator('h2').count()
    inline_code = last_md.locator('p code, li code').count()
    print(f'[5] Markdown 元素: 表格 {tables} / 标题 {headings} / 行内代码 {inline_code}')
    assert headings >= 1, '二级标题未渲染'
    assert inline_code >= 1, '行内代码未渲染'

    # 6. mermaid 图表：优先 SVG；若模型输出异常语法则应看到回退代码块
    mermaid_svg = page.locator('.md-body .md-mermaid svg').count()
    mermaid_fallback = page.locator('.md-mermaid-fallback').count()
    print(f'[6] Mermaid: svg={mermaid_svg} fallback={mermaid_fallback}')
    if mermaid_svg == 0 and mermaid_fallback == 0:
        print('    （模型未输出 mermaid 块，跳过图表断言）')
    else:
        # 防“空白 SVG”回归：每个 SVG 必须有可见尺寸且包含图形元素
        for index in range(mermaid_svg):
            info = page.locator('.md-body .md-mermaid svg').nth(index).evaluate(
                "el => ({w: el.getBoundingClientRect().width, h: el.getBoundingClientRect().height,"
                " shapes: el.querySelectorAll('path, rect, circle, polygon, ellipse').length,"
                " nodes: el.querySelectorAll('.node').length})")
            assert info['w'] > 40 and info['h'] > 40, f'第 {index+1} 个 mermaid SVG 尺寸异常: {info}'
            assert info['shapes'] > 0, f'第 {index+1} 个 mermaid SVG 无图形元素（空白图）: {info}'
            print(f"    SVG#{index+1}: {info['w']:.0f}x{info['h']:.0f}, 形状 {info['shapes']}, 节点 {info['nodes']}")

    # 7. RunResult 终态输出也走 Markdown
    result_md = page.locator('.result-panel .md-body').count()
    print('[7] RunResult Markdown 容器:', result_md)

    page.screenshot(path='task/e2e-chat-markdown.png', full_page=False)
    real_errors = [e for e in errors if 'favicon' not in e.lower()]
    print('[8] 控制台错误:', real_errors[:3] if real_errors else '无')
    assert not real_errors, f'存在控制台错误: {real_errors[:3]}'
    browser.close()
    print('CHAT MARKDOWN ALL PASS')
