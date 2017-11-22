package io.github.smutty_tools.smutty_viewer;

import android.net.Uri;

public class DownloadInfo {
    private Uri uri;
    private String targetSubDirectory;
    private String relativeSubPath;
    private int actionId;

    public DownloadInfo(Uri uri, String targetSubDirectory, String relativeSubPath, int actionId) {
        this.uri = uri;
        this.targetSubDirectory = targetSubDirectory;
        this.relativeSubPath = relativeSubPath;
        this.actionId = actionId;
    }

    public String getRelativeSubPath() {
        return relativeSubPath;
    }

    public String getTargetSubDirectory() {
        return targetSubDirectory;
    }
}
