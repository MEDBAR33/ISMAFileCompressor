package com.ismafilecompressor.web;

import static spark.Spark.*;
import javax.servlet.MultipartConfigElement;
import com.google.gson.Gson;
import com.ismafilecompressor.config.AppConfig;
import com.ismafilecompressor.service.CompressionService;
import com.ismafilecompressor.model.*;
import com.ismafilecompressor.util.LoggerUtil;
import spark.Request;
import spark.Response;
import spark.staticfiles.StaticFilesConfiguration;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

public class WebServer {
    private final int port;
    private final CompressionService compressionService;
    private final Gson gson;
    private final Map<String, CompressionSession> sessions;
    private final ExecutorService executorService;

    public WebServer(int port) {
        this.port = port;
        this.compressionService = new CompressionService();
        this.gson = new Gson();
        this.sessions = new ConcurrentHashMap<>();
        this.executorService = Executors.newCachedThreadPool();
    }

    public void start() {
        port(port);

        // Configure static files
        StaticFilesConfiguration staticFiles = new StaticFilesConfiguration();
        staticFiles.configure("/public");

        before((request, response) -> {
            // CORS headers
            response.header("Access-Control-Allow-Origin", "*");
            response.header("Access-Control-Allow-Methods", "GET,POST,PUT,DELETE,OPTIONS");
            response.header("Access-Control-Allow-Headers", "Content-Type,Authorization,X-Requested-With");
            response.header("Access-Control-Allow-Credentials", "true");

            // Log request
            LoggerUtil.logInfo(String.format("%s %s", request.requestMethod(), request.pathInfo()));
        });

        // API Routes - MUST come BEFORE static file handler
        path("/api", () -> {
            // System info
            get("/info", this::getSystemInfo);

            // File upload
            post("/upload", this::handleUpload);

            // Compression
            post("/compress", this::handleCompression);
            get("/status/:sessionId", this::getCompressionProgress);
            get("/progress/:sessionId", this::getCompressionProgress);
            post("/cancel/:sessionId", this::cancelCompression);

            // Results
            get("/results/:sessionId", this::getResults);
            get("/download/:fileId", this::downloadFile);
            get("/download-all/:sessionId", this::downloadAll);

            // Settings
            get("/settings", this::getSettings);
            post("/settings", this::updateSettings);

            // Formats
            get("/formats", this::getSupportedFormats);

            // Session management
            delete("/session/:sessionId", this::deleteSession);
            
            // Open folder in file explorer
            post("/open-folder", this::openFolder);
        });

        // Serve static files - MUST come AFTER API routes
        get("/*", (request, response) -> {
            staticFiles.consume(request.raw(), response.raw());
            return "";
        });

        // WebSocket for real-time updates (commented out - requires additional setup)
        // webSocket("/ws/compression", CompressionWebSocketHandler.class);

        // Error handling
        exception(Exception.class, (exception, request, response) -> {
            response.status(500);
            response.type("application/json");
            response.body(gson.toJson(Map.of(
                    "error", exception.getMessage(),
                    "timestamp", new Date().toString()
            )));
            LoggerUtil.logError("API Error", exception);
        });

        // Start server
        init();
        LoggerUtil.logInfo("Web server started on port " + port);
        LoggerUtil.logInfo("Web interface available at: http://localhost:" + port);
    }

    public void stop() {
        spark.Spark.stop();
        compressionService.shutdown();
        executorService.shutdown();
    }

    private Object getSystemInfo(Request req, Response res) {
        Map<String, Object> info = new HashMap<>();
        info.put("name", "FileCompressor Pro");
        info.put("version", "2.0");
        info.put("javaVersion", System.getProperty("java.version"));
        info.put("maxMemory", Runtime.getRuntime().maxMemory() / 1024 / 1024 + " MB");
        info.put("availableProcessors", Runtime.getRuntime().availableProcessors());
        info.put("supportedFormats", compressionService.getSupportedFormats());

        res.type("application/json");
        return gson.toJson(info);
    }

