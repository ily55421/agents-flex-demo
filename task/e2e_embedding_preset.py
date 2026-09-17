# -*- coding: utf-8 -*-
# 验证：知识库面板“我的预设”分组展示、选中回填、删除按钮出现、重启后表单回填保存配置
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

    # 1. 进入知识库 Tab，展开向量模型配置
    page.locator('.page-tabs button', has_text='知识库').click()
    page.wait_for_timeout(800)
    summary = page.locator('details summary', has_text='向量模型配置')
    summary.click()
    page.wait_for_timeout(400)

    # 2. 预设下拉应含“内置预设”与“我的预设”两个分组
    select = page.locator('#kb-emb-preset')
    groups = select.locator('optgroup').all_inner_texts()
    assert any('我的预设' in g for g in groups), f'缺少我的预设分组: {groups}'
    options = select.locator('option').all_inner_texts()
    assert any('内网 Xinference 214' in o for o in options), f'我的预设缺自建项: {options}'
    print('[1] 预设下拉含内置+我的预设分组: OK ->', [o for o in options if o.strip()])

    # 3. 表单应已用后端保存的配置回填（重启后免重填）
    endpoint_value = page.locator('#kb-emb-endpoint').input_value()
    model_value = page.locator('#kb-emb-model').input_value()
    assert '192.168.31.214:8081' in endpoint_value, f'表单未回填保存的服务地址: {endpoint_value!r}'
    assert model_value == 'bge-m3', f'表单未回填模型名: {model_value!r}'
    print('[2] 重启后表单自动回填已保存配置: OK ->', endpoint_value, model_value)

    # 4. 选中“我的预设”应回填（含删除按钮出现）
    select.select_option(label='BGE-M3（内网 Xinference 214）')
    page.wait_for_timeout(300)
    assert '192.168.31.214:8081' in page.locator('#kb-emb-endpoint').input_value(), '选中预设未回填地址'
    delete_btn = page.locator('button[title="删除选中的我的预设"]')
    assert delete_btn.count() == 1, '选中我的预设后删除按钮未出现'
    assert page.locator('button', has_text='存为预设').count() == 1, '存为预设按钮未出现'
    print('[3] 选中我的预设回填+删除/保存按钮: OK')

    page.screenshot(path='task/e2e_embedding_preset.png', full_page=False)
    print('截图: task/e2e_embedding_preset.png')

    if errors:
        print('控制台错误:', errors[:5])
    browser.close()
    print('全部通过')
