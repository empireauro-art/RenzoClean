package com.renzo.idscanner;

public class RecordFormatTest {
    static int fails = 0;

    static void check(String name, boolean expect, boolean got) {
        System.out.println((got == expect ? "PASS " : "FAIL ") + name);
        if (got != expect) fails++;
    }

    public static void main(String[] a) {
        String ua = "Mozilla/5.0 (Linux; Android 13; Pixel 6) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36";
        String ok = "c737bcbb-995e-4d9a-b077-308734ff6a98|1793979555295-5083449865490225941|d378b55b4b36c5db|" + ua;
        check("legacy four-field example is valid", true, RecordFormat.isValid(ok));
        check("provider export accepts opaque SDK identifier", true, RecordFormat.isValidExportRecord("adjust-test-adid-001"));
        check("provider export accepts Singular record", true, RecordFormat.isValidExportRecord("singular-device-id-001|" + ua));
        check("trailing newline rejected", false, RecordFormat.isValidExportRecord(ok + "\n"));
        check("control character rejected", false, RecordFormat.isValidExportRecord("abc\u0001def"));
        check("empty record rejected", false, RecordFormat.isValidExportRecord(""));
        check("oversized record rejected", false, RecordFormat.isValidExportRecord(new String(new char[4097]).replace('\0', 'x')));
        check("trailing newline rejected by legacy validator", false, RecordFormat.isValid(ok + "\n"));
        check("trailing space rejected by legacy validator", false, RecordFormat.isValid(ok + " "));
        check("only 2 pipes rejected by legacy validator", false, RecordFormat.isValid("c737bcbb-995e-4d9a-b077-308734ff6a98|1793979555295-5083449865490225941|d378b55b4b36c5db"));
        check("4 pipes rejected by legacy validator", false, RecordFormat.isValid(ok + "|x"));
        check("bad uuid rejected by legacy validator", false, RecordFormat.isValid("zzzz|1793979555295-5083449865490225941|d378b55b4b36c5db|" + ua));
        check("non-hex third field rejected by legacy validator", false, RecordFormat.isValid("c737bcbb-995e-4d9a-b077-308734ff6a98|1793979555295-5083449865490225941|ghij|" + ua));
        check("empty UA rejected by legacy validator", false, RecordFormat.isValid("c737bcbb-995e-4d9a-b077-308734ff6a98|1793979555295-5083449865490225941|d378b55b4b36c5db|"));
        check("quoted legacy record rejected", false, RecordFormat.isValid("\"" + ok + "\""));
        check("null rejected", false, RecordFormat.isValid(null));
        check("AppsFlyer provider supported", true, Provider.isSupported(Provider.APPSFLYER));
        check("Adjust provider supported", true, Provider.isSupported(Provider.ADJUST));
        check("Singular provider supported", true, Provider.isSupported(Provider.SINGULAR));
        check("unknown provider rejected", false, Provider.isSupported("unknown"));
        System.out.println(fails == 0 ? "ALL PASSED" : fails + " FAILED");
        System.exit(fails == 0 ? 0 : 1);
    }
}
