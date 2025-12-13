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

            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                zos.putNextEntry(new ZipEntry(entry.getName()));

                // Don't compress already compressed files inside
                if (entry.getName().endsWith(".png") || entry.getName().endsWith(".jpg") ||
                        entry.getName().endsWith(".jpeg")) {

                    // For images inside documents, we could compress them here
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    zis.transferTo(baos);
                    byte[] data = baos.toByteArray();

                    // TODO: Compress image data
                    zos.write(data);
                } else {
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