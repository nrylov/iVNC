#!/usr/bin/env python3
# Fetch a pinned tool archive and verify its digest before allowing extraction.
import hashlib
import json
from pathlib import Path
import subprocess
import sys

name, destination = sys.argv[1:]
entry = json.loads(Path(__file__).with_name('toolchain-lock.json').read_text())[name]
archive = Path(destination)
if not archive.exists() or hashlib.sha256(archive.read_bytes()).hexdigest() != entry['sha256']:
    subprocess.run(['curl', '--fail', '--location', '--retry', '3', entry['url'], '-o', str(archive)], check=True)
if hashlib.sha256(archive.read_bytes()).hexdigest() != entry['sha256']:
    raise SystemExit('Checksum mismatch: ' + name)
print(name + ': SHA-256 verified')
