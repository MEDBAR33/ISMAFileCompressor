package com.ismafilecompressor.service.optimizer;

import com.ismafilecompressor.util.FileManager;
import java.io.*;
import java.util.concurrent.TimeUnit;

/**
 * WebP optimizer using cwebp command-line tool
 * Requires cwebp executable in system PATH
 */
public class WebPOptimizer {

    public File convertToWebP(File input, int quality, boolean lossless) throws Exception {
        if (!input.exists()) {
            throw new FileNotFoundException("Input file not found: " + input.getPath());
        }

        String outputExt = lossless ? "webp" : "webp";
        File output = FileManager.createOutputFile(input, "webp_", null, outputExt);

        // Build command for cwebp
        ProcessBuilder pb = new ProcessBuilder();
        if (lossless) {
            pb.command(
                    "cwebp",
                    "-lossless",
                    "-z", "9", // Maximum compression
                    "-quiet",
                    input.getAbsolutePath(),
                    "-o", output.getAbsolutePath()
            );
        } else {
            pb.command(
                    "cwebp",
                    "-q", String.valueOf(quality),
                    "-m", "6", // Compression method (0=fast, 6=slow/best)
                    "-pass", "10", // Analysis passes
                    "-af", // Auto-filter
                    "-f", "50", // Filter strength
                    "-sharpness", "0", // Sharpness
                    "-strong", // Strong filtering
                    "-quiet",
                    input.getAbsolutePath(),
                    "-o", output.getAbsolutePath()
            );
        }

        Process process = pb.start();

        // Read error stream
        BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
        StringBuilder error = new StringBuilder();
        String line;
        while ((line = errorReader.readLine()) != null) {
            error.append(line).append("\n");
        }

        // Wait for process
        boolean completed = process.waitFor(60, TimeUnit.SECONDS);

        if (!completed) {
            process.destroyForcibly();
            throw new RuntimeException("WebP conversion timed out");
        }

        int exitCode = process.exitValue();
        if (exitCode != 0) {
            throw new RuntimeException("cwebp failed with exit code " + exitCode + ": " + error);
        }

        // Check if output was created
        if (!output.exists() || output.length() == 0) {
            throw new RuntimeException("cwebp failed to create output file");
        }

        return output;
    }

    public File convertFromWebP(File input, String outputFormat) throws Exception {
        if (!input.exists()) {
            throw new FileNotFoundException("Input file not found: " + input.getPath());
        }

        File output = FileManager.createOutputFile(input, "converted_", null, outputFormat);

        // Use dwebp to convert from WebP
        ProcessBuilder pb = new ProcessBuilder();
        pb.command(
                "dwebp",
                input.getAbsolutePath(),
                "-o", output.getAbsolutePath()
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
            throw new RuntimeException("WebP to " + outputFormat + " conversion timed out");
        }

        int exitCode = process.exitValue();
        if (exitCode != 0) {
            throw new RuntimeException("dwebp failed with exit code " + exitCode + ": " + error);
        }

        return output;
    }

    public boolean isAvailable() {
        try {
            Process process = new ProcessBuilder("cwebp", "-version").start();
            return process.waitFor(5, TimeUnit.SECONDS) && process.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    public String getVersion() {
        try {
            Process process = new ProcessBuilder("cwebp", "-version").start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String version = reader.readLine();
            process.waitFor(5, TimeUnit.SECONDS);
            return version != null ? version : "Unknown";
        } catch (Exception e) {
            return "Not available";
        }
    }

    public double getCompressionAdvantage() {
        // WebP typically offers 25-34% smaller files than JPEG at equivalent quality
        return 0.3; // 30% average savings
    }

    public String getWebPTips() {
        return """
               WebP Optimization Tips:
               1. Lossless WebP: Perfect for graphics, logos, text
               2. Lossy WebP: Best for photos (30% smaller than JPEG)
               3. Quality 75-80: Sweet spot for most images
               4. Use -m 6 for best compression (slower)
               5. Enable -af for automatic filter selection
               6. Progressive WebP not yet widely supported
               """;
    }

    public boolean supportsAnimation() {
        try {
            Process process = new ProcessBuilder("cwebp", "-help").start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains("-loop") || line.contains("animation")) {
                    return true;
                }
            }
            process.waitFor(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            // Ignore
        }
        return false;
    }
}