    private Object handleUpload(Request req, Response res) {
        try {
            String sessionId = UUID.randomUUID().toString();
            CompressionSession session = new CompressionSession(sessionId);
            sessions.put(sessionId, session);

            // Create upload directory
            Path uploadDir = Paths.get("uploads", sessionId);
            Files.createDirectories(uploadDir);

            // Handle file upload
            req.attribute("org.eclipse.jetty.multipartConfig", new MultipartConfigElement("/temp"));

            List<CompressionSession.UploadedFile> uploadedFiles = new ArrayList<>();
            
            if (req.raw().getContentType() != null && req.raw().getContentType().contains("multipart")) {
                for (var part : req.raw().getParts()) {
                    if (part.getName().equals("files")) {
                        String fileName = part.getSubmittedFileName();
                        if (fileName == null || fileName.isEmpty()) {
                            continue;
                        }
                        
                        Path filePath = uploadDir.resolve(fileName);

                        try (InputStream input = part.getInputStream();
                             OutputStream output = Files.newOutputStream(filePath)) {
                            input.transferTo(output);
                        }

                        CompressionSession.UploadedFile uf = new CompressionSession.UploadedFile();
                        uf.id = UUID.randomUUID().toString();
                        uf.name = fileName;
                        uf.path = filePath.toString();
                        uf.size = Files.size(filePath);
                        uf.uploadedAt = new Date();

                        uploadedFiles.add(uf);
                    }
                }
            }

            if (uploadedFiles.isEmpty()) {
                res.status(400);
                res.type("application/json");
                return "{\"error\":\"No files uploaded\"}";
            }

            session.setFiles(uploadedFiles);

            Map<String, Object> response = new HashMap<>();
            response.put("sessionId", sessionId);
            response.put("files", uploadedFiles);
            response.put("totalFiles", uploadedFiles.size());
            response.put("totalSize", uploadedFiles.stream().mapToLong(f -> f.size).sum());

            res.type("application/json");
            return gson.toJson(response);
            
        } catch (Exception e) {
            LoggerUtil.logError("Upload failed", e);
            res.status(500);
            res.type("application/json");
            return "{\"error\":\"Upload failed: " + e.getMessage() + "\"}";
        }
    }

