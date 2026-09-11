# -*- coding: utf-8 -*-
# 复现并验证修复：弹窗应用模型配置 -> 模型状态生效 -> 创建 Agent -> 对话框可输入。
import sys
import io

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')

from playwright.sync_api import sync_playwright

BASE = 'http://localhost:5173'

with sync_playwright() as p:
    browser = p.chromium.launch(headless=True)
    page = browser.new_page(viewport={'width': 1680, 'height': 950})
    errors = []
    page.on('console', lambda m: errors.append(m.text) if m.type == 'error' else None)
    page.goto(BASE)
    page.wait_for_load_state('networkidle')
    page.wait_for_timeout(1500)

    # 1. 初始状态：输入框被禁用并给出可操作提示
    box = page.locator('textarea[aria-label="输入对话内容"]')
    assert box.is_disabled(), '初始输入框应为禁用'
    print('[1] 初始禁用提示:', box.get_attribute('placeholder'))

    # 2. 弹窗选择本地 Ollama 预设（不填 API Key）并应用
    page.get_by_role('button', name='配置模型').click()
    page.wait_for_timeout(400)
    page.select_option('#chat-preset', label='本地 Ollama（OpenAI 兼容）')
    page.wait_for_timeout(200)
    assert page.input_value('#dl-api-key') == '', '预设不应写入 API Key'
    page.get_by_role('button', name='应用配置').click()
    page.wait_for_timeout(1200)

    # 3. 应用后模型状态立即生效（后端 configured=true 反映到头部芯片）
    chip = page.locator('.model-chip')
    assert chip.count() == 1
    assert chip.get_attribute('data-configured') == 'true', f'模型状态未生效: {chip.inner_text()}'
    print('[3] 模型状态生效: OK ->', chip.inner_text().replace('\n', ' '))

    # 4. 输入框提示应从“应用模型配置”推进到“创建 Agent”
    page.wait_for_timeout(400)
    placeholder = box.get_attribute('placeholder') or ''
    assert '创建 Agent' in placeholder, f'提示未推进: {placeholder}'
    print('[4] 引导提示推进: OK ->', placeholder)

    # 5. 点击创建 Agent（滚动到按钮）
    create_btn = page.get_by_role('button', name='创建 Agent')
    create_btn.scroll_into_view_if_needed()
    create_btn.click()
    page.wait_for_timeout(2500)

    # 6. Agent 就绪后输入框可输入
    ready = page.locator('.ready-badge')
    assert ready.count() == 1, f'Agent 未创建成功；错误提示: {page.locator(".error-toast").inner_text() if page.locator(".error-toast").count() else "(无)"}'
    assert not box.is_disabled(), 'Agent 就绪后输入框仍被禁用'
    print('[6] 对话框可输入: OK -> placeholder =', box.get_attribute('placeholder'))

    # 7. 实际输入并确认草稿被接收
    box.fill('测试一下输入是否可用')
    assert box.input_value() == '测试一下输入是否可用', '输入内容未被接收'
    print('[7] 文本输入: OK')

    page.screenshot(path='task/e2e-model-apply-fix.png', full_page=False)
    real_errors = [e for e in errors if 'favicon' not in e.lower()]
    print('[8] 控制台错误:', real_errors[:3] if real_errors else '无')
    browser.close()
    print('MODEL APPLY FIX ALL PASS')
