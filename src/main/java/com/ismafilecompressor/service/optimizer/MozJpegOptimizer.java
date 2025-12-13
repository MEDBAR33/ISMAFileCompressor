package com.ismafilecompressor.service.optimizer;

import com.ismafilecompressor.util.FileManager;
import java.io.*;
import java.util.concurrent.TimeUnit;

/**
 * Optimizer using MozJPEG (cjpeg) for better JPEG compression
 * Requires cjpeg executable in system PATH
 */
public class MozJpegOptimizer {

    public File optimizeJpeg(File input, int quality, boolean progressive) throws Exception {
        if (!input.exists()) {
            throw new FileNotFoundException("Input file not found: " + input.getPath());
        }

        File output = FileManager.createOutputFile(input, "optimized_", null, "jpg");

        // Build command for cjpeg (MozJPEG)
        ProcessBuilder pb = new ProcessBuilder();
        pb.command(
                "cjpeg",
                "-quality", String.valueOf(quality),
                progressive ? "-progressive" : "-baseline",
                "-optimize",
                "-outfile", output.getAbsolutePath(),
                input.getAbsolutePath()
        );

        Process process = pb.start();

        // Read error stream
        BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
        StringBuilder error = new StringBuilder();
        String line;
        while ((line = errorReader.readLine()) != null) {
            error.append(line).append("\n");
        }

        // Wait for process
        boolean completed = process.waitFor(30, TimeUnit.SECONDS);

        if (!completed) {
            process.destroyForcibly();
            throw new RuntimeException("MozJPEG optimization timed out");
        }

        int exitCode = process.exitValue();
        if (exitCode != 0) {
            throw new RuntimeException("MozJPEG failed with exit code " + exitCode + ": " + error);
        }

        // Check if output was created
        if (!output.exists() || output.length() == 0) {
            throw new RuntimeException("MozJPEG failed to create output file");
        }

        return output;
    }

    public boolean isAvailable() {
        try {
            Process process = new ProcessBuilder("cjpeg", "-version").start();
            return process.waitFor(5, TimeUnit.SECONDS) && process.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    public String getVersion() {
        try {
            Process process = new ProcessBuilder("cjpeg", "-version").start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String version = reader.readLine();
            process.waitFor(5, TimeUnit.SECONDS);
            return version != null ? version : "Unknown";
        } catch (Exception e) {
            return "Not available";
        }
    }

    public double estimateCompressionRatio(int quality) {
        // Estimated compression ratios based on quality
        if (quality >= 90) return 0.7; // 30% savings
        if (quality >= 80) return 0.6; // 40% savings
        if (quality >= 70) return 0.5; // 50% savings
        if (quality >= 60) return 0.4; // 60% savings
        if (quality >= 50) return 0.3; // 70% savings
        return 0.2; // 80% savings
    }

    public String getOptimizationTips() {
        return """
               MozJPEG Optimization Tips:
               1. Quality 80-85: Best balance for web images
               2. Use progressive JPEG for web (faster perceived loading)
               3. Enable optimize flag for better compression
               4. Use baseline for maximum compatibility
               5. Try different chroma subsampling for extra savings
               """;
    }
}