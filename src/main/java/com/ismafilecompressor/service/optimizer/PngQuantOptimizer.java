package com.ismafilecompressor.service.optimizer;

import com.ismafilecompressor.util.FileManager;
import java.io.*;
import java.nio.file.Files;
import java.util.concurrent.TimeUnit;

/**
 * PNG optimizer using pngquant for lossy PNG compression
 * Requires pngquant executable in system PATH
 */
public class PngQuantOptimizer {

    public File optimizePng(File input, int maxColors, double qualityMin, double qualityMax) throws Exception {
        if (!input.exists()) {
            throw new FileNotFoundException("Input file not found: " + input.getPath());
        }

        File output = FileManager.createOutputFile(input, "quantized_", null, "png");

        // Build command for pngquant
        ProcessBuilder pb = new ProcessBuilder();
        pb.command(
                "pngquant",
                "--force",
                "--speed", "1", // 1=slow/best, 11=fast
                "--quality", String.format("%.0f-%.0f", qualityMin, qualityMax),
                "--output", output.getAbsolutePath(),
                "--", input.getAbsolutePath()
        );

        // Add max colors if specified
        if (maxColors > 0 && maxColors <= 256) {
            pb.command().add(3, "--colors");
            pb.command().add(4, String.valueOf(maxColors));
        }

        Process process = pb.start();

        // Read output and error streams
        BufferedReader outputReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));

        StringBuilder outputText = new StringBuilder();
        StringBuilder errorText = new StringBuilder();

        String line;
        while ((line = outputReader.readLine()) != null) {
            outputText.append(line).append("\n");
        }
        while ((line = errorReader.readLine()) != null) {
            errorText.append(line).append("\n");
        }

        // Wait for process
        boolean completed = process.waitFor(30, TimeUnit.SECONDS);

        if (!completed) {
            process.destroyForcibly();
            throw new RuntimeException("pngquant optimization timed out");
        }

        int exitCode = process.exitValue();
        if (exitCode != 0) {
            throw new RuntimeException("pngquant failed with exit code " + exitCode +
                    ": " + errorText + "\nOutput: " + outputText);
        }

        // Check if output was created
        if (!output.exists() || output.length() == 0) {
            // Try to use input as output (pngquant sometimes doesn't overwrite)
            if (input.exists()) {
                Files.copy(input.toPath(), output.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } else {
                throw new RuntimeException("pngquant failed to create output file");
            }
        }

        return output;
    }

    public File optimizeWithDithering(File input, double ditheringLevel) throws Exception {
        if (!input.exists()) {
            throw new FileNotFoundException("Input file not found: " + input.getPath());
        }

        File output = FileManager.createOutputFile(input, "dithered_", null, "png");

        ProcessBuilder pb = new ProcessBuilder();
        pb.command(
                "pngquant",
                "--force",
                "--speed", "1",
                "--quality", "70-95",
                "--floyd", String.format("%.2f", ditheringLevel), // 0-1 dithering level
                "--output", output.getAbsolutePath(),
                "--", input.getAbsolutePath()
        );

        Process process = pb.start();

        BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
        StringBuilder error = new StringBuilder();
        String line;
        while ((line = errorReader.readLine()) != null) {
            error.append(line).append("\n");
        }

        boolean completed = process.waitFor(30, TimeUnit.SECONDS);

        if (!completed) {
            process.destroyForcibly();
            throw new RuntimeException("pngquant with dithering timed out");
        }

        int exitCode = process.exitValue();
        if (exitCode != 0) {
            throw new RuntimeException("pngquant dithering failed: " + error);
        }

        return output;
    }

    public boolean isAvailable() {
        try {
            Process process = new ProcessBuilder("pngquant", "--version").start();
            return process.waitFor(5, TimeUnit.SECONDS) && process.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    public String getVersion() {
        try {
            Process process = new ProcessBuilder("pngquant", "--version").start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String version = reader.readLine();
            process.waitFor(5, TimeUnit.SECONDS);
            return version != null ? version : "Unknown";
        } catch (Exception e) {
            return "Not available";
        }
    }

    public double estimateSavings(int maxColors) {
        // Estimate savings based on color reduction
        if (maxColors <= 64) return 0.7; // 70% savings
        if (maxColors <= 128) return 0.5; // 50% savings
        if (maxColors <= 192) return 0.3; // 30% savings
        return 0.2; // 20% savings for 256 colors
    }

    public String getOptimizationTips() {
        return """
               PNGQuant Optimization Tips:
               1. 256 colors: Good for screenshots, minimal artifacts
               2. 128 colors: Best for most images, great quality/size
               3. 64 colors: For simple graphics, noticeable artifacts
               4. Quality 70-95: Good range for most images
               5. Speed 1: Best compression (slow)
               6. Speed 4: Balanced (default)
               7. Speed 10: Fast compression
               8. Use dithering for gradients and photos
               9. Skip dithering for sharp graphics
               """;
    }

    public String analyzeImage(File input) throws Exception {
        if (!input.exists()) {
            return "File not found";
        }

        // Simple analysis using ImageIO
        try {
            javax.imageio.ImageIO.read(input);
            return "Valid PNG image";
        } catch (Exception e) {
            return "Invalid or corrupted PNG";
        }
    }
}