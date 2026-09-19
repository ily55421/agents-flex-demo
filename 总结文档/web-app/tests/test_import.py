"""
v1.3 导入一致性测试
覆盖：增量导入、文件变更检测、源文件删除同步
运行：python -m pytest tests/test_import.py -v
"""
import sys
import json
import tempfile
import shutil
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent.parent))

import server as app


def test_file_fingerprint():
    """文件指纹应包含 mtime 和 size"""
    with tempfile.NamedTemporaryFile(mode='w', suffix='.md', delete=False) as f:
        f.write('# Test\n')
        f.flush()
        path = Path(f.name)

    try:
        mtime, size = app._file_fingerprint(path)
        assert mtime > 0
        assert size > 0
        assert size == path.stat().st_size
    finally:
        path.unlink()


def test_import_new_file():
    """导入新文件应成功"""
    with tempfile.TemporaryDirectory() as tmpdir:
        # 创建临时配置
        original_src = app.DOCS_SRC
        app.DOCS_SRC = Path(tmpdir)
        app.DOCS_CATEGORIES_DIR = Path(tmpdir) / "docs"

        # 创建测试 markdown
        md_file = Path(tmpdir) / "test_doc.md"
        md_file.write_text('# Hello World\n\nThis is a test.', encoding='utf-8')

        try:
            result = app.import_documents(incremental=False)
            assert result['imported'] >= 1
            assert result['updated'] == 0
        finally:
            app.DOCS_SRC = original_src
            app.DOCS_CATEGORIES_DIR = original_src / "docs"


def test_import_skip_unchanged():
    """未变更文件应跳过"""
    with tempfile.TemporaryDirectory() as tmpdir:
        original_src = app.DOCS_SRC
        app.DOCS_SRC = Path(tmpdir)
        app.DOCS_CATEGORIES_DIR = Path(tmpdir) / "docs"

        md_file = Path(tmpdir) / "test_doc.md"
        md_file.write_text('# Hello World\n\nThis is a test.', encoding='utf-8')

        try:
            # 首次导入
            result1 = app.import_documents(incremental=False)
            imported = result1['imported']

            # 再次增量导入
            result2 = app.import_documents(incremental=True)
            assert result2['skipped'] >= imported
            assert result2['imported'] == 0
            assert result2['updated'] == 0
        finally:
            app.DOCS_SRC = original_src
            app.DOCS_CATEGORIES_DIR = original_src / "docs"


def test_import_detect_deleted():
    """删除源文件后应同步到回收站"""
    with tempfile.TemporaryDirectory() as tmpdir:
        original_src = app.DOCS_SRC
        app.DOCS_SRC = Path(tmpdir)
        app.DOCS_CATEGORIES_DIR = Path(tmpdir) / "docs"

        md_file = Path(tmpdir) / "test_doc.md"
        md_file.write_text('# Hello World\n\nThis is a test.', encoding='utf-8')

        try:
            # 首次导入
            result1 = app.import_documents(incremental=False)
            imported = result1['imported']

            # 删除文件
            md_file.unlink()

            # 增量同步
            result2 = app.import_documents(incremental=True)
            assert result2['deleted'] >= imported
        finally:
            app.DOCS_SRC = original_src
            app.DOCS_CATEGORIES_DIR = original_src / "docs"


if __name__ == '__main__':
    print('=== 导入一致性测试 ===')
    test_file_fingerprint()
    print('✓ 文件指纹')
    test_import_new_file()
    print('✓ 新文件导入')
    test_import_skip_unchanged()
    print('✓ 跳过未变更')
    test_import_detect_deleted()
    print('✓ 删除同步')
    print('\n=== 全部通过 ===')
