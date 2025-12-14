package com.ismafilecompressor.service.compressor;

import com.ismafilecompressor.model.CompressionOptions;
import com.ismafilecompressor.util.FileManager;
import com.ismafilecompressor.util.LoggerUtil;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.xz.XZCompressorOutputStream;
import java.io.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import java.util.concurrent.TimeUnit;

public class ArchiveCompressor implements FileCompressor {

    @Override
    public File compress(File input, CompressionOptions options) throws Exception {
        String ext = FileManager.getFileExtension(input.getName()).toLowerCase();

        // Try advanced compression tools first
        if ("zip".equals(ext)) {
            // Try 7z first (best compression)
            File sevenZOutput = try7zCompression(input, options);
            if (sevenZOutput != null && sevenZOutput.exists() && sevenZOutput.length() < input.length()) {
                return sevenZOutput;
            }
            return recompressZip(input, options);
        } else if ("tar".equals(ext) || "gz".equals(ext) || "tgz".equals(ext)) {
            // Try zstd (Zstandard) - modern, fast compression
            File zstdOutput = tryZstdCompression(input, options);
            if (zstdOutput != null && zstdOutput.exists() && zstdOutput.length() < input.length()) {
                return zstdOutput;
            }
            return recompressTarGz(input, options);
        } else if ("7z".equals(ext)) {
            return recompress7z(input, options);
        } else {
            // For other archives, try to recompress
            return recompressGeneric(input, options);
        }
    }
    
    private File try7zCompression(File input, CompressionOptions options) {
        try {
            Process checkProcess = new ProcessBuilder("7z", "i").start();
            if (!checkProcess.waitFor(2, TimeUnit.SECONDS)) {
                return null;
            }
            
            File output = FileManager.createOutputFile(input, "compressed_",
                    options.getOutputDirectory(), "zip");
            
            CompressionOptions.CompressionLevel level = options.getCompressionLevel();
            String compressionLevel;
            String method = "LZMA2"; // Best compression method
            
            switch (level) {
                case MAXIMUM:
                    compressionLevel = "9"; // Maximum compression
                    method = "LZMA2"; // LZMA2 is best
                    break;
                case BALANCED:
                    compressionLevel = "6";
                    method = "LZMA2";
                    break;
                case BEST_QUALITY:
                    compressionLevel = "3";
                    method = "Deflate"; // Faster
                    break;
                default:
                    compressionLevel = "6";
                    method = "LZMA2";
            }
            
            ProcessBuilder pb = new ProcessBuilder(
                "7z",
                "a", // Add to archive
                "-tzip", // ZIP format
                "-mm=" + method, // Compression method
                "-mx=" + compressionLevel, // Compression level
                "-mmt=on", // Multi-threading
                output.getAbsolutePath(),
                input.getAbsolutePath()
            );
            
            Process process = pb.start();
            boolean completed = process.waitFor(300, TimeUnit.SECONDS);
            
            if (completed && process.exitValue() == 0 && output.exists() && output.length() > 0) {
                LoggerUtil.logInfo("Used 7z for archive compression");
                return output;
            }
        } catch (Exception e) {
            // 7z not available
        }
        return null;
    }
    
    private File tryZstdCompression(File input, CompressionOptions options) {
        try {
            Process checkProcess = new ProcessBuilder("zstd", "--version").start();
            if (!checkProcess.waitFor(2, TimeUnit.SECONDS)) {
                return null;
            }
            
            File output = FileManager.createOutputFile(input, "compressed_",
                    options.getOutputDirectory(), "tar.zst");
            
            CompressionOptions.CompressionLevel level = options.getCompressionLevel();
            int compressionLevel;
            
            switch (level) {
                case MAXIMUM:
                    compressionLevel = 22; // Maximum (1-22)
                    break;
                case BALANCED:
                    compressionLevel = 10;
                    break;
                case BEST_QUALITY:
                    compressionLevel = 5;
                    break;
                default:
                    compressionLevel = 10;
            }
            
            ProcessBuilder pb = new ProcessBuilder(
                "zstd",
                "-" + compressionLevel,
                "--long", // Enable long distance matching
                "-T0", // Use all CPU cores
                input.getAbsolutePath(),
                "-o", output.getAbsolutePath()
            );
            
            Process process = pb.start();
            boolean completed = process.waitFor(300, TimeUnit.SECONDS);
            
            if (completed && process.exitValue() == 0 && output.exists() && output.length() > 0) {
                LoggerUtil.logInfo("Used zstd for archive compression");
                return output;
            }
        } catch (Exception e) {
            // zstd not available
        }
        return null;
    }
    
