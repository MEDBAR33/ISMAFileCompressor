package com.ismafilecompressor.service.optimizer;

import com.ismafilecompressor.util.FileManager;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.*;

public class ImageOptimizer {
    private final MozJpegOptimizer mozJpegOptimizer;
    private final WebPOptimizer webPOptimizer;
    private final PngQuantOptimizer pngQuantOptimizer;
    private final Map<String, Boolean> toolAvailability;

    public ImageOptimizer() {
        this.mozJpegOptimizer = new MozJpegOptimizer();
        this.webPOptimizer = new WebPOptimizer();
        this.pngQuantOptimizer = new PngQuantOptimizer();
        this.toolAvailability = new HashMap<>();

        // Check tool availability
        toolAvailability.put("mozjpeg", mozJpegOptimizer.isAvailable());
        toolAvailability.put("webp", webPOptimizer.isAvailable());
        toolAvailability.put("pngquant", pngQuantOptimizer.isAvailable());
    }

    public File optimizeImage(File input, OptimizationOptions options) throws Exception {
        if (!input.exists()) {
            throw new IllegalArgumentException("Input file does not exist: " + input.getPath());
        }

        String extension = FileManager.getFileExtension(input.getName()).toLowerCase();

        // Choose optimization strategy based on file type and options
        switch (extension) {
            case "jpg":
            case "jpeg":
                return optimizeJpeg(input, options);

            case "png":
                return optimizePng(input, options);

            case "webp":
                return optimizeWebP(input, options);

            case "gif":
            case "bmp":
            case "tiff":
            case "tif":
                return convertToOptimalFormat(input, options);

            default:
                throw new UnsupportedOperationException("Unsupported image format: " + extension);
        }
    }

    private File optimizeJpeg(File input, OptimizationOptions options) throws Exception {
        // Strategy 1: Convert to WebP if enabled and available
        if (options.convertToWebP && toolAvailability.get("webp")) {
            return webPOptimizer.convertToWebP(input, options.quality, false);
        }

        // Strategy 2: Use MozJPEG if available
        if (toolAvailability.get("mozjpeg")) {
            return mozJpegOptimizer.optimizeJpeg(input, options.quality, options.progressive);
        }

        // Strategy 3: Fallback to Java built-in compression
        return compressWithJava(input, options.quality, "jpg");
    }

    private File optimizePng(File input, OptimizationOptions options) throws Exception {
        // Strategy 1: Convert to WebP if enabled (WebP is often better for PNG content)
        if (options.convertToWebP && toolAvailability.get("webp")) {
            return webPOptimizer.convertToWebP(input, options.quality, options.lossless);
        }

        // Strategy 2: Use pngquant for lossy PNG compression
        if (options.lossyPng && toolAvailability.get("pngquant")) {
            return pngQuantOptimizer.optimizePng(input, options.maxColors,
                    options.qualityMin, options.qualityMax);
        }

        // Strategy 3: Convert to JPEG if enabled (for photos)
        if (options.convertPngToJpeg) {
            File jpegFile = FileManager.createOutputFile(input, "converted_", null, "jpg");
            BufferedImage image = ImageIO.read(input);
            ImageIO.write(image, "jpg", jpegFile);
            return optimizeJpeg(jpegFile, options);
        }

        // Strategy 4: Keep as PNG with minimal optimization
        return compressWithJava(input, 100, "png");
    }

    private File optimizeWebP(File input, OptimizationOptions options) throws Exception {
        // If we need to convert FROM WebP to another format
        if (options.outputFormat != null && !options.outputFormat.equals("webp")) {
            return webPOptimizer.convertFromWebP(input, options.outputFormat);
        }

        // Otherwise, recompress WebP
        if (toolAvailability.get("webp")) {
            return webPOptimizer.convertToWebP(input, options.quality, options.lossless);
        }

        // Can't optimize WebP without cwebp
        return input;
    }

    private File convertToOptimalFormat(File input, OptimizationOptions options) throws Exception {
        BufferedImage image = ImageIO.read(input);
        if (image == null) {
            throw new IllegalArgumentException("Cannot read image file");
        }

        // Analyze image type
        boolean hasAlpha = image.getColorModel().hasAlpha();
        boolean isPhoto = isPhotographic(image);

        // Choose optimal format
        String optimalFormat;
        if (hasAlpha) {
            optimalFormat = options.convertToWebP ? "webp" : "png";
        } else if (isPhoto) {
            optimalFormat = options.convertToWebP ? "webp" : "jpg";
        } else {
            optimalFormat = "png";
        }

        // Convert to optimal format
        File output = FileManager.createOutputFile(input, "converted_", null, optimalFormat);
        ImageIO.write(image, optimalFormat, output);

        // Optimize the converted file
        return optimizeImage(output, options);
    }