    private Object handleCompression(Request req, Response res) {
        try {
            Map<String, Object> data = gson.fromJson(req.body(), Map.class);
            String sessionId = (String) data.get("sessionId");
            
            if (sessionId == null || sessionId.isEmpty()) {
                res.status(400);
                return "{\"error\":\"Session ID is required\"}";
            }
            
            CompressionSession session = sessions.get(sessionId);

            if (session == null) {
                res.status(404);
                return "{\"error\":\"Session not found\"}";
            }

            if (session.getFiles().isEmpty()) {
                res.status(400);
                return "{\"error\":\"No files to compress\"}";
            }

            // Parse compression options from request
            CompressionOptions options;
            try {
                options = parseOptions(data);
            } catch (Exception e) {
                res.status(400);
                return "{\"error\":\"Invalid compression options: " + e.getMessage() + "\"}";
            }

            // Ensure output directory is set
            if (options.getOutputDirectory() == null || options.getOutputDirectory().trim().isEmpty()) {
                String outputDir = com.ismafilecompressor.config.AppConfig.getOutputFolder();
                options.setOutputDirectory(outputDir);
                LoggerUtil.logInfo("Output directory set to: " + outputDir);
            }

            // Start compression in background
            Future<?> compressionFuture = executorService.submit(() -> {
                try {
                    session.setStatus("processing");
                    session.setStartTime(System.currentTimeMillis());

                    List<File> files = new ArrayList<>();
                    for (CompressionSession.UploadedFile uf : session.getFiles()) {
                        File file = new File(uf.path);
                        if (file.exists()) {
                            files.add(file);
                        } else {
                            LoggerUtil.logWarning("File not found: " + uf.path);
                        }
                    }
                    
                    if (files.isEmpty()) {
                        session.setStatus("error");
                        session.setError("No valid files found");
                        return;
                    }

                    // Add progress listener
                    CompressionService.CompressionListener listener = new CompressionService.CompressionListener() {
                        @Override
                        public void onProgress(FileInfo fileInfo, int total, int processed) {
                            session.setCurrentFile(fileInfo.getFileName());
                            session.setProcessedCount(processed);
                            int progress = total > 0 ? (int) ((processed * 100.0) / total) : 0;
                            progress = Math.min(100, progress); // Cap at 100%
                            session.setProgress(progress);
                            LoggerUtil.logInfo(String.format("Progress: %d/%d files (%d%%)", processed, total, progress));
                        }

                        @Override
                        public void onComplete(CompressionResult result) {
                            session.setResult(result);
                            session.setProgress(100); // Ensure 100% on completion
                            session.setProcessedCount(result.getFilesProcessed());
                            // Only set to completed if there are successful files
                            // If all files failed, status should be error (set in main thread)
                            int totalFiles = session.getFiles().size();
                            int failedFiles = result.getFilesFailed();
                            int successFiles = result.getFilesProcessed() - failedFiles;
                            
                            if (totalFiles > 0 && successFiles == 0) {
                                // All files failed
                                session.setStatus("error");
                                session.setError("All files failed to compress: " + result.getMessage());
                                LoggerUtil.logInfo("Compression failed: " + result.getMessage());
                            } else {
                                // At least one file succeeded
                                session.setStatus("completed");
                                LoggerUtil.logInfo("Compression completed: " + result.getMessage());
                            }
                            session.setEndTime(System.currentTimeMillis());
                        }

                        @Override
                        public void onError(FileInfo fileInfo, Exception e) {
                            session.addError(fileInfo, e);
                            LoggerUtil.logError("File compression error: " + fileInfo.getFileName(), e);
                        }
                    };

                    compressionService.addListener(listener);
                    // Pass cancellation checker
                    CompressionResult result = compressionService.compressFiles(files, options, 
                        () -> session.isCancelled());
                    compressionService.removeListener(listener);
                    
                    // Ensure completion status is set
                    session.setResult(result);
                    session.setProgress(100);
                    session.setProcessedCount(result.getFilesProcessed());
                    // If all files failed, set status to error
                    if (result.getFilesFailed() > 0 && result.getFilesFailed() >= result.getFilesProcessed()) {
                        session.setStatus("error");
                        session.setError("All files failed to compress: " + result.getMessage());
                    } else {
                        session.setStatus("completed");
                    }
                    session.setEndTime(System.currentTimeMillis());

                } catch (Exception e) {
                    session.setStatus("error");
                    session.setError(e.getMessage() != null ? e.getMessage() : "Compression failed: " + e.getClass().getSimpleName());
                    session.setEndTime(System.currentTimeMillis());
                    session.setProgress(100); // Set to 100% so frontend knows it's done
                    LoggerUtil.logError("Compression failed", e);
                }
            });
            
            // Store the future for cancellation
            session.addCompressionTask(compressionFuture);

            res.type("application/json");
            return "{\"status\":\"started\",\"sessionId\":\"" + sessionId + "\"}";
            
        } catch (Exception e) {
            LoggerUtil.logError("Compression start failed", e);
            res.status(500);
            res.type("application/json");
            return "{\"error\":\"Compression start failed: " + e.getMessage() + "\"}";
        }
    }

    private CompressionOptions parseOptions(Map<String, Object> data) {
        CompressionOptions options = new CompressionOptions();

        // Set compression level
        String level = (String) data.getOrDefault("level", "balanced");
        try {
            // Handle different level formats
            level = level.toLowerCase();
            if (level.equals("maximum")) {
                options.setCompressionLevel(CompressionOptions.CompressionLevel.MAXIMUM);
            } else if (level.equals("balanced")) {
                options.setCompressionLevel(CompressionOptions.CompressionLevel.BALANCED);
            } else if (level.equals("best") || level.equals("best_quality")) {
                options.setCompressionLevel(CompressionOptions.CompressionLevel.BEST_QUALITY);
            } else if (level.equals("custom")) {
                options.setCompressionLevel(CompressionOptions.CompressionLevel.CUSTOM);
            } else {
                // Try to parse as enum value
                options.setCompressionLevel(CompressionOptions.CompressionLevel.valueOf(level.toUpperCase()));
            }
        } catch (IllegalArgumentException e) {
            // Default to balanced if invalid
            LoggerUtil.logWarning("Invalid compression level: " + level + ", defaulting to BALANCED");
            options.setCompressionLevel(CompressionOptions.CompressionLevel.BALANCED);
        }

        // Set output directory
        String outputDir = (String) data.getOrDefault("outputDir", AppConfig.getOutputFolder());
        options.setOutputDirectory(outputDir);

        // Set other options
        if (data.containsKey("resize")) {
            Object resizeValue = data.get("resize");
            if (resizeValue instanceof Boolean) {
                options.setResizeImages((Boolean) resizeValue);
            } else if (resizeValue instanceof String) {
                options.setResizeImages(Boolean.parseBoolean((String) resizeValue));
            }
        }

        if (data.containsKey("maxWidth")) {
            Object maxWidthValue = data.get("maxWidth");
            if (maxWidthValue instanceof Number) {
                options.setMaxWidth(((Number) maxWidthValue).intValue());
            } else if (maxWidthValue instanceof String) {
                options.setMaxWidth(Integer.parseInt((String) maxWidthValue));
            }
        }

        if (data.containsKey("maxHeight")) {
            Object maxHeightValue = data.get("maxHeight");
            if (maxHeightValue instanceof Number) {
                options.setMaxHeight(((Number) maxHeightValue).intValue());
            } else if (maxHeightValue instanceof String) {
                options.setMaxHeight(Integer.parseInt((String) maxHeightValue));
            }
        }

        return options;
    }

