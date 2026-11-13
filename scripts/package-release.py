#!/usr/bin/env python3
"""Package a verified release APK with complete tracked/native dependency sources and checksums."""
import hashlib
import io
import json
from pathlib import Path
import shutil
import subprocess
import tarfile
import tempfile

ROOT = Path(__file__).resolve().parent.parent

def git(*args, cwd=ROOT):
    """Read Git metadata/source without including ignored toolchains, keys or user data."""
    return subprocess.check_output(['git', *args], cwd=cwd)

def extract(source, destination):
    """Extract only trusted local Git archives into an isolated packaging directory."""
    with tarfile.open(fileobj=io.BytesIO(source)) as archive:
        root = destination.resolve()
        for member in archive.getmembers():
            target = (root/member.name).resolve()
            if root != target and root not in target.parents:
                raise ValueError('Archive path leaves source directory')
            if member.isdev():
                raise ValueError('Device entries are not source files')
            if member.issym() or member.islnk():
                base = target.parent if member.issym() else root
                link = (base/member.linkname).resolve()
                if root != link and root not in link.parents:
                    raise ValueError('Archive link leaves source directory')
        # These archives come from local Git commits. Validate paths explicitly
        # for Python versions predating tarfile's extraction-filter API.
        archive.extractall(destination)

def main():
    """Publishable artifacts contain no signing key; source includes exact prepared client code."""
    subprocess.run(['git', 'diff', '--exit-code', 'HEAD', '--'], cwd=ROOT, check=True, stdout=subprocess.DEVNULL)
    metadata = json.loads((ROOT/'android/native-app/build/outputs/apk/release/output-metadata.json').read_text())
    entry = metadata['elements'][0]
    assert metadata['variantName'] == 'release'
    version = entry['versionName']
    assert all(c in '0123456789.-abcdefghijklmnopqrstuvwxyz' for c in version)
    commit = git('rev-parse', 'HEAD').decode().strip()
    library = git('rev-parse', 'HEAD:third_party/libvncserver').decode().strip()
    output = ROOT/'.dev/releases'/f'v{version}'
    output.mkdir(parents=True, exist_ok=True)
    apk = output/f'iVNC-{version}-arm64-v8a.apk'
    shutil.copyfile(ROOT/'android/native-app/build/outputs/apk/release'/entry['outputFile'], apk)
    source = output/f'iVNC-{version}-source.tar.gz'
    with tempfile.TemporaryDirectory(prefix='source-', dir=output) as temporary:
        stage = Path(temporary)/f'iVNC-{version}'
        stage.mkdir()
        extract(git('archive', '--format=tar', commit), stage)
        dependency = stage/'third_party/libvncserver'
        dependency.mkdir(parents=True, exist_ok=True)
        extract(git('archive', '--format=tar', library, cwd=ROOT/'third_party/libvncserver'), dependency)
        prepared = stage/'prepared/libvncclient'
        shutil.copytree(ROOT/'.dev/libvncclient-source', prepared, symlinks=True)
        patch = (prepared/'.ivnc-patch').read_text().strip()
        (stage/'RELEASE-INFO.txt').write_text(
            f'iVNC {version}\nSource commit: {commit}\nPackage: {metadata["applicationId"]}\n'
            f'Android version code: {entry["versionCode"]}\nABI: arm64-v8a\nMinimum Android API: 31\n'
            f'LibVNCClient: {library}\nLocal patch: {patch}\n'
            'prepared/libvncclient contains the exact patched source used to build.\n'
            'FFmpeg original source archive and license: third_party/ffmpeg.\n'
            'Build: scripts/bootstrap.sh; scripts/bootstrap-native.sh; scripts/build-release.sh\n'
            'SDK terms must be accepted by the builder. Private signing keys are not included.\n')
        with tarfile.open(source, 'w:gz') as archive:
            archive.add(stage, arcname=stage.name)
    checksums = output/'SHA256SUMS'
    checksums.write_text(''.join(f'{hashlib.sha256(path.read_bytes()).hexdigest()}  {path.name}\n' for path in [apk, source]))
    print(json.dumps({'commit':commit, 'version':version, 'assets':[str(apk),str(source),str(checksums)]}, indent=2))

if __name__ == '__main__':
    main()
