package com.ismafilecompressor.service.compressor;

import com.ismafilecompressor.model.CompressionOptions;
import com.ismafilecompressor.util.FileManager;
import com.ismafilecompressor.util.LoggerUtil;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.PDPageContentStream.AppendMode;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.concurrent.TimeUnit;

public class PdfCompressor implements FileCompressor {

    @Override
    public File compress(File input, CompressionOptions options) throws Exception {
        // Try Ghostscript first (best PDF compression)
        File gsOutput = tryGhostscriptCompression(input, options);
        if (gsOutput != null && gsOutput.exists() && gsOutput.length() < input.length()) {
            return gsOutput;
        }
        
        // Fallback to PDFBox advanced compression
        return compressWithPdfBox(input, options);
    }
    
    private File tryGhostscriptCompression(File input, CompressionOptions options) {
        try {
            Process checkProcess = new ProcessBuilder("gs", "--version").start();
            if (!checkProcess.waitFor(2, TimeUnit.SECONDS) || checkProcess.exitValue() != 0) {
                return null;
            }
            
            File output = FileManager.createOutputFile(input, "compressed_",
                    options.getOutputDirectory(), "pdf");
            
            CompressionOptions.CompressionLevel level = options.getCompressionLevel();
            String imageResolution, colorImageResolution, grayImageResolution;
            
            switch (level) {
                case MAXIMUM:
                    imageResolution = "150";
                    colorImageResolution = "150";
                    grayImageResolution = "150";
                    break;
                case BALANCED:
                    imageResolution = "200";
                    colorImageResolution = "200";
                    grayImageResolution = "200";
                    break;
                case BEST_QUALITY:
                    imageResolution = "300";
                    colorImageResolution = "300";
                    grayImageResolution = "300";
                    break;
                default:
                    imageResolution = "200";
                    colorImageResolution = "200";
                    grayImageResolution = "200";
            }
            
            ProcessBuilder pb = new ProcessBuilder(
                "gs",
                "-sDEVICE=pdfwrite",
                "-dCompatibilityLevel=1.4",
                "-dPDFSETTINGS=/screen", // /screen, /ebook, /printer, /prepress
                "-dNOPAUSE",
                "-dQUIET",
                "-dBATCH",
                "-dDetectDuplicateImages=true",
                "-dCompressFonts=true",
                "-dSubsetFonts=true",
                "-dEmbedAllFonts=true",
                "-sColorConversionStrategy=RGB",
                "-sColorConversionStrategyForImages=RGB",
                "-dProcessColorModel=/DeviceRGB",
                "-dConvertCMYKImagesToRGB=true",
                "-dDownsampleColorImages=true",
                "-dColorImageResolution=" + colorImageResolution,
                "-dDownsampleGrayImages=true",
                "-dGrayImageResolution=" + grayImageResolution,
                "-dDownsampleMonoImages=true",
                "-dMonoImageResolution=" + imageResolution,
                "-dAutoRotatePages=/None",
                "-dEncodeColorImages=true",
                "-dEncodeGrayImages=true",
                "-dEncodeMonoImages=true",
                "-sOutputFile=" + output.getAbsolutePath(),
                input.getAbsolutePath()
            );
            
            Process process = pb.start();
            boolean completed = process.waitFor(300, TimeUnit.SECONDS);
            
            if (completed && process.exitValue() == 0 && output.exists() && output.length() > 0) {
                LoggerUtil.logInfo("Used Ghostscript for PDF compression");
                return output;
            }
        } catch (Exception e) {
            // Ghostscript not available
        }
        return null;
    }
    
    private File compressWithPdfBox(File input, CompressionOptions options) throws Exception {
        PDDocument document = PDDocument.load(input);

        try {
            PDFRenderer renderer = new PDFRenderer(document);
            File output = FileManager.createOutputFile(input, "compressed_",
                    options.getOutputDirectory(), "pdf");
            PDDocument compressedDoc = new PDDocument();

            // Advanced compression based on quality level
            CompressionOptions.CompressionLevel level = options.getCompressionLevel();
            
            for (int i = 0; i < document.getNumberOfPages(); i++) {
                PDPage page = document.getPage(i);

                if (level.isAggressive()) {
                    // MAXIMUM: Render to low DPI image with aggressive compression
                    int dpi = calculateDpi(level);
                    BufferedImage pageImage = renderer.renderImageWithDPI(i, dpi);
                    
                    // Compress image aggressively
                    File tempImage = File.createTempFile("pdf_page_", ".jpg");
                    float quality = calculateImageQuality(level);
                    byte[] compressedBytes = getImageBytes(pageImage, "jpg", quality);
                    java.io.FileOutputStream fos = new java.io.FileOutputStream(tempImage);
                    fos.write(compressedBytes);
                    fos.close();

                    PDPage newPage = new PDPage(page.getMediaBox());
                    compressedDoc.addPage(newPage);
                    PDImageXObject pdImage = PDImageXObject.createFromFileByContent(tempImage, compressedDoc);
                    try (PDPageContentStream contentStream = new PDPageContentStream(
                            compressedDoc, newPage, AppendMode.APPEND, true, true)) {
                        contentStream.drawImage(pdImage, 0, 0,
                                page.getMediaBox().getWidth(), page.getMediaBox().getHeight());
                    }
                    tempImage.delete();
                    
                } else if (level == CompressionOptions.CompressionLevel.BALANCED) {
                    // BALANCED: Compress embedded images and optimize
                    PDPage newPage = compressedDoc.importPage(page);
                    compressPageImages(newPage, compressedDoc, options, calculateImageQuality(level));
                    
                } else {
                    // BEST QUALITY: Light compression, preserve quality
                    PDPage newPage = compressedDoc.importPage(page);
                    compressPageImages(newPage, compressedDoc, options, calculateImageQuality(level));
                }
            }

            // Note: PDFBox handles compression automatically during save
            // Advanced compression is achieved through image optimization above
            compressedDoc.save(output);
            compressedDoc.close();

            return output;

        } finally {
            document.close();
        }
    }
    
