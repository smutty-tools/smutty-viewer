package io.github.smutty_tools.smutty_viewer.Downloads;

import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;
import android.util.LongSparseArray;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;

import io.github.smutty_tools.smutty_viewer.Tools.ContentStorage;
import io.github.smutty_tools.smutty_viewer.Tools.Logger;


public class Downloader extends BroadcastReceiver {

    private static final String TAG = "Downloader";

    private static final String DOWNLOADING_SUB_DIRECTORY = "downloading";

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

    private ContentStorage contentStorage;
    private DownloadManager downloadManager;
    private Logger logger;
    private FinishedDownloadReceiver receiver;

    public Downloader(ContentStorage contentStorage, DownloadManager downloadManager, Logger logger, FinishedDownloadReceiver receiver) {
        this.contentStorage = contentStorage;
        this.downloadManager = downloadManager;
        this.logger = logger;
        this.receiver = receiver;
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
        return contentStorage.getStorageFile(subDirectory, relativeSubPath);
    }

    public void queue(String urlString, String subDirectory, int actionId) {
        // build download information
        subDirectory = subDirectory.trim();
        if (subDirectory.length() == 0) {
            logger.error("Subdirectory cannot be empty");
            return;
        }
        Uri uri = getUri(urlString);
        if (uri == null) {
            logger.error("Invalid remote URI", urlString);
            return;
        }
        if (uri.getPath().length() == 0 || uri.getPath().endsWith("/")) {
            logger.error("Url local path must include file name, in", urlString);
            return;
        }
        // prepare temporary situation
        String downloadName = new File(uri.getHost(), uri.getPath()).toString();
        File downloadFile = contentStorage.getStorageFile(DOWNLOADING_SUB_DIRECTORY, downloadName);
        Log.d(TAG, downloadFile.toString());
        Uri downloadStorageUri = Uri.fromFile(downloadFile);
        // queue download
        DownloadManager.Request request = new DownloadManager.Request(uri)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_HIDDEN)
                .setVisibleInDownloadsUi(false)
                .setDestinationUri(downloadStorageUri);
        long resultId = downloadManager.enqueue(request);
        // setup for callback
        if (downloads.get(resultId) == null) {
            downloads.put(resultId, new Info(uri, subDirectory, actionId));
            logger.info("Downloading id", resultId, "which fetches", uri.toString());
        } else {
            logger.warning("Download already running as id", resultId);
        }
    }

    private void moveToTarget(long downloadId, Info info) {
        if (info == null) {
            logger.critical("Provided download info is 'null'");
            return;
        }
        // look up download DownloadManager for more info
        DownloadManager.Query query = new DownloadManager.Query().setFilterById(downloadId);
        Cursor cursor = downloadManager.query(query);
        if (!cursor.moveToFirst()) {
            logger.error("No download found for id", downloadId, "in DownloadManager");
        } else {
            int status = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS));
            switch(status) {
                case DownloadManager.STATUS_FAILED:
                    logger.error("Download failed");
                    break;
                case DownloadManager.STATUS_PAUSED:
                    logger.warning("Download paused");
                    break;
                case DownloadManager.STATUS_PENDING:
                    logger.info("Download pending");
                    break;
                case DownloadManager.STATUS_RUNNING:
                    logger.info("Download running");
                    break;
                case DownloadManager.STATUS_SUCCESSFUL:
                    // get uri of temporary downloaded path from DownloadManager
                    String localSourceString = cursor.getString(cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI));
                    Log.d(TAG, "Source URI String: " + localSourceString);
                    Uri localSourceUri = getUri(localSourceString);
                    if (localSourceUri == null) {
                        logger.error("Invalid local source", localSourceString, "from DownloadManager");
                        break;
                    }
                    Log.d(TAG, "Source URI Uri: " + localSourceUri.toString());
                    File src = new File(localSourceUri.getPath());
                    Log.d(TAG, "Source path: " + src.toString());
                    // build uri of final downloaded path
                    String storageUri = info.getUri().toString();
                    File dst = getStoragePathFile(storageUri, info.getTargetSubDirectory());
                    if (dst == null) {
                        logger.error("Invalid storage URI", storageUri);
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
                        logger.info("Download id", downloadId, "stored at", dst.toString());
                    } else {
                        logger.info("Moving download id", downloadId, "failed");
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
            logger.info(n, "downloads cleaned up");
        }
    }

    /**
     * transits from temporary download to final destination storage
     * @param downloadId provided by Intent on ACTION_DOWNLOAD_COMPLETE
     * @return null if info not found in downloads
     *         otherwise updated info with updated success
     *         if success is false, storagePath is null
     */
    private Info finalize(long downloadId) {
        // find our own info
        Info info = downloads.get(downloadId);
        // remove internal association
        downloads.delete(downloadId);
        // ignore unknown downloads
        if (info == null) {
            logger.warning("Unknown download id", downloadId, ", ignore and remove");
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

    @Override
    public void onReceive(Context context, Intent intent) {
        long downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
        // get our stored data for this download
        Downloader.Info info = finalize(downloadId);
        if (info == null) {
            logger.warning("Download", downloadId, "not found in hashMap");
            return;
        }
        if (!info.isSuccess()) {
            logger.info("Download failed");
            return;
        }
        // build data for callback
        String downloadUri = info.getUri().toString();
        String subDirectory = info.getTargetSubDirectory();
        File downloadedFile = getStoragePathFile(downloadUri, subDirectory);
        if (downloadedFile == null) {
            logger.error("Invalid download URI on callback :", downloadUri);
            return;
        }
        // notify callback
        receiver.downloadFinished(downloadUri, subDirectory, downloadedFile, info.getActionId());
    }
}
