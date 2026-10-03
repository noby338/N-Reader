package com.noby.nreader.model;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;

import com.noby.nreader.parser.BookParser;
import com.noby.nreader.parser.PdfDocument;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class BookCoverLoader {
    private static final int CACHE_SIZE = 4 * 1024 * 1024; // 4MB RAM cache
    private static final LruCache<String, Bitmap> MEMORY_CACHE = new LruCache<String, Bitmap>(CACHE_SIZE) {
        @Override
        protected int sizeOf(String key, Bitmap bitmap) {
            return bitmap.getByteCount();
        }
    };

    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(2);
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());

    private BookCoverLoader() {
    }

    public static void load(final Context context, final BookItem book, final ImageView targetView) {
        if (book == null || targetView == null) return;
        final String key = book.getFile().getAbsolutePath();
        targetView.setTag(key);

        Bitmap cached = MEMORY_CACHE.get(key);
        if (cached != null) {
            targetView.setImageBitmap(cached);
            return;
        }

        final File diskCover = BookStore.getCoverFile(context, book.getFile());
        if (diskCover.exists() && diskCover.length() > 0) {
            Bitmap diskBmp = BitmapFactory.decodeFile(diskCover.getAbsolutePath());
            if (diskBmp != null) {
                MEMORY_CACHE.put(key, diskBmp);
                targetView.setImageBitmap(diskBmp);
                return;
            }
        }

        // Generate temporary placeholder while loading in background
        Bitmap placeholder = generateStyledCover(book.getTitle(), book.getFormat());
        targetView.setImageBitmap(placeholder);

        EXECUTOR.submit(new Runnable() {
            @Override
            public void run() {
                Bitmap extracted = null;
                boolean isRealCover = false;
                String format = book.getFormat().toLowerCase(Locale.US);

                try {
                    if ("epub".equals(format)) {
                        BookParser.EpubDocument epub = null;
                        try {
                            epub = new BookParser.EpubDocument(book.getFile());
                            extracted = epub.extractCover();
                            if (extracted != null) isRealCover = true;
                        } finally {
                            if (epub != null) {
                                epub.close();
                            }
                        }
                    } else if ("pdf".equals(format)) {
                        PdfDocument pdf = null;
                        try {
                            pdf = new PdfDocument(book.getFile());
                            extracted = pdf.extractCover();
                            if (extracted != null) isRealCover = true;
                        } finally {
                            if (pdf != null) {
                                pdf.close();
                            }
                        }
                    }
                } catch (Throwable t) {
                }

                if (extracted == null) {
                    extracted = generateStyledCover(book.getTitle(), book.getFormat());
                    isRealCover = false;
                }

                if (extracted != null) {
                    if (isRealCover) {
                        saveCoverToDisk(diskCover, extracted);
                    }
                    MEMORY_CACHE.put(key, extracted);
                    final Bitmap finalBmp = extracted;
                    MAIN_HANDLER.post(new Runnable() {
                        @Override
                        public void run() {
                            if (key.equals(targetView.getTag())) {
                                targetView.setImageBitmap(finalBmp);
                            }
                        }
                    });
                }
            }
        });
    }

    public static Bitmap generateStyledCover(String title, String format) {
        int width = 240;
        int height = 340;
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);
        Canvas canvas = new Canvas(bitmap);

        int bgColor;
        int accentColor;
        String fmt = format != null ? format.toUpperCase(Locale.US) : "TXT";

        if ("PDF".equals(fmt)) {
            bgColor = Color.rgb(51, 65, 85);
            accentColor = Color.rgb(203, 213, 225);
        } else if ("EPUB".equals(fmt)) {
            bgColor = Color.rgb(30, 58, 138);
            accentColor = Color.rgb(186, 230, 253);
        } else if ("MOBI".equals(fmt) || "AZW".equals(fmt)) {
            bgColor = Color.rgb(68, 64, 60);
            accentColor = Color.rgb(214, 211, 209);
        } else {
            bgColor = Color.rgb(39, 55, 50);
            accentColor = Color.rgb(167, 243, 208);
        }

        canvas.drawColor(bgColor);

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        // Spine shading
        paint.setColor(Color.argb(70, 0, 0, 0));
        canvas.drawRect(0, 0, 16, height, paint);

        // White page cut at bottom
        paint.setColor(Color.WHITE);
        canvas.drawRect(16, height - 8, width, height, paint);

        // Format Pill Badge
        paint.setColor(accentColor);
        RectF pill = new RectF(28, 24, 96, 52);
        canvas.drawRoundRect(pill, 8, 8, paint);

        paint.setColor(Color.rgb(15, 23, 42));
        paint.setTextSize(14);
        paint.setFakeBoldText(true);
        paint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(fmt, 62, 43, paint);

        // Title text drawing with wrap
        paint.setColor(Color.WHITE);
        paint.setTextSize(17);
        paint.setFakeBoldText(true);
        paint.setTextAlign(Paint.Align.LEFT);

        String cleanTitle = title != null ? title : "Book";
        int startY = 100;
        int maxCharsPerLine = 10;
        int lineCount = 0;

        for (int i = 0; i < cleanTitle.length() && lineCount < 4; i += maxCharsPerLine) {
            int end = Math.min(cleanTitle.length(), i + maxCharsPerLine);
            String line = cleanTitle.substring(i, end);
            if (lineCount == 3 && end < cleanTitle.length()) {
                line = line.substring(0, Math.min(line.length(), 8)) + "…";
            }
            canvas.drawText(line, 28, startY + (lineCount * 26), paint);
            lineCount++;
        }

        // Bookmark ribbon at top-right
        paint.setColor(Color.rgb(251, 191, 36)); // Golden ribbon
        canvas.drawRect(width - 44, 0, width - 24, 48, paint);
        canvas.drawCircle(width - 34, 48, 10, paint);

        return bitmap;
    }

    private static void saveCoverToDisk(File file, Bitmap bmp) {
        if (file == null || bmp == null) return;
        FileOutputStream out = null;
        try {
            out = new FileOutputStream(file);
            bmp.compress(Bitmap.CompressFormat.JPEG, 85, out);
            out.flush();
        } catch (Exception ignored) {
        } finally {
            if (out != null) {
                try { out.close(); } catch (Exception ignored) {}
            }
        }
    }
}
