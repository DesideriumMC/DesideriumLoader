package dev.desiderium.loader;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import net.minecraft.launchwrapper.ITweaker;
import net.minecraft.launchwrapper.Launch;
import net.minecraft.launchwrapper.LaunchClassLoader;
import org.spongepowered.asm.launch.MixinTweaker;
import org.spongepowered.asm.mixin.Mixins;

/** Loader-owned Mixin lifecycle; scheduled by CoreModManager on both launch sides. */
public final class LoaderMixinTweaker implements ITweaker {
    private final MixinTweaker delegate;
    private final java.util.Set<String> configs = new java.util.LinkedHashSet<String>();

    public LoaderMixinTweaker() {
        if (Launch.blackboard.put("desiderium.loader.mixin.owner", "0.8.7") != null) {
            throw new IllegalStateException("Duplicate Loader Mixin initialization");
        }
        try {
            java.util.Enumeration<java.net.URL> runtimes = LoaderMixinTweaker.class.getClassLoader().getResources("org/spongepowered/asm/launch/MixinTweaker.class");
            java.util.Set<String> locations = new java.util.LinkedHashSet<String>();
            while (runtimes.hasMoreElements()) locations.add(runtimes.nextElement().toString());
            if (locations.size() != 1) throw new IllegalStateException("Expected one shared Mixin runtime, found " + locations);
        } catch (java.io.IOException e) { throw new IllegalStateException("Cannot inspect Mixin runtimes", e); }
        Launch.classLoader.addClassLoaderExclusion("dev.desiderium.internal.");
        org.apache.logging.log4j.core.config.Configurator.setRootLevel(org.apache.logging.log4j.Level.INFO);
        delegate = new MixinTweaker();
        System.out.println("[DESIDERIUM-L1] Loader Mixin 0.8.7 initialized");
    }

    public void acceptOptions(List<String> args, File gameDir, File assetsDir, String profile) {
        long discoveryStarted = System.nanoTime();
        delegate.acceptOptions(args, gameDir, assetsDir, profile);
        String developmentConfigs = System.getProperty("desiderium.mixin.configs", "");
        addConfigs(developmentConfigs);
        File mods = new File(gameDir == null ? new File(".") : gameDir, "mods");
        java.util.List<File> jars = new java.util.ArrayList<File>();
        File[] top = mods.listFiles();
        File[] versioned = new File(mods, "1.6.4").listFiles();
        if (top != null) jars.addAll(Arrays.asList(top));
        if (versioned != null) jars.addAll(Arrays.asList(versioned));
        java.util.Collections.sort(jars);
        for (File file : jars) {
            if (!file.isFile() || !file.getName().endsWith(".jar")) continue;
            try {
                JarFile jar = new JarFile(file);
                try {
                    Manifest manifest = jar.getManifest();
                    String tweak = manifest == null ? null : manifest.getMainAttributes().getValue("TweakClass");
                    if ("dev.desiderium.bootstrap.DesideriumTweaker".equals(tweak) || "org.spongepowered.asm.launch.MixinTweaker".equals(tweak)) {
                        throw new IllegalStateException("Remove mod-bundled Mixin runtime; Loader provides Mixin: " + file);
                    }
                    String configs = manifest == null ? null : manifest.getMainAttributes().getValue("DesideriumMixinConfigs");
                    if (configs != null) {
                        Launch.classLoader.addURL(file.getCanonicalFile().toURI().toURL());
                        // It is now on the classpath for early config loading,
                        // but FML must discover its @Mod only once, in mods/.
                        Class<?> manager = Class.forName("cpw.mods.fml.relauncher.CoreModManager", true, Launch.classLoader);
                        @SuppressWarnings("unchecked")
                        List<String> reparseable = (List<String>) manager.getMethod("getReparseableCoremods").invoke(null);
                        if (!reparseable.contains(file.getName())) reparseable.add(file.getName());
                        System.out.println("[DESIDERIUM-L2] discovery source=" + file.getAbsolutePath());
                        addConfigs(configs);
                    }
                } finally { jar.close(); }
            } catch (Exception e) {
                throw new IllegalStateException("Cannot discover Mixin configs in " + file, e);
            }
        }
        System.out.println("[DESIDERIUM-L2] discovery nanos=" + (System.nanoTime() - discoveryStarted) + " configs=" + configs.size());
    }

    private void addConfigs(String declarations) {
        for (String entry : declarations.split(",")) {
            String config = entry.trim();
            if (config.length() == 0 || !configs.add(config)) continue;
            if (Launch.classLoader.getResource(config) == null) {
                throw new IllegalStateException("Missing declared Mixin config: " + config);
            }
            Mixins.addConfiguration(config);
            System.out.println("[DESIDERIUM-L1] config registered=" + config);
        }
    }

    public void injectIntoClassLoader(LaunchClassLoader loader) {
        // Explicit development --tweakClass does not traverse CoreModManager's
        // manifest discovery path, so register the same scanner exclusion here.
        try {
            File self = new File(LoaderMixinTweaker.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            Class<?> manager = Class.forName("cpw.mods.fml.relauncher.CoreModManager", true, loader);
            @SuppressWarnings("unchecked")
            List<String> ignored = (List<String>) manager.getMethod("getLoadedCoremods").invoke(null);
            if (!ignored.contains(self.getName())) ignored.add(self.getName());
        } catch (Exception e) {
            throw new IllegalStateException("Cannot exclude shared runtime from Forge ASM4 discovery", e);
        }
        delegate.injectIntoClassLoader(loader);
        System.out.println("[DESIDERIUM-P1] transformer chain=" + loader.getTransformers());
    }
    public String getLaunchTarget() { return delegate.getLaunchTarget(); }
    public String[] getLaunchArguments() { return delegate.getLaunchArguments(); }
}
