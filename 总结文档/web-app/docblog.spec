"""
PyInstaller 打包配置
"""
import os
import sys

block_cipher = None

# 手动指定项目目录
project_dir = 'd:\\阅读\\总结文档\\web-app'

a = Analysis(
    ['app.py'],
    pathex=[project_dir],
    binaries=[],
    datas=[
        ('static', 'static'),
        ('config.json', '.'),
    ],
    hiddenimports=[
        'server',
        'sqlite3',
        'http.server',
        'json',
        'os',
        're',
        'time',
        'urllib.parse',
        'html',
        'logging',
        'pathlib',
        'datetime',
        'threading',
        'hashlib',
        'webview',
        'webview.platforms.winforms',
        'bottle',
        'proxy_tools',
    ],
    hookspath=[],
    runtime_hooks=[],
    excludes=[],
    win_no_prefer_redirects=False,
    win_private_assemblies=False,
    cipher=block_cipher,
    noarchive=False,
)

pyz = PYZ(a.pure, a.zipped_data, cipher=block_cipher)

exe = EXE(
    pyz,
    a.scripts,
    a.binaries,
    a.zipfiles,
    a.datas,
    [],
    name='DocBlog',
    debug=False,
    bootloader_ignore_signals=False,
    strip=False,
    upx=True,
    upx_exclude=[],
    runtime_tmpdir=None,
    console=True,
    disable_windowed_traceback=False,
    target_arch=None,
    codesign_identity=None,
    entitlements_file=None,
)
