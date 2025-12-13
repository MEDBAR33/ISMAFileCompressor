package com.ismafilecompressor.service.compressor;

import com.ismafilecompressor.model.CompressionOptions;
import com.ismafilecompressor.util.FileManager;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import java.io.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class ArchiveCompressor implements FileCompressor {

    @Override
    public File compress(File input, CompressionOptions options) throws Exception {
        String ext = FileManager.getFileExtension(input.getName()).toLowerCase();

        if ("zip".equals(ext)) {
            return recompressZip(input, options);
        } else if ("tar".equals(ext) || "gz".equals(ext) || "tgz".equals(ext)) {
            return recompressTarGz(input, options);
        } else {
            // For other archives, try to recompress
            return recompressGeneric(input, options);
        }
    }

    private File recompressZip(File input, CompressionOptions options) throws Exception {
        File output = FileManager.createOutputFile(input, "compressed_",
                options.getOutputDirectory(), "zip");

        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(output))) {
            // Set compression level based on options
            if (options.getCompressionLevel().isAggressive()) {
                zos.setLevel(9); // Maximum compression
            } else if (options.getCompressionLevel() == CompressionOptions.CompressionLevel.BALANCED) {
                zos.setLevel(6); // Balanced
            } else {
                zos.setLevel(1); // Best speed
            }

            // TODO: Extract and recompress contents
            // For now, just copy
            try (FileInputStream fis = new FileInputStream(input)) {
                fis.transferTo(zos);
            }
        }

        return output;
    }

    private File recompressTarGz(File input, CompressionOptions options) throws Exception {
        File output = FileManager.createOutputFile(input, "compressed_",
                options.getOutputDirectory(), "tar.gz");

        try (FileOutputStream fos = new FileOutputStream(output);
             GzipCompressorOutputStream gzos = new GzipCompressorOutputStream(fos);
             TarArchiveOutputStream taos = new TarArchiveOutputStream(gzos)) {

            // Set compression level
            if (options.getCompressionLevel().isAggressive()) {
                taos.setLongFileMode(TarArchiveOutputStream.LONGFILE_GNU);
            }

            // TODO: Add entries from original archive
            // For now, just copy
            try (FileInputStream fis = new FileInputStream(input)) {
                fis.transferTo(taos);
            }
        }

        return output;
    }

    private File recompressGeneric(File input, CompressionOptions options) throws Exception {
        // For unsupported archives, just copy
        File output = FileManager.createOutputFile(input, "compressed_",
                options.getOutputDirectory(), FileManager.getFileExtension(input.getName()));

        try (FileInputStream fis = new FileInputStream(input);
             FileOutputStream fos = new FileOutputStream(output)) {
            fis.transferTo(fos);
        }

        return output;
    }

    @Override
    public String getSupportedFormats() {
        return "zip,rar,7z,tar,gz,bz2";
    }
}