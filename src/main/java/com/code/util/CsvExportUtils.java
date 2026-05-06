package com.code.util;

import java.nio.charset.StandardCharsets;

public final class CsvExportUtils {

    private static final byte[] UTF8_BOM = new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    private CsvExportUtils() {
    }

    public static byte[] toExcelCompatibleUtf8Bytes(String content) {
        byte[] csvBytes = (content == null ? "" : content).getBytes(StandardCharsets.UTF_8);
        byte[] result = new byte[UTF8_BOM.length + csvBytes.length];
        System.arraycopy(UTF8_BOM, 0, result, 0, UTF8_BOM.length);
        System.arraycopy(csvBytes, 0, result, UTF8_BOM.length, csvBytes.length);
        return result;
    }
}

