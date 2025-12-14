package com.ismafilecompressor.service.compressor;

import com.ismafilecompressor.model.CompressionOptions;
import com.ismafilecompressor.util.FileManager;
import com.ismafilecompressor.util.LoggerUtil;
import java.io.*;
import java.util.concurrent.TimeUnit;

/**
 * Audio compressor using FFmpeg for real audio compression
 * Requires FFmpeg executable in system PATH
 */
public class AudioCompressor implements FileCompressor {

    @Override
    public File compress(File input, CompressionOptions options) throws Exception {
        if (!input.exists()) {
            throw new FileNotFoundException("Input file not found: " + input.getPath());
        }

        String ext = FileManager.getFileExtension(input.getName()).toLowerCase();
        
        // Check if FFmpeg is available
        if (!isFfmpegAvailable()) {
            LoggerUtil.logWarning("FFmpeg not available, copying audio file: " + input.getName());
            return copyFile(input, options);
        }

        // Determine output format and bitrate based on compression level
        String outputFormat = getOutputFormat(ext, options);
        int bitrate = getBitrate(options);
        
        File output = FileManager.createOutputFile(input, "compressed_",
                options.getOutputDirectory(), outputFormat);

        try {
            compressWithFfmpeg(input, output, outputFormat, bitrate, options);
            
            // Verify output was created and is smaller
            if (!output.exists() || output.length() == 0) {
                LoggerUtil.logWarning("FFmpeg compression failed, falling back to copy");
                return copyFile(input, options);
            }
            
            // If compressed file is larger, use original
            if (output.length() >= input.length()) {
                LoggerUtil.logWarning("Compressed file is larger, using original");
                output.delete();
                return copyFile(input, options);
            }
            
            return output;
        } catch (Exception e) {
            LoggerUtil.logError("Audio compression failed: " + e.getMessage(), e);
            // Fallback to copy
            if (output.exists()) {
                output.delete();
            }
            return copyFile(input, options);
        }
    }

    private void compressWithFfmpeg(File input, File output, String format, int bitrate, CompressionOptions options) throws Exception {
        ProcessBuilder pb = new ProcessBuilder();
        
        CompressionOptions.CompressionLevel level = options.getCompressionLevel();
        
        // Build advanced FFmpeg command based on format
        if ("mp3".equals(format)) {
            // Advanced MP3 compression with VBR
            pb.command(
                    "ffmpeg",
                    "-i", input.getAbsolutePath(),
                    "-codec:a", "libmp3lame",
                    "-q:a", String.valueOf(getQualityValue(options)), // VBR quality (0-9, lower is better)
                    "-b:a", bitrate + "k", // Fallback bitrate
                    "-compression_level", String.valueOf(getCompressionLevel(level)), // 0-9
                    "-joint_stereo", "1", // Use joint stereo for better compression
                    "-y",
                    output.getAbsolutePath()
            );
        } else if ("aac".equals(format)) {
            // Advanced AAC compression
            pb.command(
                    "ffmpeg",
                    "-i", input.getAbsolutePath(),
                    "-codec:a", "aac",
                    "-b:a", bitrate + "k",
                    "-aac_coder", "twoloop", // Two-loop search for better quality
                    "-profile:a", "aac_low", // Use AAC-LC profile
                    "-y",
                    output.getAbsolutePath()
            );
        } else if ("opus".equals(format)) {
            // Opus codec - best compression/quality ratio
            pb.command(
                    "ffmpeg",
                    "-i", input.getAbsolutePath(),
                    "-codec:a", "libopus",
                    "-b:a", bitrate + "k",
                    "-vbr", "on", // Variable bitrate
                    "-compression_level", String.valueOf(getOpusCompressionLevel(level)),
                    "-y",
                    output.getAbsolutePath()
            );
        } else if ("ogg".equals(format)) {
            // Advanced Ogg Vorbis compression
            pb.command(
                    "ffmpeg",
                    "-i", input.getAbsolutePath(),
                    "-codec:a", "libvorbis",
                    "-q:a", String.valueOf(getVorbisQuality(options)), // -1 to 10
                    "-y",
                    output.getAbsolutePath()
            );
        } else {
            // For WAV, FLAC, etc., use Opus for best compression (if available), else MP3
            if (isOpusAvailable()) {
                pb.command(
                        "ffmpeg",
                        "-i", input.getAbsolutePath(),
                        "-codec:a", "libopus",
                        "-b:a", bitrate + "k",
                        "-vbr", "on",
                        "-compression_level", String.valueOf(getOpusCompressionLevel(level)),
                        "-y",
                        output.getAbsolutePath()
                );
            } else {
                pb.command(
                        "ffmpeg",
                        "-i", input.getAbsolutePath(),
                        "-codec:a", "libmp3lame",
                        "-q:a", String.valueOf(getQualityValue(options)),
                        "-b:a", bitrate + "k",
                        "-compression_level", String.valueOf(getCompressionLevel(level)),
                        "-joint_stereo", "1",
                        "-y",
                        output.getAbsolutePath()
                );
            }
        }

        Process process = pb.start();

        // Read error stream (FFmpeg outputs to stderr)
        BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
        StringBuilder error = new StringBuilder();
        String line;
        while ((line = errorReader.readLine()) != null) {
            error.append(line).append("\n");
        }

        // Wait for process (audio compression can take time)
        boolean completed = process.waitFor(300, TimeUnit.SECONDS); // 5 minutes timeout

        if (!completed) {
            process.destroyForcibly();
            throw new RuntimeException("FFmpeg audio compression timed out");
        }

        int exitCode = process.exitValue();
        if (exitCode != 0) {
            throw new RuntimeException("FFmpeg failed with exit code " + exitCode + ": " + error);
        }
    }

