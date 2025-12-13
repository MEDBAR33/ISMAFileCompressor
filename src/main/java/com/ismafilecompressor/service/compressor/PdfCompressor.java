package com.ismafilecompressor.service.compressor;

import com.ismafilecompressor.model.CompressionOptions;
import com.ismafilecompressor.util.FileManager;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.PDPageContentStream.AppendMode;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;

public class PdfCompressor implements FileCompressor {

    @Override
    public File compress(File input, CompressionOptions options) throws Exception {
        PDDocument document = PDDocument.load(input);

        try {
            // Strategy: Lower DPI for images within PDF
            PDFRenderer renderer = new PDFRenderer(document);

            File output = FileManager.createOutputFile(input, "compressed_",
                    options.getOutputDirectory(), "pdf");

            PDDocument compressedDoc = new PDDocument();

            // Copy pages with compression
            for (int i = 0; i < document.getNumberOfPages(); i++) {
                PDPage page = document.getPage(i);

                // For aggressive compression, render to image and back
                if (options.getCompressionLevel().isAggressive()) {
                    BufferedImage pageImage = renderer.renderImageWithDPI(i, options.getPdfDpi());
                    File tempImage = File.createTempFile("pdf_page_", ".jpg");
                    ImageIO.write(pageImage, "jpg", tempImage);

                    // Create new page
                    PDPage newPage = new PDPage(page.getMediaBox());
                    compressedDoc.addPage(newPage);

                    // Add compressed image
                    PDImageXObject pdImage = PDImageXObject.createFromFileByContent(tempImage, compressedDoc);
                    try (PDPageContentStream contentStream = new PDPageContentStream(
                            compressedDoc, newPage, AppendMode.APPEND, true, true)) {
                        contentStream.drawImage(pdImage, 0, 0,
                                page.getMediaBox().getWidth(), page.getMediaBox().getHeight());
                    }

                    tempImage.delete();
                } else {
                    // Just copy the page for balanced/best quality
                    compressedDoc.importPage(page);
                }
            }

            // Save with compression settings
            compressedDoc.save(output);
            compressedDoc.close();

            return output;

        } finally {
            document.close();
        }
    }

    @Override
    public String getSupportedFormats() {
        return "pdf";
    }
}