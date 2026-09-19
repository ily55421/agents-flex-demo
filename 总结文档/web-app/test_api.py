import urllib.request
import json

BASE = 'http://127.0.0.1:8080'

def test(endpoint):
    try:
        req = urllib.request.Request(BASE + endpoint)
        resp = urllib.request.urlopen(req)
        data = json.loads(resp.read())
        return True, data
    except Exception as e:
        return False, str(e)

print('=== API 测试 ===')

# 基础API
for ep in ['/api/categories', '/api/stats', '/api/health', '/api/tags', '/api/trash']:
    ok, data = test(ep)
    print(f'{ep}: {"OK" if ok else "FAIL"} - {list(data.keys()) if ok else data}')

# 搜索
ok, data = test('/api/search-suggest?q=java')
print(f'/api/search-suggest?q=java: {"OK" if ok else "FAIL"} - {len(data.get("suggestions", [])) if ok else data} suggestions')

ok, data = test('/api/search?q=java&page=1&limit=5')
print(f'/api/search?q=java: {"OK" if ok else "FAIL"} - {data.get("total", 0) if ok else data} results')

# 文档
ok, data = test('/api/document?id=1')
print(f'/api/document?id=1: {"OK" if ok else "FAIL"} - {data.get("title", "N/A") if ok else data}')

ok, data = test('/api/related?id=1&limit=3')
print(f'/api/related?id=1: {"OK" if ok else "FAIL"} - {len(data.get("documents", [])) if ok else data} related')

# 标签
ok, data = test('/api/tag?slug=java&page=1&limit=5')
print(f'/api/tag?slug=java: {"OK" if ok else "FAIL"} - {data.get("total", 0) if ok else data} docs')

print('\n=== 测试完成 ===')
