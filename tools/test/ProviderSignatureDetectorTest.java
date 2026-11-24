package com.renzo.idscanner;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

public final class ProviderSignatureDetectorTest {
    public static void main(String[] args) throws Exception {
        detectsAllSupportedProviders();
        detectsSignatureAcrossReadBoundary();
        ignoresUnrelatedText();
        System.out.println("ProviderSignatureDetectorTest: OK");
    }

    private static void detectsAllSupportedProviders() throws Exception {
        String dexText = "prefix Lcom/appsflyer/AppsFlyerLib; middle "
                + "Lcom/adjust/sdk/AdjustConfig; another "
                + "Lcom/singular/sdk/Singular; suffix";
        List<String> found = ProviderSignatureDetector.detect(
                new ByteArrayInputStream(dexText.getBytes(StandardCharsets.UTF_8)));
        assertEquals(List.of(Provider.APPSFLYER, Provider.ADJUST, Provider.SINGULAR), found,
                "all SDK signatures");
    }

    private static void detectsSignatureAcrossReadBoundary() throws Exception {
        byte[] marker = "Lcom/appsflyer/AppsFlyerLib;".getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream data = new ByteArrayOutputStream();
        for (int i = 0; i < 8192 - 5; i++) data.write('x');
        data.write(marker);
        List<String> found = ProviderSignatureDetector.detect(
                new ByteArrayInputStream(data.toByteArray()));
        assertEquals(List.of(Provider.APPSFLYER), found, "signature split across chunks");
    }

    private static void ignoresUnrelatedText() throws Exception {
        byte[] text = "No attribution SDK included".getBytes(StandardCharsets.UTF_8);
        List<String> found = ProviderSignatureDetector.detect(new ByteArrayInputStream(text));
        assertEquals(List.of(), found, "unrelated text");
    }

    private static void assertEquals(Object expected, Object actual, String label) {
        if (!expected.equals(actual)) {
            throw new AssertionError(label + ": expected " + expected + " but got " + actual);
        }
    }
}
