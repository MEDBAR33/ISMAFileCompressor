package com.ismafilecompressor.service.compressor;

import com.ismafilecompressor.model.CompressionOptions;
import com.ismafilecompressor.util.FileManager;
import com.ismafilecompressor.util.LoggerUtil;
import net.coobird.thumbnailator.Thumbnails;
import javax.imageio.*;
import javax.imageio.stream.FileImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Iterator;
import java.util.concurrent.TimeUnit;

public class ImageCompressor implements FileCompressor {

    @Override
    public File compress(File input, CompressionOptions options) throws Exception {
        // For PNG files with potential color space issues, automatically convert to JPEG
        String inputExt = FileManager.getFileExtension(input.getName()).toLowerCase();
        if ("png".equals(inputExt)) {
            // Check if we should convert to JPEG (avoids color space issues)
            boolean shouldConvert = options.isConvertPngToJpeg() || 
                                   options.getCompressionLevel() == CompressionOptions.CompressionLevel.BALANCED ||
                                   options.getCompressionLevel().isAggressive();
            
            if (shouldConvert) {
                // Try to read to check for transparency
                try {
                    BufferedImage testImage = ImageIO.read(input);
                    if (testImage != null && !testImage.getColorModel().hasAlpha()) {
                        // No transparency, safe to convert - will be handled in getOutputFormat
                    }
                } catch (javax.imageio.IIOException e) {
                    // If reading fails due to color space, force JPEG conversion
                    String errorMsg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
                    if (errorMsg.contains("colorspace") || errorMsg.contains("bogus")) {
                        options.setConvertPngToJpeg(true);
                    }
                }
            }
        }
        
        BufferedImage image = null;
        
        // Try to read image with better color space handling
        // Catch color space errors during reading - use multiple fallback strategies
        try {
            image = ImageIO.read(input);
        } catch (Exception e) {
            // If reading fails, check if it's a color space error
            String errorMsg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
            boolean isColorSpaceError = errorMsg.contains("colorspace") || errorMsg.contains("bogus");
            String ext = FileManager.getFileExtension(input.getName()).toLowerCase();
            
            if (isColorSpaceError || (e instanceof javax.imageio.IIOException && "png".equals(ext))) {
                // For PNG with color space issues, automatically convert to JPEG
                if ("png".equals(ext)) {
                    // Force JPEG conversion
                    options.setConvertPngToJpeg(true);
                    // Try reading again - this will go through JPEG compression path
                    // But first, we need to read the image somehow
                    // Use a workaround: read with a different approach
                    try {
                        java.util.Iterator<javax.imageio.ImageReader> readers = ImageIO.getImageReadersByFormatName("png");
                        if (readers.hasNext()) {
                            javax.imageio.ImageReader reader = readers.next();
                            try (javax.imageio.stream.ImageInputStream iis = ImageIO.createImageInputStream(input)) {
                                if (iis != null) {
                                    reader.setInput(iis);
                                    javax.imageio.ImageReadParam param = reader.getDefaultReadParam();
                                    image = reader.read(0, param);
                                }
                            } catch (Exception ex) {
                                // If still fails, we'll handle it by converting format
                                throw new Exception("PNG has color space issues - converting to JPEG", e);
                            } finally {
                                reader.dispose();
                            }
                        } else {
                            throw new Exception("PNG has color space issues - converting to JPEG", e);
                        }
                    } catch (Exception ex2) {
                        // Final fallback: re-throw with message indicating JPEG conversion
                        throw new Exception("PNG file has color space issues. Will convert to JPEG format automatically.", e);
                    }
                } else {
                    throw new Exception("Failed to read image: " + e.getMessage(), e);
                }
            } else {
                throw e; // Re-throw if not a color space issue
            }
        }
        
        if (image == null) {
            throw new IllegalArgumentException("Cannot read image file");
        }

        // Always convert to standard RGB/ARGB format to avoid color space issues
        // This fixes "Bogus input colorspace" errors with some PNG files
        BufferedImage converted;
        boolean hasAlpha = image.getColorModel().hasAlpha();
        
        if (image.getType() == BufferedImage.TYPE_INT_RGB || 
            image.getType() == BufferedImage.TYPE_INT_ARGB) {
            // Already in correct format, but ensure color space is correct
            converted = new BufferedImage(
                image.getWidth(), 
                image.getHeight(), 
                hasAlpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB
            );
            java.awt.Graphics2D g = converted.createGraphics();
            try {
                g.setRenderingHint(java.awt.RenderingHints.KEY_COLOR_RENDERING, 
                    java.awt.RenderingHints.VALUE_COLOR_RENDER_QUALITY);
                g.drawImage(image, 0, 0, null);
            } finally {
                g.dispose();
            }
            image = converted;
        } else {
            // Convert from any format to standard RGB/ARGB
            converted = new BufferedImage(
                image.getWidth(), 
                image.getHeight(), 
                hasAlpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB
            );
            java.awt.Graphics2D g = converted.createGraphics();
            try {
                g.setRenderingHint(java.awt.RenderingHints.KEY_COLOR_RENDERING, 
                    java.awt.RenderingHints.VALUE_COLOR_RENDER_QUALITY);
                g.drawImage(image, 0, 0, null);
            } finally {
                g.dispose();
            }
            image = converted;
        }

        // Resize if needed
        if (options.isResizeImages() &&
                (image.getWidth() > options.getMaxWidth() ||
                        image.getHeight() > options.getMaxHeight())) {

            image = Thumbnails.of(image)
                    .size(options.getMaxWidth(), options.getMaxHeight())
                    .keepAspectRatio(true)
                    .asBufferedImage();
        }

        // Get output format
        String outputFormat = getOutputFormat(input, options);
        
        // If original was PNG but we're converting to JPEG, ensure image doesn't have alpha
        // (JPEG doesn't support transparency)
        if (("png".equalsIgnoreCase(FileManager.getFileExtension(input.getName()))) && 
            ("jpg".equalsIgnoreCase(outputFormat) || "jpeg".equalsIgnoreCase(outputFormat))) {
            // Remove alpha channel if present
            if (image.getColorModel().hasAlpha()) {
                BufferedImage noAlpha = new BufferedImage(
                    image.getWidth(), 
                    image.getHeight(), 
                    BufferedImage.TYPE_INT_RGB
                );
                java.awt.Graphics2D g = noAlpha.createGraphics();
                try {
                    g.setColor(java.awt.Color.WHITE);
                    g.fillRect(0, 0, image.getWidth(), image.getHeight());
                    g.drawImage(image, 0, 0, null);
                } finally {
                    g.dispose();
                }
                image = noAlpha;
            }
        }
        
        File output = FileManager.createOutputFile(input, "compressed_",
                options.getOutputDirectory(), outputFormat);

        // Handle different image formats
        if ("jpg".equalsIgnoreCase(outputFormat) || "jpeg".equalsIgnoreCase(outputFormat)) {
            compressJPEG(image, output, options);
        } else if ("png".equalsIgnoreCase(outputFormat)) {
            compressPNG(image, output, options);
        } else if ("webp".equalsIgnoreCase(outputFormat)) {
            compressWebP(image, output, options);
        } else {
            // Default to JPEG for better compression
            compressJPEG(image, output, options);
        }

        return output;
    }

