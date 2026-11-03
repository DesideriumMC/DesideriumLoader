package dev.desiderium.install;

import java.io.*;
import java.net.*;
import java.nio.channels.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.*;

/** Dependency-free Forge installation processor; creates runtime resources locally. */
public final class Processor {
    static void noLinks(Path p) throws IOException {
        for (Path x=p.toAbsolutePath();x!=null;x=x.getParent())
            if (Files.isSymbolicLink(x) || (Files.exists(x,LinkOption.NOFOLLOW_LINKS) &&
                Files.readAttributes(x,java.nio.file.attribute.BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS).isOther()))
                throw new IOException("Link/reparse path is not supported: "+x);
    }
    static String hash(Path p, String algorithm) throws Exception {
        MessageDigest digest=MessageDigest.getInstance(algorithm);
        try (InputStream in=Files.newInputStream(p)) {
            byte[] b=new byte[65536]; int n;
            while ((n=in.read(b))!=-1) digest.update(b,0,n);
        }
        StringBuilder out=new StringBuilder();
        for (byte b:digest.digest()) out.append(String.format("%02x",b&255));
        return out.toString();
    }
    static void checked(Path p, String expected) throws Exception {
        noLinks(p);
        if (!Files.isRegularFile(p) || !hash(p,"SHA-256").equals(expected))
            throw new IOException("SHA-256 mismatch: "+p+" expected="+expected);
    }
    static void pack(Path data, Path output, String entryName) throws Exception {
        byte[] bytes=Files.readAllBytes(data); CRC32 crc=new CRC32(); crc.update(bytes);
        ZipEntry entry=new ZipEntry(entryName);
        entry.setMethod(ZipEntry.STORED); entry.setSize(bytes.length); entry.setCompressedSize(bytes.length); entry.setCrc(crc.getValue());
        Calendar date=new GregorianCalendar(); date.clear(); date.set(2000,Calendar.JANUARY,1,0,0,0);
        entry.setTime(date.getTimeInMillis());
        try (ZipOutputStream zip=new ZipOutputStream(Files.newOutputStream(output))) {
            zip.putNextEntry(entry); zip.write(bytes); zip.closeEntry();
        }
    }
    public static void main(String[] args) throws Exception {
        if (args.length==4 && args[0].equals("--pack")) {
            pack(Paths.get(args[1]),Paths.get(args[2]),args[3]); return;
        }
        if (args.length!=2 || !args[0].equals("--libraries")) throw new IOException("Expected --libraries DIR");
        Properties p=new Properties();
        try (InputStream in=Processor.class.getResourceAsStream("/processor.properties")) {
            if (in==null) throw new IOException("Missing processor configuration");
            p.load(in);
        }
        Path libraries=Paths.get(args[1]).toAbsolutePath().normalize(); noLinks(libraries); Files.createDirectories(libraries);
        Path output=libraries.resolve(p.getProperty("output")).normalize();
        if (!output.startsWith(libraries) || output.equals(libraries)) throw new IOException("Invalid output path");
        noLinks(output); Files.createDirectories(output.getParent());
        Path lock=output.resolveSibling(output.getFileName()+".lock"); noLinks(lock);
        try (FileChannel channel=FileChannel.open(lock,StandardOpenOption.CREATE,StandardOpenOption.WRITE);
             FileLock guard=channel.lock()) {
            if (Files.exists(output)) { checked(output,p.getProperty("outputSha256")); return; }
            Path cache=libraries.resolve(".desiderium-cache"); noLinks(cache); Files.createDirectories(cache);
            Path archive=cache.resolve(p.getProperty("archiveSha256")); noLinks(archive);
            Path archiveLock=archive.resolveSibling(archive.getFileName()+".lock"); noLinks(archiveLock);
            try (FileChannel cacheChannel=FileChannel.open(archiveLock,StandardOpenOption.CREATE,StandardOpenOption.WRITE);
                 FileLock cacheGuard=cacheChannel.lock()) {
                if (!Files.exists(archive)) {
                    Path download=Files.createTempFile(cache,"download-",".part");
                    try {
                        URL url=new URL(p.getProperty("url"));
                        if (!url.getProtocol().equals("https")) throw new IOException("HTTPS required");
                        URLConnection connection=url.openConnection(); connection.setConnectTimeout(20000); connection.setReadTimeout(30000);
                        try (InputStream in=connection.getInputStream()) { Files.copy(in,download,StandardCopyOption.REPLACE_EXISTING); }
                        checked(download,p.getProperty("archiveSha256"));
                        Files.move(download,archive,StandardCopyOption.ATOMIC_MOVE);
                    } finally { Files.deleteIfExists(download); }
                }
                checked(archive,p.getProperty("archiveSha256"));
                Path raw=Files.createTempFile(output.getParent(),"resource-",".part");
                Path temp=Files.createTempFile(output.getParent(),"jar-",".part");
                try {
                    try (ZipFile zip=new ZipFile(archive.toFile())) {
                        ZipEntry entry=zip.getEntry(p.getProperty("entry"));
                        if (entry==null) throw new IOException("Missing runtime resource");
                        try (InputStream in=zip.getInputStream(entry)) { Files.copy(in,raw,StandardCopyOption.REPLACE_EXISTING); }
                    }
                    checked(raw,p.getProperty("entrySha256"));
                    pack(raw,temp,p.getProperty("entry")); checked(temp,p.getProperty("outputSha256"));
                    Files.move(temp,output,StandardCopyOption.ATOMIC_MOVE);
                    System.out.println("DESIDERIUM PROCESSOR: runtime resources prepared");
                } finally { Files.deleteIfExists(raw); Files.deleteIfExists(temp); }
            }
        }
    }
}
