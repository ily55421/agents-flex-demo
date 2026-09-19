#!/usr/bin/env python3
"""Prepare assets for Android build - copy static files and zip database"""
import os
import shutil
import zipfile
from pathlib import Path

def main():
    script_dir = Path(__file__).parent
    webapp_dir = script_dir.parent
    assets_dir = script_dir / "app" / "src" / "main" / "assets"
    
    print("Cleaning old assets...")
    if assets_dir.exists():
        shutil.rmtree(assets_dir)
    assets_dir.mkdir(parents=True, exist_ok=True)
    
    # Copy static files
    print("Copying static files...")
    static_src = webapp_dir / "static"
    static_dst = assets_dir / "static"
    shutil.copytree(static_src, static_dst)
    
    # Zip database
    print("Compressing database...")
    data_src = webapp_dir / "data"
    data_dst = assets_dir / "data"
    data_dst.mkdir(parents=True, exist_ok=True)
    
    db_file = data_src / "docs.db"
    if not db_file.exists():
        print("ERROR: docs.db not found! Please run python server.py first.")
        return 1
    
    db_zip = data_dst / "docs.db.zip"
    if db_zip.exists():
        db_zip.unlink()
    
    db_size_before = db_file.stat().st_size
    with zipfile.ZipFile(db_zip, 'w', zipfile.ZIP_DEFLATED, compresslevel=9) as zf:
        zf.write(db_file, "docs.db")
    
    db_size_after = db_zip.stat().st_size
    static_count = sum(1 for _ in static_dst.rglob('*') if _.is_file())
    
    print()
    print("Assets prepared successfully!")
    print(f"  Static files: {static_count}")
    print(f"  DB size: {db_size_before/1024/1024:.2f} MB")
    print(f"  Compressed: {db_size_after/1024/1024:.2f} MB")
    ratio = (1 - db_size_after/db_size_before) * 100
    print(f"  Compression ratio: {ratio:.1f}%")
    print()
    print("Now build APK with Android Studio")
    return 0

if __name__ == "__main__":
    exit(main())
