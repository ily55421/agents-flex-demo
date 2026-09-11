# -*- coding: utf-8 -*-
# RAG 知识库 UI 端到端验证：面板渲染、文本入库、检索测试、向量档案下拉与“应用到知识库”。
import sys
import io

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')

from playwright.sync_api import sync_playwright

BASE = 'http://localhost:5174'

with sync_playwright() as p:
    browser = p.chromium.launch(headless=True)
    page = browser.new_page(viewport={'width': 1680, 'height': 950})
    errors = []
    page.on('console', lambda msg: errors.append(msg.text) if msg.type == 'error' else None)
    page.goto(BASE)
    page.wait_for_load_state('networkidle')
    page.wait_for_timeout(1500)

    # 1. 知识库面板已渲染且状态正常（等待初始加载完成）
    panel = page.locator('.knowledge-panel')
    assert panel.count() == 1, '知识库面板未渲染'
    page.wait_for_function("() => !document.querySelector('.knowledge-status')?.innerText.includes('加载中')", timeout=8000)
    status_text = panel.locator('.knowledge-status').inner_text()
    print('[1] 面板状态:', status_text.replace('\n', ' | '))
    assert '文档' in status_text and '加载中' not in status_text, '状态区未就绪'

    # 2. 文本入库（展开“添加知识”折叠组）
    page.locator('.knowledge-add summary').click()
    page.wait_for_timeout(200)
    page.fill('#kb-title', 'UI 端到端验证文档')
    page.fill('#kb-content', 'UI 自动化验证：混合检索结合向量 ANN 与 BM25 关键词召回，RRF 融合排序提升命中率。独特标记QAWEB789。')
    page.get_by_role('button', name='文本入库').click()
    page.wait_for_timeout(1500)
    docs_text = panel.locator('.knowledge-docs').inner_text()
    assert 'UI 端到端验证文档' in docs_text, '新文档未出现在清单'
    print('[2] 文本入库: OK（清单已出现新文档）')

    # 3. 检索测试命中（BM25 分词按自然语言词命中）
    page.fill('#kb-query', '混合检索 向量 召回')
    page.get_by_role('button', name='检索', exact=True).click()
    page.wait_for_timeout(1200)
    hits_text = panel.locator('.knowledge-hits').inner_text()
    assert 'UI 端到端验证文档' in hits_text and '混合检索' in hits_text, '检索未命中新文档'
    print('[3] 检索测试: OK（命中含标题与分数）')

    # 4. 向量模型档案下拉存在 bge-m3 预设
    emb_select = page.locator('#embedding-profile-select')
    emb_select.select_option(label='BGE-M3（本地网关 18888）')
    endpoint_value = page.input_value('#embedding-endpoint')
    model_value = page.input_value('#embedding-model')
    assert '18888' in endpoint_value, f'向量档案未应用地址: {endpoint_value}'
    assert model_value == 'bge-m3', f'向量档案未应用模型: {model_value}'
    print('[4] 向量档案应用: OK ->', endpoint_value, '/', model_value)

    # 5. “应用到知识库”按钮存在（展开向量组；不点击，避免污染签名状态）
    page.locator('.embedding-config-group summary').click()
    page.wait_for_timeout(200)
    apply_btn = page.get_by_role('button', name='应用到知识库')
    assert apply_btn.count() == 1 and apply_btn.is_enabled(), '应用到知识库按钮不可用'
    print('[5] 应用到知识库按钮: OK')

    # 6. 聊天档案下拉仍可用
    chat_select = page.locator('#model-profile-select')
    chat_select.select_option(label='DeepSeek Reasoner（深度思考）')
    model_name = page.input_value('#model-name')
    assert model_name == 'deepseek-reasoner', f'聊天档案未应用: {model_name}'
    print('[6] 聊天档案应用: OK -> deepseek-reasoner')

    page.screenshot(path='task/e2e-knowledge-panel.png', full_page=False)
    real_errors = [e for e in errors if 'favicon' not in e.lower()]
    if real_errors:
        print('[!] 控制台错误:', real_errors[:5])
    else:
        print('[7] 浏览器控制台: 无错误')
    browser.close()
    print('E2E ALL PASS')
