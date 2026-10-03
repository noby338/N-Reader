package com.noby.nreader;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;

public class NReaderApp extends Application {
    private static NReaderApp instance;

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(AppText.wrapContext(base));
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;

        AppLog.init(this);

        SharedPreferences prefs = getSharedPreferences("app_global", Context.MODE_PRIVATE);
        boolean firstLaunch = prefs.getBoolean("first_launch", true);
        String language;
        if (firstLaunch) {
            // Requirement: First launch defaults to English regardless of system locale
            language = "en";
            prefs.edit()
                    .putString("language", "en")
                    .putBoolean("first_launch", false)
                    .apply();
        } else {
            language = prefs.getString("language", "en");
        }

        AppText.initialize(this, language);
        FormatPrefs.checkVersionUpgrade(this);
        AppLog.info("NReaderApp initialized, language=" + language + ", firstLaunch=" + firstLaunch);
    }

    public static NReaderApp getInstance() {
        return instance;
    }
}
