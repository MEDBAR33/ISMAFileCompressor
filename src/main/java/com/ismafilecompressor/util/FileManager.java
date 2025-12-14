package com.ismafilecompressor.util;

import java.io.*;
import java.nio.file.*;
import java.util.*;

public class FileManager {
    // Supported file extensions
    public static final Set<String> IMAGE_EXTENSIONS = Set.of(
            "jpg", "jpeg", "png", "gif", "bmp", "tiff", "tif", "webp", "svg"
    );

    public static final Set<String> DOCUMENT_EXTENSIONS = Set.of(
            "pdf", "doc", "docx", "ppt", "pptx", "xls", "xlsx", "txt", "rtf", "odt", "ods", "odp"
    );

    public static final Set<String> AUDIO_EXTENSIONS = Set.of(
            "mp3", "wav", "flac", "aac", "ogg", "m4a", "wma"
    );

    public static final Set<String> VIDEO_EXTENSIONS = Set.of(
            "mp4", "avi", "mkv", "mov", "wmv", "flv", "webm", "m4v", "mpg", "mpeg"
    );

    public static final Set<String> ARCHIVE_EXTENSIONS = Set.of(
            "zip", "rar", "7z", "tar", "gz", "bz2", "xz"
    );

    public static Set<String> getAllSupportedExtensions() {
        Set<String> all = new HashSet<>();
        all.addAll(IMAGE_EXTENSIONS);
        all.addAll(DOCUMENT_EXTENSIONS);
        all.addAll(AUDIO_EXTENSIONS);
        all.addAll(VIDEO_EXTENSIONS);
        all.addAll(ARCHIVE_EXTENSIONS);
        return all;
    }

    public static String getFileExtension(String filename) {
        int dotIndex = filename.lastIndexOf('.');
        return (dotIndex == -1) ? "" : filename.substring(dotIndex + 1).toLowerCase();
    }

    public static String getFileCategory(String filename) {
        String ext = getFileExtension(filename);

        if (IMAGE_EXTENSIONS.contains(ext)) return "IMAGE";
        if (DOCUMENT_EXTENSIONS.contains(ext)) return "DOCUMENT";
        if (AUDIO_EXTENSIONS.contains(ext)) return "AUDIO";
        if (VIDEO_EXTENSIONS.contains(ext)) return "VIDEO";
        if (ARCHIVE_EXTENSIONS.contains(ext)) return "ARCHIVE";

        return "UNKNOWN";
    }

    public static String getFileCategoryByExtension(String extension) {
        String ext = extension.toLowerCase();

        if (IMAGE_EXTENSIONS.contains(ext)) return "IMAGE";
        if (DOCUMENT_EXTENSIONS.contains(ext)) return "DOCUMENT";
        if (AUDIO_EXTENSIONS.contains(ext)) return "AUDIO";
        if (VIDEO_EXTENSIONS.contains(ext)) return "VIDEO";
        if (ARCHIVE_EXTENSIONS.contains(ext)) return "ARCHIVE";

        return "UNKNOWN";
    }

    public static boolean isSupportedFile(File file) {
        String ext = getFileExtension(file.getName());
        return getAllSupportedExtensions().contains(ext);
    }

    public static File createOutputFile(File input, String prefix, String outputDir,
                                        String newExtension) throws IOException {
        if (input == null) {
            throw new IllegalArgumentException("Input file cannot be null");
        }
        
        Path parent;
        if (outputDir != null && !outputDir.trim().isEmpty()) {
            parent = Paths.get(outputDir);
            Files.createDirectories(parent);
        } else {
            Path inputPath = input.toPath().getParent();
            if (inputPath == null) {
                parent = Paths.get(System.getProperty("user.dir"));
            } else {
                parent = inputPath;
            }
        }

        String originalName = input.getName();
        int dotIndex = originalName.lastIndexOf('.');
        String baseName = (dotIndex > 0) ? originalName.substring(0, dotIndex) : originalName;

        String extension = (newExtension != null && !newExtension.isEmpty()) ?
                newExtension : getFileExtension(originalName);

        String outputName = prefix + baseName + "." + extension;
        Path outputPath = parent.resolve(outputName);

        // Avoid duplicate names
        int counter = 1;
        while (Files.exists(outputPath)) {
            outputName = prefix + baseName + "_" + counter + "." + extension;
            outputPath = parent.resolve(outputName);
            counter++;
        }

        return outputPath.toFile();
    }

    public static long getFileSize(File file) {
        if (file == null || !file.exists()) {
            return 0;
        }
        try {
            return Files.size(file.toPath());
        } catch (IOException e) {
            return file.length();
        }
    }

    public static String formatFileSize(long bytes) {
        if (bytes < 0) return "0 B";
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        return String.format("%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0));
    }

    public static List<File> scanDirectory(File directory, boolean recursive) {
        List<File> files = new ArrayList<>();
        if (!directory.isDirectory()) return files;

        File[] fileArray = directory.listFiles();
        if (fileArray == null) return files;

        for (File file : fileArray) {
            if (file.isFile() && isSupportedFile(file)) {
                files.add(file);
            } else if (file.isDirectory() && recursive) {
                files.addAll(scanDirectory(file, true));
            }
        }

        return files;
    }

    public static void copyFile(File source, File destination) throws IOException {
        Files.copy(source.toPath(), destination.toPath(),
                StandardCopyOption.REPLACE_EXISTING);
    }

    public static void moveFile(File source, File destination) throws IOException {
        Files.move(source.toPath(), destination.toPath(),
                StandardCopyOption.REPLACE_EXISTING);
    }

    public static boolean deleteFile(File file) {
        try {
            return Files.deleteIfExists(file.toPath());
        } catch (IOException e) {
            return false;
        }
    }

    public static String getMimeType(File file) {
        try {
            return Files.probeContentType(file.toPath());
        } catch (IOException e) {
            return "application/octet-stream";
        }
    }

    public static String getFileIconClass(String filename) {
        String category = getFileCategory(filename);

        switch (category) {
            case "IMAGE": return "fas fa-file-image";
            case "PDF": return "fas fa-file-pdf";
            case "DOCUMENT": return "fas fa-file-word";
            case "AUDIO": return "fas fa-file-audio";
            case "VIDEO": return "fas fa-file-video";
            case "ARCHIVE": return "fas fa-file-archive";
            default: return "fas fa-file";
        }
    }
}