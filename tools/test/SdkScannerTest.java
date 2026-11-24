package com.renzo.idscanner;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class SdkScannerTest {
    public static void main(String[] args) throws Exception {
        File dir = Files.createTempDirectory("renzo-sdk-scan").toFile();
        try {
            File baseApk = new File(dir, "base.apk");
            File splitApk = new File(dir, "split_config.apk");
            writeZip(baseApk,
                    new Entry("classes.dex", "Lcom/appsflyer/AppsFlyerLib;"),
                    new Entry("classes2.dex", "Lcom/adjust/sdk/Adjust;"),
                    new Entry("assets/not-dex.txt", "Lcom/singular/sdk/Singular;"));
            writeZip(splitApk,
                    new Entry("classes.dex", "Lcom/singular/sdk/SingularConfig;"));

            List<String> found = SdkScanner.scan(baseApk.getAbsolutePath(),
                    new String[]{splitApk.getAbsolutePath()});
            List<String> expected = List.of(
                    Provider.APPSFLYER, Provider.ADJUST, Provider.SINGULAR);
            if (!expected.equals(found)) {
                throw new AssertionError("expected " + expected + " but got " + found);
            }
            System.out.println("SdkScannerTest: OK");
        } finally {
            deleteTree(dir);
        }
    }

    private static void writeZip(File file, Entry... entries) throws Exception {
        try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(file))) {
            for (Entry entry : entries) {
                out.putNextEntry(new ZipEntry(entry.name));
                out.write(entry.value.getBytes(StandardCharsets.UTF_8));
                out.closeEntry();
            }
        }
    }

    private static void deleteTree(File file) {
        File[] children = file.listFiles();
        if (children != null) for (File child : children) deleteTree(child);
        file.delete();
    }

    private static final class Entry {
        final String name;
        final String value;
        Entry(String name, String value) { this.name = name; this.value = value; }
    }
}
