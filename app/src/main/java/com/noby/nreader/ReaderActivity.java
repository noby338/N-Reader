package com.noby.nreader;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.text.Layout;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.style.AbsoluteSizeSpan;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.noby.nreader.model.BookStore;
import com.noby.nreader.parser.BookDocument;
import com.noby.nreader.parser.BookParser;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ReaderActivity extends Activity {
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private File bookFile;
    private String formatExt;
    private BookDocument document;

    private LinearLayout rootContainer;
    private FrameLayout readingViewport;
    private ScrollView scrollView;
    private LinearLayout contentContainer;

    private TextView singleTextView;

    private LinearLayout dualPageContainer;
    private BookPageView leftPageView;
    private BookPageView rightPageView;
    private StaticLayout masterLayout;

    private static class PageRange {
        final int start;
        final int end;
        PageRange(int s, int e) {
            this.start = s;
            this.end = e;
        }
    }
    private final List<PageRange> dualPages = new ArrayList<PageRange>();

    private ImageView pdfImageView;
    private Bitmap currentPdfBitmap;
    private LinearLayout dualPdfContainer;
    private ImageView leftPdfImageView;
    private ImageView rightPdfImageView;
    private Bitmap leftPdfBitmap;
    private Bitmap rightPdfBitmap;

    private LinearLayout topToolbar;
    private LinearLayout bottomDock;
    private HorizontalScrollView secondaryScroll;
    private LinearLayout secondaryToolbar;
    private LinearLayout bottomToolbar;
    private String activeSubMenu = null;
    private ProgressBar loadingBar;

    private LinearLayout bottomStatusBar;
    private TextView statusChapterView;
    private TextView statusPageView;
    private TextView statusClockView;

    private int currentChapter = 0;
    private int totalChapters = 1;
    private boolean isOverlayVisible = false;
    private boolean isDualPageMode = false;
    private boolean isPdf = false;
    private int currentThemeId = 0;

    private int fontSize;
    private int lineSpacing;
    private int fontFamily;
    private int paragraphSpacing;
    private int pageMargin;

    private CharSequence rawChapterText = "";
    private int currentSpread = 0;
    private int lastPaginatedWidth = 0;
    private int lastPaginatedHeight = 0;
    private String pendingAnchor = null;
    private int currentTocIndex = -1;
    private int initialAnchorChar = -1;

    private long lastBackPressTime = 0;

    private final Runnable clockRunnable = new Runnable() {
        @Override
        public void run() {
            updateClock();
            mainHandler.postDelayed(this, 15000);
        }
    };

    private final Runnable saveProgressRunnable = new Runnable() {
        @Override
        public void run() {
            saveReadingPosition();
        }
    };

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(AppText.wrapContext(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        String path = getIntent().getStringExtra("book_path");
        if (path == null) {
            finish();
            return;
        }

        bookFile = new File(path);
        if (!bookFile.exists()) {
            Toast.makeText(this, AppText.get(R.string.cannot_open_book), Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        formatExt = FormatPrefs.getFormatExtension(bookFile);
        isPdf = "pdf".equalsIgnoreCase(formatExt);

        loadFormatSettings();
        buildUi();
        openBookDocument();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateClock();
        mainHandler.post(clockRunnable);
    }

    @Override
    protected void onPause() {
        super.onPause();
        mainHandler.removeCallbacks(clockRunnable);
        mainHandler.removeCallbacks(saveProgressRunnable);
        saveReadingPosition();
    }

    private void loadFormatSettings() {
        fontSize = FormatPrefs.getFontSize(this, formatExt);
        lineSpacing = FormatPrefs.getLineSpacing(this, formatExt);
        fontFamily = FormatPrefs.getFontFamily(this, formatExt);
        paragraphSpacing = FormatPrefs.getParagraphSpacing(this, formatExt);
        pageMargin = FormatPrefs.getPageMargin(this, formatExt);
        currentThemeId = FormatPrefs.getThemeId(this, formatExt);
        String mode = FormatPrefs.getReadingMode(this, formatExt);
        isDualPageMode = FormatPrefs.MODE_DUAL_PAGE.equals(mode);
    }

    private void buildUi() {
        rootContainer = new LinearLayout(this);
        rootContainer.setOrientation(LinearLayout.VERTICAL);
        rootContainer.setBackgroundColor(ThemeHelper.getBackgroundColor(currentThemeId));

        readingViewport = new FrameLayout(this);
        LinearLayout.LayoutParams vpParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f);
        rootContainer.addView(readingViewport, vpParams);

        int safeMargin = Math.max(28, pageMargin);

        scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setVerticalScrollBarEnabled(false);

        contentContainer = new LinearLayout(this);
        contentContainer.setOrientation(LinearLayout.VERTICAL);
        contentContainer.setPadding(dp(safeMargin), dp(safeMargin), dp(safeMargin), dp(safeMargin + 32));

        Typeface tf = FormatPrefs.getTypeface(fontFamily);

        singleTextView = new TextView(this);
        singleTextView.setTextSize(fontSize);
        singleTextView.setTypeface(tf);
        singleTextView.setLineSpacing(dp(lineSpacing), 1.0f);
        singleTextView.setIncludeFontPadding(false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            singleTextView.setBreakStrategy(Layout.BREAK_STRATEGY_SIMPLE);
            singleTextView.setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE);
        }
        singleTextView.setTextColor(ThemeHelper.getTextColor(currentThemeId));
        contentContainer.addView(singleTextView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        pdfImageView = new ImageView(this);
        pdfImageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        pdfImageView.setAdjustViewBounds(true);
        pdfImageView.setVisibility(View.GONE);
        contentContainer.addView(pdfImageView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        scrollView.addView(contentContainer, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        readingViewport.addView(scrollView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        dualPageContainer = new LinearLayout(this);
        dualPageContainer.setOrientation(LinearLayout.HORIZONTAL);
        dualPageContainer.setPadding(dp(safeMargin), dp(safeMargin), dp(safeMargin), dp(12));
        dualPageContainer.setVisibility(View.GONE);

        leftPageView = new BookPageView(this);
        LinearLayout.LayoutParams leftParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.0f);
        leftParams.setMargins(0, 0, dp(16), 0);
        dualPageContainer.addView(leftPageView, leftParams);

        View spine = new View(this);
        spine.setBackgroundColor(Color.argb(40, 128, 128, 128));
        dualPageContainer.addView(spine, new LinearLayout.LayoutParams(dp(2), ViewGroup.LayoutParams.MATCH_PARENT));

        rightPageView = new BookPageView(this);
        LinearLayout.LayoutParams rightParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.0f);
        rightParams.setMargins(dp(16), 0, 0, 0);
        dualPageContainer.addView(rightPageView, rightParams);

        readingViewport.addView(dualPageContainer, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        dualPdfContainer = new LinearLayout(this);
        dualPdfContainer.setOrientation(LinearLayout.HORIZONTAL);
        dualPdfContainer.setPadding(0, 0, 0, 0);
        dualPdfContainer.setVisibility(View.GONE);

        leftPdfImageView = new ImageView(this);
        leftPdfImageView.setScaleType(ImageView.ScaleType.FIT_END);
        leftPdfImageView.setAdjustViewBounds(true);
        LinearLayout.LayoutParams leftPdfParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.0f);
        leftPdfParams.setMargins(0, 0, 0, 0);
        dualPdfContainer.addView(leftPdfImageView, leftPdfParams);

        View pdfSpine = new View(this);
        pdfSpine.setBackgroundColor(Color.argb(40, 128, 128, 128));
        dualPdfContainer.addView(pdfSpine, new LinearLayout.LayoutParams(dp(1), ViewGroup.LayoutParams.MATCH_PARENT));

        rightPdfImageView = new ImageView(this);
        rightPdfImageView.setScaleType(ImageView.ScaleType.FIT_START);
        rightPdfImageView.setAdjustViewBounds(true);
        LinearLayout.LayoutParams rightPdfParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.0f);
        rightPdfParams.setMargins(0, 0, 0, 0);
        dualPdfContainer.addView(rightPdfImageView, rightPdfParams);

        readingViewport.addView(dualPdfContainer, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        readingViewport.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                if (isPdf || !isDualPageMode) return;
                if (rawChapterText == null || rawChapterText.length() == 0) return;
                int w = leftPageView.getWidth();
                int h = leftPageView.getHeight();
                if (w > 0 && h > 0 && (w != lastPaginatedWidth || h != lastPaginatedHeight)) {
                    lastPaginatedWidth = w;
                    lastPaginatedHeight = h;
                    int anchor = getCurrentAnchorCharIndex();
                    paginateCurrentChapter(applyParagraphSpacing(rawChapterText));
                    restoreAnchorCharIndex(anchor);
                }
            }
        });

        loadingBar = new ProgressBar(this);
        FrameLayout.LayoutParams loadParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        loadParams.gravity = Gravity.CENTER;
        readingViewport.addView(loadingBar, loadParams);

        buildOverlays();

        bottomStatusBar = new LinearLayout(this);
        bottomStatusBar.setOrientation(LinearLayout.HORIZONTAL);
        bottomStatusBar.setGravity(Gravity.CENTER_VERTICAL);
        bottomStatusBar.setPadding(dp(24), dp(4), dp(24), dp(4));
        updateStatusBarGradient();

        statusChapterView = new TextView(this);
        statusChapterView.setTextSize(12);
        statusChapterView.setTextColor(ThemeHelper.getStatusBarTextColor(currentThemeId));
        statusChapterView.setSingleLine(true);
        bottomStatusBar.addView(statusChapterView, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.2f));

        statusPageView = new TextView(this);
        statusPageView.setTextSize(12);
        statusPageView.setTextColor(ThemeHelper.getStatusBarAccentColor(currentThemeId));
        statusPageView.setGravity(Gravity.CENTER);
        bottomStatusBar.addView(statusPageView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        statusClockView = new TextView(this);
        statusClockView.setTextSize(12);
        statusClockView.setTextColor(ThemeHelper.getStatusBarTextColor(currentThemeId));
        statusClockView.setGravity(Gravity.END);
        bottomStatusBar.addView(statusClockView, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));

        rootContainer.addView(bottomStatusBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        setContentView(rootContainer);
        updateModeVisibility();

        scrollView.getViewTreeObserver().addOnScrollChangedListener(new ViewTreeObserver.OnScrollChangedListener() {
            @Override
            public void onScrollChanged() {
                if (!isDualPageMode) {
                    updateProgress();
                    mainHandler.removeCallbacks(saveProgressRunnable);
                    mainHandler.postDelayed(saveProgressRunnable, 500);
                }
            }
        });
    }

    private void updateClock() {
        if (statusClockView == null) return;
        boolean showTime = FormatPrefs.getShowTime(this);
        statusClockView.setVisibility(showTime ? View.VISIBLE : View.GONE);
        if (showTime) {
            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
            statusClockView.setText(sdf.format(new Date()));
        }
    }

    private void buildOverlays() {
        int panelBg = ThemeHelper.getToolbarColor(currentThemeId);

        topToolbar = new LinearLayout(this);
        topToolbar.setOrientation(LinearLayout.HORIZONTAL);
        topToolbar.setGravity(Gravity.CENTER_VERTICAL);
        topToolbar.setPadding(dp(24), dp(10), dp(24), dp(10));
        topToolbar.setBackgroundColor(panelBg);
        topToolbar.setVisibility(View.GONE);

        TextView bookTitle = new TextView(this);
        bookTitle.setText(bookFile.getName());
        bookTitle.setTextSize(16);
        bookTitle.setTextColor(ThemeHelper.getTextColor(currentThemeId));
        bookTitle.getPaint().setFakeBoldText(true);
        topToolbar.addView(bookTitle, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));

        Button btnClose = createToolbarButton(AppText.get(R.string.close), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        topToolbar.addView(btnClose);

        FrameLayout.LayoutParams topParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        topParams.gravity = Gravity.TOP;
        readingViewport.addView(topToolbar, topParams);

        bottomDock = new LinearLayout(this);
        bottomDock.setOrientation(LinearLayout.VERTICAL);
        bottomDock.setVisibility(View.GONE);

        secondaryScroll = new HorizontalScrollView(this);
        secondaryScroll.setFillViewport(true);
        secondaryScroll.setHorizontalScrollBarEnabled(false);
        secondaryScroll.setVisibility(View.GONE);
        secondaryScroll.setBackgroundColor(panelBg);

        secondaryToolbar = new LinearLayout(this);
        secondaryToolbar.setOrientation(LinearLayout.HORIZONTAL);
        secondaryToolbar.setGravity(Gravity.CENTER_VERTICAL);
        secondaryToolbar.setPadding(dp(20), dp(8), dp(20), dp(8));
        secondaryScroll.addView(secondaryToolbar);

        bottomDock.addView(secondaryScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        bottomToolbar = new LinearLayout(this);
        bottomToolbar.setOrientation(LinearLayout.HORIZONTAL);
        bottomToolbar.setGravity(Gravity.CENTER_VERTICAL);
        bottomToolbar.setPadding(dp(20), dp(10), dp(20), dp(10));
        bottomToolbar.setBackgroundColor(panelBg);

        Button btnToc = createToolbarButton(AppText.get(R.string.directory), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showTocDialog();
            }
        });
        bottomToolbar.addView(btnToc);

        Button btnTheme = createToolbarButton(AppText.get(R.string.theme_select), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleSubMenu("theme");
            }
        });
        bottomToolbar.addView(btnTheme);

        if (!isPdf) {
            Button btnFont = createToolbarButton(AppText.get(R.string.font_size), new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    toggleSubMenu("font");
                }
            });
            bottomToolbar.addView(btnFont);

            Button btnLine = createToolbarButton(AppText.get(R.string.line_spacing), new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    toggleSubMenu("line");
                }
            });
            bottomToolbar.addView(btnLine);

            Button btnFontFamily = createToolbarButton(AppText.get(R.string.font_family), new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    toggleSubMenu("font_family");
                }
            });
            bottomToolbar.addView(btnFontFamily);
        }

        Button btnMode = createToolbarButton(AppText.get(R.string.reading_mode), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleSubMenu("mode");
            }
        });
        bottomToolbar.addView(btnMode);

        Button btnStatusBar = createToolbarButton(AppText.get(R.string.status_bar_setting), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleSubMenu("status_bar");
            }
        });
        bottomToolbar.addView(btnStatusBar);

        bottomDock.addView(bottomToolbar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        FrameLayout.LayoutParams dockParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dockParams.gravity = Gravity.BOTTOM;
        readingViewport.addView(bottomDock, dockParams);
    }

    private Button createToolbarButton(String text, View.OnClickListener listener) {
        Button btn = new Button(this);
        btn.setText(text);
        btn.setTextSize(13);
        btn.setTextColor(ThemeHelper.getButtonTextColor(currentThemeId, false));
        btn.setBackground(ThemeHelper.createThemedButtonDrawable(currentThemeId, false, 8, this));
        btn.setPadding(dp(12), dp(6), dp(12), dp(6));
        btn.setFocusable(true);
        btn.setOnClickListener(listener);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(38));
        params.setMargins(dp(6), 0, 0, 0);
        btn.setLayoutParams(params);
        return btn;
    }

    private void toggleSubMenu(String menu) {
        if (menu.equals(activeSubMenu)) {
            hideSubMenu();
        } else {
            showSubMenu(menu);
        }
    }

    private void hideSubMenu() {
        activeSubMenu = null;
        if (secondaryScroll != null) {
            secondaryScroll.setVisibility(View.GONE);
        }
        if (secondaryToolbar != null) {
            secondaryToolbar.removeAllViews();
        }
    }

    private void showSubMenu(String menu) {
        activeSubMenu = menu;
        secondaryToolbar.removeAllViews();
        secondaryScroll.setVisibility(View.VISIBLE);
        int toolbarBg = ThemeHelper.getToolbarColor(currentThemeId);
        secondaryScroll.setBackgroundColor(toolbarBg);

        Button defaultFocusButton = null;

        if ("font".equals(menu)) {
            int[] sizes = {14, 16, 18, 20, 24, 28, 32, 36, 40};
            for (final int sz : sizes) {
                boolean isSel = (fontSize == sz);
                Button btn = createSecondaryButton(String.valueOf(sz), isSel, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        applyQuickFontSize(sz);
                    }
                });
                if (isSel) {
                    defaultFocusButton = btn;
                }
                secondaryToolbar.addView(btn);
            }
        } else if ("line".equals(menu)) {
            int[] lines = {0, 2, 4, 6, 8, 12, 16, 20};
            for (final int sp : lines) {
                boolean isSel = (lineSpacing == sp);
                Button btn = createSecondaryButton(sp + "dp", isSel, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        applyQuickLineSpacing(sp);
                    }
                });
                if (isSel) {
                    defaultFocusButton = btn;
                }
                secondaryToolbar.addView(btn);
            }
        } else if ("font_family".equals(menu)) {
            for (int i = 0; i < FormatPrefs.FONT_FAMILY_COUNT; i++) {
                final int fId = i;
                boolean isSel = (fontFamily == fId);
                Button btn = createSecondaryButton(FormatPrefs.getFontFamilyName(fId), isSel, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        applyQuickFontFamily(fId);
                    }
                });
                if (isSel) {
                    defaultFocusButton = btn;
                }
                secondaryToolbar.addView(btn);
            }
        } else if ("mode".equals(menu)) {
            Button btnScroll = createSecondaryButton(AppText.get(R.string.mode_single_scroll), !isDualPageMode, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    applyQuickReadingMode(FormatPrefs.MODE_SCROLL);
                }
            });
            if (!isDualPageMode) defaultFocusButton = btnScroll;
            secondaryToolbar.addView(btnScroll);

            Button btnDual = createSecondaryButton(AppText.get(R.string.mode_dual_page), isDualPageMode, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    applyQuickReadingMode(FormatPrefs.MODE_DUAL_PAGE);
                }
            });
            if (isDualPageMode) defaultFocusButton = btnDual;
            secondaryToolbar.addView(btnDual);
        } else if ("theme".equals(menu)) {
            for (int i = 0; i < ThemeHelper.THEME_COUNT; i++) {
                final int thId = i;
                boolean isSel = (currentThemeId == thId);
                Button btn = createSecondaryButton(ThemeHelper.getThemeName(i), isSel, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        applyQuickTheme(thId);
                    }
                });
                if (isSel) {
                    defaultFocusButton = btn;
                }
                secondaryToolbar.addView(btn);
            }
        } else if ("status_bar".equals(menu)) {
            final boolean showBar = FormatPrefs.getShowStatusBar(this);
            Button btnShow = createSecondaryButton(AppText.get(R.string.show_status_bar), showBar, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    FormatPrefs.setShowStatusBar(ReaderActivity.this, true);
                    updateStatusBarVisibility();
                    showSubMenu("status_bar");
                }
            });
            if (showBar) defaultFocusButton = btnShow;
            secondaryToolbar.addView(btnShow);

            Button btnHide = createSecondaryButton(AppText.get(R.string.hide_status_bar), !showBar, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    FormatPrefs.setShowStatusBar(ReaderActivity.this, false);
                    updateStatusBarVisibility();
                    showSubMenu("status_bar");
                }
            });
            if (!showBar) defaultFocusButton = btnHide;
            secondaryToolbar.addView(btnHide);
        }

        final Button targetToFocus = (defaultFocusButton != null) ? defaultFocusButton :
                (secondaryToolbar.getChildCount() > 0 ? (Button) secondaryToolbar.getChildAt(0) : null);

        if (targetToFocus != null) {
            targetToFocus.post(new Runnable() {
                @Override
                public void run() {
                    targetToFocus.requestFocus();
                    int targetLeft = targetToFocus.getLeft() - dp(40);
                    secondaryScroll.smoothScrollTo(Math.max(0, targetLeft), 0);
                }
            });
        }
    }

    private Button createSecondaryButton(String text, boolean isSelected, View.OnClickListener listener) {
        Button btn = new Button(this);
        btn.setText(text);
        btn.setTextSize(12);
        btn.setTextColor(ThemeHelper.getButtonTextColor(currentThemeId, isSelected));
        btn.setBackground(ThemeHelper.createThemedButtonDrawable(currentThemeId, isSelected, 6, this));
        btn.setPadding(dp(12), dp(6), dp(12), dp(6));
        btn.setFocusable(true);
        btn.setOnClickListener(listener);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(34));
        params.setMargins(0, 0, dp(8), 0);
        btn.setLayoutParams(params);
        return btn;
    }

    private void applyQuickFontSize(int newSize) {
        int anchor = getCurrentAnchorCharIndex();
        FormatPrefs.setFontSize(this, formatExt, newSize);
        fontSize = newSize;
        singleTextView.setTextSize(fontSize);
        if (isDualPageMode) {
            paginateCurrentChapter(applyParagraphSpacing(rawChapterText));
        }
        restoreAnchorCharIndex(anchor);
        showSubMenu("font");
        Toast.makeText(this, AppText.get(R.string.font_size) + ": " + fontSize, Toast.LENGTH_SHORT).show();
    }

    private void applyQuickLineSpacing(int newSpacing) {
        int anchor = getCurrentAnchorCharIndex();
        FormatPrefs.setLineSpacing(this, formatExt, newSpacing);
        lineSpacing = newSpacing;
        singleTextView.setLineSpacing(dp(lineSpacing), 1.0f);
        if (isDualPageMode) {
            paginateCurrentChapter(applyParagraphSpacing(rawChapterText));
        }
        restoreAnchorCharIndex(anchor);
        showSubMenu("line");
        Toast.makeText(this, AppText.get(R.string.line_spacing) + ": " + lineSpacing + "dp", Toast.LENGTH_SHORT).show();
    }

    private void applyQuickFontFamily(int newFamily) {
        int anchor = getCurrentAnchorCharIndex();
        FormatPrefs.setFontFamily(this, formatExt, newFamily);
        fontFamily = newFamily;
        Typeface tf = FormatPrefs.getTypeface(fontFamily);
        singleTextView.setTypeface(tf);
        if (isDualPageMode) {
            paginateCurrentChapter(applyParagraphSpacing(rawChapterText));
        }
        restoreAnchorCharIndex(anchor);
        showSubMenu("font_family");
        Toast.makeText(this, AppText.get(R.string.font_family) + ": " + FormatPrefs.getFontFamilyName(fontFamily), Toast.LENGTH_SHORT).show();
    }

    private int getCurrentAnchorCharIndex() {
        if (isDualPageMode) {
            int pageIdx = currentSpread * 2;
            if (masterLayout != null && pageIdx >= 0 && pageIdx < dualPages.size()) {
                return masterLayout.getLineStart(dualPages.get(pageIdx).start);
            }
        } else {
            if (singleTextView != null && singleTextView.getLayout() != null && scrollView != null) {
                int scrollY = scrollView.getScrollY();
                int line = singleTextView.getLayout().getLineForVertical(scrollY);
                return singleTextView.getLayout().getLineStart(line);
            }
        }
        return 0;
    }

    private void restoreAnchorCharIndex(final int anchorCharIndex) {
        if (isDualPageMode) {
            int targetSpread = 0;
            if (masterLayout != null) {
                for (int i = 0; i < dualPages.size(); i++) {
                    int pageStartChar = masterLayout.getLineStart(dualPages.get(i).start);
                    if (pageStartChar <= anchorCharIndex) {
                        targetSpread = i / 2;
                    } else {
                        break;
                    }
                }
            }
            int totalSpreads = Math.max(1, (dualPages.size() + 1) / 2);
            currentSpread = Math.min(Math.max(0, targetSpread), totalSpreads - 1);
            displayCurrentSpread();
        } else {
            singleTextView.setText(applyParagraphSpacing(rawChapterText));
            singleTextView.post(new Runnable() {
                @Override
                public void run() {
                    if (singleTextView.getLayout() != null) {
                        int safeOffset = Math.min(anchorCharIndex, singleTextView.getText().length());
                        int line = singleTextView.getLayout().getLineForOffset(safeOffset);
                        int targetY = singleTextView.getLayout().getLineTop(line);
                        scrollView.scrollTo(0, targetY);
                    }
                }
            });
        }
    }

    private int findAnchorChar(CharSequence text, String target) {
        if (text == null || target == null || target.isEmpty()) return -1;
        String s = text.toString();
        int idx = s.indexOf(target);
        if (idx >= 0) return idx;
        String stripped = target.replaceFirst("^[0-9]+(\\.[0-9]+)*\\s*", "").trim();
        if (!stripped.isEmpty() && stripped.length() > 2) {
            idx = s.indexOf(stripped);
            if (idx >= 0) return idx;
        }
        return -1;
    }

    private void updateStatusBarVisibility() {
        if (bottomStatusBar == null) return;
        boolean showBar = FormatPrefs.getShowStatusBar(this);
        bottomStatusBar.setVisibility(showBar ? View.VISIBLE : View.GONE);
    }

    private void updateStatusBarGradient() {
        if (bottomStatusBar == null) return;
        int baseBarColor = ThemeHelper.getStatusBarBackgroundColor(currentThemeId);
        int topFadeColor = Color.argb(0, Color.red(baseBarColor), Color.green(baseBarColor), Color.blue(baseBarColor));
        GradientDrawable barGradient = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{topFadeColor, baseBarColor}
        );
        bottomStatusBar.setBackground(barGradient);
    }

    private void applyQuickReadingMode(String newMode) {
        FormatPrefs.setReadingMode(this, formatExt, newMode);
        isDualPageMode = FormatPrefs.MODE_DUAL_PAGE.equals(newMode);
        updateModeVisibility();
        if (isPdf) {
            if (isDualPageMode) {
                currentSpread = currentChapter / 2;
            } else {
                currentChapter = currentSpread * 2;
            }
        }
        loadCurrentChapter();
        showSubMenu("mode");
    }

    private void applyQuickTheme(int newThemeId) {
        currentThemeId = newThemeId;
        FormatPrefs.setThemeId(this, formatExt, newThemeId);
        applyTheme();
        showSubMenu("theme");
    }

    private void openBookDocument() {
        loadingBar.setVisibility(View.VISIBLE);
        new AsyncTask<Void, Void, BookDocument>() {
            private Exception error;

            @Override
            protected BookDocument doInBackground(Void... voids) {
                try {
                    return BookParser.open(ReaderActivity.this, bookFile);
                } catch (Exception e) {
                    error = e;
                    AppLog.error("Error opening book", e);
                    return null;
                }
            }

            @Override
            protected void onPostExecute(BookDocument doc) {
                loadingBar.setVisibility(View.GONE);
                if (isFinishing() || (Build.VERSION.SDK_INT >= 17 && isDestroyed())) {
                    if (doc != null) doc.close();
                    return;
                }
                if (doc == null) {
                    Toast.makeText(ReaderActivity.this,
                            AppText.format(R.string.cannot_open_book, error != null ? error.getMessage() : "Unknown"),
                            Toast.LENGTH_LONG).show();
                    finish();
                    return;
                }
                document = doc;
                totalChapters = doc.getChapterCount();
                currentChapter = Math.max(0, Math.min(BookStore.getSavedChapter(ReaderActivity.this, bookFile), totalChapters - 1));
                if (isPdf) {
                    if (isDualPageMode) {
                        currentSpread = currentChapter / 2;
                    } else {
                        currentSpread = 0;
                    }
                } else {
                    initialAnchorChar = BookStore.getSavedOffset(ReaderActivity.this, bookFile);
                    currentSpread = 0;
                }
                loadCurrentChapter();
            }
        }.execute();
    }

    private void loadCurrentChapter() {
        if (document == null) return;
        loadingBar.setVisibility(View.VISIBLE);

        if (document.isPdf()) {
            if (isDualPageMode) {
                if (currentSpread < 0) {
                    currentSpread = Math.max(0, currentChapter / 2);
                }
                loadPdfSpread(currentSpread);
            } else {
                loadPdfPage(currentChapter);
            }
            return;
        }

        new AsyncTask<Integer, Void, CharSequence>() {
            @Override
            protected CharSequence doInBackground(Integer... params) {
                try {
                    return document.loadChapter(params[0]);
                } catch (Exception e) {
                    AppLog.error("Error loading chapter", e);
                    return "Error loading chapter: " + e.getMessage();
                }
            }

            @Override
            protected void onPostExecute(CharSequence rawText) {
                loadingBar.setVisibility(View.GONE);
                if (isFinishing() || (Build.VERSION.SDK_INT >= 17 && isDestroyed())) {
                    return;
                }
                rawChapterText = rawText;
                CharSequence text = applyParagraphSpacing(rawChapterText);

                if (isDualPageMode) {
                    paginateCurrentChapter(text);
                    if (pendingAnchor != null && rawChapterText != null) {
                        int targetChar = findAnchorChar(rawChapterText, pendingAnchor);
                        pendingAnchor = null;
                        if (targetChar >= 0 && masterLayout != null) {
                            int targetPage = 0;
                            for (int p = 0; p < dualPages.size(); p++) {
                                int pStart = masterLayout.getLineStart(dualPages.get(p).start);
                                int pEnd = masterLayout.getLineEnd(Math.max(0, dualPages.get(p).end - 1));
                                if (targetChar >= pStart && targetChar <= pEnd) {
                                    targetPage = p;
                                    break;
                                }
                            }
                            currentSpread = targetPage / 2;
                        }
                    } else if (initialAnchorChar > 0) {
                        int anchor = initialAnchorChar;
                        initialAnchorChar = -1;
                        int targetSpread = 0;
                        if (masterLayout != null) {
                            for (int i = 0; i < dualPages.size(); i++) {
                                int pageStartChar = masterLayout.getLineStart(dualPages.get(i).start);
                                if (pageStartChar <= anchor) {
                                    targetSpread = i / 2;
                                } else {
                                    break;
                                }
                            }
                        }
                        int totalSpreads = Math.max(1, (dualPages.size() + 1) / 2);
                        currentSpread = Math.min(Math.max(0, targetSpread), totalSpreads - 1);
                    } else if (currentSpread < 0) {
                        currentSpread = Math.max(0, (dualPages.size() + 1) / 2 - 1);
                    }
                    displayCurrentSpread();
                } else {
                    singleTextView.setText(text);
                    if (pendingAnchor != null && rawChapterText != null) {
                        final int targetChar = findAnchorChar(rawChapterText, pendingAnchor);
                        pendingAnchor = null;
                        if (targetChar >= 0) {
                            singleTextView.post(new Runnable() {
                                @Override
                                public void run() {
                                    if (singleTextView.getLayout() != null) {
                                        int safe = Math.min(targetChar, singleTextView.getText().length());
                                        int line = singleTextView.getLayout().getLineForOffset(safe);
                                        int y = singleTextView.getLayout().getLineTop(line);
                                        scrollView.scrollTo(0, y);
                                    }
                                }
                            });
                        }
                    } else if (initialAnchorChar > 0) {
                        final int anchor = initialAnchorChar;
                        initialAnchorChar = -1;
                        singleTextView.post(new Runnable() {
                            @Override
                            public void run() {
                                if (singleTextView.getLayout() != null) {
                                    int safe = Math.min(anchor, singleTextView.getText().length());
                                    int line = singleTextView.getLayout().getLineForOffset(safe);
                                    int y = singleTextView.getLayout().getLineTop(line);
                                    scrollView.scrollTo(0, y);
                                }
                            }
                        });
                    } else {
                        restoreScrollOffset();
                    }
                    updateProgress();
                }
            }
        }.execute(currentChapter);
    }

    private TextPaint createTextPaint() {
        TextPaint paint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        paint.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, fontSize, getResources().getDisplayMetrics()));
        paint.setColor(ThemeHelper.getTextColor(currentThemeId));
        paint.setTypeface(FormatPrefs.getTypeface(fontFamily));
        return paint;
    }

    private void paginateCurrentChapter(CharSequence text) {
        dualPages.clear();
        if (text == null || text.length() == 0) {
            masterLayout = null;
            return;
        }

        int safeMargin = Math.max(28, pageMargin);
        int horizMarginPx = dp(safeMargin);
        int vertMarginPx = dp(safeMargin);

        int totalWidth = readingViewport.getWidth() > 0 ? readingViewport.getWidth() : rootContainer.getWidth();
        int totalHeight = readingViewport.getHeight() > 0 ? readingViewport.getHeight() : rootContainer.getHeight();
        if (totalWidth <= 0 && getResources() != null) totalWidth = getResources().getDisplayMetrics().widthPixels;
        if (totalHeight <= 0 && getResources() != null) totalHeight = getResources().getDisplayMetrics().heightPixels;
        if (totalWidth <= 0) totalWidth = 1920;
        if (totalHeight <= 0) totalHeight = 1080;

        TextPaint paint = createTextPaint();
        int actualW = leftPageView != null ? leftPageView.getWidth() : 0;
        int actualH = leftPageView != null ? leftPageView.getHeight() : 0;

        int pageW = actualW > 0 ? actualW : Math.max(dp(200), (totalWidth - horizMarginPx * 2 - dp(34)) / 2);
        int availH = actualH > 0 ? actualH : Math.max(dp(150), totalHeight - vertMarginPx - dp(12) - dp(34));

        int pageH = Math.max(dp(100), availH - dp(2));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            masterLayout = StaticLayout.Builder.obtain(text, 0, text.length(), paint, pageW)
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                    .setLineSpacing(dp(lineSpacing), 1.0f)
                    .setIncludePad(false)
                    .setBreakStrategy(Layout.BREAK_STRATEGY_SIMPLE)
                    .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE)
                    .build();
        } else {
            masterLayout = new StaticLayout(text, paint, pageW, Layout.Alignment.ALIGN_NORMAL, 1.0f, dp(lineSpacing), false);
        }

        int lineCount = masterLayout.getLineCount();
        int lineStart = 0;

        while (lineStart < lineCount) {
            int top = masterLayout.getLineTop(lineStart);
            int lineEnd = lineStart;
            while (lineEnd < lineCount && (masterLayout.getLineBottom(lineEnd) - top) <= pageH) {
                lineEnd++;
            }
            if (lineEnd == lineStart) {
                lineEnd = lineStart + 1;
            }

            if (lineEnd < lineCount && (lineEnd - lineStart) > 2) {
                int nextStart = masterLayout.getLineStart(lineEnd);
                int nextEnd = masterLayout.getLineEnd(lineEnd);
                if (nextEnd - nextStart <= 3) {
                    lineEnd--;
                }
            }

            dualPages.add(new PageRange(lineStart, lineEnd));
            lineStart = lineEnd;
        }

        if (dualPages.isEmpty()) {
            dualPages.add(new PageRange(0, Math.max(1, lineCount)));
        }
    }

    private void displayCurrentSpread() {
        int totalSpreads = Math.max(1, (dualPages.size() + 1) / 2);
        if (currentSpread < 0) {
            currentSpread = 0;
        } else if (currentSpread >= totalSpreads) {
            currentSpread = Math.max(0, totalSpreads - 1);
        }

        int leftIdx = currentSpread * 2;
        int rightIdx = leftIdx + 1;

        if (masterLayout != null && leftIdx < dualPages.size()) {
            PageRange lp = dualPages.get(leftIdx);
            leftPageView.setPage(masterLayout, lp.start, lp.end);
        } else {
            leftPageView.clear();
        }

        if (masterLayout != null && rightIdx < dualPages.size()) {
            PageRange rp = dualPages.get(rightIdx);
            rightPageView.setPage(masterLayout, rp.start, rp.end);
        } else {
            rightPageView.clear();
        }

        saveReadingPosition();
        updateProgress();
    }

    private CharSequence applyParagraphSpacing(CharSequence text) {
        if (text == null || text.length() == 0) {
            return "";
        }
        if (text instanceof Spanned) {
            return text;
        }
        String str = text.toString().replace("\r", "");
        String[] rawLines = str.split("\n");
        List<String> paragraphs = new ArrayList<String>();
        StringBuilder currentPara = new StringBuilder();

        for (String line : rawLines) {
            String clean = line.replace('\u00A0', ' ').replace('\u3000', ' ').trim();
            if (clean.isEmpty()) {
                if (currentPara.length() > 0) {
                    paragraphs.add(currentPara.toString().trim());
                    currentPara.setLength(0);
                }
            } else {
                if (currentPara.length() > 0) {
                    currentPara.append(" ");
                }
                currentPara.append(clean);
            }
        }
        if (currentPara.length() > 0) {
            paragraphs.add(currentPara.toString().trim());
        }

        if (paragraphs.isEmpty()) {
            return "";
        }

        if (paragraphSpacing <= 0) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < paragraphs.size(); i++) {
                if (i > 0) sb.append("\n");
                sb.append("　　").append(paragraphs.get(i));
            }
            return sb.toString();
        }

        SpannableStringBuilder ssb = new SpannableStringBuilder();
        int spacerPx = dp(paragraphSpacing);
        for (int i = 0; i < paragraphs.size(); i++) {
            if (i > 0) {
                int gapStart = ssb.length();
                ssb.append("\n");
                ssb.setSpan(new AbsoluteSizeSpan(spacerPx), gapStart, gapStart + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            ssb.append("　　").append(paragraphs.get(i));
        }
        return ssb;
    }

    private void loadPdfPage(final int pageIndex) {
        loadingBar.setVisibility(View.VISIBLE);
        new AsyncTask<Integer, Void, Bitmap>() {
            @Override
            protected Bitmap doInBackground(Integer... params) {
                int width = rootContainer.getWidth();
                int height = rootContainer.getHeight();
                if (width <= 0 && getResources() != null) width = getResources().getDisplayMetrics().widthPixels;
                if (height <= 0 && getResources() != null) height = getResources().getDisplayMetrics().heightPixels;
                if (width <= 0) width = 1920;
                if (height <= 0) height = 1080;
                return document.renderPdfPage(params[0], width, height);
            }

            @Override
            protected void onPostExecute(Bitmap bitmap) {
                loadingBar.setVisibility(View.GONE);
                if (isFinishing() || (Build.VERSION.SDK_INT >= 17 && isDestroyed())) {
                    if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
                    return;
                }
                if (bitmap != null) {
                    if (currentPdfBitmap != null && !currentPdfBitmap.isRecycled()) {
                        currentPdfBitmap.recycle();
                    }
                    currentPdfBitmap = bitmap;
                    pdfImageView.setImageBitmap(bitmap);
                    scrollView.scrollTo(0, 0);
                    currentChapter = pageIndex;
                    saveReadingPosition();
                    updateProgress();
                } else {
                    Toast.makeText(ReaderActivity.this, AppText.get(R.string.cannot_open_book), Toast.LENGTH_SHORT).show();
                }
            }
        }.execute(pageIndex);
    }

    private void loadPdfSpread(final int spreadIndex) {
        loadingBar.setVisibility(View.VISIBLE);
        new AsyncTask<Integer, Void, Bitmap[]>() {
            @Override
            protected Bitmap[] doInBackground(Integer... params) {
                int spread = params[0];
                int leftIdx = spread * 2;
                int rightIdx = leftIdx + 1;

                int totalW = dualPdfContainer != null && dualPdfContainer.getWidth() > 0 ?
                        dualPdfContainer.getWidth() : readingViewport.getWidth();
                int totalH = dualPdfContainer != null && dualPdfContainer.getHeight() > 0 ?
                        dualPdfContainer.getHeight() : readingViewport.getHeight();
                if (totalW <= 0 && getResources() != null) totalW = getResources().getDisplayMetrics().widthPixels;
                if (totalH <= 0 && getResources() != null) totalH = getResources().getDisplayMetrics().heightPixels;
                if (totalW <= 0) totalW = 1920;
                if (totalH <= 0) totalH = 1080;

                int targetW = (totalW - dp(2)) / 2;
                int targetH = totalH;

                Bitmap leftBmp = document.renderPdfPage(leftIdx, targetW, targetH);
                Bitmap rightBmp = (rightIdx < totalChapters) ? document.renderPdfPage(rightIdx, targetW, targetH) : null;

                return new Bitmap[]{leftBmp, rightBmp};
            }

            @Override
            protected void onPostExecute(Bitmap[] bitmaps) {
                loadingBar.setVisibility(View.GONE);
                if (isFinishing() || (Build.VERSION.SDK_INT >= 17 && isDestroyed())) {
                    if (bitmaps != null) {
                        for (Bitmap b : bitmaps) {
                            if (b != null && !b.isRecycled()) b.recycle();
                        }
                    }
                    return;
                }
                if (bitmaps != null && bitmaps.length >= 2) {
                    if (leftPdfBitmap != null && !leftPdfBitmap.isRecycled()) {
                        leftPdfBitmap.recycle();
                    }
                    if (rightPdfBitmap != null && !rightPdfBitmap.isRecycled()) {
                        rightPdfBitmap.recycle();
                    }
                    leftPdfBitmap = bitmaps[0];
                    rightPdfBitmap = bitmaps[1];

                    leftPdfImageView.setImageBitmap(leftPdfBitmap);
                    if (rightPdfBitmap != null) {
                        leftPdfImageView.setScaleType(ImageView.ScaleType.FIT_END);
                        rightPdfImageView.setScaleType(ImageView.ScaleType.FIT_START);
                        rightPdfImageView.setImageBitmap(rightPdfBitmap);
                        rightPdfImageView.setVisibility(View.VISIBLE);
                    } else {
                        leftPdfImageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
                        rightPdfImageView.setImageDrawable(null);
                        rightPdfImageView.setVisibility(View.INVISIBLE);
                    }
                    currentChapter = spreadIndex * 2;
                    saveReadingPosition();
                    updateProgress();
                } else {
                    Toast.makeText(ReaderActivity.this, AppText.get(R.string.cannot_open_book), Toast.LENGTH_SHORT).show();
                }
            }
        }.execute(spreadIndex);
    }

    private void restoreScrollOffset() {
        if (isDualPageMode || isPdf) return;
        int savedChapter = BookStore.getSavedChapter(this, bookFile);
        if (currentChapter == savedChapter) {
            final int offset = BookStore.getSavedOffset(ReaderActivity.this, bookFile);
            scrollView.post(new Runnable() {
                @Override
                public void run() {
                    scrollView.scrollTo(0, offset);
                }
            });
        }
    }

    private void saveReadingPosition() {
        if (document == null) return;
        if (isPdf) {
            if (isDualPageMode) {
                int totalSpreads = Math.max(1, (totalChapters + 1) / 2);
                float spreadFraction = (float) currentSpread / (float) totalSpreads;
                BookStore.saveProgress(this, bookFile, currentSpread * 2, 0, spreadFraction);
            } else {
                float totalFraction = (float) currentChapter / (float) Math.max(1, totalChapters);
                BookStore.saveProgress(this, bookFile, currentChapter, 0, totalFraction);
            }
        } else if (isDualPageMode) {
            int totalSpreads = Math.max(1, (dualPages.size() + 1) / 2);
            float spreadFraction = (float) currentSpread / (float) totalSpreads;
            float totalFraction = ((float) currentChapter + spreadFraction) / (float) Math.max(1, totalChapters);
            int charIndex = getCurrentAnchorCharIndex();
            BookStore.saveProgress(this, bookFile, currentChapter, charIndex, totalFraction);
        } else {
            int scrollY = scrollView.getScrollY();
            int maxScroll = Math.max(1, contentContainer.getHeight() - scrollView.getHeight());
            float chapterFraction = Math.max(0.0f, Math.min(1.0f, (float) scrollY / (float) maxScroll));
            float totalFraction = ((float) currentChapter + chapterFraction) / (float) Math.max(1, totalChapters);
            int charIndex = getCurrentAnchorCharIndex();
            BookStore.saveProgress(this, bookFile, currentChapter, charIndex, totalFraction);
        }
        updateProgress();
    }

    private void updateProgress() {
        if (document == null) return;

        if (isPdf) {
            if (isDualPageMode) {
                int pageStart = currentSpread * 2 + 1;
                int pageEnd = Math.min(totalChapters, pageStart + 1);
                float progress = ((float) pageEnd / (float) Math.max(1, totalChapters)) * 100.0f;
                String pageStr = (pageStart == pageEnd) ? String.valueOf(pageStart) : (pageStart + "-" + pageEnd);
                statusPageView.setText(String.format(Locale.US, "%s %s / %d  (%.1f%%)",
                        AppText.isChinese() ? "页" : "p.", pageStr, totalChapters, progress));
                statusChapterView.setText(AppText.isChinese() ? ("第 " + pageStr + " 页") : ("Page " + pageStr));
            } else {
                String chapTitle = document.getChapterTitle(currentChapter);
                statusChapterView.setText(chapTitle);
                float progress = ((float) (currentChapter + 1) / (float) Math.max(1, totalChapters)) * 100.0f;
                statusPageView.setText(String.format(Locale.US, "%s %d / %d  (%.1f%%)",
                        AppText.isChinese() ? "第" : "Page", currentChapter + 1, totalChapters, progress));
            }
        } else if (isDualPageMode) {
            String chapTitle = document.getChapterTitle(currentChapter);
            statusChapterView.setText(chapTitle);
            int totalSpreads = Math.max(1, (dualPages.size() + 1) / 2);
            int curSpread1 = currentSpread + 1;
            float chapFraction = (float) curSpread1 / (float) totalSpreads;
            float totalProgress = ((float) currentChapter + chapFraction) / (float) Math.max(1, totalChapters) * 100.0f;
            int pageStart = currentSpread * 2 + 1;
            int pageEnd = Math.min(dualPages.size(), pageStart + 1);
            String pageStr = (pageStart == pageEnd) ? String.valueOf(pageStart) : (pageStart + "-" + pageEnd);
            statusPageView.setText(String.format(Locale.US, "%s %s / %d  (%.1f%%)",
                    AppText.isChinese() ? "页" : "p.", pageStr, dualPages.size(), totalProgress));
        } else {
            String chapTitle = document.getChapterTitle(currentChapter);
            statusChapterView.setText(chapTitle);
            int scrollY = scrollView.getScrollY();
            int maxScroll = Math.max(1, contentContainer.getHeight() - scrollView.getHeight());
            float chapterFraction = Math.max(0.0f, Math.min(1.0f, (float) scrollY / (float) maxScroll));
            float totalProgress = ((float) currentChapter + chapterFraction) / (float) Math.max(1, totalChapters) * 100.0f;
            statusPageView.setText(String.format(Locale.US, "%.1f%%", totalProgress));
        }
    }

    private void toggleReadingMode() {
        if (isPdf) {
            isDualPageMode = !isDualPageMode;
            FormatPrefs.setReadingMode(this, formatExt, isDualPageMode ? FormatPrefs.MODE_DUAL_PAGE : FormatPrefs.MODE_SCROLL);
            updateModeVisibility();
            if (isDualPageMode) {
                currentSpread = currentChapter / 2;
            } else {
                currentChapter = currentSpread * 2;
            }
            loadCurrentChapter();
            return;
        }
        int anchor = getCurrentAnchorCharIndex();
        isDualPageMode = !isDualPageMode;
        FormatPrefs.setReadingMode(this, formatExt, isDualPageMode ? FormatPrefs.MODE_DUAL_PAGE : FormatPrefs.MODE_SCROLL);
        updateModeVisibility();
        paginateCurrentChapter(applyParagraphSpacing(rawChapterText));
        restoreAnchorCharIndex(anchor);
    }

    private void updateModeVisibility() {
        if (isPdf) {
            contentContainer.setPadding(0, 0, 0, 0);
            if (isDualPageMode) {
                scrollView.setVisibility(View.GONE);
                dualPageContainer.setVisibility(View.GONE);
                if (dualPdfContainer != null) dualPdfContainer.setVisibility(View.VISIBLE);
                pdfImageView.setVisibility(View.GONE);
                singleTextView.setVisibility(View.GONE);
            } else {
                scrollView.setVisibility(View.VISIBLE);
                dualPageContainer.setVisibility(View.GONE);
                if (dualPdfContainer != null) dualPdfContainer.setVisibility(View.GONE);
                pdfImageView.setVisibility(View.VISIBLE);
                singleTextView.setVisibility(View.GONE);
            }
        } else {
            int safeMargin = Math.max(28, pageMargin);
            contentContainer.setPadding(dp(safeMargin), dp(safeMargin), dp(safeMargin), dp(safeMargin + 32));
            if (isDualPageMode) {
                scrollView.setVisibility(View.GONE);
                dualPageContainer.setVisibility(View.VISIBLE);
                if (dualPdfContainer != null) dualPdfContainer.setVisibility(View.GONE);
                pdfImageView.setVisibility(View.GONE);
                singleTextView.setVisibility(View.GONE);
            } else {
                scrollView.setVisibility(View.VISIBLE);
                dualPageContainer.setVisibility(View.GONE);
                if (dualPdfContainer != null) dualPdfContainer.setVisibility(View.GONE);
                pdfImageView.setVisibility(View.GONE);
                singleTextView.setVisibility(View.VISIBLE);
            }
        }
        updateStatusBarVisibility();
    }

    private void applyTheme() {
        int bg = ThemeHelper.getBackgroundColor(currentThemeId);
        int text = ThemeHelper.getTextColor(currentThemeId);
        int toolbar = ThemeHelper.getToolbarColor(currentThemeId);

        getWindow().getDecorView().setBackgroundColor(bg);
        rootContainer.setBackgroundColor(bg);
        readingViewport.setBackgroundColor(bg);
        scrollView.setBackgroundColor(bg);
        contentContainer.setBackgroundColor(bg);
        dualPageContainer.setBackgroundColor(bg);
        if (dualPdfContainer != null) {
            dualPdfContainer.setBackgroundColor(bg);
        }

        singleTextView.setTextColor(text);
        if (isDualPageMode && rawChapterText != null) {
            paginateCurrentChapter(applyParagraphSpacing(rawChapterText));
            displayCurrentSpread();
        }

        topToolbar.setBackgroundColor(toolbar);
        for (int i = 0; i < topToolbar.getChildCount(); i++) {
            View v = topToolbar.getChildAt(i);
            if (v instanceof Button) {
                v.setBackground(ThemeHelper.createThemedButtonDrawable(currentThemeId, false, 8, this));
                ((Button) v).setTextColor(ThemeHelper.getButtonTextColor(currentThemeId, false));
            } else if (v instanceof TextView) {
                ((TextView) v).setTextColor(text);
            }
        }

        bottomToolbar.setBackgroundColor(toolbar);
        for (int i = 0; i < bottomToolbar.getChildCount(); i++) {
            View v = bottomToolbar.getChildAt(i);
            if (v instanceof Button) {
                v.setBackground(ThemeHelper.createThemedButtonDrawable(currentThemeId, false, 8, this));
                ((Button) v).setTextColor(ThemeHelper.getButtonTextColor(currentThemeId, false));
            }
        }

        if (secondaryScroll != null) {
            secondaryScroll.setBackgroundColor(toolbar);
            if (activeSubMenu != null) {
                showSubMenu(activeSubMenu);
            }
        }

        updateStatusBarGradient();
        statusChapterView.setTextColor(ThemeHelper.getStatusBarTextColor(currentThemeId));
        statusClockView.setTextColor(ThemeHelper.getStatusBarTextColor(currentThemeId));
        statusPageView.setTextColor(ThemeHelper.getStatusBarAccentColor(currentThemeId));
    }

    private void showTocDialog() {
        if (document == null) return;

        final List<BookDocument.TocItem> richToc = document.getToc();
        final boolean hasRichToc = (richToc != null && !richToc.isEmpty());
        final int itemCount = hasRichToc ? richToc.size() : totalChapters;

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(ThemeHelper.getDialogBackgroundColor(currentThemeId));
        root.setPadding(dp(24), dp(20), dp(24), dp(16));

        TextView titleView = new TextView(this);
        titleView.setText(AppText.get(R.string.toc_title) + " (" + AppText.format(R.string.toc_summary, itemCount) + ")");
        titleView.setTextSize(18);
        titleView.setTextColor(ThemeHelper.getDialogTitleColor(currentThemeId));
        titleView.getPaint().setFakeBoldText(true);
        titleView.setPadding(0, 0, 0, dp(12));
        root.addView(titleView);

        final ListView listView = new ListView(this);
        listView.setDividerHeight(dp(6));
        listView.setSelector(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        listView.setItemsCanFocus(true);
        listView.setDrawSelectorOnTop(false);

        final String[] fallbackTitles = hasRichToc ? null : new String[totalChapters];
        if (!hasRichToc) {
            for (int i = 0; i < totalChapters; i++) {
                fallbackTitles[i] = document.getChapterTitle(i);
            }
        }

        final AlertDialog[] dialogHolder = new AlertDialog[1];

        int initialFocusPos = currentChapter;
        if (hasRichToc) {
            if (currentTocIndex >= 0 && currentTocIndex < richToc.size() && richToc.get(currentTocIndex).chapterIndex == currentChapter) {
                initialFocusPos = currentTocIndex;
            } else {
                for (int i = 0; i < richToc.size(); i++) {
                    if (richToc.get(i).chapterIndex == currentChapter) {
                        initialFocusPos = i;
                        break;
                    }
                }
            }
        }
        final int activePosition = initialFocusPos;

        final BaseAdapter tocAdapter = new BaseAdapter() {
            @Override
            public int getCount() {
                return itemCount;
            }

            @Override
            public Object getItem(int position) {
                if (hasRichToc) return richToc.get(position).title;
                return fallbackTitles[position];
            }

            @Override
            public long getItemId(int position) {
                return position;
            }

            @Override
            public View getView(final int position, View convertView, ViewGroup parent) {
                LinearLayout item = new LinearLayout(ReaderActivity.this);
                item.setOrientation(LinearLayout.HORIZONTAL);
                item.setGravity(Gravity.CENTER_VERTICAL);
                item.setFocusable(true);

                final int chapIndex;
                final String anchorTarget;
                final String displayTitle;
                final int level;

                if (hasRichToc) {
                    BookDocument.TocItem ti = richToc.get(position);
                    chapIndex = ti.chapterIndex;
                    anchorTarget = ti.title;
                    displayTitle = ti.title;
                    level = ti.level;
                } else {
                    chapIndex = position;
                    anchorTarget = null;
                    displayTitle = fallbackTitles[position];
                    level = 0;
                }

                int leftPad = dp(16 + level * 20);
                item.setPadding(leftPad, dp(8), dp(16), dp(8));

                boolean isCurrent = (position == activePosition);
                item.setBackground(ThemeHelper.createThemedButtonDrawable(
                        currentThemeId, isCurrent, 8, ReaderActivity.this));

                if (level == 0) {
                    TextView tvNum = new TextView(ReaderActivity.this);
                    tvNum.setText(String.format(Locale.US, "%02d", position + 1));
                    tvNum.setTextSize(13);
                    tvNum.setTextColor(isCurrent ? Color.WHITE : ThemeHelper.getStatusBarAccentColor(currentThemeId));
                    tvNum.getPaint().setFakeBoldText(true);
                    tvNum.setPadding(0, 0, dp(12), 0);
                    item.addView(tvNum);
                } else {
                    TextView tvBullet = new TextView(ReaderActivity.this);
                    tvBullet.setText("•");
                    tvBullet.setTextSize(14);
                    tvBullet.setTextColor(isCurrent ? Color.WHITE : ThemeHelper.getStatusBarAccentColor(currentThemeId));
                    tvBullet.setPadding(0, 0, dp(8), 0);
                    item.addView(tvBullet);
                }

                TextView tvTitle = new TextView(ReaderActivity.this);
                tvTitle.setText(displayTitle);
                tvTitle.setTextSize(level == 0 ? 14 : 13);
                tvTitle.setTextColor(isCurrent ? Color.WHITE : ThemeHelper.getTextColor(currentThemeId));
                if (level == 0) {
                    tvTitle.getPaint().setFakeBoldText(true);
                }
                tvTitle.setSingleLine(true);
                item.addView(tvTitle, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));

                final Runnable onSelect = new Runnable() {
                    @Override
                    public void run() {
                        currentTocIndex = position;
                        currentChapter = chapIndex;
                        pendingAnchor = anchorTarget;
                        if (isPdf && isDualPageMode) {
                            currentSpread = currentChapter / 2;
                        } else {
                            currentSpread = 0;
                        }
                        loadCurrentChapter();
                        if (dialogHolder[0] != null) {
                            dialogHolder[0].dismiss();
                        }
                        hideOverlays();
                    }
                };

                item.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        onSelect.run();
                    }
                });

                item.setOnKeyListener(new View.OnKeyListener() {
                    @Override
                    public boolean onKey(View v, int keyCode, KeyEvent event) {
                        if (event.getAction() == KeyEvent.ACTION_DOWN) {
                            if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
                                onSelect.run();
                                return true;
                            }
                            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_PAGE_UP) {
                                final int target = Math.max(0, position - 7);
                                listView.setSelection(target);
                                listView.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        int first = listView.getFirstVisiblePosition();
                                        int last = listView.getLastVisiblePosition();
                                        if (target >= first && target <= last) {
                                            View child = listView.getChildAt(target - first);
                                            if (child != null) child.requestFocus();
                                        }
                                    }
                                });
                                return true;
                            }
                            if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT || keyCode == KeyEvent.KEYCODE_PAGE_DOWN) {
                                final int target = Math.min(itemCount - 1, position + 7);
                                listView.setSelection(target);
                                listView.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        int first = listView.getFirstVisiblePosition();
                                        int last = listView.getLastVisiblePosition();
                                        if (target >= first && target <= last) {
                                            View child = listView.getChildAt(target - first);
                                            if (child != null) child.requestFocus();
                                        }
                                    }
                                });
                                return true;
                            }
                        }
                        return false;
                    }
                });

                return item;
            }
        };

        listView.setAdapter(tocAdapter);

        listView.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_PAGE_UP) {
                        int cur = listView.getSelectedItemPosition();
                        if (cur < 0) cur = listView.getFirstVisiblePosition();
                        int target = Math.max(0, cur - 7);
                        listView.setSelection(target);
                        return true;
                    }
                    if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT || keyCode == KeyEvent.KEYCODE_PAGE_DOWN) {
                        int cur = listView.getSelectedItemPosition();
                        if (cur < 0) cur = listView.getFirstVisiblePosition();
                        int target = Math.min(itemCount - 1, cur + 7);
                        listView.setSelection(target);
                        return true;
                    }
                }
                return false;
            }
        });

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setView(root);
        dialogHolder[0] = builder.create();

        root.addView(listView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(460)));

        dialogHolder[0].show();

        final int targetFocus = activePosition;

        listView.post(new Runnable() {
            @Override
            public void run() {
                listView.setSelection(targetFocus);
                listView.requestFocus();
                listView.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        int first = listView.getFirstVisiblePosition();
                        int last = listView.getLastVisiblePosition();
                        if (targetFocus >= first && targetFocus <= last) {
                            View child = listView.getChildAt(targetFocus - first);
                            if (child != null) {
                                child.requestFocus();
                            }
                        }
                    }
                }, 60);
            }
        });
    }

    private void nextChapter() {
        if (isPdf && isDualPageMode) {
            nextSpreadOrChapter();
            return;
        }
        if (currentChapter < totalChapters - 1) {
            currentChapter++;
            currentSpread = 0;
            loadCurrentChapter();
        } else {
            Toast.makeText(this, AppText.get(R.string.already_at_book_end), Toast.LENGTH_SHORT).show();
        }
    }

    private void prevChapter() {
        if (isPdf && isDualPageMode) {
            prevSpreadOrChapter();
            return;
        }
        if (currentChapter > 0) {
            currentChapter--;
            currentSpread = -1;
            loadCurrentChapter();
        } else {
            Toast.makeText(this, AppText.get(R.string.already_at_book_start), Toast.LENGTH_SHORT).show();
        }
    }

    private void nextSpreadOrChapter() {
        if (isPdf) {
            int totalSpreads = Math.max(1, (totalChapters + 1) / 2);
            if (currentSpread < totalSpreads - 1) {
                currentSpread++;
                currentChapter = currentSpread * 2;
                loadPdfSpread(currentSpread);
            } else {
                Toast.makeText(this, AppText.get(R.string.already_at_book_end), Toast.LENGTH_SHORT).show();
            }
            return;
        }

        int totalSpreads = Math.max(1, (dualPages.size() + 1) / 2);
        if (currentSpread < totalSpreads - 1) {
            currentSpread++;
            displayCurrentSpread();
        } else {
            if (currentChapter < totalChapters - 1) {
                currentChapter++;
                currentSpread = 0;
                loadCurrentChapter();
            } else {
                Toast.makeText(this, AppText.get(R.string.already_at_book_end), Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void prevSpreadOrChapter() {
        if (isPdf) {
            if (currentSpread > 0) {
                currentSpread--;
                currentChapter = currentSpread * 2;
                loadPdfSpread(currentSpread);
            } else {
                Toast.makeText(this, AppText.get(R.string.already_at_book_start), Toast.LENGTH_SHORT).show();
            }
            return;
        }

        if (currentSpread > 0) {
            currentSpread--;
            displayCurrentSpread();
        } else {
            if (currentChapter > 0) {
                currentChapter--;
                currentSpread = -1;
                loadCurrentChapter();
            } else {
                Toast.makeText(this, AppText.get(R.string.already_at_book_start), Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void scrollNextFullPage() {
        if (singleTextView == null || scrollView == null) return;
        Layout layout = singleTextView.getLayout();
        if (layout == null) return;

        int currentY = scrollView.getScrollY();
        int maxScroll = Math.max(1, contentContainer.getHeight() - scrollView.getHeight());

        if (currentY >= maxScroll - dp(10)) {
            nextChapter();
            return;
        }

        int visibleH = scrollView.getHeight() - scrollView.getPaddingTop() - scrollView.getPaddingBottom();
        if (visibleH <= 0) visibleH = dp(400);

        int topLine = layout.getLineForVertical(currentY);
        int firstLine = layout.getLineTop(topLine) >= currentY ? topLine : Math.min(layout.getLineCount() - 1, topLine + 1);
        int startTopY = layout.getLineTop(firstLine);

        int lineEnd = firstLine;
        while (lineEnd < layout.getLineCount() && (layout.getLineBottom(lineEnd) - startTopY) <= visibleH) {
            lineEnd++;
        }

        if (lineEnd <= firstLine) {
            lineEnd = firstLine + 1;
        }

        if (lineEnd >= layout.getLineCount()) {
            if (currentY >= maxScroll - dp(10)) {
                nextChapter();
            } else {
                scrollView.smoothScrollTo(0, maxScroll);
            }
            return;
        }

        int targetY = layout.getLineTop(lineEnd);
        scrollView.smoothScrollTo(0, Math.min(maxScroll, targetY));
    }

    private void scrollPrevFullPage() {
        if (singleTextView == null || scrollView == null) return;
        Layout layout = singleTextView.getLayout();
        if (layout == null) return;

        int currentY = scrollView.getScrollY();
        if (currentY <= dp(10)) {
            prevChapter();
            return;
        }

        int visibleH = scrollView.getHeight() - scrollView.getPaddingTop() - scrollView.getPaddingBottom();
        if (visibleH <= 0) visibleH = dp(400);

        int topLine = layout.getLineForVertical(currentY);
        int firstLine = layout.getLineTop(topLine) >= currentY ? topLine : Math.min(layout.getLineCount() - 1, topLine + 1);

        int line = firstLine;
        while (line > 0 && (layout.getLineTop(firstLine) - layout.getLineTop(line - 1)) <= visibleH) {
            line--;
        }

        int targetY = layout.getLineTop(line);
        scrollView.smoothScrollTo(0, Math.max(0, targetY));
    }

    private void toggleOverlays() {
        isOverlayVisible = !isOverlayVisible;
        if (isOverlayVisible) {
            topToolbar.setVisibility(View.VISIBLE);
            bottomDock.setVisibility(View.VISIBLE);
            hideSubMenu();
            bottomToolbar.getChildAt(0).requestFocus();
        } else {
            hideOverlays();
        }
    }

    private void hideOverlays() {
        isOverlayVisible = false;
        topToolbar.setVisibility(View.GONE);
        bottomDock.setVisibility(View.GONE);
        hideSubMenu();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int keyCode = event.getKeyCode();

        if (isOverlayVisible) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                if (keyCode == KeyEvent.KEYCODE_BACK) {
                    if (secondaryScroll != null && secondaryScroll.getVisibility() == View.VISIBLE) {
                        hideSubMenu();
                        return true;
                    }
                    hideOverlays();
                    return true;
                }
            }
            return super.dispatchKeyEvent(event);
        }

        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
            if (event.getAction() == KeyEvent.ACTION_UP) {
                toggleOverlays();
            }
            return true;
        }

        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            switch (keyCode) {
                case KeyEvent.KEYCODE_DPAD_UP:
                    if (isDualPageMode) {
                        prevSpreadOrChapter();
                    } else if (isPdf) {
                        scrollView.smoothScrollBy(0, -dp(80));
                    } else {
                        scrollView.smoothScrollBy(0, -dp(60));
                    }
                    return true;

                case KeyEvent.KEYCODE_DPAD_DOWN:
                    if (isDualPageMode) {
                        nextSpreadOrChapter();
                    } else if (isPdf) {
                        scrollView.smoothScrollBy(0, dp(80));
                    } else {
                        scrollView.smoothScrollBy(0, dp(60));
                    }
                    return true;

                case KeyEvent.KEYCODE_DPAD_LEFT:
                case KeyEvent.KEYCODE_PAGE_UP:
                    if (isDualPageMode) {
                        prevSpreadOrChapter();
                    } else if (isPdf) {
                        prevChapter();
                    } else {
                        scrollPrevFullPage();
                    }
                    return true;

                case KeyEvent.KEYCODE_DPAD_RIGHT:
                case KeyEvent.KEYCODE_PAGE_DOWN:
                    if (isDualPageMode) {
                        nextSpreadOrChapter();
                    } else if (isPdf) {
                        nextChapter();
                    } else {
                        scrollNextFullPage();
                    }
                    return true;

                case KeyEvent.KEYCODE_MENU:
                case KeyEvent.KEYCODE_SETTINGS:
                    toggleOverlays();
                    return true;

                case KeyEvent.KEYCODE_BACK:
                    long now = System.currentTimeMillis();
                    if (now - lastBackPressTime < 2000) {
                        finish();
                    } else {
                        lastBackPressTime = now;
                        Toast.makeText(this, AppText.get(R.string.press_back_again_to_exit), Toast.LENGTH_SHORT).show();
                    }
                    return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mainHandler.removeCallbacks(clockRunnable);
        mainHandler.removeCallbacks(saveProgressRunnable);
        if (currentPdfBitmap != null && !currentPdfBitmap.isRecycled()) {
            currentPdfBitmap.recycle();
            currentPdfBitmap = null;
        }
        if (leftPdfBitmap != null && !leftPdfBitmap.isRecycled()) {
            leftPdfBitmap.recycle();
            leftPdfBitmap = null;
        }
        if (rightPdfBitmap != null && !rightPdfBitmap.isRecycled()) {
            rightPdfBitmap.recycle();
            rightPdfBitmap = null;
        }
        if (document != null) {
            document.close();
            document = null;
        }
    }

    private int dp(float dp) {
        return ThemeHelper.dpToPx(dp, this);
    }
}
