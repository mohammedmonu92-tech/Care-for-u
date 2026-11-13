
package com.careforu.shop;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.*;
import android.graphics.Color;
import android.view.View;
import android.widget.Toast;

import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserProfileChangeRequest;
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

    private long backPressedTime = 0;

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

                intent.addCategory(Intent.CATEGORY_OPENABLE);
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
                "(function() {" +
                "var id = " + JSONObject.quote(callbackId) + ";" +
                "var result = " + result.toString() + ";" +
                "if (window.onFirebaseResult) {" +
                "window.onFirebaseResult(id, result);" +
                "} else if (window.__firebaseCallback) {" +
                "window.__firebaseCallback(id, result);" +
                "}" +
                "})()",
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

    private void sendSuccess(String id, String message) {

        try {
            sendResult(id, makeResult(true, message));
        } catch (Exception ignored) {}
    }

    private void sendError(String id, String message) {

        try {
            sendResult(id, makeResult(false, message));
        } catch (Exception ignored) {}
    }

    private boolean allowedCollection(String collection) {

        return collection.equals("products")
            || collection.equals("orders")
            || collection.equals("reviews")
            || collection.equals("users");
    }

    private boolean isAdmin() {

        FirebaseUser user = auth.getCurrentUser();

        return user != null &&
            ADMIN_UID.equals(user.getUid());
    }

    private boolean isLoggedIn() {
        return auth.getCurrentUser() != null;
    }

    private boolean canReadDocument(
        String collection,
        String documentId
    ) {

        if (collection.equals("products")) {
            return true;
        }

        if (!isLoggedIn()) {
            return false;
        }

        if (isAdmin()) {
            return true;
        }

        if (collection.equals("users")) {
            return documentId.equals(auth.getCurrentUser().getUid());
        }

        return collection.equals("reviews");
    }

    private boolean canWriteDocument(
        String collection,
        String documentId,
        Map<String, Object> data
    ) {

        if (!isLoggedIn()) {
            return false;
        }

        if (isAdmin()) {
            return true;
        }

        if (collection.equals("products")) {
            return false;
        }

        if (collection.equals("users")) {
            return documentId.equals(auth.getCurrentUser().getUid());
        }

        if (collection.equals("orders")) {

            Object uid = data.get("uid");

            return uid != null &&
                uid.toString().equals(auth.getCurrentUser().getUid());
        }

        return collection.equals("reviews");
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

        @JavascriptInterface
        public void signIn(
            String email,
            String password,
            String callbackId
        ) {

            auth.signInWithEmailAndPassword(email, password)

            .addOnSuccessListener(result -> {

                try {

                    FirebaseUser user = result.getUser();

                    JSONObject data =
                        makeResult(true, "Login successful");

                    if (user != null) {

                        data.put("uid", user.getUid());
                        data.put("email", user.getEmail());
                        data.put(
                            "displayName",
                            user.getDisplayName() == null
                                ? ""
                                : user.getDisplayName()
                        );
                    }

                    sendResult(callbackId, data);

                } catch (Exception e) {
                    sendError(callbackId, e.getMessage());
                }

            })

            .addOnFailureListener(error ->
                sendError(callbackId, error.getMessage())
            );
        }

        // Registration: email, password, name, mobile, callbackId

        @JavascriptInterface
        public void createAccount(
            String email,
            String password,
            String name,
            String mobile,
            String callbackId
        ) {

            auth.createUserWithEmailAndPassword(email, password)

            .addOnSuccessListener(result -> {

                FirebaseUser user = result.getUser();

                if (user == null) {
                    sendError(callbackId, "Account creation failed");
                    return;
                }

                UserProfileChangeRequest profile =
                    new UserProfileChangeRequest.Builder()
                        .setDisplayName(name)
                        .build();

                user.updateProfile(profile)

                .addOnCompleteListener(task -> {

                    try {

                        JSONObject data =
                            makeResult(true, "Account created");

                        data.put("uid", user.getUid());
                        data.put("email", user.getEmail());
                        data.put("name", name);
                        data.put("mobile", mobile);
                        data.put("displayName", name);

                        sendResult(callbackId, data);

                    } catch (Exception e) {
                        sendError(callbackId, e.getMessage());
                    }
                });

            })

            .addOnFailureListener(error ->
                sendError(callbackId, error.getMessage())
            );
        }

        @JavascriptInterface
        public void resetPassword(
            String email,
            String callbackId
        ) {

            auth.sendPasswordResetEmail(email)

            .addOnSuccessListener(unused ->
                sendSuccess(callbackId, "Password reset email sent")
            )

            .addOnFailureListener(error ->
                sendError(callbackId, error.getMessage())
            );
        }

        @JavascriptInterface
        public String currentUser() {

            FirebaseUser user = auth.getCurrentUser();

            if (user == null) {
                return "";
            }

            try {

                JSONObject data = new JSONObject();

                data.put("uid", user.getUid());
                data.put("email", user.getEmail());
                data.put(
                    "displayName",
                    user.getDisplayName() == null
                        ? ""
                        : user.getDisplayName()
                );

                data.put(
                    "phoneNumber",
                    user.getPhoneNumber() == null
                        ? ""
                        : user.getPhoneNumber()
                );

                return data.toString();

            } catch (Exception e) {
                return "";
            }
        }

        @JavascriptInterface
        public void signOut() {
            auth.signOut();
        }

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

            try {

                JSONObject object = new JSONObject(jsonData);
                Map<String, Object> data = jsonToMap(object);

                if (!canWriteDocument(collection, documentId, data)) {
                    sendError(callbackId, "Permission denied");
                    return;
                }

                db.collection(collection)
                    .document(documentId)
                    .set(data)

                    .addOnSuccessListener(unused ->
                        sendSuccess(callbackId, "Data saved successfully")
                    )

                    .addOnFailureListener(error ->
                        sendError(callbackId, error.getMessage())
                    );

            } catch (Exception e) {
                sendError(callbackId, e.getMessage());
            }
        }

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

            if (!canReadDocument(collection, documentId)) {
                sendError(callbackId, "Please login first");
                return;
            }

            db.collection(collection)
                .document(documentId)
                .get()

                .addOnSuccessListener(document -> {

                    try {

                        if (collection.equals("orders") &&
                            !isAdmin() &&
                            document.exists()) {

                            String uid = document.getString("uid");

                            if (uid == null ||
                                !uid.equals(auth.getCurrentUser().getUid())) {

                                sendError(callbackId, "Permission denied");
                                return;
                            }
                        }

                        JSONObject result =
                            makeResult(true, "Data loaded");

                        if (document.exists()) {

                            result.put(
                                "data",
                                new JSONObject(document.getData())
                            );

                        } else {

                            result.put("data", JSONObject.NULL);
                        }

                        sendResult(callbackId, result);

                    } catch (Exception e) {
                        sendError(callbackId, e.getMessage());
                    }

                })

                .addOnFailureListener(error ->
                    sendError(callbackId, error.getMessage())
                );
        }

        @JavascriptInterface
        public void getCollection(
            String collection,
            String callbackId
        ) {

            if (!allowedCollection(collection)) {
                sendError(callbackId, "Invalid collection");
                return;
            }

            // Products can be read without login.
            // Other collections require authentication.

            if (!collection.equals("products") && !isLoggedIn()) {
                sendError(callbackId, "Please login first");
                return;
            }

            // Customer accounts cannot read the complete users collection.

            if (collection.equals("users") && !isAdmin()) {
                sendError(callbackId, "Permission denied");
                return;
            }

            Query query = db.collection(collection);

            if (
                collection.equals("orders") &&
                !isAdmin()
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

                        for (DocumentSnapshot document : snapshot.getDocuments()) {

                            JSONObject item =
                                new JSONObject(document.getData());

                            item.put("id", document.getId());

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
                    sendError(callbackId, error.getMessage())
                );
        }

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

            if (!isLoggedIn()) {
                sendError(callbackId, "Please login first");
                return;
            }

            if (collection.equals("products") && !isAdmin()) {
                sendError(callbackId, "Only admin can delete products");
                return;
            }

            if (collection.equals("users") &&
                !isAdmin() &&
                !documentId.equals(auth.getCurrentUser().getUid())) {

                sendError(callbackId, "Permission denied");
                return;
            }

            if (collection.equals("orders") && !isAdmin()) {

                db.collection(collection)
                    .document(documentId)
                    .get()

                    .addOnSuccessListener(document -> {

                        String uid = document.getString("uid");

                        if (uid == null ||
                            !uid.equals(auth.getCurrentUser().getUid())) {

                            sendError(callbackId, "Permission denied");
                            return;
                        }

                        deleteDocumentNow(collection, documentId, callbackId);
                    })

                    .addOnFailureListener(error ->
                        sendError(callbackId, error.getMessage())
                    );

                return;
            }

            if (collection.equals("reviews") && !isAdmin()) {
                // Reviews can be deleted by authenticated users here.
                // Firestore Rules should enforce the actual ownership policy.
            }

            deleteDocumentNow(collection, documentId, callbackId);
        }

        private void deleteDocumentNow(
            String collection,
            String documentId,
            String callbackId
        ) {

            db.collection(collection)
                .document(documentId)
                .delete()

                .addOnSuccessListener(unused ->
                    sendSuccess(callbackId, "Deleted successfully")
                )

                .addOnFailureListener(error ->
                    sendError(callbackId, error.getMessage())
                );
        }
    }

    // HARDWARE BACK BUTTON

    @Override
    public void onBackPressed() {

        if (webView == null) {
            super.onBackPressed();
            return;
        }

        webView.evaluateJavascript(
            "(function() {" +
            "if (typeof handleAppBack === 'function' " +
            "&& handleAppBack()) {" +
            "return 'handled';" +
            "}" +
            "return 'home';" +
            "})()",

            value -> {

                if ("\"home\"".equals(value)) {

                    if (
                        backPressedTime + 2000 >
                        System.currentTimeMillis()
                    ) {

                        backPressedTime = 0;
                        MainActivity.super.onBackPressed();

                    } else {

                        backPressedTime = System.currentTimeMillis();

                        Toast.makeText(
                            MainActivity.this,
                            "Dobara Back dabao app band ho jayegi",
                            Toast.LENGTH_SHORT
                        ).show();
                    }

                } else {
                    backPressedTime = 0;
                }
            }
        );
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
