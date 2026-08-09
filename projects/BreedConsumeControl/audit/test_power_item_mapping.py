from pathlib import Path

source = Path(__file__).parents[1] / 'src/main/java/cn/licry/breedconsume/service/BreedConsumeService.java'
text = source.read_text(encoding='utf-8')
expected = {
    'powerWeight': 'StatsType.HP',
    'powerBracer': 'StatsType.Attack',
    'powerBelt': 'StatsType.Defence',
    'powerLens': 'StatsType.SpecialAttack',
    'powerBand': 'StatsType.SpecialDefence',
    'powerAnklet': 'StatsType.Speed',
}
for field, stat in expected.items():
    assert f'matchesPixelmonHeldItem(item, "{field}")' in text
    line = next(line for line in text.splitlines() if f'matchesPixelmonHeldItem(item, "{field}")' in line)
    assert stat in line, (field, line)
assert 'egg.getIVs().setStat(lock.statType, expected);' in text
assert 'applyPowerItemLock(event.getEgg(), plan, targetIvs, "MakeEgg")' in text
assert 'applyPowerItemLock(committedEgg, plan, targetIvs, "PostCommit")' in text
print('power item mapping and two-stage verification: PASS')

assert 'plan.powerLocks.add(new PowerLock(parentIndex, item, lockedIv));' in text
assert 'for (PowerLock lock : plan.powerLocks)' in text
assert 'allow-two-power-items' not in text  # service consumes validated runtime setting, not raw YAML
print('dual Power Item lock plan: PASS')
