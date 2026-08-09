#!/usr/bin/env python3
from __future__ import annotations
import re
import struct
import sys
import zipfile
from pathlib import Path
import yaml

ROOT = Path(__file__).resolve().parents[1]
DEFAULT = ROOT / 'src/main/resources/defaults/breedconsumecontrol-1.8.5.yml'

class UniqueKeyLoader(yaml.SafeLoader):
    pass

def construct_mapping(loader, node, deep=False):
    mapping = {}
    for key_node, value_node in node.value:
        key = loader.construct_object(key_node, deep=deep)
        if key in mapping:
            raise AssertionError(f'duplicate YAML key: {key!r} at line {key_node.start_mark.line + 1}')
        mapping[key] = loader.construct_object(value_node, deep=deep)
    return mapping

UniqueKeyLoader.add_constructor(
    yaml.resolver.BaseResolver.DEFAULT_MAPPING_TAG,
    construct_mapping,
)

text = DEFAULT.read_text(encoding='utf-8')
data = yaml.load(text, Loader=UniqueKeyLoader)
assert data['config-version'] == 3

for forbidden in (
    'synthesis-upgrade-mode:',
    'force-exact-result-v:',
    'nature-inheritance-mode:',
    'nature-lock-require-everstone:',
    'force-nature-from-everstone:',
    'force-iv-from-power-item:',
    'show-egg-ivs:',
    'consume-parents-on-egg-created:',
    'consume-zero-v-parents:',
    'parent-consume-delay-ticks:',
    'require-exactly-one-power-item:',
):
    assert forbidden not in text, f'legacy/duplicate key remains in canonical config: {forbidden}'

leaves = set()
def walk(value, prefix=''):
    if isinstance(value, dict):
        for key, child in value.items():
            walk(child, f'{prefix}.{key}' if prefix else str(key))
    else:
        leaves.add(prefix)
walk(data)

runtime = (ROOT / 'src/main/java/cn/licry/breedconsume/config/RuntimeSettings.java').read_text(encoding='utf-8')
paths = set(re.findall(r'config\.(?:getBoolean|getInt|getString|contains)\("([^"]+)"', runtime))
missing = sorted(paths - leaves)
assert not missing, f'RuntimeSettings reads keys missing from canonical config: {missing}'

jar_path = Path(sys.argv[1]) if len(sys.argv) > 1 else ROOT / 'BreedConsumeControl-1.8.5.jar'
if jar_path.exists():
    with zipfile.ZipFile(jar_path) as jar:
        names = jar.namelist()
        assert len(names) == len(set(names)), 'duplicate ZIP/JAR entries detected'
        assert names.count('defaults/breedconsumecontrol-1.8.5.yml') == 1
        assert names.count('plugin.yml') == 1
        assert 'config.yml' not in names
        forbidden_prefixes = ('org/bukkit/', 'net/minecraft/', 'net/minecraftforge/', 'catserver/api/', 'com/pixelmonmod/')
        bundled_dependencies = [name for name in names if name.startswith(forbidden_prefixes)]
        assert not bundled_dependencies, f'compileOnly classes were packaged: {bundled_dependencies[:10]}'
        clazz = jar.read('cn/licry/breedconsume/BreedConsumePlugin.class')
        assert clazz[:4] == b'\xca\xfe\xba\xbe'
        assert struct.unpack('>H', clazz[6:8])[0] == 52, 'class is not Java 8 bytecode'

print('CONFIG_SCHEMA_AND_JAR_AUDIT_OK')
print(f'canonical_leaf_keys={len(leaves)} runtime_paths={len(paths)}')
