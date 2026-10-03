package com.noby.nreader;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.util.TypedValue;

public final class ThemeHelper {
    // Reordered per user request: Black, White, Teal, Beige, Gray, Light Gray, Dark Gray, Brown
    public static final int THEME_BLACK = 0;
    public static final int THEME_WHITE = 1;
    public static final int THEME_TEAL = 2;
    public static final int THEME_BEIGE = 3;
    public static final int THEME_GRAY = 4;
    public static final int THEME_LIGHT_GRAY = 5;
    public static final int THEME_DARK_GRAY = 6;
    public static final int THEME_BROWN = 7;

    public static final int THEME_COUNT = 8;

    // Background colors for the 8 themes
    private static final int[] BG_COLORS = {
            Color.rgb(0, 0, 0),        // 0: Black (OLED Black)
            Color.rgb(247, 247, 247),  // 1: White
            Color.rgb(16, 20, 24),     // 2: Teal
            Color.rgb(245, 239, 220),  // 3: Beige (Warm Parchment)
            Color.rgb(26, 26, 26),     // 4: Gray
            Color.rgb(237, 237, 236),  // 5: Light Gray
            Color.rgb(42, 42, 46),     // 6: Dark Gray
            Color.rgb(43, 29, 14)      // 7: Brown (Dark Sepia)
    };

    // Primary Text colors
    private static final int[] TEXT_COLORS = {
            Color.rgb(224, 224, 224),  // 0: Black
            Color.rgb(26, 26, 26),     // 1: White
            Color.rgb(232, 245, 245),  // 2: Teal
            Color.rgb(35, 32, 28),     // 3: Beige
            Color.rgb(208, 208, 208),  // 4: Gray
            Color.rgb(42, 42, 42),     // 5: Light Gray
            Color.rgb(207, 207, 207),  // 6: Dark Gray
            Color.rgb(232, 213, 176)   // 7: Brown
    };

    // Secondary / Hint Text colors
    private static final int[] SECONDARY_COLORS = {
            Color.rgb(136, 136, 136),  // 0: Black
            Color.rgb(102, 102, 102),  // 1: White
            Color.rgb(128, 203, 196),  // 2: Teal
            Color.rgb(107, 94, 78),    // 3: Beige
            Color.rgb(144, 144, 144),  // 4: Gray
            Color.rgb(102, 102, 102),  // 5: Light Gray
            Color.rgb(136, 136, 136),  // 6: Dark Gray
            Color.rgb(160, 128, 96)    // 7: Brown
    };

    // Toolbar / Panel Overlay colors
    private static final int[] TOOLBAR_COLORS = {
            Color.argb(235, 12, 12, 12),   // 0: Black
            Color.argb(235, 240, 240, 240),// 1: White
            Color.argb(235, 12, 16, 20),   // 2: Teal
            Color.argb(235, 238, 230, 210),// 3: Beige
            Color.argb(235, 20, 20, 20),   // 4: Gray
            Color.argb(235, 228, 228, 226),// 5: Light Gray
            Color.argb(235, 34, 34, 38),   // 6: Dark Gray
            Color.argb(235, 32, 20, 10)    // 7: Brown
    };

    public static final int FOCUS_COLOR = Color.rgb(255, 190, 75); // Amber Gold focus outline for TV D-Pad hover
    public static final int SELECTED_COLOR = Color.rgb(2, 132, 199); // Ocean Cyan for selected/active tab
    public static final int SELECTED_BORDER = Color.rgb(56, 189, 248);
    public static final int ACCENT_CYAN = Color.rgb(56, 189, 248);

    private ThemeHelper() {
    }

    public static int getBackgroundColor(int themeId) {
        int safeId = (themeId >= 0 && themeId < THEME_COUNT) ? themeId : 0;
        return BG_COLORS[safeId];
    }

    public static int getTextColor(int themeId) {
        int safeId = (themeId >= 0 && themeId < THEME_COUNT) ? themeId : 0;
        return TEXT_COLORS[safeId];
    }

    public static int getSecondaryTextColor(int themeId) {
        int safeId = (themeId >= 0 && themeId < THEME_COUNT) ? themeId : 0;
        return SECONDARY_COLORS[safeId];
    }

