package io.github.smutty_tools.smutty_viewer;

import android.app.DownloadManager;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;
import android.util.LongSparseArray;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;


public class Downloader {

    public static final String TAG = "Downloader";

    public static final String DOWNLOADING_SUB_DIRECTORY = "downloading";

    public class Info {
        private Uri uri;
        private String targetSubDirectory;
        private int actionId;
        private boolean success;

        public Info(Uri uri, String targetSubDirectory, int actionId) {
            this.uri = uri;
            this.targetSubDirectory = targetSubDirectory;
            this.actionId = actionId;
            // defaults
            this.success = false;
        }

        public boolean isSuccess() {
            return success;
        }

        public int getActionId() {
            return actionId;
        }

        public Uri getUri() {
            return uri;
        }

        public String getTargetSubDirectory() {
            return targetSubDirectory;
        }
    }

    private LongSparseArray<Info> downloads = new LongSparseArray<>();

    private Context parentContext;
    private DownloadManager downloadManager;
    private Toaster toaster;

    public Downloader(Context parent, Toaster toaster) {
        this.parentContext = parent;
        this.downloadManager = (DownloadManager) parentContext.getSystemService(Context.DOWNLOAD_SERVICE);
        this.toaster = toaster;
    }

    public Uri getUri(String urlString) {
        URI validUri = null;
        try {
            validUri  = new URI(urlString.trim());
        } catch (URISyntaxException e) {
            return null;
        }
        Uri uri = Uri.parse(validUri.toString());
        return uri;
    }

    public File getStoragePathFile(String urlString, String subDirectory) {
        Uri uri = getUri(urlString);
        if (uri == null) {
            return null;
        }
        String relativeSubPath = new File(uri.getHost(), uri.getPath()).getPath();
        return new File(parentContext.getExternalFilesDir(subDirectory), relativeSubPath);
    }

    public void queue(String urlString, String subDirectory, int actionId) {
        // build download information
        subDirectory = subDirectory.trim();
        if (subDirectory.length() == 0) {
            toaster.display("Subdirectory cannot be empty");
            return;
        }
        Uri uri = getUri(urlString);
        if (uri == null) {
            toaster.display("Invalid remote URI " + urlString);
            return;
        }
        if (uri.getPath().length() == 0 || uri.getPath().endsWith("/")) {
            toaster.display("Url local path must include file name, in " + urlString);
            return;
        }
        // prepare final situation
        String downloadName = new File(uri.getHost(), uri.getPath()).toString();
        // queue download
        DownloadManager.Request request = new DownloadManager.Request(uri)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_HIDDEN)
                .setVisibleInDownloadsUi(false)
                .setDestinationInExternalFilesDir(parentContext, DOWNLOADING_SUB_DIRECTORY, downloadName);
        long resultId = downloadManager.enqueue(request);
        // setup for callback
        Log.d(TAG, "Download " + Long.toString(resultId) + " from Uri " + uri.toString() + " as " + downloadName.toString() + " in " + subDirectory);
        if (downloads.get(resultId) == null) {
            downloads.put(resultId, new Info(uri, subDirectory, actionId));
            toaster.display("Download started");
            Log.d(TAG, "download queued as " + Long.toString(resultId));
        } else {
            toaster.display("Download already running");
            Log.d(TAG, "download already running as " + Long.toString(resultId));
        }
    }

    private void moveToTarget(long downloadId, Info info) {
        if (info == null) {
            Log.d(TAG, "provided info is null");
            return;
        }
        // look up download DownloadManager for more info
        DownloadManager.Query query = new DownloadManager.Query().setFilterById(downloadId);
        Cursor cursor = downloadManager.query(query);
        if (!cursor.moveToFirst()) {
            String message = "No download found for this ID in DownloadManager";
            toaster.display(message);
            Log.d(TAG, message);
        } else {
            int status = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS));
            switch(status) {
                case DownloadManager.STATUS_FAILED:
                    toaster.display("Download failed");
                    break;
                case DownloadManager.STATUS_PAUSED:
                    toaster.display("Download paused");
                    break;
                case DownloadManager.STATUS_PENDING:
                    toaster.display("Download pending");
                    break;
                case DownloadManager.STATUS_RUNNING:
                    toaster.display("Download running");
                    break;
                case DownloadManager.STATUS_SUCCESSFUL:
                    // get uri of temporary downloaded path from DownloadManager
                    String localSourceString = cursor.getString(cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI));
                    Log.d(TAG, "Source URI String: " + localSourceString);
                    Uri localSourceUri = getUri(localSourceString);
                    if (localSourceUri == null) {
                        Log.e(TAG, "Invalid local source " + localSourceString + " from DownloadManager");
                        break;
                    }
                    Log.d(TAG, "Source URI Uri: " + localSourceUri.toString());
                    File src = new File(localSourceUri.getPath());
                    Log.d(TAG, "Source path: " + src.toString());
                    // build uri of final downloaded path
                    String storageUri = info.getUri().toString();
                    File dst = getStoragePathFile(storageUri, info.getTargetSubDirectory());
                    if (dst == null) {
                        Log.e(TAG, "Invalid URI " + storageUri);
                        break;
                    }
                    Log.d(TAG, "Destination file: " + dst.toString());
                    // ensure that target directory exists
                    File parent = dst.getParentFile();
                    Log.d(TAG, "Destination path: " + parent.toString());
                    boolean result;
                    result = parent.mkdirs();
                    Log.d(TAG, "Creating parents returned " + result);
                    // remove possibly existing target download
                    result = dst.delete();
                    Log.d(TAG, "Deleting target returned " + result);
                    // notify the user
                    if (src.renameTo(dst)) {
                        info.success = true;
                        toaster.display("Download succeeded");
                        Log.d(TAG, "Target of " + Long.toString(downloadId) + " is " + dst.toString());
                    } else {
                        toaster.display("Renaming download failed");
                    }
                    break;
            }
        }
        // close database query
        cursor.close();
    }

    private void cleanupDownloads() {
        DownloadManager.Query query = new DownloadManager.Query();
        Cursor cursor = downloadManager.query(query);
        int n = 0;
        while (cursor.moveToNext()) {
            long downloadId = cursor.getLong(cursor.getColumnIndex(DownloadManager.COLUMN_ID));
            downloadManager.remove(downloadId);
            n++;
        }
        cursor.close();
        if (n != 0) {
            toaster.display(n + " downloads cleaned up");
        }
    }

    /**
     * transits from temporary download to final destination storage
     * @param downloadId provided by Intent on ACTION_DOWNLOAD_COMPLETE
     * @return null if info not found in downloads
     *         otherwise updated info with updated success
     *         if success is false, storagePath is null
     */
    public Info finalize(long downloadId) {
        // find our own info
        Info info = downloads.get(downloadId);
        // remove internal association
        downloads.delete(downloadId);
        // ignore unknown downloads
        if (info == null) {
            toaster.display("Unknown download, ignore and remove");
        } else {
            moveToTarget(downloadId, info);
        }
        // prune given download anyway
        downloadManager.remove(downloadId);
        // prune all other downloads
        cleanupDownloads();
        // return updated information to caller
        return info;
    }
}
