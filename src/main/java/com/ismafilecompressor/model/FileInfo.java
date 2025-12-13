package com.ismafilecompressor.model;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class FileInfo {
    private Path original;
    private Path compressed;
    private long originalSize;
    private long compressedSize;
    private String status = "Pending";
    private String timestamp;
    private String fileType;
    private String mimeType;
    private String compressionLevel = "Balanced";
    private String errorMessage;

    public FileInfo() {
        this.timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    public FileInfo(Path original) {
        this();
        this.original = original;
    }

    // Getters and Setters
    public Path getOriginal() { return original; }
    public void setOriginal(Path original) { this.original = original; }

    public Path getCompressed() { return compressed; }
    public void setCompressed(Path compressed) { this.compressed = compressed; }

    public long getOriginalSize() { return originalSize; }
    public void setOriginalSize(long originalSize) { this.originalSize = originalSize; }

    public long getCompressedSize() { return compressedSize; }
    public void setCompressedSize(long compressedSize) { this.compressedSize = compressedSize; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getTimestamp() { return timestamp; }

    public String getFileType() { return fileType; }
    public void setFileType(String fileType) { this.fileType = fileType; }

    public String getMimeType() { return mimeType; }
    public void setMimeType(String mimeType) { this.mimeType = mimeType; }

    public String getCompressionLevel() { return compressionLevel; }
    public void setCompressionLevel(String compressionLevel) { this.compressionLevel = compressionLevel; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    // Calculated properties
    public double getCompressionRatio() {
        if (originalSize == 0) return 0;
        return (1 - ((double) compressedSize / originalSize)) * 100.0;
    }

    public long getSizeSaved() {
        return originalSize - compressedSize;
    }

    public String getFormattedOriginalSize() {
        return formatFileSize(originalSize);
    }

    public String getFormattedCompressedSize() {
        return formatFileSize(compressedSize);
    }

    public String getFormattedSaved() {
        return formatFileSize(getSizeSaved());
    }

    public String getFileName() {
        return original != null ? original.getFileName().toString() : "";
    }

    private String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        return String.format("%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0));
    }

    @Override
    public String toString() {
        return String.format("FileInfo{name='%s', status='%s', saved=%.1f%%}",
                getFileName(), status, getCompressionRatio());
    }
}