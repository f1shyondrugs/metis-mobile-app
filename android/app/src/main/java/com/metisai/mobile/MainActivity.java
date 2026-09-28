package com.metisai.mobile;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private static final String SERVER = "server_url";
    private static final String SESSION = "session_cookie";
    private static final int BG = Color.rgb(16, 16, 16);
    private static final int FG = Color.rgb(242, 242, 242);
    private static final int MUTED = Color.rgb(157, 157, 157);
    private static final int SURFACE = Color.rgb(32, 32, 32);

    private final ExecutorService network = Executors.newSingleThreadExecutor();
    private String serverUrl = "";
    private String sessionCookie = "";
    private String activeChatId = "";
    private TextView liveAssistantText;
    private TextView liveStatus;
    private Button sendButton;
    private EditText composer;

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        if (Build.VERSION.SDK_INT >= 29) {
            getWindow().setStatusBarContrastEnforced(false);
            getWindow().setNavigationBarContrastEnforced(false);
        }
        if (Build.VERSION.SDK_INT >= 30) {
            getWindow().setDecorFitsSystemWindows(false);
        } else {
            getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            );
        }
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);

        serverUrl = getPreferences(MODE_PRIVATE).getString(SERVER, "");
        sessionCookie = getPreferences(MODE_PRIVATE).getString(SESSION, "");
        if (serverUrl.isEmpty()) {
            showServerSetup("");
        } else if (sessionCookie.isEmpty()) {
            showLogin("");
        } else {
            loadChatList();
        }
    }

    private void applySystemInsets(View root) {
        final int left = root.getPaddingLeft();
        final int top = root.getPaddingTop();
        final int right = root.getPaddingRight();
        final int bottom = root.getPaddingBottom();
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            int insetLeft;
            int insetTop;
            int insetRight;
            int insetBottom;
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = insets.getInsets(
                    WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout()
                );
                android.graphics.Insets ime = insets.getInsets(WindowInsets.Type.ime());
                insetLeft = bars.left;
                insetTop = bars.top;
                insetRight = bars.right;
                insetBottom = Math.max(bars.bottom, ime.bottom);
            } else {
                insetLeft = insets.getSystemWindowInsetLeft();
                insetTop = insets.getSystemWindowInsetTop();
                insetRight = insets.getSystemWindowInsetRight();
                insetBottom = insets.getSystemWindowInsetBottom();
            }
            view.setPadding(left + insetLeft, top + insetTop, right + insetRight, bottom + insetBottom);
            return insets;
        });
        root.post(root::requestApplyInsets);
    }

    private LinearLayout page() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        applySystemInsets(root);
        return root;
    }

    private TextView label(String value, float size, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        return view;
    }

    private Button button(String title) {
        Button result = new Button(this);
        result.setText(title);
        result.setTextColor(Color.BLACK);
        result.setAllCaps(false);
        result.setBackgroundTintList(android.content.res.ColorStateList.valueOf(FG));
        return result;
    }

    private EditText input(String hint, boolean secret) {
        EditText edit = new EditText(this);
        edit.setSingleLine(!secret);
        edit.setHint(hint);
        edit.setTextColor(FG);
        edit.setHintTextColor(MUTED);
        edit.setTextSize(16);
        edit.setPadding(dp(14), dp(12), dp(14), dp(12));
        edit.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(95, 95, 95)));
        if (secret) edit.setInputType(129);
        return edit;
    }

    private void header(LinearLayout root, String title, String action, View.OnClickListener listener) {
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(16), 0, dp(8), 0);
        TextView heading = label(title, 19, FG);
        heading.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        bar.addView(heading, new LinearLayout.LayoutParams(0, dp(54), 1));
        if (action != null) {
            TextView button = label(action, 14, FG);
            button.setGravity(Gravity.CENTER);
            button.setPadding(dp(12), 0, dp(12), 0);
            button.setOnClickListener(listener);
            bar.addView(button, new LinearLayout.LayoutParams(-2, dp(48)));
        }
        root.addView(bar, new LinearLayout.LayoutParams(-1, dp(54)));
        View divider = new View(this);
        divider.setBackgroundColor(Color.rgb(48, 48, 48));
        root.addView(divider, new LinearLayout.LayoutParams(-1, dp(1)));
    }

    private LinearLayout centeredForm() {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setGravity(Gravity.CENTER_VERTICAL);
        form.setPadding(dp(26), dp(24), dp(26), dp(28));
        return form;
    }

    private void showServerSetup(String previous) {
        LinearLayout root = page();
        LinearLayout form = centeredForm();
        TextView brand = label("Metis", 42, FG);
        brand.setTypeface(Typeface.create("serif", Typeface.ITALIC));
        form.addView(brand);
        TextView title = label("Mit deinem Server verbinden", 22, FG);
        title.setPadding(0, dp(26), 0, dp(8));
        form.addView(title);
        TextView hint = label("Gib die Adresse deiner Metis-Instanz ein. Danach meldest du dich mit deinem Konto an.", 15, MUTED);
        form.addView(hint);
        EditText address = input("https://metis.example.com", false);
        address.setSingleLine(true);
        address.setInputType(17);
        address.setText(previous);
        LinearLayout.LayoutParams addressParams = new LinearLayout.LayoutParams(-1, -2);
        addressParams.topMargin = dp(22);
        form.addView(address, addressParams);
        Button connect = button("Weiter");
        LinearLayout.LayoutParams connectParams = new LinearLayout.LayoutParams(-1, -2);
        connectParams.topMargin = dp(14);
        form.addView(connect, connectParams);
        TextView foot = label("Die Serveradresse bleibt auf diesem Gerät gespeichert.", 13, MUTED);
        foot.setPadding(0, dp(16), 0, 0);
        form.addView(foot);
        connect.setOnClickListener(v -> {
            String value = address.getText().toString().trim();
            if (value.isEmpty()) {
                address.setError("Serveradresse erforderlich");
                return;
            }
            if (!value.matches("(?i)^https?://.*")) value = "https://" + value;
            Uri uri = Uri.parse(value);
            if (uri.getHost() == null || uri.getUserInfo() != null) {
                address.setError("Bitte eine gültige Serveradresse eingeben");
                return;
            }
            value = uri.buildUpon().fragment(null).build().toString();
            while (value.endsWith("/")) value = value.substring(0, value.length() - 1);
            serverUrl = value;
            sessionCookie = "";
            getPreferences(MODE_PRIVATE).edit().putString(SERVER, serverUrl).remove(SESSION).apply();
            showLogin("");
        });
        root.addView(form, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
    }

    private void showLogin(String error) {
        LinearLayout root = page();
        LinearLayout form = centeredForm();
        TextView brand = label("Metis", 42, FG);
        brand.setTypeface(Typeface.create("serif", Typeface.ITALIC));
        form.addView(brand);
        TextView title = label("Anmelden", 22, FG);
        title.setPadding(0, dp(26), 0, dp(6));
        form.addView(title);
        TextView server = label(serverUrl, 13, MUTED);
        form.addView(server);
        EditText username = input("Benutzername", false);
        EditText password = input("Passwort", true);
        LinearLayout.LayoutParams field = new LinearLayout.LayoutParams(-1, -2);
        field.topMargin = dp(20);
        form.addView(username, field);
        LinearLayout.LayoutParams second = new LinearLayout.LayoutParams(-1, -2);
        second.topMargin = dp(10);
        form.addView(password, second);
        TextView message = label(error, 14, Color.rgb(255, 120, 120));
        message.setPadding(0, dp(12), 0, 0);
        form.addView(message);
        Button login = button("Anmelden");
        LinearLayout.LayoutParams loginParams = new LinearLayout.LayoutParams(-1, -2);
        loginParams.topMargin = dp(14);
        form.addView(login, loginParams);
        TextView change = label("Server ändern", 14, MUTED);
        change.setGravity(Gravity.CENTER);
        change.setPadding(0, dp(18), 0, dp(6));
        change.setOnClickListener(v -> showServerSetup(serverUrl));
        form.addView(change);
        login.setOnClickListener(v -> {
            String user = username.getText().toString().trim();
            String pass = password.getText().toString();
            if (user.isEmpty() || pass.isEmpty()) {
                message.setText("Benutzername und Passwort eingeben.");
                return;
            }
            login.setEnabled(false);
            message.setText("Verbindung wird hergestellt …");
            network.execute(() -> {
                try {
                    HttpURLConnection connection = openConnection("/api/auth", "POST", null);
                    connection.setDoOutput(true);
                    connection.setRequestProperty("Content-Type", "application/json");
                    JSONObject body = new JSONObject();
                    body.put("username", user);
                    body.put("password", pass);
                    writeBody(connection, body.toString());
                    int code = connection.getResponseCode();
                    String response = readResponse(connection, code);
                    String setCookie = connection.getHeaderField("Set-Cookie");
                    connection.disconnect();
                    if (code < 200 || code >= 300) throw apiError(response, code);
                    if (setCookie == null || !setCookie.contains("=")) {
                        throw new Exception("Der Server hat keine Sitzung zurückgegeben.");
                    }
                    sessionCookie = setCookie.split(";", 2)[0].trim();
                    getPreferences(MODE_PRIVATE).edit().putString(SESSION, sessionCookie).apply();
                    JSONObject chatsResponse = requestJson("/api/chats", "GET", null);
                    runOnUiThread(this::showChatList);
                } catch (Exception ex) {
                    runOnUiThread(() -> {
                        login.setEnabled(true);
                        message.setText(messageFor(ex));
                    });
                }
            });
        });
        root.addView(form, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
    }

    private void loadChatList() {
        network.execute(() -> {
            try {
                JSONObject response = requestJson("/api/chats", "GET", null);
                JSONArray chats = response.optJSONArray("chats");
                runOnUiThread(() -> showChatList(chats == null ? new JSONArray() : chats));
            } catch (Exception ex) {
                runOnUiThread(() -> {
                    if (isUnauthorized(ex)) showLogin("Bitte melde dich erneut an.");
                    else showLogin(messageFor(ex));
                });
            }
        });
    }

    private void showChatList() {
        loadChatList();
    }

    private void showChatList(JSONArray chats) {
        activeChatId = "";
        LinearLayout root = page();
        header(root, "Metis", "Abmelden", v -> logout());
        ScrollView scroll = new ScrollView(this);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(16), dp(14), dp(16), dp(24));

        Button newChat = button("Neuer Chat");
        list.addView(newChat, new LinearLayout.LayoutParams(-1, -2));
        newChat.setOnClickListener(v -> createChat(newChat));

        TextView section = label("Chats", 13, MUTED);
        section.setPadding(dp(4), dp(22), dp(4), dp(8));
        list.addView(section);
        if (chats.length() == 0) {
            TextView empty = label("Noch keine Chats. Starte mit „Neuer Chat“.", 15, MUTED);
            empty.setPadding(dp(4), dp(8), dp(4), dp(8));
            list.addView(empty);
        }
        for (int i = 0; i < chats.length(); i++) {
            JSONObject chat = chats.optJSONObject(i);
            if (chat == null) continue;
            String id = chat.optString("id");
            String title = chat.optString("title", "Neuer Chat");
            TextView row = label(title, 16, FG);
            row.setMaxLines(2);
            row.setPadding(dp(14), dp(15), dp(14), dp(15));
            row.setBackground(background(SURFACE, dp(10)));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
            params.bottomMargin = dp(8);
            list.addView(row, params);
            row.setOnClickListener(v -> openChat(id, title));
        }
        scroll.addView(list);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
    }

    private void createChat(Button source) {
        source.setEnabled(false);
        network.execute(() -> {
            try {
                JSONObject body = new JSONObject();
                JSONObject result = requestJson("/api/chats", "POST", body);
                JSONObject chat = result.optJSONObject("chat");
                if (chat == null) throw new Exception("Der Server hat keinen Chat zurückgegeben.");
                String id = chat.optString("id");
                String title = chat.optString("title", "Neuer Chat");
                runOnUiThread(() -> openChat(id, title));
            } catch (Exception ex) {
                runOnUiThread(() -> {
                    source.setEnabled(true);
                    toastMessage(messageFor(ex));
                });
            }
        });
    }

    private void openChat(String id, String title) {
        activeChatId = id;
        showConversation(title, new JSONArray(), false);
        network.execute(() -> {
            try {
                JSONObject result = requestJson(
                    "/api/chats/" + encodePath(id) + "?messageLimit=100",
                    "GET", null
                );
                JSONObject chat = result.optJSONObject("chat");
                JSONArray messages = chat == null ? new JSONArray() : chat.optJSONArray("messages");
                if (messages == null) messages = new JSONArray();
                JSONArray finalMessages = messages;
                runOnUiThread(() -> {
                    if (id.equals(activeChatId)) showConversation(title, finalMessages, false);
                });
            } catch (Exception ex) {
                runOnUiThread(() -> {
                    if (id.equals(activeChatId)) toastMessage(messageFor(ex));
                });
            }
        });
    }

    private void showConversation(String title, JSONArray messages, boolean busy) {
        LinearLayout root = page();
        header(root, title, "Chats", v -> loadChatList());
        ScrollView scroll = new ScrollView(this);
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(dp(14), dp(16), dp(14), dp(14));
        for (int i = 0; i < messages.length(); i++) {
            JSONObject message = messages.optJSONObject(i);
            if (message == null) continue;
            String role = message.optString("role");
            String content = message.optString("content");
            addMessageBubble(column, role, content);
        }
        liveStatus = label(busy ? "Metis antwortet …" : "", 13, MUTED);
        liveStatus.setPadding(dp(12), dp(6), dp(12), dp(10));
        column.addView(liveStatus);
        scroll.addView(column);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout composeRow = new LinearLayout(this);
        composeRow.setGravity(Gravity.BOTTOM);
        composeRow.setPadding(dp(10), dp(8), dp(10), dp(8));
        composeRow.setBackgroundColor(Color.rgb(22, 22, 22));
        composer = input("Nachricht an Metis …", false);
        composer.setSingleLine(false);
        composer.setMinLines(1);
        composer.setMaxLines(5);
        composer.setInputType(147457);
        composeRow.addView(composer, new LinearLayout.LayoutParams(0, -2, 1));
        sendButton = button("Senden");
        LinearLayout.LayoutParams sendParams = new LinearLayout.LayoutParams(-2, dp(48));
        sendParams.leftMargin = dp(8);
        composeRow.addView(sendButton, sendParams);
        root.addView(composeRow, new LinearLayout.LayoutParams(-1, -2));

        sendButton.setEnabled(!busy);
        sendButton.setOnClickListener(v -> {
            String text = composer.getText().toString().trim();
            if (!text.isEmpty()) sendMessage(text, title, column, scroll);
        });
        setContentView(root);
        scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
    }

    private void addMessageBubble(LinearLayout column, String role, String content) {
        boolean user = "user".equals(role);
        TextView bubble = label(content, 15, FG);
        bubble.setTextIsSelectable(true);
        bubble.setPadding(dp(14), dp(11), dp(14), dp(11));
        bubble.setBackground(background(user ? Color.rgb(54, 54, 54) : Color.rgb(25, 25, 25), dp(13)));
        LinearLayout line = new LinearLayout(this);
        line.setGravity(user ? Gravity.RIGHT : Gravity.LEFT);
        line.addView(bubble, new LinearLayout.LayoutParams(-2, -2));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(12);
        column.addView(line, params);
    }

    private void sendMessage(String text, String title, LinearLayout column, ScrollView scroll) {
        composer.setText("");
        composer.setEnabled(false);
        sendButton.setEnabled(false);
        addMessageBubble(column, "user", text);
        liveAssistantText = label("", 15, FG);
        liveAssistantText.setPadding(dp(14), dp(11), dp(14), dp(11));
        LinearLayout assistantLine = new LinearLayout(this);
        assistantLine.setGravity(Gravity.LEFT);
        assistantLine.addView(liveAssistantText, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout.LayoutParams assistantParams = new LinearLayout.LayoutParams(-1, -2);
        assistantParams.bottomMargin = dp(12);
        column.addView(assistantLine, column.indexOfChild(liveStatus), assistantParams);
        liveStatus.setText("Metis antwortet …");
        scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
        String chatId = activeChatId;

        network.execute(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("chatId", chatId);
                body.put("message", text);
                body.put("messageId", UUID.randomUUID().toString());
                body.put("streamDeviceId", "android-" + UUID.randomUUID());
                HttpURLConnection connection = openConnection("/api/chat", "POST", null);
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json");
                writeBody(connection, body.toString());
                int code = connection.getResponseCode();
                String response = readResponse(connection, code);
                connection.disconnect();
                if (code < 200 || code >= 300) throw apiError(response, code);
                JSONObject accepted = new JSONObject(response);
                String jobId = accepted.optString("jobId");
                if (jobId.isEmpty()) throw new Exception("Der Server hat keine Run-ID zurückgegeben.");

                long after = 0;
                boolean finished = false;
                StringBuilder answer = new StringBuilder();
                while (!finished) {
                    String path = "/api/runs?chatId=" + encodePath(chatId)
                        + "&jobId=" + encodePath(jobId) + "&events=1&after=" + after;
                    JSONObject result = requestJson(path, "GET", null);
                    JSONArray events = result.optJSONArray("events");
                    if (events != null) {
                        for (int i = 0; i < events.length(); i++) {
                            JSONObject event = events.optJSONObject(i);
                            if (event == null) continue;
                            after = Math.max(after, event.optLong("id", 0));
                            JSONObject data = event.optJSONObject("data");
                            if (data == null) continue;
                            String kind = event.optString("event");
                            if ("text".equals(kind)) {
                                answer.append(data.optString("text", ""));
                                String partial = answer.toString();
                                runOnUiThread(() -> {
                                    if (chatId.equals(activeChatId) && liveAssistantText != null) {
                                        liveAssistantText.setText(partial);
                                        scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
                                    }
                                });
                            } else if ("status".equals(kind)) {
                                String status = data.optString("message", data.optString("status", "Metis arbeitet …"));
                                runOnUiThread(() -> {
                                    if (chatId.equals(activeChatId) && liveStatus != null) liveStatus.setText(status);
                                });
                            } else if ("done".equals(kind)) {
                                finished = true;
                            } else if ("error".equals(kind)) {
                                throw new Exception(data.optString("message", "Agent-Run fehlgeschlagen."));
                            }
                        }
                    }
                    if (!finished) Thread.sleep(600);
                }
                runOnUiThread(() -> {
                    if (!chatId.equals(activeChatId)) return;
                    liveStatus.setText("");
                    composer.setEnabled(true);
                    sendButton.setEnabled(true);
                    composer.requestFocus();
                });
            } catch (Exception ex) {
                runOnUiThread(() -> {
                    if (!chatId.equals(activeChatId)) return;
                    if (liveStatus != null) liveStatus.setText(messageFor(ex));
                    if (composer != null) composer.setEnabled(true);
                    if (sendButton != null) sendButton.setEnabled(true);
                });
            }
        });
    }

    private void logout() {
        network.execute(() -> {
            try { requestJson("/api/auth", "DELETE", null); } catch (Exception ignored) {}
            sessionCookie = "";
            getPreferences(MODE_PRIVATE).edit().remove(SESSION).apply();
            runOnUiThread(() -> showLogin(""));
        });
    }

    private HttpURLConnection openConnection(String path, String method, String ignored) throws Exception {
        URL url = new URL(serverUrl + path);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod(method);
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(20000);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("X-Metis-Device-Id", "metis-android");
        if (!sessionCookie.isEmpty()) connection.setRequestProperty("Cookie", sessionCookie);
        return connection;
    }

    private void writeBody(HttpURLConnection connection, String body) throws Exception {
        byte[] data = body.getBytes(StandardCharsets.UTF_8);
        try (OutputStream output = connection.getOutputStream()) {
            output.write(data);
        }
    }

    private String readResponse(HttpURLConnection connection, int code) throws Exception {
        InputStream stream = code >= 400 ? connection.getErrorStream() : connection.getInputStream();
        if (stream == null) return "";
        StringBuilder result = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) result.append(line);
        }
        return result.toString();
    }

    private JSONObject requestJson(String path, String method, JSONObject body) throws Exception {
        HttpURLConnection connection = openConnection(path, method, null);
        if (body != null) {
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json");
            writeBody(connection, body.toString());
        }
        int code = connection.getResponseCode();
        String response = readResponse(connection, code);
        connection.disconnect();
        if (code < 200 || code >= 300) throw apiError(response, code);
        return response.isEmpty() ? new JSONObject() : new JSONObject(response);
    }

    private Exception apiError(String response, int code) {
        try {
            String message = new JSONObject(response).optString("error");
            if (!message.isEmpty()) return new Exception(message);
        } catch (Exception ignored) {}
        return new Exception("Serverfehler (HTTP " + code + ")");
    }

    private boolean isUnauthorized(Exception ex) {
        return ex.getMessage() != null && ex.getMessage().contains("Unauthorized");
    }

    private String messageFor(Exception ex) {
        if (ex instanceof java.net.UnknownHostException) return "Server nicht gefunden. Prüfe die Serveradresse.";
        if (ex instanceof java.net.ConnectException) return "Verbindung zum Metis-Server nicht möglich.";
        return ex.getMessage() == null ? "Verbindung fehlgeschlagen." : ex.getMessage();
    }

    private String encodePath(String value) throws Exception {
        return URLEncoder.encode(value, "UTF-8");
    }

    private GradientDrawable background(int color, int radius) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(radius);
        return shape;
    }

    private void toastMessage(String text) {
        android.widget.Toast.makeText(this, text, android.widget.Toast.LENGTH_LONG).show();
    }

    @Override public void onBackPressed() {
        if (!activeChatId.isEmpty()) {
            activeChatId = "";
            loadChatList();
        } else {
            super.onBackPressed();
        }
    }

    @Override protected void onDestroy() {
        network.shutdownNow();
        super.onDestroy();
    }
}
