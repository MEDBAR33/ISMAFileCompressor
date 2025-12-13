package com.ismafilecompressor.service.compressor;

import com.ismafilecompressor.model.CompressionOptions;
import com.ismafilecompressor.util.FileManager;
import net.coobird.thumbnailator.Thumbnails;
import javax.imageio.*;
import javax.imageio.stream.FileImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Iterator;

public class ImageCompressor implements FileCompressor {

    @Override
    public File compress(File input, CompressionOptions options) throws Exception {
        BufferedImage image = ImageIO.read(input);
        if (image == null) {
            throw new IllegalArgumentException("Cannot read image file");
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

        String outputFormat = getOutputFormat(input, options);
        File output = FileManager.createOutputFile(input, "compressed_",
                options.getOutputDirectory(), outputFormat);

        // Handle different image formats
        if ("jpg".equalsIgnoreCase(outputFormat) || "jpeg".equalsIgnoreCase(outputFormat)) {
            compressJPEG(image, output, options);
        } else if ("png".equalsIgnoreCase(outputFormat)) {
            ImageIO.write(image, "png", output);
        } else if ("webp".equalsIgnoreCase(outputFormat)) {
            compressWebP(image, output, options);
        } else {
            // Default to JPEG
            compressJPEG(image, output, options);
        }

        return output;
    }

    private void compressJPEG(BufferedImage image, File output, CompressionOptions options) throws Exception {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
        if (!writers.hasNext()) {
            ImageIO.write(image, "jpeg", output);
            return;
        }

        ImageWriter writer = writers.next();
        ImageWriteParam param = writer.getDefaultWriteParam();
        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        param.setCompressionQuality(options.getQualityFactor());

        try (FileImageOutputStream fios = new FileImageOutputStream(output)) {
            writer.setOutput(fios);
            writer.write(null, new IIOImage(image, null, null), param);
        } finally {
            writer.dispose();
        }
    }

    private void compressWebP(BufferedImage image, File output, CompressionOptions options) throws Exception {
        // For WebP, we need to check if the format is supported
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

        // WebP compression settings (if supported by the writer)
        try {
            param.setCompressionQuality(options.getQualityFactor());
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

        // Keep original format
        return inputExt;
    }

    @Override
    public String getSupportedFormats() {
        return "jpg,jpeg,png,gif,bmp,tiff,tif,webp";
    }
}