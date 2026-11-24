package com.renzo.idscanner;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Scans public installed APK code for known SDK class signatures only. */
final class SdkScanner {
    private SdkScanner() {}

    static List<String> scan(String baseApkPath, String[] splitApkPaths) {
        Set<String> found = new LinkedHashSet<>();
        scanApk(baseApkPath, found);
        if (splitApkPaths != null) {
            for (String splitPath : splitApkPaths) scanApk(splitPath, found);
        }

        List<String> ordered = new ArrayList<>();
        for (String provider : Provider.ORDERED) {
            if (found.contains(provider)) ordered.add(provider);
        }
        return ordered;
    }

    private static void scanApk(String apkPath, Set<String> found) {
        if (apkPath == null || apkPath.isEmpty()
                || found.size() == Provider.ORDERED.size()) return;

        try (ZipFile apk = new ZipFile(apkPath)) {
            Enumeration<? extends ZipEntry> entries = apk.entries();
            while (entries.hasMoreElements() && found.size() < Provider.ORDERED.size()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory() || !isDexFile(entry.getName())) continue;
                try (InputStream input = apk.getInputStream(entry)) {
                    found.addAll(ProviderSignatureDetector.detect(input));
                }
            }
        } catch (IOException | SecurityException ignored) {
            // Some vendor packages may restrict access to their installed APK path.
        }
    }

    private static boolean isDexFile(String name) {
        if (name == null || !name.startsWith("classes") || !name.endsWith(".dex")) {
            return false;
        }
        String middle = name.substring("classes".length(), name.length() - ".dex".length());
        if (middle.isEmpty()) return true;
        for (int i = 0; i < middle.length(); i++) {
            if (!Character.isDigit(middle.charAt(i))) return false;
        }
        return true;
    }
}
