package io.github.smutty_tools.smutty_viewer;

import android.net.Uri;

public class DownloadInfo {
    private Uri uri;
    private String targetSubDirectory;
    private String relativeSubPath;
    private int actionId;
    private String storagePath;
    private boolean success;

    public DownloadInfo(Uri uri, String targetSubDirectory, String relativeSubPath, int actionId) {
        this.uri = uri;
        this.targetSubDirectory = targetSubDirectory;
        this.relativeSubPath = relativeSubPath;
        this.actionId = actionId;
        this.storagePath = null;
        this.success = false;
    }

    public String getRelativeSubPath() {
        return relativeSubPath;
    }

    public String getTargetSubDirectory() {
        return targetSubDirectory;
    }

    public void setStoragePath(String storagePath) {
        this.storagePath = storagePath;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }
}