    private File recompress7z(File input, CompressionOptions options) throws Exception {
        // Recompress 7z with better settings
        File output = FileManager.createOutputFile(input, "compressed_",
                options.getOutputDirectory(), "7z");
        
        CompressionOptions.CompressionLevel level = options.getCompressionLevel();
        String compressionLevel = level.isAggressive() ? "9" : "6";
        
        ProcessBuilder pb = new ProcessBuilder(
            "7z",
            "a",
            "-t7z",
            "-mm=LZMA2",
            "-mx=" + compressionLevel,
            "-mmt=on",
            output.getAbsolutePath(),
            input.getAbsolutePath()
        );
        
        Process process = pb.start();
        boolean completed = process.waitFor(300, TimeUnit.SECONDS);
        
        if (!completed || process.exitValue() != 0 || !output.exists()) {
            // Fallback to copy
            return copyFile(input, options);
        }
        
        return output;
    }

    private File recompressZip(File input, CompressionOptions options) throws Exception {
        File output = FileManager.createOutputFile(input, "compressed_",
                options.getOutputDirectory(), "zip");

        try (java.util.zip.ZipInputStream zis = new java.util.zip.ZipInputStream(new FileInputStream(input));
             ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(output))) {
            
            // Set advanced compression level based on options
            CompressionOptions.CompressionLevel level = options.getCompressionLevel();
            int compressionLevel = calculateZipCompressionLevel(level);
            zos.setLevel(compressionLevel);
            
            // Use Deflate64 if available for better compression (Java 9+)
            try {
                // Try to use better compression method
                zos.setMethod(ZipOutputStream.DEFLATED);
            } catch (Exception e) {
                // Fallback to default
            }

            // Extract and recompress contents
            ZipEntry entry;
            byte[] buffer = new byte[8192];
            
            while ((entry = zis.getNextEntry()) != null) {
                // Create new entry with same name
                ZipEntry newEntry = new ZipEntry(entry.getName());
                newEntry.setTime(entry.getTime());
                newEntry.setComment(entry.getComment());
                newEntry.setExtra(entry.getExtra());
                
                zos.putNextEntry(newEntry);
                
                // Copy entry data
                int len;
                while ((len = zis.read(buffer)) > 0) {
                    zos.write(buffer, 0, len);
                }
                
                zos.closeEntry();
                zis.closeEntry();
            }
        }

