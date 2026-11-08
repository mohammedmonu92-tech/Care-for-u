package com.careforu.shop;

import android.app.Activity;
import android.os.Bundle;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.JavascriptInterface;
import android.graphics.Color;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.PhoneAuthOptions;
import com.google.firebase.auth.PhoneAuthProvider;
import com.google.firebase.auth.PhoneAuthCredential;
import com.google.firebase.auth.PhoneAuthProvider.ForceResendingToken;

import java.util.concurrent.TimeUnit;

public class MainActivity extends Activity {

    private WebView webView;
    private FirebaseAuth auth;
    private String verificationId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        auth = FirebaseAuth.getInstance();

        getWindow().setStatusBarColor(Color.rgb(194, 24, 104));
        getWindow().setNavigationBarColor(Color.rgb(80, 12, 47));

        webView = new WebView(this);
        webView.setBackgroundColor(Color.WHITE);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.getSettings().setAllowFileAccess(true);

        webView.addJavascriptInterface(new AndroidBridge(), "Android");

        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new WebChromeClient());

        setContentView(webView);
        webView.loadUrl("file:///android_asset/index.html");
    }

    private void sendToPage(String script) {
        runOnUiThread(() -> webView.evaluateJavascript(script, null));
    }

    private void showError(String message) {
        sendToPage("window.onLoginError && window.onLoginError("
                + org.json.JSONObject.quote(message) + ")");
    }

    private class AndroidBridge {

        @JavascriptInterface
        public void startPhoneLogin(String phone) {
            String number = phone.trim();

            if (number.matches("[0-9]{10}")) {
                number = "+91" + number;
            }

            if (!number.matches("\\+[0-9]{10,15}")) {
                showError("Please enter a valid phone number.");
                return;
            }

            PhoneAuthOptions options =
                    PhoneAuthOptions.newBuilder(auth)
                    .setPhoneNumber(number)
                    .setTimeout(60L, TimeUnit.SECONDS)
                    .setActivity(MainActivity.this)
                    .setCallbacks(new PhoneAuthProvider.OnVerificationStateChangedCallbacks() {

                        @Override
                        public void onVerificationCompleted(PhoneAuthCredential credential) {
                            signIn(credential);
                        }

                        @Override
                        public void onVerificationFailed(
                                com.google.firebase.FirebaseException e) {
                            showError(e.getMessage());
                        }

                        @Override
                        public void onCodeSent(
                                String id,
                                ForceResendingToken token) {
                            verificationId = id;
                            sendToPage("window.onOtpSent && window.onOtpSent()");
                        }
                    })
                    .build();

            PhoneAuthProvider.verifyPhoneNumber(options);
        }

        @JavascriptInterface
        public void verifyOtp(String code) {
            if (verificationId == null) {
                showError("Please request an OTP first.");
                return;
            }

            PhoneAuthCredential credential =
                    PhoneAuthProvider.getCredential(verificationId, code.trim());

            signIn(credential);
        }
    }

    private void signIn(PhoneAuthCredential credential) {
        auth.signInWithCredential(credential)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        sendToPage("window.onLoginSuccess && window.onLoginSuccess()");
                    } else {
                        showError("OTP verification failed. Please try again.");
                    }
                });
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}
