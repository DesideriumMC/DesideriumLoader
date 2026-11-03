package net.minecraft.launchwrapper;

/** Optional observation only: never replaces transformer objects or changes their order. */
final class DesideriumDiagnostics {
    static final boolean ENABLED = !"false".equals(System.getProperty("desiderium.diagnostics", "true"));
    private static final java.util.Map<String, long[]> totals = new java.util.TreeMap<String, long[]>();
    static {
        if (ENABLED) Runtime.getRuntime().addShutdownHook(new Thread("Desiderium diagnostics") {
            public void run() {
                java.io.PrintWriter output;
                try {
                    output = new java.io.PrintWriter(new java.io.FileOutputStream(
                        System.getProperty("desiderium.diagnostics.file", "desiderium-diagnostics.log")));
                } catch (java.io.IOException e) { throw new IllegalStateException("Cannot write diagnostic summary", e); }
                try {
                synchronized (totals) {
                    for (java.util.Map.Entry<String, long[]> entry : totals.entrySet()) {
                        long[] v = entry.getValue();
                        output.println("[DESIDERIUM-L2] transform-summary transformer=" + entry.getKey()
                            + " calls=" + v[0] + " nanos=" + v[1] + " failures=" + v[2]);
                    }
                }
                java.lang.management.MemoryUsage heap = java.lang.management.ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
                output.println("[DESIDERIUM-L2] heap used=" + heap.getUsed() + " committed=" + heap.getCommitted() + " max=" + heap.getMax());
                } finally { output.close(); }
            }
        });
    }
    static byte[] transform(IClassTransformer transformer, String name, String mapped, byte[] bytes) {
        if (!ENABLED) return transformer.transform(name, mapped, bytes);
        long started = System.nanoTime();
        boolean failed = false;
        try { return transformer.transform(name, mapped, bytes); }
        catch (RuntimeException e) { failed = true; report(transformer, name, mapped, e); throw e; }
        catch (Error e) { failed = true; report(transformer, name, mapped, e); throw e; }
        finally {
            synchronized (totals) {
                String key = transformer.getClass().getName();
                long[] v = totals.get(key);
                if (v == null) { v = new long[3]; totals.put(key, v); }
                v[0]++; v[1] += System.nanoTime() - started; if (failed) v[2]++;
            }
        }
    }
    private static void report(IClassTransformer transformer, String name, String mapped, Throwable error) {
        java.security.CodeSource source = transformer.getClass().getProtectionDomain().getCodeSource();
        System.err.println("[DESIDERIUM-L2] transform-failure stage=transform class=" + name
            + " mapped=" + mapped + " transformer=" + transformer.getClass().getName()
            + " source=" + (source == null ? "unknown" : source.getLocation()) + " cause=" + error);
    }
}
