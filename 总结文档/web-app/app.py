"""
文档博客桌面应用
使用 PyWebView 封装 web-app
"""
import os
import sys
import threading
import time
import webview

# 获取应用根目录（exe所在目录或开发环境下的web-app目录）
if getattr(sys, 'frozen', False):
    # 打包后的exe环境
    APP_ROOT = os.path.dirname(sys.executable)
else:
    # 开发环境
    APP_ROOT = os.path.dirname(os.path.abspath(__file__))

# 添加当前目录到路径
sys.path.insert(0, APP_ROOT)

def start_server():
    """启动内部 HTTP 服务器"""
    import server

    # exe在 dist 目录下，源文档和数据在其上级目录
    # APP_ROOT = d:\阅读\总结文档\web-app\dist
    # parent_dir = d:\阅读\总结文档\web-app
    # docs_src = d:\阅读\总结文档 (parent_dir 的上级)
    # db_path = d:\阅读\总结文档\web-app\data\docs.db
    parent_dir = os.path.abspath(os.path.join(APP_ROOT, '..'))
    docs_src = os.path.abspath(os.path.join(parent_dir, '..'))  # d:\阅读\总结文档
    db_path = os.path.join(parent_dir, 'data', 'docs.db')

    print(f"[config] APP_ROOT = {APP_ROOT}")
    print(f"[config] parent_dir = {parent_dir}")
    print(f"[config] docs_src = {docs_src}")
    print(f"[config] db_path = {db_path}")

    server.update_config({
        'docs_src': docs_src,
        'db_path': db_path
    })

    # 修改配置：绑定 localhost
    server.HOST = '127.0.0.1'
    server.PORT = 8888

    # 初始化数据库
    server.init_db()

    # 导入文档
    conn = server.get_db()
    count = conn.execute("SELECT COUNT(*) FROM documents").fetchone()[0]
    conn.close()

    if count == 0:
        print("[init] 数据库为空，执行全量导入...")
        server.import_documents(incremental=False)
    else:
        print(f"[init] 已有 {count} 篇文档，执行增量导入...")
        server.import_documents(incremental=True)

    # 启动服务
    server.main()

def main():
    # 使用 ASCII 字符避免 Unicode 编码问题
    print("=== DocBlog Desktop App ===")
    print("Starting DocBlog...")
    print(f"App root: {APP_ROOT}")

    # 在后台线程启动服务器
    server_thread = threading.Thread(target=start_server, daemon=True)
    server_thread.start()

    # 等待服务器启动
    print("Waiting for server to start...")
    time.sleep(3)

    # 创建窗口 - 使用兼容的参数
    window = webview.create_window(
        title='文档博客',
        url='http://127.0.0.1:8888',
        width=1200,
        height=800,
        resizable=True,
        fullscreen=False
    )

    # 启动 GUI 循环，启用调试模式
    # debug=True 允许使用 F12 打开开发者工具
    print("Opening window...")
    webview.start(debug=True)

if __name__ == '__main__':
    main()