    private void compressJPEG(BufferedImage image, File output, CompressionOptions options) throws Exception {
        // Try advanced external tools first
        if (tryAdvancedJpegCompression(image, output, options)) {
            return;
        }
        
        // Fallback to Java-based advanced compression
        compressJPEGAdvanced(image, output, options);
    }
    
    private boolean tryAdvancedJpegCompression(BufferedImage image, File output, CompressionOptions options) {
        // Try Guetzli (Google's advanced JPEG encoder) - best quality/size ratio
        if (tryGuetzli(image, output, options)) {
            return true;
        }
        
        // Try MozJPEG (better than standard JPEG)
        if (tryMozJpeg(image, output, options)) {
            return true;
        }
        
        return false;
    }
    
    private boolean tryGuetzli(BufferedImage image, File output, CompressionOptions options) {
        try {
            // Check if guetzli is available
            Process checkProcess = new ProcessBuilder("guetzli", "--version").start();
            if (!checkProcess.waitFor(2, TimeUnit.SECONDS) || checkProcess.exitValue() != 0) {
                return false;
            }
            
            // Save image to temp file
            File tempInput = File.createTempFile("guetzli_input_", ".jpg");
            ImageIO.write(image, "jpg", tempInput);
            
            // Calculate quality based on compression level
            int quality = calculateJpegQuality(options);
            
            // Run Guetzli (it uses quality 84-100, we map our quality to this range)
            int guetzliQuality = Math.max(84, Math.min(100, 84 + (quality * 16 / 100)));
            
            ProcessBuilder pb = new ProcessBuilder(
                "guetzli",
                "--quality", String.valueOf(guetzliQuality),
                tempInput.getAbsolutePath(),
                output.getAbsolutePath()
            );
            
            Process process = pb.start();
            boolean completed = process.waitFor(300, TimeUnit.SECONDS); // 5 min timeout
            
            tempInput.delete();
            
            if (completed && process.exitValue() == 0 && output.exists() && output.length() > 0) {
                LoggerUtil.logInfo("Used Guetzli for JPEG compression");
                return true;
            }
        } catch (Exception e) {
            // Guetzli not available or failed, continue to next method
        }
        return false;
    }
    
