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


def class_superclasses(data):
    string_count, string_offset = struct.unpack_from('<II', data, 0x38)
    type_count, type_offset = struct.unpack_from('<II', data, 0x40)
    class_count, class_offset = struct.unpack_from('<II', data, 0x60)
    strings = []
    for i in range(string_count):
        offset = struct.unpack_from('<I', data, string_offset + 4 * i)[0]
        while data[offset] & 0x80:
            offset += 1
        offset += 1
        strings.append(data[offset:data.index(b'\0', offset)].decode('utf-8', errors='replace'))
    types = [strings[struct.unpack_from('<I', data, type_offset + 4 * i)[0]]
             for i in range(type_count)]
    result = {}
    for i in range(class_count):
        offset = class_offset + 32 * i
        class_index, _, superclass_index = struct.unpack_from('<III', data, offset)
        result[types[class_index]] = None if superclass_index == 0xffffffff else types[superclass_index]
    return result


def properties(text):
    values = {}
    for line in text.splitlines():
        line = line.strip()
        if not line or line.startswith(('#', '!')):
            continue
        key, value = line.split('=', 1)
        assert key.strip() not in values, 'Duplicate module property: ' + key
        values[key.strip()] = value.strip()
    return values


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
        names = archive.namelist()
        assert archive.read('META-INF/xposed/java_init.list').decode('utf-8').splitlines() == [
            'dev.bucket.lxmusic.stopfix.LxModule']
        assert properties(archive.read('META-INF/xposed/module.prop').decode('utf-8')) == {
            'minApiVersion': '102', 'targetApiVersion': '102', 'staticScope': 'true'}
        scopes = archive.read('META-INF/xposed/scope.list').decode('utf-8').splitlines()
        assert len(scopes) == 2 and set(scopes) == {'cn.toside.music.mobile', 'system'}, scopes
        assert not any(name.startswith('assets/') for name in names), 'Legacy or other assets are forbidden'
        definitions = set()
        superclasses = {}
        for name in names:
            if re.fullmatch(r'classes(?:\d+)?\.dex', name):
                dex = archive.read(name)
                definitions.update(class_definitions(dex))
                superclasses.update(class_superclasses(dex))
        entry = 'Ldev/bucket/lxmusic/stopfix/LxModule;'
        assert entry in definitions, 'Missing real modern entry DEX definition'
        assert superclasses[entry] == 'Lio/github/libxposed/api/XposedModule;', superclasses[entry]
        # R8 may rename or merge internal helpers, so their original names/count are not required.
        assert all(name.startswith('Ldev/bucket/lxmusic/stopfix/') for name in definitions), definitions
        forbidden = ('Lio/github/libxposed/', 'Lde/robv/android/xposed/', 'Landroid/',
                     'Lcom/android/server/', 'Lorg/junit/', 'Ljunit/')
        assert not any(name.startswith(forbidden) or 'Test' in name for name in definitions), definitions
        assert not any(name.endswith('/RemoteStopHook;') or name.endswith('/MediaServiceTypeHook;')
                       for name in definitions), 'Legacy production entry remains'
        assert not any(name.startswith('lib/') or name.endswith(('.jar', '.bundle'))
                       for name in names)

    badging = aapt('dump', 'badging', str(apk))
    assert "name='lx.fix'" in badging
    assert "versionName='0.3.0'" in badging
    assert "versionCode='5'" in badging
    manifest = aapt('dump', 'xmltree', str(apk), 'AndroidManifest.xml')
    assert 'android:icon' in manifest
    assert not re.search(r'E: (?:uses-permission|permission|activity|service|receiver|provider)\b', manifest)
    assert 'E: meta-data' not in manifest, 'Legacy manifest metadata is forbidden'
    print('PASS real modern entry DEX, API 102 static metadata, exact LX + system scopes and version 0.3.0 (5).')
    print('PASS APK has no legacy assets/metadata, permissions, components, test/API classes or native/JS payload.')
    print('APK_SHA256', hashlib.sha256(apk.read_bytes()).hexdigest())
    print('DEFINED_CLASSES', ', '.join(sorted(definitions)))


if __name__ == '__main__':
    main()
