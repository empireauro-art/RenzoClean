package com.renzo.idscanner;

import java.util.regex.Pattern;

/** Validators preserve the original record; no trimming or normalization is performed. */
final class RecordFormat {
    private static final Pattern UUID = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    private static final Pattern HEX = Pattern.compile("[0-9a-fA-F]+");
    private static final int MAX_LEN = 4096;

    private RecordFormat() {}

    /** Legacy four-field format used by the original sample. */
    static boolean isValid(String s) {
        if (!isValidExportRecord(s)) return false;
        String[] p = s.split("\\|", -1);
        if (p.length != 4) return false;
        for (String part : p) {
            if (part.isEmpty() || !part.equals(part.trim())) return false;
        }
        if (!UUID.matcher(p[0]).matches()) return false;
        if (p[1].indexOf(' ') >= 0) return false;
        return HEX.matcher(p[2]).matches();
    }

    /** Provider exports may use different SDK identifier formats; preserve their raw text. */
    static boolean isValidExportRecord(String s) {
        if (s == null || s.isEmpty() || s.length() > MAX_LEN || s.trim().isEmpty()) return false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isISOControl(c) || c == '\u2028' || c == '\u2029') return false;
        }
        return true;
    }
}
