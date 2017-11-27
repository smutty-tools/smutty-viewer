package io.github.smutty_tools.smutty_viewer.Decompress;

import android.util.Log;

import java.io.File;

import io.github.smutty_tools.smutty_viewer.Tools.Logger;

public class Decompressor {

    private static final String TAG = "Decompressor";

    private Logger logger;
    private FinishedDecompressionReceiver receiver;

    public Decompressor(Logger logger, FinishedDecompressionReceiver receiver) {
        this.logger = logger;
        this.receiver = receiver;
    }

    public void queue(File storedFile, int actionId) {
        Log.d(TAG, "Action " + Integer.toString(actionId) + " decompressing " + storedFile.toString());
        // queue decompression task
        new DecompressorAsyncTask(logger, storedFile, actionId, receiver).run();
    }
}
