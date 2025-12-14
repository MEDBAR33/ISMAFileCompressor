package com.ismafilecompressor.service.compressor;

import com.ismafilecompressor.model.CompressionOptions;
import com.ismafilecompressor.util.FileManager;
import com.ismafilecompressor.util.LoggerUtil;
import java.io.*;
import java.util.concurrent.TimeUnit;

/**
 * Video compressor using FFmpeg for real video compression
 * Requires FFmpeg executable in system PATH
 */
public class VideoCompressor implements FileCompressor {

    @Override
    public File compress(File input, CompressionOptions options) throws Exception {
        if (!input.exists()) {
            throw new FileNotFoundException("Input file not found: " + input.getPath());
        }

        String ext = FileManager.getFileExtension(input.getName()).toLowerCase();
        
        // Check if FFmpeg is available
        if (!isFfmpegAvailable()) {
            LoggerUtil.logWarning("FFmpeg not available, copying video file: " + input.getName());
            return copyFile(input, options);
        }

        // Determine output format
        String outputFormat = getOutputFormat(ext);
        int crf = getCrfValue(options); // Constant Rate Factor: lower = better quality, larger file
        
        File output = FileManager.createOutputFile(input, "compressed_",
                options.getOutputDirectory(), outputFormat);

        try {
            compressWithFfmpeg(input, output, outputFormat, crf, options);
            
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
            LoggerUtil.logError("Video compression failed: " + e.getMessage(), e);
            // Fallback to copy
            if (output.exists()) {
                output.delete();
            }
            return copyFile(input, options);
        }
    }

    private void compressWithFfmpeg(File input, File output, String format, int crf, CompressionOptions options) throws Exception {
        ProcessBuilder pb = new ProcessBuilder();
        
        CompressionOptions.CompressionLevel level = options.getCompressionLevel();
        
        // Try advanced codecs first (H.265/HEVC, VP9, AV1)
        String videoCodec = getBestVideoCodec(level);
        
        if ("libx265".equals(videoCodec)) {
            // H.265/HEVC - better compression than H.264
            pb.command(
                    "ffmpeg",
                    "-i", input.getAbsolutePath(),
                    "-c:v", "libx265", // H.265 codec
                    "-crf", String.valueOf(crf),
                    "-preset", getPreset(level),
                    "-x265-params", getX265Params(level), // Advanced H.265 parameters
                    "-c:a", "aac",
                    "-b:a", getAudioBitrate(level) + "k",
                    "-movflags", "+faststart",
                    "-tag:v", "hvc1", // Compatibility tag
                    "-y",
                    output.getAbsolutePath()
            );
        } else if ("libvpx-vp9".equals(videoCodec)) {
            // VP9 - Google's codec, excellent compression
            pb.command(
                    "ffmpeg",
                    "-i", input.getAbsolutePath(),
                    "-c:v", "libvpx-vp9",
                    "-crf", String.valueOf(crf),
                    "-b:v", "0", // VBR mode
                    "-cpu-used", getVp9CpuUsed(level), // 0-5, lower is better quality
                    "-row-mt", "1", // Multi-threading
                    "-c:a", "libopus", // Opus audio for VP9
                    "-b:a", getAudioBitrate(level) + "k",
                    "-y",
                    output.getAbsolutePath()
            );
        } else if ("libaom-av1".equals(videoCodec)) {
            // AV1 - newest codec, best compression
            pb.command(
                    "ffmpeg",
                    "-i", input.getAbsolutePath(),
                    "-c:v", "libaom-av1",
                    "-crf", String.valueOf(crf),
                    "-cpu-used", getAv1CpuUsed(level), // 0-8, lower is better
                    "-row-mt", "1",
                    "-c:a", "libopus",
                    "-b:a", getAudioBitrate(level) + "k",
                    "-y",
                    output.getAbsolutePath()
            );
        } else {
            // Fallback to H.264 with advanced settings
            pb.command(
                    "ffmpeg",
                    "-i", input.getAbsolutePath(),
                    "-c:v", "libx264",
                    "-crf", String.valueOf(crf),
                    "-preset", getPreset(level),
                    "-profile:v", "high", // High profile for better compression
                    "-level", "4.0", // H.264 level
                    "-pix_fmt", "yuv420p", // Standard pixel format
                    "-c:a", "aac",
                    "-b:a", getAudioBitrate(level) + "k",
                    "-movflags", "+faststart",
                    "-y",
                    output.getAbsolutePath()
            );
        }

        Process process = pb.start();

        // Read error stream (FFmpeg outputs progress to stderr)
        BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
        StringBuilder error = new StringBuilder();
        String line;
        while ((line = errorReader.readLine()) != null) {
            error.append(line).append("\n");
            // Could parse progress from FFmpeg output here if needed
        }

        // Wait for process (video compression can take a long time)
        boolean completed = process.waitFor(1800, TimeUnit.SECONDS); // 30 minutes timeout

        if (!completed) {
            process.destroyForcibly();
            throw new RuntimeException("FFmpeg video compression timed out");
        }

        int exitCode = process.exitValue();
        if (exitCode != 0) {
            throw new RuntimeException("FFmpeg failed with exit code " + exitCode + ": " + error);
        }
    }