    public static int getToolbarColor(int themeId) {
        int safeId = (themeId >= 0 && themeId < THEME_COUNT) ? themeId : 0;
        return TOOLBAR_COLORS[safeId];
    }

    public static boolean isDarkTheme(int themeId) {
        return themeId != THEME_WHITE && themeId != THEME_BEIGE && themeId != THEME_LIGHT_GRAY;
    }

    public static int getButtonBackgroundColor(int themeId) {
        if (!isDarkTheme(themeId)) {
            if (themeId == THEME_BEIGE) {
                return Color.rgb(232, 222, 202);
            }
            return Color.rgb(226, 232, 240);
        }
        return Color.rgb(30, 41, 59);
    }

    public static int getButtonTextColor(int themeId, boolean isSelected) {
        if (isSelected) {
            return Color.WHITE;
        }
        if (!isDarkTheme(themeId)) {
            return Color.rgb(30, 41, 59);
        }
        return Color.WHITE;
    }

    public static int getStatusBarBackgroundColor(int themeId) {
        if (!isDarkTheme(themeId)) {
            if (themeId == THEME_BEIGE) {
                return Color.argb(235, 238, 230, 210);
            }
            return Color.argb(235, 238, 242, 246);
        }
        if (themeId == THEME_BLACK) {
            return Color.argb(235, 12, 12, 12);
        }
        return Color.argb(235, 15, 23, 42);
    }

    public static int getStatusBarTextColor(int themeId) {
        if (!isDarkTheme(themeId)) {
            return Color.rgb(71, 85, 105);
        }
        return Color.rgb(148, 163, 184);
    }

    public static int getStatusBarAccentColor(int themeId) {
        if (!isDarkTheme(themeId)) {
            return Color.rgb(2, 132, 199);
        }
        return Color.rgb(56, 189, 248);
    }

    public static int getDialogBackgroundColor(int themeId) {
        if (!isDarkTheme(themeId)) {
            if (themeId == THEME_BEIGE) {
                return Color.rgb(245, 239, 220);
            }
            return Color.rgb(250, 250, 252);
        }
        if (themeId == THEME_BLACK) {
            return Color.rgb(12, 12, 12);
        }
        return Color.rgb(15, 23, 42);
    }

    public static int getDialogTitleColor(int themeId) {
        if (!isDarkTheme(themeId)) {
            return Color.rgb(15, 23, 42);
        }
        return Color.WHITE;
    }

    public static StateListDrawable createThemedButtonDrawable(int themeId, boolean isSelected, int cornerDp, Context context) {
        int cornerPx = dpToPx(cornerDp, context);
        int borderPx = dpToPx(3, context);

        StateListDrawable states = new StateListDrawable();

        int baseColor = isSelected ? SELECTED_COLOR : getButtonBackgroundColor(themeId);

        GradientDrawable focused = new GradientDrawable();
        focused.setColor(baseColor);
        focused.setCornerRadius(cornerPx);
        focused.setStroke(borderPx, FOCUS_COLOR);
        states.addState(new int[]{android.R.attr.state_focused}, focused);

        GradientDrawable pressed = new GradientDrawable();
        pressed.setColor(brighten(baseColor, 0.2f));
        pressed.setCornerRadius(cornerPx);
        pressed.setStroke(borderPx, Color.WHITE);
        states.addState(new int[]{android.R.attr.state_pressed}, pressed);

        if (isSelected) {
            GradientDrawable sel = new GradientDrawable();
            sel.setColor(SELECTED_COLOR);
            sel.setCornerRadius(cornerPx);
            sel.setStroke(borderPx, SELECTED_BORDER);
            states.addState(new int[]{}, sel);
            return states;
        }

        GradientDrawable normal = new GradientDrawable();
        normal.setColor(baseColor);
        normal.setCornerRadius(cornerPx);
        int normalBorder = isDarkTheme(themeId)
                ? Color.argb(40, 255, 255, 255)
                : Color.argb(40, 0, 0, 0);
        normal.setStroke(dpToPx(1, context), normalBorder);
        states.addState(new int[]{}, normal);

        return states;
    }

