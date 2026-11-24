package com.renzo.idscanner;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Local-only diagnostic records, separated by app package and declared provider. */
final class RecordStore {
    static final String LEGACY_PROVIDER = "legacy";
    private static final String SEP = "::provider::";
    private static final String ROOT_FOUND = "root-found::";
    private final SharedPreferences prefs;

    RecordStore(Context c) {
        prefs = c.getApplicationContext().getSharedPreferences("records", Context.MODE_PRIVATE);
    }

    boolean save(String pkg, String provider, String record) {
        if (pkg == null || pkg.isEmpty() || !Provider.isSupported(provider)
                || !RecordFormat.isValidExportRecord(record)) return false;
        String key = pkg + SEP + provider;
        String old = prefs.getString(key, null);
        if (record.equals(old)) return true;
        return prefs.edit().putString(key, record).commit();
    }

    String get(String pkg, String provider) {
        if (pkg == null || provider == null) return null;
        String record = prefs.getString(pkg + SEP + provider, null);
        if (record == null && LEGACY_PROVIDER.equals(provider)) {
            record = prefs.getString(pkg, null);
        }
        return record;
    }

    Map<String, String> getForPackage(String pkg) {
        Map<String, String> out = new LinkedHashMap<>();
        if (pkg == null) return out;
        for (String provider : Provider.ORDERED) {
            String record = get(pkg, provider);
            if (record != null) out.put(provider, record);
        }
        // Preserve records created by versions before provider-specific storage.
        String legacy = prefs.getString(pkg, null);
        if (legacy != null) out.put(LEGACY_PROVIDER, legacy);
        return out;
    }

    boolean markRootProvider(String pkg, String provider) {
        if (pkg == null || pkg.isEmpty() || !Provider.isSupported(provider)) return false;
        return prefs.edit().putBoolean(pkg + SEP + ROOT_FOUND + provider, true).commit();
    }

    void clearRootProviders(String pkg) {
        if (pkg == null || pkg.isEmpty()) return;
        String prefix = pkg + SEP + ROOT_FOUND;
        SharedPreferences.Editor editor = prefs.edit();
        for (String key : prefs.getAll().keySet()) {
            if (key.startsWith(prefix)) editor.remove(key);
        }
        editor.commit();
    }

    List<String> getRootProviders(String pkg) {
        List<String> out = new ArrayList<>();
        if (pkg == null || pkg.isEmpty()) return out;
        String prefix = pkg + SEP + ROOT_FOUND;
        Map<String, ?> all = prefs.getAll();
        for (Map.Entry<String, ?> entry : all.entrySet()) {
            if (entry.getKey().startsWith(prefix) && Boolean.TRUE.equals(entry.getValue())) {
                String provider = entry.getKey().substring(prefix.length());
                if (Provider.isSupported(provider) && !out.contains(provider)) out.add(provider);
            }
        }
        List<String> ordered = new ArrayList<>();
        for (String provider : Provider.ORDERED) {
            if (out.contains(provider)) ordered.add(provider);
        }
        return ordered;
    }

    boolean hasAny(String pkg) {
        return !getForPackage(pkg).isEmpty();
    }
}
