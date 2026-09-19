#!/usr/bin/env python3
"""生成 PWA 图标"""

import sys
from pathlib import Path

try:
    from PIL import Image, ImageDraw, ImageFont
except ImportError:
    print("正在安装 Pillow...")
    import subprocess
    subprocess.check_call([sys.executable, "-m", "pip", "install", "Pillow", "-q"])
    from PIL import Image, ImageDraw, ImageFont

BASE_DIR = Path(__file__).parent
SIZES = [72, 96, 128, 144, 152, 192, 384, 512]

def create_icon(size):
    """创建文档博客图标"""
    img = Image.new('RGBA', (size, size), (15, 17, 23, 255))
    draw = ImageDraw.Draw(img)
    
    # 绘制书本形状
    margin = size // 8
    book_w = size - margin * 2
    book_h = size - margin * 2
    x = margin
    y = margin
    
    # 书本主体
    color = (108, 140, 255)
    draw.rounded_rectangle(
        [x, y, x + book_w, y + book_h],
        radius=size // 16,
        fill=color,
        outline=(140, 170, 255),
        width=max(2, size // 64)
    )
    
    # 书脊
    spine_x = x + book_w // 2 - max(2, size // 64)
    draw.line(
        [(spine_x, y + size // 16), (spine_x, y + book_h - size // 16)],
        fill=(80, 110, 220),
        width=max(2, size // 64)
    )
    
    # 文字 "文"
    try:
        font_size = size // 3
        font = ImageFont.truetype("msyh.ttc", font_size)
    except:
        try:
            font = ImageFont.truetype("msyhbd.ttc", font_size)
        except:
            font = ImageFont.load_default()
    
    text = "文"
    bbox = draw.textbbox((0, 0), text, font=font)
    text_w = bbox[2] - bbox[0]
    text_h = bbox[3] - bbox[1]
    text_x = x + (book_w - text_w) // 2
    text_y = y + (book_h - text_h) // 2 - size // 20
    
    draw.text((text_x, text_y), text, fill=(255, 255, 255), font=font)
    
    return img

def main():
    print("生成 PWA 图标...")
    for size in SIZES:
        icon = create_icon(size)
        path = BASE_DIR / f"icon-{size}.png"
        icon.save(path, "PNG")
        print(f"  ✓ icon-{size}.png")
    print("完成！")

if __name__ == "__main__":
    main()
