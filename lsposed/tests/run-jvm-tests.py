#!/usr/bin/env python3
"""Run the production modern module against test doubles and compile with real API 102.

The compatibility compilation excludes the modern API doubles and uses the actual
compile-only jar. Android/framework doubles remain test-only and never enter the APK.
"""
import argparse
from pathlib import Path
import shutil
import subprocess
import tempfile


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--libxposed-api', '--xposed-api', dest='libxposed_api', type=Path,
                        help='Real modern API jar (default: app/libs/libxposed-api-102.jar)')
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
        subprocess.run([java, '-cp', str(behavior), 'LxModuleTest'], check=True)
        api = (args.libxposed_api or module / 'app/libs/libxposed-api-102.jar').resolve(strict=True)
        compatibility = build / 'api-compatibility'
        compatibility.mkdir()
        subprocess.run([javac, '--release', '8', '-encoding', 'UTF-8', '-cp', str(api), '-d', str(compatibility),
                        *map(str, sorted((tests / 'java/android').rglob('*.java'))),
                        *map(str, production)], check=True)
        print('PASS production module compiles against the real libxposed API 102 jar without API doubles.', flush=True)


if __name__ == '__main__':
    main()
