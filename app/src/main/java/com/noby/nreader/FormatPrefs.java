package com.noby.nreader;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.os.Build;

import com.noby.nreader.model.BookStore;

import java.io.File;
import java.util.Locale;

public final class FormatPrefs {
    public static final String MODE_SCROLL = "scroll";
    public static final String MODE_DUAL_PAGE = "dual_page";

    public static final String GLOBAL_FORMAT = "global";
    public static final int USE_DEFAULT_INT = -1;
    public static final String USE_DEFAULT_STR = "default";

    public static final int DEFAULT_FONT_SIZE = 20;
    public static final int MIN_FONT_SIZE = 12;
    public static final int MAX_FONT_SIZE = 48;

    public static final int DEFAULT_LINE_SPACING = 4;
    public static final int MIN_LINE_SPACING = 0;
    public static final int MAX_LINE_SPACING = 24;

    public static final int FIXED_PARAGRAPH_SPACING = 8;
    public static final int FIXED_PAGE_MARGIN = 36;

    public static final int FONT_FAMILY_DEFAULT = 0;
    public static final int FONT_FAMILY_SERIF = 1;
    public static final int FONT_FAMILY_MONO = 2;
    public static final int FONT_FAMILY_SANS_MEDIUM = 3;
    public static final int FONT_FAMILY_COUNT = 4;

    public static final int DEFAULT_THEME_ID = 0;

    private FormatPrefs() {
    }

    public static void checkVersionUpgrade(Context context) {
        if (context == null) return;
        SharedPreferences global = context.getSharedPreferences("format_meta", Context.MODE_PRIVATE);
        int v = global.getInt("version", 1);
        if (v < 4) {
            BookStore.clearCoversCache(context);
            for (String f : new String[]{"txt", "epub", "mobi", "pdf"}) {
                SharedPreferences prefs = getPrefs(context, f);
                int savedSize = prefs.getInt("font_size", 0);
                if (savedSize == 26 || savedSize > MAX_FONT_SIZE) {
                    prefs.edit().putInt("font_size", DEFAULT_FONT_SIZE).apply();
                }
                int savedSpacing = prefs.getInt("line_spacing", -1);
                if (savedSpacing == 8 || savedSpacing > MAX_LINE_SPACING) {
                    prefs.edit().putInt("line_spacing", DEFAULT_LINE_SPACING).apply();
                }
            }
            global.edit().putInt("version", 4).apply();
        }
    }

    public static String getFormatExtension(File file) {
        if (file == null) {
            return "txt";
        }
        return getFormatExtension(file.getName());
    }

    public static String getFormatExtension(String fileName) {
        if (fileName == null) {
            return "txt";
        }
        String lower = fileName.toLowerCase(Locale.US);
        if (lower.endsWith(".epub")) {
            return "epub";
        } else if (lower.endsWith(".mobi") || lower.endsWith(".azw") || lower.endsWith(".azw3")) {
            return "mobi";
        } else if (lower.endsWith(".pdf")) {
            return "pdf";
        } else {
            return "txt";
        }
    }

    public static SharedPreferences getPrefs(Context context, String formatExt) {
        String safeExt = formatExt != null ? formatExt.toLowerCase(Locale.US) : "txt";
        return context.getSharedPreferences("format_" + safeExt, Context.MODE_PRIVATE);
    }

    public static int getFontSize(Context context, String format) {
        String safe = normalizeFormat(format);
        if (!GLOBAL_FORMAT.equals(safe)) {
            int custom = getPrefs(context, safe).getInt("font_size", USE_DEFAULT_INT);
            if (custom != USE_DEFAULT_INT) {
                return custom;
            }
        }
        return getPrefs(context, GLOBAL_FORMAT).getInt("font_size", DEFAULT_FONT_SIZE);
    }

    public static int getRawFontSize(Context context, String format) {
        String safe = normalizeFormat(format);
        if (GLOBAL_FORMAT.equals(safe)) {
            return getFontSize(context, GLOBAL_FORMAT);
        }
        return getPrefs(context, safe).getInt("font_size", USE_DEFAULT_INT);
    }

    public static void setFontSize(Context context, String format, int fontSize) {
        String safe = normalizeFormat(format);
        if (fontSize == USE_DEFAULT_INT && !GLOBAL_FORMAT.equals(safe)) {
            getPrefs(context, safe).edit().putInt("font_size", USE_DEFAULT_INT).apply();
        } else {
            int clamped = Math.max(MIN_FONT_SIZE, Math.min(MAX_FONT_SIZE, fontSize));
            getPrefs(context, safe).edit().putInt("font_size", clamped).apply();
        }
    }