    private Object getCompressionProgress(Request req, Response res) {
        try {
            String sessionId = req.params(":sessionId");
            CompressionSession session = sessions.get(sessionId);

            if (session == null) {
                res.status(404);
                res.type("application/json");
                return "{\"error\":\"Session not found\"}";
            }

            Map<String, Object> progress = new HashMap<>();
            progress.put("sessionId", sessionId);
            progress.put("status", session.getStatus() != null ? session.getStatus() : "processing");
        
        // Calculate progress percentage
        int totalFiles = session.getFiles().size();
        int processedCount = session.getProcessedCount();
        int progressPercent = totalFiles > 0 ? Math.round((processedCount * 100.0f) / totalFiles) : 0;
        progressPercent = Math.max(progressPercent, session.getProgress()); // Use higher value
        progressPercent = Math.min(100, progressPercent); // Cap at 100%
        
        progress.put("progress", progressPercent);
        progress.put("totalFiles", totalFiles);
        progress.put("processedFiles", processedCount);
        progress.put("currentFile", session.getCurrentFile());
        progress.put("estimatedTime", session.getEstimatedTime());
        progress.put("formattedRemaining", session.getEstimatedTime());

        // If completed, include result summary
        boolean isCompletedStatus = "completed".equalsIgnoreCase(session.getStatus());
        boolean hasResult = session.getResult() != null;
        boolean allFilesProcessed = totalFiles > 0 && processedCount >= totalFiles;
        
        if (isCompletedStatus && hasResult) {
            progress.put("result", session.getResultSummary());
            // Ensure progress is 100% when completed
            progress.put("progress", 100);
            progress.put("processedFiles", session.getFiles().size());
            // Ensure status is set to completed
            progress.put("status", "completed");
        } else if (allFilesProcessed && hasResult) {
            // If all files are processed and result exists, treat as completed even if status isn't set
            progress.put("result", session.getResultSummary());
            progress.put("progress", 100);
            progress.put("processedFiles", session.getFiles().size());
            progress.put("status", "completed");
            // Also update the session status
            if (!isCompletedStatus) {
                session.setStatus("completed");
            }
        } else if (allFilesProcessed && totalFiles > 0) {
            // If all files processed but no result yet, still mark as 100% progress
            progress.put("progress", 100);
            progress.put("processedFiles", totalFiles);
        }

        // If error, include error message
        if ("error".equalsIgnoreCase(session.getStatus())) {
            progress.put("error", session.getError() != null ? session.getError() : "Compression failed");
        }
        
        // If cancelled, include status
        if ("cancelled".equalsIgnoreCase(session.getStatus())) {
            progress.put("error", "Compression was cancelled");
        }

            res.type("application/json");
            String jsonResponse = gson.toJson(progress);
            LoggerUtil.logInfo("Progress response for session " + sessionId + ": status=" + session.getStatus() + ", progress=" + progress.get("progress"));
            return jsonResponse;
        } catch (Exception e) {
            LoggerUtil.logError("Error getting compression progress", e);
            res.status(500);
            res.type("application/json");
            return "{\"error\":\"Internal server error: " + e.getMessage() + "\"}";
        }
    }

