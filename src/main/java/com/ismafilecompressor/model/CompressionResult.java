package com.ismafilecompressor.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

public class CompressionResult {
    private boolean success;
    private String message;
    private List<FileInfo> files;
    private long totalOriginalSize;
    private long totalCompressedSize;
    private long totalTimeMs;
    private long timestamp;
    private int filesProcessed;
    private int filesFailed;

    public CompressionResult() {
        this.files = new ArrayList<>();
        this.timestamp = System.currentTimeMillis();
    }

    // Getters and Setters
    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public List<FileInfo> getFiles() { return files; }
    public void setFiles(List<FileInfo> files) { this.files = files; }
    public void addFile(FileInfo file) { this.files.add(file); }

    public long getTotalOriginalSize() { return totalOriginalSize; }
    public void setTotalOriginalSize(long totalOriginalSize) { this.totalOriginalSize = totalOriginalSize; }

    public long getTotalCompressedSize() { return totalCompressedSize; }
    public void setTotalCompressedSize(long totalCompressedSize) { this.totalCompressedSize = totalCompressedSize; }

    public long getTotalTimeMs() { return totalTimeMs; }
    public void setTotalTimeMs(long totalTimeMs) { this.totalTimeMs = totalTimeMs; }

    public int getFilesProcessed() { return filesProcessed; }
    public void setFilesProcessed(int filesProcessed) { this.filesProcessed = filesProcessed; }

    public int getFilesFailed() { return filesFailed; }
    public void setFilesFailed(int filesFailed) { this.filesFailed = filesFailed; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    // Calculated properties
    public long getTotalSizeSaved() {
        return totalOriginalSize - totalCompressedSize;
    }

    public double getOverallCompressionRatio() {
        if (totalOriginalSize == 0) return 0;
        return (1 - ((double) totalCompressedSize / totalOriginalSize)) * 100.0;
    }

    public double getAverageCompressionRatio() {
        if (files.isEmpty()) return 0;
        double sum = files.stream()
                .mapToDouble(FileInfo::getCompressionRatio)
                .sum();
        return sum / files.size();
    }

    public String getFormattedTotalOriginalSize() {
        return formatFileSize(totalOriginalSize);
    }

    public String getFormattedTotalCompressedSize() {
        return formatFileSize(totalCompressedSize);
    }

    public String getFormattedTotalSaved() {
        return formatFileSize(getTotalSizeSaved());
    }

    public String getFormattedTime() {
        if (totalTimeMs < 1000) return totalTimeMs + " ms";
        if (totalTimeMs < 60000) return String.format("%.1f seconds", totalTimeMs / 1000.0);
        long minutes = totalTimeMs / 60000;
        long seconds = (totalTimeMs % 60000) / 1000;
        return String.format("%d min %d sec", minutes, seconds);
    }

    private String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        return String.format("%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0));
    }

    public Map<String, Object> getResultSummary() {
        Map<String, Object> summary = new HashMap<>();
        summary.put("success", success);
        summary.put("message", message);
        summary.put("filesProcessed", filesProcessed);
        summary.put("filesFailed", filesFailed);
        summary.put("totalFiles", files.size());
        summary.put("totalOriginalSize", totalOriginalSize);
        summary.put("totalCompressedSize", totalCompressedSize);
        summary.put("totalSaved", getTotalSizeSaved());
        summary.put("overallCompressionRatio", getOverallCompressionRatio());
        summary.put("averageCompressionRatio", getAverageCompressionRatio());
        summary.put("formattedOriginalSize", getFormattedTotalOriginalSize());
        summary.put("formattedCompressedSize", getFormattedTotalCompressedSize());
        summary.put("formattedSaved", getFormattedTotalSaved());
        summary.put("timeMs", totalTimeMs);
        summary.put("formattedTime", getFormattedTime());
        return summary;
    }

    @Override
    public String toString() {
        return String.format("CompressionResult{success=%s, processed=%d, saved=%.1f%%, time=%s}",
                success, filesProcessed, getOverallCompressionRatio(), getFormattedTime());
    }
}