    public static int getLineSpacing(Context context, String format) {
        String safe = normalizeFormat(format);
        if (!GLOBAL_FORMAT.equals(safe)) {
            int custom = getPrefs(context, safe).getInt("line_spacing", USE_DEFAULT_INT);
            if (custom != USE_DEFAULT_INT) {
                return custom;
            }
        }
        return getPrefs(context, GLOBAL_FORMAT).getInt("line_spacing", DEFAULT_LINE_SPACING);
    }

    public static int getRawLineSpacing(Context context, String format) {
        String safe = normalizeFormat(format);
        if (GLOBAL_FORMAT.equals(safe)) {
            return getLineSpacing(context, GLOBAL_FORMAT);
        }
        return getPrefs(context, safe).getInt("line_spacing", USE_DEFAULT_INT);
    }

    public static void setLineSpacing(Context context, String format, int spacing) {
        String safe = normalizeFormat(format);
        if (spacing == USE_DEFAULT_INT && !GLOBAL_FORMAT.equals(safe)) {
            getPrefs(context, safe).edit().putInt("line_spacing", USE_DEFAULT_INT).apply();
        } else {
            int clamped = Math.max(MIN_LINE_SPACING, Math.min(MAX_LINE_SPACING, spacing));
            getPrefs(context, safe).edit().putInt("line_spacing", clamped).apply();
        }
    }

    public static int getParagraphSpacing(Context context, String format) {
        String safe = normalizeFormat(format);
        if (!GLOBAL_FORMAT.equals(safe)) {
            int custom = getPrefs(context, safe).getInt("paragraph_spacing", USE_DEFAULT_INT);
            if (custom != USE_DEFAULT_INT) {
                return custom;
            }
        }
        return getPrefs(context, GLOBAL_FORMAT).getInt("paragraph_spacing", FIXED_PARAGRAPH_SPACING);
    }

    public static int getRawParagraphSpacing(Context context, String format) {
        String safe = normalizeFormat(format);
        if (GLOBAL_FORMAT.equals(safe)) {
            return getParagraphSpacing(context, GLOBAL_FORMAT);
        }
        return getPrefs(context, safe).getInt("paragraph_spacing", USE_DEFAULT_INT);
    }

    public static void setParagraphSpacing(Context context, String format, int spacing) {
        String safe = normalizeFormat(format);
        if (spacing == USE_DEFAULT_INT && !GLOBAL_FORMAT.equals(safe)) {
            getPrefs(context, safe).edit().putInt("paragraph_spacing", USE_DEFAULT_INT).apply();
        } else {
            getPrefs(context, safe).edit().putInt("paragraph_spacing", spacing).apply();
        }
    }

    public static int getPageMargin(Context context, String format) {
        String safe = normalizeFormat(format);
        if (!GLOBAL_FORMAT.equals(safe)) {
            int custom = getPrefs(context, safe).getInt("page_margin", USE_DEFAULT_INT);
            if (custom != USE_DEFAULT_INT) {
                return custom;
            }
        }
        return getPrefs(context, GLOBAL_FORMAT).getInt("page_margin", FIXED_PAGE_MARGIN);
    }

    public static int getRawPageMargin(Context context, String format) {
        String safe = normalizeFormat(format);
        if (GLOBAL_FORMAT.equals(safe)) {
            return getPageMargin(context, GLOBAL_FORMAT);
        }
        return getPrefs(context, safe).getInt("page_margin", USE_DEFAULT_INT);
    }

    public static void setPageMargin(Context context, String format, int margin) {
        String safe = normalizeFormat(format);
        if (margin == USE_DEFAULT_INT && !GLOBAL_FORMAT.equals(safe)) {
            getPrefs(context, safe).edit().putInt("page_margin", USE_DEFAULT_INT).apply();
        } else {
            getPrefs(context, safe).edit().putInt("page_margin", margin).apply();
        }
    }

    public static int getFontFamily(Context context, String format) {
        String safe = normalizeFormat(format);
        if (!GLOBAL_FORMAT.equals(safe)) {
            int custom = getPrefs(context, safe).getInt("font_family", USE_DEFAULT_INT);
            if (custom != USE_DEFAULT_INT) {
                return custom;
            }
        }
        return getPrefs(context, GLOBAL_FORMAT).getInt("font_family", FONT_FAMILY_DEFAULT);
    }

    public static int getRawFontFamily(Context context, String format) {
        String safe = normalizeFormat(format);
        if (GLOBAL_FORMAT.equals(safe)) {
            return getFontFamily(context, GLOBAL_FORMAT);
        }
        return getPrefs(context, safe).getInt("font_family", USE_DEFAULT_INT);
    }

    public static void setFontFamily(Context context, String format, int fontFamily) {
        String safe = normalizeFormat(format);
        if (fontFamily == USE_DEFAULT_INT && !GLOBAL_FORMAT.equals(safe)) {
            getPrefs(context, safe).edit().putInt("font_family", USE_DEFAULT_INT).apply();
        } else {
            int clamped = (fontFamily >= 0 && fontFamily < FONT_FAMILY_COUNT) ? fontFamily : 0;
            getPrefs(context, safe).edit().putInt("font_family", clamped).apply();
        }
    }

