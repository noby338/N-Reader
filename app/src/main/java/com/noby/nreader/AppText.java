package com.noby.nreader;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;

import java.util.Locale;

public final class AppText {
    private static Context appContext;
    private static Context localizedContext;
    private static String currentLanguage = "en";

    private AppText() {
    }

    public static void initialize(Context context, String langCode) {
        appContext = context.getApplicationContext();
        if (langCode == null || langCode.isEmpty()) {
            SharedPreferences prefs = appContext.getSharedPreferences("app_global", Context.MODE_PRIVATE);
            langCode = prefs.getString("language", "en");
        }
        setLanguageInternal(langCode);
    }

    public static void setLanguage(String langCode) {
        if (appContext != null) {
            SharedPreferences prefs = appContext.getSharedPreferences("app_global", Context.MODE_PRIVATE);
            prefs.edit().putString("language", langCode).apply();
        }
        setLanguageInternal(langCode);
    }

    private static void setLanguageInternal(String langCode) {
        if (langCode == null || (!langCode.equals("en") && !langCode.startsWith("zh"))) {
            langCode = "en";
        }
        currentLanguage = langCode;
        Locale targetLocale = langCode.startsWith("zh") ? Locale.SIMPLIFIED_CHINESE : Locale.ENGLISH;
        Locale.setDefault(targetLocale);

        if (appContext == null) {
            return;
        }

        Resources res = appContext.getResources();
        Configuration config = new Configuration(res.getConfiguration());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            config.setLocale(targetLocale);
            localizedContext = appContext.createConfigurationContext(config);
        } else {
            config.locale = targetLocale;
            res.updateConfiguration(config, res.getDisplayMetrics());
            localizedContext = appContext;
        }
    }

    public static Context wrapContext(Context base) {
        if (base == null) return null;
        String lang = currentLanguage;
        if (lang == null || lang.isEmpty()) {
            SharedPreferences prefs = base.getSharedPreferences("app_global", Context.MODE_PRIVATE);
            lang = prefs.getString("language", "en");
            currentLanguage = lang;
        }

        Locale targetLocale = (lang != null && lang.startsWith("zh")) ? Locale.SIMPLIFIED_CHINESE : Locale.ENGLISH;
        Locale.setDefault(targetLocale);

        Configuration config = new Configuration(base.getResources().getConfiguration());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            config.setLocale(targetLocale);
            return base.createConfigurationContext(config);
        } else {
            config.locale = targetLocale;
            base.getResources().updateConfiguration(config, base.getResources().getDisplayMetrics());
            return base;
        }
    }

    public static String getLanguage() {
        return currentLanguage;
    }

    public static boolean isChinese() {
        return currentLanguage != null && currentLanguage.startsWith("zh");
    }

    public static String get(int resourceId) {
        Context ctx = localizedContext != null ? localizedContext : appContext;
        if (ctx == null) {
            return "";
        }
        return ctx.getString(resourceId);
    }

    public static String format(int resourceId, Object... arguments) {
        Context ctx = localizedContext != null ? localizedContext : appContext;
        if (ctx == null) {
            return "";
        }
        return ctx.getString(resourceId, arguments);
    }
}
