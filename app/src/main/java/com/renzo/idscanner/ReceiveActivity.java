package com.renzo.idscanner;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

/** Receives an explicit diagnostic export from an authorized source app. */
public class ReceiveActivity extends Activity {
    public static final String EXTRA_PROVIDER = "com.renzo.idscanner.extra.PROVIDER";

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Intent in = getIntent();
        CharSequence cs = in == null ? null : in.getCharSequenceExtra(Intent.EXTRA_TEXT);
        String record = cs == null ? null : cs.toString();
        String provider = in == null ? null : in.getStringExtra(EXTRA_PROVIDER);
        if (provider == null || provider.isEmpty()) provider = Provider.LEGACY;

        // Require Android's verified startActivityForResult caller identity; do not trust
        // a package name supplied inside the payload or an unverified referrer string.
        String pkg = getCallingPackage();
        if (pkg == null || pkg.equals(getPackageName())) {
            Toast.makeText(this, "Rejected: verified sender unavailable", Toast.LENGTH_LONG).show();
            setResult(RESULT_CANCELED);
        } else if (!Provider.isSupported(provider)) {
            Toast.makeText(this, "Rejected: unsupported provider", Toast.LENGTH_LONG).show();
            setResult(RESULT_CANCELED);
        } else if (new RecordStore(this).save(pkg, provider, record)) {
            Toast.makeText(this, "Diagnostic record saved", Toast.LENGTH_SHORT).show();
            setResult(RESULT_OK);
        } else {
            Toast.makeText(this, "Rejected: invalid diagnostic record", Toast.LENGTH_LONG).show();
            setResult(RESULT_CANCELED);
        }
        finish();
    }
}
