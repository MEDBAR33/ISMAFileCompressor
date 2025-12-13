package com.ismafilecompressor.util;

import org.apache.tika.Tika;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class FormatDetector {
    private static final Tika tika = new Tika();
    private static final Map<String, String> MIME_TO_CATEGORY = new HashMap<>();
    private static final Map<String, String> MIME_TO_EXTENSION = new HashMap<>();

    static {
        // Initialize MIME to Category mapping
        // Images
        MIME_TO_CATEGORY.put("image/jpeg", "IMAGE");
        MIME_TO_CATEGORY.put("image/png", "IMAGE");
        MIME_TO_CATEGORY.put("image/gif", "IMAGE");
        MIME_TO_CATEGORY.put("image/bmp", "IMAGE");
        MIME_TO_CATEGORY.put("image/tiff", "IMAGE");
        MIME_TO_CATEGORY.put("image/webp", "IMAGE");
        MIME_TO_CATEGORY.put("image/svg+xml", "IMAGE");

        // Documents
        MIME_TO_CATEGORY.put("application/pdf", "PDF");
        MIME_TO_CATEGORY.put("application/msword", "DOCUMENT");
        MIME_TO_CATEGORY.put("application/vnd.openxmlformats-officedocument.wordprocessingml.document", "DOCUMENT");
        MIME_TO_CATEGORY.put("application/vnd.ms-powerpoint", "DOCUMENT");
        MIME_TO_CATEGORY.put("application/vnd.openxmlformats-officedocument.presentationml.presentation", "DOCUMENT");
        MIME_TO_CATEGORY.put("application/vnd.ms-excel", "DOCUMENT");
        MIME_TO_CATEGORY.put("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "DOCUMENT");
        MIME_TO_CATEGORY.put("text/plain", "TEXT");
        MIME_TO_CATEGORY.put("text/rtf", "TEXT");
        MIME_TO_CATEGORY.put("application/rtf", "TEXT");

        // Audio
        MIME_TO_CATEGORY.put("audio/mpeg", "AUDIO");
        MIME_TO_CATEGORY.put("audio/wav", "AUDIO");
        MIME_TO_CATEGORY.put("audio/flac", "AUDIO");
        MIME_TO_CATEGORY.put("audio/aac", "AUDIO");
        MIME_TO_CATEGORY.put("audio/ogg", "AUDIO");
        MIME_TO_CATEGORY.put("audio/x-m4a", "AUDIO");
        MIME_TO_CATEGORY.put("audio/x-ms-wma", "AUDIO");

        // Video
        MIME_TO_CATEGORY.put("video/mp4", "VIDEO");
        MIME_TO_CATEGORY.put("video/x-msvideo", "VIDEO");
        MIME_TO_CATEGORY.put("video/x-matroska", "VIDEO");
        MIME_TO_CATEGORY.put("video/quicktime", "VIDEO");
        MIME_TO_CATEGORY.put("video/x-ms-wmv", "VIDEO");
        MIME_TO_CATEGORY.put("video/x-flv", "VIDEO");
        MIME_TO_CATEGORY.put("video/webm", "VIDEO");

        // Archives
        MIME_TO_CATEGORY.put("application/zip", "ARCHIVE");
        MIME_TO_CATEGORY.put("application/vnd.rar", "ARCHIVE");
        MIME_TO_CATEGORY.put("application/x-7z-compressed", "ARCHIVE");
        MIME_TO_CATEGORY.put("application/x-tar", "ARCHIVE");
        MIME_TO_CATEGORY.put("application/gzip", "ARCHIVE");
        MIME_TO_CATEGORY.put("application/x-bzip2", "ARCHIVE");
        MIME_TO_CATEGORY.put("application/x-xz", "ARCHIVE");

        // Initialize MIME to Extension mapping
        // Images
        MIME_TO_EXTENSION.put("image/jpeg", "jpg");
        MIME_TO_EXTENSION.put("image/png", "png");
        MIME_TO_EXTENSION.put("image/gif", "gif");
        MIME_TO_EXTENSION.put("image/bmp", "bmp");
        MIME_TO_EXTENSION.put("image/tiff", "tiff");
        MIME_TO_EXTENSION.put("image/webp", "webp");
        MIME_TO_EXTENSION.put("image/svg+xml", "svg");

        // Documents
        MIME_TO_EXTENSION.put("application/pdf", "pdf");
        MIME_TO_EXTENSION.put("application/msword", "doc");
        MIME_TO_EXTENSION.put("application/vnd.openxmlformats-officedocument.wordprocessingml.document", "docx");
        MIME_TO_EXTENSION.put("application/vnd.ms-powerpoint", "ppt");
        MIME_TO_EXTENSION.put("application/vnd.openxmlformats-officedocument.presentationml.presentation", "pptx");
        MIME_TO_EXTENSION.put("application/vnd.ms-excel", "xls");
        MIME_TO_EXTENSION.put("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "xlsx");
        MIME_TO_EXTENSION.put("text/plain", "txt");
        MIME_TO_EXTENSION.put("text/rtf", "rtf");
        MIME_TO_EXTENSION.put("application/rtf", "rtf");
    }

    public static FileFormat detect(File file) {
        try {
            String mimeType = tika.detect(file);
            String extension = FileManager.getFileExtension(file.getName());
            String category = getCategory(mimeType);

            return new FileFormat(
                    file.getName(),
                    extension,
                    mimeType,
                    category,
                    FileManager.getFileSize(file)
            );
        } catch (IOException e) {
            // Fallback to extension-based detection
            String extension = FileManager.getFileExtension(file.getName());
            String category = FileManager.getFileCategoryByExtension(extension);
            String mimeType = getMimeTypeFromExtension(extension);

            return new FileFormat(
                    file.getName(),
                    extension,
                    mimeType,
                    category,
                    file.length()
            );
        }
    }

    private static String getCategory(String mimeType) {
        return MIME_TO_CATEGORY.getOrDefault(mimeType, "UNKNOWN");
    }

    private static String getMimeTypeFromExtension(String extension) {
        for (Map.Entry<String, String> entry : MIME_TO_EXTENSION.entrySet()) {
            if (entry.getValue().equalsIgnoreCase(extension)) {
                return entry.getKey();
            }
        }
        return "application/octet-stream";
    }

    public static class FileFormat {
        private final String filename;
        private final String extension;
        private final String mimeType;
        private final String category;
        private final long size;

        public FileFormat(String filename, String extension, String mimeType, String category, long size) {
            this.filename = filename;
            this.extension = extension;
            this.mimeType = mimeType;
            this.category = category;
            this.size = size;
        }

        public String getFilename() { return filename; }
        public String getExtension() { return extension; }
        public String getMimeType() { return mimeType; }
        public String getCategory() { return category; }
        public long getSize() { return size; }

        public boolean isImage() { return "IMAGE".equals(category); }
        public boolean isPdf() { return "PDF".equals(category); }
        public boolean isDocument() { return "DOCUMENT".equals(category); }
        public boolean isAudio() { return "AUDIO".equals(category); }
        public boolean isVideo() { return "VIDEO".equals(category); }
        public boolean isArchive() { return "ARCHIVE".equals(category); }
        public boolean isText() { return "TEXT".equals(category); }

        public String getIconClass() {
            switch (category) {
                case "IMAGE": return "fas fa-file-image";
                case "PDF": return "fas fa-file-pdf";
                case "DOCUMENT": return "fas fa-file-word";
                case "AUDIO": return "fas fa-file-audio";
                case "VIDEO": return "fas fa-file-video";
                case "ARCHIVE": return "fas fa-file-archive";
                case "TEXT": return "fas fa-file-alt";
                default: return "fas fa-file";
            }
        }

        public String getColorClass() {
            switch (category) {
                case "IMAGE": return "image-color";
                case "PDF": return "pdf-color";
                case "DOCUMENT": return "document-color";
                case "AUDIO": return "audio-color";
                case "VIDEO": return "video-color";
                case "ARCHIVE": return "archive-color";
                case "TEXT": return "text-color";
                default: return "default-color";
            }
        }

        @Override
        public String toString() {
            return String.format("%s (%s, %s)", filename, extension.toUpperCase(),
                    FileManager.formatFileSize(size));
        }
    }
}