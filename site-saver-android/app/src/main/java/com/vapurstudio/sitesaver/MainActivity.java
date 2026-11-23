package com.vapurstudio.sitesaver;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.JsonReader;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Tiny browser: you browse normally (and pass any "are you human" check
 * yourself), it remembers every file the page loads, and the side "Kaydet"
 * button packs all of them into one zip in Downloads.
 */
public class MainActivity extends Activity {
    private static final String HOME = "https://saga.rimaldm.com/game/late-night-drive/";

    private WebView web;
    private EditText urlBar;
    private TextView status;
    private Button saveBtn;
    private final Set<String> seen = Collections.synchronizedSet(new LinkedHashSet<>());
    private final Handler ui = new Handler(Looper.getMainLooper());
    private volatile boolean saving = false;

    private int dp(float v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.BLACK);

        urlBar = new EditText(this);
        urlBar.setSingleLine(true);
        urlBar.setTextSize(14);
        urlBar.setTextColor(Color.WHITE);
        urlBar.setHintTextColor(0xFF888888);
        urlBar.setHint("Adres yaz…");
        urlBar.setBackgroundColor(0xFF202020);
        urlBar.setPadding(dp(10), dp(6), dp(10), dp(6));
        urlBar.setImeOptions(EditorInfo.IME_ACTION_GO);
        urlBar.setOnEditorActionListener((v, id, ev) -> {
            String u = urlBar.getText().toString().trim();
            if (!u.isEmpty()) {
                if (!u.contains("://")) u = "https://" + u;
                web.loadUrl(u);
                ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE))
                        .hideSoftInputFromWindow(urlBar.getWindowToken(), 0);
                web.requestFocus();
            }
            return true;
        });
        root.addView(urlBar, new LinearLayout.LayoutParams(-1, -2));

        FrameLayout frame = new FrameLayout(this);
        root.addView(frame, new LinearLayout.LayoutParams(-1, 0, 1f));

        web = new WebView(this);
        frame.addView(web, new FrameLayout.LayoutParams(-1, -1));

        // Side buttons
        LinearLayout side = new LinearLayout(this);
        side.setOrientation(LinearLayout.VERTICAL);
        FrameLayout.LayoutParams sp = new FrameLayout.LayoutParams(-2, -2, Gravity.END | Gravity.CENTER_VERTICAL);
        sp.setMarginEnd(dp(6));
        frame.addView(side, sp);
        saveBtn = sideButton(side, "💾\nKaydet", 0xCC1E88E5, v -> saveAll());
        sideButton(side, "⟳", 0x99333333, v -> web.reload());
        sideButton(side, "←", 0x99333333, v -> { if (web.canGoBack()) web.goBack(); });

        status = new TextView(this);
        status.setTextColor(Color.WHITE);
        status.setTextSize(13);
        status.setPadding(dp(10), dp(6), dp(10), dp(6));
        GradientDrawable sbg = new GradientDrawable();
        sbg.setColor(0xCC000000);
        sbg.setCornerRadius(dp(8));
        status.setBackground(sbg);
        status.setVisibility(View.GONE);
        FrameLayout.LayoutParams stp = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        stp.bottomMargin = dp(16);
        frame.addView(status, stp);

        setContentView(root);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setSupportMultipleWindows(false);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true);

        web.setWebChromeClient(new WebChromeClient());
        web.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView v, WebResourceRequest r) {
                record(r.getUrl().toString());
                return null; // let WebView load it normally
            }

            @Override
            public void onPageStarted(WebView v, String url, android.graphics.Bitmap f) {
                record(url);
                urlBar.setText(url);
            }

            @Override
            public void onPageFinished(WebView v, String url) {
                urlBar.setText(url);
            }
        });

        if (Build.VERSION.SDK_INT < 29 && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
        }

        if (b != null) web.restoreState(b);
        else web.loadUrl(HOME);
    }

    private Button sideButton(LinearLayout parent, String text, int color, View.OnClickListener l) {
        Button btn = new Button(this);
        btn.setText(text);
        btn.setAllCaps(false);
        btn.setTextColor(Color.WHITE);
        btn.setTextSize(12);
        btn.setPadding(dp(4), dp(4), dp(4), dp(4));
        btn.setMinWidth(0);
        btn.setMinHeight(0);
        btn.setMinimumWidth(0);
        btn.setMinimumHeight(0);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color);
        bg.setCornerRadius(dp(12));
        btn.setBackground(bg);
        btn.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(58), dp(52));
        lp.topMargin = dp(6);
        parent.addView(btn, lp);
        return btn;
    }

    private void record(String u) {
        if (u != null && (u.startsWith("http://") || u.startsWith("https://"))) seen.add(u);
    }

    private void showStatus(String msg) {
        ui.post(() -> { status.setText(msg); status.setVisibility(View.VISIBLE); });
    }

    private void hideStatusLater() {
        ui.postDelayed(() -> status.setVisibility(View.GONE), 4000);
    }

    // ---------------------------------------------------------------- saving

    private static final String COLLECT_JS =
            "(function(){var o={dom:'<!DOCTYPE html>\\n'+document.documentElement.outerHTML,urls:[]};" +
            "try{performance.getEntriesByType('resource').forEach(function(e){o.urls.push(e.name)});}catch(e){}" +
            "document.querySelectorAll('[src],[href],[data-src]').forEach(function(e){" +
            "['src','href','data-src'].forEach(function(a){var v=e.getAttribute(a);if(v)try{o.urls.push(new URL(v,location.href).href)}catch(x){}})});" +
            "o.urls.push(location.href);return JSON.stringify(o);})()";

    private void saveAll() {
        if (saving) return;
        saving = true;
        saveBtn.setEnabled(false);
        showStatus("Dosyalar toplanıyor…");
        final String pageUrl = web.getUrl();
        final String ua = web.getSettings().getUserAgentString();
        web.evaluateJavascript(COLLECT_JS, raw -> {
            String dom = "";
            List<String> extra = new ArrayList<>();
            try {
                // evaluateJavascript returns a JSON-encoded string; unwrap it, then parse.
                JsonReader outer = new JsonReader(new StringReader(raw));
                outer.setLenient(true);
                String json = outer.nextString();
                JsonReader r = new JsonReader(new StringReader(json));
                r.beginObject();
                while (r.hasNext()) {
                    String k = r.nextName();
                    if ("dom".equals(k)) dom = r.nextString();
                    else if ("urls".equals(k)) {
                        r.beginArray();
                        while (r.hasNext()) extra.add(r.nextString());
                        r.endArray();
                    } else r.skipValue();
                }
                r.endObject();
            } catch (Exception ignored) { }
            for (String u : extra) record(u);
            final String domFinal = dom;
            new Thread(() -> doSave(pageUrl, ua, domFinal), "saver").start();
        });
    }

    private void doSave(String pageUrl, String ua, String dom) {
        List<String> urls;
        synchronized (seen) { urls = new ArrayList<>(seen); }
        String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new java.util.Date());
        String name = "site-" + stamp + ".zip";
        StringBuilder log = new StringBuilder();
        log.append("Sayfa: ").append(pageUrl).append('\n').append("UA: ").append(ua).append("\n\n");
        int ok = 0, fail = 0;
        Uri outUri = null;
        File outFile = null;
        try {
            OutputStream os;
            if (Build.VERSION.SDK_INT >= 29) {
                ContentValues cv = new ContentValues();
                cv.put(MediaStore.Downloads.DISPLAY_NAME, name);
                cv.put(MediaStore.Downloads.MIME_TYPE, "application/zip");
                cv.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                outUri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
                os = getContentResolver().openOutputStream(outUri);
            } else {
                File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                if (!dir.canWrite()) dir = getExternalFilesDir(null);
                dir.mkdirs();
                outFile = new File(dir, name);
                os = new FileOutputStream(outFile);
            }
            try (ZipOutputStream zip = new ZipOutputStream(os)) {
                Set<String> used = new HashSet<>();
                putEntry(zip, used, "_canli_sayfa.html", dom.getBytes(StandardCharsets.UTF_8));
                int i = 0;
                for (String u : urls) {
                    i++;
                    showStatus("İndiriliyor " + i + "/" + urls.size());
                    try {
                        byte[] data = fetch(u, ua, pageUrl);
                        putEntry(zip, used, entryName(u), data);
                        log.append("OK   ").append(data.length).append("  ").append(u).append('\n');
                        ok++;
                    } catch (Exception e) {
                        log.append("HATA ").append(e.getMessage()).append("  ").append(u).append('\n');
                        fail++;
                    }
                }
                putEntry(zip, used, "_liste.txt", log.toString().getBytes(StandardCharsets.UTF_8));
            }
            final Uri shareUri = outUri != null ? outUri : Uri.fromFile(outFile);
            final int fOk = ok, fFail = fail;
            ui.post(() -> {
                showStatus("Kaydedildi: İndirilenler/" + name + "  (" + fOk + " dosya"
                        + (fFail > 0 ? ", " + fFail + " hata" : "") + ")");
                hideStatusLater();
                Toast.makeText(this, "Kaydedildi: İndirilenler/" + name, Toast.LENGTH_LONG).show();
                share(shareUri);
            });
        } catch (Exception e) {
            ui.post(() -> {
                showStatus("Kaydetme hatası: " + e.getMessage());
                hideStatusLater();
            });
        } finally {
            ui.post(() -> { saving = false; saveBtn.setEnabled(true); });
        }
    }

    private void share(Uri uri) {
        if (!"content".equals(uri.getScheme())) return; // file:// can't be shared on new Android
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("application/zip");
        i.putExtra(Intent.EXTRA_STREAM, uri);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try { startActivity(Intent.createChooser(i, "Zip'i gönder")); } catch (Exception ignored) { }
    }

    private static byte[] fetch(String u, String ua, String referer) throws Exception {
        String cur = u;
        for (int hop = 0; hop < 5; hop++) {
            HttpURLConnection c = (HttpURLConnection) new URL(cur).openConnection();
            c.setInstanceFollowRedirects(false);
            c.setConnectTimeout(15000);
            c.setReadTimeout(30000);
            c.setRequestProperty("User-Agent", ua);
            if (referer != null) c.setRequestProperty("Referer", referer);
            c.setRequestProperty("Accept", "*/*");
            String ck = CookieManager.getInstance().getCookie(cur);
            if (ck != null) c.setRequestProperty("Cookie", ck);
            int code = c.getResponseCode();
            if (code >= 300 && code < 400 && c.getHeaderField("Location") != null) {
                cur = new URL(new URL(cur), c.getHeaderField("Location")).toString();
                c.disconnect();
                continue;
            }
            if (code >= 400) { c.disconnect(); throw new Exception("HTTP " + code); }
            try (InputStream in = c.getInputStream()) {
                ByteArrayOutputStream bo = new ByteArrayOutputStream();
                byte[] buf = new byte[16384];
                int n;
                while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
                return bo.toByteArray();
            } finally { c.disconnect(); }
        }
        throw new Exception("çok fazla yönlendirme");
    }

    private static String entryName(String u) {
        Uri p = Uri.parse(u);
        String host = p.getHost() == null ? "site" : p.getHost();
        String path = p.getPath() == null || p.getPath().isEmpty() ? "/" : p.getPath();
        if (path.endsWith("/")) path += "index.html";
        String q = p.getQuery();
        if (q != null && !q.isEmpty()) {
            String safe = q.replaceAll("[^A-Za-z0-9._=-]", "_");
            if (safe.length() > 60) safe = safe.substring(0, 60);
            path += "@" + safe;
        }
        return (host + path).replaceAll("[^A-Za-z0-9._/@=-]", "_").replaceAll("/+", "/");
    }

    private static void putEntry(ZipOutputStream zip, Set<String> used, String name, byte[] data) throws Exception {
        String n = name;
        for (int k = 2; used.contains(n); k++) n = name + "~" + k;
        used.add(n);
        zip.putNextEntry(new ZipEntry(n));
        zip.write(data);
        zip.closeEntry();
    }

    // ---------------------------------------------------------------- misc

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent e) {
        if (keyCode == KeyEvent.KEYCODE_BACK && web.canGoBack()) { web.goBack(); return true; }
        return super.onKeyDown(keyCode, e);
    }

    @Override protected void onPause() { super.onPause(); web.onPause(); CookieManager.getInstance().flush(); }
    @Override protected void onResume() { super.onResume(); web.onResume(); }
    @Override protected void onSaveInstanceState(Bundle o) { super.onSaveInstanceState(o); web.saveState(o); }
}