    private Object cancelCompression(Request req, Response res) {
        String sessionId = req.params(":sessionId");
        CompressionSession session = sessions.get(sessionId);

        if (session == null) {
            res.status(404);
            res.type("application/json");
            return "{\"error\":\"Session not found\"}";
        }

        // Cancel the compression
        session.setCancelled(true);
        compressionService.cancel();
        
        res.type("application/json");
        return "{\"status\":\"cancelled\",\"message\":\"Compression cancelled successfully\"}";
    }

    private Object getResults(Request req, Response res) {
        String sessionId = req.params(":sessionId");
        CompressionSession session = sessions.get(sessionId);

        if (session == null) {
            res.status(404);
            return "{\"error\":\"Session not found\"}";
        }

        CompressionResult result = session.getResult();
        if (result == null) {
            res.status(400);
            return "{\"error\":\"Results not available yet\"}";
        }

        res.type("application/json");
        return gson.toJson(result);
    }

    private Object downloadFile(Request req, Response res) {
        String fileId = req.params(":fileId");
        // Implementation would look up file and serve it
        res.status(501);
        return "{\"error\":\"Not implemented\"}";
    }

    private Object downloadAll(Request req, Response res) {
        String sessionId = req.params(":sessionId");
        CompressionSession session = sessions.get(sessionId);

        if (session == null) {
            res.status(404);
            return "{\"error\":\"Session not found\"}";
        }

        // Implementation would create ZIP and serve it
        res.status(501);
        return "{\"error\":\"Not implemented\"}";
    }

    private Object getSettings(Request req, Response res) {
        Map<String, Object> settings = new HashMap<>();
        settings.put("defaultQuality", AppConfig.getDefaultQuality());
        settings.put("defaultLevel", AppConfig.getDefaultLevel());
        settings.put("outputFolder", AppConfig.getOutputFolder());
        settings.put("keepOriginals", AppConfig.keepOriginals());

        res.type("application/json");
        return gson.toJson(settings);
    }

    private Object updateSettings(Request req, Response res) {
        Map<String, Object> data = gson.fromJson(req.body(), Map.class);

        if (data.containsKey("defaultQuality")) {
            AppConfig.setDefaultQuality(((Number) data.get("defaultQuality")).intValue());
        }

        if (data.containsKey("defaultLevel")) {
            AppConfig.setDefaultLevel((String) data.get("defaultLevel"));
        }

        if (data.containsKey("outputFolder")) {
            AppConfig.setOutputFolder((String) data.get("outputFolder"));
        }

        return "{\"success\":true}";
    }

    private Object getSupportedFormats(Request req, Response res) {
        res.type("application/json");
        return gson.toJson(compressionService.getSupportedFormats());
    }

    private Object deleteSession(Request req, Response res) {
        String sessionId = req.params(":sessionId");
        sessions.remove(sessionId);
        return "{\"success\":true}";
    }
    
    private Object openFolder(Request req, Response res) {
        try {
            Map<String, Object> data = gson.fromJson(req.body(), Map.class);
            String path = (String) data.get("path");
            
            if (path == null || path.isEmpty()) {
                res.status(400);
                return "{\"success\":false,\"error\":\"Path is required\"}";
            }
            
            File folder = new File(path);
            if (!folder.exists() || !folder.isDirectory()) {
                res.status(404);
                return "{\"success\":false,\"error\":\"Directory does not exist\"}";
            }
            
            // Use ProcessBuilder to open folder in file explorer (cross-platform)
            String os = System.getProperty("os.name").toLowerCase();
            ProcessBuilder processBuilder;
            if (os.contains("win")) {
                // Windows - use explorer to open folder
                String windowsPath = path.replace("/", "\\");
                processBuilder = new ProcessBuilder("explorer.exe", windowsPath);
            } else if (os.contains("mac")) {
                // macOS
                processBuilder = new ProcessBuilder("open", path);
            } else {
                // Linux
                processBuilder = new ProcessBuilder("xdg-open", path);
            }
            processBuilder.start();
            LoggerUtil.logInfo("Opened folder in file explorer: " + path);
            return "{\"success\":true}";
            
        } catch (Exception e) {
            LoggerUtil.logError("Failed to open folder", e);
            res.status(500);
            return "{\"success\":false,\"error\":\"" + e.getMessage() + "\"}";
        }
    }

    // Use CompressionSession from separate file
}