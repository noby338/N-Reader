package com.noby.nreader;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.os.Build;
import android.util.Log;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class AppLog {
    private static final String TAG = "N-Reader";
    private static final long MAX_LOG_BYTES = 512 * 1024;
    private static final Object LOCK = new Object();
    private static final ExecutorService DISK_WRITER = Executors.newSingleThreadExecutor();
    private static Context context;
    private static boolean abnormalTerminationDetected;

    private AppLog() {
    }

    public static void init(Context value) {
        synchronized (LOCK) {
            if (context != null) {
                return;
            }
            context = value.getApplicationContext();
            rotateIfNeeded();
            String crash = readSmallFile(crashMarkerFile());
            if (crash.length() > 0) {
                abnormalTerminationDetected = true;
                write("ERROR", "Previous process reported a crash: " + crash, null);
                crashMarkerFile().delete();
            }
            String interrupted = readSmallFile(markerFile());
            if (interrupted.length() > 0) {
                abnormalTerminationDetected = true;
                write("ERROR", "Previous process ended during: " + interrupted, null);
                markerFile().delete();
            }
            String version = "1.0.0";
            try {
                PackageInfo info = context.getPackageManager()
                        .getPackageInfo(context.getPackageName(), 0);
                version = info.versionName + " (" + info.versionCode + ")";
            } catch (Exception ignored) {
            }
            write("INFO", "N-Reader started; version=" + version
                    + "; sdk=" + Build.VERSION.SDK_INT
                    + "; abi=" + join(Build.SUPPORTED_ABIS), null);
            installCrashHandler();
        }
    }

    public static void info(String message) {
        write("INFO", message, null);
    }

    public static void warn(String message) {
        write("WARN", message, null);
    }

    public static void error(String message, Throwable error) {
        write("ERROR", message, error);
    }

    public static void beginCriticalOperation(String operation) {
        synchronized (LOCK) {
            writeSmallFile(markerFile(), timestamp() + " " + operation);
            write("INFO", "Critical operation started: " + operation, null);
        }
    }

    public static void endCriticalOperation(String operation) {
        synchronized (LOCK) {
            write("INFO", "Critical operation finished: " + operation, null);
            markerFile().delete();
        }
    }

    public static String readAll() {
        synchronized (LOCK) {
            if (context == null) {
                return "Log system is not initialized.";
            }
            StringBuilder result = new StringBuilder();
            File previous = previousLogFile();
            if (previous.isFile()) {
                result.append("===== Previous log =====\n");
                result.append(readFile(previous));
                result.append("\n");
            }
            result.append("===== Current log =====\n");
            result.append(readFile(logFile()));
            return result.toString();
        }
    }

    public static void clear() {
        synchronized (LOCK) {
            if (context == null) {
                return;
            }
            logFile().delete();
            previousLogFile().delete();
            markerFile().delete();
            crashMarkerFile().delete();
            abnormalTerminationDetected = false;
            write("INFO", "Logs cleared", null);
        }
    }

    public static boolean hasPreviousAbnormalTermination() {
        synchronized (LOCK) {
            return abnormalTerminationDetected;
        }
    }

    private static void installCrashHandler() {
        final Thread.UncaughtExceptionHandler previous =
                Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(
                new Thread.UncaughtExceptionHandler() {
                    @Override
                    public void uncaughtException(Thread thread, Throwable error) {
                        synchronized (LOCK) {
                            writeSmallFile(crashMarkerFile(),
                                    timestamp() + " thread=" + thread.getName()
                                            + " error=" + error.getClass().getName());
                        }
                        writeSync("ERROR", "Uncaught exception on thread " + thread.getName(), error);
                        if (previous != null) {
                            previous.uncaughtException(thread, error);
                        } else {
                            android.os.Process.killProcess(android.os.Process.myPid());
                            System.exit(10);
                        }
                    }
                });
    }

    private static void write(final String level, final String message, final Throwable error) {
        Log.println("ERROR".equals(level) ? Log.ERROR : Log.INFO, TAG, level + " " + message);
        if (context == null) {
            return;
        }
        final String ts = timestamp();
        final String threadName = Thread.currentThread().getName();
        final String stackStr;
        if (error != null) {
            StringWriter sw = new StringWriter();
            error.printStackTrace(new PrintWriter(sw));
            stackStr = sw.toString();
        } else {
            stackStr = null;
        }

        DISK_WRITER.submit(new Runnable() {
            @Override
            public void run() {
                writeToDisk(level, message, ts, threadName, stackStr);
            }
        });
    }

    private static void writeSync(String level, String message, Throwable error) {
        Log.println("ERROR".equals(level) ? Log.ERROR : Log.INFO, TAG, level + " " + message);
        String ts = timestamp();
        String threadName = Thread.currentThread().getName();
        String stackStr = null;
        if (error != null) {
            StringWriter sw = new StringWriter();
            error.printStackTrace(new PrintWriter(sw));
            stackStr = sw.toString();
        }
        writeToDisk(level, message, ts, threadName, stackStr);
    }

    private static void writeToDisk(String level, String message, String ts, String threadName, String stackStr) {
        synchronized (LOCK) {
            if (context == null) {
                return;
            }
            rotateIfNeeded();
            BufferedWriter writer = null;
            try {
                writer = new BufferedWriter(new OutputStreamWriter(
                        new FileOutputStream(logFile(), true), "UTF-8"));
                writer.write(ts);
                writer.write(" [");
                writer.write(level);
                writer.write("] [");
                writer.write(threadName);
                writer.write("] ");
                writer.write(message == null ? "" : message);
                writer.newLine();
                if (stackStr != null) {
                    writer.write(stackStr);
                }
                writer.flush();
            } catch (Exception ignored) {
            } finally {
                if (writer != null) {
                    try {
                        writer.close();
                    } catch (Exception ignored) {
                    }
                }
            }
        }
    }

    private static void rotateIfNeeded() {
        if (context == null) {
            return;
        }
        File current = logFile();
        if (current.length() < MAX_LOG_BYTES) {
            return;
        }
        File previous = previousLogFile();
        previous.delete();
        current.renameTo(previous);
    }

    private static File logDirectory() {
        File directory = new File(context.getFilesDir(), "logs");
        if (!directory.exists()) {
            directory.mkdirs();
        }
        return directory;
    }

    private static File logFile() {
        return new File(logDirectory(), "app.log");
    }

    private static File previousLogFile() {
        return new File(logDirectory(), "app.previous.log");
    }

    private static File markerFile() {
        return new File(logDirectory(), "critical-operation.txt");
    }

    private static File crashMarkerFile() {
        return new File(logDirectory(), "java-crash.txt");
    }

    private static String readFile(File file) {
        if (!file.isFile()) {
            return "(empty)\n";
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        FileInputStream input = null;
        try {
            input = new FileInputStream(file);
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) {
                output.write(buffer, 0, count);
            }
            return output.toString("UTF-8");
        } catch (Exception error) {
            return "Unable to read log: " + error.getMessage() + "\n";
        } finally {
            if (input != null) {
                try {
                    input.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    private static String readSmallFile(File file) {
        if (context == null || !file.isFile()) {
            return "";
        }
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new InputStreamReader(
                    new FileInputStream(file), "UTF-8"));
            String value = reader.readLine();
            return value == null ? "" : value;
        } catch (Exception ignored) {
            return "";
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    private static void writeSmallFile(File file, String value) {
        BufferedWriter writer = null;
        try {
            writer = new BufferedWriter(new OutputStreamWriter(
                    new FileOutputStream(file, false), "UTF-8"));
            writer.write(value);
            writer.flush();
        } catch (Exception ignored) {
        } finally {
            if (writer != null) {
                try {
                    writer.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    private static String timestamp() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
                .format(new Date());
    }

    private static String join(String[] values) {
        if (values == null || values.length == 0) {
            return "unknown";
        }
        StringBuilder result = new StringBuilder();
        for (String value : values) {
            if (result.length() > 0) {
                result.append(',');
            }
            result.append(value);
        }
        return result.toString();
    }
}