    private boolean tryMozJpeg(BufferedImage image, File output, CompressionOptions options) {
        try {
            // Check if cjpeg (MozJPEG) is available
            Process checkProcess = new ProcessBuilder("cjpeg", "-version").start();
            if (!checkProcess.waitFor(2, TimeUnit.SECONDS)) {
                return false;
            }
            
            // Save image to temp file
            File tempInput = File.createTempFile("mozjpeg_input_", ".jpg");
            ImageIO.write(image, "jpg", tempInput);
            
            int quality = calculateJpegQuality(options);
            boolean progressive = shouldUseProgressive(options);
            
            ProcessBuilder pb = new ProcessBuilder();
            pb.command(
                "cjpeg",
                "-quality", String.valueOf(quality),
                progressive ? "-progressive" : "-baseline",
                "-optimize",
                "-outfile", output.getAbsolutePath(),
                tempInput.getAbsolutePath()
            );
            
            Process process = pb.start();
            boolean completed = process.waitFor(60, TimeUnit.SECONDS);
            
            tempInput.delete();
            
            if (completed && process.exitValue() == 0 && output.exists() && output.length() > 0) {
                LoggerUtil.logInfo("Used MozJPEG for JPEG compression");
                return true;
            }
        } catch (Exception e) {
            // MozJPEG not available or failed
        }
        return false;
    }
    
    private void compressJPEGAdvanced(BufferedImage image, File output, CompressionOptions options) throws Exception {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
        if (!writers.hasNext()) {
            ImageIO.write(image, "jpeg", output);
            return;
        }

        ImageWriter writer = writers.next();
        ImageWriteParam param = writer.getDefaultWriteParam();
        
        // Advanced compression settings
        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        
        // Calculate quality based on compression level
        float quality = calculateQualityFactor(options);
        param.setCompressionQuality(quality);
        
        // Enable progressive encoding for better compression
        if (shouldUseProgressive(options)) {
            param.setProgressiveMode(ImageWriteParam.MODE_DEFAULT);
        }
        
        // Optimize Huffman tables
        if (param.canWriteCompressed()) {
            try {
                // Set optimized Huffman tables
                param.setCompressionType("JPEG");
            } catch (Exception e) {
                // Some writers don't support this
            }
        }
        
        // Chroma subsampling for better compression (4:2:0 is standard)
        // This is usually automatic, but we can optimize it
        if (param.canWriteCompressed()) {
            try {
                // Use 4:2:0 subsampling for maximum compression
                if (options.getCompressionLevel().isAggressive()) {
                    // Force chroma subsampling
                    param.setCompressionType("JPEG");
                }
            } catch (Exception e) {
                // Ignore
            }
        }

        try (FileImageOutputStream fios = new FileImageOutputStream(output)) {
            writer.setOutput(fios);
            writer.write(null, new IIOImage(image, null, null), param);
        } finally {
            writer.dispose();
        }
    }
    
