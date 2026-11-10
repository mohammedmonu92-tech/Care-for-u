package com.careforu.shop;

import android.app.Activity;
import android.content.Intent;
import android.content.ActivityNotFoundException;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.*;
import android.graphics.Color;
import android.view.View;

import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;

import org.json.JSONObject;
import org.json.JSONArray;
import org.json.JSONException;

import java.util.HashMap;
import java.util.Map;
import java.util.Iterator;

public class MainActivity extends Activity {

    private WebView webView;
    private ValueCallback<Uri[]> fileCallback;

    private FirebaseAuth auth;
    private FirebaseFirestore db;

    private static final int FILE_REQUEST = 501;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        FirebaseApp.initializeApp(this);

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        getWindow().setStatusBarColor(Color.rgb(230, 0, 112));
        getWindow().setNavigationBarColor(Color.rgb(120, 0, 65));

        webView = new WebView(this);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);

        webView.setOverScrollMode(View.OVER_SCROLL_NEVER);

        webView.addJavascriptInterface(
            new FirebaseBridge(),
            "AndroidFirebase"
        );

        webView.setWebViewClient(new WebViewClient());

        webView.setWebChromeClient(new WebChromeClient() {

            @Override
            public boolean onShowFileChooser(
                WebView view,
                ValueCallback<Uri[]> callback,
                FileChooserParams params
            ) {
                if (fileCallback != null) {
                    fileCallback.onReceiveValue(null);
                }

                fileCallback = callback;

                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("image/*");
                intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);

                try {
                    Intent chooser = Intent.createChooser(
                        intent,
                        "Select Product Photos"
                    );

                    startActivityForResult(chooser, FILE_REQUEST);

                } catch (ActivityNotFoundException e) {
                    fileCallback.onReceiveValue(null);
                    fileCallback = null;
                    return false;
                }

                return true;
            }
        });

        setContentView(webView);

        webView.loadUrl("file:///android_asset/index.html");
    }

    private void sendResult(String callbackId, JSONObject result) {
        runOnUiThread(() -> {
            if (webView == null) return;

            String safeId = JSONObject.quote(callbackId);
            String safeResult = result.toString();

            webView.evaluateJavascript(
                "window.__firebaseCallback && " +
                "window.__firebaseCallback(" +
                safeId + "," + safeResult + ")",
                null
            );
        });
    }

    private JSONObject success(String message) throws JSONException {
        JSONObject result = new JSONObject();
        result.put("success", true);
        result.put("message", message);
        return result;
    }

    private JSONObject failure(String message) throws JSONException {
        JSONObject result = new JSONObject();
        result.put("success", false);
        result.put("message", message);
        return result;
    }

    private Map<String, Object> jsonToMap(JSONObject object)
        throws JSONException {

        Map<String, Object> map = new HashMap<>();

        Iterator<String> keys = object.keys();

        while (keys.hasNext()) {
            String key = keys.next();
            Object value = object.get(key);

            if (value instanceof JSONObject) {
                value = jsonToMap((JSONObject) value);
            } else if (value instanceof JSONArray) {
                value = jsonToList((JSONArray) value);
            } else if (value == JSONObject.NULL) {
                value = null;
            }

            map.put(key, value);
        }

        return map;
    }

    private java.util.List<Object> jsonToList(JSONArray array)
        throws JSONException {

        java.util.List<Object> list = new java.util.ArrayList<>();

        for (int i = 0; i < array.length(); i++) {
            Object value = array.get(i);

            if (value instanceof JSONObject) {
                value = jsonToMap((JSONObject) value);
            } else if (value instanceof JSONArray) {
                value = jsonToList((JSONArray) value);
            } else if (value == JSONObject.NULL) {
                value = null;
            }

            list.add(value);
        }

        return list;
    }

    public class FirebaseBridge {

        @JavascriptInterface
        public void signIn(
            String email,
            String password,
            String callbackId
        ) {
            auth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener(result -> {
                    try {
                        JSONObject data = success("Login successful");
                        data.put("uid", result.getUser().getUid());
                        data.put("email", result.getUser().getEmail());
                        sendResult(callbackId, data);
                    } catch (Exception e) {
                        try {
                            sendResult(callbackId, failure(e.getMessage()));
                        } catch (Exception ignored) {}
                    }
                })
                .addOnFailureListener(error -> {
                    try {
                        sendResult(callbackId, failure(error.getMessage()));
                    } catch (Exception ignored) {}
                });
        }

        @JavascriptInterface
        public void createAccount(
            String email,
            String password,
            String callbackId
        ) {
            auth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener(result -> {
                    try {
                        JSONObject data = success("Account created");
                        data.put("uid", result.getUser().getUid());
                        data.put("email", result.getUser().getEmail());
                        sendResult(callbackId, data);
                    } catch (Exception e) {
                        try {
                            sendResult(callbackId, failure(e.getMessage()));
                        } catch (Exception ignored) {}
                    }
                })
                .addOnFailureListener(error -> {
                    try {
                        sendResult(callbackId, failure(error.getMessage()));
                    } catch (Exception ignored) {}
                });
        }

        @JavascriptInterface
        public void resetPassword(
            String email,
            String callbackId
        ) {
            auth.sendPasswordResetEmail(email)
                .addOnSuccessListener(unused -> {
                    try {
                        sendResult(callbackId,
                            success("Password reset email sent"));
                    } catch (Exception ignored) {}
                })
                .addOnFailureListener(error -> {
                    try {
                        sendResult(callbackId, failure(error.getMessage()));
                    } catch (Exception ignored) {}
                });
        }

        @JavascriptInterface
        public void signOut() {
            auth.signOut();
        }

        @JavascriptInterface
        public String currentUser() {
            if (auth.getCurrentUser() == null) {
                return "";
            }

            try {
                JSONObject data = new JSONObject();
                data.put("uid", auth.getCurrentUser().getUid());
                data.put("email", auth.getCurrentUser().getEmail());
                return data.toString();
            } catch (Exception e) {
                return "";
            }
        }

        @JavascriptInterface
        public void saveDocument(
            String collection,
            String documentId,
            String jsonData,
            String callbackId
        ) {
            try {
                JSONObject object = new JSONObject(jsonData);
                Map<String, Object> data = jsonToMap(object);

                db.collection(collection)
                    .document(documentId)
                    .set(data)
                    .addOnSuccessListener(unused -> {
                        try {
                            sendResult(callbackId,
                                success("Data saved"));
                        } catch (Exception ignored) {}
                    })
                    .addOnFailureListener(error -> {
                        try {
                            sendResult(callbackId,
                                failure(error.getMessage()));
                        } catch (Exception ignored) {}
                    });

            } catch (Exception e) {
                try {
                    sendResult(callbackId, failure(e.getMessage()));
                } catch (Exception ignored) {}
            }
        }

        @JavascriptInterface
        public void getDocument(
            String collection,
            String documentId,
            String callbackId
        ) {
            db.collection(collection)
                .document(documentId)
                .get()
                .addOnSuccessListener(document -> {
                    try {
                        JSONObject result = success("Data loaded");
                        result.put(
                            "data",
                            document.exists()
                                ? new JSONObject(document.getData())
                                : JSONObject.NULL
                        );
                        sendResult(callbackId, result);
                    } catch (Exception e) {
                        try {
                            sendResult(callbackId, failure(e.getMessage()));
                        } catch (Exception ignored) {}
                    }
                })
                .addOnFailureListener(error -> {
                    try {
                        sendResult(callbackId, failure(error.getMessage()));
                    } catch (Exception ignored) {}
                });
        }

        @JavascriptInterface
        public void getCollection(
            String collection,
            String callbackId
        ) {
            db.collection(collection)
                .get()
                .addOnSuccessListener(snapshot -> {
                    try {
                        JSONArray array = new JSONArray();

                        for (DocumentSnapshot document : snapshot) {
                            JSONObject item =
                                new JSONObject(document.getData());

                            item.put("id", document.getId());
                            array.put(item);
                        }

                        JSONObject result = success("Data loaded");
                        result.put("data", array);

                        sendResult(callbackId, result);

                    } catch (Exception e) {
                        try {
                            sendResult(callbackId, failure(e.getMessage()));
                        } catch (Exception ignored) {}
                    }
                })
                .addOnFailureListener(error -> {
                    try {
                        sendResult(callbackId, failure(error.getMessage()));
                    } catch (Exception ignored) {}
                });
        }
    }

    @Override
    protected void onActivityResult(
        int requestCode,
        int resultCode,
        Intent data
    ) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == FILE_REQUEST && fileCallback != null) {
            Uri[] results = null;

            if (resultCode == RESULT_OK && data != null) {
                if (data.getClipData() != null) {
                    int count = data.getClipData().getItemCount();
                    results = new Uri[count];

                    for (int i = 0; i < count; i++) {
                        results[i] = data.getClipData()
                            .getItemAt(i).getUri();
                    }

                } else if (data.getData() != null) {
                    results = new Uri[]{data.getData()};
                }
            }

            fileCallback.onReceiveValue(results);
            fileCallback = null;
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
        if (fileCallback != null) {
            fileCallback.onReceiveValue(null);
            fileCallback = null;
        }

        if (webView != null) {
            webView.destroy();
            webView = null;
        }

        super.onDestroy();
    }
}
