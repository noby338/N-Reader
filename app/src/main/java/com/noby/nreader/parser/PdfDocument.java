package com.noby.nreader.parser;

import android.annotation.TargetApi;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.pdf.PdfRenderer;
import android.os.Build;
import android.os.ParcelFileDescriptor;

import com.noby.nreader.AppLog;
import com.noby.nreader.AppText;

import java.io.File;
import java.io.IOException;
import java.util.List;

@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public class PdfDocument implements BookDocument {
    private final File file;
    private ParcelFileDescriptor pfd;
    private PdfRenderer renderer;
    private final int pageCount;

    public PdfDocument(File file) throws IOException {
        this.file = file;
        this.pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
        this.renderer = new PdfRenderer(pfd);
        this.pageCount = renderer.getPageCount();
        AppLog.info("Opened PDF: " + file.getName() + ", total pages=" + pageCount);
    }

    @Override
    public int getChapterCount() {
        return pageCount;
    }

    @Override
    public String getChapterTitle(int index) {
        return AppText.isChinese() ? ("第 " + (index + 1) + " 页") : ("Page " + (index + 1));
    }

    @Override
    public CharSequence loadChapter(int index) {
        return "";
    }

    @Override
    public List<TocItem> getToc() {
        return null;
    }

    @Override
    public boolean isPdf() {
        return true;
    }

    public synchronized Bitmap extractCover() {
        if (renderer == null || pageCount <= 0) return null;
        PdfRenderer.Page page = null;
        try {
            page = renderer.openPage(0);
            int pw = page.getWidth();
            int ph = page.getHeight();
            int targetW = 240;
            int targetH = Math.round(ph * ((float) targetW / (float) pw));
            targetH = Math.max(120, Math.min(360, targetH));
            // Android PdfRenderer REQUIRES ARGB_8888 format
            Bitmap bmp = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(bmp);
            c.drawColor(Color.WHITE);
            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
            return bmp;
        } catch (Throwable t) {
            AppLog.error("Error extracting PDF cover", t);
            return null;
        } finally {
            if (page != null) {
                try { page.close(); } catch (Exception ignored) {}
            }
        }
    }

    @Override
    public synchronized Bitmap renderPdfPage(int pageIndex, int targetWidth, int targetHeight) {
        if (renderer == null || pageIndex < 0 || pageIndex >= pageCount) {
            return null;
        }

        PdfRenderer.Page page = null;
        try {
            page = renderer.openPage(pageIndex);
            int originalWidth = page.getWidth();
            int originalHeight = page.getHeight();

            if (originalWidth <= 0 || originalHeight <= 0) {
                return null;
            }

            int maxWidth = targetWidth > 0 ? Math.min(1920, targetWidth) : 1920;
            int maxHeight = targetHeight > 0 ? Math.min(1920, targetHeight) : 1920;

            float scale = (float) maxWidth / (float) originalWidth;
            if (originalHeight * scale > maxHeight) {
                scale = (float) maxHeight / (float) originalHeight;
            }

            int renderWidth = Math.max(1, Math.min(1920, Math.round(originalWidth * scale)));
            int renderHeight = Math.max(1, Math.min(1920, Math.round(originalHeight * scale)));

            Bitmap bitmap = Bitmap.createBitmap(renderWidth, renderHeight, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            canvas.drawColor(Color.WHITE);

            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
            return bitmap;
        } catch (Throwable t) {
            AppLog.error("Error rendering PDF page " + pageIndex, t);
            return null;
        } finally {
            if (page != null) {
                try {
                    page.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    @Override
    public synchronized void close() {
        if (renderer != null) {
            try {
                renderer.close();
            } catch (Exception ignored) {
            }
            renderer = null;
        }
        if (pfd != null) {
            try {
                pfd.close();
            } catch (Exception ignored) {
            }
            pfd = null;
        }
    }
}
