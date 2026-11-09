package com.careforu.shop;

import android.app.Activity;
import android.os.Bundle;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebSettings;
import android.webkit.ConsoleMessage;
import android.graphics.Color;
import android.util.Log;
import android.view.View;
import android.widget.Toast;
import android.content.Intent;
import android.net.Uri;

public class MainActivity extends Activity {

    private WebView webView;
    private static final String TAG = "CARE_FOR_U";

    private static final int FILE_CHOOSER_REQUEST_CODE = 1001;

    private android.webkit.ValueCallback<Uri[]> filePathCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setStatusBarColor(Color.rgb(194, 24, 104));
        getWindow().setNavigationBarColor(Color.rgb(80, 12, 47));

        webView = new WebView(this);
        webView.setBackgroundColor(Color.WHITE);

        WebSettings settings = webView.getSettings();

        // JavaScript and storage
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);

        // Local HTML access
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setSupportMultipleWindows(false);

        // Security
        settings.setAllowFileAccessFromFileURLs(false);
        settings.setAllowUniversalAccessFromFileURLs(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);

        // Page handling
        webView.setWebViewClient(new WebViewClient() {

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                Log.d(TAG, "Page loaded: " + url);
            }

            @Override
            public void onReceivedError(
                    WebView view,
                    android.webkit.WebResourceRequest request,
                    android.webkit.WebResourceError error) {

                super.onReceivedError(view, request, error);

                if (request.isForMainFrame()) {
                    Log.e(TAG, "Page error: " + error.getDescription());

                    Toast.makeText(
                            MainActivity.this,
                            "App page load error",
                            Toast.LENGTH_LONG
                    ).show();
                }
            }
        });

        // Gallery / file chooser support
        webView.setWebChromeClient(new WebChromeClient() {

            @Override
            public boolean onShowFileChooser(
                    WebView webView,
                    android.webkit.ValueCallback<Uri[]> callback,
                    WebChromeClient.FileChooserParams fileChooserParams) {

                // Clear any previous pending request
                if (filePathCallback != null) {
                    filePathCallback.onReceiveValue(null);
                }

                filePathCallback = callback;

                try {
                    Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType("image/*");

                    Intent chooser = Intent.createChooser(
                            intent,
                            "Select Product Photo"
                    );

                    startActivityForResult(
                            chooser,
                            FILE_CHOOSER_REQUEST_CODE
                    );

                    return true;

                } catch (Exception e) {

                    Log.e(TAG, "Gallery opening error", e);

                    filePathCallback = null;

                    Toast.makeText(
                            MainActivity.this,
                            "Gallery open nahi ho paayi",
                            Toast.LENGTH_LONG
                    ).show();

                    return false;
                }
            }

            @Override
            public boolean onConsoleMessage(ConsoleMessage message) {

                Log.e(TAG,
                        "JavaScript: " + message.message()
                        + " | Line: " + message.lineNumber()
                        + " | Source: " + message.sourceId());

                return true;
            }
        });

        webView.setOverScrollMode(View.OVER_SCROLL_NEVER);

        setContentView(webView);

        webView.loadUrl("file:///android_asset/index.html");
    }

    // Receive selected image from Gallery
    @Override
    protected void onActivityResult(
            int requestCode,
            int resultCode,
            Intent data) {

        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == FILE_CHOOSER_REQUEST_CODE) {

            if (filePathCallback == null) {
                return;
            }

            Uri[] results = null;

            if (resultCode == Activity.RESULT_OK && data != null) {

                Uri selectedImage = data.getData();

                if (selectedImage != null) {
                    results = new Uri[]{selectedImage};
                }
            }

            filePathCallback.onReceiveValue(results);
            filePathCallback = null;
        }
    }

    @Override
    public void onBackPressed() {

        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {

        if (filePathCallback != null) {
            filePathCallback.onReceiveValue(null);
            filePathCallback = null;
        }

        if (webView != null) {
            webView.stopLoading();
            webView.loadUrl("about:blank");
            webView.clearHistory();
            webView.removeAllViews();
            webView.destroy();
            webView = null;
        }

        super.onDestroy();
    }
}
