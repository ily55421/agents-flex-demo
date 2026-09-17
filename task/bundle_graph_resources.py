"""把 CAD 转换工程产出的图谱数据固化为本项目后端资源。

来源: D:\\workspace\\qiz\\ylj\\cad-to-svg-converter\\cad-to-svg-converter\\svg
产物: src/main/resources/graph/
  - power-topology-graph.json   本体图谱（meta/tbox/nodes/edges）
  - power-topology-kb.json      站点档案（generated + stations，取自电力拓扑知识库.html 内嵌 DATA）
  - power-topology-facts.jsonl  知识问答事实（station/category/text）
"""
import io
import json
import shutil
import sys
from pathlib import Path

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")

SRC = Path(r"D:\workspace\qiz\ylj\cad-to-svg-converter\cad-to-svg-converter\svg")
DEST = Path(r"E:\code\ai-platform-amd64\agents-flex-demo\src\main\resources\graph")
DEST.mkdir(parents=True, exist_ok=True)

# 1. 图谱本体数据（原样拷贝）
shutil.copyfile(SRC / "ontology/kg/graph.json", DEST / "power-topology-graph.json")
graph = json.loads((DEST / "power-topology-graph.json").read_text(encoding="utf-8"))
print(f"power-topology-graph.json: {len(graph['nodes'])} nodes / {len(graph['edges'])} edges / "
      f"tbox {len(graph['tbox']['nodes'])} classes")

# 2. 站点档案：从电力拓扑知识库.html 提取内嵌 DATA（generated + stations）
kb_html = (SRC / "topology_data/电力拓扑知识库.html").read_text(encoding="utf-8")
marker = "const DATA = "
start = kb_html.find(marker) + len(marker)
kb_data, _ = json.JSONDecoder().raw_decode(kb_html[start:])
kb_bundle = {"generated": kb_data.get("generated"), "stations": kb_data.get("stations", [])}
(DEST / "power-topology-kb.json").write_text(
    json.dumps(kb_bundle, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
print(f"power-topology-kb.json: {len(kb_bundle['stations'])} stations, generated={kb_bundle['generated']}")

# 3. 知识问答事实 JSONL（原样拷贝）
shutil.copyfile(SRC / "topology_data/电力拓扑知识问答.jsonl", DEST / "power-topology-facts.jsonl")
facts = [json.loads(line) for line in
         (DEST / "power-topology-facts.jsonl").read_text(encoding="utf-8").splitlines() if line.strip()]
print(f"power-topology-facts.jsonl: {len(facts)} facts")

for f in sorted(DEST.iterdir()):
    print(f"  {f.name}: {f.stat().st_size/1024:.0f} KB")
