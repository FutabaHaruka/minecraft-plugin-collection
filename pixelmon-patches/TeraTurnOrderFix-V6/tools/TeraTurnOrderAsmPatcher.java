import java.nio.file.*;
import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;

public final class TeraTurnOrderAsmPatcher {
    private static final String BC = "com/pixelmonmod/pixelmon/battles/controller/BattleControllerBase";
    private static final String FIX = "com/pixelmonmod/pixelmon/battles/controller/TeraTurnOrderFix";

    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("usage: <in class> <out class>");
        ClassNode node = new ClassNode();
        new ClassReader(Files.readAllBytes(Paths.get(args[0]))).accept(node, 0);
        if (!BC.equals(node.name)) throw new IllegalStateException("unexpected class: " + node.name);

        int inserted = 0;
        for (MethodNode m : node.methods) {
            if (!"update".equals(m.name) || !"()V".equals(m.desc)) continue;

            // Remove an earlier V6 hook if the patcher is re-run.
            for (AbstractInsnNode n = m.instructions.getFirst(); n != null; ) {
                AbstractInsnNode next = n.getNext();
                if (n instanceof MethodInsnNode) {
                    MethodInsnNode mi = (MethodInsnNode)n;
                    if (mi.getOpcode() == Opcodes.INVOKESTATIC && FIX.equals(mi.owner)
                            && "activateBeforeStats".equals(mi.name)
                            && ("(L" + BC + ";)V").equals(mi.desc)) {
                        AbstractInsnNode p = previousExecutable(n);
                        if (p != null && p.getOpcode() == Opcodes.ALOAD && p instanceof VarInsnNode
                                && ((VarInsnNode)p).var == 0) {
                            m.instructions.remove(p);
                        }
                        m.instructions.remove(n);
                    }
                }
                n = next;
            }

            MethodInsnNode target = null;
            for (AbstractInsnNode n = m.instructions.getFirst(); n != null; n = n.getNext()) {
                if (!(n instanceof MethodInsnNode)) continue;
                MethodInsnNode mi = (MethodInsnNode)n;
                if (mi.getOpcode() == Opcodes.INVOKEVIRTUAL && BC.equals(mi.owner)
                        && "modifyStats".equals(mi.name) && "()V".equals(mi.desc)) {
                    if (target != null) throw new IllegalStateException("multiple modifyStats() calls in update");
                    target = mi;
                }
            }
            if (target == null) throw new IllegalStateException("modifyStats() call not found in update");

            // At this point the stack contains the receiver for modifyStats (ALOAD 0).
            // Insert ALOAD 0 + static hook before that receiver load, leaving the original
            // ALOAD 0 / invokevirtual modifyStats sequence intact.
            AbstractInsnNode receiver = previousExecutable(target);
            if (!(receiver instanceof VarInsnNode) || receiver.getOpcode() != Opcodes.ALOAD
                    || ((VarInsnNode)receiver).var != 0) {
                throw new IllegalStateException("unexpected modifyStats receiver sequence");
            }
            InsnList hook = new InsnList();
            hook.add(new VarInsnNode(Opcodes.ALOAD, 0));
            hook.add(new MethodInsnNode(Opcodes.INVOKESTATIC, FIX, "activateBeforeStats",
                    "(L" + BC + ";)V", false));
            m.instructions.insertBefore(receiver, hook);
            inserted++;
        }
        if (inserted != 1) throw new IllegalStateException("expected one update hook, got " + inserted);

        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(cw);
        Path out = Paths.get(args[1]);
        Files.createDirectories(out.getParent());
        Files.write(out, cw.toByteArray());
        System.out.println("inserted=" + inserted + " class=" + node.name);
    }

    private static AbstractInsnNode previousExecutable(AbstractInsnNode n) {
        for (AbstractInsnNode p = n.getPrevious(); p != null; p = p.getPrevious()) {
            if (p.getOpcode() >= 0) return p;
        }
        return null;
    }
}
