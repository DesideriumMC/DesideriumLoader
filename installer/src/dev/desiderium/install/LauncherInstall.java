package dev.desiderium.install;

import java.io.*;
import java.nio.channels.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.*;

/** Standard launcher version export. Launcher owns authentication and the game JVM. */
final class LauncherInstall {
    static Map<String,Object> obj(Object... pairs) {
        Map<String,Object> out = new LinkedHashMap<String,Object>();
        for (int i=0;i<pairs.length;i+=2) out.put((String)pairs[i],pairs[i+1]);
        return out;
    }
    static String json(Object value) {
        if (value instanceof Map) {
            List<String> fields=new ArrayList<String>();
            for (Map.Entry<?,?> e:((Map<?,?>)value).entrySet()) fields.add(json(e.getKey())+":"+json(e.getValue()));
            return "{"+String.join(",",fields)+"}";
        }
        if (value instanceof List) {
            List<String> values=new ArrayList<String>();
            for (Object v:(List<?>)value) values.add(json(v));
            return "["+String.join(",",values)+"]";
        }
        if (value instanceof Number || value instanceof Boolean) return value.toString();
        StringBuilder s=new StringBuilder("\"");
        for (char c:value.toString().toCharArray()) {
            if (c=='"' || c=='\\') s.append('\\').append(c);
            else if (c<32) s.append(String.format("\\u%04x",(int)c));
            else s.append(c);
        }
        return s.append('"').toString();
    }
    static String sha1(Path path) throws Exception {
        MessageDigest digest=MessageDigest.getInstance("SHA-1");
        try (InputStream in=Files.newInputStream(path)) {
            byte[] b=new byte[65536]; int n;
            while ((n=in.read(b))!=-1) digest.update(b,0,n);
        }
        return Main.hex(digest.digest());
    }
    static void copy(Path from, Path to) throws Exception {
        Main.noLinks(from); Main.noLinks(to);
        Files.createDirectories(to.getParent());
        if (Files.exists(to)) { Main.checked(to,Main.hash(from),from.toString()); return; }
        Path temp=Files.createTempFile(to.getParent(),"desiderium-",".part");
        try {
            Files.copy(from,temp,StandardCopyOption.REPLACE_EXISTING);
            Main.checked(temp,Main.hash(from),from.toString());
            Files.move(temp,to,StandardCopyOption.ATOMIC_MOVE);
        } finally { Files.deleteIfExists(temp); }
    }
    static void removeTree(Path dir) throws Exception {
        if (!Files.exists(dir)) return;
        Main.noLinks(dir);
        try (java.util.stream.Stream<Path> stream=Files.walk(dir)) {
            Iterator<Path> it=stream.sorted(Comparator.reverseOrder()).iterator();
            while (it.hasNext()) { Path p=it.next(); Main.noLinks(p); Files.delete(p); }
        }
    }
    static void install() throws Exception {
        if (Main.options.containsKey("--instance") || Main.options.containsKey("--side") || Main.options.containsKey("--username"))
            throw new IOException("install-hmcl accepts --minecraft, --version, --cache and --offline only");
        String version=Main.required("--version");
        Properties meta=Main.metadata(version,"client"); // Platform and catalog validation before writes.
        Path root=Paths.get(Main.required("--minecraft")).toAbsolutePath().normalize();
        Main.noLinks(root); Files.createDirectories(root);
        if (!Main.options.containsKey("--cache")) Main.options.put("--cache",Main.safe(root,".desiderium-cache").toString());
        Path lock=Main.safe(root,".desiderium-install.lock");
        try (FileChannel ch=FileChannel.open(lock,StandardOpenOption.CREATE,StandardOpenOption.WRITE);
             FileLock guard=ch.tryLock()) {
            if (guard==null) throw new IOException("Another launcher installation is in progress");
            String id="desiderium-"+version;
            Path versions=Main.safe(root,"versions"); Files.createDirectories(versions);
            Path target=Main.safe(versions,id);
            if (Files.exists(target)) throw new IOException("Refusing to overwrite existing launcher version: "+target);
            Path work=Files.createTempDirectory(root,".desiderium-install-");
            Path stage=Files.createTempDirectory(versions,".desiderium-staging-");
            try {
                Main.instance=work;
                Main.managed=work.resolve(".desiderium"); Files.createDirectories(Main.managed);
                Main.options.put("--side","client");
                Main.install();
                Path release=Main.managed.resolve("versions/"+version);
                Main.verify(meta,release);
                export(root,stage,release,meta,id,version);
                Files.move(stage,target,StandardCopyOption.ATOMIC_MOVE);
                System.out.println("HMCL VERSION INSTALLED "+id+" minecraft="+root);
                System.out.println("Select this game directory in HMCL, refresh versions, and select Java 8 x64. Accounts are managed by the launcher.");
            } finally { removeTree(stage); removeTree(work); }
        }
    }
    static void export(Path root, Path stage, Path release, Properties meta, String id, String version) throws Exception {
        Properties base=new Properties();
        try (InputStream in=Main.class.getResourceAsStream("/launcher/base.properties")) {
            if (in==null) throw new IOException("Installer has no launcher metadata; rebuild the distribution");
            base.load(in);
        }
        List<Object> libraries=new ArrayList<Object>();
        List<Object> jvm=new ArrayList<Object>(Arrays.<Object>asList("-Djava.library.path=${natives_directory}","-Dfml.ignoreInvalidMinecraftCertificates=true","-cp","${classpath}"));
        String os=meta.getProperty("platform").split("-")[0];
        // Locally extracted restricted runtime data is packaged only on the user's machine.
        Path resources=stage.resolve("resources.jar");
        try (ZipOutputStream zip=new ZipOutputStream(Files.newOutputStream(resources))) {
            for (int i=0;i<Integer.parseInt(meta.getProperty("count"));i++) {
                String k="file."+i+".";
                if (!"resource".equals(meta.getProperty(k+"role"))) continue;
                String path=meta.getProperty(k+"path");
                if (!path.startsWith("resources/")) throw new IOException("Invalid runtime resource path");
                ZipEntry entry=new ZipEntry(path.substring(10)); entry.setTime(0); zip.putNextEntry(entry);
                Files.copy(Main.safe(release,path),zip); zip.closeEntry();
            }
        }
        libraries.add(library(root,resources,"resources",version,null,null));
        Files.delete(resources);
        for (int i=0;i<Integer.parseInt(meta.getProperty("count"));i++) {
            String k="file."+i+".", path=meta.getProperty(k+"path"), role=meta.getProperty(k+"role"), source=meta.getProperty(k+"source");
            Path file=Main.safe(release,path);
            if (path.equals("minecraft.jar")) {
                if (!sha1(file).equals(base.getProperty("client.sha1"))) throw new IOException("Minecraft client differs from official launcher metadata");
                copy(file,stage.resolve(id+".jar"));
            } else if (role.equals("asset")) {
                String relative=path.substring("assets/".length());
                copy(file,Main.safe(root,"assets/virtual/legacy/"+relative));
                // Content-addressed assets allow launcher verification/reconstruction without downloads.
                String digest=sha1(file);
                copy(file,Main.safe(root,"assets/objects/"+digest.substring(0,2)+"/"+digest));
            } else if (role.equals("classpath") || role.equals("native") || role.equals("agent")) {
                Map<String,Object> lib=library(root,file,"file-"+i,version,role.equals("native")?os:null,source);
                libraries.add(lib);
                if (role.equals("agent")) {
                    @SuppressWarnings("unchecked") Map<String,Object> downloads=(Map<String,Object>)lib.get("downloads");
                    @SuppressWarnings("unchecked") Map<String,Object> artifact=(Map<String,Object>)downloads.get("artifact");
                    jvm.add("-javaagent:${library_directory}/"+artifact.get("path"));
                }
            }
        }
        Path index=stage.resolve("legacy.json");
        try (InputStream in=Main.class.getResourceAsStream("/launcher/legacy.json")) { Files.copy(in,index); }
        if (!sha1(index).equals(base.getProperty("assetIndex.sha1"))) throw new IOException("Invalid embedded asset index");
        copy(index,Main.safe(root,"assets/indexes/legacy.json")); Files.delete(index);
        Map<String,Object> assetIndex=obj("id","legacy","sha1",base.getProperty("assetIndex.sha1"),"size",Long.valueOf(base.getProperty("assetIndex.size")),"url",base.getProperty("assetIndex.url"));
        Map<String,Object> profile=obj("id",id,"type","release","time","2026-11-03T00:00:00Z","releaseTime","2026-11-03T00:00:00Z",
            "mainClass","net.minecraft.launchwrapper.Launch","assets","legacy","assetIndex",assetIndex,
            "javaVersion",obj("component","jre-legacy","majorVersion",8),"libraries",libraries,
            "downloads",obj("client",obj("sha1",base.getProperty("client.sha1"),"size",Long.valueOf(base.getProperty("client.size")),"url",base.getProperty("client.url"))),
            "arguments",obj("jvm",jvm,"game",Arrays.asList("--username","${auth_player_name}","--session","${auth_session}","--version","${version_name}","--gameDir","${game_directory}","--assetsDir","${game_assets}","--tweakClass","cpw.mods.fml.common.launcher.FMLTweaker")));
        Files.write(stage.resolve(id+".json"),(json(profile)+"\n").getBytes("UTF-8"),StandardOpenOption.CREATE_NEW);
    }
    static Map<String,Object> library(Path root, Path source, String label, String version, String nativeOS, String url) throws Exception {
        String artifact="desiderium-"+label+"-"+Main.hash(source).substring(0,16);
        String classifier=nativeOS==null?"":"-natives-"+nativeOS;
        String path="dev/desiderium/runtime/"+artifact+"/"+version+"/"+artifact+"-"+version+classifier+".jar";
        copy(source,Main.safe(root,"libraries/"+path));
        Map<String,Object> download=obj("path",path,"sha1",sha1(source),"size",Files.size(source));
        if (url!=null && url.startsWith("https:")) download.put("url",url);
        Map<String,Object> out=obj("name","dev.desiderium.runtime:"+artifact+":"+version,"checksums",Arrays.asList(sha1(source)));
        if (nativeOS==null) out.put("downloads",obj("artifact",download));
        else {
            out.put("downloads",obj("classifiers",obj("natives-"+nativeOS,download)));
            out.put("natives",obj(nativeOS,"natives-"+nativeOS));
            out.put("rules",Arrays.asList(obj("action","allow","os",obj("name",nativeOS))));
            out.put("extract",obj("exclude",Arrays.asList("META-INF/")));
        }
        return out;
    }
}
