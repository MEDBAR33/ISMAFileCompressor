package com.ismafilecompressor.service.compressor;

import com.ismafilecompressor.model.CompressionOptions;
import com.ismafilecompressor.util.FileManager;
import java.io.File;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import java.io.*;

public class DocumentCompressor implements FileCompressor {

    @Override
    public File compress(File input, CompressionOptions options) throws Exception {
        String ext = FileManager.getFileExtension(input.getName()).toLowerCase();

        // For Office documents (DOCX, PPTX, XLSX), they're ZIP files
        if (ext.equals("docx") || ext.equals("pptx") || ext.equals("xlsx")) {
            return compressOfficeDocument(input, options);
        }

        // For other documents, just copy (can't compress much)
        File output = FileManager.createOutputFile(input, "compressed_",
                options.getOutputDirectory(), ext);

        try (FileInputStream fis = new FileInputStream(input);
             FileOutputStream fos = new FileOutputStream(output)) {
            fis.transferTo(fos);
        }

        return output;
    }

    private File compressOfficeDocument(File input, CompressionOptions options) throws Exception {
        File output = FileManager.createOutputFile(input, "compressed_",
                options.getOutputDirectory(), FileManager.getFileExtension(input.getName()));

        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(input));
             ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(output))) {

            // Set compression level for the ZIP
            if (options.getCompressionLevel().isAggressive()) {
                zos.setLevel(9); // Maximum compression
            } else if (options.getCompressionLevel() == CompressionOptions.CompressionLevel.BALANCED) {
                zos.setLevel(6); // Balanced
            } else {
                zos.setLevel(1); // Best speed
            }

            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                ZipEntry newEntry = new ZipEntry(entry.getName());
                newEntry.setTime(entry.getTime());
                newEntry.setComment(entry.getComment());
                newEntry.setExtra(entry.getExtra());
                zos.putNextEntry(newEntry);

                // Compress images inside documents if aggressive compression is enabled
                if (options.getCompressionLevel().isAggressive() && 
                    (entry.getName().endsWith(".png") || entry.getName().endsWith(".jpg") ||
                     entry.getName().endsWith(".jpeg"))) {

                    // Read image data
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    zis.transferTo(baos);
                    byte[] imageData = baos.toByteArray();

                    // Try to compress the image using ImageCompressor
                    try {
                        File tempImage = File.createTempFile("doc_img_", 
                            entry.getName().substring(entry.getName().lastIndexOf('.')));
                        try (FileOutputStream fos = new FileOutputStream(tempImage)) {
                            fos.write(imageData);
                        }

                        // Compress the image
                        ImageCompressor imageCompressor = new ImageCompressor();
                        File compressedImage = imageCompressor.compress(tempImage, options);
                        
                        // Read compressed image data
                        byte[] compressedData = java.nio.file.Files.readAllBytes(compressedImage.toPath());
                        zos.write(compressedData);
                        
                        // Clean up temp files
                        tempImage.delete();
                        compressedImage.delete();
                    } catch (Exception e) {
                        // If image compression fails, use original data
                        zos.write(imageData);
                    }
                } else {
                    // For non-image files or non-aggressive mode, just copy
                    zis.transferTo(zos);
                }

                zos.closeEntry();
                zis.closeEntry();
            }
        }

        return output;
    }

    @Override
    public String getSupportedFormats() {
        return "doc,docx,ppt,pptx,xls,xlsx,txt,rtf";
    }
}