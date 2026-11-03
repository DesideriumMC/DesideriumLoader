package dev.desiderium.diagnostics;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;
import org.objectweb.asm.*;

/** Local runtime instrumentation; this JAR contains no upstream launcher bytecode. */
public final class DiagnosticsAgent {
    public static void premain(String options, Instrumentation instrumentation) {
        instrumentation.addTransformer(new ClassFileTransformer() {
            public byte[] transform(ClassLoader loader, String name, Class<?> type,
                                    ProtectionDomain domain, byte[] bytes) {
                if (!"net/minecraft/launchwrapper/LaunchClassLoader".equals(name)) return null;
                final int[] hooks = {0};
                ClassReader reader = new ClassReader(bytes);
                ClassWriter writer = new ClassWriter(reader, 0);
                reader.accept(new ClassVisitor(Opcodes.ASM4, writer) {
                    public MethodVisitor visitMethod(int access, String method, String descriptor, String signature, String[] exceptions) {
                        return new MethodVisitor(Opcodes.ASM4, super.visitMethod(access, method, descriptor, signature, exceptions)) {
                            public void visitMethodInsn(int opcode, String owner, String method, String descriptor) {
                                if (opcode == Opcodes.INVOKEINTERFACE && owner.equals("net/minecraft/launchwrapper/IClassTransformer")
                                    && method.equals("transform") && descriptor.equals("(Ljava/lang/String;Ljava/lang/String;[B)[B")) {
                                    hooks[0]++;
                                    super.visitMethodInsn(Opcodes.INVOKESTATIC, "net/minecraft/launchwrapper/DesideriumDiagnostics",
                                        "transform", "(Lnet/minecraft/launchwrapper/IClassTransformer;Ljava/lang/String;Ljava/lang/String;[B)[B");
                                } else super.visitMethodInsn(opcode, owner, method, descriptor);
                            }
                        };
                    }
                }, 0);
                if (hooks[0] != 2) {
                    System.err.println("[DESIDERIUM-L5] Unexpected official LaunchWrapper diagnostic hook count=" + hooks[0]);
                    return new byte[0]; // Fail class definition rather than silently omit diagnostics.
                }
                System.out.println("[DESIDERIUM-L5] local LaunchWrapper diagnostic hooks=" + hooks[0]);
                return writer.toByteArray();
            }
        });
    }
}
