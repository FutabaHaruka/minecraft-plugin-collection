#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
PIXELMON_JAR="${1:?Usage: $0 /path/to/pixelmon-8.4.2.jar}"
if [ ! -f "$ROOT/build/classes/cn/licry/breedconsume/service/BreedConsumeService.class" ]; then
  "$ROOT/build-local.sh" "$PIXELMON_JAR" >/dev/null
fi
T="$ROOT/build/dual-power-plan-test"
rm -rf "$T" && mkdir -p "$T"
cat > "$T/TestDualPowerPlan.java" <<'JAVA'
import java.lang.reflect.*;
import java.util.*;
import java.util.logging.Logger;
import com.pixelmonmod.pixelmon.entities.pixelmon.stats.StatsType;

public final class TestDualPowerPlan {
  static Object item(String id, String name, StatsType stat) throws Exception {
    Class<?> c=Class.forName("cn.licry.breedconsume.service.BreedConsumeService$PowerItem");
    Constructor<?> k=c.getDeclaredConstructor(String.class,String.class,StatsType.class,String.class);
    k.setAccessible(true); return k.newInstance(id,name,stat,"test");
  }
  static Object lock(int parent,Object item,int iv) throws Exception {
    Class<?> c=Class.forName("cn.licry.breedconsume.service.BreedConsumeService$PowerLock");
    Constructor<?> k=c.getDeclaredConstructors()[0];
    for (Constructor<?> x:c.getDeclaredConstructors()) if (x.getParameterCount()==3) k=x;
    k.setAccessible(true); return k.newInstance(parent,item,iv);
  }
  static Object plan() throws Exception {
    Class<?> c=Class.forName("cn.licry.breedconsume.service.BreedConsumeService$SynthesisPlan");
    Constructor<?> k=c.getDeclaredConstructor(int.class,int.class,int.class,int.class,boolean.class,boolean.class);
    k.setAccessible(true); return k.newInstance(2,2,2,3,false,false);
  }
  @SuppressWarnings("unchecked")
  static List<Object> list(Object plan) throws Exception {
    Field f=plan.getClass().getDeclaredField("powerLocks"); f.setAccessible(true); return (List<Object>)f.get(plan);
  }
  static void resolve(Object plan) throws Exception {
    Method m=plan.getClass().getDeclaredMethod("resolveDuplicatePowerStats",Random.class,Logger.class,String.class);
    m.setAccessible(true); m.invoke(plan,new Random(1),Logger.getLogger("test"),"audit");
  }
  public static void main(String[] args) throws Exception {
    Object attack=item("a","力量护腕",StatsType.Attack);
    Object defence=item("d","力量腰带",StatsType.Defence);
    Object p=plan(); list(p).add(lock(1,attack,31)); list(p).add(lock(2,defence,31)); resolve(p);
    if(list(p).size()!=2) throw new AssertionError("distinct stats did not keep two locks");
    p=plan(); list(p).add(lock(1,attack,31)); list(p).add(lock(2,attack,31)); resolve(p);
    if(list(p).size()!=1) throw new AssertionError("same stat was not merged");
    System.out.println("DUAL_POWER_PLAN_BYTECODE_OK");
  }
}
JAVA
javac --release 8 -encoding UTF-8 -cp "$ROOT/build/classes:$ROOT/build/stubs:$PIXELMON_JAR" -d "$T" "$T/TestDualPowerPlan.java"
java -cp "$T:$ROOT/build/classes:$ROOT/build/stubs:$PIXELMON_JAR" TestDualPowerPlan
