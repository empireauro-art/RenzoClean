package com.renzo.idscanner;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Provider names explicitly declared by an authorized exporting app. */
final class Provider {
    static final String APPSFLYER = "appsflyer";
    static final String ADJUST = "adjust";
    static final String SINGULAR = "singular";
    static final String LEGACY = "legacy";
    static final List<String> ORDERED = Collections.unmodifiableList(
            Arrays.asList(APPSFLYER, ADJUST, SINGULAR));

    private Provider() {}

    static boolean isSupported(String value) {
        return APPSFLYER.equals(value) || ADJUST.equals(value)
                || SINGULAR.equals(value) || LEGACY.equals(value);
    }

    static String displayName(String value) {
        if (APPSFLYER.equals(value)) return "AppsFlyer";
        if (ADJUST.equals(value)) return "Adjust";
        if (SINGULAR.equals(value)) return "Singular";
        return "Record";
    }
}