    public static String getThemeName(int themeId) {
        switch (themeId) {
            case THEME_BLACK:
                return AppText.get(R.string.theme_black);
            case THEME_WHITE:
                return AppText.get(R.string.theme_white);
            case THEME_TEAL:
                return AppText.get(R.string.theme_teal);
            case THEME_BEIGE:
                return AppText.get(R.string.theme_beige);
            case THEME_GRAY:
                return AppText.get(R.string.theme_gray);
            case THEME_LIGHT_GRAY:
                return AppText.get(R.string.theme_light_gray);
            case THEME_DARK_GRAY:
                return AppText.get(R.string.theme_dark_gray);
            case THEME_BROWN:
                return AppText.get(R.string.theme_brown);
            default:
                return AppText.get(R.string.theme_black);
        }
    }

    public static StateListDrawable createButtonDrawable(int baseColor, int cornerDp, Context context) {
        int cornerPx = dpToPx(cornerDp, context);
        int borderPx = dpToPx(3, context);

        StateListDrawable states = new StateListDrawable();

        // Focused State (TV remote active hover cursor: AMBER GOLD)
        GradientDrawable focused = new GradientDrawable();
        focused.setColor(baseColor);
        focused.setCornerRadius(cornerPx);
        focused.setStroke(borderPx, FOCUS_COLOR);
        states.addState(new int[]{android.R.attr.state_focused}, focused);

        // Pressed State
        GradientDrawable pressed = new GradientDrawable();
        pressed.setColor(brighten(baseColor, 0.2f));
        pressed.setCornerRadius(cornerPx);
        pressed.setStroke(borderPx, Color.WHITE);
        states.addState(new int[]{android.R.attr.state_pressed}, pressed);

        // Selected State (Active confirmed tab: SOLID OCEAN CYAN, distinct from Amber)
        GradientDrawable selected = new GradientDrawable();
        selected.setColor(SELECTED_COLOR);
        selected.setCornerRadius(cornerPx);
        selected.setStroke(borderPx, SELECTED_BORDER);
        states.addState(new int[]{android.R.attr.state_selected}, selected);

        // Default Normal State
        GradientDrawable normal = new GradientDrawable();
        normal.setColor(baseColor);
        normal.setCornerRadius(cornerPx);
        normal.setStroke(dpToPx(1, context), Color.argb(60, 255, 255, 255));
        states.addState(new int[]{}, normal);

        return states;
    }

    public static StateListDrawable createCardDrawable(int baseColor, int cornerDp, Context context) {
        int cornerPx = dpToPx(cornerDp, context);
        int borderPx = dpToPx(3, context);

        StateListDrawable states = new StateListDrawable();

        // Focused Card State (TV cursor: AMBER GOLD)
        GradientDrawable focused = new GradientDrawable();
        focused.setColor(baseColor);
        focused.setCornerRadius(cornerPx);
        focused.setStroke(borderPx, FOCUS_COLOR);
        states.addState(new int[]{android.R.attr.state_focused}, focused);

        // Selected Card State (CYAN)
        GradientDrawable selected = new GradientDrawable();
        selected.setColor(baseColor);
        selected.setCornerRadius(cornerPx);
        selected.setStroke(borderPx, SELECTED_BORDER);
        states.addState(new int[]{android.R.attr.state_selected}, selected);

        // Normal Card State
        GradientDrawable normal = new GradientDrawable();
        normal.setColor(baseColor);
        normal.setCornerRadius(cornerPx);
        normal.setStroke(dpToPx(1, context), Color.argb(40, 255, 255, 255));
        states.addState(new int[]{}, normal);

        return states;
    }

    public static int dpToPx(float dp, Context context) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, dp, context.getResources().getDisplayMetrics()));
    }

    private static int brighten(int color, float fraction) {
        int r = Color.red(color);
        int g = Color.green(color);
        int b = Color.blue(color);
        r = Math.min(255, (int) (r + (255 - r) * fraction));
        g = Math.min(255, (int) (g + (255 - g) * fraction));
        b = Math.min(255, (int) (b + (255 - b) * fraction));
        return Color.argb(Color.alpha(color), r, g, b);
    }
}
