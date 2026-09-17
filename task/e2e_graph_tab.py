# -*- coding: utf-8 -*-
# 验证图谱 Tab：五 Tab 架构、本体图谱画布、图例/站点过滤、检索与详情面板、
# 本体 TBox 视图、站点档案视图（档案 + 事实检索）与 URL 恢复。
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
    page.wait_for_timeout(1200)

    # 1. 五个页面 Tab
    tabs = page.locator('.page-tabs button')
    assert tabs.count() == 5, f'Tab 数量异常: {tabs.count()}'
    labels = [tabs.nth(i).inner_text() for i in range(5)]
    print('[1] 五 Tab:', ' / '.join(labels))

    # 2. 打开图谱 Tab：暗色画布 + 统计摘要（693 实体 / 3598 关系）
    tabs.nth(4).click()
    page.wait_for_selector('.graph-shell', timeout=8000)
    page.wait_for_selector('.stat-chip', timeout=15000)
    page.wait_for_timeout(2500)  # 等力导向布局展开
    assert 'view=graph' in page.url, 'URL 未同步 view=graph'
    stat = page.locator('.stat-chip').inner_text().replace('\n', ' ')
    assert '693' in stat and '3598' in stat, f'统计摘要异常: {stat}'
    assert page.locator('.graph-canvas').count() == 1, '画布未渲染'
    print('[2] 本体图谱画布: OK ->', stat)

    # 3. 类别图例：数量与开关过滤
    legend = page.locator('.legend-item')
    assert legend.count() >= 10, f'图例类别过少: {legend.count()}'
    breaker = page.locator('.legend-item', has_text='断路器')
    breaker.click()  # 关闭断路器
    page.wait_for_timeout(400)
    assert 'off' in (breaker.get_attribute('class') or ''), '图例未进入关闭态'
    breaker.click()  # 恢复
    page.wait_for_timeout(300)
    print('[3] 类别图例过滤: OK ->', legend.count(), '类')

    # 4. 站点过滤：切到藏木变
    station_select = page.locator('.graph-bar select')
    station_select.select_option(value='35kV藏木变')
    page.wait_for_timeout(2200)
    print('[4] 站点过滤（35kV藏木变）: OK')
    page.screenshot(path='task/e2e-graph-tab.png', full_page=False)

    # 5. 检索 + 详情面板：搜“主变”选中第一个命中
    page.locator('.graph-bar input[type=search]').fill('主变')
    page.wait_for_selector('.hit-list .hit-item', timeout=5000)
    hit_count = page.locator('.hit-list .hit-item').count()
    page.locator('.hit-list .hit-item').first.click()
    page.wait_for_selector('.side', timeout=6000)
    side_title = page.locator('.side h2').inner_text()
    assert side_title, '详情面板标题为空'
    li_count = page.locator('.side .li').count()
    assert li_count > 0, '详情面板未展示邻接关系'
    print(f'[5] 检索与详情面板: OK -> 命中 {hit_count}，选中「{side_title}」，邻接 {li_count} 条')
    page.screenshot(path='task/e2e-graph-node-detail.png', full_page=False)

    # 6. 本体 TBox 视图
    page.locator('.seg button', has_text='本体 TBox').click()
    page.wait_for_timeout(1800)
    assert page.locator('.graph-canvas').count() == 1, 'TBox 画布未渲染'
    print('[6] 本体 TBox 视图: OK')
    page.screenshot(path='task/e2e-graph-tbox.png', full_page=False)

    # 7. 站点档案视图：站清单 + 概览 + 事实检索
    page.locator('.seg button', has_text='站点档案').click()
    page.wait_for_selector('.board', timeout=8000)
    stations = page.locator('.st-item')
    assert stations.count() == 9, f'站清单数量异常: {stations.count()}'
    station_names = page.locator('.st-item .nm').all_inner_texts()
    assert any('35kVrkz土布加变电站' in n for n in station_names), f'站名与源数据不符: {station_names[:3]}'
    page.wait_for_selector('.statgrid .stat', timeout=8000)
    page.locator('.st-item', has_text='35kV藏木变').click()
    page.wait_for_timeout(800)
    body_text = page.locator('.board-body').inner_text()
    assert '母线' in body_text and '主变压器' in body_text, '站点档案区块缺失'
    # 顶部截图：概览统计 + 母线 + 主变 + 间隔
    page.screenshot(path='task/e2e-graph-station.png', full_page=False)
    # 事实检索（滚动到事实区）
    page.locator('.fact-search input').fill('主变')
    page.wait_for_timeout(900)
    facts = page.locator('.fact').count()
    assert facts > 0, '事实检索无结果'
    cats = page.locator('.cat').count()
    assert cats >= 5, f'事实类别过少: {cats}'
    page.locator('.facts').scroll_into_view_if_needed()
    page.wait_for_timeout(400)
    print(f'[7] 站点档案视图: OK -> 9 站 / 事实检索 {facts} 条展示 / {cats} 类别')
    page.screenshot(path='task/e2e-graph-station-facts.png', full_page=False)

    # 8. URL 直接恢复图谱页
    page.goto(BASE + '/?view=graph')
    page.wait_for_load_state('networkidle')
    page.wait_for_selector('.graph-shell', timeout=8000)
    print('[8] URL 恢复图谱页: OK')

    real_errors = [e for e in errors if 'favicon' not in e.lower()]
    print('[9] 控制台错误:', real_errors[:3] if real_errors else '无')
    assert not real_errors, f'存在控制台错误: {real_errors[:3]}'
    browser.close()
    print('GRAPH TAB ALL PASS')
