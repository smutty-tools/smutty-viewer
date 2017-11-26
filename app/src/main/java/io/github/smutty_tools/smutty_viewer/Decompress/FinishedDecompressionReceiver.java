package io.github.smutty_tools.smutty_viewer.Decompress;

import java.io.File;

public interface FinishedDecompressionReceiver {
    void decompressionFinished(File storedFile, byte[] content, int nextAction);
}
