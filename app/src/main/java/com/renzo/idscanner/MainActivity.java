package com.renzo.idscanner;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int BG = Color.rgb(18, 18, 18);
    private static final int FG = Color.rgb(235, 235, 235);
    private static final int MUTED = Color.rgb(160, 160, 160);
    private static final int ACCENT = Color.rgb(220, 160, 55);

    private static final class Item {
        String label, pkg, category, sourceDir;
        String[] splitSourceDirs;
        Drawable icon;
        List<String> detectedProviders = Collections.emptyList();
        List<String> rootProviders = Collections.emptyList();
    }

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final List<Item> items = new ArrayList<>();
    private RecordStore store;
    private BaseAdapter adapter;
    private TextView rootStatus;
    private Button rootButton;
    private TextView gaidText;
    private Button gaidRefreshButton;
    private volatile String gaidValue;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        store = new RecordStore(this);
        float d = getResources().getDisplayMetrics().density;

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(BG);

        // The Advertising ID belongs to this Android device/profile, not to an
        // individual game. Read it through the official Google Play services API.
        LinearLayout gaidBar = new LinearLayout(this);
        gaidBar.setGravity(Gravity.CENTER_VERTICAL);
        gaidBar.setPadding((int) (12 * d), (int) (5 * d), (int) (8 * d), (int) (3 * d));
        gaidBar.setBackgroundColor(Color.rgb(30, 30, 30));

        gaidText = new TextView(this);
        gaidText.setText("DEVICE GAID: READING...");
        gaidText.setTextColor(FG);
        gaidText.setTextSize(10);
        gaidText.setMaxLines(2);
        gaidText.setOnClickListener(v -> copyGaid());
        gaidBar.addView(gaidText, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        gaidRefreshButton = new Button(this);
        gaidRefreshButton.setText("REFRESH GAID");
        gaidRefreshButton.setTextSize(9);
        gaidRefreshButton.setAllCaps(false);
        gaidRefreshButton.setOnClickListener(v -> refreshGaid());
        gaidBar.addView(gaidRefreshButton, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, (int) (40 * d)));
        page.addView(gaidBar);

        LinearLayout rootBar = new LinearLayout(this);
        rootBar.setGravity(Gravity.CENTER_VERTICAL);
        rootBar.setPadding((int) (12 * d), (int) (8 * d), (int) (8 * d), (int) (4 * d));
        rootStatus = new TextView(this);
        rootStatus.setText("ROOT: NOT CHECKED");
        rootStatus.setTextColor(MUTED);
        rootStatus.setTextSize(11);
        rootStatus.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        rootBar.addView(rootStatus, new LinearLayout.LayoutParams(0, (int) (40 * d), 1f));

        rootButton = new Button(this);
        rootButton.setText("SCAN WITH ROOT");
        rootButton.setTextSize(10);
        rootButton.setAllCaps(false);
        rootButton.setOnClickListener(v -> scanWithRoot());
        rootBar.addView(rootButton, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, (int) (40 * d)));
        page.addView(rootBar);

        GridView grid = new GridView(this);
        grid.setBackgroundColor(BG);
        grid.setNumColumns(4);
        grid.setVerticalSpacing((int) (8 * d));
        grid.setHorizontalSpacing((int) (2 * d));
        grid.setPadding((int) (6 * d), (int) (4 * d), (int) (6 * d), (int) (8 * d));
        grid.setClipToPadding(false);
        grid.setSelector(android.R.color.transparent);
        adapter = new Adapter(d);
        grid.setAdapter(adapter);
        grid.setOnItemClickListener((p, v, pos, id) -> onAppTap(items.get(pos)));
        page.addView(grid, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(page);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        refreshGaid();
        scanWithRoot();
    }

    /** Reads this device/profile's GAID through Google Play services off the UI thread. */
    private void refreshGaid() {
        if (gaidText == null || gaidRefreshButton == null) return;
        gaidText.setText("DEVICE GAID: READING...");
        gaidText.setTextColor(MUTED);
        gaidRefreshButton.setEnabled(false);
        io.execute(() -> {
            String id = null;
            String resultText;
            try {
                com.google.android.gms.ads.identifier.AdvertisingIdClient.Info info =
                        com.google.android.gms.ads.identifier.AdvertisingIdClient
                                .getAdvertisingIdInfo(getApplicationContext());
                String fetched = info == null ? null : info.getId();
                if (!TextUtils.isEmpty(fetched)
                        && !"00000000-0000-0000-0000-000000000000".equals(fetched)) {
                    id = fetched;
                    resultText = "DEVICE GAID: " + fetched + "  •  TAP TO COPY";
                } else {
                    resultText = "DEVICE GAID: UNAVAILABLE (ZEROED / RESET)";
                }
            } catch (Exception e) {
                resultText = "DEVICE GAID: UNAVAILABLE (GOOGLE PLAY SERVICES)";
            }
            final String finalId = id;
            final String finalText = resultText;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed() || gaidText == null) return;
                gaidValue = finalId;
                gaidText.setText(finalText);
                gaidText.setTextColor(finalId == null ? MUTED : Color.rgb(85, 190, 115));
                gaidRefreshButton.setEnabled(true);
            });
        });
    }

    private void copyGaid() {
        if (TextUtils.isEmpty(gaidValue)) {
            Toast.makeText(this, "GAID is not available on this device", Toast.LENGTH_SHORT).show();
            return;
        }
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("GAID", gaidValue));
        Toast.makeText(this, "Device GAID copied", Toast.LENGTH_SHORT).show();
    }

    private void loadApps() {
        PackageManager pm = getPackageManager();
        Intent q = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> ris = pm.queryIntentActivities(q, 0);
        Map<String, Item> unique = new LinkedHashMap<>();
        for (ResolveInfo ri : ris) {
            if (ri.activityInfo == null) continue;
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(getPackageName()) || unique.containsKey(pkg)) continue;
            try {
                ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
                Item it = new Item();
                it.pkg = pkg;
                it.label = String.valueOf(ri.loadLabel(pm));
                it.icon = ri.loadIcon(pm);
                it.category = getCategory(ai);
                it.rootProviders = store.getRootProviders(pkg);
                it.sourceDir = ai.sourceDir;
                it.splitSourceDirs = ai.splitSourceDirs;
                unique.put(pkg, it);
            } catch (PackageManager.NameNotFoundException ignored) {
                // App was uninstalled while the launcher list was being read.
            }
        }
        List<Item> out = new ArrayList<>(unique.values());
        Collections.sort(out, (a, z) -> a.label.compareToIgnoreCase(z.label));
        runOnUiThread(() -> {
            if (isFinishing() || isDestroyed()) return;
            items.clear();
            items.addAll(out);
            adapter.notifyDataSetChanged();
        });

        // Inspect installed APK DEX files on the background executor. This only
        // identifies SDK code bundled in an APK; it does not read app data.
        for (Item item : out) {
            item.detectedProviders = SdkScanner.scan(item.sourceDir, item.splitSourceDirs);
        }
        runOnUiThread(() -> {
            if (!isFinishing() && !isDestroyed() && adapter != null) {
                adapter.notifyDataSetChanged();
            }
        });
    }

    private String getCategory(ApplicationInfo ai) {
        if (Build.VERSION.SDK_INT < 26) return "UNKNOWN";
        if (ai.category == ApplicationInfo.CATEGORY_GAME) return "GAME";
        // Android exposes a single explicit GAME category; other visible launcher
        // applications are grouped as APP. UNKNOWN is reserved for unavailable metadata.
        return "APP";
    }

    private void onAppTap(Item it) {
        Map<String, String> records = store.getForPackage(it.pkg);
        if (records.size() == 1) {
            String provider = records.keySet().iterator().next();
            copyRecord(it.pkg, provider);
        } else if (records.size() > 1) {
            Toast.makeText(this, "Tap a provider label below the icon", Toast.LENGTH_SHORT).show();
        } else if (!it.rootProviders.isEmpty()) {
            Toast.makeText(this, "Provider files found, but no readable ID value was extracted",
                    Toast.LENGTH_LONG).show();
        } else if (!it.detectedProviders.isEmpty()) {
            Toast.makeText(this, "SDK detected in APK; no ID record was found",
                    Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(this, "No supported SDK signature detected", Toast.LENGTH_SHORT).show();
        }
    }

    private void copyRecord(String pkg, String provider) {
        String rec = store.get(pkg, provider);
        if (rec == null) {
            Toast.makeText(this, "No diagnostic data", Toast.LENGTH_SHORT).show();
            return;
        }
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("", rec);
        cm.setPrimaryClip(clip);
        Toast.makeText(this, "Copied " + Provider.displayName(provider), Toast.LENGTH_SHORT).show();
    }

    private void scanWithRoot() {
        if (items.isEmpty()) {
            Toast.makeText(this, "App list is still loading", Toast.LENGTH_SHORT).show();
            return;
        }

        List<String> packages = new ArrayList<>();
        Map<String, Item> byPackageSnapshot = new LinkedHashMap<>();
        for (Item item : items) {
            packages.add(item.pkg);
            byPackageSnapshot.put(item.pkg, item);
        }

        rootStatus.setText("ROOT SCAN: WAITING FOR APPROVAL");
        rootStatus.setTextColor(ACCENT);
        rootButton.setText("SCANNING...");
        rootButton.setEnabled(false);

        io.execute(() -> {
            RootAttributionScanner.Report report;
            try {
                report = RootAttributionScanner.scan(packages);
            } catch (IOException e) {
                runOnUiThread(() -> finishRootScan("ROOT NOT AVAILABLE", MUTED));
                return;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                runOnUiThread(() -> finishRootScan("ROOT SCAN INTERRUPTED", MUTED));
                return;
            }

            if (!report.rootAvailable) {
                String message = "ROOT ACCESS DENIED";
                runOnUiThread(() -> finishRootScan(message, Color.rgb(230, 95, 85)));
                return;
            }

            int savedValues = 0;
            int providerFiles = 0;
            for (String pkg : packages) store.clearRootProviders(pkg);
            for (Map.Entry<String, java.util.Set<String>> entry
                    : report.providersByPackage.entrySet()) {
                Item item = byPackageSnapshot.get(entry.getKey());
                if (item != null) item.rootProviders = new ArrayList<>(entry.getValue());
                for (String provider : entry.getValue()) {
                    store.markRootProvider(entry.getKey(), provider);
                }
                providerFiles += entry.getValue().size();
            }
            for (Map.Entry<String, Map<String, String>> appEntry
                    : report.recordsByPackage.entrySet()) {
                for (Map.Entry<String, String> record : appEntry.getValue().entrySet()) {
                    if (store.save(appEntry.getKey(), record.getKey(), record.getValue())) {
                        savedValues++;
                    }
                }
            }

            int finalSavedValues = savedValues;
            int finalProviderFiles = providerFiles;
            String status = "ROOT AVAILABLE  |  " + report.scannedPackages
                    + " APPS  |  " + finalSavedValues + " IDS";
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                rootStatus.setText(status);
                rootStatus.setTextColor(Color.rgb(85, 190, 115));
                rootButton.setText("SCAN AGAIN");
                rootButton.setEnabled(true);
                if (adapter != null) adapter.notifyDataSetChanged();
                Toast.makeText(MainActivity.this,
                        finalSavedValues + " values saved; " + finalProviderFiles
                                + " provider files found locally",
                        Toast.LENGTH_LONG).show();
            });
        });
    }

    private void finishRootScan(String status, int color) {
        if (isFinishing() || isDestroyed()) return;
        rootStatus.setText(status);
        rootStatus.setTextColor(color);
        rootButton.setText("SCAN WITH ROOT");
        rootButton.setEnabled(true);
    }

    @Override protected void onResume() {
        super.onResume();
        if (adapter != null) io.execute(this::loadApps);
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        io.shutdownNow();
    }

    private final class Adapter extends BaseAdapter {
        private final float d;
        Adapter(float d) { this.d = d; }
        @Override public int getCount() { return items.size(); }
        @Override public Object getItem(int i) { return items.get(i); }
        @Override public long getItemId(int i) { return i; }

        @Override
        public View getView(int pos, View convertView, ViewGroup parent) {
            LinearLayout cell;
            if (convertView instanceof LinearLayout) {
                cell = (LinearLayout) convertView;
                cell.removeAllViews();
            } else {
                cell = new LinearLayout(MainActivity.this);
                cell.setOrientation(LinearLayout.VERTICAL);
                cell.setGravity(Gravity.CENTER_HORIZONTAL);
                cell.setPadding((int) (2 * d), (int) (3 * d), (int) (2 * d), (int) (3 * d));
            }

            Item it = items.get(pos);
            ImageView img = new ImageView(MainActivity.this);
            img.setScaleType(ImageView.ScaleType.FIT_CENTER);
            img.setImageDrawable(it.icon);
            img.setContentDescription(it.label);
            int iconSize = (int) (52 * d);
            cell.addView(img, new LinearLayout.LayoutParams(iconSize, iconSize));

            TextView category = new TextView(MainActivity.this);
            category.setText(it.category);
            category.setTextColor(ACCENT);
            category.setTextSize(9);
            category.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            category.setGravity(Gravity.CENTER);
            category.setSingleLine(true);
            cell.addView(category, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            Map<String, String> records = store.getForPackage(it.pkg);
            List<String> providersToShow = new ArrayList<>();
            for (String provider : Provider.ORDERED) {
                if (it.detectedProviders.contains(provider) || it.rootProviders.contains(provider)
                        || records.containsKey(provider)) {
                    providersToShow.add(provider);
                }
            }
            if (records.containsKey(Provider.LEGACY)) providersToShow.add(Provider.LEGACY);

            img.setAlpha(providersToShow.isEmpty() ? 0.50f : 1f);
            for (String provider : providersToShow) {
                TextView tag = new TextView(MainActivity.this);
                tag.setText(Provider.displayName(provider));
                tag.setTextColor(records.containsKey(provider)
                        ? FG : Color.rgb(110, 190, 225));
                tag.setTextSize(8);
                tag.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
                tag.setGravity(Gravity.CENTER);
                tag.setSingleLine(true);
                tag.setEllipsize(TextUtils.TruncateAt.END);
                tag.setPadding((int) (2 * d), 0, (int) (2 * d), 0);
                if (records.containsKey(provider)) {
                    tag.setOnClickListener(v -> copyRecord(it.pkg, provider));
                    tag.setContentDescription(Provider.displayName(provider) + ", diagnostic record available; tap to copy");
                } else if (it.rootProviders.contains(provider)) {
                    tag.setContentDescription(Provider.displayName(provider)
                            + " preference file found; no ID value available to copy");
                    tag.setOnClickListener(v -> {
                        String message = Provider.ADJUST.equals(provider)
                                ? "Adjust file found; the reference APK does not extract an Adjust ID"
                                : "Preference file found, but no ID value was present";
                        Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show();
                    });
                } else {
                    tag.setContentDescription(Provider.displayName(provider)
                            + " SDK detected in installed APK; no ID record available");
                    tag.setOnClickListener(v -> Toast.makeText(MainActivity.this,
                            Provider.displayName(provider) + " found in APK code; no ID record available",
                            Toast.LENGTH_SHORT).show());
                }
                cell.addView(tag, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, (int) (16 * d)));
            }
            return cell;
        }
    }
}
