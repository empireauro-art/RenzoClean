package com.renzo.idscanner;

import java.util.Arrays;

public final class RootAttributionScannerTest {
    public static void main(String[] args) {
        String sample = "RENZO_ROOT_OK\n"
                + "RENZO_PROVIDER\tcom.example.game\tappsflyer\n"
                + "RENZO_DATA\tcom.example.game\tappsflyer\t"
                + "71e61cb2-62e4-41e4-aa61-d7a7fd6a448a|1793978238557-546449225177703739"
                + "|fa8b84cc484bcd0d|Mozilla/5.0 (Linux; Android 13)\n"
                + "RENZO_PROVIDER\tcom.example.game\tadjust\n"
                + "RENZO_SCANNED\t1\n";

        RootAttributionScanner.Report report = RootAttributionScanner.parseOutput(sample, 0);
        check(report.rootAvailable, "root marker should be accepted");
        check(report.scannedPackages == 1, "scanned package count should be parsed");
        check(report.providersByPackage.get("com.example.game").contains(Provider.APPSFLYER),
                "AppsFlyer presence should be parsed");
        check(report.providersByPackage.get("com.example.game").contains(Provider.ADJUST),
                "Adjust file presence should be parsed");
        check(report.recordsByPackage.get("com.example.game").get(Provider.APPSFLYER)
                        .startsWith("71e61cb2-62e4-41e4-aa61-d7a7fd6a448a|"),
                "AppsFlyer record should preserve the raw four-field payload");
        check(!report.recordsByPackage.get("com.example.game").containsKey(Provider.ADJUST),
                "Adjust presence must not be misreported as an extracted ID");

        RootAttributionScanner.Report denied = RootAttributionScanner.parseOutput(
                "RENZO_ROOT_DENIED\n", 77);
        check(!denied.rootAvailable, "denied root must not be reported as available");

        String command = RootAttributionScanner.buildCommand(
                Arrays.asList("com.example.game", "com.bad;touch /tmp/owned"));
        check(command.contains("/data/data/$pkg/shared_prefs"),
                "scanner must check app shared preferences");
        check(command.contains("AF_INSTALLATION") && command.contains("androidIdCached"),
                "AppsFlyer keys must be checked");
        check(command.contains("singular-id") && command.contains("adjust_preferences.xml"),
                "Singular and Adjust preference files must be checked");
        check(!command.contains("touch /tmp/owned"),
                "invalid package names must not enter the root shell command");

        System.out.println("RootAttributionScannerTest: OK");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
