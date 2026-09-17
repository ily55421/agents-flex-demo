# -*- coding: utf-8 -*-
# 验证：1) 归档 Agent 重建后预算为新默认（工具调用 100）；2) 重建复用同一定义行
# 不再堆积归档副本；3) 页面刷新后自动恢复上次选中的 Agent。
import sys
import io

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')

from playwright.sync_api import sync_playwright
import urllib.request
import json as _json

BASE = 'http://localhost:5173'


def agent_count():
    with urllib.request.urlopen('http://localhost:8080/api/agent/agents') as response:
        return len(_json.loads(response.read()))


with sync_playwright() as p:
    browser = p.chromium.launch(headless=True)
    page = browser.new_page(viewport={'width': 1680, 'height': 950})
    errors = []
    page.on('console', lambda m: errors.append(m.text) if m.type == 'error' else None)
    page.goto(BASE)
    page.wait_for_load_state('networkidle')
    page.wait_for_timeout(1500)

    before = agent_count()
    print(f'[1] 后端 Agent 定义数（迁移后）: {before}')

    # 2. 选择归档 Agent 并发送消息触发自动重建。
    #    发送后可能立即进入 busy（重建 + 建运行在途，输入框禁用），以「禁用即已受理」判定。
    select = page.locator('#workspace-agent-select')
    select.wait_for(state='visible', timeout=10000)
    page.wait_for_timeout(800)
    options = select.locator('option')
    select.select_option(value=options.nth(1).get_attribute('value'))
    page.wait_for_timeout(600)
    textarea = page.locator('textarea[aria-label="输入对话内容"]')
    textarea.wait_for(state='visible', timeout=8000)
    sent = False
    for attempt in range(2):
        if not textarea.is_enabled():
            # 上一轮 Enter 已被受理（重建/对话在途），busy 禁用了输入框
            sent = True
            break
        assert textarea.is_enabled(), '输入框不可用'
        textarea.click()
        textarea.fill('你好，请只回复：OK')
        textarea.press('Enter')
        try:
            page.wait_for_selector('.chat-message', timeout=6000)
            sent = True
            break
        except Exception:
            if not textarea.is_enabled():
                sent = True
                break
            print(f'    第 {attempt + 1} 次发送未生效，重试…')
    assert sent, '两次尝试均未发出消息'
    page.wait_for_selector('.result-panel', timeout=180000)
    page.wait_for_timeout(800)
    print('[2] 归档 Agent 自动重建并完成对话: OK')

    # 3. 预算应为工具调用上限 100
    tools_stat = page.locator('.chat-header-stats span', has_text='Tools').inner_text().replace('\n', ' ')
    assert '/100' in tools_stat.replace(' ', ''), f'工具预算不是 100: {tools_stat}'
    print('[3] 工具预算: OK ->', tools_stat)

    # 4. 重建复用定义：后端 Agent 数量不增加
    after = agent_count()
    assert after == before, f'重建后定义数量增加: {before} -> {after}（应复用同一行）'
    with urllib.request.urlopen('http://localhost:8080/api/agent/agents') as response:
        names = [(a['name'], a.get('version'), a.get('maxToolCalls')) for a in _json.loads(response.read())]
    print(f'[4] 定义复用: OK（重建前后均为 {after}）->', names)

    # 5. 刷新后自动恢复选中 Agent
    page.reload()
    page.wait_for_load_state('networkidle')
    page.wait_for_timeout(2000)
    brief = page.locator('.agent-picker-brief')
    assert brief.count() == 1, '刷新后未恢复选中的 Agent'
    brief_text = brief.inner_text()
    assert '电网拓扑分析专家' in brief_text or '市场研究助手' in brief_text, \
        f'恢复的 Agent 异常: {brief_text[:60]}'
    print('[5] 刷新恢复选中 Agent: OK ->', brief_text.split('\n')[0])

    real_errors = [e for e in errors if 'favicon' not in e.lower()]
    print('[6] 控制台错误:', real_errors[:3] if real_errors else '无')
    assert not real_errors
    browser.close()
    print('AGENT REUSE ALL PASS')
