# -*- coding: utf-8 -*-
# 模型配置弹窗 + 左栏精简 + 纯对话页 的端到端验证。
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

    # 1. 顶部 Tab 存在且默认在工作台
    tabs = page.locator('.page-tabs button')
    assert tabs.count() == 2, f'Tab 数量异常: {tabs.count()}'
    assert '工作台' in tabs.nth(0).inner_text() and '纯对话' in tabs.nth(1).inner_text()
    assert 'active' in (tabs.nth(0).get_attribute('class') or ''), '默认 Tab 不是工作台'
    print('[1] 顶部 Tab: OK（Agent 工作台 / 纯对话）')

    # 2. 左栏已精简：模型连接字段不再常驻，改为摘要卡片 + 配置入口
    assert page.locator('#model-provider').count() == 0, '左栏仍残留模型连接字段'
    assert page.locator('#embedding-endpoint').count() == 0, '左栏仍残留向量模型字段'
    card = page.locator('.model-config-card')
    assert card.count() == 1, '模型配置摘要卡片缺失'
    summary = card.inner_text().replace('\n', ' | ')
    print('[2] 左栏精简: OK ->', summary[:120])

    # 3. 打开模型配置弹窗
    page.get_by_role('button', name='配置模型').click()
    page.wait_for_timeout(400)
    dialog = page.locator('.model-dialog')
    assert dialog.count() == 1, '弹窗未打开'
    assert page.locator('#dl-provider').is_visible(), '聊天模型字段未渲染'
    print('[3] 弹窗打开: OK（聊天模型 Tab 默认激活）')

    # 4. 预设快捷填充（自定义配置入口）
    page.select_option('#chat-preset', label='本地 Ollama（OpenAI 兼容）')
    page.wait_for_timeout(200)
    assert page.input_value('#dl-provider') == 'ollama', '预设未填充服务商'
    assert page.input_value('#dl-endpoint') == 'http://localhost:11434/v1', '预设未填充地址'
    # 自定义编辑：在预设基础上改成任意网关
    page.fill('#dl-model', 'qwen2.5:14b-instruct')
    assert page.input_value('#dl-model') == 'qwen2.5:14b-instruct', '自定义字段编辑失败'
    print('[4] 预设填充 + 自定义编辑: OK -> ollama / qwen2.5:14b-instruct')

    # 5. 向量模型 Tab + bge-m3 预设
    page.get_by_role('tab', name='向量模型').click()
    page.wait_for_timeout(300)
    page.select_option('#emb-preset', label='BGE-M3（本地网关 18888）')
    page.wait_for_timeout(200)
    assert '18888' in page.input_value('#dl-emb-endpoint'), '向量预设未应用地址'
    assert page.input_value('#dl-emb-model') == 'bge-m3', '向量预设未应用模型'
    print('[5] 向量模型 Tab: OK -> bge-m3 @ 127.0.0.1:18888')

    # 6. 另存为个人档案
    page.get_by_role('tab', name='向量模型').click()
    page.wait_for_timeout(200)
    page.fill('input[aria-label="档案名称"]', '我的本地 Qwen + BGE')
    page.get_by_role('button', name='保存档案').click()
    page.wait_for_timeout(300)
    stored = page.evaluate("() => JSON.parse(localStorage.getItem('agents-flex-demo.model-profiles.v1') || '[]')")
    assert any((item.get('data') or {}).get('profileName') == '我的本地 Qwen + BGE'
               and item.get('profileType') == 'embedding'
               for item in stored if isinstance(item, dict)), \
        f'档案未写入 localStorage: {stored}'
    print('[6] 另存档案: OK（localStorage 已持久化）')

    # 5. 向量 Tab 的“应用到知识库（立即生效）”入口存在且可用
    kb_btn = page.get_by_role('button', name='应用到知识库')
    assert kb_btn.count() == 1 and kb_btn.is_enabled(), '应用到知识库入口缺失或不可用'
    print('[5a] 应用到知识库入口: OK')

    # 5b. 应用配置 -> 摘要卡片更新
    page.get_by_role('button', name='应用配置').click()
    page.wait_for_timeout(500)
    assert page.locator('.model-dialog').count() == 0, '应用后弹窗未关闭'
    new_summary = page.locator('.model-config-card').inner_text()
    assert 'qwen2.5:14b-instruct' in new_summary, f'摘要未反映应用结果: {new_summary}'
    assert 'bge-m3' in new_summary, f'摘要未反映向量模型: {new_summary}'
    print('[7] 应用配置: OK ->', new_summary.replace('\n', ' | ')[:110])

    # 8. 切到纯对话页：智能体选择器 + 归档提示
    tabs.nth(1).click()
    page.wait_for_timeout(1200)
    assert page.locator('.chat-home').count() == 1, '纯对话页未渲染'
    selector = page.locator('#chat-agent-select')
    assert selector.count() == 1, '智能体选择器缺失'
    options = page.locator('#chat-agent-select option').all_inner_texts()
    print('[8] 纯对话页: OK，智能体选项 ->', [o for o in options if o.strip()][:4])
    empty = page.locator('.chat-home-empty')
    if empty.count():
        text = empty.inner_text().replace('\n', ' ')
        assert '智能体' in text, f'空状态文案异常: {text}'
        print('    空状态提示:', text[:90])
    assert 'view=chat' in page.url, f'URL 未同步 view 参数: {page.url}'
    print('[9] URL 同步: OK ->', page.url)

    # 10. 刷新后停留在纯对话页
    page.reload()
    page.wait_for_load_state('networkidle')
    page.wait_for_timeout(1200)
    assert page.locator('.chat-home').count() == 1, '刷新后未恢复纯对话页'
    print('[10] 刷新恢复纯对话页: OK')

    # 11. 回到工作台，模型配置持久化保留（localStorage 恢复）
    page.locator('.page-tabs button').nth(0).click()
    page.wait_for_timeout(600)
    page.get_by_role('button', name='配置模型').click()
    page.wait_for_timeout(400)
    page.get_by_role('tab', name='聊天模型').click()
    page.wait_for_timeout(200)
    assert page.input_value('#dl-model') == 'qwen2.5:14b-instruct', '配置未在刷新后保留'
    print('[11] 配置持久化: OK（刷新后仍为自定义模型）')

    page.screenshot(path='task/e2e-chat-page.png', full_page=True)
    real_errors = [e for e in errors if 'favicon' not in e.lower()]
    print('[12] 控制台错误:', real_errors[:3] if real_errors else '无')
    browser.close()
    print('E2E ALL PASS')
