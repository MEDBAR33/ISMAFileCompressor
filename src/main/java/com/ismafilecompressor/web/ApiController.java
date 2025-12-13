package com.ismafilecompressor.web;

import com.google.gson.Gson;
import com.ismafilecompressor.config.AppConfig;
import com.ismafilecompressor.model.*;
import com.ismafilecompressor.service.CompressionService;
import com.ismafilecompressor.util.FileManager;
import com.ismafilecompressor.util.FormatDetector;
import com.ismafilecompressor.util.LoggerUtil;
import spark.Request;
import spark.Response;
import javax.servlet.MultipartConfigElement;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ApiController {
    private final CompressionService compressionService;
    private final Gson gson;
    private final Map<String, CompressionSession> sessions;

    public ApiController(CompressionService compressionService) {
        this.compressionService = compressionService;
        this.gson = new Gson();
        this.sessions = new ConcurrentHashMap<>();
    }

    public Object handleFileAnalysis(Request req, Response res) {
        try {
            String sessionId = UUID.randomUUID().toString();
            Path tempDir = Files.createTempDirectory("compression_" + sessionId);

            List<Map<String, Object>> fileAnalysis = new ArrayList<>();

            req.attribute("org.eclipse.jetty.multipartConfig", new MultipartConfigElement("/temp"));

            for (var part : req.raw().getParts()) {
                if (part.getName().equals("files")) {
                    String filename = part.getSubmittedFileName();
                    Path filePath = tempDir.resolve(filename);

                    try (InputStream input = part.getInputStream();
                         OutputStream output = Files.newOutputStream(filePath)) {
                        input.transferTo(output);
                    }

                    File file = filePath.toFile();
                    FormatDetector.FileFormat format = FormatDetector.detect(file);

                    Map<String, Object> analysis = new HashMap<>();
                    analysis.put("id", UUID.randomUUID().toString());
                    analysis.put("name", filename);
                    analysis.put("size", file.length());
                    analysis.put("formattedSize", FileManager.formatFileSize(file.length()));
                    analysis.put("type", format.getCategory());
                    analysis.put("extension", format.getExtension());
                    analysis.put("mimeType", format.getMimeType());
                    analysis.put("icon", format.getIconClass());
                    analysis.put("color", format.getColorClass());
                    analysis.put("estimatedSave", calculateEstimatedSave(file, format));

                    fileAnalysis.add(analysis);
                }
            }

            Map<String, Object> response = new HashMap<>();
            response.put("sessionId", sessionId);
            response.put("analysis", fileAnalysis);
            response.put("totalFiles", fileAnalysis.size());
            response.put("totalSize", fileAnalysis.stream()
                    .mapToLong(f -> (Long) f.get("size"))
                    .sum());

            // Store in session
            CompressionSession session = new CompressionSession(sessionId);
            session.setTempDir(tempDir.toString());
            session.setAnalysis(fileAnalysis);
            sessions.put(sessionId, session);

            res.type("application/json");
            return gson.toJson(response);

        } catch (Exception e) {
            LoggerUtil.logError("File analysis failed", e);
            res.status(500);
            return errorResponse("Analysis failed: " + e.getMessage());
        }
    }

    public Object handleCompressionStart(Request req, Response res) {
        try {
            Map<String, Object> data = gson.fromJson(req.body(), Map.class);
            String sessionId = (String) data.get("sessionId");
            CompressionSession session = sessions.get(sessionId);

            if (session == null) {
                res.status(404);
                return errorResponse("Session not found");
            }

            // Parse compression options
            CompressionOptions options = parseCompressionOptions(data);

            // Get files from session
            List<File> files = new ArrayList<>();
            for (Map<String, Object> fileAnalysis : session.getAnalysis()) {
                String filename = (String) fileAnalysis.get("name");
                Path filePath = Paths.get(session.getTempDir(), filename);
                files.add(filePath.toFile());
            }

            // Start compression
            session.setStatus("processing");
            session.setOptions(options);

            new Thread(() -> {
                try {
                    CompressionService.CompressionListener listener = new CompressionService.CompressionListener() {
                        @Override
                        public void onProgress(FileInfo fileInfo, int total, int processed) {
                            session.updateProgress(fileInfo, total, processed);
                        }

                        @Override
                        public void onComplete(CompressionResult result) {
                            session.setResult(result);
                            session.setStatus("completed");
                        }

                        @Override
                        public void onError(FileInfo fileInfo, Exception e) {
                            session.addError(fileInfo, e);
                        }
                    };

                    compressionService.addListener(listener);
                    CompressionResult result = compressionService.compressFiles(files, options);
                    compressionService.removeListener(listener);

                } catch (Exception e) {
                    session.setStatus("error");
                    session.setError(e.getMessage());
                    LoggerUtil.logError("Compression failed", e);
                }
            }).start();

            Map<String, Object> response = new HashMap<>();
            response.put("sessionId", sessionId);
            response.put("status", "started");
            response.put("message", "Compression started successfully");
            response.put("totalFiles", files.size());

            res.type("application/json");
            return gson.toJson(response);

        } catch (Exception e) {
            LoggerUtil.logError("Compression start failed", e);
            res.status(500);
            return errorResponse("Compression start failed: " + e.getMessage());
        }
    }

    public Object handleGetProgress(Request req, Response res) {
        String sessionId = req.params("sessionId");
        CompressionSession session = sessions.get(sessionId);

        if (session == null) {
            res.status(404);
            return errorResponse("Session not found");
        }

        Map<String, Object> progress = new HashMap<>();
        progress.put("sessionId", sessionId);
        progress.put("status", session.getStatus());
        progress.put("progress", session.getProgress());
        progress.put("processedFiles", session.getProcessedCount());
        progress.put("totalFiles", session.getTotalFiles());
        progress.put("currentFile", session.getCurrentFile());
        progress.put("estimatedTime", session.getEstimatedTime());

        if (session.getResult() != null) {
            progress.put("result", session.getResultSummary());
        }

        if (session.hasErrors()) {
            progress.put("errors", session.getErrors());
        }

        res.type("application/json");
        return gson.toJson(progress);
    }

    public Object handleGetResults(Request req, Response res) {
        String sessionId = req.params("sessionId");
        CompressionSession session = sessions.get(sessionId);

        if (session == null) {
            res.status(404);
            return errorResponse("Session not found");
        }

        if (!"completed".equals(session.getStatus())) {
            res.status(400);
            return errorResponse("Compression not completed yet");
        }

        CompressionResult result = session.getResult();
        if (result == null) {
            res.status(500);
            return errorResponse("No results available");
        }

        // Prepare download links
        List<Map<String, Object>> downloadLinks = new ArrayList<>();
        for (FileInfo fileInfo : result.getFiles()) {
            Map<String, Object> link = new HashMap<>();
            link.put("originalName", fileInfo.getFileName());
            link.put("compressedName", fileInfo.getCompressed().getFileName().toString());
            link.put("downloadUrl", "/api/download/" + UUID.randomUUID().toString());
            link.put("sizeSaved", fileInfo.getFormattedSaved());
            link.put("compressionRatio", String.format("%.1f%%", fileInfo.getCompressionRatio()));
            downloadLinks.add(link);
        }

        Map<String, Object> response = new HashMap<>();
        response.put("sessionId", sessionId);
        response.put("success", result.isSuccess());
        response.put("message", result.getMessage());
        response.put("stats", Map.of(
                "totalFiles", result.getFilesProcessed(),
                "failedFiles", result.getFilesFailed(),
                "totalOriginalSize", result.getFormattedTotalOriginalSize(),
                "totalCompressedSize", result.getFormattedTotalCompressedSize(),
                "totalSaved", result.getFormattedTotalSaved(),
                "overallRatio", String.format("%.1f%%", result.getOverallCompressionRatio()),
                "timeTaken", result.getFormattedTime()
        ));
        response.put("files", downloadLinks);

        res.type("application/json");
        return gson.toJson(response);
    }

    public Object handleDownload(Request req, Response res) {
        String fileId = req.params("fileId");
        // In a real implementation, you would look up the actual file
        // For now, return a placeholder

        res.type("application/json");
        return gson.toJson(Map.of(
                "fileId", fileId,
                "status", "ready",
                "downloadUrl", "/downloads/" + fileId + ".zip"
        ));
    }

    public Object handleGetSettings(Request req, Response res) {
        Map<String, Object> settings = new HashMap<>();

        // Compression settings
        settings.put("compressionLevels", CompressionOptions.CompressionLevel.getAll().stream()
                .map(l -> Map.of(
                        "name", l.name(),
                        "displayName", l.getDisplayName(),
                        "quality", l.getQuality(),
                        "description", getLevelDescription(l)
                ))
                .toArray());

        settings.put("defaultLevel", AppConfig.getDefaultLevel());
        settings.put("defaultQuality", AppConfig.getDefaultQuality());
        settings.put("outputFolder", AppConfig.getOutputFolder());
        settings.put("keepOriginals", AppConfig.keepOriginals());
        settings.put("theme", AppConfig.getTheme());

        // Format settings
        settings.put("supportedFormats", compressionService.getSupportedFormats());
        settings.put("enabledFormats", AppConfig.getEnabledFormats());

        res.type("application/json");
        return gson.toJson(settings);
    }

    public Object handleUpdateSettings(Request req, Response res) {
        try {
            Map<String, Object> data = gson.fromJson(req.body(), Map.class);

            if (data.containsKey("defaultLevel")) {
                AppConfig.setDefaultLevel((String) data.get("defaultLevel"));
            }

            if (data.containsKey("defaultQuality")) {
                AppConfig.setDefaultQuality(((Number) data.get("defaultQuality")).intValue());
            }

            if (data.containsKey("outputFolder")) {
                AppConfig.setOutputFolder((String) data.get("outputFolder"));
            }

            if (data.containsKey("theme")) {
                AppConfig.setTheme((String) data.get("theme"));
            }

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Settings updated successfully");

            res.type("application/json");
            return gson.toJson(response);

        } catch (Exception e) {
            LoggerUtil.logError("Settings update failed", e);
            res.status(500);
            return errorResponse("Settings update failed: " + e.getMessage());
        }
    }

    public Object handleGetSystemStats(Request req, Response res) {
        Map<String, Object> stats = new HashMap<>();

        // Memory stats
        Runtime runtime = Runtime.getRuntime();
        stats.put("totalMemory", runtime.totalMemory() / 1024 / 1024);
        stats.put("freeMemory", runtime.freeMemory() / 1024 / 1024);
        stats.put("maxMemory", runtime.maxMemory() / 1024 / 1024);
        stats.put("availableProcessors", runtime.availableProcessors());

        // Session stats
        stats.put("activeSessions", sessions.size());
        stats.put("completedSessions", sessions.values().stream()
                .filter(s -> "completed".equals(s.getStatus()))
                .count());

        // Disk stats
        File disk = new File(".");
        stats.put("freeDiskSpace", disk.getFreeSpace() / 1024 / 1024 / 1024);
        stats.put("totalDiskSpace", disk.getTotalSpace() / 1024 / 1024 / 1024);

        res.type("application/json");
        return gson.toJson(stats);
    }

    private CompressionOptions parseCompressionOptions(Map<String, Object> data) {
        CompressionOptions options = new CompressionOptions();

        // Set compression level
        String level = (String) data.getOrDefault("compressionLevel", "BALANCED");
        try {
            options.setCompressionLevel(CompressionOptions.CompressionLevel.valueOf(level.toUpperCase()));
        } catch (IllegalArgumentException e) {
            options.setCompressionLevel(CompressionOptions.CompressionLevel.BALANCED);
        }

        // Set output directory
        String outputDir = (String) data.getOrDefault("outputDirectory", AppConfig.getOutputFolder());
        options.setOutputDirectory(outputDir);

        // Set format conversion
        if (data.containsKey("convertPngToJpeg")) {
            options.setConvertPngToJpeg((Boolean) data.get("convertPngToJpeg"));
        }

        if (data.containsKey("convertTiffToJpeg")) {
            options.setConvertTiffToJpeg((Boolean) data.get("convertTiffToJpeg"));
        }

        if (data.containsKey("resizeImages")) {
            options.setResizeImages((Boolean) data.get("resizeImages"));
        }

        if (data.containsKey("maxWidth")) {
            options.setMaxWidth(((Number) data.get("maxWidth")).intValue());
        }

        if (data.containsKey("maxHeight")) {
            options.setMaxHeight(((Number) data.get("maxHeight")).intValue());
        }

        if (data.containsKey("outputFormat")) {
            options.setOutputFormat((String) data.get("outputFormat"));
        }

        return options;
    }

    private String calculateEstimatedSave(File file, FormatDetector.FileFormat format) {
        long size = file.length();
        double ratio = 0.5; // Default 50% savings

        if (format.isImage()) {
            ratio = 0.6; // Images: 40% savings
        } else if (format.isPdf()) {
            ratio = 0.7; // PDFs: 30% savings
        } else if (format.isDocument()) {
            ratio = 0.3; // Documents: 70% savings
        } else if (format.isArchive()) {
            ratio = 0.1; // Archives: 90% savings (if already compressed)
        }

        return FileManager.formatFileSize((long) (size * ratio));
    }

    private String getLevelDescription(CompressionOptions.CompressionLevel level) {
        switch (level) {
            case MAXIMUM:
                return "Smallest file size, fastest processing";
            case BALANCED:
                return "Good balance between size and quality";
            case BEST_QUALITY:
                return "Highest quality, slower processing";
            default:
                return "Custom settings";
        }
    }

    private String errorResponse(String message) {
        return gson.toJson(Map.of(
                "error", true,
                "message", message,
                "timestamp", new Date().toString()
        ));
    }
}