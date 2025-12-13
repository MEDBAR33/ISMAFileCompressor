package com.ismafilecompressor.service.compressor;

import com.ismafilecompressor.model.CompressionOptions;
import com.ismafilecompressor.util.FileManager;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;

public class AudioCompressor implements FileCompressor {

    @Override
    public File compress(File input, CompressionOptions options) throws Exception {
        // Note: Real audio compression requires libraries like FFmpeg
        // This is a basic implementation that just copies the file

        String ext = FileManager.getFileExtension(input.getName()).toLowerCase();
        File output = FileManager.createOutputFile(input, "compressed_",
                options.getOutputDirectory(), ext);

        // For MP3, we could re-encode with lower bitrate
        if ("mp3".equals(ext) && options.getCompressionLevel().isAggressive()) {
            // TODO: Implement MP3 re-encoding with lower bitrate
            copyFile(input, output);
        } else {
            copyFile(input, output);
        }

        return output;
    }

    private void copyFile(File source, File destination) throws Exception {
        try (FileInputStream fis = new FileInputStream(source);
             FileOutputStream fos = new FileOutputStream(destination)) {
            fis.transferTo(fos);
        }
    }

    @Override
    public String getSupportedFormats() {
        return "mp3,wav,flac,aac,ogg";
    }
}