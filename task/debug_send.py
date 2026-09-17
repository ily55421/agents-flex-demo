# -*- coding: utf-8 -*-
import sys, io
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')
from playwright.sync_api import sync_playwright

BASE = 'http://localhost:5173'
with sync_playwright() as p:
    browser = p.chromium.launch(headless=True)
    page = browser.new_page(viewport={'width': 1680, 'height': 950})
    page.on('pageerror', lambda e: print('PAGEERROR:', str(e)[:150]))
    page.goto(BASE)
    page.wait_for_load_state('networkidle')
    page.wait_for_timeout(1500)
    select = page.locator('#workspace-agent-select')
    page.wait_for_timeout(800)
    options = select.locator('option')
    select.select_option(value=options.nth(1).get_attribute('value'))
    page.wait_for_timeout(600)
    ta = page.locator('textarea[aria-label="输入对话内容"]')
    print('runnable agent? picker option:', options.nth(1).inner_text())
    ta.click()
    ta.fill('你好，请只回复：OK')
    ta.press('Enter')
    for second in range(2, 61, 2):
        page.wait_for_timeout(2000)
        msgs = page.locator('.chat-message').count()
        panel = page.locator('.result-panel').count()
        streaming = page.locator('.streaming-content').count()
        thinking = page.locator('.thinking-message').count()
        url = page.url.split('?')[1] if '?' in page.url else ''
        print(f't={second}s msgs={msgs} result={panel} streaming={streaming} thinking={thinking} url={url}')
        if panel:
            break
    browser.close()
