package com.ismafilecompressor.util;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Service to clean up old compressed files from the server.
 * Deletes files older than 1 hour to free up server space.
 */
public class FileCleanupService {
    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private static final long CLEANUP_INTERVAL_HOURS = 1; // Clean up files older than 1 hour
    private static final long CLEANUP_CHECK_INTERVAL_MINUTES = 30; // Check every 30 minutes
    private static boolean started = false;
    
    /**
     * Start the cleanup service
     */
    public static void start() {
        if (started) {
            return;
        }
        started = true;
        
        // Run cleanup immediately, then every 30 minutes
        scheduler.scheduleAtFixedRate(
            FileCleanupService::cleanupOldFiles,
            0,
            CLEANUP_CHECK_INTERVAL_MINUTES,
            TimeUnit.MINUTES
        );
        
        LoggerUtil.logInfo("File cleanup service started - will delete files older than " + CLEANUP_INTERVAL_HOURS + " hour(s)");
    }
    
    /**
     * Stop the cleanup service
     */
    public static void stop() {
        if (!started) {
            return;
        }
        scheduler.shutdown();
        started = false;
        LoggerUtil.logInfo("File cleanup service stopped");
    }
    
    /**
     * Clean up old files from the output directory
     */
    private static void cleanupOldFiles() {
        try {
            String outputDir = com.ismafilecompressor.config.AppConfig.getOutputFolder();
            File outputFolder = new File(outputDir);
            
            if (!outputFolder.exists() || !outputFolder.isDirectory()) {
                return;
            }
            
            long currentTime = System.currentTimeMillis();
            long maxAge = CLEANUP_INTERVAL_HOURS * 60 * 60 * 1000; // 1 hour in milliseconds
            int deletedCount = 0;
            long freedSpace = 0;
            
            File[] files = outputFolder.listFiles();
            if (files == null) {
                return;
            }
            
            for (File file : files) {
                try {
                    if (file.isFile()) {
                        // Get file's last modified time
                        Path filePath = file.toPath();
                        FileTime fileTime = Files.getLastModifiedTime(filePath);
                        long fileAge = currentTime - fileTime.toMillis();
                        
                        // Delete if file is older than maxAge
                        if (fileAge > maxAge) {
                            long fileSize = file.length();
                            if (file.delete()) {
                                deletedCount++;
                                freedSpace += fileSize;
                                LoggerUtil.logInfo("Deleted old file: " + file.getName() + " (age: " + 
                                    (fileAge / (60 * 1000)) + " minutes, size: " + 
                                    FileManager.formatFileSize(fileSize) + ")");
                            } else {
                                LoggerUtil.logWarning("Failed to delete file: " + file.getName());
                            }
                        }
                    } else if (file.isDirectory()) {
                        // Recursively clean subdirectories
                        cleanupDirectory(file, currentTime, maxAge);
                    }
                } catch (Exception e) {
                    LoggerUtil.logError("Error cleaning up file: " + file.getName(), e);
                }
            }
            
            if (deletedCount > 0) {
                LoggerUtil.logInfo("Cleanup completed: Deleted " + deletedCount + " file(s), freed " + 
                    FileManager.formatFileSize(freedSpace));
            }
        } catch (Exception e) {
            LoggerUtil.logError("Error during file cleanup", e);
        }
    }
    
    /**
     * Recursively clean up files in a directory
     */
    private static void cleanupDirectory(File directory, long currentTime, long maxAge) {
        try {
            File[] files = directory.listFiles();
            if (files == null) {
                return;
            }
            
            for (File file : files) {
                try {
                    if (file.isFile()) {
                        Path filePath = file.toPath();
                        FileTime fileTime = Files.getLastModifiedTime(filePath);
                        long fileAge = currentTime - fileTime.toMillis();
                        
                        if (fileAge > maxAge) {
                            if (file.delete()) {
                                LoggerUtil.logInfo("Deleted old file: " + file.getAbsolutePath() + 
                                    " (age: " + (fileAge / (60 * 1000)) + " minutes)");
                            }
                        }
                    } else if (file.isDirectory()) {
                        cleanupDirectory(file, currentTime, maxAge);
                        // Delete empty directories
                        if (file.listFiles() == null || file.listFiles().length == 0) {
                            file.delete();
                        }
                    }
                } catch (Exception e) {
                    LoggerUtil.logError("Error cleaning up file in directory: " + file.getName(), e);
                }
            }
        } catch (Exception e) {
            LoggerUtil.logError("Error cleaning up directory: " + directory.getAbsolutePath(), e);
        }
    }
}

