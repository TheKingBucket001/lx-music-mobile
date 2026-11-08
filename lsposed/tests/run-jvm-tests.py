#!/usr/bin/env python3
"""Compile the production Hook against test doubles and run its callback behavior on the JVM.

The optional --xposed-api check separately compiles against the real Xposed API jar.
Android/Xposed doubles live only under tests/ and never enter the app source set.
"""
import argparse
from pathlib import Path
import shutil
import subprocess
import tempfile


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--xposed-api', type=Path, help='Optional real Xposed API jar for a separate compatibility compilation')
    args = parser.parse_args()
    tests = Path(__file__).resolve().parent
    module = tests.parent
    production = sorted((module / 'app/src/main/java').rglob('*.java'))
    javac = shutil.which('javac')
    java = shutil.which('java')
    if not javac or not java:
        raise SystemExit('A JDK with javac and java on PATH is required.')
    with tempfile.TemporaryDirectory(prefix='jvm-test-', dir=tests) as output:
        build = Path(output)
        behavior = build / 'behavior'
        behavior.mkdir()
        sources = [*production, *sorted((tests / 'java').rglob('*.java'))]
        subprocess.run([javac, '--release', '8', '-encoding', 'UTF-8', '-d', str(behavior), *map(str, sources)], check=True)
        subprocess.run([java, '-cp', str(behavior), 'RemoteStopHookTest'], check=True)
        subprocess.run([java, '-cp', str(behavior), 'MediaServiceTypeHookTest'], check=True)
        if args.xposed_api:
            api = args.xposed_api.resolve(strict=True)
            compatibility = build / 'api-compatibility'
            compatibility.mkdir()
            subprocess.run([javac, '--release', '8', '-encoding', 'UTF-8', '-cp', str(api), '-d', str(compatibility),
                            *map(str, sorted((tests / 'java/android').rglob('*.java'))),
                            *map(str, production)], check=True)
            print('PASS production Hook compiles against the supplied real Xposed API jar.', flush=True)


if __name__ == '__main__':
    main()