    private String getOutputFormat(String inputExt, CompressionOptions options) {
        CompressionOptions.CompressionLevel level = options.getCompressionLevel();
        
        // Use Opus for best compression/quality ratio if available
        if (isOpusAvailable()) {
            if (level.isAggressive() || level == CompressionOptions.CompressionLevel.BALANCED) {
                return "opus";
            }
        }
        
        // For maximum compression, convert to MP3
        if (level.isAggressive()) {
            return "mp3";
        }
        
        // For best quality, keep original or use AAC
        if (level == CompressionOptions.CompressionLevel.BEST_QUALITY) {
            if ("wav".equals(inputExt) || "flac".equals(inputExt)) {
                return "aac"; // AAC is better than MP3 for quality
            }
        }
        
        // Keep original format for balanced/best quality
        return inputExt;
    }

    private int getBitrate(CompressionOptions options) {
        switch (options.getCompressionLevel()) {
            case MAXIMUM:
                return 64; // Very low bitrate for maximum compression
            case BALANCED:
                return 128; // Standard bitrate
            case BEST_QUALITY:
                return 192; // Higher bitrate
            case CUSTOM:
                return 128; // Default
            default:
                return 128;
        }
    }

    private int getQualityValue(CompressionOptions options) {
        // MP3 VBR quality: 0 (best) to 9 (worst)
        CompressionOptions.CompressionLevel level = options.getCompressionLevel();
        switch (level) {
            case MAXIMUM:
                return 7; // Lower quality, smaller file
            case BALANCED:
                return 4; // Balanced
            case BEST_QUALITY:
                return 2; // High quality
            default:
                return 4;
        }
    }
    
    private int getVorbisQuality(CompressionOptions options) {
        // Ogg Vorbis quality: -1 to 10 (higher is better)
        CompressionOptions.CompressionLevel level = options.getCompressionLevel();
        switch (level) {
            case MAXIMUM:
                return 3;
            case BALANCED:
                return 5;
            case BEST_QUALITY:
                return 8;
            default:
                return 5;
        }
    }
    
    private int getCompressionLevel(CompressionOptions.CompressionLevel level) {
        // Compression level: 0 (fastest) to 9 (best compression)
        switch (level) {
            case MAXIMUM:
                return 9; // Best compression
            case BALANCED:
                return 6;
            case BEST_QUALITY:
                return 3; // Faster encoding
            default:
                return 6;
        }
    }
    
    private int getOpusCompressionLevel(CompressionOptions.CompressionLevel level) {
        // Opus compression level: 0 (fastest) to 10 (best compression)
        switch (level) {
            case MAXIMUM:
                return 10; // Best compression
            case BALANCED:
                return 7;
            case BEST_QUALITY:
                return 5; // Faster encoding
            default:
                return 7;
        }
    }
    
    private boolean isOpusAvailable() {
        try {
            Process process = new ProcessBuilder("ffmpeg", "-codecs").start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains("libopus")) {
                    process.destroy();
                    return true;
                }
            }
            process.destroy();
        } catch (Exception e) {
            // Ignore
        }
        return false;
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

    private boolean isFfmpegAvailable() {
        try {
            Process process = new ProcessBuilder("ffmpeg", "-version").start();
            return process.waitFor(5, TimeUnit.SECONDS) && process.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public String getSupportedFormats() {
        return "mp3,wav,flac,aac,ogg,opus,m4a";
    }
}