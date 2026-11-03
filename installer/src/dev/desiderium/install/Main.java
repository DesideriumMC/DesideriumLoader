package dev.desiderium.install;

import java.io.*;
import java.net.*;
import java.nio.channels.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.zip.*;

/** Java 8 only, no developer tools or third party installer dependencies. */
public final class Main {
    static final String[] VERSIONS = {"0.1.0-l1", "0.1.0-l2", "0.1.0-rc2-l1", "0.1.0-rc2-l2", "0.2.0-java8-l2", "0.0.1"};
    static final String MARKER = "Desiderium instance schema 1\n";
    static final Map<String,String> options = new HashMap<String,String>();
    static boolean offline;
    static Path instance, managed, cache;

    public static void main(String[] args) {
        try { execute(args); }
        catch (Exception e) {
            System.err.println("INSTALLER ERROR: " + e.getMessage());
            System.err.println("Recovery: the previous active version and game data are retained. " +
                "Correct the source/cache error, retry install, or select a verified installed version. " +
                "Do not delete saves or mods. See INSTALL.zh-CN.md.");
            System.exit(1);
        }
    }

    static void execute(String[] args) throws Exception {
        if (args.length == 0 || args[0].equals("help")) {
            System.out.println("Desiderium installer schema 1 (Windows/Linux x64 / Java 8)\n" +
                "list\ninstall --instance DIR --side client|server --version VERSION [--cache DIR] [--offline]\n" +
                "select --instance DIR --version VERSION\nverify --instance DIR\n" +
                "run --instance DIR [--username NAME]\n" +
                "install-hmcl --minecraft DIR --version VERSION [--cache DIR] [--offline]\n" +
                "Standalone install requires a new empty instance; install-hmcl adds a new version to a game directory. " +
                "Java 8 is required to run the game. Standalone run uses a local session; HMCL manages its own accounts.");
            return;
        }
        String command = args[0];
        if (!Arrays.asList("list", "install", "select", "verify", "run", "install-hmcl").contains(command))
            throw new IOException("Unknown command: " + command);
        for (int i=1; i<args.length; i++) {
            String key = args[i];
            if (key.equals("--offline")) { offline=true; continue; }
            if (!Arrays.asList("--instance", "--side", "--version", "--cache", "--username", "--minecraft").contains(key)
                || i+1 == args.length || options.put(key, args[++i]) != null)
                throw new IOException("Invalid/duplicate option: " + key);
        }
        if (command.equals("list")) {
            for (String v : VERSIONS) try (InputStream in=releaseResource(v,"server")) {
                if (in!=null) System.out.println(v + " Minecraft 1.6.4 / Forge 9.11.1.1345 / Mixin 0.8.7");
            }
            return;
        }
        if (command.equals("install-hmcl")) {
            LauncherInstall.install();
            return;
        }
        instance = Paths.get(required("--instance")).toAbsolutePath().normalize();
        noLinks(instance);
        managed = instance.resolve(".desiderium");
        Path marker = managed.resolve("instance.txt");
        if (!Files.exists(marker)) {
            if (!command.equals("install")) throw new IOException("Not a managed instance: " + instance);
            if (Files.exists(instance)) try (DirectoryStream<Path> entries=Files.newDirectoryStream(instance)) {
                if (entries.iterator().hasNext()) throw new IOException("Refusing nonempty unrelated instance: " + instance);
            }
            Files.createDirectories(managed);
            Files.write(marker, MARKER.getBytes("UTF-8"), StandardOpenOption.CREATE_NEW);
        }
        noLinks(managed);
        if (!new String(Files.readAllBytes(marker), "UTF-8").equals(MARKER))
            throw new IOException("Unknown instance marker: " + marker);
        noLinks(marker);
        noLinks(managed.resolve("instance.lock"));
        try (FileChannel channel=FileChannel.open(managed.resolve("instance.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             FileLock lock=channel.tryLock()) {
            if (lock == null) throw new IOException("Instance is running or being changed: " + instance);
            if (command.equals("install")) install();
            else {
                String side = text(managed.resolve("side"));
                if (!side.equals("client") && !side.equals("server")) throw new IOException("Invalid installed side");
                String v = command.equals("select") ? required("--version") : text(managed.resolve("active"));
                Properties meta=metadata(v, side);
                Path release=managed.resolve("versions/" + v);
                verify(meta, release);
                if (command.equals("select")) activate(v);
                else if (command.equals("run")) run(meta, release, v, side);
                else System.out.println("VERIFIED " + v + " " + side);
            }
        }
    }

    static String required(String key) throws IOException {
        String value=options.get(key);
        if (value==null || value.isEmpty()) throw new IOException("Missing " + key);
        return value;
    }
    static void noLinks(Path p) throws IOException {
        for (Path x=p.toAbsolutePath(); x!=null; x=x.getParent())
            if (Files.isSymbolicLink(x) || (Files.exists(x, LinkOption.NOFOLLOW_LINKS) &&
                Files.readAttributes(x, java.nio.file.attribute.BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS).isOther()))
                throw new IOException("Link/reparse path is not supported: " + x);
    }
    static Path safe(Path base, String relative) throws IOException {
        Path result=base.resolve(relative).normalize();
        if (!result.startsWith(base) || result.equals(base)) throw new IOException("Unsafe metadata path: " + relative);
        noLinks(result);
        return result;
    }
    static String text(Path p) throws IOException { noLinks(p); return new String(Files.readAllBytes(p), "UTF-8").trim(); }
    static InputStream releaseResource(String version, String side) throws IOException {
        InputStream in=Main.class.getResourceAsStream("/releases/"+platform()+"/"+version+"-"+side+".properties");
        return in!=null ? in : Main.class.getResourceAsStream("/releases/"+version+"-"+side+".properties");
    }
    static Properties metadata(String version, String side) throws Exception {
        if (!Arrays.asList(VERSIONS).contains(version) || !Arrays.asList("client","server").contains(side))
            throw new IOException("Unsupported version/side: " + version + "/" + side);
        Properties p=new Properties();
        try (InputStream in=releaseResource(version,side)) {
            if (in==null) throw new IOException("Missing embedded release metadata");
            p.load(in);
        }
        if (!"1".equals(p.getProperty("schema"))) throw new IOException("Unsupported metadata schema");
        if (!platform().equals(p.getProperty("platform")))
            throw new IOException("Installer platform="+p.getProperty("platform")+" current="+platform()+"; use the matching distribution");
        return p;
    }
    static String platform() throws IOException {
        String os=System.getProperty("os.name"), arch=System.getProperty("os.arch");
        if (!arch.equals("amd64") && !arch.equals("x86_64")) throw new IOException("Unsupported architecture: "+arch);
        if (os.startsWith("Windows")) return "windows-x64";
        if (os.equals("Linux")) return "linux-x64";
        throw new IOException("Unsupported platform: "+os);
    }
    static String hash(Path p) throws Exception {
        MessageDigest d=MessageDigest.getInstance("SHA-256");
        try (InputStream in=Files.newInputStream(p)) {
            byte[] b=new byte[65536]; int n; while ((n=in.read(b))!=-1) d.update(b,0,n);
        }
        return hex(d.digest());
    }
    static String hex(byte[] b) { StringBuilder s=new StringBuilder(); for (byte v:b) s.append(String.format("%02x",v&255)); return s.toString(); }
    static void checked(Path p, String expected, String source) throws Exception {
        noLinks(p);
        String actual=Files.isRegularFile(p) ? hash(p) : "MISSING";
        if (!actual.equals(expected)) throw new IOException("Artifact=" + p + " source=" + source + " expectedSha256=" + expected + " actualSha256=" + actual);
    }
    static void atomic(Path p, String value) throws Exception {
        noLinks(p);
        Path temp=Files.createTempFile(p.getParent(), "state-", ".tmp");
        try {
            Files.write(temp, (value+"\n").getBytes("UTF-8"));
            Files.move(temp,p,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temp); }
    }
    static void activate(String version) throws Exception {
        atomic(managed.resolve("active"), version);
        System.out.println("ACTIVE " + version + " instance=" + instance);
    }
    static void install() throws Exception {
        String side=required("--side"), version=required("--version");
        Properties meta=metadata(version,side);
        Path sideFile=managed.resolve("side");
        if (Files.exists(sideFile) && !text(sideFile).equals(side)) throw new IOException("Cannot change instance side");
        cache=options.containsKey("--cache") ? Paths.get(options.get("--cache")).toAbsolutePath().normalize() : managed.resolve("cache");
        noLinks(cache); Files.createDirectories(cache);
        Path versions=safe(managed,"versions"); Files.createDirectories(versions);
        Path target=safe(versions,version);
        if (Files.exists(target)) {
            verify(meta,target);
            if (!Files.exists(sideFile)) atomic(sideFile,side);
            Files.createDirectories(safe(instance,"mods"));
            activate(version);
            return;
        }
        Path stage=Files.createTempDirectory(versions,"staging-");
        try {
            int count=Integer.parseInt(meta.getProperty("count"));
            for (int i=0;i<count;i++) {
                String prefix="file."+i+".", name=meta.getProperty(prefix+"path"), expected=meta.getProperty(prefix+"sha256"), source=meta.getProperty(prefix+"source");
                Path object=safe(cache,expected);
                Path objectLock=safe(cache,expected+".lock");
                try (FileChannel objectChannel=FileChannel.open(objectLock,StandardOpenOption.CREATE,StandardOpenOption.WRITE);
                     FileLock objectGuard=objectChannel.lock()) {
                    if (Files.exists(object)) checked(object,expected,source);
                    else fetch(object,expected,source);
                }
                Path dest=safe(stage,name); Files.createDirectories(dest.getParent());
                Files.copy(object,dest);
                if ("native".equals(meta.getProperty(prefix+"role"))) natives(dest,stage.resolve("natives"),false);
                if (i%100==0) System.out.println("Installing " + (i+1) + "/" + count);
            }
            verify(meta,stage);
            Files.move(stage,target,StandardCopyOption.ATOMIC_MOVE);
            if (!Files.exists(sideFile)) atomic(sideFile,side);
            Path mods=safe(instance,"mods"); Files.createDirectories(mods);
            activate(version);
        } finally {
            if (Files.exists(stage)) try (java.util.stream.Stream<Path> stream=Files.walk(stage)) {
                Iterator<Path> it=stream.sorted(Comparator.reverseOrder()).iterator();
                while (it.hasNext()) Files.delete(it.next());
            }
        }
    }
    static void fetch(Path dest, String expected, String source) throws Exception {
        Path temp=Files.createTempFile(cache,"download-",".part");
        try {
            InputStream input;
            if (source.startsWith("zip:")) {
                String[] parts=source.substring(4).split("\\|",-1);
                if (parts.length!=3 || !parts[1].matches("[0-9a-f]{64}")) throw new IOException("Invalid archive source: "+source);
                Path archive=safe(cache,parts[1]);
                Path archiveLock=safe(cache,parts[1]+".lock");
                try (FileChannel channel=FileChannel.open(archiveLock,StandardOpenOption.CREATE,StandardOpenOption.WRITE);
                     FileLock guard=channel.lock()) {
                    if (!Files.exists(archive)) fetch(archive,parts[1],parts[0]);
                    checked(archive,parts[1],parts[0]);
                    try (ZipFile zip=new ZipFile(archive.toFile())) {
                        ZipEntry entry=zip.getEntry(parts[2]);
                        if (entry==null) throw new IOException("Missing archive entry: "+source);
                        try (InputStream in=zip.getInputStream(entry)) { Files.copy(in,temp,StandardCopyOption.REPLACE_EXISTING); }
                    }
                }
                input=null;
            }
            else if (source.startsWith("embedded:")) input=Main.class.getResourceAsStream("/"+source.substring(9));
            else {
                if (offline) throw new IOException("Offline cache miss source="+source+" expectedSha256="+expected+" actualSha256=MISSING cache="+dest);
                URL url=new URL(source);
                if (!url.getProtocol().equals("https")) throw new IOException("HTTPS source required: "+source);
                URLConnection c=url.openConnection(); c.setConnectTimeout(20000); c.setReadTimeout(30000);
                input=c.getInputStream();
            }
            if (input==null && !source.startsWith("zip:")) throw new IOException("Missing embedded source="+source+" expectedSha256="+expected);
            if (input!=null) try (InputStream in=input) { Files.copy(in,temp,StandardCopyOption.REPLACE_EXISTING); }
            checked(temp,expected,source);
            try { Files.move(temp,dest,StandardCopyOption.ATOMIC_MOVE); }
            catch (FileAlreadyExistsException e) { checked(dest,expected,source); }
        } catch (Exception e) { throw new IOException("Download source="+source+" expectedSha256="+expected+" cache="+dest+": "+e.getMessage(),e); }
        finally { Files.deleteIfExists(temp); }
    }
    static void natives(Path jar, Path base, boolean verify) throws Exception {
        noLinks(base); if (!verify) Files.createDirectories(base);
        try (ZipFile zip=new ZipFile(jar.toFile())) {
            Enumeration<? extends ZipEntry> entries=zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry e=entries.nextElement(); if (!e.getName().endsWith(".dll") && !e.getName().endsWith(".so")) continue;
                Path dest=safe(base,e.getName());
                if (verify) {
                    MessageDigest d=MessageDigest.getInstance("SHA-256");
                    try (InputStream in=zip.getInputStream(e)) { byte[] b=new byte[65536]; int n; while ((n=in.read(b))!=-1) d.update(b,0,n); }
                    checked(dest,hex(d.digest()),jar+"!"+e.getName());
                } else { Files.createDirectories(dest.getParent()); try (InputStream in=zip.getInputStream(e)) { Files.copy(in,dest); } }
            }
        }
    }
    static void verify(Properties p, Path release) throws Exception {
        noLinks(release);
        int count=Integer.parseInt(p.getProperty("count"));
        for (int i=0;i<count;i++) {
            String k="file."+i+"."; Path f=safe(release,p.getProperty(k+"path"));
            checked(f,p.getProperty(k+"sha256"),p.getProperty(k+"source"));
            if ("native".equals(p.getProperty(k+"role"))) natives(f,release.resolve("natives"),true);
        }
    }
    static void run(Properties p, Path release, String version, String side) throws Exception {
        if (!System.getProperty("java.specification.version").equals("1.8")) throw new IOException("Game requires Java 8; current="+System.getProperty("java.version"));
        platform();
        List<String> cp=new ArrayList<String>();
        if (Files.isDirectory(release.resolve("resources"))) cp.add(release.resolve("resources").toString());
        for (int i=0;i<Integer.parseInt(p.getProperty("count"));i++)
            if ("classpath".equals(p.getProperty("file."+i+".role"))) cp.add(safe(release,p.getProperty("file."+i+".path")).toString());
        List<String> command=new ArrayList<String>(Arrays.asList(Paths.get(System.getProperty("java.home"),"bin",platform().startsWith("windows")?"java.exe":"java").toString(),
            "-Xms256M","-Xmx1G","-Dfml.ignoreInvalidMinecraftCertificates=true","-Djava.library.path="+release.resolve("natives"),
            "-cp",String.join(File.pathSeparator,cp),"net.minecraft.launchwrapper.Launch",
            "--tweakClass=cpw.mods.fml.common.launcher.FML"+(side.equals("server")?"ServerTweaker":"Tweaker"),"--gameDir="+instance));
        if (side.equals("server")) command.add("nogui");
        else command.addAll(Arrays.asList("--assetsDir="+release.resolve("assets"),"--username="+options.getOrDefault("--username","DesideriumPlayer"),"--session=0","--version=Desiderium-"+version));
        for (int i=0;i<Integer.parseInt(p.getProperty("count"));i++)
            if ("agent".equals(p.getProperty("file."+i+".role")))
                command.add(1,"-javaagent:"+safe(release,p.getProperty("file."+i+".path")));
        Path installer=Paths.get(Main.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        System.out.println("LAUNCH version="+version+" side="+side+" instance="+instance+" contentSha256="+p.getProperty("contentSha256")+" installerSha256="+hash(installer)+" platform="+platform());
        for (int i=0;i<Integer.parseInt(p.getProperty("count"));i++)
            if ("classpath".equals(p.getProperty("file."+i+".role")))
                System.out.println("ARTIFACT path="+p.getProperty("file."+i+".path")+" sha256="+p.getProperty("file."+i+".sha256"));
        Process child=new ProcessBuilder(command).directory(instance.toFile()).inheritIO().start();
        int exit=child.waitFor();
        System.out.println("GAME EXIT "+exit);
        if (exit!=0) throw new IOException("Game exited "+exit+"; inspect FML logs in "+instance);
    }
}
