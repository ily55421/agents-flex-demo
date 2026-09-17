# -*- coding: utf-8 -*-
# 验证：配置自定义向量模型 → 应用到知识库 → 出现在“我的档案”下拉 → 知识库显示已配置
import sys
import io
import uuid

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')

from playwright.sync_api import sync_playwright

BASE = 'http://localhost:5173'
addr = f'http://10.9.9.{uuid.uuid4().int % 250 + 1}:19000/v1'

with sync_playwright() as p:
    browser = p.chromium.launch(headless=True)
    page = browser.new_page(viewport={'width': 1680, 'height': 950})
    errors = []
    page.on('console', lambda m: errors.append(m.text) if m.type == 'error' else None)

    page.goto(BASE)
    page.wait_for_load_state('networkidle')
    page.wait_for_timeout(1200)

    # 1. 打开模型配置弹窗
    page.get_by_role('button', name='配置模型').click()
    page.wait_for_timeout(600)
    dialog = page.locator('.model-dialog')
    assert dialog.count() == 1, '弹窗未打开'
    print('[1] 模型配置弹窗: OK')

    # 2. 切到向量模型 Tab
    dialog.get_by_role('tab', name='向量模型').click()
    page.wait_for_timeout(300)

    # 3. 填写自定义向量配置
    dialog.locator('#dl-emb-endpoint').fill(addr)
    dialog.locator('#dl-emb-model').fill('bge-m3')
    page.wait_for_timeout(300)

    # 4. 应用到知识库
    dialog.get_by_role('button', name='应用到知识库').click()
    page.wait_for_timeout(1200)

    # 5. “我的档案”下拉出现且包含自动命名的档案
    user_profile_row = dialog.locator('.preset-row', has_text='我的档案')
    assert user_profile_row.count() == 1, '我的档案下拉未出现'
    profile_options = user_profile_row.locator('select option').all_inner_texts()
    assert any('自定义 bge-m3' in o for o in profile_options), f'档案下拉缺自定义项: {profile_options}'
    print('[2] 自定义向量配置已进入“我的档案”下拉: OK ->', [o for o in profile_options if '自定义' in o])

    # 6. 关闭弹窗，切到知识库 Tab 验证状态
    dialog.get_by_role('button', name='关闭').click()
    page.wait_for_timeout(300)
    page.locator('.page-tabs button', has_text='知识库').click()
    page.wait_for_timeout(1200)
    status_text = page.locator('.knowledge-status').inner_text().replace('\n', ' ')
    assert '已配置' in status_text and 'bge-m3' in status_text, f'知识库状态未显示已配置: {status_text}'
    print('[3] 知识库显示“已配置 · bge-m3”: OK ->', status_text)
    err_line = page.locator('.knowledge-error-line')
    if err_line.count():
        print('    （连接提示）:', err_line.inner_text()[:100])

    page.screenshot(path='task/e2e-custom-embedding.png', full_page=False)
    real_errors = [e for e in errors if 'favicon' not in e.lower()]
    print('[4] 控制台错误:', real_errors[:3] if real_errors else '无')
    browser.close()
    print('CUSTOM EMBEDDING CONFIG ALL PASS')
