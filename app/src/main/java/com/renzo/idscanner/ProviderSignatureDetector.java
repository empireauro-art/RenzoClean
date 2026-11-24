package com.renzo.idscanner;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Finds known attribution SDK class descriptors in DEX bytecode.
 * This detects SDK code bundled in an APK; it does not read app-private data
 * or obtain device/user identifiers.
 */
final class ProviderSignatureDetector {
    private static final Map<String, String[]> SIGNATURES;
    private static final int MAX_MARKER_LENGTH;

    static {
        Map<String, String[]> markers = new LinkedHashMap<>();
        markers.put(Provider.APPSFLYER, new String[]{
                "Lcom/appsflyer/AppsFlyerLib;",
                "Lcom/appsflyer/AppsFlyerLibCore;"
        });
        markers.put(Provider.ADJUST, new String[]{
                "Lcom/adjust/sdk/Adjust;",
                "Lcom/adjust/sdk/AdjustConfig;"
        });
        markers.put(Provider.SINGULAR, new String[]{
                "Lcom/singular/sdk/Singular;",
                "Lcom/singular/sdk/SingularConfig;"
        });
        SIGNATURES = Collections.unmodifiableMap(markers);

        int max = 0;
        for (String[] values : SIGNATURES.values()) {
            for (String value : values) {
                max = Math.max(max, value.getBytes(StandardCharsets.UTF_8).length);
            }
        }
        MAX_MARKER_LENGTH = max;
    }

    private ProviderSignatureDetector() {}

    static List<String> detect(InputStream dexStream) throws IOException {
        Set<String> found = new LinkedHashSet<>();
        byte[] readBuffer = new byte[8192];
        byte[] tail = new byte[Math.max(0, MAX_MARKER_LENGTH - 1)];
        int tailLength = 0;
        int count;

        while ((count = dexStream.read(readBuffer)) != -1) {
            if (count == 0) continue;
            byte[] window = new byte[tailLength + count];
            System.arraycopy(tail, 0, window, 0, tailLength);
            System.arraycopy(readBuffer, 0, window, tailLength, count);

            for (Map.Entry<String, String[]> provider : SIGNATURES.entrySet()) {
                if (found.contains(provider.getKey())) continue;
                for (String signature : provider.getValue()) {
                    byte[] marker = signature.getBytes(StandardCharsets.UTF_8);
                    if (contains(window, marker)) {
                        found.add(provider.getKey());
                        break;
                    }
                }
            }
            if (found.size() == SIGNATURES.size()) break;

            tailLength = Math.min(tail.length, window.length);
            if (tailLength > 0) {
                System.arraycopy(window, window.length - tailLength, tail, 0, tailLength);
            }
        }

        List<String> ordered = new ArrayList<>();
        for (String provider : Provider.ORDERED) {
            if (found.contains(provider)) ordered.add(provider);
        }
        return ordered;
    }

    private static boolean contains(byte[] source, byte[] target) {
        if (target.length == 0 || source.length < target.length) return false;
        int last = source.length - target.length;
        for (int i = 0; i <= last; i++) {
            int j = 0;
            while (j < target.length && source[i + j] == target[j]) j++;
            if (j == target.length) return true;
        }
        return false;
    }
}
