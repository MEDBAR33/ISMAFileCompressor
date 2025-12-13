package com.ismafilecompressor.model;

import java.util.Arrays;
import java.util.List;

public class CompressionOptions {
    public enum CompressionLevel {
        MAXIMUM("Maximum Compression", 30, true, true, 50),
        BALANCED("Balanced", 75, false, false, 150),
        BEST_QUALITY("Best Quality", 90, false, false, 300),
        CUSTOM("Custom", 85, false, false, 200);

        private final String displayName;
        private final int quality;
        private final boolean aggressive;
        private final boolean removeMetadata;
        private final int dpi;

        CompressionLevel(String displayName, int quality, boolean aggressive,
                         boolean removeMetadata, int dpi) {
            this.displayName = displayName;
            this.quality = quality;
            this.aggressive = aggressive;
            this.removeMetadata = removeMetadata;
            this.dpi = dpi;
        }

        public String getDisplayName() { return displayName; }
        public int getQuality() { return quality; }
        public boolean isAggressive() { return aggressive; }
        public boolean isRemoveMetadata() { return removeMetadata; }
        public int getDpi() { return dpi; }

        public static List<CompressionLevel> getAll() {
            return Arrays.asList(values());
        }
    }

    private CompressionLevel compressionLevel = CompressionLevel.BALANCED;
    private String outputDirectory;
    private boolean preserveStructure = true;
    private boolean convertPngToJpeg = true;
    private boolean convertTiffToJpeg = true;
    private boolean resizeImages = false;
    private int maxWidth = 1920;
    private int maxHeight = 1080;
    private boolean keepOriginals = true;
    private String outputFormat = "auto"; // auto, jpeg, webp, png

    // Getters and Setters
    public CompressionLevel getCompressionLevel() { return compressionLevel; }
    public void setCompressionLevel(CompressionLevel compressionLevel) {
        this.compressionLevel = compressionLevel;
    }

    public String getOutputDirectory() { return outputDirectory; }
    public void setOutputDirectory(String outputDirectory) { this.outputDirectory = outputDirectory; }

    public boolean isPreserveStructure() { return preserveStructure; }
    public void setPreserveStructure(boolean preserveStructure) { this.preserveStructure = preserveStructure; }

    public boolean isConvertPngToJpeg() { return convertPngToJpeg; }
    public void setConvertPngToJpeg(boolean convertPngToJpeg) { this.convertPngToJpeg = convertPngToJpeg; }

    public boolean isConvertTiffToJpeg() { return convertTiffToJpeg; }
    public void setConvertTiffToJpeg(boolean convertTiffToJpeg) { this.convertTiffToJpeg = convertTiffToJpeg; }

    public boolean isResizeImages() { return resizeImages; }
    public void setResizeImages(boolean resizeImages) { this.resizeImages = resizeImages; }

    public int getMaxWidth() { return maxWidth; }
    public void setMaxWidth(int maxWidth) { this.maxWidth = maxWidth; }

    public int getMaxHeight() { return maxHeight; }
    public void setMaxHeight(int maxHeight) { this.maxHeight = maxHeight; }

    public boolean isKeepOriginals() { return keepOriginals; }
    public void setKeepOriginals(boolean keepOriginals) { this.keepOriginals = keepOriginals; }

    public String getOutputFormat() { return outputFormat; }
    public void setOutputFormat(String outputFormat) { this.outputFormat = outputFormat; }

    // Helper methods
    public float getQualityFactor() {
        return compressionLevel.getQuality() / 100.0f;
    }

    public int getJpegQuality() {
        return compressionLevel.getQuality();
    }

    public int getPdfDpi() {
        return compressionLevel.getDpi();
    }

    public boolean isAggressiveCompression() {
        return compressionLevel.isAggressive();
    }

    public boolean shouldRemoveMetadata() {
        return compressionLevel.isRemoveMetadata();
    }

    @Override
    public String toString() {
        return String.format("CompressionOptions{level=%s, quality=%d%%, format=%s}",
                compressionLevel.getDisplayName(), compressionLevel.getQuality(), outputFormat);
    }
}