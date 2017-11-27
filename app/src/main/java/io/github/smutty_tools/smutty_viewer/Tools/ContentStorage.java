package io.github.smutty_tools.smutty_viewer.Tools;

import android.util.Log;

import java.io.File;

public class ContentStorage {

    public static final String TAG = "ContentStorage";

    public static final String BASE_SUB_DIRECTORY = "smutty-viewer";

    private File baseDirectory;

    public ContentStorage(File baseDirectory) {
        this.baseDirectory = new File(baseDirectory, BASE_SUB_DIRECTORY);
        Log.d(TAG, "Content storage base " + baseDirectory.toString());
    }

    public File getStorageDirectory(String subDirectory) {
        File dir = new File(baseDirectory, subDirectory);
        Log.d(TAG, "Content storage directory " + dir.toString());
        return dir;
    }

    public File getStorageFile(String subDirectory, String subPath) {
        File file = new File(getStorageDirectory(subDirectory), subPath);
        Log.d(TAG, "Content storage file " + file.toString());
        return file;
    }
}
