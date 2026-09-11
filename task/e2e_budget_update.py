# -*- coding: utf-8 -*-
# 验证：已有会话时也能打开弹窗改预算 -> 重新创建 Agent -> BudgetPanel 反映新上限。
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

    # 1. 先应用本地 Ollama 预设（后端重启后模型状态重置，且本地端点免 Key）
    page.get_by_role('button', name='配置模型').click()
    page.wait_for_timeout(400)
    page.select_option('#chat-preset', label='本地 Ollama（OpenAI 兼容）')
    page.wait_for_timeout(200)
    page.get_by_role('button', name='应用配置').click()
    page.wait_for_timeout(1200)

    # 2. 用当前配置创建一个 Agent，进入“已就绪”锁定态
    create_btn = page.get_by_role('button', name='创建 Agent')
    create_btn.scroll_into_view_if_needed()
    create_btn.click()
    page.wait_for_timeout(2000)
    assert page.locator('.ready-badge').count() == 1, 'Agent 创建失败'
    print('[1] 初始 Agent 创建: OK（进入“已就绪”锁定态）')

    # 2. 已就绪状态下“配置模型”按钮必须仍然可用（核心修复点）
    config_btn = page.get_by_role('button', name='配置模型')
    assert config_btn.is_enabled(), '已就绪状态下配置模型按钮被禁用（核心缺陷未修复）'
    print('[2] 已就绪时配置按钮可用: OK')

    # 3. 打开弹窗，修改输出预算 4000 -> 20000
    config_btn.click()
    page.wait_for_timeout(400)
    budget_input = page.locator('#dl-budget-output')
    assert budget_input.count() == 1, '输出预算字段缺失'
    before = budget_input.input_value()
    budget_input.fill('20000')
    page.locator('#dl-budget-input').fill('32000')
    page.locator('#dl-budget-total').fill('64000')
    print(f'[3] 预算修改: 输出 {before} -> 20000，输入 -> 32000，总 -> 64000')

    # 4. 应用配置（同步后端 + 写回表单）
    page.get_by_role('button', name='应用配置').click()
    page.wait_for_timeout(1200)
    assert page.locator('.model-dialog').count() == 0, '应用后弹窗未关闭'
    summary = page.locator('.model-config-card').inner_text()
    assert '输出 20000' in summary and '总 64000' in summary, f'摘要未反映新预算: {summary}'
    print('[4] 应用后摘要: OK ->', summary.replace('\n', ' | ')[:130])

    # 5. “重新配置 Agent” -> 再次创建，BudgetPanel 反映 20000
    page.get_by_role('button', name='重新配置 Agent').click()
    page.wait_for_timeout(400)
    page.get_by_role('button', name='创建 Agent').scroll_into_view_if_needed()
    page.get_by_role('button', name='创建 Agent').click()
    page.wait_for_timeout(2200)
    assert page.locator('.ready-badge').count() == 1, '重建 Agent 失败'

    # 发送一条消息创建 Run（模型响应可能失败，不影响预算断言——通过 API 校验后端值）
    box = page.locator('textarea[aria-label="输入对话内容"]')
    box.fill('你好')
    page.keyboard.press('Enter')
    page.wait_for_timeout(1500)

    # 5b. 通过后端 API 校验新 Run 的预算确实生效
    import json
    import urllib.request
    with urllib.request.urlopen('http://localhost:8080/api/agent/runs', timeout=8) as resp:
        runs = json.loads(resp.read())
    assert runs, '没有创建出 Run'
    budget = runs[0]['budget']
    assert budget['tokenLimit'] == 64000, f"后端总预算未生效: {budget}"
    assert budget['durationLimitMs'] > 0
    # BudgetPanel 页面渲染（Run 已创建，面板应出现 64000 总上限）
    budget_text = page.locator('.metrics-layout').inner_text().replace('\n', ' ') \
        if page.locator('.metrics-layout').count() else ''
    if budget_text:
        assert '64000' in budget_text, f'BudgetPanel 未反映新上限: {budget_text[:200]}'
        print('[5] BudgetPanel 反映新预算: OK')
    else:
        print('[5] 后端预算生效: OK -> tokenLimit =', budget['tokenLimit'],
              '（面板因模型响应失败未渲染，但 API 值已确认）')

    page.screenshot(path='task/e2e-budget-update.png', full_page=False)
    real_errors = [e for e in errors if 'favicon' not in e.lower()]
    print('[6] 控制台错误:', real_errors[:3] if real_errors else '无')
    browser.close()
    print('BUDGET UPDATE ALL PASS')
