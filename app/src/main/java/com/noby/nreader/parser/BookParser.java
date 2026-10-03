package com.noby.nreader.parser;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.pdf.PdfRenderer;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.text.Html;
import android.text.Spanned;

import com.noby.nreader.AppLog;
import com.noby.nreader.AppText;
import com.noby.nreader.R;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.RandomAccessFile;
import java.io.Reader;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class BookParser {
    private static final int TEXT_PAGE_TARGET = 40 * 1024;
    private static final int TEXT_PAGE_LIMIT = 64 * 1024;
    private static final Pattern HTML_TITLE = Pattern.compile("(?is)<title\\b[^>]*>(.*?)</title>");
    private static final Pattern HTML_HEADING = Pattern.compile("(?is)<h[1-6]\\b[^>]*>(.*?)</h[1-6]>");

    private BookParser() {
    }

    public static BookDocument open(Context context, File file) throws Exception {
        String name = file.getName().toLowerCase(Locale.US);
        File cacheDir = context.getCacheDir();

        if (name.endsWith(".txt")) {
            return CachedTextDocument.fromText(file, cacheDir);
        } else if (name.endsWith(".epub")) {
            return new EpubDocument(file);
        } else if (name.endsWith(".mobi") || name.endsWith(".azw") || name.endsWith(".azw3")) {
            return CachedTextDocument.fromMobi(file, cacheDir);
        } else if (name.endsWith(".pdf")) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                return new PdfDocument(file);
            } else {
                throw new IOException("PDF requires Android 5.0+");
            }
        }
        throw new IOException(AppText.get(R.string.unsupported_file_format));
    }

    public static class EpubDocument implements BookDocument {
        private final File file;
        private ZipFile zipFile;
        private final List<String> chapters = new ArrayList<String>();
        private final Map<String, String> manifest = new HashMap<String, String>();
        private final Map<String, String> entryTitles = new HashMap<String, String>();
        private final List<BookDocument.TocItem> tocList = new ArrayList<BookDocument.TocItem>();
        private String opfPath;
        private String opfDir = "";
        private String coverHref = null;
        private String ncxHref = null;
        private String navHref = null;

        private synchronized ZipFile getZipFile() throws IOException {
            if (zipFile == null) {
                zipFile = new ZipFile(file);
            }
            return zipFile;
        }

        public EpubDocument(File file) throws Exception {
            this.file = file;
            ZipFile zip = new ZipFile(file);
            try {
                opfPath = findOpfPath(zip);
                if (opfPath == null) {
                    throw new IOException("EPUB container.xml missing or invalid");
                }
                opfPath = decodePath(opfPath);
                int slash = opfPath.lastIndexOf('/');
                opfDir = slash >= 0 ? opfPath.substring(0, slash + 1) : "";

                byte[] opfData = readZipEntry(zip, opfPath);
                parseOpf(new ByteArrayInputStream(opfData), zip);
                if (ncxHref != null) {
                    parseNcx(zip, normalizeZipPath(opfDir + decodePath(ncxHref)));
                } else if (navHref != null) {
                    parseNavXhtml(zip, normalizeZipPath(opfDir + decodePath(navHref)));
                }
                resolveAllChapterTitles(zip);
            } finally {
                zip.close();
            }

            if (chapters.isEmpty()) {
                fallbackScanChapters(file);
            }
            AppLog.info("Opened EPUB: " + file.getName() + ", total chapters=" + chapters.size());
        }

        private String findOpfPath(ZipFile zip) throws Exception {
            ZipEntry container = findEntryIgnoreCase(zip, "META-INF/container.xml");
            if (container == null) {
                return null;
            }
            InputStream in = zip.getInputStream(container);
            try {
                XmlPullParser parser = XmlPullParserFactory.newInstance().newPullParser();
                parser.setInput(in, "UTF-8");
                int event;
                while ((event = parser.next()) != XmlPullParser.END_DOCUMENT) {
                    if (event == XmlPullParser.START_TAG && "rootfile".equals(parser.getName())) {
                        return parser.getAttributeValue(null, "full-path");
                    }
                }
            } finally {
                in.close();
            }
            return null;
        }

        private void parseOpf(InputStream in, ZipFile zip) throws Exception {
            XmlPullParser parser = XmlPullParserFactory.newInstance().newPullParser();
            parser.setInput(in, "UTF-8");
            int event;
            List<String> spineIds = new ArrayList<String>();
            String coverId = null;

            while ((event = parser.next()) != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG) {
                    String name = parser.getName();
                    if ("item".equals(name)) {
                        String id = parser.getAttributeValue(null, "id");
                        String href = parser.getAttributeValue(null, "href");
                        String props = parser.getAttributeValue(null, "properties");
                        String mediaType = parser.getAttributeValue(null, "media-type");
                        if (id != null && href != null) {
                            manifest.put(id, href);
                            if (props != null && props.contains("cover-image")) {
                                coverHref = href;
                            }
                            if ("ncx".equalsIgnoreCase(id) || "application/x-dtbncx+xml".equalsIgnoreCase(mediaType)) {
                                ncxHref = href;
                            }
                            if (props != null && props.contains("nav")) {
                                navHref = href;
                            }
                        }
                    } else if ("itemref".equals(name)) {
                        String idref = parser.getAttributeValue(null, "idref");
                        if (idref != null) {
                            spineIds.add(idref);
                        }
                    } else if ("meta".equals(name)) {
                        String mName = parser.getAttributeValue(null, "name");
                        String mContent = parser.getAttributeValue(null, "content");
                        if ("cover".equalsIgnoreCase(mName) && mContent != null) {
                            coverId = mContent;
                        }
                    }
                }
            }

            if (coverHref == null && coverId != null) {
                coverHref = manifest.get(coverId);
            }

            for (String id : spineIds) {
                String href = manifest.get(id);
                if (href != null) {
                    int hash = href.indexOf('#');
                    if (hash >= 0) {
                        href = href.substring(0, hash);
                    }
                    String path = normalizeZipPath(opfDir + decodePath(href));
                    ZipEntry entry = findEntryIgnoreCase(zip, path);
                    if (entry != null) {
                        chapters.add(entry.getName());
                    }
                }
            }
        }

        private void parseNcx(ZipFile zip, String ncxPath) {
            ZipEntry entry = findEntryIgnoreCase(zip, ncxPath);
            if (entry == null) return;
            try {
                InputStream in = zip.getInputStream(entry);
                try {
                    XmlPullParser parser = XmlPullParserFactory.newInstance().newPullParser();
                    parser.setInput(in, "UTF-8");

                    Map<String, Integer> chapterIndexMap = new HashMap<String, Integer>();
                    for (int i = 0; i < chapters.size(); i++) {
                        chapterIndexMap.put(chapters.get(i).toLowerCase(Locale.US), i);
                    }

                    int event;
                    String currentText = null;
                    String currentSrc = null;
                    int depth = 0;

                    while ((event = parser.next()) != XmlPullParser.END_DOCUMENT) {
                        if (event == XmlPullParser.START_TAG) {
                            String tag = parser.getName();
                            if ("navPoint".equalsIgnoreCase(tag)) {
                                depth++;
                                currentText = null;
                                currentSrc = null;
                            } else if ("text".equalsIgnoreCase(tag)) {
                                currentText = parser.nextText();
                                if (currentText != null && currentSrc != null) {
                                    emitTocEntry(currentText, currentSrc, depth, chapterIndexMap);
                                    currentText = null;
                                    currentSrc = null;
                                }
                            } else if ("content".equalsIgnoreCase(tag)) {
                                currentSrc = parser.getAttributeValue(null, "src");
                                if (currentText != null && currentSrc != null) {
                                    emitTocEntry(currentText, currentSrc, depth, chapterIndexMap);
                                    currentText = null;
                                    currentSrc = null;
                                }
                            }
                        } else if (event == XmlPullParser.END_TAG) {
                            String tag = parser.getName();
                            if ("navPoint".equalsIgnoreCase(tag)) {
                                if (depth > 0) depth--;
                            }
                        }
                    }
                } finally {
                    in.close();
                }
            } catch (Exception ignored) {
            }
        }

        private void parseNavXhtml(ZipFile zip, String navPath) {
            ZipEntry entry = findEntryIgnoreCase(zip, navPath);
            if (entry == null) return;
            try {
                InputStream in = zip.getInputStream(entry);
                try {
                    XmlPullParser parser = XmlPullParserFactory.newInstance().newPullParser();
                    parser.setInput(in, "UTF-8");

                    Map<String, Integer> chapterIndexMap = new HashMap<String, Integer>();
                    for (int i = 0; i < chapters.size(); i++) {
                        chapterIndexMap.put(chapters.get(i).toLowerCase(Locale.US), i);
                    }

                    int event;
                    int depth = 0;
                    boolean inNav = false;

                    while ((event = parser.next()) != XmlPullParser.END_DOCUMENT) {
                        if (event == XmlPullParser.START_TAG) {
                            String tag = parser.getName();
                            if ("nav".equalsIgnoreCase(tag)) {
                                inNav = true;
                            } else if (inNav && "ol".equalsIgnoreCase(tag)) {
                                depth++;
                            } else if (inNav && "a".equalsIgnoreCase(tag)) {
                                String href = parser.getAttributeValue(null, "href");
                                String title = parser.nextText();
                                if (href != null && title != null && !title.trim().isEmpty()) {
                                    emitTocEntry(title, href, depth, chapterIndexMap);
                                }
                            }
                        } else if (event == XmlPullParser.END_TAG) {
                            String tag = parser.getName();
                            if ("nav".equalsIgnoreCase(tag)) {
                                inNav = false;
                            } else if (inNav && "ol".equalsIgnoreCase(tag)) {
                                if (depth > 0) depth--;
                            }
                        }
                    }
                } finally {
                    in.close();
                }
            } catch (Exception ignored) {
            }
        }

        private void emitTocEntry(String text, String src, int depth, Map<String, Integer> chapterIndexMap) {
            if (text == null || text.trim().isEmpty() || src == null) return;
            String trimmedTitle = text.trim();
            int hash = src.indexOf('#');
            String filePart = (hash >= 0) ? src.substring(0, hash) : src;
            String anchorPart = (hash >= 0) ? src.substring(hash + 1) : null;
            String fullPath = normalizeZipPath(opfDir + decodePath(filePart));
            String fullPathKey = fullPath.toLowerCase(Locale.US);

            if (hash < 0) {
                entryTitles.put(fullPathKey, trimmedTitle);
            } else if (!entryTitles.containsKey(fullPathKey)) {
                entryTitles.put(fullPathKey, trimmedTitle);
            }

            Integer chIdx = chapterIndexMap.get(fullPathKey);
            if (chIdx != null) {
                int level = Math.max(0, depth - 1);
                tocList.add(new BookDocument.TocItem(trimmedTitle, chIdx, anchorPart, level));
            }
        }

        private void resolveAllChapterTitles(ZipFile zip) {
            int bodyChapterCounter = 0;
            boolean seenBodyChapter = false;

            for (int i = 0; i < chapters.size(); i++) {
                String path = chapters.get(i);
                String lower = path.toLowerCase(Locale.US);

                if (entryTitles.containsKey(lower)) {
                    String title = entryTitles.get(lower);
                    if (title != null && !title.isEmpty()) {
                        if (title.contains("章") || title.contains("Chapter")) {
                            seenBodyChapter = true;
                        }
                        continue;
                    }
                }

                String filename = lower.substring(lower.lastIndexOf('/') + 1);
                int dot = filename.lastIndexOf('.');
                String base = dot > 0 ? filename.substring(0, dot) : filename;

                String detected = null;
                if (base.contains("cover")) {
                    detected = AppText.isChinese() ? "封面" : "Cover";
                } else if (base.contains("title") || base.contains("fmp")) {
                    detected = AppText.isChinese() ? "扉页" : "Title Page";
                } else if (base.contains("copy") || base.contains("cr") || base.contains("isbn") || base.contains("legal")) {
                    detected = AppText.isChinese() ? "版权信息" : "Copyright";
                } else if (base.contains("toc") || base.contains("nav") || base.contains("content")) {
                    detected = AppText.isChinese() ? "目录" : "Contents";
                } else if (base.contains("pref") || base.contains("foreword") || base.contains("intro")) {
                    detected = AppText.isChinese() ? "前言" : "Preface";
                } else if (base.contains("dedic")) {
                    detected = AppText.isChinese() ? "献词" : "Dedication";
                } else if (base.contains("prolog") || base.contains("xuzi") || base.contains("xiezi")) {
                    detected = AppText.isChinese() ? "序言" : "Prologue";
                } else if (base.contains("epilog") || base.contains("weisheng") || base.contains("houji")) {
                    detected = AppText.isChinese() ? "后记" : "Epilogue";
                }

                if (detected == null && zip != null) {
                    ZipEntry entry = findEntryIgnoreCase(zip, path);
                    if (entry != null) {
                        try {
                            InputStream in = zip.getInputStream(entry);
                            byte[] buf = new byte[2048];
                            int r = in.read(buf);
                            in.close();
                            if (r > 0) {
                                String head = new String(buf, 0, r);
                                String heading = extractFirstHeadingOrTitle(head);
                                if (heading != null && !heading.isEmpty() && heading.length() < 60) {
                                    detected = heading;
                                }
                            }
                        } catch (Exception ignored) {
                        }
                    }
                }

                if (detected != null) {
                    entryTitles.put(lower, detected);
                    if (detected.contains("章") || detected.contains("Chapter")) {
                        seenBodyChapter = true;
                    }
                } else {
                    if (!seenBodyChapter && i < 3) {
                        String intro = AppText.isChinese() ? "前言" : "Introduction";
                        entryTitles.put(lower, intro);
                    } else {
                        bodyChapterCounter++;
                        seenBodyChapter = true;
                        String chTitle = AppText.isChinese()
                                ? ("第 " + bodyChapterCounter + " 章")
                                : ("Chapter " + bodyChapterCounter);
                        entryTitles.put(lower, chTitle);
                    }
                }
            }
        }

        private String extractFirstHeadingOrTitle(String html) {
            Matcher mHead = HTML_HEADING.matcher(html);
            if (mHead.find()) {
                String raw = mHead.group(1).replaceAll("<[^>]+>", "").trim();
                if (!raw.isEmpty()) return raw;
            }
            Matcher mTitle = HTML_TITLE.matcher(html);
            if (mTitle.find()) {
                String raw = mTitle.group(1).replaceAll("<[^>]+>", "").trim();
                if (!raw.isEmpty() && !raw.toLowerCase(Locale.US).contains("untitled") && !raw.endsWith(".xhtml")) {
                    return raw;
                }
            }
            return null;
        }

        private void fallbackScanChapters(File file) {
            ZipFile zip = null;
            try {
                zip = new ZipFile(file);
                Enumeration<? extends ZipEntry> entries = zip.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry e = entries.nextElement();
                    String name = e.getName().toLowerCase(Locale.US);
                    if ((name.endsWith(".xhtml") || name.endsWith(".html") || name.endsWith(".htm"))
                            && !name.contains("toc") && !name.contains("nav")) {
                        chapters.add(e.getName());
                    }
                }
            } catch (Exception ignored) {
            } finally {
                if (zip != null) {
                    try { zip.close(); } catch (Exception ignored) {}
                }
            }
        }

        public Bitmap extractCover() {
            ZipFile zip = null;
            try {
                zip = new ZipFile(file);
                if (coverHref != null) {
                    String path = normalizeZipPath(opfDir + decodePath(coverHref));
                    ZipEntry entry = findEntryIgnoreCase(zip, path);
                    if (entry != null) {
                        InputStream in = zip.getInputStream(entry);
                        Bitmap bmp = BitmapFactory.decodeStream(in);
                        in.close();
                        if (bmp != null) return bmp;
                    }
                }
                Enumeration<? extends ZipEntry> entries = zip.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry e = entries.nextElement();
                    String lower = e.getName().toLowerCase(Locale.US);
                    if (lower.contains("cover") && (lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png"))) {
                        InputStream in = zip.getInputStream(e);
                        Bitmap bmp = BitmapFactory.decodeStream(in);
                        in.close();
                        if (bmp != null) return bmp;
                    }
                }
            } catch (Exception ignored) {
            } finally {
                if (zip != null) {
                    try { zip.close(); } catch (Exception ignored) {}
                }
            }
            return null;
        }

        @Override
        public int getChapterCount() {
            return Math.max(1, chapters.size());
        }

        @Override
        public String getChapterTitle(int index) {
            if (index < 0 || index >= chapters.size()) {
                return AppText.isChinese() ? ("第 " + (index + 1) + " 章") : ("Chapter " + (index + 1));
            }
            String path = chapters.get(index);
            String title = entryTitles.get(path.toLowerCase(Locale.US));
            if (title != null && !title.isEmpty()) {
                return title;
            }
            return AppText.isChinese() ? ("第 " + (index + 1) + " 章") : ("Chapter " + (index + 1));
        }

        @Override
        public List<TocItem> getToc() {
            return tocList;
        }

        @Override
        public synchronized CharSequence loadChapter(int index) throws Exception {
            if (index < 0 || index >= chapters.size()) {
                return "";
            }
            ZipFile zip = getZipFile();
            String path = chapters.get(index);
            ZipEntry entry = findEntryIgnoreCase(zip, path);
            if (entry == null) {
                return "Chapter file not found: " + path;
            }
            InputStream in = zip.getInputStream(entry);
            byte[] data = readStream(in);
            String charset = detectHtmlCharset(data);
            String html = new String(data, charset);

            html = sanitizeHtml(html);
            try {
                Spanned spanned = Html.fromHtml(html);
                if (spanned != null && spanned.length() > 0) {
                    return spanned;
                }
            } catch (Exception ignored) {
            }

            return stripHtmlTags(html);
        }

        private String sanitizeHtml(String html) {
            html = html.replaceAll("(?is)<style\\b[^>]*>.*?</style>", "");
            html = html.replaceAll("(?is)<script\\b[^>]*>.*?</script>", "");
            html = html.replaceAll("(?is)<svg\\b[^>]*>.*?</svg>", "");
            html = html.replaceAll("(?is)<head\\b[^>]*>.*?</head>", "");
            html = html.replaceAll("(?is)<img\\b[^>]*>", "");
            html = html.replaceAll("(?is)<image\\b[^>]*>", "");
            return html;
        }

        private String stripHtmlTags(String html) {
            html = sanitizeHtml(html);
            html = html.replaceAll("(?i)<br\\s*/?>", "\n");
            html = html.replaceAll("(?i)</p>", "\n\n");
            html = html.replaceAll("(?i)</div>", "\n");
            html = html.replaceAll("(?i)</h[1-6]>", "\n\n");
            html = html.replaceAll("<[^>]+>", "");
            html = html.replace("&nbsp;", " ")
                       .replace("&amp;", "&")
                       .replace("&lt;", "<")
                       .replace("&gt;", ">")
                       .replace("&quot;", "\"")
                       .replace("&apos;", "'")
                       .replace("&#39;", "'");
            return html.trim();
        }

        @Override
        public boolean isPdf() {
            return false;
        }

        @Override
        public Bitmap renderPdfPage(int pageIndex, int targetWidth, int targetHeight) {
            return null;
        }

        @Override
        public synchronized void close() {
            if (zipFile != null) {
                try {
                    zipFile.close();
                } catch (Exception ignored) {
                }
                zipFile = null;
            }
        }
    }

    public static class CachedTextDocument implements BookDocument {
        private final File sessionDir;
        private final List<File> pageFiles;
        private final List<String> chapterTitles;

        private CachedTextDocument(File sessionDir, List<File> pageFiles, List<String> chapterTitles) {
            this.sessionDir = sessionDir;
            this.pageFiles = pageFiles;
            this.chapterTitles = chapterTitles;
        }

        public static CachedTextDocument fromText(File source, File cacheDirectory) throws IOException {
            File session = createSessionDir(cacheDirectory);
            List<File> pages = new ArrayList<File>();
            List<String> titles = new ArrayList<String>();
            Charset charset = detectCharset(source);
            Pattern chapterPattern = Pattern.compile("^[ \\t]*(第[0-9零一二三四五六七八九十百千]+[章回节卷篇集部][^\\r\\n]{0,35}|Chapter\\s+[0-9]+[^\\r\\n]{0,35}|引言|前言|序言|楔子|后记|尾声|番外|结语|附录|内容简介)[ \\t]*$");

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(new FileInputStream(source), charset), 32 * 1024);
            try {
                StringBuilder pageBuffer = new StringBuilder(TEXT_PAGE_LIMIT);
                String line;
                String currentTitle = AppText.isChinese() ? "前言" : "Preface";
                boolean foundFirstChapter = false;

                while ((line = reader.readLine()) != null) {
                    String trimmed = line.trim();
                    boolean isChapterHeading = chapterPattern.matcher(trimmed).matches();

                    if (isChapterHeading && pageBuffer.length() > 0) {
                        writePage(session, pages, pageBuffer);
                        titles.add(currentTitle);
                        currentTitle = trimmed;
                        foundFirstChapter = true;
                        pageBuffer.setLength(0);
                    }

                    pageBuffer.append(line).append('\n');

                    if (pageBuffer.length() >= TEXT_PAGE_LIMIT) {
                        writePage(session, pages, pageBuffer);
                        titles.add(currentTitle);
                        pageBuffer.setLength(0);
                    }
                }

                if (pageBuffer.length() > 0) {
                    writePage(session, pages, pageBuffer);
                    titles.add(currentTitle);
                }
            } finally {
                reader.close();
            }

            if (pages.isEmpty()) {
                File emptyPage = new File(session, "page_0.txt");
                emptyPage.createNewFile();
                pages.add(emptyPage);
                titles.add(AppText.isChinese() ? "正文" : "Content");
            }
            AppLog.info("Opened TXT: " + source.getName() + ", pages=" + pages.size());
            return new CachedTextDocument(session, pages, titles);
        }

        public static CachedTextDocument fromMobi(File source, File cacheDirectory) throws Exception {
            File session = createSessionDir(cacheDirectory);
            List<File> pages = new ArrayList<File>();
            RandomAccessFile raf = new RandomAccessFile(source, "r");
            try {
                byte[] header = new byte[78];
                raf.readFully(header);
                int recordCount = ((header[76] & 0xff) << 8) | (header[77] & 0xff);
                if (recordCount < 2) {
                    throw new IOException("Invalid MOBI record count");
                }
                byte[] table = new byte[recordCount * 8];
                raf.readFully(table);
                int[] offsets = new int[recordCount];
                for (int i = 0; i < recordCount; i++) {
                    offsets[i] = (int) (
                            ((long) (table[i * 8] & 0xff) << 24)
                            | ((long) (table[i * 8 + 1] & 0xff) << 16)
                            | ((long) (table[i * 8 + 2] & 0xff) << 8)
                            | (long) (table[i * 8 + 3] & 0xff));
                }

                int rec0Len = offsets[1] - offsets[0];
                byte[] rec0 = new byte[rec0Len];
                raf.seek(offsets[0]);
                raf.readFully(rec0);
                int compression = ((rec0[0] & 0xff) << 8) | (rec0[1] & 0xff);
                int textRecords = ((rec0[8] & 0xff) << 8) | (rec0[9] & 0xff);

                StringBuilder pageBuffer = new StringBuilder(TEXT_PAGE_LIMIT);
                int lastRecord = Math.min(textRecords, recordCount - 1);
                for (int i = 1; i <= lastRecord; i++) {
                    int start = offsets[i];
                    int end = i + 1 < recordCount ? offsets[i + 1] : (int) raf.length();
                    int len = end - start;
                    if (len <= 0) continue;
                    byte[] compressed = new byte[len];
                    raf.seek(start);
                    raf.readFully(compressed);

                    String text;
                    if (compression == 1) {
                        text = new String(compressed, "UTF-8");
                    } else if (compression == 2) {
                        ByteArrayOutputStream decompressed = new ByteArrayOutputStream();
                        decompressPalmDoc(compressed, decompressed);
                        text = new String(decompressed.toByteArray(), "UTF-8");
                    } else {
                        text = "";
                    }

                    text = Html.fromHtml(text).toString();
                    pageBuffer.append(text).append("\n\n");
                    if (pageBuffer.length() >= TEXT_PAGE_TARGET) {
                        writePage(session, pages, pageBuffer);
                    }
                }
                if (pageBuffer.length() > 0) {
                    writePage(session, pages, pageBuffer);
                }
            } finally {
                raf.close();
            }

            if (pages.isEmpty()) {
                File emptyPage = new File(session, "page_0.txt");
                emptyPage.createNewFile();
                pages.add(emptyPage);
            }
            AppLog.info("Opened MOBI: " + source.getName() + ", pages=" + pages.size());
            return new CachedTextDocument(session, pages, null);
        }

        private static void decompressPalmDoc(byte[] in, ByteArrayOutputStream out) {
            int pos = 0;
            int len = in.length;
            while (pos < len) {
                int b = in[pos++] & 0xff;
                if (b == 0 || (b >= 9 && b <= 0x7f)) {
                    out.write(b);
                } else if (b >= 1 && b <= 8) {
                    int count = Math.min(b, len - pos);
                    out.write(in, pos, count);
                    pos += count;
                } else if (b >= 0x80 && b <= 0xbf) {
                    if (pos >= len) break;
                    int pair = (b << 8) | (in[pos++] & 0xff);
                    int dist = (pair >> 3) & 0x7ff;
                    int count = (pair & 7) + 3;
                    byte[] current = out.toByteArray();
                    int backPos = current.length - dist;
                    if (backPos >= 0) {
                        for (int k = 0; k < count; k++) {
                            out.write(current[backPos + (k % dist)]);
                        }
                    }
                } else {
                    out.write(' ');
                    out.write(b ^ 0x80);
                }
            }
        }

        private static void writePage(File dir, List<File> pages, StringBuilder pageBuffer) throws IOException {
            File pageFile = new File(dir, "page_" + pages.size() + ".txt");
            BufferedWriter writer = new BufferedWriter(
                    new OutputStreamWriter(new FileOutputStream(pageFile), "UTF-8"), 16 * 1024);
            try {
                writer.write(pageBuffer.toString());
                writer.flush();
            } finally {
                writer.close();
            }
            pages.add(pageFile);
            pageBuffer.setLength(0);
        }

        private static File createSessionDir(File cacheDir) throws IOException {
            File root = new File(cacheDir, "reader_pages");
            if (!root.exists()) {
                root.mkdirs();
            }
            File session = File.createTempFile("sess_", ".dir", root);
            session.delete();
            session.mkdir();
            return session;
        }

        private static Charset detectCharset(File file) {
            try {
                FileInputStream in = new FileInputStream(file);
                byte[] sample = new byte[4096];
                int read = in.read(sample);
                in.close();
                if (read > 2 && sample[0] == (byte) 0xef && sample[1] == (byte) 0xbb && sample[2] == (byte) 0xbf) {
                    return Charset.forName("UTF-8");
                }
                if (read > 1 && sample[0] == (byte) 0xff && sample[1] == (byte) 0xfe) {
                    return Charset.forName("UTF-16LE");
                }
                if (read > 1 && sample[0] == (byte) 0xfe && sample[1] == (byte) 0xff) {
                    return Charset.forName("UTF-16BE");
                }
                if (isValidUtf8(sample, read)) {
                    return Charset.forName("UTF-8");
                }
            } catch (Exception ignored) {
            }
            return Charset.forName("GB18030");
        }

        private static boolean isValidUtf8(byte[] b, int len) {
            int i = 0;
            while (i < len) {
                int c = b[i++] & 0xff;
                if (c <= 0x7f) continue;
                if ((c >> 5) == 0x6) {
                    if (i >= len || (b[i++] & 0xc0) != 0x80) return false;
                } else if ((c >> 4) == 0xe) {
                    if (i + 1 >= len || (b[i++] & 0xc0) != 0x80 || (b[i++] & 0xc0) != 0x80) return false;
                } else if ((c >> 3) == 0x1e) {
                    if (i + 2 >= len || (b[i++] & 0xc0) != 0x80 || (b[i++] & 0xc0) != 0x80 || (b[i++] & 0xc0) != 0x80) return false;
                } else {
                    return false;
                }
            }
            return true;
        }

        @Override
        public int getChapterCount() {
            return pageFiles.size();
        }

        @Override
        public String getChapterTitle(int index) {
            if (chapterTitles != null && index >= 0 && index < chapterTitles.size()) {
                return chapterTitles.get(index);
            }
            return AppText.isChinese() ? ("第 " + (index + 1) + " 部分") : ("Part " + (index + 1));
        }

        @Override
        public CharSequence loadChapter(int index) throws Exception {
            if (index < 0 || index >= pageFiles.size()) {
                return "";
            }
            File page = pageFiles.get(index);
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(new FileInputStream(page), "UTF-8"), 16 * 1024);
            try {
                StringBuilder sb = new StringBuilder((int) page.length() + 64);
                char[] buf = new char[8 * 1024];
                int read;
                while ((read = reader.read(buf)) != -1) {
                    sb.append(buf, 0, read);
                }
                return sb.toString();
            } finally {
                reader.close();
            }
        }

        @Override
        public List<TocItem> getToc() {
            return null;
        }

        @Override
        public boolean isPdf() {
            return false;
        }

        @Override
        public Bitmap renderPdfPage(int pageIndex, int targetWidth, int targetHeight) {
            return null;
        }

        @Override
        public void close() {
            deleteRecursively(sessionDir);
        }
    }

    public static ZipEntry findEntryIgnoreCase(ZipFile zip, String path) {
        if (zip == null || path == null) return null;
        ZipEntry exact = zip.getEntry(path);
        if (exact != null) {
            return exact;
        }
        Enumeration<? extends ZipEntry> entries = zip.entries();
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            if (entry.getName().equalsIgnoreCase(path)) {
                return entry;
            }
        }
        return null;
    }

    public static byte[] readZipEntry(ZipFile zip, String name) throws IOException {
        ZipEntry entry = findEntryIgnoreCase(zip, name);
        if (entry == null) {
            throw new IOException("ZIP entry not found: " + name);
        }
        InputStream in = zip.getInputStream(entry);
        try {
            return readStream(in);
        } finally {
            in.close();
        }
    }

    public static byte[] readStream(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int count;
        while ((count = in.read(buf)) != -1) {
            out.write(buf, 0, count);
        }
        return out.toByteArray();
    }

    public static String normalizeZipPath(String path) {
        String[] parts = path.split("/");
        List<String> normalized = new ArrayList<String>();
        for (String part : parts) {
            if ("..".equals(part) && !normalized.isEmpty()) {
                normalized.remove(normalized.size() - 1);
            } else if (!part.isEmpty() && !".".equals(part)) {
                normalized.add(part);
            }
        }
        StringBuilder result = new StringBuilder();
        for (String part : normalized) {
            if (result.length() > 0) {
                result.append('/');
            }
            result.append(part);
        }
        return result.toString();
    }

    public static String decodePath(String path) {
        try {
            return URLDecoder.decode(path.replace("+", "%2B"), "UTF-8");
        } catch (Exception ignored) {
            return path;
        }
    }

    public static String detectHtmlCharset(byte[] data) {
        String header = new String(data, 0, Math.min(data.length, 1024));
        int encIdx = header.indexOf("encoding=\"");
        if (encIdx >= 0) {
            int end = header.indexOf("\"", encIdx + 10);
            if (end > encIdx) {
                return header.substring(encIdx + 10, end);
            }
        }
        int charIdx = header.indexOf("charset=");
        if (charIdx >= 0) {
            int end = header.indexOf("\"", charIdx + 8);
            if (end < 0) end = header.indexOf("'", charIdx + 8);
            if (end > charIdx) {
                return header.substring(charIdx + 8, end);
            }
        }
        return "UTF-8";
    }

    private static void deleteRecursively(File file) {
        if (file == null || !file.exists()) return;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child);
                }
            }
        }
        file.delete();
    }
}
