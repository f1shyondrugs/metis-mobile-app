     1	package com.metisai.mobile;
     2	
     3	import android.app.Activity;
     4	import android.graphics.Color;
     5	import android.graphics.Typeface;
     6	import android.graphics.drawable.GradientDrawable;
     7	import android.net.Uri;
     8	import android.os.Build;
     9	import android.os.Bundle;
    10	import android.view.Gravity;
    11	import android.view.View;
    12	import android.view.WindowInsets;
    13	import android.view.WindowManager;
    14	import android.widget.Button;
    15	import android.widget.EditText;
    16	import android.widget.FrameLayout;
    17	import android.widget.LinearLayout;
    18	import android.widget.PopupMenu;
    19	import android.app.AlertDialog;
    20	import android.widget.ScrollView;
    21	import android.widget.TextView;
    22	
    23	import org.json.JSONArray;
    24	import org.json.JSONObject;
    25	
    26	import java.io.BufferedReader;
    27	import java.io.InputStream;
    28	import java.io.InputStreamReader;
    29	import java.io.OutputStream;
    30	import java.net.HttpURLConnection;
    31	import java.net.URL;
    32	import java.net.URLEncoder;
    33	import java.nio.charset.StandardCharsets;
    34	import java.util.UUID;
    35	import java.util.concurrent.ExecutorService;
    36	import java.util.concurrent.Executors;
    37	
    38	public final class MainActivity extends Activity {
    39	    private static final String SERVER = "server_url";
    40	    private static final String SESSION = "session_cookie";
    41	    // Match the Metis web client's neutral dark theme.
    42	    private static final int BG = Color.rgb(12, 12, 12);
    43	    private static final int FG = Color.rgb(237, 237, 237);
    44	    private static final int MUTED = Color.rgb(163, 163, 163);
    45	    private static final int SURFACE = Color.rgb(24, 24, 24);
    46	    private static final int SECONDARY = Color.rgb(38, 38, 38);
    47	    private static final int BORDER = Color.rgb(41, 41, 41);
    48	
    49	    private final ExecutorService network = Executors.newSingleThreadExecutor();
    50	    private String serverUrl = "";
    51	    private String sessionCookie = "";
    52	    private String activeChatId = "";
    53	    private boolean showArchivedChats = false;
    54	    private TextView liveAssistantText;
    55	    private TextView liveStatus;
    56	    private Button sendButton;
    57	    private EditText composer;
    58	
    59	    private int dp(float value) {
    60	        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    61	    }
    62	
    63	    @Override public void onCreate(Bundle state) {
    64	        super.onCreate(state);
    65	        getWindow().setStatusBarColor(BG);
    66	        getWindow().setNavigationBarColor(BG);
    67	        if (Build.VERSION.SDK_INT >= 29) {
    68	            getWindow().setStatusBarContrastEnforced(false);
    69	            getWindow().setNavigationBarContrastEnforced(false);
    70	        }
    71	        if (Build.VERSION.SDK_INT >= 30) {
    72	            getWindow().setDecorFitsSystemWindows(false);
    73	        } else {
    74	            getWindow().getDecorView().setSystemUiVisibility(
    75	                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
    76	                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
    77	                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
    78	            );
    79	        }
    80	        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
    81	
    82	        serverUrl = getPreferences(MODE_PRIVATE).getString(SERVER, "");
    83	        sessionCookie = getPreferences(MODE_PRIVATE).getString(SESSION, "");
    84	        if (serverUrl.isEmpty()) {
    85	            showServerSetup("");
    86	        } else if (sessionCookie.isEmpty()) {
    87	            showLogin("");
    88	        } else {
    89	            loadChatList();
    90	        }
    91	    }
    92	
    93	    private void applySystemInsets(View root) {
    94	        final int left = root.getPaddingLeft();
    95	        final int top = root.getPaddingTop();
    96	        final int right = root.getPaddingRight();
    97	        final int bottom = root.getPaddingBottom();
    98	        root.setOnApplyWindowInsetsListener((view, insets) -> {
    99	            int insetLeft;
   100	            int insetTop;
   101	            int insetRight;
   102	            int insetBottom;
   103	            if (Build.VERSION.SDK_INT >= 30) {
   104	                android.graphics.Insets bars = insets.getInsets(
   105	                    WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout()
   106	                );
   107	                android.graphics.Insets ime = insets.getInsets(WindowInsets.Type.ime());
   108	                insetLeft = bars.left;
   109	                insetTop = bars.top;
   110	                insetRight = bars.right;
   111	                insetBottom = Math.max(bars.bottom, ime.bottom);
   112	            } else {
   113	                insetLeft = insets.getSystemWindowInsetLeft();
   114	                insetTop = insets.getSystemWindowInsetTop();
   115	                insetRight = insets.getSystemWindowInsetRight();
   116	                insetBottom = insets.getSystemWindowInsetBottom();
   117	            }
   118	            view.setPadding(left + insetLeft, top + insetTop, right + insetRight, bottom + insetBottom);
   119	            return insets;
   120	        });
   121	        root.post(root::requestApplyInsets);
   122	    }
   123	
   124	    private LinearLayout page() {
   125	        LinearLayout root = new LinearLayout(this);
   126	        root.setOrientation(LinearLayout.VERTICAL);
   127	        root.setBackgroundColor(BG);
   128	        applySystemInsets(root);
   129	        return root;
   130	    }
   131	
   132	    private TextView label(String value, float size, int color) {
   133	        TextView view = new TextView(this);
   134	        view.setText(value);
   135	        view.setTextSize(size);
   136	        view.setTextColor(color);
   137	        view.setIncludeFontPadding(false);
   138	        return view;
   139	    }
   140	
   141	    private Button button(String title) {
   142	        Button result = new Button(this);
   143	        result.setText(title);
   144	        result.setTextColor(Color.rgb(28, 28, 28));
   145	        result.setTextSize(14);
   146	        result.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
   147	        result.setAllCaps(false);
   148	        result.setMinHeight(dp(42));
   149	        result.setMinWidth(0);
   150	        result.setPadding(dp(16), 0, dp(16), 0);
   151	        result.setBackground(background(FG, dp(9)));
   152	        result.setElevation(0);
   153	        result.setStateListAnimator(null);
   154	        return result;
   155	    }
   156	
   157	    private EditText input(String hint, boolean secret) {
   158	        EditText edit = new EditText(this);
   159	        edit.setSingleLine(!secret);
   160	        edit.setHint(hint);
   161	        edit.setTextColor(FG);
   162	        edit.setHintTextColor(Color.rgb(125, 125, 125));
   163	        edit.setTextSize(15);
   164	        edit.setPadding(dp(14), dp(12), dp(14), dp(12));
   165	        edit.setBackground(outlinedSurface(SURFACE, dp(9)));
   166	        if (secret) edit.setInputType(129);
   167	        return edit;
   168	    }
   169	
   170	    private void header(LinearLayout root, String title, String action, View.OnClickListener listener) {
   171	        LinearLayout bar = new LinearLayout(this);
   172	        bar.setGravity(Gravity.CENTER_VERTICAL);
   173	        bar.setPadding(dp(18), 0, dp(10), 0);
   174	        TextView heading = label("Metis".equals(title) ? "Μῆτις" : title, 19, FG);
   175	        if ("Metis".equals(title)) heading.setTypeface(Typeface.create("serif", Typeface.ITALIC));
   176	        else heading.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
   177	        heading.setLetterSpacing(-0.025f);
   178	        bar.addView(heading, new LinearLayout.LayoutParams(0, dp(52), 1));
   179	        if (action != null) {
   180	            TextView button = label(action, 13, MUTED);
   181	            button.setGravity(Gravity.CENTER);
   182	            button.setPadding(dp(12), 0, dp(12), 0);
   183	            button.setOnClickListener(listener);
   184	            bar.addView(button, new LinearLayout.LayoutParams(-2, dp(48)));
   185	        }
   186	        root.addView(bar, new LinearLayout.LayoutParams(-1, dp(52)));
   187	        View divider = new View(this);
   188	        divider.setBackgroundColor(BORDER);
   189	        root.addView(divider, new LinearLayout.LayoutParams(-1, dp(1)));
   190	    }
   191	
   192	    private LinearLayout centeredForm() {
   193	        LinearLayout form = new LinearLayout(this);
   194	        form.setOrientation(LinearLayout.VERTICAL);
   195	        form.setGravity(Gravity.CENTER_VERTICAL);
   196	        form.setPadding(dp(22), dp(24), dp(22), dp(24));
   197	        form.setBackground(outlinedSurface(SURFACE, dp(14)));
   198	        return form;
   199	    }
   200	
   201	    private TextView wordmark() {
   202	        TextView brand = label("Μῆτις", 36, FG);
   203	        brand.setTypeface(Typeface.create("serif", Typeface.ITALIC));
   204	        brand.setLetterSpacing(-0.035f);
   205	        return brand;
   206	    }
   207	
   208	    private void showServerSetup(String previous) {
   209	        LinearLayout root = page();
   210	        LinearLayout form = centeredForm();
   211	        form.addView(wordmark());
   212	        TextView title = label("Mit deinem Server verbinden", 22, FG);
   213	        title.setPadding(0, dp(26), 0, dp(8));
   214	        form.addView(title);
   215	        TextView hint = label("Gib die Adresse deiner Metis-Instanz ein. Danach meldest du dich mit deinem Konto an.", 15, MUTED);
   216	        form.addView(hint);
   217	        EditText address = input("https://metis.example.com", false);
   218	        address.setSingleLine(true);
   219	        address.setInputType(17);
   220	        address.setText(previous);
   221	        LinearLayout.LayoutParams addressParams = new LinearLayout.LayoutParams(-1, -2);
   222	        addressParams.topMargin = dp(22);
   223	        form.addView(address, addressParams);
   224	        Button connect = button("Weiter");
   225	        LinearLayout.LayoutParams connectParams = new LinearLayout.LayoutParams(-1, -2);
   226	        connectParams.topMargin = dp(14);
   227	        form.addView(connect, connectParams);
   228	        TextView foot = label("Die Serveradresse bleibt auf diesem Gerät gespeichert.", 13, MUTED);
   229	        foot.setPadding(0, dp(16), 0, 0);
   230	        form.addView(foot);
   231	        connect.setOnClickListener(v -> {
   232	            String value = address.getText().toString().trim();
   233	            if (value.isEmpty()) {
   234	                address.setError("Serveradresse erforderlich");
   235	                return;
   236	            }
   237	            if (!value.matches("(?i)^https?://.*")) value = "https://" + value;
   238	            Uri uri = Uri.parse(value);
   239	            if (uri.getHost() == null || uri.getUserInfo() != null) {
   240	                address.setError("Bitte eine gültige Serveradresse eingeben");
   241	                return;
   242	            }
   243	            value = uri.buildUpon().fragment(null).build().toString();
   244	            while (value.endsWith("/")) value = value.substring(0, value.length() - 1);
   245	            serverUrl = value;
   246	            sessionCookie = "";
   247	            getPreferences(MODE_PRIVATE).edit().putString(SERVER, serverUrl).remove(SESSION).apply();
   248	            showLogin("");
   249	        });
   250	        LinearLayout.LayoutParams formParams = new LinearLayout.LayoutParams(-1, 0, 1);
   251	        formParams.setMargins(dp(18), dp(18), dp(18), dp(18));
   252	        root.addView(form, formParams);
   253	        setContentView(root);
   254	    }
   255	
   256	    private void showLogin(String error) {
   257	        LinearLayout root = page();
   258	        LinearLayout form = centeredForm();
   259	        form.addView(wordmark());
   260	        TextView title = label("Anmelden", 22, FG);
   261	        title.setPadding(0, dp(26), 0, dp(6));
   262	        form.addView(title);
   263	        TextView server = label(serverUrl, 13, MUTED);
   264	        form.addView(server);
   265	        EditText username = input("Benutzername", false);
   266	        EditText password = input("Passwort", true);
   267	        LinearLayout.LayoutParams field = new LinearLayout.LayoutParams(-1, -2);
   268	        field.topMargin = dp(20);
   269	        form.addView(username, field);
   270	        LinearLayout.LayoutParams second = new LinearLayout.LayoutParams(-1, -2);
   271	        second.topMargin = dp(10);
   272	        form.addView(password, second);
   273	        TextView message = label(error, 14, Color.rgb(255, 120, 120));
   274	        message.setPadding(0, dp(12), 0, 0);
   275	        form.addView(message);
   276	        Button login = button("Anmelden");
   277	        LinearLayout.LayoutParams loginParams = new LinearLayout.LayoutParams(-1, -2);
   278	        loginParams.topMargin = dp(14);
   279	        form.addView(login, loginParams);
   280	        TextView change = label("Server ändern", 14, MUTED);
   281	        change.setGravity(Gravity.CENTER);
   282	        change.setPadding(0, dp(18), 0, dp(6));
   283	        change.setOnClickListener(v -> showServerSetup(serverUrl));
   284	        form.addView(change);
   285	        login.setOnClickListener(v -> {
   286	            String user = username.getText().toString().trim();
   287	            String pass = password.getText().toString();
   288	            if (user.isEmpty() || pass.isEmpty()) {
   289	                message.setText("Benutzername und Passwort eingeben.");
   290	                return;
   291	            }
   292	            login.setEnabled(false);
   293	            message.setText("Verbindung wird hergestellt …");
   294	            network.execute(() -> {
   295	                try {
   296	                    HttpURLConnection connection = openConnection("/api/auth", "POST", null);
   297	                    connection.setDoOutput(true);
   298	                    connection.setRequestProperty("Content-Type", "application/json");
   299	                    JSONObject body = new JSONObject();
   300	                    body.put("username", user);
   301	                    body.put("password", pass);
   302	                    writeBody(connection, body.toString());
   303	                    int code = connection.getResponseCode();
   304	                    String response = readResponse(connection, code);
   305	                    String setCookie = connection.getHeaderField("Set-Cookie");
   306	                    connection.disconnect();
   307	                    if (code < 200 || code >= 300) throw apiError(response, code);
   308	                    if (setCookie == null || !setCookie.contains("=")) {
   309	                        throw new Exception("Der Server hat keine Sitzung zurückgegeben.");
   310	                    }
   311	                    sessionCookie = setCookie.split(";", 2)[0].trim();
   312	                    getPreferences(MODE_PRIVATE).edit().putString(SESSION, sessionCookie).apply();
   313	                    JSONObject chatsResponse = requestJson("/api/chats", "GET", null);
   314	                    runOnUiThread(this::showChatList);
   315	                } catch (Exception ex) {
   316	                    runOnUiThread(() -> {
   317	                        login.setEnabled(true);
   318	                        message.setText(messageFor(ex));
   319	                    });
   320	                }
   321	            });
   322	        });
   323	        LinearLayout.LayoutParams formParams = new LinearLayout.LayoutParams(-1, 0, 1);
   324	        formParams.setMargins(dp(18), dp(18), dp(18), dp(18));
   325	        root.addView(form, formParams);
   326	        setContentView(root);
   327	    }
   328	
   329	    private void loadChatList() {
   330	        network.execute(() -> {
   331	            try {
   332	                JSONObject response = requestJson("/api/chats" + (showArchivedChats ? "?includeArchived=true" : ""), "GET", null);
   333	                JSONArray chats = response.optJSONArray("chats");
   334	                runOnUiThread(() -> showChatList(chats == null ? new JSONArray() : chats));
   335	            } catch (Exception ex) {
   336	                runOnUiThread(() -> {
   337	                    if (isUnauthorized(ex)) showLogin("Bitte melde dich erneut an.");
   338	                    else showLogin(messageFor(ex));
   339	                });
   340	            }
   341	        });
   342	    }
   343	
   344	    private void showChatList() {
   345	        loadChatList();
   346	    }
   347	
   348	    private void showChatList(JSONArray chats) {
   349	        activeChatId = "";
   350	        LinearLayout root = page();
   351	        header(root, "Metis", "Abmelden", v -> logout());
   352	        ScrollView scroll = new ScrollView(this);
   353	        LinearLayout list = new LinearLayout(this);
   354	        list.setOrientation(LinearLayout.VERTICAL);
   355	        list.setPadding(dp(16), dp(14), dp(16), dp(24));
   356	
   357	        Button newChat = button("＋  Neuer Chat");
   358	        newChat.setTextColor(FG);
   359	        newChat.setGravity(Gravity.CENTER_VERTICAL | Gravity.LEFT);
   360	        newChat.setBackground(outlinedSurface(SURFACE, dp(9)));
   361	        list.addView(newChat, new LinearLayout.LayoutParams(-1, dp(46)));
   362	        newChat.setOnClickListener(v -> createChat(newChat));
   363	
   364	        LinearLayout sectionBar = new LinearLayout(this);
   365	        sectionBar.setGravity(Gravity.CENTER_VERTICAL);
   366	        sectionBar.setPadding(dp(4), dp(20), dp(4), dp(8));
   367	        TextView section = label(showArchivedChats ? "Archiv" : "Chats", 13, MUTED);
   368	        sectionBar.addView(section, new LinearLayout.LayoutParams(0, -2, 1));
   369	        TextView archiveToggle = label(showArchivedChats ? "Aktive Chats" : "Archivierte Chats", 13, MUTED);
   370	        archiveToggle.setGravity(Gravity.CENTER_VERTICAL);
   371	        archiveToggle.setPadding(dp(8), dp(8), dp(4), dp(8));
   372	        archiveToggle.setOnClickListener(v -> {
   373	            showArchivedChats = !showArchivedChats;
   374	            loadChatList();
   375	        });
   376	        sectionBar.addView(archiveToggle);
   377	        list.addView(sectionBar);
   378	        if (chats.length() == 0) {
   379	            TextView empty = label("Noch keine Chats. Starte mit „Neuer Chat“.", 15, MUTED);
   380	            empty.setPadding(dp(4), dp(8), dp(4), dp(8));
   381	            list.addView(empty);
   382	        }
   383	        for (int i = 0; i < chats.length(); i++) {
   384	            JSONObject chat = chats.optJSONObject(i);
   385	            if (chat == null) continue;
   386	            String id = chat.optString("id");
   387	            String title = chat.optString("title", "Neuer Chat");
   388	            LinearLayout row = new LinearLayout(this);
   389	            row.setGravity(Gravity.CENTER_VERTICAL);
   390	            TextView chatTitle = label(title, 15, FG);
   391	            chatTitle.setMaxLines(2);
   392	            chatTitle.setPadding(dp(12), dp(13), dp(8), dp(13));
   393	            chatTitle.setBackground(new android.graphics.drawable.RippleDrawable(
   394	                android.content.res.ColorStateList.valueOf(Color.rgb(54, 54, 54)), null, null));
   395	            row.addView(chatTitle, new LinearLayout.LayoutParams(0, -2, 1));
   396	            TextView more = label("···", 20, MUTED);
   397	            more.setGravity(Gravity.CENTER);
   398	            more.setContentDescription("Chat-Aktionen");
   399	            more.setPadding(dp(12), 0, dp(12), 0);
   400	            row.addView(more, new LinearLayout.LayoutParams(-2, dp(48)));
   401	            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
   402	            params.bottomMargin = dp(2);
   403	            list.addView(row, params);
   404	            chatTitle.setOnClickListener(v -> openChat(id, title));
   405	            more.setOnClickListener(v -> showChatActions(more, id, title, chat.optBoolean("archived", false)));
   406	        }
   407	        scroll.addView(list);
   408	        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
   409	        setContentView(root);
   410	    }
   411	
   412	    private void showChatActions(View anchor, String chatId, String title, boolean archived) {
   413	        PopupMenu menu = new PopupMenu(this, anchor);
   414	        menu.getMenu().add("Umbenennen").setOnMenuItemClickListener(item -> {
   415	            EditText name = input("Chatname", false);
   416	            name.setText(title);
   417	            new AlertDialog.Builder(this).setTitle("Chat umbenennen").setView(name)
   418	                .setNegativeButton("Abbrechen", null)
   419	                .setPositiveButton("Speichern", (dialog, which) -> updateChat(chatId, "title", name.getText().toString().trim()))
   420	                .show();
   421	            return true;
   422	        });
   423	        menu.getMenu().add(archived ? "Wiederherstellen" : "Archivieren")
   424	            .setOnMenuItemClickListener(item -> {
   425	                updateChat(chatId, "archived", archived ? "false" : "true");
   426	                return true;
   427	            });
   428	        menu.getMenu().add("Löschen").setOnMenuItemClickListener(item -> {
   429	            new AlertDialog.Builder(this).setTitle("Chat löschen?")
   430	                .setMessage("Der Chat und sein Verlauf werden dauerhaft gelöscht.")
   431	                .setNegativeButton("Abbrechen", null)
   432	                .setPositiveButton("Löschen", (dialog, which) -> deleteChat(chatId))
   433	                .show();
   434	            return true;
   435	        });
   436	        menu.show();
   437	    }
   438	
   439	    private void updateChat(String chatId, String field, String value) {
   440	        network.execute(() -> {
   441	            try {
   442	                JSONObject body = new JSONObject();
   443	                if ("archived".equals(field)) body.put(field, Boolean.parseBoolean(value));
   444	                else body.put(field, value);
   445	                requestJson("/api/chats/" + encodePath(chatId), "PATCH", body);
   446	                runOnUiThread(this::loadChatList);
   447	            } catch (Exception ex) {
   448	                runOnUiThread(() -> toastMessage(messageFor(ex)));
   449	            }
   450	        });
   451	    }
   452	
   453	    private void deleteChat(String chatId) {
   454	        network.execute(() -> {
   455	            try {
   456	                requestJson("/api/chats/" + encodePath(chatId), "DELETE", null);
   457	                runOnUiThread(this::loadChatList);
   458	            } catch (Exception ex) {
   459	                runOnUiThread(() -> toastMessage(messageFor(ex)));
   460	            }
   461	        });
   462	    }
   463	
   464	    private void createChat(Button source) {
   465	        source.setEnabled(false);
   466	        network.execute(() -> {
   467	            try {
   468	                JSONObject body = new JSONObject();
   469	                JSONObject result = requestJson("/api/chats", "POST", body);
   470	                JSONObject chat = result.optJSONObject("chat");
   471	                if (chat == null) throw new Exception("Der Server hat keinen Chat zurückgegeben.");
   472	                String id = chat.optString("id");
   473	                String title = chat.optString("title", "Neuer Chat");
   474	                runOnUiThread(() -> openChat(id, title));
   475	            } catch (Exception ex) {
   476	                runOnUiThread(() -> {
   477	                    source.setEnabled(true);
   478	                    toastMessage(messageFor(ex));
   479	                });
   480	            }
   481	        });
   482	    }
   483	
   484	    private void openChat(String id, String title) {
   485	        activeChatId = id;
   486	        showConversation(title, new JSONArray(), false);
   487	        network.execute(() -> {
   488	            try {
   489	                JSONObject result = requestJson(
   490	                    "/api/chats/" + encodePath(id) + "?messageLimit=100",
   491	                    "GET", null
   492	                );
   493	                JSONObject chat = result.optJSONObject("chat");
   494	                JSONArray messages = chat == null ? new JSONArray() : chat.optJSONArray("messages");
   495	                if (messages == null) messages = new JSONArray();
   496	                JSONArray finalMessages = messages;
   497	                runOnUiThread(() -> {
   498	                    if (id.equals(activeChatId)) showConversation(title, finalMessages, false);
   499	                });
   500	            } catch (Exception ex) {
   501	                runOnUiThread(() -> {
   502	                    if (id.equals(activeChatId)) toastMessage(messageFor(ex));
   503	                });
   504	            }
   505	        });
   506	    }
   507	
   508	    private void showConversation(String title, JSONArray messages, boolean busy) {
   509	        LinearLayout root = page();
   510	        header(root, title, "Chats", v -> loadChatList());
   511	        ScrollView scroll = new ScrollView(this);
   512	        LinearLayout column = new LinearLayout(this);
   513	        column.setOrientation(LinearLayout.VERTICAL);
   514	        column.setPadding(dp(20), dp(18), dp(20), dp(14));
   515	        for (int i = 0; i < messages.length(); i++) {
   516	            JSONObject message = messages.optJSONObject(i);
   517	            if (message == null) continue;
   518	            String role = message.optString("role");
   519	            String content = message.optString("content");
   520	            addMessageBubble(column, role, content);
   521	        }
   522	        liveStatus = label(busy ? "Metis antwortet …" : "", 13, MUTED);
   523	        liveStatus.setPadding(dp(12), dp(6), dp(12), dp(10));
   524	        column.addView(liveStatus);
   525	        scroll.addView(column);
   526	        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
   527	
   528	        LinearLayout composeRow = new LinearLayout(this);
   529	        composeRow.setGravity(Gravity.CENTER_VERTICAL);
   530	        composeRow.setPadding(dp(7), dp(5), dp(7), dp(5));
   531	        composeRow.setBackground(outlinedSurface(SURFACE, dp(14)));
   532	        composer = input("Nachricht an Metis …", false);
   533	        composer.setSingleLine(false);
   534	        composer.setMinLines(1);
   535	        composer.setMaxLines(5);
   536	        composer.setInputType(147457);
   537	        composer.setBackgroundColor(Color.TRANSPARENT);
   538	        composer.setPadding(dp(10), dp(10), dp(8), dp(10));
   539	        composeRow.addView(composer, new LinearLayout.LayoutParams(0, -2, 1));
   540	        sendButton = button("↑");
   541	        sendButton.setContentDescription("Senden");
   542	        sendButton.setPadding(0, 0, 0, 0);
   543	        LinearLayout.LayoutParams sendParams = new LinearLayout.LayoutParams(dp(40), dp(40));
   544	        sendParams.leftMargin = dp(6);
   545	        composeRow.addView(sendButton, sendParams);
   546	        LinearLayout.LayoutParams composeParams = new LinearLayout.LayoutParams(-1, -2);
   547	        composeParams.setMargins(dp(12), dp(8), dp(12), dp(8));
   548	        root.addView(composeRow, composeParams);
   549	
   550	        sendButton.setEnabled(!busy);
   551	        sendButton.setOnClickListener(v -> {
   552	            String text = composer.getText().toString().trim();
   553	            if (!text.isEmpty()) sendMessage(text, title, column, scroll);
   554	        });
   555	        setContentView(root);
   556	        scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
   557	    }
   558	
   559	    private void addMessageBubble(LinearLayout column, String role, String content) {
   560	        boolean user = "user".equals(role);
   561	        TextView bubble = label(content, 15, FG);
   562	        bubble.setTextIsSelectable(true);
   563	        bubble.setLineSpacing(dp(2), 1.08f);
   564	        bubble.setMaxWidth((int) (getResources().getDisplayMetrics().widthPixels * 0.86f));
   565	        if (user) {
   566	            bubble.setPadding(dp(14), dp(11), dp(14), dp(11));
   567	            bubble.setBackground(background(SECONDARY, dp(10)));
   568	        } else {
   569	            bubble.setPadding(0, 0, 0, 0);
   570	            bubble.setBackgroundColor(Color.TRANSPARENT);
   571	        }
   572	        LinearLayout line = new LinearLayout(this);
   573	        line.setGravity(user ? Gravity.RIGHT : Gravity.LEFT);
   574	        line.addView(bubble, new LinearLayout.LayoutParams(user ? -2 : -1, -2));
   575	        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
   576	        params.bottomMargin = dp(user ? 16 : 18);
   577	        column.addView(line, params);
   578	    }
   579	
   580	    private void sendMessage(String text, String title, LinearLayout column, ScrollView scroll) {
   581	        composer.setText("");
   582	        composer.setEnabled(false);
   583	        sendButton.setEnabled(false);
   584	        addMessageBubble(column, "user", text);
   585	        liveAssistantText = label("", 15, FG);
   586	        liveAssistantText.setLineSpacing(dp(2), 1.08f);
   587	        liveAssistantText.setPadding(0, 0, 0, 0);
   588	        LinearLayout assistantLine = new LinearLayout(this);
   589	        assistantLine.setGravity(Gravity.LEFT);
   590	        assistantLine.addView(liveAssistantText, new LinearLayout.LayoutParams(-1, -2));
   591	        LinearLayout.LayoutParams assistantParams = new LinearLayout.LayoutParams(-1, -2);
   592	        assistantParams.bottomMargin = dp(12);
   593	        column.addView(assistantLine, column.indexOfChild(liveStatus), assistantParams);
   594	        liveStatus.setText("Metis antwortet …");
   595	        scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
   596	        String chatId = activeChatId;
   597	
   598	        network.execute(() -> {
   599	            try {
   600	                JSONObject body = new JSONObject();
   601	                body.put("chatId", chatId);
   602	                body.put("message", text);
   603	                body.put("messageId", UUID.randomUUID().toString());
   604	                body.put("streamDeviceId", "android-" + UUID.randomUUID());
   605	                HttpURLConnection connection = openConnection("/api/chat", "POST", null);
   606	                connection.setDoOutput(true);
   607	                connection.setRequestProperty("Content-Type", "application/json");
   608	                writeBody(connection, body.toString());
   609	                int code = connection.getResponseCode();
   610	                String response = readResponse(connection, code);
   611	                connection.disconnect();
   612	                if (code < 200 || code >= 300) throw apiError(response, code);
   613	                JSONObject accepted = new JSONObject(response);
   614	                String jobId = accepted.optString("jobId");
   615	                if (jobId.isEmpty()) throw new Exception("Der Server hat keine Run-ID zurückgegeben.");
   616	
   617	                long after = 0;
   618	                boolean finished = false;
   619	                StringBuilder answer = new StringBuilder();
   620	                while (!finished) {
   621	                    String path = "/api/runs?chatId=" + encodePath(chatId)
   622	                        + "&jobId=" + encodePath(jobId) + "&events=1&after=" + after;
   623	                    JSONObject result = requestJson(path, "GET", null);
   624	                    JSONArray events = result.optJSONArray("events");
   625	                    if (events != null) {
   626	                        for (int i = 0; i < events.length(); i++) {
   627	                            JSONObject event = events.optJSONObject(i);
   628	                            if (event == null) continue;
   629	                            after = Math.max(after, event.optLong("id", 0));
   630	                            JSONObject data = event.optJSONObject("data");
   631	                            if (data == null) continue;
   632	                            String kind = event.optString("event");
   633	                            if ("text".equals(kind)) {
   634	                                answer.append(data.optString("text", ""));
   635	                                String partial = answer.toString();
   636	                                runOnUiThread(() -> {
   637	                                    if (chatId.equals(activeChatId) && liveAssistantText != null) {
   638	                                        liveAssistantText.setText(partial);
   639	                                        scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
   640	                                    }
   641	                                });
   642	                            } else if ("status".equals(kind)) {
   643	                                String status = data.optString("message", data.optString("status", "Metis arbeitet …"));
   644	                                runOnUiThread(() -> {
   645	                                    if (chatId.equals(activeChatId) && liveStatus != null) liveStatus.setText(status);
   646	                                });
   647	                            } else if ("done".equals(kind)) {
   648	                                finished = true;
   649	                            } else if ("error".equals(kind)) {
   650	                                throw new Exception(data.optString("message", "Agent-Run fehlgeschlagen."));
   651	                            }
   652	                        }
   653	                    }
   654	                    if (!finished) Thread.sleep(600);
   655	                }
   656	                runOnUiThread(() -> {
   657	                    if (!chatId.equals(activeChatId)) return;
   658	                    liveStatus.setText("");
   659	                    composer.setEnabled(true);
   660	                    sendButton.setEnabled(true);
   661	                    composer.requestFocus();
   662	                });
   663	            } catch (Exception ex) {
   664	                runOnUiThread(() -> {
   665	                    if (!chatId.equals(activeChatId)) return;
   666	                    if (liveStatus != null) liveStatus.setText(messageFor(ex));
   667	                    if (composer != null) composer.setEnabled(true);
   668	                    if (sendButton != null) sendButton.setEnabled(true);
   669	                });
   670	            }
   671	        });
   672	    }
   673	
   674	    private void logout() {
   675	        network.execute(() -> {
   676	            try { requestJson("/api/auth", "DELETE", null); } catch (Exception ignored) {}
   677	            sessionCookie = "";
   678	            getPreferences(MODE_PRIVATE).edit().remove(SESSION).apply();
   679	            runOnUiThread(() -> showLogin(""));
   680	        });
   681	    }
   682	
   683	    private HttpURLConnection openConnection(String path, String method, String ignored) throws Exception {
   684	        URL url = new URL(serverUrl + path);
   685	        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
   686	        connection.setRequestMethod(method);
   687	        connection.setConnectTimeout(15000);
   688	        connection.setReadTimeout(20000);
   689	        connection.setRequestProperty("Accept", "application/json");
   690	        connection.setRequestProperty("X-Metis-Device-Id", "metis-android");
   691	        if (!sessionCookie.isEmpty()) connection.setRequestProperty("Cookie", sessionCookie);
   692	        return connection;
   693	    }
   694	
   695	    private void writeBody(HttpURLConnection connection, String body) throws Exception {
   696	        byte[] data = body.getBytes(StandardCharsets.UTF_8);
   697	        try (OutputStream output = connection.getOutputStream()) {
   698	            output.write(data);
   699	        }
   700	    }
   701	
   702	    private String readResponse(HttpURLConnection connection, int code) throws Exception {
   703	        InputStream stream = code >= 400 ? connection.getErrorStream() : connection.getInputStream();
   704	        if (stream == null) return "";
   705	        StringBuilder result = new StringBuilder();
   706	        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
   707	            String line;
   708	            while ((line = reader.readLine()) != null) result.append(line);
   709	        }
   710	        return result.toString();
   711	    }
   712	
   713	    private JSONObject requestJson(String path, String method, JSONObject body) throws Exception {
   714	        HttpURLConnection connection = openConnection(path, method, null);
   715	        if (body != null) {
   716	            connection.setDoOutput(true);
   717	            connection.setRequestProperty("Content-Type", "application/json");
   718	            writeBody(connection, body.toString());
   719	        }
   720	        int code = connection.getResponseCode();
   721	        String response = readResponse(connection, code);
   722	        connection.disconnect();
   723	        if (code < 200 || code >= 300) throw apiError(response, code);
   724	        return response.isEmpty() ? new JSONObject() : new JSONObject(response);
   725	    }
   726	
   727	    private Exception apiError(String response, int code) {
   728	        try {
   729	            String message = new JSONObject(response).optString("error");
   730	            if (!message.isEmpty()) return new Exception(message);
   731	        } catch (Exception ignored) {}
   732	        return new Exception("Serverfehler (HTTP " + code + ")");
   733	    }
   734	
   735	    private boolean isUnauthorized(Exception ex) {
   736	        return ex.getMessage() != null && ex.getMessage().contains("Unauthorized");
   737	    }
   738	
   739	    private String messageFor(Exception ex) {
   740	        if (ex instanceof java.net.UnknownHostException) return "Server nicht gefunden. Prüfe die Serveradresse.";
   741	        if (ex instanceof java.net.ConnectException) return "Verbindung zum Metis-Server nicht möglich.";
   742	        return ex.getMessage() == null ? "Verbindung fehlgeschlagen." : ex.getMessage();
   743	    }
   744	
   745	    private String encodePath(String value) throws Exception {
   746	        return URLEncoder.encode(value, "UTF-8");
   747	    }
   748	
   749	    private GradientDrawable background(int color, int radius) {
   750	        GradientDrawable shape = new GradientDrawable();
   751	        shape.setColor(color);
   752	        shape.setCornerRadius(radius);
   753	        return shape;
   754	    }
   755	
   756	    private GradientDrawable outlinedSurface(int color, int radius) {
   757	        GradientDrawable shape = background(color, radius);
   758	        shape.setStroke(dp(1), BORDER);
   759	        return shape;
   760	    }
   761	
   762	    private void toastMessage(String text) {
   763	        android.widget.Toast.makeText(this, text, android.widget.Toast.LENGTH_LONG).show();
   764	    }
   765	
   766	    @Override public void onBackPressed() {
   767	        if (!activeChatId.isEmpty()) {
   768	            activeChatId = "";
   769	            loadChatList();
   770	        } else {
   771	            super.onBackPressed();
   772	        }
   773	    }
   774	
   775	    @Override protected void onDestroy() {
   776	        network.shutdownNow();
   777	        super.onDestroy();
   778	    }
   779	}
