package com.noby.nreader.parser;

import android.graphics.Bitmap;

import java.util.List;

public interface BookDocument {
    class TocItem {
        public final String title;
        public final int chapterIndex;
        public final String anchor;
        public final int level;

        public TocItem(String title, int chapterIndex, String anchor, int level) {
            this.title = title;
            this.chapterIndex = chapterIndex;
            this.anchor = anchor;
            this.level = level;
        }
    }

    int getChapterCount();
    String getChapterTitle(int index);
    CharSequence loadChapter(int index) throws Exception;
    List<TocItem> getToc();
    boolean isPdf();
    Bitmap renderPdfPage(int pageIndex, int targetWidth, int targetHeight);
    void close();
}
