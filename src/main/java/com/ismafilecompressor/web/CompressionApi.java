package com.ismafilecompressor.web;

import com.google.gson.Gson;
import com.ismafilecompressor.model.*;
import com.ismafilecompressor.util.FileManager;
import com.ismafilecompressor.util.FormatDetector;
import spark.Request;
import spark.Response;
import spark.Route;
import javax.servlet.MultipartConfigElement;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class CompressionApi {
    private final Gson gson;
    private final Map<String, CompressionJob> jobs;
    private final Map<String, List<FileInfo>> results;

    public CompressionApi() {
        this.gson = new Gson();
        this.jobs = new ConcurrentHashMap<>();
        this.results = new ConcurrentHashMap<>();
    }

    public Route analyzeFiles() {
        return (Request req, Response res) -> {
            try {
                String sessionId = UUID.randomUUID().toString();
                Path tempDir = Files.createTempDirectory("compression_analyze_" + sessionId);

                List<Map<String, Object>> analysis = new ArrayList<>();
                AtomicInteger totalSize = new AtomicInteger(0);

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

                        Map<String, Object> fileAnalysis = new HashMap<>();
                        fileAnalysis.put("id", UUID.randomUUID().toString());
                        fileAnalysis.put("originalName", filename);
                        fileAnalysis.put("name", filename);
                        fileAnalysis.put("path", filePath.toString());
                        fileAnalysis.put("size", file.length());
                        fileAnalysis.put("formattedSize", FileManager.formatFileSize(file.length()));
                        fileAnalysis.put("type", format.getCategory());
                        fileAnalysis.put("extension", format.getExtension());
                        fileAnalysis.put("mimeType", format.getMimeType());
                        fileAnalysis.put("icon", format.getIconClass());
                        fileAnalysis.put("color", format.getColorClass());
                        fileAnalysis.put("canCompress", canCompressFormat(format));
                        fileAnalysis.put("estimatedRatio", getEstimatedRatio(format));
                        fileAnalysis.put("suggestedFormat", getSuggestedOutputFormat(format));

                        analysis.add(fileAnalysis);
                        totalSize.addAndGet((int) file.length());
                    }
                }

                // Store analysis in job
                CompressionJob job = new CompressionJob(sessionId);
                job.setStatus("analyzed");
                job.setAnalysis(analysis);
                job.setTotalSize(totalSize.get());
                job.setTempDir(tempDir.toString());
                jobs.put(sessionId, job);

                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("sessionId", sessionId);
                response.put("analysis", analysis);
                response.put("totalFiles", analysis.size());
                response.put("totalSize", totalSize.get());
                response.put("formattedTotalSize", FileManager.formatFileSize(totalSize.get()));
                response.put("estimatedTotalSave", FileManager.formatFileSize(
                        (long) (totalSize.get() * getAverageRatio(analysis))
                ));

                res.type("application/json");
                return gson.toJson(response);

            } catch (Exception e) {
                res.status(500);
                return errorResponse("Analysis failed: " + e.getMessage());
            }
        };
    }

    public Route startCompression() {
        return (Request req, Response res) -> {
            try {
                Map<String, Object> data = gson.fromJson(req.body(), Map.class);
                String sessionId = (String) data.get("sessionId");
                CompressionJob job = jobs.get(sessionId);

                if (job == null) {
                    res.status(404);
                    return errorResponse("Session not found");
                }

                // Parse options
                CompressionOptions options = parseOptions(data);

                // Update job
                job.setStatus("processing");
                job.setOptions(options);
                job.setStartTime(System.currentTimeMillis());

                // Start compression in background thread
                new Thread(() -> {
                    processCompressionJob(job);
                }).start();

                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("sessionId", sessionId);
                response.put("status", "started");
                response.put("message", "Compression started successfully");
                response.put("totalFiles", job.getAnalysis().size());

                res.type("application/json");
                return gson.toJson(response);

            } catch (Exception e) {
                res.status(500);
                return errorResponse("Compression start failed: " + e.getMessage());
            }
        };
    }

    public Route getStatus() {
        return (Request req, Response res) -> {
            String sessionId = req.params("sessionId");
            CompressionJob job = jobs.get(sessionId);

            if (job == null) {
                res.status(404);
                return errorResponse("Session not found");
            }

            Map<String, Object> status = new HashMap<>();
            status.put("sessionId", sessionId);
            status.put("status", job.getStatus());
            status.put("progress", job.getProgress());
            status.put("processedFiles", job.getProcessedFiles());
            status.put("totalFiles", job.getTotalFiles());
            status.put("currentFile", job.getCurrentFile());

            if (job.getStartTime() > 0) {
                long elapsed = System.currentTimeMillis() - job.getStartTime();
                status.put("elapsedTime", elapsed);
                status.put("formattedElapsedTime", formatTime(elapsed));

                if (job.getProgress() > 0) {
                    long estimatedTotal = (long) (elapsed / (job.getProgress() / 100.0));
                    long remaining = estimatedTotal - elapsed;
                    status.put("estimatedRemaining", remaining);
                    status.put("formattedRemaining", formatTime(remaining));
                }
            }

            if (job.getResult() != null) {
                status.put("result", job.getResultSummary());
            }

            if (job.hasErrors()) {
                status.put("errors", job.getErrors());
            }

            res.type("application/json");
            return gson.toJson(status);
        };
    }

    public Route getResults() {
        return (Request req, Response res) -> {
            String sessionId = req.params("sessionId");
            CompressionJob job = jobs.get(sessionId);

            if (job == null) {
                res.status(404);
                return errorResponse("Session not found");
            }

            if (!"completed".equals(job.getStatus()) && !"error".equals(job.getStatus())) {
                res.status(400);
                return errorResponse("Compression not completed yet");
            }

            List<FileInfo> fileResults = results.getOrDefault(sessionId, new ArrayList<>());

            Map<String, Object> response = new HashMap<>();
            response.put("sessionId", sessionId);
            response.put("status", job.getStatus());
            response.put("totalFiles", job.getTotalFiles());
            response.put("successfulFiles", (int) fileResults.stream()
                    .filter(f -> "Completed".equals(f.getStatus()))
                    .count());
            response.put("failedFiles", (int) fileResults.stream()
                    .filter(f -> "Error".equals(f.getStatus()))
                    .count());

            // Calculate totals
            long totalOriginal = fileResults.stream()
                    .mapToLong(FileInfo::getOriginalSize)
                    .sum();
            long totalCompressed = fileResults.stream()
                    .mapToLong(f -> f.getCompressedSize() > 0 ? f.getCompressedSize() : f.getOriginalSize())
                    .sum();

            response.put("totalOriginalSize", totalOriginal);
            response.put("formattedOriginalSize", FileManager.formatFileSize(totalOriginal));
            response.put("totalCompressedSize", totalCompressed);
            response.put("formattedCompressedSize", FileManager.formatFileSize(totalCompressed));
            response.put("totalSaved", totalOriginal - totalCompressed);
            response.put("formattedSaved", FileManager.formatFileSize(totalOriginal - totalCompressed));

            if (totalOriginal > 0) {
                double ratio = (1 - (double) totalCompressed / totalOriginal) * 100;
                response.put("overallRatio", String.format("%.1f%%", ratio));
            }

            // File details
            List<Map<String, Object>> files = new ArrayList<>();
            for (FileInfo fileInfo : fileResults) {
                Map<String, Object> fileData = new HashMap<>();
                fileData.put("name", fileInfo.getFileName());
                fileData.put("status", fileInfo.getStatus());
                fileData.put("originalSize", fileInfo.getFormattedOriginalSize());
                fileData.put("compressedSize", fileInfo.getFormattedCompressedSize());
                fileData.put("saved", fileInfo.getFormattedSaved());
                fileData.put("ratio", String.format("%.1f%%", fileInfo.getCompressionRatio()));
                fileData.put("type", fileInfo.getFileType());
                fileData.put("error", fileInfo.getErrorMessage());

                if (fileInfo.getCompressed() != null) {
                    fileData.put("downloadPath", "/downloads/" +
                            fileInfo.getCompressed().getFileName().toString());
                }

                files.add(fileData);
            }

            response.put("files", files);

            res.type("application/json");
            return gson.toJson(response);
        };
    }

    private void processCompressionJob(CompressionJob job) {
        try {
            List<Map<String, Object>> analysis = job.getAnalysis();
            CompressionOptions options = job.getOptions();
            List<FileInfo> fileResults = new ArrayList<>();

            for (int i = 0; i < analysis.size(); i++) {
                Map<String, Object> fileAnalysis = analysis.get(i);
                String filePath = (String) fileAnalysis.get("path");
                File file = new File(filePath);

                // Update progress
                job.setCurrentFile((String) fileAnalysis.get("name"));
                job.setProcessedFiles(i + 1);
                job.setProgress((int) ((i + 1) * 100.0 / analysis.size()));

                try {
                    // Create FileInfo
                    FileInfo fileInfo = new FileInfo(file.toPath());
                    fileInfo.setOriginalSize(file.length());
                    fileInfo.setFileType((String) fileAnalysis.get("type"));
                    fileInfo.setMimeType((String) fileAnalysis.get("mimeType"));
                    fileInfo.setStatus("Processing");

                    // Simulate compression (in real app, call compression service)
                    Thread.sleep(1000); // Simulate processing time

                    // Create output file
                    String outputDir = options.getOutputDirectory() != null ?
                            options.getOutputDirectory() : "compressed_output";
                    File outputFile = FileManager.createOutputFile(file, "compressed_", outputDir,
                            getOutputExtension(file, options));

                    // Copy file (simulated compression)
                    Files.copy(file.toPath(), outputFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

                    // Update file info
                    fileInfo.setCompressed(outputFile.toPath());
                    fileInfo.setCompressedSize(outputFile.length());
                    fileInfo.setStatus("Completed");
                    fileInfo.setCompressionLevel(options.getCompressionLevel().getDisplayName());

                    fileResults.add(fileInfo);

                } catch (Exception e) {
                    FileInfo fileInfo = new FileInfo(file.toPath());
                    fileInfo.setStatus("Error");
                    fileInfo.setErrorMessage(e.getMessage());
                    fileResults.add(fileInfo);
                    job.addError(e.getMessage());
                }
            }

            // Store results
            results.put(job.getSessionId(), fileResults);
            job.setStatus("completed");
            job.setEndTime(System.currentTimeMillis());

        } catch (Exception e) {
            job.setStatus("error");
            job.setError(e.getMessage());
        }
    }

    private CompressionOptions parseOptions(Map<String, Object> data) {
        CompressionOptions options = new CompressionOptions();

        String level = (String) data.getOrDefault("compressionLevel", "BALANCED");
        try {
            options.setCompressionLevel(CompressionOptions.CompressionLevel.valueOf(level.toUpperCase()));
        } catch (Exception e) {
            options.setCompressionLevel(CompressionOptions.CompressionLevel.BALANCED);
        }

        if (data.containsKey("outputDirectory")) {
            options.setOutputDirectory((String) data.get("outputDirectory"));
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

        if (data.containsKey("convertPngToJpeg")) {
            options.setConvertPngToJpeg((Boolean) data.get("convertPngToJpeg"));
        }

        if (data.containsKey("outputFormat")) {
            options.setOutputFormat((String) data.get("outputFormat"));
        }

        return options;
    }

    private boolean canCompressFormat(FormatDetector.FileFormat format) {
        return format.isImage() || format.isPdf() || format.isDocument() ||
                format.isAudio() || format.isVideo() || format.isArchive();
    }

    private double getEstimatedRatio(FormatDetector.FileFormat format) {
        if (format.isImage()) return 0.6; // 40% savings
        if (format.isPdf()) return 0.7;   // 30% savings
        if (format.isDocument()) return 0.3; // 70% savings
        if (format.isAudio()) return 0.5; // 50% savings
        if (format.isVideo()) return 0.4; // 60% savings
        if (format.isArchive()) return 0.1; // 90% savings (if recompressed)
        return 0.2; // 20% default
    }

    private double getAverageRatio(List<Map<String, Object>> analysis) {
        return analysis.stream()
                .mapToDouble(a -> (Double) a.get("estimatedRatio"))
                .average()
                .orElse(0.5);
    }

    private String getSuggestedOutputFormat(FormatDetector.FileFormat format) {
        if (format.isImage()) return "webp";
        if (format.isPdf()) return "pdf";
        if (format.isDocument()) return "pdf";
        if (format.isAudio()) return "mp3";
        if (format.isVideo()) return "mp4";
        return "zip";
    }

    private String getOutputExtension(File file, CompressionOptions options) {
        String ext = FileManager.getFileExtension(file.getName());

        if ("auto".equals(options.getOutputFormat())) {
            return getSuggestedOutputFormat(FormatDetector.detect(file));
        }

        return options.getOutputFormat() != null ? options.getOutputFormat() : ext;
    }

    private String formatTime(long millis) {
        if (millis < 1000) return millis + "ms";
        if (millis < 60000) return String.format("%.1fs", millis / 1000.0);

        long minutes = millis / 60000;
        long seconds = (millis % 60000) / 1000;
        return String.format("%dm %ds", minutes, seconds);
    }

    private String errorResponse(String message) {
        return gson.toJson(Map.of(
                "error", true,
                "message", message,
                "timestamp", new Date().toString()
        ));
    }

    private static class CompressionJob {
        private final String sessionId;
        private String status;
        private List<Map<String, Object>> analysis;
        private CompressionOptions options;
        private int progress;
        private int processedFiles;
        private int totalFiles;
        private String currentFile;
        private long startTime;
        private long endTime;
        private List<String> errors;
        private String tempDir;
        private long totalSize;
        private Map<String, Object> result;

        public CompressionJob(String sessionId) {
            this.sessionId = sessionId;
            this.status = "created";
            this.progress = 0;
            this.errors = new ArrayList<>();
        }

        // Getters and setters
        public String getSessionId() { return sessionId; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }

        public List<Map<String, Object>> getAnalysis() { return analysis; }
        public void setAnalysis(List<Map<String, Object>> analysis) {
            this.analysis = analysis;
            this.totalFiles = analysis.size();
        }

        public CompressionOptions getOptions() { return options; }
        public void setOptions(CompressionOptions options) { this.options = options; }

        public int getProgress() { return progress; }
        public void setProgress(int progress) { this.progress = progress; }

        public int getProcessedFiles() { return processedFiles; }
        public void setProcessedFiles(int processedFiles) { this.processedFiles = processedFiles; }

        public int getTotalFiles() { return totalFiles; }

        public String getCurrentFile() { return currentFile; }
        public void setCurrentFile(String currentFile) { this.currentFile = currentFile; }

        public long getStartTime() { return startTime; }
        public void setStartTime(long startTime) { this.startTime = startTime; }

        public long getEndTime() { return endTime; }
        public void setEndTime(long endTime) { this.endTime = endTime; }

        public List<String> getErrors() { return errors; }
        public void addError(String error) { errors.add(error); }
        public boolean hasErrors() { return !errors.isEmpty(); }

        public String getTempDir() { return tempDir; }
        public void setTempDir(String tempDir) { this.tempDir = tempDir; }

        public long getTotalSize() { return totalSize; }
        public void setTotalSize(long totalSize) { this.totalSize = totalSize; }

        public Map<String, Object> getResult() { return result; }
        public void setResult(Map<String, Object> result) { this.result = result; }

        public Map<String, Object> getResultSummary() {
            if (result == null) return null;

            Map<String, Object> summary = new HashMap<>();
            summary.put("success", result.getOrDefault("success", false));
            summary.put("message", result.getOrDefault("message", ""));
            summary.put("processedFiles", processedFiles);
            summary.put("totalFiles", totalFiles);
            summary.put("errors", errors.size());

            return summary;
        }

        public void setError(String error) {
            this.status = "error";
            this.errors.add(error);
        }
    }
}