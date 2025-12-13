package com.ismafilecompressor.service.compressor;

import com.ismafilecompressor.model.CompressionOptions;
import java.io.File;

public interface FileCompressor {
    File compress(File input, CompressionOptions options) throws Exception;
    String getSupportedFormats();
}