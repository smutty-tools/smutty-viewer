package io.github.smutty_tools.smutty_viewer.Decompress;

import android.util.Log;

import org.tukaani.xz.XZInputStream;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import io.github.smutty_tools.smutty_viewer.Tools.Logger;

public class Decompressor {

    public static final String TAG = "Decompressor";

    private Logger logger;
    private FinishedDecompressionReceiver receiver;

    public Decompressor(Logger logger, FinishedDecompressionReceiver receiver) {
        this.logger = logger;
        this.receiver = receiver;
    }

    public void queue(File storedFile, int actionId) {
        Log.d(TAG, "Action " + Integer.toString(actionId) + " decompressing " + storedFile.toString());

        // TODO: do as AsyncTask

        byte[] xzBuffer = null;
        // decompress index file
        try {
            FileInputStream fileIn = new FileInputStream(storedFile);
            XZInputStream xzIn = new XZInputStream(fileIn);
            // trigger reading the first block
            int result = xzIn.read();
            if (result == -1) {
                throw new IOException("Error while reading first byte of compressed data");
            }
            Log.d(TAG, "First byte read is " + Integer.toString(result));
            // setup buffer and handle already read byte
            int uncompressedSize = xzIn.available() + 1;
            logger.debug("UncompressedSize data length", uncompressedSize);
            xzBuffer = new byte[uncompressedSize ];
            xzBuffer[0] = (byte) result;
            // read the rest of the file
            result = xzIn.read(xzBuffer, 1, uncompressedSize - 1);
            logger.debug("Extracted", uncompressedSize, "bytes of data from compressed input");
            if (result != uncompressedSize - 1) {
                throw new IOException("Could not extract all of compressed data");
            }
            // ensure that final
            result = xzIn.read();
            if (result != -1) {
                throw new IOException("Remaining data found after end of decompression");
            }
            // clean up
            xzIn.close();
            fileIn.close();
        } catch (IOException e) {
            logger.error("Error while decompressing file", e.getMessage());
            return;
        }
        if (xzBuffer == null) {
            logger.error("Nothing extracted from file");
            return;
        }

        // notify callback
        receiver.decompressionFinished(storedFile, xzBuffer, actionId);
    }
}
