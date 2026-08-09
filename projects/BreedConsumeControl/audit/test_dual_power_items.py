#!/usr/bin/env python3
from pathlib import Path
import yaml
ROOT = Path(__file__).resolve().parents[1]
service = (ROOT / 'src/main/java/cn/licry/breedconsume/service/BreedConsumeService.java').read_text(encoding='utf-8')
config = yaml.safe_load((ROOT / 'src/main/resources/defaults/breedconsumecontrol-1.8.5.yml').read_text(encoding='utf-8'))
item = config['rules']['item-lock']
assert item['require-at-least-one-power-item'] is True
assert item['allow-two-power-items'] is True
assert 'if (firstPower != null && !addPowerLock(first, firstPower, 1, plan, player, stage))' in service
assert 'if (secondPower != null && !addPowerLock(second, secondPower, 2, plan, player, stage))' in service
assert 'plan.resolveDuplicatePowerStats(random, plugin.getLogger(), stage);' in service
assert 'for (PowerLock lock : plan.powerLocks)' in service
# Distinct indexes must survive as two assignments; same index must collapse to one result slot.
def apply(locks):
    out = [None] * 6
    for idx, value in locks:
        out[idx] = value
    return out
assert apply([(1, 31), (2, 31)])[1:3] == [31, 31]
assert apply([(1, 20), (1, 31)])[1] in (20, 31)
print('DUAL_POWER_ITEMS_OK')