    public static Typeface getTypeface(int fontFamily) {
        switch (fontFamily) {
            case FONT_FAMILY_SERIF:
                return Typeface.SERIF;
            case FONT_FAMILY_MONO:
                return Typeface.MONOSPACE;
            case FONT_FAMILY_SANS_MEDIUM:
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    return Typeface.create("sans-serif-medium", Typeface.NORMAL);
                }
                return Typeface.DEFAULT_BOLD;
            case FONT_FAMILY_DEFAULT:
            default:
                return Typeface.DEFAULT;
        }
    }

    public static String getFontFamilyName(int fontFamily) {
        switch (fontFamily) {
            case FONT_FAMILY_SERIF:
                return AppText.get(R.string.font_serif);
            case FONT_FAMILY_MONO:
                return AppText.get(R.string.font_mono);
            case FONT_FAMILY_SANS_MEDIUM:
                return AppText.get(R.string.font_sans_medium);
            case FONT_FAMILY_DEFAULT:
            default:
                return AppText.get(R.string.font_default);
        }
    }

    public static int getThemeId(Context context, String format) {
        String safe = normalizeFormat(format);
        if (!GLOBAL_FORMAT.equals(safe)) {
            int custom = getPrefs(context, safe).getInt("theme_id", USE_DEFAULT_INT);
            if (custom != USE_DEFAULT_INT) {
                return custom;
            }
        }
        return getPrefs(context, GLOBAL_FORMAT).getInt("theme_id", DEFAULT_THEME_ID);
    }

    public static int getRawThemeId(Context context, String format) {
        String safe = normalizeFormat(format);
        if (GLOBAL_FORMAT.equals(safe)) {
            return getThemeId(context, GLOBAL_FORMAT);
        }
        return getPrefs(context, safe).getInt("theme_id", USE_DEFAULT_INT);
    }

    public static void setThemeId(Context context, String format, int themeId) {
        String safe = normalizeFormat(format);
        if (themeId == USE_DEFAULT_INT && !GLOBAL_FORMAT.equals(safe)) {
            getPrefs(context, safe).edit().putInt("theme_id", USE_DEFAULT_INT).apply();
        } else {
            int clamped = (themeId >= 0 && themeId <= 7) ? themeId : 0;
            getPrefs(context, safe).edit().putInt("theme_id", clamped).apply();
        }
    }

    public static String getReadingMode(Context context, String format) {
        String safe = normalizeFormat(format);
        if (!GLOBAL_FORMAT.equals(safe)) {
            String custom = getPrefs(context, safe).getString("reading_mode", USE_DEFAULT_STR);
            if (!USE_DEFAULT_STR.equals(custom)) {
                return custom;
            }
            if ("pdf".equals(safe)) {
                return MODE_DUAL_PAGE;
            }
        }
        return getPrefs(context, GLOBAL_FORMAT).getString("reading_mode", MODE_SCROLL);
    }

    public static String getRawReadingMode(Context context, String format) {
        String safe = normalizeFormat(format);
        if (GLOBAL_FORMAT.equals(safe)) {
            return getReadingMode(context, GLOBAL_FORMAT);
        }
        return getPrefs(context, safe).getString("reading_mode", USE_DEFAULT_STR);
    }

    public static void setReadingMode(Context context, String format, String mode) {
        String safe = normalizeFormat(format);
        if (USE_DEFAULT_STR.equals(mode) && !GLOBAL_FORMAT.equals(safe)) {
            getPrefs(context, safe).edit().putString("reading_mode", USE_DEFAULT_STR).apply();
        } else {
            getPrefs(context, safe).edit().putString("reading_mode", mode).apply();
        }
    }

    public static boolean getShowTime(Context context) {
        return context.getSharedPreferences("app_global", Context.MODE_PRIVATE).getBoolean("show_clock", true);
    }

    public static void setShowTime(Context context, boolean showTime) {
        context.getSharedPreferences("app_global", Context.MODE_PRIVATE).edit().putBoolean("show_clock", showTime).apply();
    }

    public static boolean getShowStatusBar(Context context) {
        return context.getSharedPreferences("app_global", Context.MODE_PRIVATE).getBoolean("show_status_bar", true);
    }

    public static void setShowStatusBar(Context context, boolean showStatusBar) {
        context.getSharedPreferences("app_global", Context.MODE_PRIVATE).edit().putBoolean("show_status_bar", showStatusBar).apply();
    }

    private static String normalizeFormat(String format) {
        if (format == null) return GLOBAL_FORMAT;
        String lower = format.toLowerCase(Locale.US);
        if ("all".equals(lower) || "global".equals(lower)) {
            return GLOBAL_FORMAT;
        }
        return lower;
    }
}
