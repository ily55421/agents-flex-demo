# -*- coding: utf-8 -*-
# 验证知识库面板“向量模型配置（手动）”区域：展开、选预设、应用
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
    page.on('response', lambda r: print('[http]', r.status, r.request.method, r.url)
            if '/api/knowledge' in r.url and r.request.method != 'GET' else None)

    page.goto(BASE + '/?view=knowledge')
    page.wait_for_load_state('networkidle')
    page.wait_for_timeout(1200)
    assert page.locator('.knowledge-panel').count() == 1

    # 1. 展开手动配置区
    summary = page.locator('.knowledge-add summary', has_text='向量模型配置（手动）')
    assert summary.count() == 1, '手动配置区缺失'
    summary.click()
    page.wait_for_timeout(300)

    # 2. 选预设 → 自动填充地址与模型
    page.locator('#kb-emb-preset').select_option(label='BGE-M3（Ollama）')
    page.wait_for_timeout(300)
    endpoint = page.locator('#kb-emb-endpoint').input_value()
    model = page.locator('#kb-emb-model').input_value()
    assert '11434' in endpoint and model == 'bge-m3', f'预设填充异常: {endpoint} / {model}'
    print('[1] 预设自动填充: OK ->', endpoint)

    # 3. 改为自定义地址并应用
    page.locator('#kb-emb-endpoint').fill('http://192.168.9.99:19999/v1')
    page.get_by_role('button', name='应用向量模型').click()
    page.wait_for_timeout(2500)

    # 4. 状态显示已配置（连接失败也会显示，并给出原因）
    status_text = page.locator('.knowledge-status').inner_text().replace('\n', ' ')
    assert '已配置' in status_text and 'bge-m3' in status_text, f'未显示已配置: {status_text}'
    print('[2] 应用后状态: OK ->', status_text)
    err_line = page.locator('.knowledge-error-line')
    if err_line.count():
        print('    最近错误:', err_line.inner_text()[:110])

    page.screenshot(path='task/e2e-kb-manual-embedding.png', full_page=False)
    real_errors = [e for e in errors if 'favicon' not in e.lower()]
    print('[3] 控制台错误:', real_errors[:3] if real_errors else '无')
    browser.close()
    print('KB MANUAL EMBEDDING ALL PASS')
