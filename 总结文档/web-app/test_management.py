import urllib.request
import json

BASE = 'http://127.0.0.1:8080'

def api(endpoint):
    try:
        req = urllib.request.Request(BASE + endpoint)
        resp = urllib.request.urlopen(req)
        return json.loads(resp.read())
    except Exception as e:
        return {"error": str(e)}

print('=== 管理功能测试 ===')

# 1. 获取第一篇文档
docs = api('/api/documents?page=1&limit=1')
if docs.get('documents'):
    doc_id = docs['documents'][0]['id']
    print(f'1. 获取文档: ID={doc_id}, title={docs["documents"][0]["title"]}')

    # 2. 软删除
    result = api(f'/api/delete?id={doc_id}')
    print(f'2. 软删除: {result}')

    # 3. 检查回收站
    trash = api('/api/trash?page=1&limit=5')
    print(f'3. 回收站: {trash.get("total", 0)} 篇文档')

    # 4. 恢复文档
    result = api(f'/api/restore?id={doc_id}')
    print(f'4. 恢复: {result}')

    # 5. 检查回收站为空
    trash = api('/api/trash?page=1&limit=5')
    print(f'5. 回收站(恢复后): {trash.get("total", 0)} 篇文档')
else:
    print('1. 没有文档可测试')

# 6. 批量改分类
docs = api('/api/documents?page=1&limit=2')
if docs.get('documents') and len(docs['documents']) >= 2:
    ids = ','.join(str(d['id']) for d in docs['documents'])
    result = api(f'/api/batch-recategorize?ids={ids}&slug=java')
    print(f'6. 批量改分类: {result}')

# 7. 标签列表
tags = api('/api/tags')
print(f'7. 标签数: {len(tags.get("tags", []))}')

# 8. 文档标签
if docs.get('documents'):
    doc_tags = api(f'/api/document-tags?id={docs["documents"][0]["id"]}')
    print(f'8. 文档标签: {doc_tags}')

print('\n=== 测试完成 ===')
