package com.noby.nreader.model;

import android.content.Context;
import android.content.SharedPreferences;

import com.noby.nreader.AppLog;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class BookStore {
    private static final String PREFS = "nreader_progress";
    private static final String ACTIVITY_PREFIX = "activity_";

    private BookStore() {
    }

    public static File getLibraryDir(Context context) {
        File dir = new File(context.getFilesDir(), "books");
        if (!dir.exists()) {
            dir.mkdirs();
        }

        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (!prefs.getBoolean("legacy_migrated", false)) {
            try {
                File legacyBase = context.getExternalFilesDir(null);
                if (legacyBase != null) {
                    File legacyDir = new File(legacyBase, "books");
                    if (legacyDir.exists() && legacyDir.isDirectory()) {
                        File[] legacyFiles = legacyDir.listFiles();
                        if (legacyFiles != null) {
                            for (File lf : legacyFiles) {
                                if (lf.isFile() && isSupported(lf)) {
                                    File target = new File(dir, lf.getName());
                                    if (!target.exists() || target.length() != lf.length()) {
                                        copyFile(lf, target);
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (Exception ignored) {
            }
            prefs.edit().putBoolean("legacy_migrated", true).apply();
        }

        return dir;
    }

    public static File getCoverDir(Context context) {
        File dir = new File(context.getFilesDir(), "covers");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    public static void clearCoversCache(Context context) {
        try {
            File dir = new File(context.getFilesDir(), "covers");
            if (dir.exists() && dir.isDirectory()) {
                File[] files = dir.listFiles();
                if (files != null) {
                    for (File f : files) {
                        f.delete();
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }

    public static boolean isSupported(File file) {
        if (file == null || !file.isFile()) {
            return false;
        }
        return isSupported(file.getName());
    }

    public static boolean isSupported(String fileName) {
        if (fileName == null) {
            return false;
        }
        String lower = fileName.toLowerCase(Locale.US);
        return lower.endsWith(".txt")
                || lower.endsWith(".epub")
                || lower.endsWith(".mobi")
                || lower.endsWith(".azw")
                || lower.endsWith(".azw3")
                || lower.endsWith(".pdf");
    }

    public static List<BookItem> listBooks(Context context) {
        File[] files = getLibraryDir(context).listFiles();
        List<BookItem> books = new ArrayList<BookItem>();
        if (files != null) {
            SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            for (File file : files) {
                if (file.isFile() && isSupported(file)) {
                    BookItem item = new BookItem(file);
                    float frac = prefs.getFloat(progressKey(file) + "_fraction", -1.0f);
                    int chap = prefs.getInt(progressKey(file) + "_chapter", -1);
                    long activity = prefs.getLong(activityKey(file), -1L);

                    if (frac < 0.0f) {
                        String legacy = "pos_" + Integer.toHexString(file.getName().hashCode());
                        frac = prefs.getFloat(legacy + "_fraction", 0.0f);
                        chap = prefs.getInt(legacy + "_chapter", 0);
                        activity = prefs.getLong(ACTIVITY_PREFIX + Integer.toHexString(file.getName().hashCode()), file.lastModified());
                    }

                    item.setReadingProgress(Math.max(0.0f, frac * 100.0f));
                    item.setCurrentChapter(Math.max(0, chap));
                    item.setLastReadTime(activity > 0 ? activity : file.lastModified());
                    books.add(item);
                }
            }
        }

        Collections.sort(books, new Comparator<BookItem>() {
            @Override
            public int compare(BookItem left, BookItem right) {
                long leftActivity = left.getLastReadTime();
                long rightActivity = right.getLastReadTime();
                int cmp = Long.compare(rightActivity, leftActivity);
                if (cmp != 0) {
                    return cmp;
                }
                return left.getTitle().compareToIgnoreCase(right.getTitle());
            }
        });
        return books;
    }

    public static List<BookItem> listRecentlyRead(Context context, int limit) {
        List<BookItem> all = listBooks(context);
        List<BookItem> recent = new ArrayList<BookItem>();
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        for (BookItem b : all) {
            if (prefs.contains(activityKey(b.getFile()))) {
                recent.add(b);
                if (recent.size() >= limit) {
                    break;
                }
            }
        }
        return recent;
    }

    public static File importBook(Context context, File sourceFile) throws IOException {
        if (sourceFile == null || !sourceFile.isFile() || !isSupported(sourceFile)) {
            throw new IllegalArgumentException("Invalid or unsupported file: " + sourceFile);
        }

        File target = new File(getLibraryDir(context), sourceFile.getName());
        if (target.exists() && target.length() == sourceFile.length()) {
            recordActivity(context, target);
            return target;
        }

        copyFile(sourceFile, target);
        recordActivity(context, target);
        AppLog.info("Imported book to internal storage: " + target.getName() + " (" + target.length() + " bytes)");
        return target;
    }

    public static void copyFile(File source, File target) throws IOException {
        byte[] buffer = new byte[64 * 1024];
        InputStream in = null;
        OutputStream out = null;
        try {
            in = new FileInputStream(source);
            out = new FileOutputStream(target);
            int count;
            while ((count = in.read(buffer)) != -1) {
                out.write(buffer, 0, count);
            }
            out.flush();
        } finally {
            if (in != null) {
                try { in.close(); } catch (Exception ignored) {}
            }
            if (out != null) {
                try { out.close(); } catch (Exception ignored) {}
            }
        }
    }

    public static boolean deleteBook(Context context, File book) {
        if (book == null) {
            return false;
        }
        try {
            boolean deleted = book.delete();
            if (!deleted && book.exists()) {
                File target = book.getCanonicalFile();
                deleted = target.delete();
            }

            String key = progressKey(book);
            String legacyKey = "pos_" + Integer.toHexString(book.getName().hashCode());
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit()
                    .remove(key + "_fraction")
                    .remove(key + "_chapter")
                    .remove(key + "_offset")
                    .remove(activityKey(book))
                    .remove(legacyKey + "_fraction")
                    .remove(legacyKey + "_chapter")
                    .remove(legacyKey + "_offset")
                    .remove(ACTIVITY_PREFIX + Integer.toHexString(book.getName().hashCode()))
                    .apply();

            File cover = getCoverFile(context, book);
            if (cover != null && cover.exists()) {
                cover.delete();
            }
            AppLog.info("Purged deleted book from library and recent list: " + book.getName());
            return true;
        } catch (Exception error) {
            AppLog.error("Failed to delete book", error);
            return false;
        }
    }

    public static void saveProgress(Context context, File book, int chapter, int offset, float fraction) {
        if (book == null) return;
        String key = progressKey(book);
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putInt(key + "_chapter", chapter)
                .putInt(key + "_offset", offset)
                .putFloat(key + "_fraction", Math.max(0.0f, Math.min(1.0f, fraction)))
                .putLong(activityKey(book), System.currentTimeMillis())
                .apply();
    }

    public static int getSavedChapter(Context context, File book) {
        if (book == null) return 0;
        SharedPreferences sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String key = progressKey(book) + "_chapter";
        if (sp.contains(key)) {
            return sp.getInt(key, 0);
        }
        String legacyKey = "pos_" + Integer.toHexString(book.getName().hashCode()) + "_chapter";
        return sp.getInt(legacyKey, 0);
    }

    public static int getSavedOffset(Context context, File book) {
        if (book == null) return 0;
        SharedPreferences sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String key = progressKey(book) + "_offset";
        if (sp.contains(key)) {
            return sp.getInt(key, 0);
        }
        String legacyKey = "pos_" + Integer.toHexString(book.getName().hashCode()) + "_offset";
        return sp.getInt(legacyKey, 0);
    }

    public static float getSavedFraction(Context context, File book) {
        if (book == null) return 0.0f;
        SharedPreferences sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String key = progressKey(book) + "_fraction";
        if (sp.contains(key)) {
            return sp.getFloat(key, 0.0f);
        }
        String legacyKey = "pos_" + Integer.toHexString(book.getName().hashCode()) + "_fraction";
        return sp.getFloat(legacyKey, 0.0f);
    }

    public static void recordActivity(Context context, File book) {
        if (book == null) return;
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putLong(activityKey(book), System.currentTimeMillis())
                .apply();
    }

    private static String bookKey(File book) {
        if (book == null) return "null";
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(book.getName().getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format(Locale.US, "%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return Integer.toHexString(book.getName().hashCode());
        }
    }

    public static File getCoverFile(Context context, File book) {
        if (book == null) return null;
        String key = bookKey(book);
        File coverDir = getCoverDir(context);
        File newCover = new File(coverDir, "cover_" + key + ".jpg");
        if (newCover.exists()) {
            return newCover;
        }
        String legacyKey = Integer.toHexString(book.getName().hashCode());
        File legacyCover = new File(coverDir, "cover_" + legacyKey + ".jpg");
        if (legacyCover.exists()) {
            legacyCover.renameTo(newCover);
            return newCover;
        }
        return newCover;
    }

    private static String progressKey(File book) {
        return "pos_" + bookKey(book);
    }

    private static String activityKey(File book) {
        return ACTIVITY_PREFIX + bookKey(book);
    }
}
