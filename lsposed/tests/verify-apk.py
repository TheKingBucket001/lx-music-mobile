#!/usr/bin/env python3
"""Verify the built module's real DEX definitions and compiled Android metadata."""
import argparse
import hashlib
from pathlib import Path
import re
import struct
import subprocess
import zipfile


def class_definitions(data):
    if not data.startswith(b'dex\n'):
        raise ValueError('Expected a standard DEX file')
    string_count, string_offset = struct.unpack_from('<II', data, 0x38)
    type_count, type_offset = struct.unpack_from('<II', data, 0x40)
    class_count, class_offset = struct.unpack_from('<II', data, 0x60)
    strings = []
    for i in range(string_count):
        offset = struct.unpack_from('<I', data, string_offset + 4 * i)[0]
        while data[offset] & 0x80:
            offset += 1
        offset += 1
        # Class descriptors are ASCII, independent of MUTF-8 string encoding.
        strings.append(data[offset:data.index(b'\0', offset)].decode('utf-8', errors='replace'))
    types = [strings[struct.unpack_from('<I', data, type_offset + 4 * i)[0]]
             for i in range(type_count)]
    return {types[struct.unpack_from('<I', data, class_offset + 32 * i)[0]]
            for i in range(class_count)}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('apk', type=Path)
    parser.add_argument('--aapt', required=True, type=Path)
    args = parser.parse_args()
    apk = args.apk.resolve(strict=True)

    def aapt(*options):
        return subprocess.run([str(args.aapt), *options], capture_output=True,
                              text=True, encoding='utf-8', check=True).stdout

    with zipfile.ZipFile(apk) as archive:
        assert archive.read('assets/xposed_init').decode('utf-8').splitlines() == [
            'dev.bucket.lxmusic.stopfix.RemoteStopHook',
            'dev.bucket.lxmusic.stopfix.MediaServiceTypeHook']
        definitions = set()
        for name in archive.namelist():
            if re.fullmatch(r'classes(?:\d+)?\.dex', name):
                definitions.update(class_definitions(archive.read(name)))
        assert 'Ldev/bucket/lxmusic/stopfix/RemoteStopHook;' in definitions
        assert 'Ldev/bucket/lxmusic/stopfix/RemoteStopHook$1;' in definitions
        assert 'Ldev/bucket/lxmusic/stopfix/MediaServiceTypeHook;' in definitions
        assert all(name.startswith('Ldev/bucket/lxmusic/stopfix/') for name in definitions), definitions
        assert not any('Test' in name for name in definitions), definitions
        assert not any(name.startswith('lib/') or name.endswith(('.jar', '.bundle'))
                       for name in archive.namelist())

    badging = aapt('dump', 'badging', str(apk))
    assert "name='lx.fix'" in badging
    assert "versionName='0.2.1'" in badging
    assert "versionCode='3'" in badging
    manifest = aapt('dump', 'xmltree', str(apk), 'AndroidManifest.xml')
    assert 'android:icon' in manifest
    assert not re.search(r'E: (?:uses-permission|permission|activity|service|receiver|provider)\b', manifest)
    for field in ['xposedmodule', 'xposedminversion', 'xposedscope', 'xposeddescription']:
        assert f'"{field}"' in manifest, field
    assert re.search(r'"xposedmodule"[^\n]*\n\s+A: android:value[^\n]*0xffffffff', manifest)
    assert re.search(r'"xposedminversion"[^\n]*\n\s+A: android:value[^\n]*0x52\b', manifest)
    resources = aapt('dump', '--values', 'resources', str(apk))
    scope = re.search(r'resource (0x[\da-f]+) [^\n]*:array/xposed_scope: <bag>\n([^\n]*\n[^\n]*\n[^\n]*)', resources)
    assert scope and 'Count=2' in scope.group(2) and '"cn.toside.music.mobile"' in scope.group(2) and '"android"' in scope.group(2)
    assert re.search(r'"xposedscope"[^\n]*\n\s+A: android:resource[^\n]*@' + scope.group(1), manifest)
    print('PASS APK entries, real Hook classes, exact LX + Android scopes, API 82 metadata and version.')
    print('PASS APK has no permissions, components, test classes, bundled Xposed API or native/JS payload.')
    print('APK_SHA256', hashlib.sha256(apk.read_bytes()).hexdigest())
    print('DEFINED_CLASSES', ', '.join(sorted(definitions)))


if __name__ == '__main__':
    main()
