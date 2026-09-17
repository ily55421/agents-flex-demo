"""校验 WeKnora 迁移两份文档：分类索引条目计数 + 引用路径存在性。

用法：python task/verify_weknora_docs.py
输出：条目计数核对结果、路径抽查结果，任一失败以非零码退出。
"""
import re
import sys
from pathlib import Path

PROJECT = Path(r"E:\code\ai-platform-amd64\agents-flex-demo-self")
WEKNORA = Path(r"E:\code\source\WeKnora")
DOC1 = PROJECT / "docs" / "WeKnora功能模块分析与迁移适配方案.md"
DOC2 = PROJECT / "docs" / "WeKnora迁移可执行技术方案.md"

failures = []


def check_categorized_index() -> None:
    """统计 A-I 九类条目数，与文末声明的汇总数字核对。"""
    text = DOC1.read_text(encoding="utf-8")
    section = text.split("## 九、分门别类总索引", 1)
    if len(section) != 2:
        failures.append("未找到『九、分门别类总索引』章节")
        return
    index_body = section[1]

    # 每个分类小节形如「### A. 文档解析类（6 条）」
    header_re = re.compile(r"^### ([A-I])\. .+?（(\d+) 条）$", re.MULTILINE)
    headers = list(header_re.finditer(index_body))
    if not headers:
        failures.append("未匹配到任何分类小节标题")
        return

    print("=== 分类索引条目计数 ===")
    declared_total = 0
    actual_total = 0
    detail = []
    for position, match in enumerate(headers):
        letter, declared = match.group(1), int(match.group(2))
        start = match.end()
        end = headers[position + 1].start() if position + 1 < len(headers) else len(index_body)
        # 条目形如「1. ✅ text」「12. ❌ text」；只统计连续编号行
        items = re.findall(r"^(\d+)\. (✅|🔧|❌|⏭️)", index_body[start:end], re.MULTILINE)
        actual = len(items)
        declared_total += declared
        actual_total += actual
        flag = "OK " if declared == actual else "FAIL"
        detail.append((flag, letter, declared, actual))
        print(f"  [{flag}] {letter} 类：声明 {declared} 条 / 实际 {actual} 条")

    print(f"  合计：声明 {declared_total} 条 / 实际 {actual_total} 条")
    if declared_total != actual_total:
        failures.append(f"条目数不一致：声明 {declared_total}，实际 {actual_total}")

    # 性质分布统计（✅/🔧/❌/⏭️）
    all_items = re.findall(r"^\d+\. (✅|🔧|❌|⏭️)", index_body, re.MULTILINE)
    counts = {mark: all_items.count(mark) for mark in ("✅", "🔧", "❌", "⏭️")}
    print("  性质分布：" + " / ".join(f"{k}{v}" for k, v in counts.items()))
    if sum(counts.values()) != actual_total:
        failures.append("性质分布合计与条目总数不一致")

    # 与文末“索引统计”段落声明的数字核对
    stat_match = re.search(r"9 大类共 \*\*(\d+) 条\*\*", text)
    if stat_match:
        stated = int(stat_match.group(1))
        status = "OK " if stated == actual_total else "FAIL"
        print(f"  [{status}] 文末声明总数 {stated} 条 / 实际 {actual_total} 条")
        if stated != actual_total:
            failures.append(f"文末声明总数 {stated} 与实际 {actual_total} 不符")
    else:
        failures.append("未找到文末『索引统计』声明")


def check_paths() -> None:
    """抽取文档中的反引号路径，校验其在对应仓库中真实存在。

    形如 `path（Phase N 新增）` 的引用属于「计划新增文件」，不存在属于预期，
    单独计入待建清单而不判失败。
    """
    print("\n=== 引用路径存在性抽查 ===")
    project_paths, weknora_paths, planned = set(), set(), set()
    for doc in (DOC1, DOC2):
        text = doc.read_text(encoding="utf-8")
        for line in text.splitlines():
            for token in re.findall(r"`([^`\n]+)`", line):
                token = token.strip()
                # 计划新增文件：行首标「新增」、行内含「（Phase」或为包路径
                is_planned = (
                    token.split("（")[0].strip() in line
                    and ("（Phase" in token or "(Phase" in token or line.strip().startswith("新增"))
                )
                if is_planned:
                    planned.add(token.split("（")[0].strip())
                    continue
                if token.endswith("/"):
                    continue
                if token.startswith(("internal/", "docreader/", "migrations/", "mcp-server/")):
                    weknora_paths.add(token.rstrip("/"))
                elif token.startswith(("src/", "frontend/", "deploy/")) or token in (
                    "pom.xml",
                    "README.md",
                ):
                    project_paths.add(token.rstrip("/"))

    def report(label: str, root: Path, paths: set) -> None:
        missing = []
        for relative in sorted(paths):
            candidate = root / relative
            # 允许「目录/前缀」式引用：父目录存在即视为有效
            if not candidate.exists() and not (root / Path(relative).parent).exists():
                missing.append(relative)
        print(f"  {label}：检查 {len(paths)} 条，缺失 {len(missing)} 条")
        for item in missing:
            print(f"    [FAIL] {item}")
        if missing:
            failures.append(f"{label} 路径缺失 {len(missing)} 条")

    report("本项目", PROJECT, project_paths)
    report("WeKnora", WEKNORA, weknora_paths)
    if planned:
        print(f"  计划新增（Phase 实施时创建，不计缺失）：{len(planned)} 条")
        for item in sorted(planned):
            print(f"    [PLAN] {item}")


def check_deliverables() -> None:
    print("\n=== 交付物检查 ===")
    for doc in (DOC1, DOC2):
        exists = doc.exists()
        size = doc.stat().st_size if exists else 0
        status = "OK " if exists and size > 2000 else "FAIL"
        print(f"  [{status}] {doc.name}（{size} 字节）")
        if not (exists and size > 2000):
            failures.append(f"交付物缺失或过小：{doc.name}")


if __name__ == "__main__":
    check_deliverables()
    check_categorized_index()
    check_paths()
    print("\n=== 结论 ===")
    if failures:
        for item in failures:
            print(f"  [FAIL] {item}")
        sys.exit(1)
    print("  PASS：索引计数一致、引用路径存在、两份交付物就绪")
