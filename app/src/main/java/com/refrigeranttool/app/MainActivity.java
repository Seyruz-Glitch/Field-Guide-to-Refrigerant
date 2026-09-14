package com.refrigeranttool.app;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class MainActivity extends Activity {

    private static final String TAG = "RefrigTool";
    private WebView webView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Full-screen: hide system decorations
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        );

        webView = new WebView(this);
        setContentView(webView);

        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setCacheMode(WebSettings.LOAD_CACHE_ELSE_NETWORK);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setBuiltInZoomControls(false);

        webView.setWebViewClient(new WebViewClient());
        webView.addJavascriptInterface(new AndroidBridge(), "Android");
        webView.loadUrl("file:///android_asset/index.html");
    }

    // ── JavaScript → Android bridge ──────────────────────────────────────────
    private class AndroidBridge {

        @JavascriptInterface
        public void downloadAndInstall(final String apkUrl) {
            new Thread(() -> {
                File outDir  = new File(getCacheDir(), "apk_downloads");
                outDir.mkdirs();
                File apkFile = new File(outDir, "update.apk");

                try {
                    // Notify JS: download starting
                    runOnUiThread(() -> webView.evaluateJavascript(
                        "window._apkStatus && window._apkStatus('downloading')", null));

                    // Download the APK
                    URL url = new URL(apkUrl);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setConnectTimeout(15000);
                    conn.setReadTimeout(60000);
                    conn.connect();

                    if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) {
                        throw new Exception("HTTP " + conn.getResponseCode());
                    }

                    try (InputStream in = conn.getInputStream();
                         FileOutputStream out = new FileOutputStream(apkFile)) {
                        byte[] buf = new byte[8192];
                        int n;
                        while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
                    }

                    // Notify JS: launching installer
                    runOnUiThread(() -> webView.evaluateJavascript(
                        "window._apkStatus && window._apkStatus('installing')", null));

                    // Fire the system install intent via FileProvider
                    Uri apkUri = FileProvider.getUriForFile(
                        MainActivity.this,
                        getPackageName() + ".fileprovider",
                        apkFile
                    );
                    Intent intent = new Intent(Intent.ACTION_VIEW);
                    intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                                  | Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);

                } catch (Exception e) {
                    Log.e(TAG, "APK download failed", e);
                    final String msg = e.getMessage();
                    runOnUiThread(() -> webView.evaluateJavascript(
                        "window._apkStatus && window._apkStatus('error', '" + msg + "')", null));
                }
            }).start();
        }
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}