    private String getOutputFormat(String inputExt) {
        // Convert all formats to MP4 for better compatibility
        return "mp4";
    }

    private String getBestVideoCodec(CompressionOptions.CompressionLevel level) {
        // Check codec availability and return best one
        if (isCodecAvailable("libx265")) {
            return "libx265"; // H.265/HEVC - best compression
        } else if (isCodecAvailable("libvpx-vp9")) {
            return "libvpx-vp9"; // VP9 - excellent compression
        } else if (isCodecAvailable("libaom-av1")) {
            return "libaom-av1"; // AV1 - newest, best compression
        }
        return "libx264"; // Fallback to H.264
    }
    
    private boolean isCodecAvailable(String codec) {
        try {
            Process process = new ProcessBuilder("ffmpeg", "-codecs").start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains(codec)) {
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
    
    private int getCrfValue(CompressionOptions options) {
        // CRF (Constant Rate Factor): 18-28 range for H.264/H.265
        // For VP9/AV1, range is 0-63, but we'll use similar mapping
        CompressionOptions.CompressionLevel level = options.getCompressionLevel();
        switch (level) {
            case MAXIMUM:
                return 28; // Maximum compression, lower quality
            case BALANCED:
                return 23; // Balanced quality/size
            case BEST_QUALITY:
                return 18; // Best quality, larger file
            default:
                return 23;
        }
    }

    private String getPreset(CompressionOptions.CompressionLevel level) {
        // FFmpeg presets: ultrafast, superfast, veryfast, faster, fast, medium, slow, slower, veryslow
        // Slower = better compression but takes longer
        switch (level) {
            case MAXIMUM:
                return "slow"; // Better compression for maximum mode
            case BALANCED:
                return "medium"; // Balanced speed/compression
            case BEST_QUALITY:
                return "slow"; // Better quality for best mode
            default:
                return "medium";
        }
    }
    
    private String getX265Params(CompressionOptions.CompressionLevel level) {
        // Advanced H.265 parameters
        switch (level) {
            case MAXIMUM:
                return "crf=28:preset=slow:aq-mode=3:aq-strength=1.0:rd=4:subme=5:merange=57";
            case BALANCED:
                return "crf=23:preset=medium:aq-mode=2:aq-strength=1.0";
            case BEST_QUALITY:
                return "crf=18:preset=slow:aq-mode=3:aq-strength=0.8:rd=6:subme=7";
            default:
                return "crf=23:preset=medium";
        }
    }
    
    private String getVp9CpuUsed(CompressionOptions.CompressionLevel level) {
        // VP9 CPU used: 0-5, lower is better quality but slower
        switch (level) {
            case MAXIMUM:
                return "2"; // Good quality, reasonable speed
            case BALANCED:
                return "3"; // Balanced
            case BEST_QUALITY:
                return "1"; // Best quality
            default:
                return "3";
        }
    }
    
    private String getAv1CpuUsed(CompressionOptions.CompressionLevel level) {
        // AV1 CPU used: 0-8, lower is better quality but much slower
        switch (level) {
            case MAXIMUM:
                return "4"; // Good compression, reasonable speed
            case BALANCED:
                return "5"; // Balanced
            case BEST_QUALITY:
                return "3"; // Better quality
            default:
                return "5";
        }
    }

    private int getAudioBitrate(CompressionOptions.CompressionLevel level) {
        switch (level) {
            case MAXIMUM:
                return 64; // Lower audio bitrate for maximum compression
            case BALANCED:
                return 128; // Standard audio bitrate
            case BEST_QUALITY:
                return 192; // Higher audio bitrate
            default:
                return 128;
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
        return "mp4,avi,mkv,mov,wmv,webm,flv";
    }
}