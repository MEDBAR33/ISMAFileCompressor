package com.ismafilecompressor.util;

import com.ismafilecompressor.model.FileInfo;
import com.ismafilecompressor.model.CompressionResult;

public class LoggerUtil {

    public static void logFileInfo(FileInfo info) {
        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║ File: " + padRight(info.getFileName(), 54) + "║");
        System.out.println("╠══════════════════════════════════════════════════════════════╣");
        System.out.println("║ Status:      " + padRight(info.getStatus(), 49) + "║");
        System.out.println("║ Original:    " + padRight(info.getFormattedOriginalSize(), 49) + "║");
        System.out.println("║ Compressed:  " + padRight(info.getFormattedCompressedSize(), 49) + "║");
        System.out.println("║ Saved:       " + padRight(info.getFormattedSaved(), 49) + "║");
        System.out.println("║ Ratio:       " + padRight(String.format("%.1f%%", info.getCompressionRatio()), 49) + "║");
        System.out.println("║ Type:        " + padRight(info.getFileType(), 49) + "║");
        System.out.println("║ Timestamp:   " + padRight(info.getTimestamp(), 49) + "║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝");
        System.out.println();
    }

    public static void logCompressionResult(CompressionResult result) {
        System.out.println("╔════════════════════════════════════════════════════════════════════╗");
        System.out.println("║                     COMPRESSION COMPLETE                          ║");
        System.out.println("╠════════════════════════════════════════════════════════════════════╣");
        System.out.println("║ Success:      " + padRight(result.isSuccess() ? "✅ Yes" : "❌ No", 50) + "║");
        System.out.println("║ Files:        " + padRight(result.getFilesProcessed() + " processed, " +
                result.getFilesFailed() + " failed", 50) + "║");
        System.out.println("║ Total Size:   " + padRight(result.getFormattedTotalOriginalSize() + " → " +
                result.getFormattedTotalCompressedSize(), 50) + "║");
        System.out.println("║ Total Saved:  " + padRight(result.getFormattedTotalSaved() +
                String.format(" (%.1f%%)", result.getOverallCompressionRatio()), 50) + "║");
        System.out.println("║ Time Taken:   " + padRight(result.getFormattedTime(), 50) + "║");
        System.out.println("║ Message:      " + padRight(result.getMessage(), 50) + "║");
        System.out.println("╚════════════════════════════════════════════════════════════════════╝");
        System.out.println();
    }

    public static void logInfo(String message) {
        System.out.println("[INFO] " + message);
    }

    public static void logWarning(String message) {
        System.out.println("[WARN] " + message);
    }

    public static void logError(String message) {
        System.out.println("[ERROR] " + message);
    }

    public static void logError(String message, Exception e) {
        System.out.println("[ERROR] " + message);
        if (e != null) {
            System.out.println("       " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    public static void logStartup() {
        System.out.println("\n" +
                "  ╔══════════════════════════════════════════════════════╗\n" +
                "  ║            ISMA FILE COMPRESSOR PRO                  ║\n" +
                "  ║            v2.0 - Enhanced Edition                   ║\n" +
                "  ╠══════════════════════════════════════════════════════╣\n" +
                "  ║  • 30+ File Formats Supported                       ║\n" +
                "  ║  • Smart Compression Algorithms                     ║\n" +
                "  ║  • Web Interface: http://localhost:8080             ║\n" +
                "  ║  • Desktop & Web Modes                              ║\n" +
                "  ╚══════════════════════════════════════════════════════╝\n");
    }

    private static String padRight(String s, int n) {
        return String.format("%-" + n + "s", s);
    }
}