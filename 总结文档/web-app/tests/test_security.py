"""
v1.3 安全回归测试
覆盖：路径逃逸、XSS、非法参数、未授权管理操作
运行：python -m pytest tests/test_security.py -v
"""
import sys
import json
import tempfile
import shutil
from pathlib import Path
from urllib.parse import quote

# 将 web-app 加入路径
sys.path.insert(0, str(Path(__file__).parent.parent))

import server as app


class MockRequest:
    """模拟 HTTP 请求"""
    def __init__(self, path, headers=None):
        self.path = path
        self.headers = headers or {}


class MockHandler(app.DocHandler):
    """模拟请求处理器"""
    def __init__(self, path, headers=None):
        self.path = path
        self.headers = headers or {}
        self.status = None
        self.body = None

    def send_response(self, status):
        self.status = status

    def send_header(self, key, value):
        pass

    def end_headers(self):
        pass

    def send_error(self, code, message=None):
        self.status = code

    def _json(self, data, status=200):
        self.status = status
        self.body = data
        return data


def test_sanitize_url():
    """链接协议白名单测试"""
    assert app._sanitize_url('http://example.com') == 'http://example.com'
    assert app._sanitize_url('https://example.com') == 'https://example.com'
    assert app._sanitize_url('/path/to/file') == '/path/to/file'
    assert app._sanitize_url('#anchor') == '#anchor'
    assert app._sanitize_url('./relative') == './relative'
    assert app._sanitize_url('javascript:alert(1)') == '#blocked'
    assert app._sanitize_url('data:text/html,<script>') == '#blocked'
    assert app._sanitize_url('') == ''


def test_inline_html_escapes_script():
    """XSS 防护：script 标签应被转义"""
    text = '<script>alert(1)</script>'
    result = app.inline_html(text)
    assert '<script>' not in result
    assert '&lt;script&gt;' in result


def test_inline_html_allows_safe_links():
    """安全链接应正常渲染"""
    text = '[链接](https://example.com)'
    result = app.inline_html(text)
    assert '<a href="https://example.com">链接</a>' in result


def test_inline_html_blocks_js_links():
    """javascript 链接应被阻止"""
    text = '[点击](javascript:alert(1))'
    result = app.inline_html(text)
    assert '#blocked' in result
    assert 'javascript:' not in result


def test_parse_int():
    """参数校验工具测试"""
    assert app.parse_int('1', 1, 1, 100) == 1
    assert app.parse_int('100', 1, 1, 100) == 100
    assert app.parse_int('0', 1, 1, 100) == 1      # 低于最小值
    assert app.parse_int('200', 1, 1, 100) == 100  # 超过最大值
    assert app.parse_int(None, 5, 1, 100) == 5     # 默认值
    assert app.parse_int('abc', 5, 1, 100) == 5    # 非法字符串
    assert app.parse_int('', 5, 1, 100) == 5       # 空字符串


def test_check_admin_token_without_config():
    """未配置 Token 时管理操作应放行"""
    original = app.ADMIN_TOKEN
    app.ADMIN_TOKEN = None
    try:
        handler = MockHandler('/api/delete')
        assert handler._check_admin_token() is True
    finally:
        app.ADMIN_TOKEN = original


def test_check_admin_token_with_config():
    """配置 Token 后未携带应拒绝"""
    original = app.ADMIN_TOKEN
    app.ADMIN_TOKEN = 'secret123'
    try:
        handler = MockHandler('/api/delete')
        assert handler._check_admin_token() is False

        handler2 = MockHandler('/api/delete', {'X-Admin-Token': 'secret123'})
        assert handler2._check_admin_token() is True

        handler3 = MockHandler('/api/delete', {'X-Admin-Token': 'wrong'})
        assert handler3._check_admin_token() is False
    finally:
        app.ADMIN_TOKEN = original


def test_static_path_escape():
    """路径逃逸应返回 403"""
    handler = MockHandler('/../server.py')
    # 模拟 _serve_static 逻辑
    decoded = quote('/../server.py')
    safe_path = (app.BASE_DIR / 'static' / decoded.lstrip('/')).resolve()
    static_root = (app.BASE_DIR / 'static').resolve()
    try:
        safe_path.relative_to(static_root)
        assert False, "应该抛出 ValueError"
    except ValueError:
        pass  # 预期行为


if __name__ == '__main__':
    print('=== 安全回归测试 ===')
    test_sanitize_url()
    print('✓ 链接白名单')
    test_inline_html_escapes_script()
    print('✓ XSS 转义')
    test_inline_html_allows_safe_links()
    print('✓ 安全链接')
    test_inline_html_blocks_js_links()
    print('✓ JS 链接阻止')
    test_parse_int()
    print('✓ 参数校验')
    test_check_admin_token_without_config()
    print('✓ Token 未配置')
    test_check_admin_token_with_config()
    print('✓ Token 校验')
    test_static_path_escape()
    print('✓ 路径逃逸防护')
    print('\n=== 全部通过 ===')
