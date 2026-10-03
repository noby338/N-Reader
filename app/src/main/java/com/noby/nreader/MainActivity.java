package com.noby.nreader;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.GridView;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.noby.nreader.model.BookCoverLoader;
import com.noby.nreader.model.BookItem;
import com.noby.nreader.model.BookStore;
import com.noby.nreader.net.QrCodeUtil;
import com.noby.nreader.net.WifiUploadServer;

import java.io.File;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity implements WifiUploadServer.Listener {
    private static final int REQUEST_IMPORT = 100;
    private static final int REQUEST_READ = 101;

    public static final int SORT_RECENT = 0;
    public static final int SORT_TITLE = 1;
    public static final int SORT_PROGRESS = 2;
    public static final int SORT_TIME = 3;
    public static final int SORT_SIZE = 4;
    private int currentSort = SORT_RECENT;
    private String currentFilter = "all";

    private final Handler mainHandler = new Handler();
    private final List<BookItem> allBooks = new ArrayList<BookItem>();

    private ScrollView bodyScroll;
    private LinearLayout bodyContainer;
    private TextView allHeader;
    private LinearLayout booksGridContainer;
    private TextView emptyView;
    private TextView summaryView;
    private BookItem currentFocusedBook = null;

    private Button btnSortMenu;
    private Button btnFilterMenu;
    private Button btnImportMenu;
    private Button btnSettingsMenu;
    private Button btnLanguageMenu;
    private Button btnMoreMenu;

    private WifiUploadServer wifiServer;
    private AlertDialog wifiDialog;
    private boolean wifiEnabled;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(AppText.wrapContext(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SharedPreferences sp = getSharedPreferences("app_global", Context.MODE_PRIVATE);
        currentSort = sp.getInt("shelf_sort", SORT_RECENT);
        currentFilter = sp.getString("shelf_filter", "all");
        buildUi();
        loadBooks();
        focusFirstBook();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(28), dp(20), dp(28), dp(16));
        root.setBackgroundColor(Color.rgb(15, 23, 42)); // Deep Slate

        // 1. Top Grouped Navigation Bar
        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = new TextView(this);
        title.setText("N-Reader");
        title.setTextSize(26);
        title.setTextColor(Color.WHITE);
        title.getPaint().setFakeBoldText(true);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        titleParams.setMargins(0, 0, dp(14), 0);
        topBar.addView(title, titleParams);

        btnSortMenu = createTopButton(getSortButtonLabel(), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showSortChoiceDialog();
            }
        });
        topBar.addView(btnSortMenu);

        btnFilterMenu = createTopButton(getFilterButtonLabel(), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showFilterChoiceDialog();
            }
        });
        topBar.addView(btnFilterMenu);

        View topSpacer = new View(this);
        topBar.addView(topSpacer, new LinearLayout.LayoutParams(0, dp(1), 1.0f));

        // Group 1: Import Books (Local, Wi-Fi, SMB)
        btnImportMenu = createTopButton(AppText.get(R.string.menu_import), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showImportChoiceDialog();
            }
        });
        topBar.addView(btnImportMenu);

        // Group 2: Reading Settings (Format-Category Settings)
        btnSettingsMenu = createTopButton(AppText.get(R.string.menu_reading_settings), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showFormatSettingsDialog();
            }
        });
        topBar.addView(btnSettingsMenu);

        // Group 3: Language
        btnLanguageMenu = createTopButton(AppText.get(R.string.menu_language), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showLanguageDialog();
            }
        });
        topBar.addView(btnLanguageMenu);

        // Group 4: More (Donation & About)
        btnMoreMenu = createTopButton(AppText.get(R.string.menu_more), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showMoreDialog();
            }
        });
        topBar.addView(btnMoreMenu);

        root.addView(topBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        bodyScroll = new ScrollView(this);
        bodyScroll.setFillViewport(true);
        bodyScroll.setVerticalScrollBarEnabled(false);

        bodyContainer = new LinearLayout(this);
        bodyContainer.setOrientation(LinearLayout.VERTICAL);
        bodyContainer.setPadding(0, dp(12), 0, dp(16));

        allHeader = new TextView(this);
        allHeader.setText(AppText.get(R.string.all_books));
        allHeader.setTextSize(16);
        allHeader.setTextColor(Color.rgb(203, 213, 225));
        allHeader.getPaint().setFakeBoldText(true);
        allHeader.setPadding(0, dp(4), 0, dp(10));
        bodyContainer.addView(allHeader);

        booksGridContainer = new LinearLayout(this);
        booksGridContainer.setOrientation(LinearLayout.VERTICAL);
        bodyContainer.addView(booksGridContainer);

        emptyView = new TextView(this);
        emptyView.setText(AppText.get(R.string.bookshelf_empty));
        emptyView.setTextColor(Color.rgb(148, 163, 184));
        emptyView.setTextSize(15);
        emptyView.setGravity(Gravity.CENTER);
        emptyView.setPadding(0, dp(60), 0, dp(60));
        emptyView.setVisibility(View.GONE);
        bodyContainer.addView(emptyView);

        bodyScroll.addView(bodyContainer);
        root.addView(bodyScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f));

        summaryView = new TextView(this);
        summaryView.setTextColor(Color.rgb(100, 116, 139));
        summaryView.setTextSize(12);
        summaryView.setGravity(Gravity.CENTER);
        summaryView.setPadding(0, dp(6), 0, 0);
        root.addView(summaryView);

        setContentView(root);
    }

    private Button createTopButton(String text, View.OnClickListener listener) {
        Button btn = new Button(this);
        btn.setText(text);
        btn.setTextSize(13);
        btn.setTextColor(Color.WHITE);
        btn.setBackground(ThemeHelper.createButtonDrawable(Color.rgb(30, 41, 59), 8, this));
        btn.setPadding(dp(16), dp(8), dp(16), dp(8));
        btn.setFocusable(true);
        btn.setOnClickListener(listener);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(40));
        params.setMargins(dp(6), 0, 0, 0);
        btn.setLayoutParams(params);
        return btn;
    }

    private String getSortButtonLabel() {
        String name;
        switch (currentSort) {
            case SORT_TITLE:
                name = AppText.get(R.string.sort_title);
                break;
            case SORT_PROGRESS:
                name = AppText.get(R.string.sort_progress);
                break;
            case SORT_TIME:
                name = AppText.get(R.string.sort_time);
                break;
            case SORT_SIZE:
                name = AppText.get(R.string.sort_size);
                break;
            case SORT_RECENT:
            default:
                name = AppText.get(R.string.sort_recent);
                break;
        }
        return AppText.get(R.string.sort_order) + ": " + name;
    }

    private String getFilterButtonLabel() {
        String name;
        if ("all".equalsIgnoreCase(currentFilter)) {
            name = AppText.get(R.string.filter_all);
        } else {
            name = currentFilter.toUpperCase(Locale.US);
        }
        return AppText.get(R.string.file_type) + ": " + name;
    }

    private void showSortChoiceDialog() {
        final int[] sortValues = {SORT_RECENT, SORT_TITLE, SORT_PROGRESS, SORT_TIME, SORT_SIZE};
        final String[] sortLabels = {
                AppText.get(R.string.sort_recent),
                AppText.get(R.string.sort_title),
                AppText.get(R.string.sort_progress),
                AppText.get(R.string.sort_time),
                AppText.get(R.string.sort_size)
        };
        int curIdx = 0;
        for (int i = 0; i < sortValues.length; i++) {
            if (sortValues[i] == currentSort) {
                curIdx = i;
                break;
            }
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(AppText.get(R.string.sort_order));
        builder.setSingleChoiceItems(sortLabels, curIdx, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                currentSort = sortValues[which];
                getSharedPreferences("app_global", Context.MODE_PRIVATE)
                        .edit().putInt("shelf_sort", currentSort).apply();
                btnSortMenu.setText(getSortButtonLabel());
                dialog.dismiss();
                loadBooks();
                focusFirstBook();
            }
        });
        builder.setNegativeButton(AppText.get(R.string.cancel), null);
        builder.show();
    }

    private void showFilterChoiceDialog() {
        final String[] filterValues = {"all", "txt", "epub", "mobi", "pdf"};
        final String[] filterLabels = {
                AppText.get(R.string.filter_all),
                "TXT",
                "EPUB",
                "MOBI / AZW",
                "PDF"
        };
        int curIdx = 0;
        for (int i = 0; i < filterValues.length; i++) {
            if (filterValues[i].equalsIgnoreCase(currentFilter)) {
                curIdx = i;
                break;
            }
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(AppText.get(R.string.file_type));
        builder.setSingleChoiceItems(filterLabels, curIdx, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                currentFilter = filterValues[which];
                getSharedPreferences("app_global", Context.MODE_PRIVATE)
                        .edit().putString("shelf_filter", currentFilter).apply();
                btnFilterMenu.setText(getFilterButtonLabel());
                dialog.dismiss();
                loadBooks();
                focusFirstBook();
            }
        });
        builder.setNegativeButton(AppText.get(R.string.cancel), null);
        builder.show();
    }

    private void loadBooks() {
        allBooks.clear();
        List<BookItem> raw = BookStore.listBooks(this);

        if ("all".equalsIgnoreCase(currentFilter)) {
            allBooks.addAll(raw);
        } else {
            for (BookItem item : raw) {
                String fmt = item.getFormat();
                if (currentFilter.equalsIgnoreCase(fmt)) {
                    allBooks.add(item);
                } else if ("mobi".equalsIgnoreCase(currentFilter) && ("azw".equalsIgnoreCase(fmt) || "azw3".equalsIgnoreCase(fmt))) {
                    allBooks.add(item);
                }
            }
        }

        Collections.sort(allBooks, new Comparator<BookItem>() {
            @Override
            public int compare(BookItem left, BookItem right) {
                switch (currentSort) {
                    case SORT_TITLE:
                        return left.getTitle().compareToIgnoreCase(right.getTitle());
                    case SORT_PROGRESS:
                        int pCmp = Float.compare(right.getReadingProgress(), left.getReadingProgress());
                        return (pCmp != 0) ? pCmp : Long.compare(right.getLastReadTime(), left.getLastReadTime());
                    case SORT_TIME:
                        long tLeft = left.getFile() != null ? left.getFile().lastModified() : 0L;
                        long tRight = right.getFile() != null ? right.getFile().lastModified() : 0L;
                        return Long.compare(tRight, tLeft);
                    case SORT_SIZE:
                        long sLeft = left.getFile() != null ? left.getFile().length() : 0L;
                        long sRight = right.getFile() != null ? right.getFile().length() : 0L;
                        return Long.compare(sRight, sLeft);
                    case SORT_RECENT:
                    default:
                        long rLeft = left.getLastReadTime();
                        long rRight = right.getLastReadTime();
                        return Long.compare(rRight, rLeft);
                }
            }
        });

        updateBookshelfGrid();

        if (allBooks.isEmpty()) {
            allHeader.setVisibility(View.GONE);
            booksGridContainer.setVisibility(View.GONE);
            emptyView.setVisibility(View.VISIBLE);
            summaryView.setText(AppText.get(R.string.bookshelf_help));
        } else {
            allHeader.setVisibility(View.VISIBLE);
            allHeader.setText(AppText.get(R.string.all_books) + " (" + allBooks.size() + ")");
            booksGridContainer.setVisibility(View.VISIBLE);
            emptyView.setVisibility(View.GONE);
            summaryView.setText(AppText.format(R.string.bookshelf_summary_remote, raw.size()));
        }
    }

    private void updateBookshelfGrid() {
        booksGridContainer.removeAllViews();
        if (allBooks.isEmpty()) {
            return;
        }

        int totalW = getResources().getDisplayMetrics().widthPixels;
        int paddingW = dp(28) * 2;
        int availableW = Math.max(dp(300), totalW - paddingW);
        int cardW = dp(140);
        int gap = dp(14);
        int numCols = Math.max(1, (availableW + gap) / (cardW + gap));

        LinearLayout currentRow = null;
        for (int i = 0; i < allBooks.size(); i++) {
            if (i % numCols == 0) {
                currentRow = new LinearLayout(this);
                currentRow.setOrientation(LinearLayout.HORIZONTAL);
                currentRow.setPadding(0, 0, 0, gap);
                booksGridContainer.addView(currentRow);
            }
            BookItem book = allBooks.get(i);
            View card = createBookCardView(book);
            currentRow.addView(card);
        }
    }

    private View createBookCardView(final BookItem book) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(8), dp(8), dp(8), dp(8));
        card.setBackground(ThemeHelper.createCardDrawable(Color.rgb(30, 41, 59), 10, this));
        card.setFocusable(true);
        card.setClickable(true);

        card.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openBookFile(book.getFile());
            }
        });

        card.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                confirmDeleteBook(book);
                return true;
            }
        });

        int cardW = dp(140);
        int cardH = dp(210);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(cardW, cardH);
        params.setMargins(0, 0, dp(14), 0);
        card.setLayoutParams(params);

        ImageView coverImg = new ImageView(this);
        coverImg.setScaleType(ImageView.ScaleType.CENTER_CROP);
        LinearLayout.LayoutParams imgParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(145));
        card.addView(coverImg, imgParams);
        BookCoverLoader.load(this, book, coverImg);

        TextView titleView = new TextView(this);
        titleView.setText(book.getTitle());
        titleView.setTextSize(13);
        titleView.setTextColor(Color.WHITE);
        titleView.setSingleLine(true);
        titleView.setPadding(dp(4), dp(6), dp(4), 0);
        card.addView(titleView);

        TextView metaView = new TextView(this);
        metaView.setText(String.format(Locale.US, "[%s] %.1f%%", book.getFormat(), book.getReadingProgress()));
        metaView.setTextSize(11);
        metaView.setTextColor(ThemeHelper.ACCENT_CYAN);
        metaView.setPadding(dp(4), dp(2), dp(4), 0);
        card.addView(metaView);

        card.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (hasFocus) {
                    currentFocusedBook = book;
                } else if (currentFocusedBook == book) {
                    currentFocusedBook = null;
                }
            }
        });

        card.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    if (keyCode == KeyEvent.KEYCODE_MENU || keyCode == KeyEvent.KEYCODE_SETTINGS) {
                        confirmDeleteBook(book);
                        return true;
                    }
                }
                return false;
            }
        });

        return card;
    }

    private void openBook(int position) {
        if (position < 0 || position >= allBooks.size()) return;
        openBookFile(allBooks.get(position).getFile());
    }

    private void openBookFile(File file) {
        Intent intent = new Intent(this, ReaderActivity.class);
        intent.putExtra("book_path", file.getAbsolutePath());
        startActivityForResult(intent, REQUEST_READ);
    }

    private void showBookOptions(final int position) {
        if (position < 0 || position >= allBooks.size()) return;
        final BookItem book = allBooks.get(position);
        confirmDeleteBook(book);
    }

    private void confirmDeleteBook(final BookItem book) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(AppText.get(R.string.delete_book));
        builder.setMessage(AppText.format(R.string.delete_book_message, book.getTitle()));
        builder.setPositiveButton(AppText.get(R.string.delete), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                BookStore.deleteBook(MainActivity.this, book.getFile());
                loadBooks();
            }
        });
        builder.setNegativeButton(AppText.get(R.string.cancel), null);
        builder.show();
    }

    private void showImportChoiceDialog() {
        String[] options = {
                "📁 " + AppText.get(R.string.import_local),
                "🌐 " + AppText.get(R.string.import_wifi),
                "🖥️ " + AppText.get(R.string.import_smb)
        };
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(AppText.get(R.string.import_choice_title));
        builder.setItems(options, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                switch (which) {
                    case 0:
                        startActivityForResult(new Intent(MainActivity.this, LocalImportActivity.class), REQUEST_IMPORT);
                        break;
                    case 1:
                        startWifiTransfer();
                        break;
                    case 2:
                        startActivityForResult(new Intent(MainActivity.this, SmbImportActivity.class), REQUEST_IMPORT);
                        break;
                }
            }
        });
        builder.setNegativeButton(AppText.get(R.string.close), null);
        builder.show();
    }

    private void showFormatSettingsDialog() {
        final String[] targetFormats = {"all", "txt", "epub", "mobi", "pdf"};
        final String[] targetLabels = {
                AppText.get(R.string.apply_to_all),
                "TXT",
                "EPUB",
                "MOBI",
                "PDF"
        };
        final String[] currentFormat = new String[]{"all"};

        final ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);

        final LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(16), dp(24), dp(16));
        scroll.addView(root);

        TextView tvFormatHeader = new TextView(this);
        tvFormatHeader.setText(AppText.get(R.string.format_select));
        tvFormatHeader.setTextSize(15);
        tvFormatHeader.setTextColor(ThemeHelper.ACCENT_CYAN);
        tvFormatHeader.getPaint().setFakeBoldText(true);
        root.addView(tvFormatHeader);

        final LinearLayout formatRow = new LinearLayout(this);
        formatRow.setOrientation(LinearLayout.HORIZONTAL);
        formatRow.setPadding(0, dp(6), 0, dp(14));

        final LinearLayout contentRows = new LinearLayout(this);
        contentRows.setOrientation(LinearLayout.VERTICAL);

        final List<Button> formatButtons = new ArrayList<Button>();
        for (int i = 0; i < targetFormats.length; i++) {
            final String f = targetFormats[i];
            Button btn = new Button(this);
            btn.setText(targetLabels[i]);
            btn.setTextSize(12);
            btn.setTextColor(Color.WHITE);
            btn.setBackground(ThemeHelper.createButtonDrawable(
                    f.equals(currentFormat[0]) ? ThemeHelper.SELECTED_COLOR : Color.rgb(30, 41, 59), 6, this));
            btn.setPadding(dp(12), dp(6), dp(12), dp(6));
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(36));
            p.setMargins(0, 0, dp(8), 0);
            btn.setLayoutParams(p);
            btn.setFocusable(true);
            btn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    currentFormat[0] = f;
                    for (int j = 0; j < formatButtons.size(); j++) {
                        formatButtons.get(j).setBackground(ThemeHelper.createButtonDrawable(
                                targetFormats[j].equals(currentFormat[0]) ? ThemeHelper.SELECTED_COLOR : Color.rgb(30, 41, 59), 6, MainActivity.this));
                    }
                    renderFormatOptions(scroll, contentRows, currentFormat[0], null);
                }
            });
            formatButtons.add(btn);
            formatRow.addView(btn);
        }
        root.addView(formatRow);
        root.addView(contentRows);

        renderFormatOptions(scroll, contentRows, currentFormat[0], null);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(AppText.get(R.string.layout_settings_title));
        builder.setView(scroll);
        builder.setPositiveButton(AppText.get(R.string.close), null);
        builder.show();
    }

    private void renderFormatOptions(final ScrollView scroll, final LinearLayout container, final String format, final String focusTag) {
        final int currentScrollY = scroll != null ? scroll.getScrollY() : 0;
        container.removeAllViews();
        final boolean isGlobal = "all".equals(format);
        final View[] targetFocusView = new View[1];

        if (!"pdf".equals(format)) {
            int curFontSize = FormatPrefs.getFontSize(this, format);
            int rawFontSize = FormatPrefs.getRawFontSize(this, format);
            boolean usingDefaultFont = !isGlobal && (rawFontSize == FormatPrefs.USE_DEFAULT_INT);

            addSectionHeader(container, AppText.get(R.string.font_size));
            final LinearLayout fontRow = new LinearLayout(this);
            fontRow.setOrientation(LinearLayout.HORIZONTAL);
            fontRow.setPadding(0, dp(4), 0, dp(14));

            if (!isGlobal) {
                Button btnDef = createOptionButton(AppText.get(R.string.use_default), "font_def", focusTag, usingDefaultFont, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        FormatPrefs.setFontSize(MainActivity.this, format, FormatPrefs.USE_DEFAULT_INT);
                        updateRowSelection(fontRow, "font_def");
                    }
                }, targetFocusView);
                fontRow.addView(btnDef);
            }

            int[] fontPresets = {14, 16, 18, 20, 24, 28, 32, 36, 40};
            for (final int sz : fontPresets) {
                boolean isSel = !usingDefaultFont && (curFontSize == sz);
                final String tag = "font_" + sz;
                Button btnP = createOptionButton(String.valueOf(sz), tag, focusTag, isSel, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        FormatPrefs.setFontSize(MainActivity.this, format, sz);
                        updateRowSelection(fontRow, tag);
                    }
                }, targetFocusView);
                fontRow.addView(btnP);
            }
            container.addView(fontRow);

            int curSpacing = FormatPrefs.getLineSpacing(this, format);
            int rawSpacing = FormatPrefs.getRawLineSpacing(this, format);
            boolean usingDefaultSpacing = !isGlobal && (rawSpacing == FormatPrefs.USE_DEFAULT_INT);

            addSectionHeader(container, AppText.get(R.string.line_spacing));
            final LinearLayout spacingRow = new LinearLayout(this);
            spacingRow.setOrientation(LinearLayout.HORIZONTAL);
            spacingRow.setPadding(0, dp(4), 0, dp(14));

            if (!isGlobal) {
                Button btnDef = createOptionButton(AppText.get(R.string.use_default), "line_def", focusTag, usingDefaultSpacing, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        FormatPrefs.setLineSpacing(MainActivity.this, format, FormatPrefs.USE_DEFAULT_INT);
                        updateRowSelection(spacingRow, "line_def");
                    }
                }, targetFocusView);
                spacingRow.addView(btnDef);
            }

            int[] spacingPresets = {0, 2, 4, 6, 8, 12, 16, 20};
            for (final int sp : spacingPresets) {
                boolean isSel = !usingDefaultSpacing && (curSpacing == sp);
                final String tag = "line_" + sp;
                Button btnSp = createOptionButton(sp + "dp", tag, focusTag, isSel, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        FormatPrefs.setLineSpacing(MainActivity.this, format, sp);
                        updateRowSelection(spacingRow, tag);
                    }
                }, targetFocusView);
                spacingRow.addView(btnSp);
            }
            container.addView(spacingRow);

            int curFontFamily = FormatPrefs.getFontFamily(this, format);
            int rawFontFamily = FormatPrefs.getRawFontFamily(this, format);
            boolean usingDefaultFamily = !isGlobal && (rawFontFamily == FormatPrefs.USE_DEFAULT_INT);

            addSectionHeader(container, AppText.get(R.string.font_family));
            final LinearLayout fontFamilyRow = new LinearLayout(this);
            fontFamilyRow.setOrientation(LinearLayout.HORIZONTAL);
            fontFamilyRow.setPadding(0, dp(4), 0, dp(14));

            if (!isGlobal) {
                Button btnDef = createOptionButton(AppText.get(R.string.use_default), "family_def", focusTag, usingDefaultFamily, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        FormatPrefs.setFontFamily(MainActivity.this, format, FormatPrefs.USE_DEFAULT_INT);
                        updateRowSelection(fontFamilyRow, "family_def");
                    }
                }, targetFocusView);
                fontFamilyRow.addView(btnDef);
            }

            for (int i = 0; i < FormatPrefs.FONT_FAMILY_COUNT; i++) {
                final int fId = i;
                boolean isSel = !usingDefaultFamily && (curFontFamily == fId);
                final String tag = "family_" + fId;
                Button btnF = createOptionButton(FormatPrefs.getFontFamilyName(fId), tag, focusTag, isSel, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        FormatPrefs.setFontFamily(MainActivity.this, format, fId);
                        updateRowSelection(fontFamilyRow, tag);
                    }
                }, targetFocusView);
                fontFamilyRow.addView(btnF);
            }
            container.addView(fontFamilyRow);
        }

        String curMode = FormatPrefs.getReadingMode(this, format);
        String rawMode = FormatPrefs.getRawReadingMode(this, format);
        boolean usingDefaultMode = !isGlobal && FormatPrefs.USE_DEFAULT_STR.equals(rawMode);

        addSectionHeader(container, AppText.get(R.string.reading_mode));
        final LinearLayout modeRow = new LinearLayout(this);
        modeRow.setOrientation(LinearLayout.HORIZONTAL);
        modeRow.setPadding(0, dp(4), 0, dp(14));

        if (!isGlobal) {
            Button btnDef = createOptionButton(AppText.get(R.string.use_default), "mode_def", focusTag, usingDefaultMode, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    FormatPrefs.setReadingMode(MainActivity.this, format, FormatPrefs.USE_DEFAULT_STR);
                    updateRowSelection(modeRow, "mode_def");
                }
            }, targetFocusView);
            modeRow.addView(btnDef);
        }

        boolean isScrollSel = !usingDefaultMode && FormatPrefs.MODE_SCROLL.equals(curMode);
        Button btnScroll = createOptionButton(AppText.get(R.string.mode_single_scroll), "mode_scroll", focusTag, isScrollSel, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FormatPrefs.setReadingMode(MainActivity.this, format, FormatPrefs.MODE_SCROLL);
                updateRowSelection(modeRow, "mode_scroll");
            }
        }, targetFocusView);
        modeRow.addView(btnScroll);

        boolean isDualSel = !usingDefaultMode && FormatPrefs.MODE_DUAL_PAGE.equals(curMode);
        Button btnDual = createOptionButton(AppText.get(R.string.mode_dual_page), "mode_dual", focusTag, isDualSel, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FormatPrefs.setReadingMode(MainActivity.this, format, FormatPrefs.MODE_DUAL_PAGE);
                updateRowSelection(modeRow, "mode_dual");
            }
        }, targetFocusView);
        modeRow.addView(btnDual);
        container.addView(modeRow);

        int curThemeId = FormatPrefs.getThemeId(this, format);
        int rawThemeId = FormatPrefs.getRawThemeId(this, format);
        boolean usingDefaultTheme = !isGlobal && (rawThemeId == FormatPrefs.USE_DEFAULT_INT);

        addSectionHeader(container, AppText.get(R.string.theme_select));
        final LinearLayout themeRow1 = new LinearLayout(this);
        themeRow1.setOrientation(LinearLayout.HORIZONTAL);
        themeRow1.setPadding(0, dp(4), 0, dp(8));

        final LinearLayout themeRow2 = new LinearLayout(this);
        themeRow2.setOrientation(LinearLayout.HORIZONTAL);
        themeRow2.setPadding(0, 0, 0, dp(14));

        if (!isGlobal) {
            Button btnDef = createOptionButton(AppText.get(R.string.use_default), "theme_def", focusTag, usingDefaultTheme, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    FormatPrefs.setThemeId(MainActivity.this, format, FormatPrefs.USE_DEFAULT_INT);
                    updateTwoRowSelection(themeRow1, themeRow2, "theme_def");
                }
            }, targetFocusView);
            themeRow1.addView(btnDef);
        }

        for (int i = 0; i < ThemeHelper.THEME_COUNT; i++) {
            final int thId = i;
            boolean isSel = !usingDefaultTheme && (curThemeId == thId);
            final String tag = "theme_" + thId;
            Button btnTh = createOptionButton(ThemeHelper.getThemeName(i), tag, focusTag, isSel, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    FormatPrefs.setThemeId(MainActivity.this, format, thId);
                    updateTwoRowSelection(themeRow1, themeRow2, tag);
                }
            }, targetFocusView);
            if (themeRow1.getChildCount() < 5) {
                themeRow1.addView(btnTh);
            } else {
                themeRow2.addView(btnTh);
            }
        }
        container.addView(themeRow1);
        if (themeRow2.getChildCount() > 0) {
            container.addView(themeRow2);
        }

        if (isGlobal) {
            addSectionHeader(container, AppText.get(R.string.clock_setting));
            final LinearLayout clockRow = new LinearLayout(this);
            clockRow.setOrientation(LinearLayout.HORIZONTAL);
            clockRow.setPadding(0, dp(4), 0, dp(14));

            boolean showClock = FormatPrefs.getShowTime(this);
            Button btnShow = createOptionButton(AppText.get(R.string.show_clock), "clock_show", focusTag, showClock, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    FormatPrefs.setShowTime(MainActivity.this, true);
                    updateRowSelection(clockRow, "clock_show");
                }
            }, targetFocusView);
            clockRow.addView(btnShow);

            Button btnHide = createOptionButton(AppText.get(R.string.hide_clock), "clock_hide", focusTag, !showClock, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    FormatPrefs.setShowTime(MainActivity.this, false);
                    updateRowSelection(clockRow, "clock_hide");
                }
            }, targetFocusView);
            clockRow.addView(btnHide);
            container.addView(clockRow);

            addSectionHeader(container, AppText.get(R.string.status_bar_setting));
            final LinearLayout barRow = new LinearLayout(this);
            barRow.setOrientation(LinearLayout.HORIZONTAL);
            barRow.setPadding(0, dp(4), 0, dp(14));

            boolean showBar = FormatPrefs.getShowStatusBar(this);
            Button btnShowBar = createOptionButton(AppText.get(R.string.show_status_bar), "bar_show", focusTag, showBar, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    FormatPrefs.setShowStatusBar(MainActivity.this, true);
                    updateRowSelection(barRow, "bar_show");
                }
            }, targetFocusView);
            barRow.addView(btnShowBar);

            Button btnHideBar = createOptionButton(AppText.get(R.string.hide_status_bar), "bar_hide", focusTag, !showBar, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    FormatPrefs.setShowStatusBar(MainActivity.this, false);
                    updateRowSelection(barRow, "bar_hide");
                }
            }, targetFocusView);
            barRow.addView(btnHideBar);
            container.addView(barRow);
        }

        if (scroll != null) {
            scroll.post(new Runnable() {
                @Override
                public void run() {
                    if (currentScrollY > 0) {
                        scroll.scrollTo(0, currentScrollY);
                    }
                    if (targetFocusView[0] != null) {
                        targetFocusView[0].requestFocus();
                    }
                }
            });
        }
    }

    private void updateRowSelection(LinearLayout row, String selectedTag) {
        if (row == null) return;
        for (int i = 0; i < row.getChildCount(); i++) {
            View v = row.getChildAt(i);
            if (v instanceof Button) {
                boolean isSel = selectedTag != null && selectedTag.equals(v.getTag());
                int bg = isSel ? ThemeHelper.SELECTED_COLOR : Color.rgb(30, 41, 59);
                v.setBackground(ThemeHelper.createButtonDrawable(bg, 6, this));
            }
        }
    }

    private void updateTwoRowSelection(LinearLayout row1, LinearLayout row2, String selectedTag) {
        updateRowSelection(row1, selectedTag);
        updateRowSelection(row2, selectedTag);
    }

    private void addSectionHeader(LinearLayout container, String title) {
        TextView header = new TextView(this);
        header.setText(title);
        header.setTextSize(14);
        header.setTextColor(Color.WHITE);
        header.getPaint().setFakeBoldText(true);
        header.setPadding(0, dp(4), 0, dp(4));
        container.addView(header);
    }

    private Button createOptionButton(String text, String tag, String focusTag, boolean isSelected, View.OnClickListener listener, View[] targetFocusView) {
        Button btn = new Button(this);
        btn.setText(text);
        btn.setTextSize(12);
        btn.setTextColor(Color.WHITE);
        int bg = isSelected ? ThemeHelper.SELECTED_COLOR : Color.rgb(30, 41, 59);
        btn.setBackground(ThemeHelper.createButtonDrawable(bg, 6, this));
        btn.setPadding(dp(12), dp(6), dp(12), dp(6));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(36));
        params.setMargins(0, 0, dp(8), 0);
        btn.setLayoutParams(params);
        btn.setFocusable(true);
        btn.setTag(tag);
        if (tag != null && tag.equals(focusTag) && targetFocusView != null) {
            targetFocusView[0] = btn;
        }
        btn.setOnClickListener(listener);
        return btn;
    }

    private void showLanguageDialog() {
        final String[] langs = {"English", "简体中文"};
        int current = AppText.isChinese() ? 1 : 0;

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(AppText.get(R.string.select_language_title));
        builder.setSingleChoiceItems(langs, current, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String target = (which == 1) ? "zh" : "en";
                AppText.setLanguage(target);
                dialog.dismiss();
                recreate();
            }
        });
        builder.setNegativeButton(AppText.get(R.string.cancel), null);
        builder.show();
    }

    private void showMoreDialog() {
        String[] options = {
                AppText.get(R.string.more_donate),
                AppText.get(R.string.more_about)
        };
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(AppText.get(R.string.menu_more).replace(" ▾", ""));
        builder.setItems(options, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (which == 0) {
                    startActivity(new Intent(MainActivity.this, DonationActivity.class));
                } else {
                    showAboutDialog();
                }
            }
        });
        builder.setNegativeButton(AppText.get(R.string.close), null);
        builder.show();
    }

    private void startWifiTransfer() {
        stopWifiTransfer();
        wifiServer = new WifiUploadServer(this, this);
        wifiServer.start(8080);
    }

    private void stopWifiTransfer() {
        if (wifiServer != null) {
            wifiServer.stop();
            wifiServer = null;
        }
    }

    @Override
    public void onServerStarted(final int port) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                showWifiDialog(port);
            }
        });
    }

    @Override
    public void onBookImported(final String name) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                loadBooks();
                Toast.makeText(MainActivity.this,
                        AppText.format(R.string.book_auto_imported, name), Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onServerError(final String message) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(MainActivity.this, "Wi-Fi Server Error: " + message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void showWifiDialog(int port) {
        String ip = getLocalIpAddress();
        final String url = "http://" + ip + ":" + port + "/";

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER);
        layout.setPadding(dp(24), dp(20), dp(24), dp(20));

        TextView urlView = new TextView(this);
        urlView.setText(url);
        urlView.setTextSize(20);
        urlView.setTextColor(ThemeHelper.ACCENT_CYAN);
        urlView.getPaint().setFakeBoldText(true);
        layout.addView(urlView);

        ImageView qrView = new ImageView(this);
        try {
            Bitmap qr = QrCodeUtil.create(url, dp(220));
            qrView.setImageBitmap(qr);
        } catch (Exception ignored) {
        }
        LinearLayout.LayoutParams qrParams = new LinearLayout.LayoutParams(dp(220), dp(220));
        qrParams.setMargins(0, dp(14), 0, dp(14));
        layout.addView(qrView, qrParams);

        TextView tipView = new TextView(this);
        tipView.setText(AppText.get(R.string.qr_scan_tip));
        tipView.setTextColor(Color.LTGRAY);
        tipView.setTextSize(13);
        tipView.setGravity(Gravity.CENTER);
        layout.addView(tipView);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(AppText.get(R.string.wifi_upload));
        builder.setView(layout);
        builder.setPositiveButton(AppText.get(R.string.close), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                stopWifiTransfer();
            }
        });
        builder.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialog) {
                stopWifiTransfer();
            }
        });
        wifiDialog = builder.show();
    }

    private String getLocalIpAddress() {
        try {
            String ethIp = null;
            String wlanIp = null;
            String fallbackIp = null;

            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            if (interfaces != null) {
                while (interfaces.hasMoreElements()) {
                    NetworkInterface iface = interfaces.nextElement();
                    if (iface.isLoopback() || !iface.isUp()) {
                        continue;
                    }
                    String ifaceName = iface.getName().toLowerCase(Locale.US);
                    Enumeration<InetAddress> addresses = iface.getInetAddresses();
                    while (addresses.hasMoreElements()) {
                        InetAddress addr = addresses.nextElement();
                        if (addr instanceof Inet4Address && !addr.isLoopbackAddress() && !addr.isLinkLocalAddress()) {
                            String host = addr.getHostAddress();
                            if (ifaceName.startsWith("eth")) {
                                ethIp = host;
                            } else if (ifaceName.startsWith("wlan")) {
                                wlanIp = host;
                            } else if (fallbackIp == null) {
                                fallbackIp = host;
                            }
                        }
                    }
                }
            }
            if (ethIp != null) return ethIp;
            if (wlanIp != null) return wlanIp;
            if (fallbackIp != null) return fallbackIp;
        } catch (Exception ignored) {
        }

        try {
            WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wm != null && wm.getConnectionInfo() != null) {
                int ipInt = wm.getConnectionInfo().getIpAddress();
                if (ipInt != 0) {
                    return String.format(Locale.US, "%d.%d.%d.%d",
                            (ipInt & 0xff), (ipInt >> 8 & 0xff), (ipInt >> 16 & 0xff), (ipInt >> 24 & 0xff));
                }
            }
        } catch (Exception ignored) {
        }
        return "127.0.0.1";
    }

    private void showAboutDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(AppText.get(R.string.about_title));
        builder.setMessage(AppText.get(R.string.about_version) + "\n\n"
                + AppText.get(R.string.about_github) + ": " + AppText.get(R.string.about_github_url) + "\n"
                + AppText.get(R.string.about_email) + ": " + AppText.get(R.string.about_email_addr) + "\n\n"
                + AppText.get(R.string.about_disclaimer));
        builder.setPositiveButton(AppText.get(R.string.close), null);
        builder.show();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MENU || keyCode == KeyEvent.KEYCODE_SETTINGS) {
            if (currentFocusedBook != null) {
                confirmDeleteBook(currentFocusedBook);
                return true;
            } else {
                showImportChoiceDialog();
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        loadBooks();
        focusFirstBook();
    }

    private void focusFirstBook() {
        if (booksGridContainer != null) {
            booksGridContainer.post(new Runnable() {
                @Override
                public void run() {
                    if (booksGridContainer.getChildCount() > 0) {
                        View row = booksGridContainer.getChildAt(0);
                        if (row instanceof ViewGroup && ((ViewGroup) row).getChildCount() > 0) {
                            ((ViewGroup) row).getChildAt(0).requestFocus();
                            return;
                        }
                    }
                    if (btnSortMenu != null) {
                        btnSortMenu.requestFocus();
                    } else if (btnImportMenu != null) {
                        btnImportMenu.requestFocus();
                    }
                }
            });
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (wifiDialog != null && wifiDialog.isShowing()) {
            wifiDialog.dismiss();
        }
        stopWifiTransfer();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopWifiTransfer();
    }

    private int dp(float dp) {
        return ThemeHelper.dpToPx(dp, this);
    }
}
