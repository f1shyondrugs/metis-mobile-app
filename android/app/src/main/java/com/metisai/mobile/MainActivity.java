     1	package com.metisai.mobile;
     2	
     3	import android.app.Activity;
     4	import android.content.Intent;
     5	import android.database.Cursor;
     6	import android.net.Uri;
     7	import android.provider.OpenableColumns;
     8	import android.util.Base64;
     9	import android.graphics.Color;
    10	import android.graphics.Typeface;
    11	import android.graphics.drawable.GradientDrawable;
    12	import android.os.Build;
    13	import android.os.Bundle;
    14	import android.view.Gravity;
    15	import android.view.View;
    16	import android.view.WindowInsets;
    17	import android.view.WindowManager;
    18	import android.widget.Button;
    19	import android.widget.EditText;
    20	import android.widget.FrameLayout;
    21	import android.widget.LinearLayout;
    22	import android.widget.PopupMenu;
    23	import android.app.AlertDialog;
    24	import android.text.TextUtils;
    25	import android.widget.ScrollView;
    26	import android.widget.TextView;
    27	
    28	import org.json.JSONArray;
    29	import org.json.JSONObject;
    30	
    31	import io.noties.markwon.Markwon;
    32	import io.noties.markwon.ext.tables.TablePlugin;
    33	import io.noties.markwon.ext.strikethrough.StrikethroughPlugin;
    34	
    35	import java.io.BufferedReader;
    36	import java.io.ByteArrayOutputStream;
    37	import java.io.InputStream;
    38	import java.io.InputStreamReader;
    39	import java.io.OutputStream;
    40	import java.net.HttpURLConnection;
    41	import java.net.URL;
    42	import java.net.URLEncoder;
    43	import java.nio.charset.StandardCharsets;
    44	import java.util.ArrayList;
    45	import java.util.UUID;
    46	import java.util.concurrent.ExecutorService;
    47	import java.util.concurrent.Executors;
    48	
    49	public final class MainActivity extends Activity {
    50	    private static final String SERVER = "server_url";
    51	    private static final String SESSION = "session_cookie";
    52	    // Match the Metis web client's neutral dark theme.
    53	    private static final int BG = Color.rgb(12, 12, 12);
    54	    private static final int FG = Color.rgb(237, 237, 237);
    55	    private static final int MUTED = Color.rgb(163, 163, 163);
    56	    private static final int SURFACE = Color.rgb(24, 24, 24);
    57	    private static final int SECONDARY = Color.rgb(38, 38, 38);
    58	    private static final int BORDER = Color.rgb(41, 41, 41);
    59	
    60	    private final ExecutorService network = Executors.newFixedThreadPool(3);
    61	    private String serverUrl = "";
    62	    private String sessionCookie = "";
    63	    private String activeChatId = "";
    64	    private boolean showArchivedChats = false;
    65	    private TextView liveAssistantText;
    66	    private TextView liveStatus;
    67	    private Button sendButton;
    68	    private EditText composer;
    69	    private TextView modelButton;
    70	    private Markwon markdown;
    71	    private JSONArray availableModels = new JSONArray();
    72	    private String selectedModelId = "";
    73	    private String selectedModelName = "Standardmodell";
    74	    private boolean busyRun = false;
    75	    private final ArrayList<Uri> pendingAttachments = new ArrayList<>();
    76	    private static final int PICK_ATTACHMENTS = 4107;
    77	
    78	    private int dp(float value) {
    79	        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    80	    }
    81	
    82	    @Override public void onCreate(Bundle state) {
    83	        super.onCreate(state);
    84	        getWindow().setStatusBarColor(BG);
    85	        getWindow().setNavigationBarColor(BG);
    86	        if (Build.VERSION.SDK_INT >= 29) {
    87	            getWindow().setStatusBarContrastEnforced(false);
    88	            getWindow().setNavigationBarContrastEnforced(false);
    89	        }
    90	        if (Build.VERSION.SDK_INT >= 30) {
    91	            getWindow().setDecorFitsSystemWindows(false);
    92	        } else {
    93	            getWindow().getDecorView().setSystemUiVisibility(
    94	                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
    95	                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
    96	                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
    97	            );
    98	        }
    99	        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
   100	
   101	        markdown = Markwon.builder(this).usePlugin(TablePlugin.create(this)).usePlugin(StrikethroughPlugin.create()).build();
   102	        serverUrl = getPreferences(MODE_PRIVATE).getString(SERVER, "");
   103	        sessionCookie = getPreferences(MODE_PRIVATE).getString(SESSION, "");
   104	        if (serverUrl.isEmpty()) {
   105	            showServerSetup("");
   106	        } else if (sessionCookie.isEmpty()) {
   107	            showLogin("");
   108	        } else {
   109	            loadChatList();
   110	        }
   111	    }
   112	
   113	    private void applySystemInsets(View root) {
   114	        final int left = root.getPaddingLeft();
   115	        final int top = root.getPaddingTop();
   116	        final int right = root.getPaddingRight();
   117	        final int bottom = root.getPaddingBottom();
   118	        root.setOnApplyWindowInsetsListener((view, insets) -> {
   119	            int insetLeft;
   120	            int insetTop;
   121	            int insetRight;
   122	            int insetBottom;
   123	            if (Build.VERSION.SDK_INT >= 30) {
   124	                android.graphics.Insets bars = insets.getInsets(
   125	                    WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout()
   126	                );
   127	                android.graphics.Insets ime = insets.getInsets(WindowInsets.Type.ime());
   128	                insetLeft = bars.left;
   129	                insetTop = bars.top;
   130	                insetRight = bars.right;
   131	                insetBottom = Math.max(bars.bottom, ime.bottom);
   132	            } else {
   133	                insetLeft = insets.getSystemWindowInsetLeft();
   134	                insetTop = insets.getSystemWindowInsetTop();
   135	                insetRight = insets.getSystemWindowInsetRight();
   136	                insetBottom = insets.getSystemWindowInsetBottom();
   137	            }
   138	            view.setPadding(left + insetLeft, top + insetTop, right + insetRight, bottom + insetBottom);
   139	            return insets;
   140	        });
   141	        root.post(root::requestApplyInsets);
   142	    }
   143	
   144	    private LinearLayout page() {
   145	        LinearLayout root = new LinearLayout(this);
   146	        root.setOrientation(LinearLayout.VERTICAL);
   147	        root.setBackgroundColor(BG);
   148	        applySystemInsets(root);
   149	        return root;
   150	    }
   151	
   152	    private TextView label(String value, float size, int color) {
   153	        TextView view = new TextView(this);
   154	        view.setText(value);
   155	        view.setTextSize(size);
   156	        view.setTextColor(color);
   157	        view.setIncludeFontPadding(false);
   158	        return view;
   159	    }
   160	
   161	    private Button button(String title) {
   162	        Button result = new Button(this);
   163	        result.setText(title);
   164	        result.setTextColor(Color.rgb(28, 28, 28));
   165	        result.setTextSize(14);
   166	        result.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
   167	        result.setAllCaps(false);
   168	        result.setMinHeight(dp(42));
   169	        result.setMinWidth(0);
   170	        result.setPadding(dp(16), 0, dp(16), 0);
   171	        result.setBackground(background(FG, dp(9)));
   172	        result.setElevation(0);
   173	        result.setStateListAnimator(null);
   174	        return result;
   175	    }
   176	
   177	    private EditText input(String hint, boolean secret) {
   178	        EditText edit = new EditText(this);
   179	        edit.setSingleLine(!secret);
   180	        edit.setHint(hint);
   181	        edit.setTextColor(FG);
   182	        edit.setHintTextColor(Color.rgb(125, 125, 125));
   183	        edit.setTextSize(15);
   184	        edit.setPadding(dp(14), dp(12), dp(14), dp(12));
   185	        edit.setBackground(outlinedSurface(SURFACE, dp(9)));
   186	        if (secret) edit.setInputType(129);
   187	        return edit;
   188	    }
   189	
   190	    private void header(LinearLayout root, String title, String action, View.OnClickListener listener) {
   191	        LinearLayout bar = new LinearLayout(this);
   192	        bar.setGravity(Gravity.CENTER_VERTICAL);
   193	        bar.setPadding(dp(18), 0, dp(10), 0);
   194	        TextView heading = label("Metis".equals(title) ? "Μῆτις" : title, 19, FG);
   195	        if ("Metis".equals(title)) heading.setTypeface(Typeface.create("serif", Typeface.ITALIC));
   196	        else heading.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
   197	        heading.setLetterSpacing(-0.025f);
   198	        bar.addView(heading, new LinearLayout.LayoutParams(0, dp(52), 1));
   199	        if (action != null) {
   200	            TextView button = label(action, 13, MUTED);
   201	            button.setGravity(Gravity.CENTER);
   202	            button.setPadding(dp(12), 0, dp(12), 0);
   203	            button.setOnClickListener(listener);
   204	            bar.addView(button, new LinearLayout.LayoutParams(-2, dp(48)));
   205	        }
   206	        root.addView(bar, new LinearLayout.LayoutParams(-1, dp(52)));
   207	        View divider = new View(this);
   208	        divider.setBackgroundColor(BORDER);
   209	        root.addView(divider, new LinearLayout.LayoutParams(-1, dp(1)));
   210	    }
   211	
   212	    private LinearLayout centeredForm() {
   213	        LinearLayout form = new LinearLayout(this);
   214	        form.setOrientation(LinearLayout.VERTICAL);
   215	        form.setGravity(Gravity.CENTER_VERTICAL);
   216	        form.setPadding(dp(22), dp(24), dp(22), dp(24));
   217	        form.setBackground(outlinedSurface(SURFACE, dp(14)));
   218	        return form;
   219	    }
   220	
   221	    private TextView wordmark() {
   222	        TextView brand = label("Μῆτις", 36, FG);
   223	        brand.setTypeface(Typeface.create("serif", Typeface.ITALIC));
   224	        brand.setLetterSpacing(-0.035f);
   225	        return brand;
   226	    }
   227	
   228	    private void showServerSetup(String previous) {
   229	        LinearLayout root = page();
   230	        LinearLayout form = centeredForm();
   231	        form.addView(wordmark());
   232	        TextView title = label("Mit deinem Server verbinden", 22, FG);
   233	        title.setPadding(0, dp(26), 0, dp(8));
   234	        form.addView(title);
   235	        TextView hint = label("Gib die Adresse deiner Metis-Instanz ein. Danach meldest du dich mit deinem Konto an.", 15, MUTED);
   236	        form.addView(hint);
   237	        EditText address = input("https://metis.example.com", false);
   238	        address.setSingleLine(true);
   239	        address.setInputType(17);
   240	        address.setText(previous);
   241	        LinearLayout.LayoutParams addressParams = new LinearLayout.LayoutParams(-1, -2);
   242	        addressParams.topMargin = dp(22);
   243	        form.addView(address, addressParams);
   244	        Button connect = button("Weiter");
   245	        LinearLayout.LayoutParams connectParams = new LinearLayout.LayoutParams(-1, -2);
   246	        connectParams.topMargin = dp(14);
   247	        form.addView(connect, connectParams);
   248	        TextView foot = label("Die Serveradresse bleibt auf diesem Gerät gespeichert.", 13, MUTED);
   249	        foot.setPadding(0, dp(16), 0, 0);
   250	        form.addView(foot);
   251	        connect.setOnClickListener(v -> {
   252	            String value = address.getText().toString().trim();
   253	            if (value.isEmpty()) {
   254	                address.setError("Serveradresse erforderlich");
   255	                return;
   256	            }
   257	            if (!value.matches("(?i)^https?://.*")) value = "https://" + value;
   258	            Uri uri = Uri.parse(value);
   259	            if (uri.getHost() == null || uri.getUserInfo() != null) {
   260	                address.setError("Bitte eine gültige Serveradresse eingeben");
   261	                return;
   262	            }
   263	            value = uri.buildUpon().fragment(null).build().toString();
   264	            while (value.endsWith("/")) value = value.substring(0, value.length() - 1);
   265	            serverUrl = value;
   266	            sessionCookie = "";
   267	            getPreferences(MODE_PRIVATE).edit().putString(SERVER, serverUrl).remove(SESSION).apply();
   268	            showLogin("");
   269	        });
   270	        LinearLayout.LayoutParams formParams = new LinearLayout.LayoutParams(-1, 0, 1);
   271	        formParams.setMargins(dp(18), dp(18), dp(18), dp(18));
   272	        root.addView(form, formParams);
   273	        setContentView(root);
   274	    }
   275	
   276	    private void showLogin(String error) {
   277	        LinearLayout root = page();
   278	        LinearLayout form = centeredForm();
   279	        form.addView(wordmark());
   280	        TextView title = label("Anmelden", 22, FG);
   281	        title.setPadding(0, dp(26), 0, dp(6));
   282	        form.addView(title);
   283	        TextView server = label(serverUrl, 13, MUTED);
   284	        form.addView(server);
   285	        EditText username = input("Benutzername", false);
   286	        EditText password = input("Passwort", true);
   287	        LinearLayout.LayoutParams field = new LinearLayout.LayoutParams(-1, -2);
   288	        field.topMargin = dp(20);
   289	        form.addView(username, field);
   290	        LinearLayout.LayoutParams second = new LinearLayout.LayoutParams(-1, -2);
   291	        second.topMargin = dp(10);
   292	        form.addView(password, second);
   293	        TextView message = label(error, 14, Color.rgb(255, 120, 120));
   294	        message.setPadding(0, dp(12), 0, 0);
   295	        form.addView(message);
   296	        Button login = button("Anmelden");
   297	        LinearLayout.LayoutParams loginParams = new LinearLayout.LayoutParams(-1, -2);
   298	        loginParams.topMargin = dp(14);
   299	        form.addView(login, loginParams);
   300	        TextView change = label("Server ändern", 14, MUTED);
   301	        change.setGravity(Gravity.CENTER);
   302	        change.setPadding(0, dp(18), 0, dp(6));
   303	        change.setOnClickListener(v -> showServerSetup(serverUrl));
   304	        form.addView(change);
   305	        login.setOnClickListener(v -> {
   306	            String user = username.getText().toString().trim();
   307	            String pass = password.getText().toString();
   308	            if (user.isEmpty() || pass.isEmpty()) {
   309	                message.setText("Benutzername und Passwort eingeben.");
   310	                return;
   311	            }
   312	            login.setEnabled(false);
   313	            message.setText("Verbindung wird hergestellt …");
   314	            network.execute(() -> {
   315	                try {
   316	                    HttpURLConnection connection = openConnection("/api/auth", "POST", null);
   317	                    connection.setDoOutput(true);
   318	                    connection.setRequestProperty("Content-Type", "application/json");
   319	                    JSONObject body = new JSONObject();
   320	                    body.put("username", user);
   321	                    body.put("password", pass);
   322	                    writeBody(connection, body.toString());
   323	                    int code = connection.getResponseCode();
   324	                    String response = readResponse(connection, code);
   325	                    String setCookie = connection.getHeaderField("Set-Cookie");
   326	                    connection.disconnect();
   327	                    if (code < 200 || code >= 300) throw apiError(response, code);
   328	                    if (setCookie == null || !setCookie.contains("=")) {
   329	                        throw new Exception("Der Server hat keine Sitzung zurückgegeben.");
   330	                    }
   331	                    sessionCookie = setCookie.split(";", 2)[0].trim();
   332	                    getPreferences(MODE_PRIVATE).edit().putString(SESSION, sessionCookie).apply();
   333	                    JSONObject chatsResponse = requestJson("/api/chats", "GET", null);
   334	                    runOnUiThread(this::showChatList);
   335	                } catch (Exception ex) {
   336	                    runOnUiThread(() -> {
   337	                        login.setEnabled(true);
   338	                        message.setText(messageFor(ex));
   339	                    });
   340	                }
   341	            });
   342	        });
   343	        LinearLayout.LayoutParams formParams = new LinearLayout.LayoutParams(-1, 0, 1);
   344	        formParams.setMargins(dp(18), dp(18), dp(18), dp(18));
   345	        root.addView(form, formParams);
   346	        setContentView(root);
   347	    }
   348	
   349	    private void loadChatList() {
   350	        network.execute(() -> {
   351	            try {
   352	                JSONObject response = requestJson("/api/chats" + (showArchivedChats ? "?includeArchived=true" : ""), "GET", null);
   353	                loadModels();
   354	                JSONArray chats = response.optJSONArray("chats");
   355	                runOnUiThread(() -> showChatList(chats == null ? new JSONArray() : chats));
   356	            } catch (Exception ex) {
   357	                runOnUiThread(() -> {
   358	                    if (isUnauthorized(ex)) showLogin("Bitte melde dich erneut an.");
   359	                    else showLogin(messageFor(ex));
   360	                });
   361	            }
   362	        });
   363	    }
   364	
   365	    private void loadModels() {
   366	        try {
   367	            JSONObject response = requestJson("/api/models", "GET", null);
   368	            JSONArray models = response.optJSONArray("models");
   369	            if (models != null) availableModels = models;
   370	            String defaultId = response.optString("defaultModelId", "");
   371	            if (selectedModelId.isEmpty()) selectedModelId = defaultId;
   372	            for (int i = 0; i < availableModels.length(); i++) {
   373	                JSONObject model = availableModels.optJSONObject(i);
   374	                if (model != null && selectedModelId.equals(model.optString("id"))) {
   375	                    selectedModelName = model.optString("displayName", model.optString("id"));
   376	                    break;
   377	                }
   378	            }
   379	        } catch (Exception ignored) {
   380	            // Chat functions remain available using the server's default model.
   381	        }
   382	    }
   383	
   384	    private void chooseModel() {
   385	        if (availableModels.length() == 0) {
   386	            toastMessage("Für dieses Konto sind keine Modelle verfügbar.");
   387	            return;
   388	        }
   389	        String[] names = new String[availableModels.length()];
   390	        int checked = -1;
   391	        for (int i = 0; i < availableModels.length(); i++) {
   392	            JSONObject model = availableModels.optJSONObject(i);
   393	            names[i] = model == null ? "Unbekanntes Modell" : model.optString("displayName", model.optString("id"));
   394	            if (model != null && selectedModelId.equals(model.optString("id"))) checked = i;
   395	        }
   396	        new AlertDialog.Builder(this).setTitle("Modell auswählen").setSingleChoiceItems(names, checked, (dialog, which) -> {
   397	            JSONObject model = availableModels.optJSONObject(which);
   398	            if (model == null) return;
   399	            selectedModelId = model.optString("id");
   400	            selectedModelName = model.optString("displayName", selectedModelId);
   401	            if (modelButton != null) modelButton.setText(selectedModelName + "  ⌄");
   402	            String chatId = activeChatId;
   403	            if (!chatId.isEmpty()) updateChat(chatId, "modelId", selectedModelId);
   404	            dialog.dismiss();
   405	        }).setNegativeButton("Abbrechen", null).show();
   406	    }
   407	
   408	    private void showChatList() {
   409	        loadChatList();
   410	    }
   411	
   412	    private void showChatList(JSONArray chats) {
   413	        activeChatId = "";
   414	        LinearLayout root = page();
   415	        header(root, "Metis", "Abmelden", v -> logout());
   416	        ScrollView scroll = new ScrollView(this);
   417	        LinearLayout list = new LinearLayout(this);
   418	        list.setOrientation(LinearLayout.VERTICAL);
   419	        list.setPadding(dp(16), dp(14), dp(16), dp(24));
   420	
   421	        Button newChat = button("＋  Neuer Chat");
   422	        newChat.setTextColor(FG);
   423	        newChat.setGravity(Gravity.CENTER_VERTICAL | Gravity.LEFT);
   424	        newChat.setBackground(outlinedSurface(SURFACE, dp(9)));
   425	        list.addView(newChat, new LinearLayout.LayoutParams(-1, dp(46)));
   426	        newChat.setOnClickListener(v -> createChat(newChat));
   427	
   428	        LinearLayout sectionBar = new LinearLayout(this);
   429	        sectionBar.setGravity(Gravity.CENTER_VERTICAL);
   430	        sectionBar.setPadding(dp(4), dp(20), dp(4), dp(8));
   431	        TextView section = label(showArchivedChats ? "Archiv" : "Chats", 13, MUTED);
   432	        sectionBar.addView(section, new LinearLayout.LayoutParams(0, -2, 1));
   433	        TextView archiveToggle = label(showArchivedChats ? "Aktive Chats" : "Archivierte Chats", 13, MUTED);
   434	        archiveToggle.setGravity(Gravity.CENTER_VERTICAL);
   435	        archiveToggle.setPadding(dp(8), dp(8), dp(4), dp(8));
   436	        archiveToggle.setOnClickListener(v -> {
   437	            showArchivedChats = !showArchivedChats;
   438	            loadChatList();
   439	        });
   440	        sectionBar.addView(archiveToggle);
   441	        list.addView(sectionBar);
   442	        if (chats.length() == 0) {
   443	            TextView empty = label("Noch keine Chats. Starte mit „Neuer Chat“.", 15, MUTED);
   444	            empty.setPadding(dp(4), dp(8), dp(4), dp(8));
   445	            list.addView(empty);
   446	        }
   447	        for (int i = 0; i < chats.length(); i++) {
   448	            JSONObject chat = chats.optJSONObject(i);
   449	            if (chat == null) continue;
   450	            String id = chat.optString("id");
   451	            String title = chat.optString("title", "Neuer Chat");
   452	            LinearLayout row = new LinearLayout(this);
   453	            row.setGravity(Gravity.CENTER_VERTICAL);
   454	            TextView chatTitle = label(title, 15, FG);
   455	            chatTitle.setMaxLines(2);
   456	            chatTitle.setPadding(dp(12), dp(13), dp(8), dp(13));
   457	            chatTitle.setBackground(new android.graphics.drawable.RippleDrawable(
   458	                android.content.res.ColorStateList.valueOf(Color.rgb(54, 54, 54)), null, null));
   459	            row.addView(chatTitle, new LinearLayout.LayoutParams(0, -2, 1));
   460	            TextView more = label("···", 20, MUTED);
   461	            more.setGravity(Gravity.CENTER);
   462	            more.setContentDescription("Chat-Aktionen");
   463	            more.setPadding(dp(12), 0, dp(12), 0);
   464	            row.addView(more, new LinearLayout.LayoutParams(-2, dp(48)));
   465	            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
   466	            params.bottomMargin = dp(2);
   467	            list.addView(row, params);
   468	            chatTitle.setOnClickListener(v -> openChat(id, title));
   469	            more.setOnClickListener(v -> showChatActions(more, id, title, chat.optBoolean("archived", false)));
   470	        }
   471	        scroll.addView(list);
   472	        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
   473	        setContentView(root);
   474	    }
   475	
   476	    private void showChatActions(View anchor, String chatId, String title, boolean archived) {
   477	        PopupMenu menu = new PopupMenu(this, anchor);
   478	        menu.getMenu().add("Umbenennen").setOnMenuItemClickListener(item -> {
   479	            EditText name = input("Chatname", false);
   480	            name.setText(title);
   481	            new AlertDialog.Builder(this).setTitle("Chat umbenennen").setView(name)
   482	                .setNegativeButton("Abbrechen", null)
   483	                .setPositiveButton("Speichern", (dialog, which) -> updateChat(chatId, "title", name.getText().toString().trim()))
   484	                .show();
   485	            return true;
   486	        });
   487	        menu.getMenu().add(archived ? "Wiederherstellen" : "Archivieren")
   488	            .setOnMenuItemClickListener(item -> {
   489	                updateChat(chatId, "archived", archived ? "false" : "true");
   490	                return true;
   491	            });
   492	        menu.getMenu().add("Löschen").setOnMenuItemClickListener(item -> {
   493	            new AlertDialog.Builder(this).setTitle("Chat löschen?")
   494	                .setMessage("Der Chat und sein Verlauf werden dauerhaft gelöscht.")
   495	                .setNegativeButton("Abbrechen", null)
   496	                .setPositiveButton("Löschen", (dialog, which) -> deleteChat(chatId))
   497	                .show();
   498	            return true;
   499	        });
   500	        menu.show();
   501	    }
   502	
   503	    private void updateChat(String chatId, String field, String value) {
   504	        network.execute(() -> {
   505	            try {
   506	                JSONObject body = new JSONObject();
   507	                if ("archived".equals(field)) body.put(field, Boolean.parseBoolean(value));
   508	                else body.put(field, value);
   509	                requestJson("/api/chats/" + encodePath(chatId), "PATCH", body);
   510	                runOnUiThread(() -> {
   511	                    if (!"modelId".equals(field)) loadChatList();
   512	                });
   513	            } catch (Exception ex) {
   514	                runOnUiThread(() -> toastMessage(messageFor(ex)));
   515	            }
   516	        });
   517	    }
   518	
   519	    private void deleteChat(String chatId) {
   520	        network.execute(() -> {
   521	            try {
   522	                requestJson("/api/chats/" + encodePath(chatId), "DELETE", null);
   523	                runOnUiThread(this::loadChatList);
   524	            } catch (Exception ex) {
   525	                runOnUiThread(() -> toastMessage(messageFor(ex)));
   526	            }
   527	        });
   528	    }
   529	
   530	    private void createChat(Button source) {
   531	        source.setEnabled(false);
   532	        network.execute(() -> {
   533	            try {
   534	                JSONObject body = new JSONObject();
   535	                if (!selectedModelId.isEmpty()) body.put("modelId", selectedModelId);
   536	                JSONObject result = requestJson("/api/chats", "POST", body);
   537	                JSONObject chat = result.optJSONObject("chat");
   538	                if (chat == null) throw new Exception("Der Server hat keinen Chat zurückgegeben.");
   539	                String id = chat.optString("id");
   540	                String title = chat.optString("title", "Neuer Chat");
   541	                runOnUiThread(() -> openChat(id, title));
   542	            } catch (Exception ex) {
   543	                runOnUiThread(() -> {
   544	                    source.setEnabled(true);
   545	                    toastMessage(messageFor(ex));
   546	                });
   547	            }
   548	        });
   549	    }
   550	
   551	    private void openChat(String id, String title) {
   552	        activeChatId = id;
   553	        showConversation(title, new JSONArray(), false);
   554	        network.execute(() -> {
   555	            try {
   556	                JSONObject result = requestJson(
   557	                    "/api/chats/" + encodePath(id) + "?messageLimit=100",
   558	                    "GET", null
   559	                );
   560	                JSONObject chat = result.optJSONObject("chat");
   561	                if (chat != null && !chat.optString("modelId").isEmpty()) {
   562	                    selectedModelId = chat.optString("modelId");
   563	                    for (int i = 0; i < availableModels.length(); i++) {
   564	                        JSONObject model = availableModels.optJSONObject(i);
   565	                        if (model != null && selectedModelId.equals(model.optString("id"))) selectedModelName = model.optString("displayName", selectedModelId);
   566	                    }
   567	                }
   568	                JSONArray messages = chat == null ? new JSONArray() : chat.optJSONArray("messages");
   569	                if (messages == null) messages = new JSONArray();
   570	                JSONArray finalMessages = messages;
   571	                runOnUiThread(() -> {
   572	                    if (id.equals(activeChatId)) showConversation(title, finalMessages, false);
   573	                });
   574	            } catch (Exception ex) {
   575	                runOnUiThread(() -> {
   576	                    if (id.equals(activeChatId)) toastMessage(messageFor(ex));
   577	                });
   578	            }
   579	        });
   580	    }
   581	
   582	    private void showConversation(String title, JSONArray messages, boolean busy) {
   583	        LinearLayout root = page();
   584	        header(root, title, "Chats", v -> loadChatList());
   585	        ScrollView scroll = new ScrollView(this);
   586	        LinearLayout column = new LinearLayout(this);
   587	        column.setOrientation(LinearLayout.VERTICAL);
   588	        column.setPadding(dp(20), dp(18), dp(20), dp(14));
   589	        for (int i = 0; i < messages.length(); i++) {
   590	            JSONObject message = messages.optJSONObject(i);
   591	            if (message == null) continue;
   592	            String role = message.optString("role");
   593	            String content = message.optString("content");
   594	            addMessageBubble(column, role, content);
   595	        }
   596	        liveStatus = label(busy ? "Metis antwortet …" : "", 13, MUTED);
   597	        liveStatus.setPadding(dp(12), dp(6), dp(12), dp(10));
   598	        column.addView(liveStatus);
   599	        scroll.addView(column);
   600	        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
   601	
   602	        LinearLayout composeRow = new LinearLayout(this);
   603	        composeRow.setGravity(Gravity.CENTER_VERTICAL);
   604	        composeRow.setPadding(dp(7), dp(5), dp(7), dp(5));
   605	        composeRow.setBackground(outlinedSurface(Color.rgb(18, 18, 18), dp(20)));
   606	        composer = input("Nachricht an Metis …", false);
   607	        composer.setSingleLine(false);
   608	        composer.setMinLines(1);
   609	        composer.setMaxLines(5);
   610	        composer.setInputType(147457);
   611	        composer.setBackgroundColor(Color.TRANSPARENT);
   612	        composer.setPadding(dp(10), dp(10), dp(8), dp(10));
   613	        composer.setHintTextColor(Color.rgb(120, 120, 120));
   614	        TextView attach = label("＋", 22, MUTED);
   615	        attach.setGravity(Gravity.CENTER);
   616	        attach.setContentDescription("Dateien anhängen");
   617	        attach.setPadding(dp(6), 0, dp(8), 0);
   618	        attach.setOnClickListener(v -> pickAttachments());
   619	        composeRow.addView(attach, new LinearLayout.LayoutParams(dp(38), dp(42)));
   620	        composeRow.addView(composer, new LinearLayout.LayoutParams(0, -2, 1));
   621	        sendButton = button("↑");
   622	        sendButton.setContentDescription("Senden");
   623	        sendButton.setPadding(0, 0, 0, 0);
   624	        LinearLayout.LayoutParams sendParams = new LinearLayout.LayoutParams(dp(40), dp(40));
   625	        sendParams.leftMargin = dp(6);
   626	        composeRow.addView(sendButton, sendParams);
   627	        LinearLayout.LayoutParams composeParams = new LinearLayout.LayoutParams(-1, -2);
   628	        composeParams.setMargins(dp(12), dp(8), dp(12), dp(8));
   629	        root.addView(composeRow, composeParams);
   630	
   631	        LinearLayout modelBar = new LinearLayout(this);
   632	        modelBar.setGravity(Gravity.CENTER_VERTICAL);
   633	        modelBar.setPadding(dp(12), 0, dp(12), dp(4));
   634	        modelButton = label(selectedModelName + "  ⌄", 12, MUTED);
   635	        modelButton.setGravity(Gravity.CENTER_VERTICAL);
   636	        modelButton.setPadding(dp(6), dp(8), dp(8), dp(8));
   637	        modelButton.setEllipsize(TextUtils.TruncateAt.END);
   638	        modelButton.setSingleLine(true);
   639	        modelButton.setOnClickListener(v -> chooseModel());
   640	        modelBar.addView(modelButton, new LinearLayout.LayoutParams(-2, dp(34)));
   641	        root.addView(modelBar, new LinearLayout.LayoutParams(-1, -2));
   642	
   643	        sendButton.setEnabled(!busy);
   644	        sendButton.setText(busy ? "■" : "↑");
   645	        sendButton.setOnClickListener(v -> {
   646	            if (busyRun) { cancelRun(activeChatId); return; }
   647	            String text = composer.getText().toString().trim();
   648	            if (!text.isEmpty() || !pendingAttachments.isEmpty()) sendMessage(text, title, column, scroll);
   649	        });
   650	        setContentView(root);
   651	        scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
   652	    }
   653	
   654	    private void addMessageBubble(LinearLayout column, String role, String content) {
   655	        boolean user = "user".equals(role);
   656	        TextView bubble = label(content, 15, FG);
   657	        bubble.setTextIsSelectable(true);
   658	        bubble.setLineSpacing(dp(2), 1.08f);
   659	        if (!user && markdown != null) markdown.setMarkdown(bubble, content);
   660	        bubble.setMaxWidth((int) (getResources().getDisplayMetrics().widthPixels * 0.86f));
   661	        if (user) {
   662	            bubble.setPadding(dp(14), dp(11), dp(14), dp(11));
   663	            bubble.setBackground(background(SECONDARY, dp(10)));
   664	        } else {
   665	            bubble.setPadding(0, 0, 0, 0);
   666	            bubble.setBackgroundColor(Color.TRANSPARENT);
   667	        }
   668	        LinearLayout line = new LinearLayout(this);
   669	        line.setGravity(user ? Gravity.RIGHT : Gravity.LEFT);
   670	        line.addView(bubble, new LinearLayout.LayoutParams(user ? -2 : -1, -2));
   671	        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
   672	        params.bottomMargin = dp(user ? 16 : 18);
   673	        column.addView(line, params);
   674	    }
   675	
   676	    private void sendMessage(String text, String title, LinearLayout column, ScrollView scroll) {
   677	        composer.setText("");
   678	        composer.setEnabled(false);
   679	        busyRun = true;
   680	        sendButton.setEnabled(true);
   681	        sendButton.setText("■");
   682	        String displayText = text.isEmpty() && !pendingAttachments.isEmpty()
   683	            ? "Dateien angehängt (" + pendingAttachments.size() + ")" : text;
   684	        addMessageBubble(column, "user", displayText);
   685	        liveAssistantText = label("", 15, FG);
   686	        liveAssistantText.setLineSpacing(dp(2), 1.08f);
   687	        liveAssistantText.setPadding(0, 0, 0, 0);
   688	        LinearLayout assistantLine = new LinearLayout(this);
   689	        assistantLine.setGravity(Gravity.LEFT);
   690	        assistantLine.addView(liveAssistantText, new LinearLayout.LayoutParams(-1, -2));
   691	        LinearLayout.LayoutParams assistantParams = new LinearLayout.LayoutParams(-1, -2);
   692	        assistantParams.bottomMargin = dp(12);
   693	        column.addView(assistantLine, column.indexOfChild(liveStatus), assistantParams);
   694	        liveStatus.setText("Metis antwortet …");
   695	        scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
   696	        String chatId = activeChatId;
   697	
   698	        network.execute(() -> {
   699	            try {
   700	                JSONObject body = new JSONObject();
   701	                body.put("chatId", chatId);
   702	                body.put("message", text);
   703	                body.put("messageId", UUID.randomUUID().toString());
   704	                JSONArray attachmentPayload = readPendingAttachments();
   705	                if (attachmentPayload.length() > 0) body.put("attachments", attachmentPayload);
   706	                body.put("streamDeviceId", "android-" + UUID.randomUUID());
   707	                if (!selectedModelId.isEmpty()) body.put("modelId", selectedModelId);
   708	                HttpURLConnection connection = openConnection("/api/chat", "POST", null);
   709	                connection.setDoOutput(true);
   710	                connection.setRequestProperty("Content-Type", "application/json");
   711	                writeBody(connection, body.toString());
   712	                int code = connection.getResponseCode();
   713	                String response = readResponse(connection, code);
   714	                connection.disconnect();
   715	                if (code < 200 || code >= 300) throw apiError(response, code);
   716	                if (!pendingAttachments.isEmpty()) {
   717	                    pendingAttachments.clear();
   718	                    runOnUiThread(() -> { if (composer != null) composer.setHint("Nachricht an Metis …"); });
   719	                }
   720	                JSONObject accepted = new JSONObject(response);
   721	                String jobId = accepted.optString("jobId");
   722	                if (jobId.isEmpty()) throw new Exception("Der Server hat keine Run-ID zurückgegeben.");
   723	
   724	                long after = 0;
   725	                boolean finished = false;
   726	                StringBuilder answer = new StringBuilder();
   727	                while (!finished) {
   728	                    String path = "/api/runs?chatId=" + encodePath(chatId)
   729	                        + "&jobId=" + encodePath(jobId) + "&events=1&after=" + after;
   730	                    JSONObject result = requestJson(path, "GET", null);
   731	                    JSONArray events = result.optJSONArray("events");
   732	                    if (events != null) {
   733	                        for (int i = 0; i < events.length(); i++) {
   734	                            JSONObject event = events.optJSONObject(i);
   735	                            if (event == null) continue;
   736	                            after = Math.max(after, event.optLong("id", 0));
   737	                            JSONObject data = event.optJSONObject("data");
   738	                            if (data == null) continue;
   739	                            String kind = event.optString("event");
   740	                            if ("text".equals(kind)) {
   741	                                answer.append(data.optString("text", ""));
   742	                                String partial = answer.toString();
   743	                                runOnUiThread(() -> {
   744	                                    if (chatId.equals(activeChatId) && liveAssistantText != null) {
   745	                                        if (markdown != null) markdown.setMarkdown(liveAssistantText, partial);
   746	                                        else liveAssistantText.setText(partial);
   747	                                        scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
   748	                                    }
   749	                                });
   750	                            } else if ("status".equals(kind)) {
   751	                                String status = data.optString("message", data.optString("status", "Metis arbeitet …"));
   752	                                runOnUiThread(() -> {
   753	                                    if (chatId.equals(activeChatId) && liveStatus != null) liveStatus.setText(status);
   754	                                });
   755	                            } else if ("question".equals(kind)) {
   756	                                showPendingQuestion(data);
   757	                                runOnUiThread(() -> {
   758	                                    if (chatId.equals(activeChatId) && liveStatus != null) liveStatus.setText("Metis wartet auf deine Antwort");
   759	                                });
   760	                            } else if ("tool".equals(kind)) {
   761	                                String tool = data.optString("name", "Metis arbeitet");
   762	                                String state = data.optString("status", "wird ausgeführt");
   763	                                runOnUiThread(() -> {
   764	                                    if (chatId.equals(activeChatId) && liveStatus != null) liveStatus.setText(tool + " · " + state);
   765	                                });
   766	                            } else if ("done".equals(kind)) {
   767	                                finished = true;
   768	                            } else if ("error".equals(kind)) {
   769	                                throw new Exception(data.optString("message", "Agent-Run fehlgeschlagen."));
   770	                            }
   771	                        }
   772	                    }
   773	                    if (!finished) Thread.sleep(600);
   774	                }
   775	                runOnUiThread(() -> {
   776	                    if (!chatId.equals(activeChatId)) return;
   777	                    liveStatus.setText("");
   778	                    busyRun = false;
   779	                    composer.setEnabled(true);
   780	                    sendButton.setText("↑");
   781	                    sendButton.setEnabled(true);
   782	                    composer.requestFocus();
   783	                });
   784	            } catch (Exception ex) {
   785	                runOnUiThread(() -> {
   786	                    if (!chatId.equals(activeChatId)) return;
   787	                    if (liveStatus != null) liveStatus.setText(messageFor(ex));
   788	                    busyRun = false;
   789	                    if (composer != null) composer.setEnabled(true);
   790	                    if (sendButton != null) { sendButton.setText("↑"); sendButton.setEnabled(true); }
   791	                });
   792	            }
   793	        });
   794	    }
   795	
   796	    private void showPendingQuestion(JSONObject data) {
   797	        runOnUiThread(() -> {
   798	            JSONArray questions = data.optJSONArray("questions");
   799	            String questionId = data.optString("questionId");
   800	            if (questions == null || questions.length() == 0 || questionId.isEmpty()) return;
   801	            LinearLayout form = new LinearLayout(this);
   802	            form.setOrientation(LinearLayout.VERTICAL);
   803	            form.setPadding(dp(20), dp(8), dp(20), 0);
   804	            ArrayList<EditText> answers = new ArrayList<>();
   805	            for (int i = 0; i < questions.length(); i++) {
   806	                JSONObject question = questions.optJSONObject(i);
   807	                if (question == null) continue;
   808	                TextView prompt = label(question.optString("question"), 14, FG);
   809	                prompt.setPadding(0, dp(8), 0, dp(6));
   810	                form.addView(prompt);
   811	                EditText answer = input("Deine Antwort", false);
   812	                answer.setSingleLine(true);
   813	                form.addView(answer, new LinearLayout.LayoutParams(-1, -2));
   814	                answers.add(answer);
   815	            }
   816	            AlertDialog dialog = new AlertDialog.Builder(this)
   817	                .setTitle("Metis fragt nach")
   818	                .setView(form)
   819	                .setNegativeButton("Abbrechen", (d, which) -> cancelRun(activeChatId))
   820	                .setPositiveButton("Antwort senden", null)
   821	                .create();
   822	            dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
   823	                JSONArray values = new JSONArray();
   824	                for (EditText answer : answers) {
   825	                    String value = answer.getText().toString().trim();
   826	                    if (value.isEmpty()) { answer.setError("Bitte beantworten"); return; }
   827	                    values.put(value);
   828	                }
   829	                dialog.dismiss();
   830	                network.execute(() -> {
   831	                    try {
   832	                        JSONObject body = new JSONObject();
   833	                        body.put("questionId", questionId);
   834	                        body.put("version", data.optInt("version", 0));
   835	                        body.put("answers", values);
   836	                        requestJson("/api/chat/answer", "POST", body);
   837	                    } catch (Exception ex) {
   838	                        runOnUiThread(() -> toastMessage(messageFor(ex)));
   839	                    }
   840	                });
   841	            }));
   842	            dialog.show();
   843	        });
   844	    }
   845	
   846	    private void pickAttachments() {
   847	        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
   848	        intent.addCategory(Intent.CATEGORY_OPENABLE);
   849	        intent.setType("*/*");
   850	        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
   851	        startActivityForResult(intent, PICK_ATTACHMENTS);
   852	    }
   853	
   854	    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
   855	        super.onActivityResult(requestCode, resultCode, data);
   856	        if (requestCode != PICK_ATTACHMENTS || resultCode != RESULT_OK || data == null) return;
   857	        if (data.getClipData() != null) {
   858	            for (int i = 0; i < data.getClipData().getItemCount() && pendingAttachments.size() < 10; i++) {
   859	                Uri uri = data.getClipData().getItemAt(i).getUri();
   860	                if (!pendingAttachments.contains(uri)) pendingAttachments.add(uri);
   861	            }
   862	        } else if (data.getData() != null && !pendingAttachments.contains(data.getData())) {
   863	            pendingAttachments.add(data.getData());
   864	        }
   865	        if (pendingAttachments.size() >= 10) toastMessage("Maximal 10 Dateien pro Nachricht.");
   866	        if (composer != null) composer.setHint(pendingAttachments.isEmpty()
   867	            ? "Nachricht an Metis …" : pendingAttachments.size() + " Datei(en) angehängt · Nachricht an Metis …");
   868	    }
   869	
   870	    private JSONArray readPendingAttachments() throws Exception {
   871	        JSONArray result = new JSONArray();
   872	        for (Uri uri : new ArrayList<>(pendingAttachments)) {
   873	            String name = "Datei";
   874	            try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
   875	                if (cursor != null && cursor.moveToFirst()) {
   876	                    int column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
   877	                    if (column >= 0) name = cursor.getString(column);
   878	                }
   879	            }
   880	            String mime = getContentResolver().getType(uri);
   881	            if (mime == null) mime = "application/octet-stream";
   882	            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
   883	            try (InputStream input = getContentResolver().openInputStream(uri)) {
   884	                if (input == null) throw new Exception("Datei kann nicht geöffnet werden: " + name);
   885	                byte[] buffer = new byte[8192];
   886	                int count;
   887	                while ((count = input.read(buffer)) != -1) {
   888	                    if (bytes.size() + count > 50 * 1024 * 1024) throw new Exception("Datei zu groß (max. 50 MB): " + name);
   889	                    bytes.write(buffer, 0, count);
   890	                }
   891	            }
   892	            JSONObject item = new JSONObject();
   893	            item.put("name", name);
   894	            item.put("mimeType", mime);
   895	            item.put("data", Base64.encodeToString(bytes.toByteArray(), Base64.NO_WRAP));
   896	            result.put(item);
   897	        }
   898	        return result;
   899	    }
   900	
   901	    private void cancelRun(String chatId) {
   902	        if (chatId == null || chatId.isEmpty()) return;
   903	        sendButton.setEnabled(false);
   904	        liveStatus.setText("Antwort wird gestoppt …");
   905	        network.execute(() -> {
   906	            try {
   907	                JSONObject body = new JSONObject();
   908	                body.put("chatId", chatId);
   909	                requestJson("/api/chat/cancel", "POST", body);
   910	            } catch (Exception ex) {
   911	                runOnUiThread(() -> toastMessage(messageFor(ex)));
   912	            }
   913	        });
   914	    }
   915	
   916	    private void logout() {
   917	        network.execute(() -> {
   918	            try { requestJson("/api/auth", "DELETE", null); } catch (Exception ignored) {}
   919	            sessionCookie = "";
   920	            getPreferences(MODE_PRIVATE).edit().remove(SESSION).apply();
   921	            runOnUiThread(() -> showLogin(""));
   922	        });
   923	    }
   924	
   925	    private HttpURLConnection openConnection(String path, String method, String ignored) throws Exception {
   926	        URL url = new URL(serverUrl + path);
   927	        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
   928	        connection.setRequestMethod(method);
   929	        connection.setConnectTimeout(15000);
   930	        connection.setReadTimeout(20000);
   931	        connection.setRequestProperty("Accept", "application/json");
   932	        connection.setRequestProperty("X-Metis-Device-Id", "metis-android");
   933	        if (!sessionCookie.isEmpty()) connection.setRequestProperty("Cookie", sessionCookie);
   934	        return connection;
   935	    }
   936	
   937	    private void writeBody(HttpURLConnection connection, String body) throws Exception {
   938	        byte[] data = body.getBytes(StandardCharsets.UTF_8);
   939	        try (OutputStream output = connection.getOutputStream()) {
   940	            output.write(data);
   941	        }
   942	    }
   943	
   944	    private String readResponse(HttpURLConnection connection, int code) throws Exception {
   945	        InputStream stream = code >= 400 ? connection.getErrorStream() : connection.getInputStream();
   946	        if (stream == null) return "";
   947	        StringBuilder result = new StringBuilder();
   948	        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
   949	            String line;
   950	            while ((line = reader.readLine()) != null) result.append(line);
   951	        }
   952	        return result.toString();
   953	    }
   954	
   955	    private JSONObject requestJson(String path, String method, JSONObject body) throws Exception {
   956	        HttpURLConnection connection = openConnection(path, method, null);
   957	        if (body != null) {
   958	            connection.setDoOutput(true);
   959	            connection.setRequestProperty("Content-Type", "application/json");
   960	            writeBody(connection, body.toString());
   961	        }
   962	        int code = connection.getResponseCode();
   963	        String response = readResponse(connection, code);
   964	        connection.disconnect();
   965	        if (code < 200 || code >= 300) throw apiError(response, code);
   966	        return response.isEmpty() ? new JSONObject() : new JSONObject(response);
   967	    }
   968	
   969	    private Exception apiError(String response, int code) {
   970	        try {
   971	            String message = new JSONObject(response).optString("error");
   972	            if (!message.isEmpty()) return new Exception(message);
   973	        } catch (Exception ignored) {}
   974	        return new Exception("Serverfehler (HTTP " + code + ")");
   975	    }
   976	
   977	    private boolean isUnauthorized(Exception ex) {
   978	        return ex.getMessage() != null && ex.getMessage().contains("Unauthorized");
   979	    }
   980	
   981	    private String messageFor(Exception ex) {
   982	        if (ex instanceof java.net.UnknownHostException) return "Server nicht gefunden. Prüfe die Serveradresse.";
   983	        if (ex instanceof java.net.ConnectException) return "Verbindung zum Metis-Server nicht möglich.";
   984	        return ex.getMessage() == null ? "Verbindung fehlgeschlagen." : ex.getMessage();
   985	    }
   986	
   987	    private String encodePath(String value) throws Exception {
   988	        return URLEncoder.encode(value, "UTF-8");
   989	    }
   990	
   991	    private GradientDrawable background(int color, int radius) {
   992	        GradientDrawable shape = new GradientDrawable();
   993	        shape.setColor(color);
   994	        shape.setCornerRadius(radius);
   995	        return shape;
   996	    }
   997	
   998	    private GradientDrawable outlinedSurface(int color, int radius) {
   999	        GradientDrawable shape = background(color, radius);
  1000	        shape.setStroke(dp(1), BORDER);
  1001	        return shape;
  1002	    }
  1003	
  1004	    private void toastMessage(String text) {
  1005	        android.widget.Toast.makeText(this, text, android.widget.Toast.LENGTH_LONG).show();
  1006	    }
  1007	
  1008	    @Override public void onBackPressed() {
  1009	        if (!activeChatId.isEmpty()) {
  1010	            activeChatId = "";
  1011	            loadChatList();
  1012	        } else {
  1013	            super.onBackPressed();
  1014	        }
  1015	    }
  1016	
  1017	    @Override protected void onDestroy() {
  1018	        network.shutdownNow();
  1019	        super.onDestroy();
  1020	    }
  1021	}
