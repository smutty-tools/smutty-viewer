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

    private LongSparseArray<DownloadInfo> downloads = new LongSparseArray<>();

    private Context parentContext;
    private DownloadManager downloadManager;
    private Toaster toaster;

    public Downloader(Context parent, Toaster toaster) {
        this.parentContext = parent;
        this.downloadManager = (DownloadManager) parentContext.getSystemService(Context.DOWNLOAD_SERVICE);
        this.toaster = toaster;
    }

    public void queue(String urlString, String subDirectory, int actionId) {
        // build download information
        urlString = urlString.trim();
        subDirectory = subDirectory.trim();
        if (urlString.startsWith("//")) {
            urlString = "https:" + urlString;
        }
        Uri uri = Uri.parse(urlString.trim());
        if (uri.getPath().length() == 0 || uri.getPath().endsWith("/")) {
            toaster.display("Url local path must include file name");
            return;
        }
        // prepare final situation
        String relativeSubPath = new File(uri.getHost(), uri.getPath()).getPath();
        DownloadInfo info = new DownloadInfo(uri, subDirectory, relativeSubPath, actionId);
        // setup temporary situation
        String targetName = relativeSubPath + ".downloading";
        DownloadManager.Request request = new DownloadManager.Request(uri)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_HIDDEN)
                .setVisibleInDownloadsUi(false)
                .setDestinationInExternalFilesDir(parentContext, DOWNLOADING_SUB_DIRECTORY, targetName);
        // queue download
        long resultId = downloadManager.enqueue(request);
        if (downloads.get(resultId) == null) {
            downloads.put(resultId, info);
            toaster.display("Download started");
        } else {
            toaster.display("Download already running");
        }
        Log.d(TAG, "Downloading URI " + uri.toString() + " as " + targetName + " with id " + Long.toString(resultId));
    }

    private void moveToTarget(long downloadId, DownloadInfo info) {
        if (info == null) {
            return;
        }
        // look up download DownloadManager for more info
        DownloadManager.Query query = new DownloadManager.Query().setFilterById(downloadId);
        Cursor cursor = downloadManager.query(query);
        if (!cursor.moveToFirst()) {
            toaster.display("No download found for this ID in DownloadManager");
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
                    String local_uri = cursor.getString(cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI));
                    URI uri = null;
                    try {
                        uri = new URI(local_uri);
                    } catch (URISyntaxException e) {
                        toaster.display(e.getMessage());
                        break;
                    }
                    File src = new File(uri);
                    File dst = new File(parentContext.getExternalFilesDir(info.getTargetSubDirectory()), info.getRelativeSubPath());
                    dst.getParentFile().mkdirs();
                    dst.delete();
                    // store destination path for the caller
                    info.setStoragePath(dst.toString());
                    // notify the user
                    if (src.renameTo(dst)) {
                        info.setSuccess(true);
                        toaster.display("Download succeeded");
                        Log.d(TAG, "Target file " + dst.toString());
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
     * @return info struct (null if not found, otherwise with updated success/storagePath)
     */
    public DownloadInfo finalize(long downloadId) {
        // find our own info
        DownloadInfo info = downloads.get(downloadId);
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