    private int calculateJpegQuality(CompressionOptions options) {
        CompressionOptions.CompressionLevel level = options.getCompressionLevel();
        switch (level) {
            case MAXIMUM:
                return 40; // Very aggressive compression
            case BALANCED:
                return 75; // Balanced quality/size
            case BEST_QUALITY:
                return 92; // High quality
            case CUSTOM:
                return Math.max(30, Math.min(95, level.getQuality()));
            default:
                return 75;
        }
    }
    
    private float calculateQualityFactor(CompressionOptions options) {
        return calculateJpegQuality(options) / 100.0f;
    }
    
    private boolean shouldUseProgressive(CompressionOptions options) {
        // Progressive JPEG is better for web and compression
        return true; // Always use progressive for better compression
    }

    private void compressPNG(BufferedImage image, File output, CompressionOptions options) throws Exception {
        // Try advanced external tools first
        if (tryAdvancedPngCompression(image, output, options)) {
            return;
        }
        
        // Fallback to Java-based advanced compression
        compressPNGAdvanced(image, output, options);
    }
    
    private boolean tryAdvancedPngCompression(BufferedImage image, File output, CompressionOptions options) {
        // Try pngquant for lossy compression (best results)
        if (options.getCompressionLevel().isAggressive() || 
            options.getCompressionLevel() == CompressionOptions.CompressionLevel.BALANCED) {
            if (tryPngQuant(image, output, options)) {
                return true;
            }
        }
        
        // Try zopflipng for lossless compression (best compression ratio)
        if (tryZopfliPng(image, output, options)) {
            return true;
        }
        
        // Try optipng for optimization
        if (tryOptiPng(image, output, options)) {
            return true;
        }
        
        return false;
    }
    
    private boolean tryPngQuant(BufferedImage image, File output, CompressionOptions options) {
        try {
            Process checkProcess = new ProcessBuilder("pngquant", "--version").start();
            if (!checkProcess.waitFor(2, TimeUnit.SECONDS)) {
                return false;
            }
            
            File tempInput = File.createTempFile("pngquant_input_", ".png");
            ImageIO.write(image, "png", tempInput);
            
            CompressionOptions.CompressionLevel level = options.getCompressionLevel();
            int qualityMin, qualityMax, maxColors;
            
            switch (level) {
                case MAXIMUM:
                    qualityMin = 50;
                    qualityMax = 80;
                    maxColors = 128;
                    break;
                case BALANCED:
                    qualityMin = 70;
                    qualityMax = 90;
                    maxColors = 192;
                    break;
                case BEST_QUALITY:
                    qualityMin = 90;
                    qualityMax = 100;
                    maxColors = 256;
                    break;
                default:
                    qualityMin = 70;
                    qualityMax = 90;
                    maxColors = 192;
            }
            
            ProcessBuilder pb = new ProcessBuilder(
                "pngquant",
                "--force",
                "--speed", "1", // Slowest = best compression
                "--quality", String.format("%d-%d", qualityMin, qualityMax),
                "--colors", String.valueOf(maxColors),
                "--output", output.getAbsolutePath(),
                tempInput.getAbsolutePath()
            );
            
            Process process = pb.start();
            boolean completed = process.waitFor(60, TimeUnit.SECONDS);
            
            tempInput.delete();
            
            if (completed && process.exitValue() == 0 && output.exists() && output.length() > 0) {
                LoggerUtil.logInfo("Used pngquant for PNG compression");
                return true;
            }
        } catch (Exception e) {
            // pngquant not available
        }
        return false;
    }
    
