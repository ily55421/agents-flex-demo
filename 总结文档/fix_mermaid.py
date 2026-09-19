import os
import re

base_dir = r"d:\阅读\总结文档"

for root, dirs, files in os.walk(base_dir):
    for file in files:
        if not file.endswith('.md'):
            continue
        
        filepath = os.path.join(root, file)
        try:
            with open(filepath, 'r', encoding='utf-8') as f:
                content = f.read()
        except Exception as e:
            print(f"读取失败: {file} - {e}")
            continue
        
        original = content
        
        # 匹配 ```mermaid ... ``` 代码块
        pattern = r'```mermaid\s*\n([\s\S]*?)\n```'
        
        def replace_question(match):
            block = match.group(1)
            # 去除 [] 中的 ? 号
            block_fixed = re.sub(r'\[([^\]]*?)\?([^\]]*?)\]', r'[\1\2]', block)
            if block_fixed != block:
                return f'```mermaid\n{block_fixed}\n```'
            return match.group(0)
        
        content = re.sub(pattern, replace_question, content)
        
        if content != original:
            try:
                with open(filepath, 'w', encoding='utf-8') as f:
                    f.write(content)
                print(f"已更新: {file}")
            except Exception as e:
                print(f"写入失败: {file} - {e}")

print("\n处理完成")
