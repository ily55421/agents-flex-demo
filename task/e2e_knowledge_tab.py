# -*- coding: utf-8 -*-
# 验证三 Tab 架构 + 知识库独立页 + 批量导入 UI + 对话选择知识范围。
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

    # 1. 三个 Tab
    tabs = page.locator('.page-tabs button')
    assert tabs.count() == 3, f'Tab 数量异常: {tabs.count()}'
    labels = [tabs.nth(i).inner_text() for i in range(3)]
    print('[1] 三 Tab:', ' / '.join(labels))

    # 2. 知识库 Tab：面板全宽展示 + 文档清单包含导入的电力拓扑资料
    tabs.nth(1).click()
    page.wait_for_timeout(1500)
    assert page.locator('.knowledge-panel').count() == 1, '知识库页未渲染'
    assert 'view=knowledge' in page.url, 'URL 未同步'
    panel_text = page.locator('.knowledge-panel').inner_text()
    assert '电力拓扑知识问答' in panel_text, '导入的问答库未出现在文档清单'
    assert '35kV藏木变' in panel_text, '导入的站点文档未出现'
    status_line = page.locator('.knowledge-status').inner_text().replace('\n', ' ')
    print('[2] 知识库独立页: OK ->', status_line, '| 清单含电力拓扑资料 ✓')

    # 3. 检索范围下拉含“全部文档”与导入文档
    ns_select = page.locator('.knowledge-namespace')
    options = ns_select.locator('option').all_inner_texts()
    assert '全部文档' in options and any('藏木' in o for o in options), f'范围下拉异常: {options[:5]}'
    print('[3] 检索范围下拉: OK（全部文档 + 各站点文档）')

    # 4. 批量导入按钮存在（在“添加知识”折叠组内）
    page.locator('.knowledge-add summary', has_text='添加知识').click()
    page.wait_for_timeout(300)
    assert page.get_by_role('button', name='批量导入').count() == 1, '批量导入按钮缺失'
    print('[4] 批量导入按钮: OK')

    # 5. 工作台“知识库范围”下拉列出导入文档
    tabs.nth(0).click()
    page.wait_for_timeout(800)
    kb_select = page.locator('#knowledge-namespace')
    assert kb_select.count() == 1, '工作台知识库范围选择缺失'
    kb_options = kb_select.locator('option').all_inner_texts()
    assert any('藏木' in o for o in kb_options), f'范围下拉未含导入文档: {kb_options[:4]}'
    print('[5] 工作台知识库范围: OK ->', kb_options[:3], '...')

    # 6. 刷新后仍停留在知识库页
    page.goto(BASE + '/?view=knowledge')
    page.wait_for_load_state('networkidle')
    page.wait_for_timeout(1200)
    assert page.locator('.knowledge-panel').count() == 1, '刷新后未恢复知识库页'
    print('[6] 刷新恢复知识库页: OK')

    page.screenshot(path='task/e2e-knowledge-tab.png', full_page=False)
    real_errors = [e for e in errors if 'favicon' not in e.lower()]
    print('[7] 控制台错误:', real_errors[:3] if real_errors else '无')
    browser.close()
    print('KNOWLEDGE TAB ALL PASS')
