# -*- coding: utf-8 -*-
# 验证：1) 正文中的“A → B → C”结构化连接链自动转换为 Mermaid 流程图（同名节点合并）；
# 2) 图表点击可打开灯箱放大（滚轮缩放/百分比/键盘 Esc 关闭）。
import sys
import io

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')

from playwright.sync_api import sync_playwright

BASE = 'http://localhost:5173'
PROMPT = ('不要调用任何工具。请原样逐行输出下面两行列表，保持内容完全一致，不要改写：\n'
          '- 10kV母线 → 5311隔离开关 → 531断路器 → 主变102000147\n'
          '- 10kV母线 → 5221隔离开关 → 522断路器 → 主变102000148')

with sync_playwright() as p:
    browser = p.chromium.launch(headless=True)
    page = browser.new_page(viewport={'width': 1680, 'height': 950})
    errors = []
    page.on('console', lambda m: errors.append(m.text) if m.type == 'error' else None)
    page.goto(BASE)
    page.wait_for_load_state('networkidle')
    page.wait_for_timeout(1500)

    # 1. 选择第一个 Agent（重启后为归档态，发送时自动重建）
    select = page.locator('#workspace-agent-select')
    select.wait_for(state='visible', timeout=10000)
    page.wait_for_timeout(600)
    options = select.locator('option')
    assert options.count() > 1, '智能体列表为空'
    select.select_option(value=options.nth(1).get_attribute('value'))
    page.wait_for_timeout(400)
    print('[1] 选择 Agent: OK ->', options.nth(1).inner_text())

    # 2. 发送连接链消息
    textarea = page.locator('textarea[aria-label="输入对话内容"]')
    textarea.fill(PROMPT)
    textarea.press('Enter')
    print('[2] 连接链消息已发送')

    # 3. 等待终态
    page.wait_for_selector('.result-panel', timeout=240000)
    page.wait_for_timeout(1500)
    print('[3] Run 终态: OK ->', page.locator('.result-panel strong').inner_text())

    # 4. 连接链应被自动转换为 Mermaid 流程图（同名节点 10kV母线 合并）
    boxes = page.locator('.md-body .md-mermaid svg')
    assert boxes.count() >= 1, f'结构化链未生成 Mermaid 图（svg={boxes.count()}）'
    node_count = boxes.first.locator('.node').count()
    assert node_count >= 7, f'流程图节点数异常: {node_count}（期望 ≥7，同名节点应合并）'
    print(f'[4] 结构化链自动图示: OK -> {boxes.count()} 张图 / {node_count} 节点（两条链共享 10kV母线）')

    # 5. 点击图表打开灯箱
    page.locator('.md-body .md-mermaid').first.click()
    page.wait_for_selector('.md-lightbox', timeout=5000)
    assert page.locator('.md-lightbox-canvas svg').count() == 1, '灯箱内 SVG 缺失'
    # 防“空画布”回归：SVG 必须有实际渲染尺寸（width=100% 在绝对定位画布中会塌缩为 0）
    box = page.locator('.md-lightbox-canvas svg').first.bounding_box()
    assert box and box['width'] > 300 and box['height'] > 120, f'灯箱 SVG 尺寸异常: {box}'
    canvas_box = page.locator('.md-lightbox-canvas').bounding_box()
    assert canvas_box['width'] >= box['width'] and canvas_box['height'] >= box['height'] * 0.8, \
        f'画布未包住 SVG: canvas={canvas_box} svg={box}'
    assert page.locator('.md-lightbox-scale').inner_text() == '100%', '初始缩放应为 100%'
    page.screenshot(path='task/e2e-chat-mermaid-lightbox.png', full_page=False)
    print(f'[5] 灯箱打开: OK（100%，SVG {box["width"]:.0f}x{box["height"]:.0f}）')

    # 6. 滚轮缩放：放大两档
    stage = page.locator('.md-lightbox-stage')
    stage.hover()
    page.mouse.wheel(0, -120)
    page.wait_for_timeout(150)
    page.mouse.wheel(0, -120)
    page.wait_for_timeout(250)
    scale_text = page.locator('.md-lightbox-scale').inner_text()
    assert scale_text != '100%', f'滚轮缩放未生效: {scale_text}'
    transform = page.locator('.md-lightbox-canvas').get_attribute('style')
    assert 'scale(' in transform, f'canvas transform 缺失 scale: {transform}'
    # 放大后 SVG 视觉尺寸应同步增大（防“只变百分比不变图形”回归）
    zoomed_box = page.locator('.md-lightbox-canvas svg').first.bounding_box()
    assert zoomed_box['height'] > box['height'] * 1.2, f'放大后 SVG 未变大: {box} -> {zoomed_box}'
    print(f'[6] 滚轮缩放: OK -> {scale_text}（SVG 高 {box["height"]:.0f}->{zoomed_box["height"]:.0f}）')
    page.screenshot(path='task/e2e-chat-mermaid-lightbox-zoomed.png', full_page=False)

    # 7. Esc 关闭灯箱
    page.keyboard.press('Escape')
    page.wait_for_timeout(300)
    assert page.locator('.md-lightbox').count() == 0, 'Esc 未关闭灯箱'
    print('[7] Esc 关闭灯箱: OK')

    real_errors = [e for e in errors if 'favicon' not in e.lower()]
    print('[8] 控制台错误:', real_errors[:3] if real_errors else '无')
    assert not real_errors, f'存在控制台错误: {real_errors[:3]}'
    browser.close()
    print('CHAT MERMAID ZOOM ALL PASS')
