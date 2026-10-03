package com.noby.nreader.net;

import android.content.Context;

import com.noby.nreader.AppLog;
import com.noby.nreader.AppText;
import com.noby.nreader.R;
import com.noby.nreader.model.BookStore;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PushbackInputStream;
import java.net.BindException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.util.Locale;

public final class WifiUploadServer {
    public interface Listener {
        void onServerStarted(int port);
        void onBookImported(String name);
        void onServerError(String message);
    }

    private static final int MAX_UPLOAD_BYTES = 50 * 1024 * 1024; // 50MB for PDF and EPUBs
    private final Context context;
    private final Listener listener;
    private volatile boolean running;
    private volatile int port;
    private ServerSocket serverSocket;

    public WifiUploadServer(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
    }

    public void start(final int preferredPort) {
        if (running) {
            return;
        }
        running = true;
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    serverSocket = bindAvailablePort(preferredPort, 20);
                    port = serverSocket.getLocalPort();
                    if (listener != null) {
                        listener.onServerStarted(port);
                    }
                    AppLog.info("Wi-Fi upload server started on port " + port);
                    while (running) {
                        final Socket socket = serverSocket.accept();
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                handle(socket);
                            }
                        }, "wifi-upload-client").start();
                    }
                } catch (IOException error) {
                    if (running) {
                        AppLog.error("Wi-Fi server failed", error);
                        if (listener != null) {
                            listener.onServerError(error.getMessage());
                        }
                    }
                } finally {
                    running = false;
                }
            }
        }, "wifi-book-upload").start();
    }

    private static ServerSocket bindAvailablePort(int firstPort, int attempts) throws IOException {
        IOException lastError = null;
        for (int candidate = firstPort; candidate < firstPort + attempts; candidate++) {
            try {
                return new ServerSocket(candidate);
            } catch (BindException error) {
                lastError = error;
            }
        }
        throw lastError != null ? lastError : new IOException("No available port found");
    }

    public void stop() {
        running = false;
        if (serverSocket != null) {
            try {
                serverSocket.close();
            } catch (IOException ignored) {
            }
            serverSocket = null;
        }
        AppLog.info("Wi-Fi upload server stopped");
    }

    public boolean isRunning() {
        return running;
    }

    public int getPort() {
        return port;
    }

    private void handle(Socket socket) {
        try {
            socket.setSoTimeout(60000);
            BufferedInputStream input = new BufferedInputStream(socket.getInputStream());
            String requestLine = readLine(input);
            if (requestLine == null) {
                return;
            }

            int contentLength = 0;
            String contentType = "";
            String line;
            while ((line = readLine(input)) != null && line.length() > 0) {
                String lower = line.toLowerCase(Locale.US);
                if (lower.startsWith("content-length:")) {
                    try {
                        contentLength = Integer.parseInt(line.substring(line.indexOf(':') + 1).trim());
                    } catch (Exception ignored) {}
                } else if (lower.startsWith("content-type:")) {
                    contentType = line.substring(line.indexOf(':') + 1).trim();
                }
            }

            String[] parts = requestLine.split(" ");
            String method = parts.length > 0 ? parts[0] : "";
            String path = parts.length > 1 ? parts[1] : "/";

            OutputStream rawOut = socket.getOutputStream();
            BufferedOutputStream output = new BufferedOutputStream(rawOut);

            if ("GET".equalsIgnoreCase(method)) {
                if ("/logs".equals(path)) {
                    serveLogs(output);
                } else {
                    serveUploadPage(output);
                }
            } else if ("POST".equalsIgnoreCase(method)) {
                if (path.equals("/upload") || path.startsWith("/upload")) {
                    handleUpload(input, output, contentType, contentLength);
                } else {
                    sendResponse(output, 404, "text/plain", "Not Found");
                }
            } else {
                sendResponse(output, 405, "text/plain", "Method Not Allowed");
            }
            output.flush();
        } catch (Exception error) {
            AppLog.error("Error handling Wi-Fi request", error);
        } finally {
            try {
                socket.close();
            } catch (Exception ignored) {
            }
        }
    }

    private void serveUploadPage(OutputStream out) throws IOException {
        boolean isZh = AppText.isChinese();
        String title = isZh ? "N-Reader 局域网传书" : "N-Reader Wi-Fi Transfer";
        String subtitle = isZh ? "支持 TXT, EPUB, MOBI, PDF 格式" : "Supports TXT, EPUB, MOBI, PDF formats";
        String promptText = isZh ? "点击选择电子书文件" : "Click to select e-book files";
        String btnText = isZh ? "发送至电视" : "Send to TV";
        String alertNoFiles = isZh ? "请先选择电子书文件" : "Please select e-book files first";
        String statusUploading = isZh ? "正在上传到电视中，请稍候..." : "Uploading to TV, please wait...";
        String statusSuccess = isZh ? "✓ 上传成功，电视书架已自动刷新。" : "✓ Upload successful! Added to TV library.";
        String statusFailed = isZh ? "✗ 上传失败: " : "✗ Upload failed: ";
        String statusNetworkError = isZh ? "✗ 网络连接异常" : "✗ Network connection error";
        String selectedPrefix = isZh ? "已选 " : "Selected ";
        String selectedSuffix = isZh ? " 个文件: " : " file(s): ";

        String html = "<!DOCTYPE html><html><head><meta charset='utf-8'>"
                + "<meta name='viewport' content='width=device-width, initial-scale=1'>"
                + "<title>" + title + "</title>"
                + "<style>"
                + "body{font-family:-apple-system,BlinkMacSystemFont,sans-serif;margin:0;padding:24px;background:#0f172a;color:#f8fafc;text-align:center}"
                + ".card{max-width:440px;margin:24px auto;background:#1e293b;padding:28px;border-radius:12px;box-shadow:0 4px 20px rgba(0,0,0,0.3);border:1px solid #334155}"
                + "h1{font-size:20px;margin:0 0 8px 0;color:#f8fafc;font-weight:600}"
                + "p{color:#94a3b8;font-size:13px;line-height:1.5;margin:0 0 20px 0}"
                + ".upload-box{border:1px solid #475569;background:#0f172a;border-radius:8px;padding:20px;margin:16px 0;display:block;cursor:pointer}"
                + ".upload-box:hover{border-color:#38bdf8}"
                + "input[type=file]{display:none}"
                + ".btn{width:100%;background:#0284c7;color:#ffffff;padding:12px 0;border-radius:8px;font-weight:600;border:none;cursor:pointer;font-size:14px}"
                + ".btn:hover{background:#0369a1}"
                + "#status{margin-top:14px;font-size:13px;color:#38bdf8}"
                + "</style></head><body>"
                + "<div class='card'>"
                + "<h1>" + title + "</h1>"
                + "<p>" + subtitle + "</p>"
                + "<form id='form' method='POST' action='/upload' enctype='multipart/form-data'>"
                + "<label class='upload-box' for='fileInput' id='boxLabel'>"
                + "<div id='filePrompt' style='font-size:14px;color:#cbd5e1'>" + promptText + "</div>"
                + "<input type='file' id='fileInput' name='file' accept='.txt,.epub,.mobi,.azw,.azw3,.pdf' multiple onchange='onFileSelected(this)'>"
                + "</label>"
                + "<button type='button' class='btn' onclick='uploadFiles()'>" + btnText + "</button>"
                + "<div id='status'></div>"
                + "</form></div>"
                + "<script>"
                + "function onFileSelected(input){"
                + "  var prompt = document.getElementById('filePrompt');"
                + "  if(input.files && input.files.length > 0){"
                + "    prompt.innerText = '" + selectedPrefix + "' + input.files.length + '" + selectedSuffix + "' + input.files[0].name;"
                + "  }"
                + "}"
                + "function uploadFiles(){"
                + "  var input = document.getElementById('fileInput');"
                + "  if(!input.files || input.files.length === 0){ alert('" + alertNoFiles + "'); return; }"
                + "  var status = document.getElementById('status');"
                + "  status.innerText = '" + statusUploading + "';"
                + "  var data = new FormData();"
                + "  for(var i=0; i<input.files.length; i++){ data.append('file', input.files[i]); }"
                + "  var xhr = new XMLHttpRequest();"
                + "  xhr.open('POST', '/upload', true);"
                + "  xhr.onload = function(){ if(xhr.status === 200){ status.innerText = '" + statusSuccess + "'; } else { status.innerText = '" + statusFailed + "' + xhr.responseText; } };"
                + "  xhr.onerror = function(){ status.innerText = '" + statusNetworkError + "'; };"
                + "  xhr.send(data);"
                + "}"
                + "</script></body></html>";

        sendResponse(out, 200, "text/html; charset=UTF-8", html);
    }

    private void serveLogs(OutputStream out) throws IOException {
        String logs = AppLog.readAll();
        sendResponse(out, 200, "text/plain; charset=UTF-8", logs);
    }

    private void handleUpload(InputStream in, OutputStream out, String contentType, int contentLength) throws IOException {
        if (contentLength > MAX_UPLOAD_BYTES) {
            sendResponse(out, 413, "text/plain", "File too large (max 50MB)");
            return;
        }

        String boundary = "";
        String[] cParts = contentType.split(";");
        for (String p : cParts) {
            p = p.trim();
            if (p.startsWith("boundary=")) {
                boundary = p.substring("boundary=".length());
                if (boundary.startsWith("\"") && boundary.endsWith("\"") && boundary.length() >= 2) {
                    boundary = boundary.substring(1, boundary.length() - 1);
                }
            }
        }

        if (boundary.length() == 0) {
            sendResponse(out, 400, "text/plain", "Missing multipart boundary");
            return;
        }

        PushbackInputStream pIn = new PushbackInputStream(in, 65536);
        String boundaryMarker = "--" + boundary;
        byte[] delimiter = ("\r\n--" + boundary).getBytes("US-ASCII");

        String line;
        boolean initialBoundaryFound = false;
        while ((line = readLine(pIn)) != null) {
            if (line.startsWith(boundaryMarker)) {
                initialBoundaryFound = true;
                break;
            }
        }

        if (!initialBoundaryFound) {
            sendResponse(out, 400, "text/plain", "Initial boundary not found");
            return;
        }

        int importedCount = 0;
        try {
            while (true) {
                if (line != null && line.startsWith(boundaryMarker + "--")) {
                    break;
                }

                String partHeader;
                String partFilename = null;
                while ((partHeader = readLine(pIn)) != null && partHeader.length() > 0) {
                    String lower = partHeader.toLowerCase(Locale.US);
                    if (lower.startsWith("content-disposition:")) {
                        int fnIdx = partHeader.indexOf("filename=\"");
                        if (fnIdx >= 0) {
                            int endIdx = partHeader.indexOf("\"", fnIdx + 10);
                            if (endIdx > fnIdx) {
                                partFilename = partHeader.substring(fnIdx + 10, endIdx);
                            }
                        }
                    }
                }

                if (partHeader == null) {
                    break;
                }

                File tempFile = null;
                FileOutputStream fileOut = null;
                boolean isBookFile = false;
                String sanitizedName = null;

                if (partFilename != null && partFilename.trim().length() > 0) {
                    sanitizedName = sanitizeFilename(partFilename);
                    if (BookStore.isSupported(sanitizedName)) {
                        isBookFile = true;
                        tempFile = File.createTempFile("upload_", ".tmp", context.getCacheDir());
                        fileOut = new FileOutputStream(tempFile);
                    }
                }

                try {
                    boolean eof = streamUntilDelimiter(pIn, fileOut, delimiter);
                    if (fileOut != null) {
                        fileOut.flush();
                        fileOut.close();
                        fileOut = null;
                    }

                    if (isBookFile && tempFile != null && tempFile.length() > 0) {
                        File dest = new File(BookStore.getLibraryDir(context), sanitizedName);
                        if (dest.exists()) {
                            dest.delete();
                        }
                        if (!tempFile.renameTo(dest)) {
                            BookStore.copyFile(tempFile, dest);
                            tempFile.delete();
                        }
                        BookStore.recordActivity(context, dest);
                        if (listener != null) {
                            listener.onBookImported(dest.getName());
                        }
                        importedCount++;
                        AppLog.info("Wi-Fi uploaded file saved: " + dest.getAbsolutePath() + " (" + dest.length() + " bytes)");
                    }
                    if (eof) {
                        break;
                    }
                } finally {
                    if (fileOut != null) {
                        try { fileOut.close(); } catch (Exception ignored) {}
                    }
                    if (tempFile != null && tempFile.exists()) {
                        tempFile.delete();
                    }
                }

                line = readLine(pIn);
                if (line == null || line.startsWith("--")) {
                    break;
                }
            }

            if (importedCount > 0) {
                sendResponse(out, 200, "text/plain", "OK");
            } else {
                sendResponse(out, 400, "text/plain", "No valid supported e-book files found in upload");
            }
        } catch (Exception error) {
            AppLog.error("Upload error", error);
            sendResponse(out, 500, "text/plain", "Upload failed: " + error.getMessage());
        }
    }

    private static boolean streamUntilDelimiter(PushbackInputStream in, OutputStream out, byte[] delimiter) throws IOException {
        int L = delimiter.length;
        byte[] chunk = new byte[32 * 1024];
        byte[] carry = new byte[L - 1];
        int carryLen = 0;

        while (true) {
            int read = in.read(chunk);
            if (read == -1) {
                if (out != null && carryLen > 0) {
                    out.write(carry, 0, carryLen);
                }
                return true;
            }

            int total = carryLen + read;
            int foundIdx = -1;

            for (int i = 0; i <= total - L; i++) {
                boolean match = true;
                for (int j = 0; j < L; j++) {
                    byte b = (i + j < carryLen) ? carry[i + j] : chunk[i + j - carryLen];
                    if (b != delimiter[j]) {
                        match = false;
                        break;
                    }
                }
                if (match) {
                    foundIdx = i;
                    break;
                }
            }

            if (foundIdx >= 0) {
                if (out != null) {
                    for (int i = 0; i < foundIdx; i++) {
                        byte b = (i < carryLen) ? carry[i] : chunk[i - carryLen];
                        out.write(b);
                    }
                }
                int remainingStart = foundIdx + L - carryLen;
                if (remainingStart < read) {
                    int unreadLen = read - remainingStart;
                    in.unread(chunk, remainingStart, unreadLen);
                }
                return false;
            }

            int safeToWrite = total - (L - 1);
            if (out != null) {
                for (int i = 0; i < safeToWrite; i++) {
                    byte b = (i < carryLen) ? carry[i] : chunk[i - carryLen];
                    out.write(b);
                }
            }

            byte[] newCarry = new byte[L - 1];
            for (int i = 0; i < L - 1; i++) {
                int srcIdx = safeToWrite + i;
                newCarry[i] = (srcIdx < carryLen) ? carry[srcIdx] : chunk[srcIdx - carryLen];
            }
            carry = newCarry;
            carryLen = L - 1;
        }
    }

    private static String sanitizeFilename(String filename) {
        if (filename == null) return "uploaded_book";
        try {
            filename = URLDecoder.decode(filename, "UTF-8");
        } catch (Exception ignored) {}
        int lastSlash = Math.max(filename.lastIndexOf('/'), filename.lastIndexOf('\\'));
        if (lastSlash >= 0) {
            filename = filename.substring(lastSlash + 1);
        }
        filename = filename.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        if (filename.isEmpty() || filename.equals(".") || filename.equals("..")) {
            filename = "uploaded_book";
        }
        return filename;
    }

    private static void sendResponse(OutputStream out, int status, String mime, String content) throws IOException {
        byte[] bytes = content.getBytes("UTF-8");
        String header = "HTTP/1.1 " + status + " OK\r\n"
                + "Content-Type: " + mime + "\r\n"
                + "Content-Length: " + bytes.length + "\r\n"
                + "Connection: close\r\n"
                + "\r\n";
        out.write(header.getBytes("UTF-8"));
        out.write(bytes);
        out.flush();
    }

    private static String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int c;
        while ((c = in.read()) != -1) {
            if (c == '\n') {
                break;
            }
            if (c != '\r') {
                buffer.write(c);
            }
        }
        if (buffer.size() == 0 && c == -1) {
            return null;
        }
        return buffer.toString("UTF-8");
    }
}
