package com.noby.nreader;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.noby.nreader.model.BookStore;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LocalImportActivity extends Activity {
    private static final int STORAGE_PERMISSION_REQUEST = 101;

    private static class Entry {
        final File file;
        final String label;
        final boolean isFolder;
        final boolean isParent;

        Entry(File file, String label, boolean isFolder, boolean isParent) {
            this.file = file;
            this.label = label;
            this.isFolder = isFolder;
            this.isParent = isParent;
        }
    }

    private final List<Entry> entries = new ArrayList<Entry>();
    private final LinkedHashMap<String, File> selectedFiles = new LinkedHashMap<String, File>();
    private final Set<String> storageRoots = new HashSet<String>();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private ListView listView;
    private TextView pathView;
    private TextView statusView;
    private EntryAdapter adapter;
    private File currentDirectory;
    private boolean importing;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(AppText.wrapContext(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();

        if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, STORAGE_PERMISSION_REQUEST);
        } else {
            showStorageRoots();
        }
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(32), dp(24), dp(32), dp(24));
        root.setBackgroundColor(Color.rgb(15, 23, 42)); // Deep Slate

        TextView title = new TextView(this);
        title.setText(AppText.get(R.string.local_import_title));
        title.setTextSize(26);
        title.setTextColor(Color.WHITE);
        title.getPaint().setFakeBoldText(true);
        root.addView(title);

        pathView = new TextView(this);
        pathView.setText(AppText.get(R.string.reading_device_storage));
        pathView.setTextSize(15);
        pathView.setTextColor(Color.rgb(148, 163, 184));
        pathView.setPadding(0, dp(6), 0, dp(4));
        root.addView(pathView);

        TextView help = new TextView(this);
        help.setText(AppText.get(R.string.local_import_help));
        help.setTextSize(14);
        help.setTextColor(ThemeHelper.ACCENT_CYAN);
        root.addView(help);

        LinearLayout actionRow = new LinearLayout(this);
        actionRow.setOrientation(LinearLayout.HORIZONTAL);
        actionRow.setGravity(Gravity.CENTER_VERTICAL);

        Button btnImportFolder = new Button(this);
        btnImportFolder.setText(AppText.get(R.string.folder_import_all));
        btnImportFolder.setTextSize(13);
        btnImportFolder.setTextColor(Color.WHITE);
        btnImportFolder.setBackground(ThemeHelper.createButtonDrawable(Color.rgb(30, 41, 59), 8, this));
        btnImportFolder.setPadding(dp(16), dp(8), dp(16), dp(8));
        btnImportFolder.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (currentDirectory != null) {
                    confirmImportFolder(currentDirectory);
                } else {
                    Toast.makeText(LocalImportActivity.this, AppText.get(R.string.please_enter_folder), Toast.LENGTH_SHORT).show();
                }
            }
        });
        actionRow.addView(btnImportFolder);

        Button btnImportBatch = new Button(this);
        btnImportBatch.setText(AppText.isChinese() ? "导入已勾选项" : "Import Selected");
        btnImportBatch.setTextSize(13);
        btnImportBatch.setTextColor(Color.WHITE);
        btnImportBatch.setBackground(ThemeHelper.createButtonDrawable(Color.rgb(30, 41, 59), 8, this));
        btnImportBatch.setPadding(dp(16), dp(8), dp(16), dp(8));
        LinearLayout.LayoutParams batchParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        batchParams.setMargins(dp(12), 0, 0, 0);
        btnImportBatch.setLayoutParams(batchParams);
        btnImportBatch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                importBatch();
            }
        });
        actionRow.addView(btnImportBatch);

        root.addView(actionRow);

        listView = new ListView(this);
        listView.setDividerHeight(dp(4));
        listView.setSelector(ThemeHelper.createButtonDrawable(Color.argb(60, 56, 189, 248), 6, this));
        listView.setDrawSelectorOnTop(true);
        adapter = new EntryAdapter();
        listView.setAdapter(adapter);

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                activateEntry(position);
            }
        });

        listView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                toggleSelection(position);
                return true;
            }
        });

        LinearLayout.LayoutParams listParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f);
        listParams.setMargins(0, dp(12), 0, dp(12));
        root.addView(listView, listParams);

        statusView = new TextView(this);
        statusView.setTextSize(14);
        statusView.setTextColor(Color.rgb(203, 213, 225));
        root.addView(statusView);

        setContentView(root);
    }

    private void showStorageRoots() {
        currentDirectory = null;
        entries.clear();
        storageRoots.clear();

        File internal = Environment.getExternalStorageDirectory();
        if (internal != null && internal.exists()) {
            storageRoots.add(internal.getAbsolutePath());
            entries.add(new Entry(internal, "[Storage] " + internal.getName(), true, false));
        }

        File[] extDirs = getExternalFilesDirs(null);
        if (extDirs != null) {
            for (File ext : extDirs) {
                if (ext == null) continue;
                File probe = ext;
                while (probe != null && !"/storage".equals(probe.getParent()) && !"/mnt".equals(probe.getParent())) {
                    probe = probe.getParentFile();
                }
                if (probe != null && !storageRoots.contains(probe.getAbsolutePath())) {
                    storageRoots.add(probe.getAbsolutePath());
                    entries.add(new Entry(probe, "[USB/External] " + probe.getName(), true, false));
                }
            }
        }

        pathView.setText(AppText.get(R.string.device_storage));
        adapter.notifyDataSetChanged();
        updateStatus();
    }

    private void openDirectory(File dir) {
        if (dir == null || !dir.isDirectory()) {
            return;
        }
        currentDirectory = dir;
        entries.clear();

        if (!storageRoots.contains(dir.getAbsolutePath()) && dir.getParentFile() != null) {
            entries.add(new Entry(dir.getParentFile(), AppText.get(R.string.parent_folder), true, true));
        }

        File[] files = dir.listFiles();
        List<Entry> folders = new ArrayList<Entry>();
        List<Entry> books = new ArrayList<Entry>();

        if (files != null) {
            for (File f : files) {
                if (f.isDirectory() && !f.isHidden()) {
                    folders.add(new Entry(f, "[Folder] " + f.getName(), true, false));
                } else if (f.isFile() && BookStore.isSupported(f)) {
                    books.add(new Entry(f, f.getName(), false, false));
                }
            }
        }

        Collections.sort(folders, new Comparator<Entry>() {
            @Override
            public int compare(Entry a, Entry b) {
                return a.label.compareToIgnoreCase(b.label);
            }
        });
        Collections.sort(books, new Comparator<Entry>() {
            @Override
            public int compare(Entry a, Entry b) {
                return a.label.compareToIgnoreCase(b.label);
            }
        });

        entries.addAll(folders);
        entries.addAll(books);
        pathView.setText(dir.getAbsolutePath());
        adapter.notifyDataSetChanged();
        listView.setSelection(0);
        updateStatus();
    }

    private void activateEntry(int position) {
        if (position < 0 || position >= entries.size() || importing) {
            return;
        }
        Entry entry = entries.get(position);
        if (entry.isFolder) {
            if (entry.isParent) {
                if (storageRoots.contains(currentDirectory.getAbsolutePath())) {
                    showStorageRoots();
                } else {
                    openDirectory(entry.file);
                }
            } else {
                openDirectory(entry.file);
            }
        } else {
            // Single book import
            importSingleBook(entry.file);
        }
    }

    private void toggleSelection(int position) {
        if (position < 0 || position >= entries.size() || importing) {
            return;
        }
        Entry entry = entries.get(position);
        if (entry.isParent) return;

        String path = entry.file.getAbsolutePath();
        if (selectedFiles.containsKey(path)) {
            selectedFiles.remove(path);
        } else {
            selectedFiles.put(path, entry.file);
        }
        adapter.notifyDataSetChanged();
        updateStatus();
    }

    private void importSingleBook(final File file) {
        importing = true;
        statusView.setText(AppText.format(R.string.importing_books, 1));
        executor.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    BookStore.importBook(LocalImportActivity.this, file);
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(LocalImportActivity.this,
                                    AppText.format(R.string.book_auto_imported, file.getName()),
                                    Toast.LENGTH_SHORT).show();
                            setResult(RESULT_OK);
                            finish();
                        }
                    });
                } catch (final Exception e) {
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            importing = false;
                            Toast.makeText(LocalImportActivity.this,
                                    "Import failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                            updateStatus();
                        }
                    });
                }
            }
        });
    }

    private void importBatch() {
        if (selectedFiles.isEmpty() || importing) {
            return;
        }
        importing = true;
        final List<File> targets = new ArrayList<File>(selectedFiles.values());
        statusView.setText(AppText.format(R.string.importing_books, targets.size()));

        executor.submit(new Runnable() {
            @Override
            public void run() {
                int success = 0;
                for (File f : targets) {
                    try {
                        if (f.isDirectory()) {
                            success += scanAndImportFolder(f);
                        } else if (BookStore.isSupported(f)) {
                            BookStore.importBook(LocalImportActivity.this, f);
                            success++;
                        }
                    } catch (Exception ignored) {
                    }
                }
                final int finalSuccess = success;
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(LocalImportActivity.this,
                                AppText.format(R.string.books_imported, finalSuccess),
                                Toast.LENGTH_SHORT).show();
                        setResult(RESULT_OK);
                        finish();
                    }
                });
            }
        });
    }

    private int scanAndImportFolder(File folder) {
        int count = 0;
        File[] files = folder.listFiles();
        if (files == null) return 0;
        for (File f : files) {
            if (f.isDirectory() && !f.isHidden()) {
                count += scanAndImportFolder(f);
            } else if (f.isFile() && BookStore.isSupported(f)) {
                try {
                    BookStore.importBook(this, f);
                    count++;
                } catch (Exception ignored) {}
            }
        }
        return count;
    }

    private void confirmImportFolder(final File folder) {
        if (folder == null || importing) return;
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(AppText.get(R.string.folder_import_all));
        builder.setMessage(folder.getName() + "\n\n" + (AppText.isChinese()
                ? "确定扫描并导入该文件夹中的全部书籍？"
                : "Scan and import all books from this folder?"));
        builder.setPositiveButton(AppText.get(R.string.confirm_batch_delete).replace("删除", "导入").replace("Delete", "Import"), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                importFolderAll(folder);
            }
        });
        builder.setNegativeButton(AppText.get(R.string.cancel), null);
        builder.show();
    }

    private void importFolderAll(final File folder) {
        if (folder == null || importing) return;
        importing = true;
        statusView.setText(AppText.isChinese() ? "正在扫描并导入全部书籍..." : "Scanning and importing books...");
        executor.submit(new Runnable() {
            @Override
            public void run() {
                final int count = scanAndImportFolder(folder);
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        importing = false;
                        Toast.makeText(LocalImportActivity.this,
                                AppText.format(R.string.books_imported, count), Toast.LENGTH_SHORT).show();
                        setResult(RESULT_OK);
                        finish();
                    }
                });
            }
        });
    }

    private void updateStatus() {
        if (selectedFiles.isEmpty()) {
            statusView.setText(AppText.isChinese()
                    ? "已选: 0 | 按【菜单键】或长按可多选文件/文件夹"
                    : "Selected: 0 | Press MENU or long-click to multi-select");
        } else {
            statusView.setText(AppText.isChinese()
                    ? ("已选择 " + selectedFiles.size() + " 项，点击【导入已选】开始导入")
                    : ("Selected " + selectedFiles.size() + " items, click Import to proceed"));
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (currentDirectory != null) {
                if (storageRoots.contains(currentDirectory.getAbsolutePath())) {
                    showStorageRoots();
                } else {
                    openDirectory(currentDirectory.getParentFile());
                }
                return true;
            }
        } else if (keyCode == KeyEvent.KEYCODE_MENU || keyCode == KeyEvent.KEYCODE_SETTINGS) {
            int pos = listView.getSelectedItemPosition();
            if (pos >= 0) {
                toggleSelection(pos);
                return true;
            }
        } else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
            int pos = listView.getSelectedItemPosition();
            if (pos >= 0 && pos < entries.size()) {
                Entry entry = entries.get(pos);
                if (entry.isFolder && !entry.isParent) {
                    confirmImportFolder(entry.file);
                    return true;
                }
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }

    private int dp(float dp) {
        return ThemeHelper.dpToPx(dp, this);
    }

    private class EntryAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return entries.size();
        }

        @Override
        public Object getItem(int position) {
            return entries.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            TextView row = (TextView) convertView;
            if (row == null) {
                row = new TextView(LocalImportActivity.this);
                row.setTextSize(17);
                row.setPadding(dp(16), dp(14), dp(16), dp(14));
            }

            Entry entry = entries.get(position);
            boolean isSelected = selectedFiles.containsKey(entry.file.getAbsolutePath());

            String text = entry.label;
            if (isSelected) {
                text = "[✓] " + text;
                row.setTextColor(Color.rgb(255, 190, 75)); // Amber for selected
            } else if (entry.isFolder) {
                row.setTextColor(Color.rgb(56, 189, 248)); // Cyan for folders
            } else {
                row.setTextColor(Color.WHITE); // White for books
            }
            row.setText(text);
            return row;
        }
    }
}
