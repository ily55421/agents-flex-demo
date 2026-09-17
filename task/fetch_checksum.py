import json
import io
import sys

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')

data = json.load(open('deploy/runtime/assets.json', encoding='utf-8'))
for release in data:
    for binary in release.get('binaries', []):
        package = binary.get('package', {})
        name = package.get('name', '')
        if 'x64_linux' in name:
            print(name, package.get('checksum'))
