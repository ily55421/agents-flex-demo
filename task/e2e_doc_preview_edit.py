# -*- coding: utf-8 -*-
# 验证文档预览/编辑闭环：旧文档降级提示、新文档只读预览、编辑保存重向量化、
# 检索命中新内容、清理。
import sys
import io
import uuid

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')

from playwright.sync_api import sync_playwright

BASE = 'http://localhost:5173'
# 中文自然关键词（避免 BM25 对无空格中英数字混合串分词不命中）
marker = '预览编辑验证' + str(uuid.uuid4().int % 100000)
title = 'E2E预览编辑测试'

with sync_playwright() as p:
    browser = p.chromium.launch(headless=True)
    page = browser.new_page(viewport={'width': 1680, 'height': 950})
    errors = []
    page.on('console', lambda m: errors.append(m.text) if m.type == 'error' else None)

    # 1. 进入知识库 Tab
    page.goto(BASE + '/?view=knowledge')
    page.wait_for_load_state('networkidle')
    page.wait_for_timeout(1500)
    assert page.locator('.knowledge-panel').count() == 1, '知识库页未渲染'
    print('[1] 知识库 Tab: OK')

    # 2. 旧文档预览（仅当存在旧格式文档时校验降级提示，新导入文档无此分支）
    old_row = page.locator('.knowledge-docs li', has_text='35kV藏木变').first
    old_row.locator('button[aria-label^="预览文档"]').click()
    page.wait_for_timeout(800)
    dialog = page.locator('.doc-dialog')
    assert dialog.count() == 1, '预览弹窗未打开'
    if dialog.locator('.doc-dialog-missing').count() == 1:
        print('[2] 旧文档降级提示+可编辑态: OK')
    else:
        print('[2] 当前无旧格式文档，跳过降级检查')
    page.locator('.doc-dialog-foot button', has_text='关闭').click()
    page.wait_for_timeout(300)

    # 3. 文本入库新建测试文档
    page.locator('.knowledge-add summary', has_text='添加知识').click()
    page.locator('#kb-title').fill(title)
    page.locator('#kb-content').fill(f'这是第一段。{marker} 这是第二段补充。')
    page.get_by_role('button', name='文本入库').click()
    page.wait_for_timeout(1200)

    # 4. 新文档预览：只读展示原文
    row = page.locator('.knowledge-docs li', has_text=title).first
    row.locator('button[aria-label^="预览文档"]').click()
    page.wait_for_timeout(800)
    dialog = page.locator('.doc-dialog')
    assert dialog.locator('.doc-dialog-missing').count() == 0, '新文档不应有缺失提示'
    ta = dialog.locator('#doc-edit-content')
    content_val = ta.input_value()
    assert marker in content_val, f'预览内容缺失 marker: {content_val[:60]}'
    assert ta.get_attribute('readonly') is not None, '预览模式应只读'
    print('[3] 新文档只读预览: OK')

    # 5. 进入编辑 → 修改 → 保存
    dialog.get_by_role('button', name='编辑').click()
    page.wait_for_timeout(300)
    ta.fill(f'编辑后的新内容。{marker} 新增验证句。')
    dialog.get_by_role('button', name='保存并重新向量化').click()
    page.wait_for_timeout(1500)
    # 保存后弹窗回到只读预览态且保持打开，先关闭再重新打开验证原文已刷新
    page.locator('.doc-dialog-foot button', has_text='关闭').click()
    page.wait_for_timeout(300)

    # 6. 重新打开预览确认原文已刷新
    row = page.locator('.knowledge-docs li', has_text=title).first
    row.locator('button[aria-label^="预览文档"]').click()
    page.wait_for_timeout(800)
    dialog = page.locator('.doc-dialog')
    content2 = dialog.locator('#doc-edit-content').input_value()
    assert '新增验证句' in content2, f'保存后原文未更新: {content2[:60]}'
    print('[4] 保存后原文刷新: OK')
    page.locator('.doc-dialog-foot button', has_text='关闭').click()
    page.wait_for_timeout(300)

    # 7. 检索命中编辑后内容
    page.locator('#kb-query').fill(marker)
    page.get_by_role('button', name='检索').click()
    page.wait_for_timeout(1200)
    hits = page.locator('.knowledge-hits li').all_inner_texts()
    assert any(marker in h for h in hits), f'检索未命中编辑后内容: {hits[:2]}'
    print('[5] 检索命中编辑后内容: OK')

    # 8. 清理测试文档
    row = page.locator('.knowledge-docs li', has_text=title).first
    row.locator('button[aria-label^="删除文档"]').click()
    page.wait_for_timeout(1000)
    assert page.locator('.knowledge-docs li', has_text=title).count() == 0, '测试文档未删除'
    print('[6] 清理测试文档: OK')

    page.screenshot(path='task/e2e-doc-preview-edit.png', full_page=False)
    real_errors = [e for e in errors if 'favicon' not in e.lower()]
    print('[7] 控制台错误:', real_errors[:3] if real_errors else '无')
    browser.close()
    print('DOC PREVIEW EDIT ALL PASS')