    private int calculateDpi(CompressionOptions.CompressionLevel level) {
        switch (level) {
            case MAXIMUM:
                return 100; // Very low DPI for maximum compression
            case BALANCED:
                return 150;
            case BEST_QUALITY:
                return 300;
            default:
                return 150;
        }
    }
    
    private float calculateImageQuality(CompressionOptions.CompressionLevel level) {
        switch (level) {
            case MAXIMUM:
                return 0.35f; // 35% quality
            case BALANCED:
                return 0.70f; // 70% quality
            case BEST_QUALITY:
                return 0.90f; // 90% quality
            default:
                return 0.70f;
        }
    }
    

    private void compressPageImages(PDPage page, PDDocument doc, CompressionOptions options, float targetQuality) {
        try {
            PDResources resources = page.getResources();
            if (resources == null) return;
            
            Iterable<COSName> xObjectNames = resources.getXObjectNames();
            if (xObjectNames == null) return;
            
            for (COSName xObjectName : xObjectNames) {
                try {
                    PDXObject xObject = resources.getXObject(xObjectName);
                    if (xObject instanceof PDImageXObject) {
                        PDImageXObject image = (PDImageXObject) xObject;
                        BufferedImage bufferedImage = image.getImage();
                        
                        // Advanced image compression with adaptive scaling
                        CompressionOptions.CompressionLevel level = options.getCompressionLevel();
                        float scaleFactor = calculateScaleFactor(level);
                        
                        int targetWidth = (int) (bufferedImage.getWidth() * scaleFactor);
                        int targetHeight = (int) (bufferedImage.getHeight() * scaleFactor);
                        
                        // Only resize if it will reduce size significantly
                        if (targetWidth < bufferedImage.getWidth() || targetHeight < bufferedImage.getHeight()) {
                            BufferedImage compressedImage = new BufferedImage(
                                targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
                            java.awt.Graphics2D g = compressedImage.createGraphics();
                            
                            // Use high-quality rendering hints
                            g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, 
                                java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                            g.setRenderingHint(java.awt.RenderingHints.KEY_RENDERING,
                                java.awt.RenderingHints.VALUE_RENDER_QUALITY);
                            g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,
                                java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
                            
                            g.drawImage(bufferedImage, 0, 0, targetWidth, targetHeight, null);
                            g.dispose();
                            
                            // Compress as JPEG with target quality
                            PDImageXObject compressedPDImage = PDImageXObject.createFromByteArray(
                                doc, getImageBytes(compressedImage, "jpg", targetQuality), xObjectName.getName());
                            resources.put(xObjectName, compressedPDImage);
                        } else {
                            // Just recompress without resizing
                            PDImageXObject compressedPDImage = PDImageXObject.createFromByteArray(
                                doc, getImageBytes(bufferedImage, "jpg", targetQuality), xObjectName.getName());
                            resources.put(xObjectName, compressedPDImage);
                        }
                    }
                } catch (Exception e) {
                    // Skip images that can't be compressed
                    continue;
                }
            }
        } catch (Exception e) {
            // If compression fails, continue without compressing images
        }
    }
    
    private float calculateScaleFactor(CompressionOptions.CompressionLevel level) {
        switch (level) {
            case MAXIMUM:
                return 0.6f; // 60% of original size
            case BALANCED:
                return 0.8f; // 80% of original size
            case BEST_QUALITY:
                return 0.95f; // 95% of original size
            default:
                return 0.8f;
        }
    }
    
    private byte[] getImageBytes(BufferedImage image, String format, float quality) throws IOException {
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        Iterator<javax.imageio.ImageWriter> writers = javax.imageio.ImageIO.getImageWritersByFormatName(format);
        if (!writers.hasNext()) {
            throw new IOException("No image writer found for format: " + format);
        }
        javax.imageio.ImageWriter writer = writers.next();
        javax.imageio.ImageWriteParam param = writer.getDefaultWriteParam();
        
        if (param.canWriteCompressed()) {
            param.setCompressionMode(javax.imageio.ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(quality);
        }
        
        try (javax.imageio.stream.ImageOutputStream ios = javax.imageio.ImageIO.createImageOutputStream(baos)) {
            writer.setOutput(ios);
            writer.write(null, new javax.imageio.IIOImage(image, null, null), param);
        } finally {
            writer.dispose();
        }
        
        return baos.toByteArray();
    }

    @Override
    public String getSupportedFormats() {
        return "pdf";
    }
}