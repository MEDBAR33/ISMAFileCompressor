package com.ismafilecompressor.service;

import com.ismafilecompressor.model.*;
import com.ismafilecompressor.service.compressor.*;
import com.ismafilecompressor.util.FileManager;
import com.ismafilecompressor.util.FormatDetector;
import com.ismafilecompressor.util.LoggerUtil;

import java.io.File;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class CompressionService {
    private final Map<String, FileCompressor> compressors;
    private final ExecutorService executorService;
    private final List<CompressionListener> listeners;

    public CompressionService() {
        this.compressors = new HashMap<>();
        this.listeners = new ArrayList<>();
        initializeCompressors();
        this.executorService = Executors.newFixedThreadPool(
                Runtime.getRuntime().availableProcessors()
        );
    }

    private void initializeCompressors() {
        registerCompressor(new ImageCompressor());
        registerCompressor(new PdfCompressor());
        registerCompressor(new DocumentCompressor());
        registerCompressor(new AudioCompressor());
        registerCompressor(new VideoCompressor());
        registerCompressor(new ArchiveCompressor());
    }

    private void registerCompressor(FileCompressor compressor) {
        String[] formats = compressor.getSupportedFormats().split(",");
        for (String format : formats) {
            compressors.put(format.trim().toLowerCase(), compressor);
        }
    }

    public void addListener(CompressionListener listener) {
        listeners.add(listener);
    }

    public void removeListener(CompressionListener listener) {
        listeners.remove(listener);
    }

    private void notifyProgress(FileInfo fileInfo, int total, int processed) {
        for (CompressionListener listener : listeners) {
            listener.onProgress(fileInfo, total, processed);
        }
    }

    private void notifyComplete(CompressionResult result) {
        for (CompressionListener listener : listeners) {
            listener.onComplete(result);
        }
    }

    private void notifyError(FileInfo fileInfo, Exception e) {
        for (CompressionListener listener : listeners) {
            listener.onError(fileInfo, e);
        }
    }

    public Future<CompressionResult> compressFilesAsync(List<File> files, CompressionOptions options) {
        return executorService.submit(() -> compressFiles(files, options));
    }

    public CompressionResult compressFiles(List<File> files, CompressionOptions options) {
        CompressionResult result = new CompressionResult();
        result.setTimestamp(System.currentTimeMillis());

        if (files == null || files.isEmpty()) {
            result.setSuccess(false);
            result.setMessage("No files to compress");
            return result;
        }

        List<Future<FileInfo>> futures = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(files.size());
        final int totalFiles = files.size();
        final AtomicInteger processedCount = new AtomicInteger(0);

        for (File file : files) {
            futures.add(executorService.submit(() -> {
                try {
                    FileInfo fileInfo = compressSingleFile(file, options);
                    int current = processedCount.incrementAndGet();
                    
                    // Notify progress as each file completes
                    notifyProgress(fileInfo, totalFiles, current);
                    
                    latch.countDown();
                    return fileInfo;
                } catch (Exception e) {
                    int current = processedCount.incrementAndGet();
                    FileInfo errorInfo = new FileInfo(file.toPath());
                    errorInfo.setStatus("Error");
                    errorInfo.setErrorMessage(e.getMessage());
                    notifyProgress(errorInfo, totalFiles, current);
                    latch.countDown();
                    throw e;
                }
            }));
        }

        try {
            latch.await();

            for (Future<FileInfo> future : futures) {
                try {
                    FileInfo fileInfo = future.get();
                    result.addFile(fileInfo);
                    result.setFilesProcessed(result.getFilesProcessed() + 1);

                    if ("Completed".equals(fileInfo.getStatus())) {
                        result.setTotalOriginalSize(result.getTotalOriginalSize() + fileInfo.getOriginalSize());
                        result.setTotalCompressedSize(result.getTotalCompressedSize() + fileInfo.getCompressedSize());
                    } else {
                        result.setFilesFailed(result.getFilesFailed() + 1);
                    }

                } catch (Exception e) {
                    result.setFilesFailed(result.getFilesFailed() + 1);
                    LoggerUtil.logError("Failed to compress file", e);
                }
            }

            result.setSuccess(true);
            result.setMessage(String.format(
                    "Compressed %d files successfully (%d failed). Average compression: %.1f%%",
                    result.getFilesProcessed() - result.getFilesFailed(),
                    result.getFilesFailed(),
                    result.getAverageCompressionRatio()
            ));

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            result.setSuccess(false);
            result.setMessage("Compression was interrupted");
        }

        result.setTotalTimeMs(System.currentTimeMillis() - result.getTimestamp());
        notifyComplete(result);
        return result;
    }

    public FileInfo compressSingleFile(File file, CompressionOptions options) {
        FileInfo fileInfo = new FileInfo(file.toPath());
        fileInfo.setOriginalSize(FileManager.getFileSize(file));
        fileInfo.setStatus("Processing");

        try {
            // Detect file type
            FormatDetector.FileFormat format = FormatDetector.detect(file);
            fileInfo.setFileType(format.getCategory());
            fileInfo.setMimeType(format.getMimeType());

            // Find appropriate compressor
            FileCompressor compressor = findCompressor(format);
            if (compressor == null) {
                throw new UnsupportedOperationException(
                        "Unsupported file type: " + format.getCategory() + " (" + format.getExtension() + ")"
                );
            }

            // Compress the file
            File outputFile = compressor.compress(file, options);

            fileInfo.setCompressed(outputFile.toPath());
            fileInfo.setCompressedSize(FileManager.getFileSize(outputFile));
            fileInfo.setStatus("Completed");
            fileInfo.setCompressionLevel(options.getCompressionLevel().getDisplayName());

            LoggerUtil.logFileInfo(fileInfo);

        } catch (Exception e) {
            fileInfo.setStatus("Error");
            fileInfo.setErrorMessage(e.getMessage());
            LoggerUtil.logError("Failed to compress file: " + file.getName(), e);
            notifyError(fileInfo, e);
        }

        return fileInfo;
    }

    private FileCompressor findCompressor(FormatDetector.FileFormat format) {
        // Try exact format first
        FileCompressor compressor = compressors.get(format.getExtension());
        if (compressor != null) return compressor;

        // Try category
        return compressors.get(format.getCategory().toLowerCase());
    }

    public List<String> getSupportedFormats() {
        List<String> formats = new ArrayList<>();
        for (FileCompressor compressor : compressors.values()) {
            formats.add(compressor.getSupportedFormats());
        }
        return formats;
    }

    public void shutdown() {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    public interface CompressionListener {
        void onProgress(FileInfo fileInfo, int total, int processed);
        void onComplete(CompressionResult result);
        void onError(FileInfo fileInfo, Exception e);
    }
}