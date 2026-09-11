# -*- coding: utf-8 -*-
"""导入 topology_data 资料到知识库：9 站 md + 总览 md + jsonl 问答库。"""
import io
import json
import sys
import urllib.request
import urllib.error
from pathlib import Path

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')

SRC = Path(r"D:\workspace\qiz\ylj\cad-to-svg-converter\cad-to-svg-converter\svg\topology_data")
API = "http://localhost:8080/api/knowledge"


def upload_jsonl(title: str, path: Path):
    """jsonl 走专用逻辑：逐条 text 入库。"""
    content = path.read_text(encoding="utf-8")
    records = []
    for line in content.splitlines():
        line = line.strip()
        if not line:
            continue
        try:
            record = json.loads(line)
            if isinstance(record.get("text"), str) and record["text"].strip():
                records.append({"station": record.get("station"),
                                "category": record.get("category"),
                                "text": record["text"].strip()})
        except json.JSONDecodeError:
            continue
    # jsonl 走 multipart 上传（后端按扩展名分流），直接传原始文件
    boundary = "----kbimport"
    body = io.BytesIO()
    body.write(f"--{boundary}\r\n".encode())
    body.write(f'Content-Disposition: form-data; name="file"; filename="{path.name}"\r\n'.encode())
    body.write(b"Content-Type: application/octet-stream\r\n\r\n")
    body.write(path.read_bytes())
    body.write(f"\r\n--{boundary}--\r\n".encode())
    req = urllib.request.Request(
        f"{API}/documents/upload",
        data=body.getvalue(),
        headers={"Content-Type": f"multipart/form-data; boundary={boundary}"},
        method="POST",
    )
    with urllib.request.urlopen(req, timeout=120) as resp:
        result = json.loads(resp.read())
    return result


def main():
    print("=== 导入电力拓扑知识库 ===")
    # 1. 总览
    overview = SRC / "电力拓扑知识库总览.md"
    results = []
    # 2. 9 站 md
    md_files = sorted((SRC / "knowledge").glob("*.md"))
    # 3. jsonl
    jsonl = SRC / "电力拓扑知识问答.jsonl"

    files = [overview] + md_files + [jsonl]
    ok, failed = 0, 0
    for path in files:
        try:
            if path.suffix == ".jsonl":
                result = upload_jsonl(path.stem, path)
            else:
                boundary = "----kbimport"
                body = io.BytesIO()
                body.write(f"--{boundary}\r\n".encode())
                body.write(
                    f'Content-Disposition: form-data; name="file"; filename="{path.name}"\r\n'.encode())
                body.write(b"Content-Type: text/markdown\r\n\r\n")
                body.write(path.read_bytes())
                body.write(f"\r\n--{boundary}--\r\n".encode())
                req = urllib.request.Request(
                    f"{API}/documents/upload",
                    data=body.getvalue(),
                    headers={"Content-Type": f"multipart/form-data; boundary={boundary}"},
                    method="POST",
                )
                with urllib.request.urlopen(req, timeout=60) as resp:
                    result = json.loads(resp.read())
            chunks = result.get("chunkCount", "?")
            doc_id = result.get("docId", "?")[:13]
            print(f"  [OK] {path.name}: {chunks} 片 (docId={doc_id}...)")
            results.append((path.name, chunks))
            ok += 1
        except urllib.error.HTTPError as e:
            detail = e.read().decode("utf-8", "ignore")[:200]
            print(f"  [FAIL] {path.name}: HTTP {e.code} {detail}")
            failed += 1
        except Exception as e:  # noqa: BLE001
            print(f"  [FAIL] {path.name}: {e}")
            failed += 1

    print(f"\n导入完成：成功 {ok}/{len(files)}，失败 {failed}")
    with urllib.request.urlopen(f"{API}/status", timeout=10) as resp:
        status = json.loads(resp.read())
    print(f"知识库状态: 文档 {status['documentCount']} · 切片 {status['chunkCount']} · "
          f"模式 {status['searchMode']} · embedding={status['embeddingModel']}")


if __name__ == "__main__":
    main()
