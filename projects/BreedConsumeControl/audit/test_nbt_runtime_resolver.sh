#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
PIXELMON_JAR="${1:?Usage: $0 /path/to/pixelmon-8.4.2.jar}"
if [ ! -f "$ROOT/build/classes/cn/licry/breedconsume/service/BreedConsumeService.class" ]; then
  "$ROOT/build-local.sh" "$PIXELMON_JAR"
fi
T="$ROOT/build/nbt-runtime-test"
rm -rf "$T"
mkdir -p "$T/src/spigot" "$T/src/obf" "$T/src/test" "$T/classes"
cat > "$T/src/spigot/NBTTagCompound.java" <<'JAVA'
package spigot;
import java.util.HashMap;
import java.util.Map;
public class NBTTagCompound {
    private final Map<String,Object> data = new HashMap<String,Object>();
    public void setInt(String key, int value) { data.put(key, value); }
    public int getInt(String key) { Object v=data.get(key); return v instanceof Number ? ((Number)v).intValue() : 0; }
    public void remove(String key) { data.remove(key); }
}
JAVA
cat > "$T/src/obf/NBTTagCompound.java" <<'JAVA'
package obf;
import java.util.HashMap;
import java.util.Map;
public class NBTTagCompound {
    private final Map<String,Object> data = new HashMap<String,Object>();
    public void a(String key, int value) { data.put(key, value); }
    public int b(String key) { Object v=data.get(key); return v instanceof Number ? ((Number)v).intValue() : 0; }
    public void c(String key) { data.remove(key); }
}
JAVA
cat > "$T/src/test/NbtResolverTest.java" <<'JAVA'
package test;
import java.lang.reflect.Method;
public final class NbtResolverTest {
    private static Object call(Object target, String[] names, Object... args) throws Exception {
        Class<?> service = Class.forName("cn.licry.breedconsume.service.BreedConsumeService");
        Method invoke = service.getDeclaredMethod("invoke", Object.class, String[].class, Object[].class);
        invoke.setAccessible(true);
        return invoke.invoke(null, target, names, args);
    }
    private static void test(Object tag) throws Exception {
        call(tag, new String[]{"setInteger","setInt","putInt","func_74768_a"}, "Attack", 31);
        Object value = call(tag, new String[]{"getInteger","getInt","func_74762_e"}, "Attack");
        if (!Integer.valueOf(31).equals(value)) throw new AssertionError("write/read failed: " + value);
        call(tag, new String[]{"removeTag","remove","func_82580_o"}, "Attack");
        value = call(tag, new String[]{"getInteger","getInt","func_74762_e"}, "Attack");
        if (!Integer.valueOf(0).equals(value)) throw new AssertionError("remove failed: " + value);
    }
    public static void main(String[] args) throws Exception {
        test(new spigot.NBTTagCompound());
        test(new obf.NBTTagCompound());
        System.out.println("NBT resolver tests passed");
    }
}
JAVA
javac --release 8 -encoding UTF-8 \
  -cp "$ROOT/build/classes:$ROOT/build/stubs:$PIXELMON_JAR" \
  -d "$T/classes" $(find "$T/src" -name '*.java')
java -cp "$T/classes:$ROOT/build/classes:$ROOT/build/stubs:$PIXELMON_JAR" test.NbtResolverTest
