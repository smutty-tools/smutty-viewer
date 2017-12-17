package io.github.smutty_tools.smutty_viewer.Decompress;

import android.util.Log;

import org.tukaani.xz.XZInputStream;

import java.io.IOException;
import java.io.InputStream;

public class Decompressor {

    private static final String TAG = "Decompressor";

    public static byte[] extractXz(InputStream inputStream) throws IOException {
        byte[] xzBuffer = null;
        // decompress index file
        XZInputStream xzIn = new XZInputStream(inputStream);
        // trigger reading the first block
        int result = xzIn.read();
        if (result == -1) {
            throw new IOException("Error while reading first byte of compressed data");
        }
        Log.d(TAG, "First byte read is " + Integer.toString(result));
        // setup buffer and handle already read byte
        int uncompressedSize = xzIn.available() + 1;
        Log.d(TAG, "UncompressedSize data length: " + uncompressedSize);
        xzBuffer = new byte[uncompressedSize];
        xzBuffer[0] = (byte) result;
        // read the rest of the file
        result = xzIn.read(xzBuffer, 1, uncompressedSize - 1);
        if (result != uncompressedSize - 1) {
            throw new IOException("Could not extract all of compressed data");
        }
        Log.d(TAG, "Extracted " + uncompressedSize + " bytes of data from compressed input");
        // ensure that final
        result = xzIn.read();
        if (result != -1) {
            throw new IOException("Remaining data found after end of decompression");
        }
        // clean up
        xzIn.close();
        return xzBuffer;
    }
}
