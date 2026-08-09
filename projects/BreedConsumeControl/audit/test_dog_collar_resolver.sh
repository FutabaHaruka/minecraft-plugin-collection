#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
PIXELMON_JAR="${1:?Usage: $0 /path/to/pixelmon-8.4.2.jar}"
"$ROOT/build-local.sh" "$PIXELMON_JAR" >/dev/null
TEST="$ROOT/build/TestDogCollarResolver.java"
mkdir -p "$ROOT/build/test-classes"
cat > "$TEST" <<'JAVA'
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import com.pixelmonmod.pixelmon.entities.pixelmon.stats.StatsType;

public final class TestDogCollarResolver {
    public static final class FakeType {
        public StatsType statAffected;
        FakeType(StatsType stat) { this.statAffected = stat; }
    }
    public static final class FakeEVAdjusting {
        public FakeType type;
        FakeEVAdjusting(StatsType stat) { this.type = new FakeType(stat); }
    }
    public static void main(String[] args) throws Exception {
        Class<?> service = Class.forName("cn.licry.breedconsume.service.BreedConsumeService");
        Method resolver = service.getDeclaredMethod("powerItemFromEvAdjusting", Object.class, String.class);
        resolver.setAccessible(true);
        StatsType[] stats = {StatsType.HP, StatsType.Attack, StatsType.Defence,
                StatsType.SpecialAttack, StatsType.SpecialDefence, StatsType.Speed, StatsType.None};
        int[] expected = {0, 1, 2, 3, 4, 5, -1};
        for (int i = 0; i < stats.length; i++) {
            Object result = resolver.invoke(null, new FakeEVAdjusting(stats[i]), "test");
            if (expected[i] < 0) {
                if (result != null) throw new AssertionError("Macho Brace/None must not resolve as a breeding Power Item");
                continue;
            }
            if (result == null) throw new AssertionError("No result for " + stats[i]);
            Field index = result.getClass().getDeclaredField("statIndex");
            index.setAccessible(true);
            if (index.getInt(result) != expected[i]) {
                throw new AssertionError(stats[i] + " mapped to wrong IV index");
            }
        }
        System.out.println("DOG_COLLAR_RESOLVER_OK");
    }
}
JAVA
javac --release 8 -encoding UTF-8 \
  -cp "$ROOT/build/classes:$ROOT/build/stubs:$PIXELMON_JAR" \
  -d "$ROOT/build/test-classes" "$TEST"
java -cp "$ROOT/build/test-classes:$ROOT/build/classes:$ROOT/build/stubs:$PIXELMON_JAR" TestDogCollarResolver
