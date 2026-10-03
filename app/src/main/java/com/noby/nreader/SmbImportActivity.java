package com.noby.nreader;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.hierynomus.msdtyp.AccessMask;
import com.hierynomus.msfscc.FileAttributes;
import com.hierynomus.msfscc.fileinformation.FileIdBothDirectoryInformation;
import com.hierynomus.mssmb2.SMB2CreateDisposition;
import com.hierynomus.mssmb2.SMB2ShareAccess;
import com.hierynomus.smbj.SMBClient;
import com.hierynomus.smbj.auth.AuthenticationContext;
import com.hierynomus.smbj.connection.Connection;
import com.hierynomus.smbj.session.Session;
import com.hierynomus.smbj.share.DiskShare;
import com.noby.nreader.model.BookStore;

import android.net.wifi.WifiManager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class SmbImportActivity extends Activity {
    private static final String PREFS_SMB = "nreader_smb";

    private final List<SmbItem> items = new ArrayList<SmbItem>();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private ListView listView;
    private TextView pathView;
    private TextView statusView;
    private ProgressBar loadingBar;
    private SmbAdapter adapter;

    private SMBClient smbClient;
    private Connection connection;
    private Session session;
    private DiskShare diskShare;

    private String currentHost = "";
    private String currentShare = "";
    private String currentPath = "";
    private String currentUser = "";
    private String currentPass = "";

    public static class SmbItem {
        final String name;
        final boolean isDirectory;
        final long size;

        public SmbItem(String name, boolean isDirectory, long size) {
            this.name = name;
            this.isDirectory = isDirectory;
            this.size = size;
        }
    }

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(AppText.wrapContext(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
        loadSavedConfigAndPrompt();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(32), dp(24), dp(32), dp(24));
        root.setBackgroundColor(Color.rgb(15, 23, 42)); // Deep Slate

        // Top bar
        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = new TextView(this);
        title.setText(AppText.get(R.string.smb_title));
        title.setTextSize(24);
        title.setTextColor(Color.WHITE);
        title.getPaint().setFakeBoldText(true);
        topBar.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));

        Button btnScan = new Button(this);
        btnScan.setText(AppText.get(R.string.smb_scan_lan));
        btnScan.setTextSize(13);
        btnScan.setTextColor(Color.WHITE);
        btnScan.setBackground(ThemeHelper.createButtonDrawable(ThemeHelper.ACCENT_CYAN, 8, this));
        btnScan.setPadding(dp(16), dp(8), dp(16), dp(8));
        btnScan.setFocusable(true);
        btnScan.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startLanAutoScan();
            }
        });
        topBar.addView(btnScan);

        Button btnManual = new Button(this);
        btnManual.setText(AppText.get(R.string.smb_manual_connect));
        btnManual.setTextSize(13);
        btnManual.setTextColor(Color.WHITE);
        btnManual.setBackground(ThemeHelper.createButtonDrawable(Color.rgb(30, 41, 59), 8, this));
        btnManual.setPadding(dp(16), dp(8), dp(16), dp(8));
        btnManual.setFocusable(true);
        LinearLayout.LayoutParams manualParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        manualParams.setMargins(dp(10), 0, 0, 0);
        btnManual.setLayoutParams(manualParams);
        btnManual.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                promptSmbConnection();
            }
        });
        topBar.addView(btnManual);

        Button btnImportAll = new Button(this);
        btnImportAll.setText(AppText.get(R.string.folder_import_all));
        btnImportAll.setTextSize(13);
        btnImportAll.setTextColor(Color.WHITE);
        btnImportAll.setBackground(ThemeHelper.createButtonDrawable(Color.rgb(30, 41, 59), 8, this));
        btnImportAll.setPadding(dp(16), dp(8), dp(16), dp(8));
        btnImportAll.setFocusable(true);
        LinearLayout.LayoutParams allParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        allParams.setMargins(dp(10), 0, 0, 0);
        btnImportAll.setLayoutParams(allParams);
        btnImportAll.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                confirmImportAllFolder();
            }
        });
        topBar.addView(btnImportAll);

        root.addView(topBar);

        pathView = new TextView(this);
        pathView.setText(AppText.get(R.string.smb_not_connected));
        pathView.setTextSize(14);
        pathView.setTextColor(ThemeHelper.ACCENT_CYAN);
        pathView.setPadding(0, dp(8), 0, dp(4));
        root.addView(pathView);

        loadingBar = new ProgressBar(this);
        loadingBar.setVisibility(View.GONE);
        root.addView(loadingBar);

        listView = new ListView(this);
        listView.setDividerHeight(dp(4));
        listView.setSelector(ThemeHelper.createButtonDrawable(Color.argb(60, 56, 189, 248), 6, this));
        listView.setDrawSelectorOnTop(true);
        adapter = new SmbAdapter();
        listView.setAdapter(adapter);

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                activateItem(position);
            }
        });

        LinearLayout.LayoutParams listParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f);
        listParams.setMargins(0, dp(10), 0, dp(10));
        root.addView(listView, listParams);

        statusView = new TextView(this);
        statusView.setText(AppText.get(R.string.smb_help));
        statusView.setTextSize(13);
        statusView.setTextColor(Color.rgb(148, 163, 184));
        root.addView(statusView);

        setContentView(root);
    }

    private void loadSavedConfigAndPrompt() {
        SharedPreferences sp = getSharedPreferences(PREFS_SMB, MODE_PRIVATE);
        currentHost = sp.getString("host", "");
        currentShare = sp.getString("share", "");
        currentUser = sp.getString("user", "");
        currentPass = sp.getString("pass", "");

        if (currentHost.length() > 0 && currentShare.length() > 0) {
            connectToSmb(currentHost, currentShare, currentUser, currentPass);
        } else {
            startLanAutoScan();
        }
    }

    private String getLocalSubnetPrefix() {
        try {
            WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wm != null && wm.getConnectionInfo() != null) {
                int ip = wm.getConnectionInfo().getIpAddress();
                if (ip != 0) {
                    return String.format(Locale.US, "%d.%d.%d.",
                            (ip & 0xff), (ip >> 8 & 0xff), (ip >> 16 & 0xff));
                }
            }
            Enumeration<NetworkInterface> ifaces = NetworkInterface.getNetworkInterfaces();
            while (ifaces != null && ifaces.hasMoreElements()) {
                NetworkInterface iface = ifaces.nextElement();
                if (iface.isLoopback() || !iface.isUp()) continue;
                Enumeration<InetAddress> addrs = iface.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress addr = addrs.nextElement();
                    if (addr instanceof Inet4Address && !addr.isLoopbackAddress()) {
                        String ip = addr.getHostAddress();
                        int dot = ip.lastIndexOf('.');
                        if (dot > 0) {
                            return ip.substring(0, dot + 1);
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return "192.168.2.";
    }

    private void startLanAutoScan() {
        final String prefix = getLocalSubnetPrefix();
        loadingBar.setVisibility(View.VISIBLE);
        pathView.setText(AppText.format(R.string.smb_scanning_lan, prefix));

        final List<String> foundIps = Collections.synchronizedList(new ArrayList<String>());
        final ExecutorService executor = Executors.newFixedThreadPool(32);
        final AtomicInteger remaining = new AtomicInteger(254);

        for (int i = 1; i <= 254; i++) {
            final String targetIp = prefix + i;
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    Socket socket = null;
                    try {
                        socket = new Socket();
                        socket.connect(new InetSocketAddress(targetIp, 445), 300);
                        foundIps.add(targetIp);
                    } catch (Exception ignored) {
                    } finally {
                        if (socket != null) {
                            try {
                                socket.close();
                            } catch (Exception ignored) {
                            }
                        }
                        if (remaining.decrementAndGet() == 0) {
                            executor.shutdown();
                            mainHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    loadingBar.setVisibility(View.GONE);
                                    Collections.sort(foundIps);
                                    handleDiscoveredServers(foundIps);
                                }
                            });
                        }
                    }
                }
            });
        }
    }

    private void handleDiscoveredServers(final List<String> servers) {
        if (isFinishing() || (Build.VERSION.SDK_INT >= 17 && isDestroyed())) return;
        if (servers.isEmpty()) {
            pathView.setText(AppText.get(R.string.smb_no_devices_found));
            Toast.makeText(this, AppText.get(R.string.smb_no_devices_found), Toast.LENGTH_LONG).show();
            promptSmbConnection();
            return;
        }

        pathView.setText(AppText.get(R.string.smb_devices_found) + ": " + servers.size());

        final String[] options = new String[servers.size() + 1];
        for (int i = 0; i < servers.size(); i++) {
            options[i] = "💻 " + servers.get(i) + " (SMB 服务器)";
        }
        options[servers.size()] = "✏️ " + AppText.get(R.string.smb_manual_connect);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(AppText.get(R.string.smb_devices_found));
        builder.setItems(options, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (which < servers.size()) {
                    currentHost = servers.get(which);
                    promptAuthForHost(currentHost);
                } else {
                    promptSmbConnection();
                }
            }
        });
        builder.setNegativeButton(AppText.get(R.string.cancel), null);
        builder.show();
    }

    private void promptAuthForHost(final String host) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(20), dp(10), dp(20), dp(10));

        final EditText etUser = new EditText(this);
        etUser.setHint(AppText.get(R.string.smb_hint_user));
        etUser.setText(currentUser.isEmpty() ? "noby" : currentUser);
        layout.addView(etUser);

        final EditText etPass = new EditText(this);
        etPass.setHint(AppText.get(R.string.smb_hint_pass));
        etPass.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        etPass.setText(currentPass);
        layout.addView(etPass);

        final EditText etShare = new EditText(this);
        etShare.setHint(AppText.isChinese() ? "共享名称 (可选，留空将自动探测)" : "Share name (optional, auto-detect if blank)");
        etShare.setText(currentShare);
        layout.addView(etShare);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(AppText.format(R.string.smb_auth_title, host));
        builder.setView(layout);
        builder.setPositiveButton(AppText.isChinese() ? "连接" : "Connect", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                currentUser = etUser.getText().toString().trim();
                currentPass = etPass.getText().toString().trim();
                String rawShare = etShare.getText().toString().trim();
                currentShare = sanitizeShareName(rawShare);

                getSharedPreferences(PREFS_SMB, MODE_PRIVATE).edit()
                        .putString("host", host)
                        .putString("share", currentShare)
                        .putString("user", currentUser)
                        .putString("pass", currentPass)
                        .apply();

                if (!currentShare.isEmpty()) {
                    connectToSmb(host, currentShare, currentUser, currentPass);
                } else {
                    discoverSharesOnHost(host, currentUser, currentPass);
                }
            }
        });
        builder.setNegativeButton(AppText.get(R.string.cancel), null);
        builder.show();
    }

    private void discoverSharesOnHost(final String host, final String user, final String pass) {
        loadingBar.setVisibility(View.VISIBLE);
        pathView.setText(AppText.format(R.string.smb_discovering_shares, host));

        new AsyncTask<Void, Void, List<String>>() {
            private String authError = null;

            @Override
            protected List<String> doInBackground(Void... voids) {
                List<String> found = new ArrayList<String>();
                try {
                    closeConnection();
                    smbClient = new SMBClient();
                    connection = smbClient.connect(host);
                    AuthenticationContext auth = user.length() > 0
                            ? new AuthenticationContext(user, pass.toCharArray(), "")
                            : AuthenticationContext.anonymous();
                    session = connection.authenticate(auth);

                    String[] candidates = {
                            "test", "noby", "books", "Books", "book", "ebook", "ebooks",
                            "public", "Public", "share", "Share", "shared", "Shared",
                            "documents", "Documents", "nas", "media", "data", "download",
                            "Downloads", "NobyT7", "“Noby Tan”的公共文件夹"
                    };

                    for (String cand : candidates) {
                        try {
                            DiskShare ds = (DiskShare) session.connectShare(cand);
                            if (ds != null) {
                                if (!found.contains(cand)) {
                                    found.add(cand);
                                }
                                ds.close();
                            }
                        } catch (Exception ignored) {
                        }
                    }
                } catch (Exception e) {
                    authError = formatSmbError(e, host, "", user);
                    AppLog.error("SMB discover shares error", e);
                }
                return found;
            }

            @Override
            protected void onPostExecute(List<String> shares) {
                loadingBar.setVisibility(View.GONE);
                if (isFinishing() || (Build.VERSION.SDK_INT >= 17 && isDestroyed())) return;

                if (authError != null) {
                    pathView.setText(authError);
                    Toast.makeText(SmbImportActivity.this, authError, Toast.LENGTH_LONG).show();
                    return;
                }

                if (shares == null || shares.isEmpty()) {
                    Toast.makeText(SmbImportActivity.this, AppText.isChinese() ? "未能自动探测到公开共享，请输入共享名" : "No public shares detected, please enter share name", Toast.LENGTH_SHORT).show();
                    promptSmbConnection();
                    return;
                }

                if (shares.size() == 1) {
                    currentShare = shares.get(0);
                    connectToSmb(host, currentShare, user, pass);
                    return;
                }

                showShareSelectionDialog(host, shares, user, pass);
            }
        }.execute();
    }

    private void showShareSelectionDialog(final String host, final List<String> shares, final String user, final String pass) {
        final String[] options = new String[shares.size() + 1];
        for (int i = 0; i < shares.size(); i++) {
            options[i] = "📁 " + shares.get(i);
        }
        options[shares.size()] = "✏️ 手动输入共享名";

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(AppText.get(R.string.smb_select_share));
        builder.setItems(options, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (which < shares.size()) {
                    currentShare = shares.get(which);
                    getSharedPreferences(PREFS_SMB, MODE_PRIVATE).edit()
                            .putString("share", currentShare).apply();
                    connectToSmb(host, currentShare, user, pass);
                } else {
                    promptSmbConnection();
                }
            }
        });
        builder.setNegativeButton(AppText.get(R.string.cancel), null);
        builder.show();
    }

    private String sanitizeShareName(String raw) {
        if (raw == null) return "";
        String s = raw.replaceAll("^[\"']+|[\"']+$", "").trim();
        s = s.replace("\\ ", " ");
        s = s.replace("\\", "/").trim();
        while (s.startsWith("/")) {
            s = s.substring(1);
        }
        while (s.endsWith("/")) {
            s = s.substring(0, s.length() - 1);
        }
        if (s.contains("/")) {
            int lastSlash = s.lastIndexOf('/');
            return s.substring(lastSlash + 1);
        }
        return s;
    }

    private void promptSmbConnection() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(20), dp(10), dp(20), dp(10));

        final EditText etHost = new EditText(this);
        etHost.setHint(AppText.get(R.string.smb_hint_host));
        etHost.setText(currentHost);
        layout.addView(etHost);

        final EditText etShare = new EditText(this);
        etShare.setHint(AppText.get(R.string.smb_hint_share));
        etShare.setText(currentShare);
        layout.addView(etShare);

        final EditText etUser = new EditText(this);
        etUser.setHint(AppText.get(R.string.smb_hint_user));
        etUser.setText(currentUser);
        layout.addView(etUser);

        final EditText etPass = new EditText(this);
        etPass.setHint(AppText.get(R.string.smb_hint_pass));
        etPass.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        etPass.setText(currentPass);
        layout.addView(etPass);

        TextView tvTips = new TextView(this);
        tvTips.setText(AppText.isChinese()
                ? "提示：Mac 共享名通常为文件夹名（如 test，无需填 /Users/... 完整路径）；用户名请使用短用户名（如 noby，非显示全名）。"
                : "Tip: Mac share name is the folder name (e.g. test, not /Users/... path). Username is the short username (e.g. noby).");
        tvTips.setTextSize(12);
        tvTips.setTextColor(Color.rgb(148, 163, 184));
        tvTips.setPadding(dp(4), dp(10), dp(4), dp(4));
        layout.addView(tvTips);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(AppText.get(R.string.smb_dialog_title));
        builder.setView(layout);
        builder.setPositiveButton(AppText.get(R.string.smb_connect_btn), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String rawHost = etHost.getText().toString().trim();
                String rawShare = etShare.getText().toString().trim();
                currentUser = etUser.getText().toString().trim();
                currentPass = etPass.getText().toString().trim();

                rawHost = rawHost.replace("smb://", "").replace("\\", "/").trim();
                if (rawHost.contains("/")) {
                    String[] parts = rawHost.split("/", 2);
                    rawHost = parts[0];
                    if (rawShare.isEmpty()) {
                        rawShare = parts[1];
                    }
                }
                currentHost = rawHost;
                currentShare = sanitizeShareName(rawShare);

                getSharedPreferences(PREFS_SMB, MODE_PRIVATE).edit()
                        .putString("host", currentHost)
                        .putString("share", currentShare)
                        .putString("user", currentUser)
                        .putString("pass", currentPass)
                        .apply();

                connectToSmb(currentHost, currentShare, currentUser, currentPass);
            }
        });
        builder.setNegativeButton(AppText.get(R.string.cancel), null);
        builder.show();
    }

    private void connectToSmb(final String host, final String share, final String user, final String pass) {
        loadingBar.setVisibility(View.VISIBLE);
        pathView.setText(AppText.format(R.string.smb_connecting, host, share));

        new AsyncTask<Void, Void, String>() {
            @Override
            protected String doInBackground(Void... voids) {
                try {
                    closeConnection();
                    smbClient = new SMBClient();
                    connection = smbClient.connect(host);
                    AuthenticationContext auth = user.length() > 0
                            ? new AuthenticationContext(user, pass.toCharArray(), "")
                            : AuthenticationContext.anonymous();
                    session = connection.authenticate(auth);
                    try {
                        diskShare = (DiskShare) session.connectShare(share);
                    } catch (Exception shareEx) {
                        if (user.length() > 0 && !share.equalsIgnoreCase(user)) {
                            try {
                                diskShare = (DiskShare) session.connectShare(user);
                            } catch (Exception ignored) {
                                throw shareEx;
                            }
                        } else {
                            throw shareEx;
                        }
                    }
                    currentPath = "";
                    return null;
                } catch (Exception e) {
                    AppLog.error("SMB connection failed", e);
                    return formatSmbError(e, host, share, user);
                }
            }

            @Override
            protected void onPostExecute(String error) {
                loadingBar.setVisibility(View.GONE);
                if (isFinishing() || (Build.VERSION.SDK_INT >= 17 && isDestroyed())) {
                    return;
                }
                if (error != null) {
                    pathView.setText(AppText.format(R.string.smb_connect_failed, error));
                    Toast.makeText(SmbImportActivity.this, AppText.format(R.string.smb_connect_failed, error), Toast.LENGTH_LONG).show();
                } else {
                    pathView.setText("smb://" + host + "/" + share + "/");
                    loadFolder(currentPath);
                }
            }
        }.execute();
    }

    private String formatSmbError(Exception e, String host, String share, String user) {
        String msg = e.getMessage() != null ? e.getMessage() : e.toString();
        if (msg.contains("EOF") || msg.contains("ConnectException") || msg.contains("timeout") || msg.contains("unreachable")) {
            return AppText.isChinese()
                    ? ("无法连接至 " + host + "。请确认 IP 是否正确（Mac 当前 IP: 192.168.2.102），且电视与 Mac 在同一 Wi-Fi。")
                    : ("Cannot connect to " + host + ". Check IP and network connection.");
        }
        if (msg.contains("0xc00000cc") || msg.contains("STATUS_BAD_NETWORK_NAME") || msg.contains("STATUS_OBJECT_NAME_NOT_FOUND")) {
            return AppText.isChinese()
                    ? ("找不到共享名【" + share + "】。请在 Mac【系统设置-通用-共享-文件共享】中添加该文件夹，共享名称直接填文件夹名（如 test，勿填路径）。")
                    : ("Share name [" + share + "] not found on server.");
        }
        if (msg.contains("0xc000006d") || msg.contains("STATUS_LOGON_FAILURE") || msg.contains("STATUS_WRONG_PASSWORD")) {
            return AppText.isChinese()
                    ? "用户名或密码错误。Mac 用户名请使用短用户名（如 noby，非显示全名）。"
                    : "Invalid username or password. On Mac, use the short username.";
        }
        return msg;
    }

    private void loadFolder(final String folder) {
        if (diskShare == null) return;
        loadingBar.setVisibility(View.VISIBLE);

        new AsyncTask<String, Void, List<SmbItem>>() {
            @Override
            protected List<SmbItem> doInBackground(String... params) {
                List<SmbItem> result = new ArrayList<SmbItem>();
                try {
                    String path = params[0];
                    List<FileIdBothDirectoryInformation> list = diskShare.list(path);
                    for (FileIdBothDirectoryInformation info : list) {
                        String name = info.getFileName();
                        if (".".equals(name) || "..".equals(name)) continue;
                        boolean isDir = (info.getFileAttributes() & FileAttributes.FILE_ATTRIBUTE_DIRECTORY.getValue()) != 0;
                        if (isDir || BookStore.isSupported(name)) {
                            result.add(new SmbItem(name, isDir, info.getEndOfFile()));
                        }
                    }
                    Collections.sort(result, new Comparator<SmbItem>() {
                        @Override
                        public int compare(SmbItem a, SmbItem b) {
                            if (a.isDirectory != b.isDirectory) {
                                return a.isDirectory ? -1 : 1;
                            }
                            return a.name.compareToIgnoreCase(b.name);
                        }
                    });
                } catch (Exception e) {
                    AppLog.error("SMB list failed", e);
                }
                return result;
            }

            @Override
            protected void onPostExecute(List<SmbItem> result) {
                loadingBar.setVisibility(View.GONE);
                if (isFinishing() || (Build.VERSION.SDK_INT >= 17 && isDestroyed())) {
                    return;
                }
                items.clear();
                if (currentPath.length() > 0) {
                    items.add(new SmbItem(AppText.get(R.string.parent_folder), true, 0));
                }
                items.addAll(result);
                adapter.notifyDataSetChanged();
                pathView.setText("smb://" + currentHost + "/" + currentShare + "/" + currentPath);
                listView.setSelection(0);
            }
        }.execute(folder);
    }

    private void activateItem(int position) {
        if (position < 0 || position >= items.size()) return;
        SmbItem item = items.get(position);

        if (item.name.equals(AppText.get(R.string.parent_folder))) {
            navigateUp();
            return;
        }

        if (item.isDirectory) {
            currentPath = currentPath.isEmpty() ? item.name : currentPath + "\\" + item.name;
            loadFolder(currentPath);
        } else {
            downloadAndImportBook(item);
        }
    }

    private void navigateUp() {
        if (currentPath.isEmpty()) return;
        int last = currentPath.lastIndexOf('\\');
        currentPath = last >= 0 ? currentPath.substring(0, last) : "";
        loadFolder(currentPath);
    }

    private void downloadAndImportBook(final SmbItem item) {
        loadingBar.setVisibility(View.VISIBLE);
        statusView.setText(AppText.format(R.string.smb_downloading, item.name));

        new AsyncTask<Void, Void, Boolean>() {
            @Override
            protected Boolean doInBackground(Void... voids) {
                File temp = null;
                try {
                    String fullPath = currentPath.isEmpty() ? item.name : currentPath + "\\" + item.name;
                    com.hierynomus.smbj.share.File smbFile = diskShare.openFile(
                            fullPath,
                            EnumSet.of(AccessMask.GENERIC_READ),
                            null,
                            SMB2ShareAccess.ALL,
                            SMB2CreateDisposition.FILE_OPEN,
                            null);

                    InputStream in = smbFile.getInputStream();
                    temp = File.createTempFile("smb_dl_", ".tmp", getCacheDir());
                    FileOutputStream out = new FileOutputStream(temp);

                    byte[] buf = new byte[64 * 1024];
                    int len;
                    while ((len = in.read(buf)) != -1) {
                        out.write(buf, 0, len);
                    }
                    out.flush();
                    out.close();
                    in.close();
                    smbFile.close();

                    File dest = new File(BookStore.getLibraryDir(SmbImportActivity.this), item.name);
                    if (dest.exists()) {
                        dest.delete();
                    }
                    if (!temp.renameTo(dest)) {
                        BookStore.copyFile(temp, dest);
                        temp.delete();
                    }
                    BookStore.recordActivity(SmbImportActivity.this, dest);
                    return true;
                } catch (Exception e) {
                    if (temp != null && temp.exists()) {
                        temp.delete();
                    }
                    AppLog.error("SMB download failed", e);
                    return false;
                }
            }

            @Override
            protected void onPostExecute(Boolean success) {
                loadingBar.setVisibility(View.GONE);
                if (isFinishing() || (Build.VERSION.SDK_INT >= 17 && isDestroyed())) {
                    return;
                }
                if (success) {
                    Toast.makeText(SmbImportActivity.this, AppText.format(R.string.smb_import_success, item.name), Toast.LENGTH_SHORT).show();
                    setResult(RESULT_OK);
                    finish();
                } else {
                    Toast.makeText(SmbImportActivity.this, AppText.get(R.string.smb_import_failed), Toast.LENGTH_LONG).show();
                    statusView.setText(AppText.get(R.string.smb_import_failed));
                }
            }
        }.execute();
    }

    private void confirmImportAllFolder() {
        if (diskShare == null) {
            Toast.makeText(this, AppText.get(R.string.smb_not_connected), Toast.LENGTH_SHORT).show();
            return;
        }
        final List<SmbItem> books = new ArrayList<SmbItem>();
        for (SmbItem item : items) {
            if (!item.isDirectory && BookStore.isSupported(item.name)) {
                books.add(item);
            }
        }
        if (books.isEmpty()) {
            Toast.makeText(this, AppText.isChinese() ? "当前文件夹没有可导入的电子书" : "No supported books in folder", Toast.LENGTH_SHORT).show();
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(AppText.get(R.string.folder_import_all));
        String msg = AppText.isChinese() ? ("确认导入当前文件夹中的 " + books.size() + " 本书籍？")
                : ("Import all " + books.size() + " books in current folder?");
        builder.setMessage(msg);
        builder.setPositiveButton(AppText.isChinese() ? "导入全部" : "Import All", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                downloadAllBooksInFolder(books);
            }
        });
        builder.setNegativeButton(AppText.get(R.string.cancel), null);
        builder.show();
    }

    private void downloadAllBooksInFolder(final List<SmbItem> books) {
        loadingBar.setVisibility(View.VISIBLE);
        new AsyncTask<Void, String, Integer>() {
            @Override
            protected Integer doInBackground(Void... voids) {
                int successCount = 0;
                for (int i = 0; i < books.size(); i++) {
                    SmbItem item = books.get(i);
                    publishProgress(String.format(Locale.US, "(%d/%d) %s", i + 1, books.size(), item.name));
                    File temp = null;
                    try {
                        String fullPath = currentPath.isEmpty() ? item.name : currentPath + "\\" + item.name;
                        com.hierynomus.smbj.share.File smbFile = diskShare.openFile(
                                fullPath,
                                EnumSet.of(AccessMask.GENERIC_READ),
                                null,
                                SMB2ShareAccess.ALL,
                                SMB2CreateDisposition.FILE_OPEN,
                                null);

                        InputStream in = smbFile.getInputStream();
                        temp = File.createTempFile("smb_batch_", ".tmp", getCacheDir());
                        FileOutputStream out = new FileOutputStream(temp);

                        byte[] buf = new byte[64 * 1024];
                        int len;
                        while ((len = in.read(buf)) != -1) {
                            out.write(buf, 0, len);
                        }
                        out.flush();
                        out.close();
                        in.close();
                        smbFile.close();

                        File dest = new File(BookStore.getLibraryDir(SmbImportActivity.this), item.name);
                        if (dest.exists()) {
                            dest.delete();
                        }
                        if (!temp.renameTo(dest)) {
                            BookStore.copyFile(temp, dest);
                            temp.delete();
                        }
                        BookStore.recordActivity(SmbImportActivity.this, dest);
                        successCount++;
                    } catch (Exception e) {
                        if (temp != null && temp.exists()) {
                            temp.delete();
                        }
                        AppLog.error("SMB batch download failed for " + item.name, e);
                    }
                }
                return successCount;
            }

            @Override
            protected void onProgressUpdate(String... values) {
                if (values != null && values.length > 0) {
                    statusView.setText(AppText.isChinese() ? ("正在下载: " + values[0]) : ("Downloading: " + values[0]));
                }
            }

            @Override
            protected void onPostExecute(Integer imported) {
                loadingBar.setVisibility(View.GONE);
                if (isFinishing() || (Build.VERSION.SDK_INT >= 17 && isDestroyed())) {
                    return;
                }
                Toast.makeText(SmbImportActivity.this, AppText.format(R.string.books_imported, imported), Toast.LENGTH_LONG).show();
                setResult(RESULT_OK);
                finish();
            }
        }.execute();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (currentPath.length() > 0) {
                navigateUp();
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    private void closeConnection() {
        if (diskShare != null) {
            try { diskShare.close(); } catch (Exception ignored) {}
            diskShare = null;
        }
        if (session != null) {
            try { session.close(); } catch (Exception ignored) {}
            session = null;
        }
        if (connection != null) {
            try { connection.close(); } catch (Exception ignored) {}
            connection = null;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        closeConnection();
    }

    private int dp(float dp) {
        return ThemeHelper.dpToPx(dp, this);
    }

    private class SmbAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return items.size();
        }

        @Override
        public Object getItem(int position) {
            return items.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            TextView row = (TextView) convertView;
            if (row == null) {
                row = new TextView(SmbImportActivity.this);
                row.setTextSize(17);
                row.setPadding(dp(16), dp(14), dp(16), dp(14));
            }
            SmbItem item = items.get(position);
            if (item.isDirectory) {
                row.setText(AppText.get(R.string.smb_folder_prefix) + item.name);
                row.setTextColor(ThemeHelper.ACCENT_CYAN);
            } else {
                row.setText(item.name);
                row.setTextColor(Color.WHITE);
            }
            return row;
        }
    }
}