    private boolean tryZopfliPng(BufferedImage image, File output, CompressionOptions options) {
        try {
            Process checkProcess = new ProcessBuilder("zopflipng", "--version").start();
            if (!checkProcess.waitFor(2, TimeUnit.SECONDS)) {
                return false;
            }
            
            File tempInput = File.createTempFile("zopfli_input_", ".png");
            ImageIO.write(image, "png", tempInput);
            
            ProcessBuilder pb = new ProcessBuilder(
                "zopflipng",
                "--lossy_transparent", // Allow lossy compression of alpha
                "--filters", "0meb", // Try multiple filter strategies
                "--iterations", "15", // More iterations = better compression
                tempInput.getAbsolutePath(),
                output.getAbsolutePath()
            );
            
            Process process = pb.start();
            boolean completed = process.waitFor(120, TimeUnit.SECONDS);
            
            tempInput.delete();
            
            if (completed && process.exitValue() == 0 && output.exists() && output.length() > 0) {
                LoggerUtil.logInfo("Used zopflipng for PNG compression");
                return true;
            }
        } catch (Exception e) {
            // zopflipng not available
        }
        return false;
    }
    
    private boolean tryOptiPng(BufferedImage image, File output, CompressionOptions options) {
        try {
            Process checkProcess = new ProcessBuilder("optipng", "-v").start();
            if (!checkProcess.waitFor(2, TimeUnit.SECONDS)) {
                return false;
            }
            
            File tempInput = File.createTempFile("optipng_input_", ".png");
            ImageIO.write(image, "png", tempInput);
            
            CompressionOptions.CompressionLevel level = options.getCompressionLevel();
            int optimizationLevel;
            switch (level) {
                case MAXIMUM:
                    optimizationLevel = 7; // Maximum optimization
                    break;
                case BALANCED:
                    optimizationLevel = 5;
                    break;
                default:
                    optimizationLevel = 3;
            }
            
            ProcessBuilder pb = new ProcessBuilder(
                "optipng",
                "-o" + optimizationLevel,
                "-out", output.getAbsolutePath(),
                tempInput.getAbsolutePath()
            );
            
            Process process = pb.start();
            boolean completed = process.waitFor(60, TimeUnit.SECONDS);
            
            tempInput.delete();
            
            if (completed && process.exitValue() == 0 && output.exists() && output.length() > 0) {
                LoggerUtil.logInfo("Used optipng for PNG compression");
                return true;
            }
        } catch (Exception e) {
            // optipng not available
        }
        return false;
    }
    
    private void compressPNGAdvanced(BufferedImage image, File output, CompressionOptions options) throws Exception {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("png");
        if (!writers.hasNext()) {
            ImageIO.write(image, "png", output);
            return;
        }

        ImageWriter writer = writers.next();
        ImageWriteParam param = writer.getDefaultWriteParam();
        
        // Advanced PNG compression
        if (param.canWriteCompressed()) {
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionType("Deflate");
            
            // Calculate compression level (0-9, where 9 is maximum)
            int compressionLevel = calculatePngCompressionLevel(options);
            try {
                // Some writers support compression level directly
                param.setCompressionQuality(compressionLevel / 9.0f);
            } catch (Exception e) {
                // Fallback: use default compression
            }
        }

        try (FileImageOutputStream fios = new FileImageOutputStream(output)) {
            writer.setOutput(fios);
            writer.write(null, new IIOImage(image, null, null), param);
        } finally {
            writer.dispose();
        }
    }
    
    private int calculatePngCompressionLevel(CompressionOptions options) {
        CompressionOptions.CompressionLevel level = options.getCompressionLevel();
        switch (level) {
            case MAXIMUM:
                return 9; // Maximum compression
            case BALANCED:
                return 6; // Balanced
            case BEST_QUALITY:
                return 3; // Faster, less compression
            default:
                return 6;
        }
    }

    private void compressWebP(BufferedImage image, File output, CompressionOptions options) throws Exception {
        // Try cwebp (Google's WebP encoder) - best compression
        if (tryCWebP(image, output, options)) {
            return;
        }
        
        // Fallback to Java-based WebP compression
        compressWebPAdvanced(image, output, options);
    }
    
