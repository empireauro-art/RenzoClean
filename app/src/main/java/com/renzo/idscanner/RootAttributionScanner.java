package com.renzo.idscanner;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

final class RootAttributionScanner {
    private static final Pattern PACKAGE_NAME = Pattern.compile("[A-Za-z0-9_.]+");
    private static final String ROOT_MARKER = "RENZO_ROOT_OK";

    static final class Report {
        final boolean rootAvailable;
        final Map<String, Set<String>> providersByPackage;
        final Map<String, Map<String, String>> recordsByPackage;
        final int scannedPackages;
        final String diagnostic;

        Report(boolean rootAvailable, Map<String, Set<String>> providersByPackage,
               Map<String, Map<String, String>> recordsByPackage,
               int scannedPackages, String diagnostic) {
            this.rootAvailable = rootAvailable;
            this.providersByPackage = providersByPackage;
            this.recordsByPackage = recordsByPackage;
            this.scannedPackages = scannedPackages;
            this.diagnostic = diagnostic;
        }
    }

    private RootAttributionScanner() {}

    static Report scan(List<String> packageNames) throws IOException, InterruptedException {
        Process process = new ProcessBuilder("su", "-c", buildCommand(packageNames))
                .redirectErrorStream(true).start();
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) output.append(line).append('\n');
        }
        int exitCode = process.waitFor();
        return parseOutput(output.toString(), exitCode);
    }

    static String buildCommand(List<String> packageNames) {
        StringBuilder safePackages = new StringBuilder();
        if (packageNames != null) {
            for (String pkg : packageNames) {
                if (pkg == null || !PACKAGE_NAME.matcher(pkg).matches()) continue;
                if (safePackages.length() > 0) safePackages.append(' ');
                safePackages.append(pkg);
            }
        }

        StringBuilder sh = new StringBuilder(6144);
        sh.append("if [ \"$(id -u)\" != \"0\" ]; then echo RENZO_ROOT_DENIED; exit 77; fi\n");
        sh.append("echo ").append(ROOT_MARKER).append('\n');
        sh.append("scanned=0\n");
        sh.append("PACKAGES='").append(safePackages).append("'\n");
        sh.append("clean_value() { printf '%s' \"$1\" | tr '\\t\\r\\n' '   '; }\n");
        sh.append("for pkg in $PACKAGES; do\n");
        sh.append("  prefs=\"/data/data/$pkg/shared_prefs\"\n");
        sh.append("  if [ ! -d \"$prefs\" ]; then prefs=\"/data/user/0/$pkg/shared_prefs\"; fi\n");
        sh.append("  if [ -d \"$prefs\" ]; then\n    scanned=$((scanned+1))\n");

        // 1. AppsFlyer (uid, appsflyer_id, or AF_INSTALLATION)
        sh.append("    af=\"$prefs/appsflyer-data.xml\"\n");
        sh.append("    if [ -f \"$af\" ]; then\n");
        sh.append("      printf 'RENZO_PROVIDER\\t%s\\tappsflyer\\n' \"$pkg\"\n");
        sh.append("      af_id=$(sed -n 's/.*name=\"uid\">\\([^<]*\\).*/\\1/p' \"$af\" 2>/dev/null | head -n 1)\n");
        sh.append("      if [ -z \"$af_id\" ]; then af_id=$(sed -n 's/.*name=\"appsflyer_id\">\\([^<]*\\).*/\\1/p' \"$af\" 2>/dev/null | head -n 1); fi\n");
        sh.append("      if [ -z \"$af_id\" ]; then af_id=$(sed -n 's/.*name=\"AF_INSTALLATION\">\\([^<]*\\).*/\\1/p' \"$af\" 2>/dev/null | head -n 1); fi\n");
        sh.append("      af_cached=$(sed -n 's/.*name=\"androidIdCached\">\\([^<]*\\).*/\\1/p' \"$af\" 2>/dev/null | head -n 1)\n");
        sh.append("      app_android_id=$(grep \"package=\\\"$pkg\\\"\" /data/system/users/0/settings_ssaid.xml 2>/dev/null | sed -n 's/.*value=\"\\([^\"]*\\)\".*/\\1/p' | head -n 1)\n");
        sh.append("      if [ -z \"$app_android_id\" ]; then app_android_id=$(settings get secure android_id 2>/dev/null | tr -d '\\r\\n'); fi\n");
        sh.append("      ua=$(grep -hoE 'Mozilla/5\\.0[^<\"]*' \"$prefs\"/*.xml 2>/dev/null | head -n 1)\n");
        sh.append("      if [ -z \"$ua\" ]; then android_ver=$(getprop ro.build.version.release); model=$(getprop ro.product.model); ua=\"Mozilla/5.0 (Linux; Android $android_ver; $model) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36\"; fi\n");
        sh.append("      if [ -n \"$af_id\" ]; then record=\"$(clean_value \"$af_id\")|$(clean_value \"$af_cached\")|$(clean_value \"$app_android_id\")|$(clean_value \"$ua\")\"; printf 'RENZO_DATA\\t%s\\tappsflyer\\t%s\\n' \"$pkg\" \"$record\"; fi\n");
        sh.append("    fi\n");

        // 2. Singular (pref-singular-id.xml or singular-pref-session.xml)
        sh.append("    singular=\"$prefs/pref-singular-id.xml\"\n");
        sh.append("    if [ ! -f \"$singular\" ]; then singular=\"$prefs/singular-pref-session.xml\"; fi\n");
        sh.append("    if [ -f \"$singular\" ]; then\n");
        sh.append("      printf 'RENZO_PROVIDER\\t%s\\tsingular\\n' \"$pkg\"\n");
        sh.append("      singular_id=$(sed -n 's/.*name=\"singular-id\">\\([^<]*\\).*/\\1/p' \"$singular\" 2>/dev/null | head -n 1)\n");
        sh.append("      if [ -z \"$singular_id\" ]; then singular_id=$(sed -n 's/.*name=\"sdid\">\\([^<]*\\).*/\\1/p' \"$singular\" 2>/dev/null | head -n 1); fi\n");
        sh.append("      if [ -n \"$singular_id\" ]; then printf 'RENZO_DATA\\t%s\\tsingular\\t%s\\n' \"$pkg\" \"$(clean_value \"$singular_id\")\"; fi\n");
        sh.append("    fi\n");

        // 3. Adjust (adjust_preferences.xml or AdjustIoPreferences.xml)
        sh.append("    adjust=\"$prefs/adjust_preferences.xml\"\n");
        sh.append("    if [ ! -f \"$adjust\" ]; then adjust=\"$prefs/AdjustIoPreferences.xml\"; fi\n");
        sh.append("    if [ -f \"$adjust\" ]; then\n");
        sh.append("      printf 'RENZO_PROVIDER\\t%s\\tadjust\\n' \"$pkg\"\n");
        sh.append("      adj_id=$(sed -n 's/.*name=\"adid\">\\([^<]*\\).*/\\1/p' \"$adjust\" 2>/dev/null | head -n 1)\n");
        sh.append("      if [ -z \"$adj_id\" ]; then adj_id=$(grep -hoE '[0-9a-f]{32}' \"$adjust\" 2>/dev/null | head -n 1); fi\n");
        sh.append("      if [ -n \"$adj_id\" ]; then printf 'RENZO_DATA\\t%s\\tadjust\\t%s\\n' \"$pkg\" \"$(clean_value \"$adj_id\")\"; fi\n");
        sh.append("    fi\n");

        sh.append("  fi\n");
        sh.append("done\n");
        sh.append("printf 'RENZO_SCANNED\\t%s\\n' \"$scanned\"\n");
        return sh.toString();
    }

    static Report parseOutput(String output, int exitCode) {
        Map<String, Set<String>> providers = new LinkedHashMap<>();
        Map<String, Map<String, String>> records = new LinkedHashMap<>();
        boolean root = false;
        int scanned = 0;
        String diagnostic = "";

        if (output != null) {
            for (String line : output.split("\\r?\\n")) {
                if (ROOT_MARKER.equals(line)) { root = true; continue; }
                if (line.startsWith("RENZO_ROOT_DENIED")) {
                    diagnostic = "Root permission denied";
                    continue;
                }
                String[] fields = line.split("\\t", 4);
                if (fields.length >= 2 && "RENZO_SCANNED".equals(fields[0])) {
                    try { scanned = Integer.parseInt(fields[1]); }
                    catch (NumberFormatException ignored) { scanned = 0; }
                } else if (fields.length >= 3 && "RENZO_PROVIDER".equals(fields[0])
                        && Provider.isSupported(fields[2])) {
                    providers.computeIfAbsent(fields[1], k -> new LinkedHashSet<>()).add(fields[2]);
                } else if (fields.length == 4 && "RENZO_DATA".equals(fields[0])
                        && Provider.isSupported(fields[2]) && !fields[3].isEmpty()) {
                    providers.computeIfAbsent(fields[1], k -> new LinkedHashSet<>()).add(fields[2]);
                    records.computeIfAbsent(fields[1], k -> new LinkedHashMap<>()).put(fields[2], fields[3]);
                }
            }
        }
        if (!root && diagnostic.isEmpty()) {
            diagnostic = exitCode == 0 ? "Root shell did not confirm UID 0" : "Root command failed";
        }
        return new Report(root && exitCode == 0, providers, records, scanned, diagnostic);
    }
}
