#!/usr/bin/env python3
"""Create simple mipmap icons for Android"""
from pathlib import Path
import shutil
import struct
import zlib

def create_png(width, height, color):
    """Create a simple solid color PNG with rounded corners effect"""
    def make_chunk(chunk_type, data):
        chunk = chunk_type + data
        return struct.pack('>I', len(data)) + chunk + struct.pack('>I', zlib.crc32(chunk) & 0xffffffff)
    
    # Simple PNG with solid color and simple pattern
    signature = b'\x89PNG\r\n\x1a\n'
    
    # IHDR
    ihdr_data = struct.pack('>IIBBBBB', width, height, 8, 2, 0, 0, 0)
    ihdr = make_chunk(b'IHDR', ihdr_data)
    
    # IDAT - simple gradient background
    raw_data = b''
    r, g, b = color
    for y in range(height):
        raw_data += b'\x00'  # filter byte
        for x in range(width):
            # Create a simple gradient
            factor = 0.8 + 0.2 * (x + y) / (width + height)
            px_r = min(255, int(r * factor))
            px_g = min(255, int(g * factor))
            px_b = min(255, int(b * factor))
            raw_data += bytes([px_r, px_g, px_b])
    
    compressed = zlib.compress(raw_data)
    idat = make_chunk(b'IDAT', compressed)
    iend = make_chunk(b'IEND', b'')
    
    return signature + ihdr + idat + iend

def main():
    android_dir = Path(__file__).parent
    res_dir = android_dir / "app" / "src" / "main" / "res"
    
    # Icon color (accent blue: #6c8cff)
    icon_color = (108, 140, 255)
    
    # Create mipmap directories
    densities = {
        'mipmap-mdpi': 48,
        'mipmap-hdpi': 72,
        'mipmap-xhdpi': 96,
        'mipmap-xxhdpi': 144,
        'mipmap-xxxhdpi': 192,
    }
    
    # Copy existing icons from static folder
    static_icons = android_dir.parent / "static"
    
    for folder, size in densities.items():
        mipmap_dir = res_dir / folder
        mipmap_dir.mkdir(parents=True, exist_ok=True)
        
        # Try to use existing icon file
        icon_src = static_icons / f"icon-{size}.png"
        icon_dst = mipmap_dir / "ic_launcher.png"
        
        if icon_src.exists():
            shutil.copy(icon_src, icon_dst)
            print(f"Copied icon-{size}.png -> {folder}/ic_launcher.png")
        else:
            # Generate a simple icon
            png_data = create_png(size, size, icon_color)
            with open(icon_dst, 'wb') as f:
                f.write(png_data)
            print(f"Generated icon -> {folder}/ic_launcher.png ({size}x{size})")
    
    # Create round icons for adaptive icon
    for folder in densities.keys():
        mipmap_dir = res_dir / folder
        round_dst = mipmap_dir / "ic_launcher_round.png"
        if not round_dst.exists():
            launcher = mipmap_dir / "ic_launcher.png"
            if launcher.exists():
                shutil.copy(launcher, round_dst)
    
    print("\nIcons created successfully!")
    return 0

if __name__ == "__main__":
    exit(main())
