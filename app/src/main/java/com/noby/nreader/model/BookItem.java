package com.noby.nreader.model;

import java.io.File;
import java.util.Locale;

public class BookItem {
    private final File file;
    private final String title;
    private final String format;
    private final long fileSize;
    private float readingProgress; // 0.0 to 100.0%
    private int currentChapter;
    private int totalChapters;
    private long lastReadTime;

    public BookItem(File file) {
        this.file = file;
        this.fileSize = file.length();
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        if (dot > 0) {
            this.title = name.substring(0, dot);
            this.format = name.substring(dot + 1).toUpperCase(Locale.US);
        } else {
            this.title = name;
            this.format = "TXT";
        }
        this.lastReadTime = file.lastModified();
    }

    public File getFile() {
        return file;
    }

    public String getTitle() {
        return title;
    }

    public String getFormat() {
        return format;
    }

    public long getFileSize() {
        return fileSize;
    }

    public float getReadingProgress() {
        return readingProgress;
    }

    public void setReadingProgress(float progress) {
        this.readingProgress = Math.max(0.0f, Math.min(100.0f, progress));
    }

    public int getCurrentChapter() {
        return currentChapter;
    }

    public void setCurrentChapter(int currentChapter) {
        this.currentChapter = currentChapter;
    }

    public int getTotalChapters() {
        return totalChapters;
    }

    public void setTotalChapters(int totalChapters) {
        this.totalChapters = totalChapters;
    }

    public long getLastReadTime() {
        return lastReadTime;
    }

    public void setLastReadTime(long lastReadTime) {
        this.lastReadTime = lastReadTime;
    }

    public String getFormattedSize() {
        if (fileSize < 1024) {
            return fileSize + " B";
        } else if (fileSize < 1024 * 1024) {
            return String.format(Locale.US, "%.1f KB", fileSize / 1024.0f);
        } else {
            return String.format(Locale.US, "%.1f MB", fileSize / (1024.0f * 1024.0f));
        }
    }
}
