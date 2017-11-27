package io.github.smutty_tools.smutty_viewer.Decompress;

import android.os.AsyncTask;
import android.util.Log;

import org.tukaani.xz.XZInputStream;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import io.github.smutty_tools.smutty_viewer.Tools.Logger;

public class DecompressorAsyncTask extends AsyncTask<File, Void, byte[]> {

    private static final String TAG = "DecompressorAsyncTask";

    public static byte[] extractXz(Logger logger, File inputFile) throws IOException {
        byte[] xzBuffer = null;
        // decompress index file
        FileInputStream fileIn = new FileInputStream(inputFile);
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
        if (xzBuffer == null) {
            throw new IOException("Nothing extracted from file");
        }
        return xzBuffer;
    }

    private Logger logger;
    private File fromFile;
    private int actionId;
    private FinishedDecompressionReceiver receiver;

    public DecompressorAsyncTask(Logger logger, File fromFile, int actionId, FinishedDecompressionReceiver receiver) {
        this.logger = logger;
        this.fromFile = fromFile;
        this.actionId = actionId;
        this.receiver = receiver;
    }

    @Override
    protected void onPostExecute(byte[] bytes) {
        // handle possible empty return on success
        if (bytes == null) {
            logger.warning("Decompressing", fromFile, "returned no data");
            return;
        }
        // notify caller
        logger.info("Decompression succeeded for file", fromFile, "with length", bytes.length);
        receiver.decompressionFinished(fromFile, bytes, actionId);
    }

    @Override
    protected void onCancelled(byte[] bytes) {
        logger.warning("Decompression failed for file", fromFile);
    }

    @Override
    protected byte[] doInBackground(File... files) {
        if (files.length == 0) {
            logger.error("No input file provided");
            return null;
        }
        try {
            return extractXz(logger, files[0]);
        } catch (Exception e) {
            logger.error("Error while decompressing file", e.getMessage());
            this.cancel(true);
        }
        return null;
    }

    public void run() {
        this.execute(fromFile);
    }
}