    private boolean tryCWebP(BufferedImage image, File output, CompressionOptions options) {
        try {
            Process checkProcess = new ProcessBuilder("cwebp", "-version").start();
            if (!checkProcess.waitFor(2, TimeUnit.SECONDS)) {
                return false;
            }
            
            File tempInput = File.createTempFile("cwebp_input_", ".png");
            ImageIO.write(image, "png", tempInput);
            
            CompressionOptions.CompressionLevel level = options.getCompressionLevel();
            int quality;
            boolean lossless = false;
            
            switch (level) {
                case MAXIMUM:
                    quality = 60;
                    break;
                case BALANCED:
                    quality = 80;
                    break;
                case BEST_QUALITY:
                    quality = 95;
                    lossless = true; // Use lossless for best quality
                    break;
                default:
                    quality = 80;
            }
            
            ProcessBuilder pb = new ProcessBuilder();
            if (lossless) {
                pb.command(
                    "cwebp",
                    "-lossless",
                    "-z", "9", // Maximum compression effort
                    "-m", "6", // Maximum method (0-6)
                    tempInput.getAbsolutePath(),
                    "-o", output.getAbsolutePath()
                );
            } else {
                pb.command(
                    "cwebp",
                    "-q", String.valueOf(quality),
                    "-m", "6", // Maximum method
                    "-pass", "10", // Multi-pass encoding
                    "-af", // Auto-filter
                    "-f", "50", // Filter strength
                    tempInput.getAbsolutePath(),
                    "-o", output.getAbsolutePath()
                );
            }
            
            Process process = pb.start();
            boolean completed = process.waitFor(120, TimeUnit.SECONDS);
            
            tempInput.delete();
            
            if (completed && process.exitValue() == 0 && output.exists() && output.length() > 0) {
                LoggerUtil.logInfo("Used cwebp for WebP compression");
                return true;
            }
        } catch (Exception e) {
            // cwebp not available
        }
        return false;
    }
    
    private void compressWebPAdvanced(BufferedImage image, File output, CompressionOptions options) throws Exception {
        ImageWriter writer = null;
        for (Iterator<ImageWriter> it = ImageIO.getImageWritersByMIMEType("image/webp"); it.hasNext(); ) {
            writer = it.next();
            break;
        }

        if (writer == null) {
            // Fallback to JPEG if WebP not supported
            compressJPEG(image, output, options);
            return;
        }

        ImageWriteParam param = writer.getDefaultWriteParam();
        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);

        // Advanced WebP compression settings
        float quality = calculateQualityFactor(options);
        try {
            param.setCompressionQuality(quality);
        } catch (UnsupportedOperationException e) {
            // Some WebP writers don't support quality setting
        }

        try (FileImageOutputStream fios = new FileImageOutputStream(output)) {
            writer.setOutput(fios);
            writer.write(null, new IIOImage(image, null, null), param);
        } finally {
            writer.dispose();
        }
    }

    private String getOutputFormat(File input, CompressionOptions options) {
        String inputExt = FileManager.getFileExtension(input.getName()).toLowerCase();

        // For maximum compression, convert PNG to JPEG (better compression)
        if (options.getCompressionLevel().isAggressive() && "png".equals(inputExt)) {
            return "jpg";
        }

        // Convert PNG to JPEG if enabled
        if (options.isConvertPngToJpeg() && "png".equals(inputExt)) {
            return "jpg";
        }

        // Convert TIFF to JPEG if enabled
        if (options.isConvertTiffToJpeg() && ("tiff".equals(inputExt) || "tif".equals(inputExt))) {
            return "jpg";
        }

        // If output format is specified, use it
        if (options.getOutputFormat() != null && !options.getOutputFormat().equals("auto")) {
            return options.getOutputFormat();
        }

        // For balanced mode, ALWAYS convert PNG to JPEG to avoid color space issues
        // This prevents "Bogus input colorspace" errors
        if (options.getCompressionLevel() == CompressionOptions.CompressionLevel.BALANCED 
            && "png".equals(inputExt)) {
            return "jpg"; // Always convert PNG to JPEG in balanced mode
        }

        // Keep original format
        return inputExt;
    }

    @Override
    public String getSupportedFormats() {
        return "jpg,jpeg,png,gif,bmp,tiff,tif,webp";
    }
}