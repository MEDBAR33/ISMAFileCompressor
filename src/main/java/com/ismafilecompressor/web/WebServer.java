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

        // Serve static files
        get("/*", (request, response) -> {
            staticFiles.consume(request.raw(), response.raw());
            return "";
        });

        // API Routes
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

            // Start compression in background
            executorService.submit(() -> {
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
                            session.updateProgress(fileInfo, total, processed);
                            int progress = total > 0 ? (int) ((processed * 100.0) / total) : 0;
                            session.setProgress(progress);
                            session.setProcessedCount(processed);
                        }

                        @Override
                        public void onComplete(CompressionResult result) {
                            session.setResult(result);
                            session.setStatus("completed");
                            session.setEndTime(System.currentTimeMillis());
                        }

                        @Override
                        public void onError(FileInfo fileInfo, Exception e) {
                            session.addError(fileInfo, e);
                        }
                    };

                    compressionService.addListener(listener);
                    CompressionResult result = compressionService.compressFiles(files, options);
                    compressionService.removeListener(listener);
                    
                    session.setResult(result);
                    session.setStatus("completed");
                    session.setEndTime(System.currentTimeMillis());

                } catch (Exception e) {
                    session.setStatus("error");
                    session.setError(e.getMessage());
                    LoggerUtil.logError("Compression failed", e);
                }
            });

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
        String sessionId = req.params(":sessionId");
        CompressionSession session = sessions.get(sessionId);

        if (session == null) {
            res.status(404);
            return "{\"error\":\"Session not found\"}";
        }

        Map<String, Object> progress = new HashMap<>();
        progress.put("sessionId", sessionId);
        progress.put("status", session.getStatus());
        progress.put("progress", session.getProgress());
        progress.put("totalFiles", session.getFiles().size());
        progress.put("processedFiles", session.getProcessedCount());

        res.type("application/json");
        return gson.toJson(progress);
    }

    private Object cancelCompression(Request req, Response res) {
        String sessionId = req.params(":sessionId");
        CompressionSession session = sessions.get(sessionId);

        if (session == null) {
            res.status(404);
            return "{\"error\":\"Session not found\"}";
        }

        session.setStatus("cancelled");
        return "{\"status\":\"cancelled\"}";
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

    // Use CompressionSession from separate file
}