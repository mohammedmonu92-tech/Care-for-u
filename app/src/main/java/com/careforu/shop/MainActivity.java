package com.careforu.shop;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.*;
import android.graphics.Color;
import android.view.View;

import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.DocumentSnapshot;

import org.json.JSONObject;
import org.json.JSONArray;

import java.util.HashMap;
import java.util.Map;
import java.util.Iterator;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {

    private WebView webView;
    private ValueCallback<Uri[]> fileCallback;

    private FirebaseAuth auth;
    private FirebaseFirestore db;

    private static final int FILE_REQUEST = 501;

    private static final String ADMIN_UID =
        "wAAMhfDpR1YJjbBrNPaaF5jGsr52";

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

                Intent intent = new Intent(
                    Intent.ACTION_GET_CONTENT
                );

                intent.addCategory(
                    Intent.CATEGORY_OPENABLE
                );

                intent.setType("image/*");

                intent.putExtra(
                    Intent.EXTRA_ALLOW_MULTIPLE,
                    true
                );

                try {

                    Intent chooser = Intent.createChooser(
                        intent,
                        "Select Product Photos"
                    );

                    startActivityForResult(
                        chooser,
                        FILE_REQUEST
                    );

                } catch (Exception e) {

                    fileCallback.onReceiveValue(null);
                    fileCallback = null;

                    return false;
                }

                return true;
            }
        });

        setContentView(webView);

        webView.loadUrl(
            "file:///android_asset/index.html"
        );
    }

    // GALLERY RESULT

    @Override
    protected void onActivityResult(
        int requestCode,
        int resultCode,
        Intent data
    ) {

        super.onActivityResult(
            requestCode,
            resultCode,
            data
        );

        if (requestCode == FILE_REQUEST) {

            if (fileCallback == null) return;

            Uri[] results = null;

            if (resultCode == RESULT_OK && data != null) {

                if (data.getClipData() != null) {

                    int count = data.getClipData().getItemCount();

                    results = new Uri[count];

                    for (int i = 0; i < count; i++) {

                        results[i] =
                            data.getClipData()
                                .getItemAt(i)
                                .getUri();
                    }

                } else if (data.getData() != null) {

                    results = new Uri[]{
                        data.getData()
                    };
                }
            }

            fileCallback.onReceiveValue(results);
            fileCallback = null;
        }
    }

    private void sendResult(
        String callbackId,
        JSONObject result
    ) {

        runOnUiThread(() -> {

            if (webView == null) return;

            webView.evaluateJavascript(
                "window.__firebaseCallback && " +
                "window.__firebaseCallback(" +
                JSONObject.quote(callbackId) +
                "," + result.toString() + ")",
                null
            );
        });
    }

    private JSONObject makeResult(
        boolean success,
        String message
    ) throws Exception {

        JSONObject result = new JSONObject();

        result.put("success", success);
        result.put("message", message == null ? "" : message);

        return result;
    }

    private void sendSuccess(
        String id,
        String message
    ) {

        try {
            sendResult(
                id,
                makeResult(true, message)
            );
        } catch (Exception ignored) {}
    }

    private void sendError(
        String id,
        String message
    ) {

        try {
            sendResult(
                id,
                makeResult(false, message)
            );
        } catch (Exception ignored) {}
    }

    private boolean allowedCollection(String collection) {

        return collection.equals("products")
            || collection.equals("orders")
            || collection.equals("reviews")
            || collection.equals("users");
    }

    private Map<String, Object> jsonToMap(
        JSONObject object
    ) throws Exception {

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

    private List<Object> jsonToList(
        JSONArray array
    ) throws Exception {

        List<Object> list = new ArrayList<>();

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

        // LOGIN

        @JavascriptInterface
        public void signIn(
            String email,
            String password,
            String callbackId
        ) {

            auth.signInWithEmailAndPassword(
                email,
                password
            )

            .addOnSuccessListener(result -> {

                try {

                    JSONObject data =
                        makeResult(true, "Login successful");

                    data.put(
                        "uid",
                        result.getUser().getUid()
                    );

                    data.put(
                        "email",
                        result.getUser().getEmail()
                    );

                    sendResult(callbackId, data);

                } catch (Exception e) {

                    sendError(callbackId, e.getMessage());
                }

            })

            .addOnFailureListener(error ->
                sendError(callbackId, error.getMessage())
            );
        }

        // REGISTER

        @JavascriptInterface
        public void createAccount(
            String email,
            String password,
            String callbackId
        ) {

            auth.createUserWithEmailAndPassword(
                email,
                password
            )

            .addOnSuccessListener(result -> {

                try {

                    JSONObject data =
                        makeResult(true, "Account created");

                    data.put(
                        "uid",
                        result.getUser().getUid()
                    );

                    data.put(
                        "email",
                        result.getUser().getEmail()
                    );

                    sendResult(callbackId, data);

                } catch (Exception e) {

                    sendError(callbackId, e.getMessage());
                }

            })

            .addOnFailureListener(error ->
                sendError(callbackId, error.getMessage())
            );
        }

        // PASSWORD RESET

        @JavascriptInterface
        public void resetPassword(
            String email,
            String callbackId
        ) {

            auth.sendPasswordResetEmail(email)

            .addOnSuccessListener(unused ->
                sendSuccess(
                    callbackId,
                    "Password reset email sent"
                )
            )

            .addOnFailureListener(error ->
                sendError(callbackId, error.getMessage())
            );
        }

        // CURRENT USER

        @JavascriptInterface
        public String currentUser() {

            if (auth.getCurrentUser() == null) {
                return "";
            }

            try {

                JSONObject data = new JSONObject();

                data.put(
                    "uid",
                    auth.getCurrentUser().getUid()
                );

                data.put(
                    "email",
                    auth.getCurrentUser().getEmail()
                );

                return data.toString();

            } catch (Exception e) {

                return "";
            }
        }

        // LOGOUT

        @JavascriptInterface
        public void signOut() {
            auth.signOut();
        }

        // SAVE DOCUMENT

        @JavascriptInterface
        public void saveDocument(
            String collection,
            String documentId,
            String jsonData,
            String callbackId
        ) {

            if (!allowedCollection(collection)) {
                sendError(callbackId, "Invalid collection");
                return;
            }

            if (auth.getCurrentUser() == null) {
                sendError(callbackId, "Please login first");
                return;
            }

            try {

                JSONObject object =
                    new JSONObject(jsonData);

                Map<String, Object> data =
                    jsonToMap(object);

                db.collection(collection)
                    .document(documentId)
                    .set(data)

                    .addOnSuccessListener(unused ->
                        sendSuccess(
                            callbackId,
                            "Data saved successfully"
                        )
                    )

                    .addOnFailureListener(error ->
                        sendError(
                            callbackId,
                            error.getMessage()
                        )
                    );

            } catch (Exception e) {

                sendError(callbackId, e.getMessage());
            }
        }

        // GET DOCUMENT

        @JavascriptInterface
        public void getDocument(
            String collection,
            String documentId,
            String callbackId
        ) {

            if (!allowedCollection(collection)) {
                sendError(callbackId, "Invalid collection");
                return;
            }

            if (auth.getCurrentUser() == null) {
                sendError(callbackId, "Please login first");
                return;
            }

            db.collection(collection)
                .document(documentId)
                .get()

                .addOnSuccessListener(document -> {

                    try {

                        JSONObject result =
                            makeResult(true, "Data loaded");

                        if (document.exists()) {

                            result.put(
                                "data",
                                new JSONObject(document.getData())
                            );

                        } else {

                            result.put(
                                "data",
                                JSONObject.NULL
                            );
                        }

                        sendResult(callbackId, result);

                    } catch (Exception e) {

                        sendError(callbackId, e.getMessage());
                    }

                })

                .addOnFailureListener(error ->
                    sendError(
                        callbackId,
                        error.getMessage()
                    )
                );
        }

        // GET COLLECTION

        @JavascriptInterface
        public void getCollection(
            String collection,
            String callbackId
        ) {

            if (!allowedCollection(collection)) {
                sendError(callbackId, "Invalid collection");
                return;
            }

            if (auth.getCurrentUser() == null) {
                sendError(callbackId, "Please login first");
                return;
            }

            Query query = db.collection(collection);

            // CUSTOMER: ONLY OWN ORDERS
            // ADMIN: ALL ORDERS

            if (
                collection.equals("orders") &&
                !ADMIN_UID.equals(
                    auth.getCurrentUser().getUid()
                )
            ) {

                query = query.whereEqualTo(
                    "uid",
                    auth.getCurrentUser().getUid()
                );
            }

            query.get()

                .addOnSuccessListener(snapshot -> {

                    try {

                        JSONArray array = new JSONArray();

                        for (
                            DocumentSnapshot document :
                            snapshot.getDocuments()
                        ) {

                            JSONObject item =
                                new JSONObject(document.getData());

                            item.put(
                                "id",
                                document.getId()
                            );

                            array.put(item);
                        }

                        JSONObject result =
                            makeResult(true, "Data loaded");

                        result.put("data", array);

                        sendResult(callbackId, result);

                    } catch (Exception e) {

                        sendError(callbackId, e.getMessage());
                    }

                })

                .addOnFailureListener(error ->
                    sendError(
                        callbackId,
                        error.getMessage()
                    )
                );
        }

        // DELETE DOCUMENT

        @JavascriptInterface
        public void deleteDocument(
            String collection,
            String documentId,
            String callbackId
        ) {

            if (!allowedCollection(collection)) {
                sendError(callbackId, "Invalid collection");
                return;
            }

            if (auth.getCurrentUser() == null) {
                sendError(callbackId, "Please login first");
                return;
            }

            db.collection(collection)
                .document(documentId)
                .delete()

                .addOnSuccessListener(unused ->
                    sendSuccess(
                        callbackId,
                        "Deleted successfully"
                    )
                )

                .addOnFailureListener(error ->
                    sendError(
                        callbackId,
                        error.getMessage()
                    )
                );
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

        if (webView != null) {

            webView.destroy();
            webView = null;
        }

        super.onDestroy();
    }
}
