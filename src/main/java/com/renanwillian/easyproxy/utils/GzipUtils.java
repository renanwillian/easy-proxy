package com.renanwillian.easyproxy.utils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.GZIPInputStream;

public class GzipUtils {

    public static final String GZIP = "gzip";

    private GzipUtils() {}

    public static byte[] uncompressIfGzipped(byte[] responseData, String contentEncoding) throws IOException {
        if (GZIP.equalsIgnoreCase(contentEncoding)) return uncompress(responseData);
        return responseData;
    }

    public static byte[] uncompress(byte[] responseData) throws IOException {
        byte[] buffer = new byte[2048];
        try (ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(responseData);
             GZIPInputStream gzipInputStream = new GZIPInputStream(byteArrayInputStream);
             ByteArrayOutputStream uncompressedOutput = new ByteArrayOutputStream()) {
            int bytesRead;
            while ((bytesRead = gzipInputStream.read(buffer)) != -1) {
                uncompressedOutput.write(buffer, 0, bytesRead);
            }
            return uncompressedOutput.toByteArray();
        }
    }
}