        return output;
    }

    private File recompressTarGz(File input, CompressionOptions options) throws Exception {
        File output = FileManager.createOutputFile(input, "compressed_",
                options.getOutputDirectory(), "tar.gz");

        CompressionOptions.CompressionLevel level = options.getCompressionLevel();
        
        // Try XZ compression (better than GZIP) if available
        if (level.isAggressive() || level == CompressionOptions.CompressionLevel.BALANCED) {
            File xzOutput = recompressTarXz(input, options);
            if (xzOutput != null && xzOutput.exists() && xzOutput.length() < input.length()) {
                return xzOutput;
            }
        }

        try (FileInputStream fis = new FileInputStream(input);
             GzipCompressorInputStream gzis = new GzipCompressorInputStream(fis);
             TarArchiveInputStream tis = new TarArchiveInputStream(gzis);
             FileOutputStream fos = new FileOutputStream(output);
             GzipCompressorOutputStream gzos = new GzipCompressorOutputStream(fos);
             TarArchiveOutputStream taos = new TarArchiveOutputStream(gzos)) {

            // Note: GzipCompressorOutputStream doesn't support setLevel() directly
            // Compression level is handled by the underlying implementation
            
            if (level.isAggressive()) {
                taos.setLongFileMode(TarArchiveOutputStream.LONGFILE_GNU);
            }

            // Extract and recompress entries
            TarArchiveEntry entry;
            byte[] buffer = new byte[8192];
            
            while ((entry = tis.getNextTarEntry()) != null) {
                TarArchiveEntry newEntry = new TarArchiveEntry(entry.getName());
                newEntry.setSize(entry.getSize());
                newEntry.setMode(entry.getMode());
                newEntry.setModTime(entry.getModTime());
                newEntry.setUserId(entry.getUserId());
                newEntry.setGroupId(entry.getGroupId());
                newEntry.setUserName(entry.getUserName());
                newEntry.setGroupName(entry.getGroupName());
                
                taos.putArchiveEntry(newEntry);
                
                // Copy entry data
                int len;
                while ((len = tis.read(buffer)) > 0) {
                    taos.write(buffer, 0, len);
                }
                
                taos.closeArchiveEntry();
            }
        }

        return output;
    }

    private File recompressGeneric(File input, CompressionOptions options) throws Exception {
        // For unsupported archives, just copy
        File output = FileManager.createOutputFile(input, "compressed_",
                options.getOutputDirectory(), FileManager.getFileExtension(input.getName()));

        try (FileInputStream fis = new FileInputStream(input);
             FileOutputStream fos = new FileOutputStream(output)) {
            fis.transferTo(fos);
        }

        return output;
    }

    private File recompressTarXz(File input, CompressionOptions options) {
        try {
            File output = FileManager.createOutputFile(input, "compressed_",
                    options.getOutputDirectory(), "tar.xz");
            
            try (FileInputStream fis = new FileInputStream(input);
                 GzipCompressorInputStream gzis = new GzipCompressorInputStream(fis);
                 TarArchiveInputStream tis = new TarArchiveInputStream(gzis);
                 FileOutputStream fos = new FileOutputStream(output);
                 XZCompressorOutputStream xzos = new XZCompressorOutputStream(fos);
                 TarArchiveOutputStream taos = new TarArchiveOutputStream(xzos)) {
                
                // Note: XZCompressorOutputStream compression level is set via constructor
                // The default level is used here
                
                TarArchiveEntry entry;
                byte[] buffer = new byte[8192];
                
                while ((entry = tis.getNextTarEntry()) != null) {
                    TarArchiveEntry newEntry = new TarArchiveEntry(entry.getName());
                    newEntry.setSize(entry.getSize());
                    newEntry.setMode(entry.getMode());
                    newEntry.setModTime(entry.getModTime());
                    
                    taos.putArchiveEntry(newEntry);
                    
                    int len;
                    while ((len = tis.read(buffer)) > 0) {
                        taos.write(buffer, 0, len);
                    }
                    
                    taos.closeArchiveEntry();
                }
                
                return output;
            }
        } catch (Exception e) {
            // XZ not available or failed
            return null;
        }
    }
    
    private int calculateZipCompressionLevel(CompressionOptions.CompressionLevel level) {
        switch (level) {
            case MAXIMUM:
                return 9; // Maximum compression
            case BALANCED:
                return 6; // Balanced
            case BEST_QUALITY:
                return 3; // Faster
            default:
                return 6;
        }
    }
    
    
    private File copyFile(File input, CompressionOptions options) throws Exception {
        String ext = FileManager.getFileExtension(input.getName());
        File output = FileManager.createOutputFile(input, "compressed_",
                options.getOutputDirectory(), ext);

        try (FileInputStream fis = new FileInputStream(input);
             FileOutputStream fos = new FileOutputStream(output)) {
            fis.transferTo(fos);
        }

        return output;
    }

    @Override
    public String getSupportedFormats() {
        return "zip,rar,7z,tar,gz,bz2,xz,zst";
    }
}