    private File compressWithJava(File input, int quality, String format) throws Exception {
        BufferedImage image = ImageIO.read(input);
        File output = FileManager.createOutputFile(input, "compressed_", null, format);

        // Java's built-in JPEG compression
        if ("jpg".equals(format) || "jpeg".equals(format)) {
            javax.imageio.ImageWriter writer = javax.imageio.ImageIO.getImageWritersByFormatName("jpeg").next();
            javax.imageio.ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(javax.imageio.ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(quality / 100.0f);

            try (javax.imageio.stream.FileImageOutputStream fios =
                         new javax.imageio.stream.FileImageOutputStream(output)) {
                writer.setOutput(fios);
                writer.write(null, new javax.imageio.IIOImage(image, null, null), param);
            } finally {
                writer.dispose();
            }
        } else {
            // Other formats
            ImageIO.write(image, format, output);
        }

        return output;
    }

    private boolean isPhotographic(BufferedImage image) {
        // Simple heuristic to detect if image is photographic
        // Real implementation would be more sophisticated
        int width = image.getWidth();
        int height = image.getHeight();

        // Photos are usually larger
        return width * height > 500 * 500;
    }

    public Map<String, String> getToolInfo() {
        Map<String, String> info = new HashMap<>();

        info.put("mozjpeg", toolAvailability.get("mozjpeg") ?
                "Available (" + mozJpegOptimizer.getVersion() + ")" : "Not available");
        info.put("webp", toolAvailability.get("webp") ?
                "Available (" + webPOptimizer.getVersion() + ")" : "Not available");
        info.put("pngquant", toolAvailability.get("pngquant") ?
                "Available (" + pngQuantOptimizer.getVersion() + ")" : "Not available");

        return info;
    }

    public OptimizationReport analyzeOptimization(File original, File optimized) {
        OptimizationReport report = new OptimizationReport();

        report.originalSize = original.length();
        report.optimizedSize = optimized.length();
        report.savingsBytes = report.originalSize - report.optimizedSize;
        report.savingsPercentage = (report.savingsBytes * 100.0) / report.originalSize;

        // Determine optimization type
        String originalExt = FileManager.getFileExtension(original.getName());
        String optimizedExt = FileManager.getFileExtension(optimized.getName());

        if (!originalExt.equalsIgnoreCase(optimizedExt)) {
            report.optimizationType = "Format conversion: " + originalExt + " → " + optimizedExt;
        } else {
            report.optimizationType = "Compression: " + originalExt;
        }

        // Check if lossless is possible
        try {
            BufferedImage origImg = ImageIO.read(original);
            BufferedImage optImg = ImageIO.read(optimized);
            report.isVisuallyLossless = isVisuallyIdentical(origImg, optImg);
        } catch (Exception e) {
            report.isVisuallyLossless = false;
        }

        return report;
    }

    private boolean isVisuallyIdentical(BufferedImage img1, BufferedImage img2) {
        // Simple check for visual identity
        if (img1.getWidth() != img2.getWidth() || img1.getHeight() != img2.getHeight()) {
            return false;
        }

        // For a real implementation, you'd use SSIM or other metrics
        return true;
    }

    public static class OptimizationOptions {
        public int quality = 85;
        public int qualityMin = 70;
        public int qualityMax = 95;
        public boolean progressive = true;
        public boolean lossless = false;
        public boolean lossyPng = true;
        public int maxColors = 256;
        public boolean convertToWebP = true;
        public boolean convertPngToJpeg = false;
        public String outputFormat = null; // null = auto

        public static OptimizationOptions forMaximumCompression() {
            OptimizationOptions opts = new OptimizationOptions();
            opts.quality = 60;
            opts.qualityMin = 50;
            opts.qualityMax = 80;
            opts.progressive = true;
            opts.lossless = false;
            opts.lossyPng = true;
            opts.maxColors = 128;
            opts.convertToWebP = true;
            opts.convertPngToJpeg = true;
            return opts;
        }

        public static OptimizationOptions forBestQuality() {
            OptimizationOptions opts = new OptimizationOptions();
            opts.quality = 95;
            opts.qualityMin = 90;
            opts.qualityMax = 100;
            opts.progressive = true;
            opts.lossless = true;
            opts.lossyPng = false;
            opts.maxColors = 256;
            opts.convertToWebP = false;
            opts.convertPngToJpeg = false;
            return opts;
        }

        public static OptimizationOptions forBalanced() {
            OptimizationOptions opts = new OptimizationOptions();
            opts.quality = 80;
            opts.qualityMin = 70;
            opts.qualityMax = 90;
            opts.progressive = true;
            opts.lossless = false;
            opts.lossyPng = true;
            opts.maxColors = 192;
            opts.convertToWebP = true;
            opts.convertPngToJpeg = false;
            return opts;
        }
    }

    public static class OptimizationReport {
        public long originalSize;
        public long optimizedSize;
        public long savingsBytes;
        public double savingsPercentage;
        public String optimizationType;
        public boolean isVisuallyLossless;

        public String getFormattedOriginalSize() {
            return FileManager.formatFileSize(originalSize);
        }

        public String getFormattedOptimizedSize() {
            return FileManager.formatFileSize(optimizedSize);
        }

        public String getFormattedSavings() {
            return FileManager.formatFileSize(savingsBytes);
        }

        public String getSummary() {
            return String.format(
                    "Optimized from %s to %s (%.1f%% savings)",
                    getFormattedOriginalSize(),
                    getFormattedOptimizedSize(),
                    savingsPercentage
            );
        }
    }
}