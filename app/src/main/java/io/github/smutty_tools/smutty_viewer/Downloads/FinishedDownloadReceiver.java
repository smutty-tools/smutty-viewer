package io.github.smutty_tools.smutty_viewer.Downloads;

import java.io.File;

public interface FinishedDownloadReceiver {
    void downloadFinished(String urlString, String subDirectory, File storedFile, int actionId);
}
