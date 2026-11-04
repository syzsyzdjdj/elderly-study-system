"""Build a new public source archive; never modifies Git or original media."""
import argparse
import hashlib
import json
from pathlib import Path
import zipfile

from audit_git import ROOT, scan_source_tree, source_files


def package(output):
    report = scan_source_tree()
    if report['findings']:
        raise ValueError('Public source audit failed; run audit_git.py --source-tree for path-only details')
    output = output.resolve()
    output.parent.mkdir(parents=True, exist_ok=True)
    if output.exists(): raise ValueError('Output already exists; choose another filename')
    files = list(source_files())
    if output in [p.resolve() for p in files]: raise ValueError('Output must be outside source allow-list')
    partial = output.with_suffix(output.suffix + '.partial')
    with zipfile.ZipFile(partial, 'w', zipfile.ZIP_DEFLATED) as archive:
        for file in files:
            archive.write(file, file.relative_to(ROOT).as_posix())
    partial.replace(output)
    digest = hashlib.sha256(output.read_bytes()).hexdigest()
    output.with_suffix(output.suffix + '.sha256').write_text(digest + '  ' + output.name + '\n', encoding='utf-8')
    print(json.dumps({'archive': str(output), 'files': len(files), 'sha256': digest,
                      'historyIncluded': False, 'unverifiedMediaIncluded': False}, ensure_ascii=False))


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--output', type=Path, default=ROOT / 'work/public/elderly-study-source.zip')
    args = parser.parse_args()
    package(args.output)
