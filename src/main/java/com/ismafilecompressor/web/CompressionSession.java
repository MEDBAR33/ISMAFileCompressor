package com.ismafilecompressor.web;

import com.ismafilecompressor.model.CompressionOptions;
import com.ismafilecompressor.model.CompressionResult;
import com.ismafilecompressor.model.FileInfo;

import java.util.*;

public class CompressionSession {
    private final String sessionId;
    private String status;
    private List<UploadedFile> files;
    private CompressionOptions options;
    private CompressionResult result;
    private int progress;
    private int processedCount;
    private String currentFile;
    private long startTime;
    private long endTime;
    private String error;
    private List<FileInfo> errors;
    private String tempDir;
    private List<Map<String, Object>> analysis;
    private volatile boolean cancelled = false;
    private List<java.util.concurrent.Future<?>> compressionTasks;

    public CompressionSession(String sessionId) {
        this.sessionId = sessionId;
        this.status = "created";
        this.files = new ArrayList<>();
        this.progress = 0;
        this.processedCount = 0;
        this.errors = new ArrayList<>();
        this.analysis = new ArrayList<>();
        this.compressionTasks = new ArrayList<>();
    }

    public boolean isCancelled() {
        return cancelled;
    }

    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
        if (cancelled) {
            this.status = "cancelled";
            // Cancel all compression tasks
            if (compressionTasks != null) {
                for (java.util.concurrent.Future<?> task : compressionTasks) {
                    if (task != null && !task.isDone()) {
                        task.cancel(true);
                    }
                }
            }
        }
    }

    public void addCompressionTask(java.util.concurrent.Future<?> task) {
        if (compressionTasks != null) {
            compressionTasks.add(task);
        }
    }

    // Getters and Setters
    public String getSessionId() {
        return sessionId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public List<UploadedFile> getFiles() {
        return files;
    }

    public void setFiles(List<UploadedFile> files) {
        this.files = files;
    }

    public CompressionOptions getOptions() {
        return options;
    }

    public void setOptions(CompressionOptions options) {
        this.options = options;
    }

    public CompressionResult getResult() {
        return result;
    }

    public void setResult(CompressionResult result) {
        this.result = result;
    }

    public int getProgress() {
        return progress;
    }

    public void setProgress(int progress) {
        this.progress = progress;
    }

    public int getProcessedCount() {
        return processedCount;
    }

    public void setProcessedCount(int processedCount) {
        this.processedCount = processedCount;
    }

    public String getCurrentFile() {
        return currentFile;
    }

    public void setCurrentFile(String currentFile) {
        this.currentFile = currentFile;
    }

    public long getStartTime() {
        return startTime;
    }

    public void setStartTime(long startTime) {
        this.startTime = startTime;
    }

    public long getEndTime() {
        return endTime;
    }

    public void setEndTime(long endTime) {
        this.endTime = endTime;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public String getTempDir() {
        return tempDir;
    }

    public void setTempDir(String tempDir) {
        this.tempDir = tempDir;
    }

    public List<Map<String, Object>> getAnalysis() {
        return analysis;
    }

    public void setAnalysis(List<Map<String, Object>> analysis) {
        this.analysis = analysis;
    }

    public int getTotalFiles() {
        return files != null ? files.size() : 0;
    }

    public boolean hasErrors() {
        return errors != null && !errors.isEmpty();
    }

    public List<FileInfo> getErrors() {
        return errors;
    }

    public void addError(FileInfo fileInfo, Exception e) {
        if (errors == null) {
            errors = new ArrayList<>();
        }
        errors.add(fileInfo);
        if (error == null || error.isEmpty()) {
            error = e.getMessage();
        }
    }

    public void updateProgress(FileInfo fileInfo, int total, int processed) {
        this.currentFile = fileInfo.getFileName();
        this.processedCount = processed;
        if (total > 0) {
            this.progress = Math.min(100, (int) ((processed * 100.0) / total)); // Cap at 100%
        }
    }

    public String getEstimatedTime() {
        if (startTime == 0 || progress == 0) {
            return "Calculating...";
        }
        
        long elapsed = System.currentTimeMillis() - startTime;
        if (progress > 0) {
            long estimatedTotal = (long) (elapsed / (progress / 100.0));
            long remaining = estimatedTotal - elapsed;
            return formatTime(remaining);
        }
        return "Calculating...";
    }

    public Map<String, Object> getResultSummary() {
        if (result == null) {
            return null;
        }
        
        Map<String, Object> summary = new HashMap<>();
        summary.put("success", result.isSuccess());
        summary.put("message", result.getMessage());
        summary.put("totalFiles", result.getFiles().size());
        summary.put("filesProcessed", result.getFilesProcessed());
        summary.put("filesFailed", result.getFilesFailed());
        summary.put("totalOriginalSize", result.getTotalOriginalSize());
        summary.put("totalCompressedSize", result.getTotalCompressedSize());
        summary.put("totalSaved", result.getTotalSizeSaved());
        summary.put("compressionRatio", result.getOverallCompressionRatio());
        summary.put("overallCompressionRatio", result.getOverallCompressionRatio()); // Alias for frontend
        summary.put("timeMs", result.getTotalTimeMs());
        
        // Include output directory information
        if (options != null && options.getOutputDirectory() != null) {
            summary.put("outputDirectory", options.getOutputDirectory());
        } else {
            summary.put("outputDirectory", com.ismafilecompressor.config.AppConfig.getOutputFolder());
        }
        
        // Add download URLs for each compressed file
        List<Map<String, Object>> downloadFiles = new ArrayList<>();
        for (FileInfo fileInfo : result.getFiles()) {
            if (fileInfo.getCompressed() != null && 
                java.nio.file.Files.exists(fileInfo.getCompressed())) {
                Map<String, Object> fileData = new HashMap<>();
                fileData.put("originalName", fileInfo.getFileName());
                fileData.put("compressedName", fileInfo.getCompressed().getFileName().toString());
                // URL encode the filename for safe transmission
                String encodedName = java.net.URLEncoder.encode(
                    fileInfo.getCompressed().getFileName().toString(), 
                    java.nio.charset.StandardCharsets.UTF_8);
                fileData.put("downloadUrl", "/api/download/" + encodedName + "?sessionId=" + sessionId);
                fileData.put("size", fileInfo.getCompressedSize());
                fileData.put("formattedSize", fileInfo.getFormattedCompressedSize());
                downloadFiles.add(fileData);
            }
        }
        summary.put("downloadFiles", downloadFiles);
        
        return summary;
    }

    private String formatTime(long milliseconds) {
        long seconds = milliseconds / 1000;
        if (seconds < 60) {
            return seconds + "s";
        }
        long minutes = seconds / 60;
        seconds = seconds % 60;
        if (minutes < 60) {
            return minutes + "m " + seconds + "s";
        }
        long hours = minutes / 60;
        minutes = minutes % 60;
        return hours + "h " + minutes + "m";
    }

    // Inner class for uploaded files
    public static class UploadedFile {
        public String id;
        public String name;
        public String path;
        public long size;
        public Date uploadedAt;
    }
}

