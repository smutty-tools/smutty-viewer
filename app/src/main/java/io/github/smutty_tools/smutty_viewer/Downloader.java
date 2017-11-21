package io.github.smutty_tools.smutty_viewer;

import android.app.DownloadManager;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;
import android.util.LongSparseArray;

import java.io.File;
import java.math.BigInteger;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;


public class Downloader {

    interface ActionType {
        int NONE = 0;
    }

    public static final String TAG = "Downloader";

    public static final String DOWNLOADING_SUB_DIRECTORY = "downloading";

    public static final String HASH_ALGO = "MD5";

    public class Info {
        private Uri uri;
        private String targetSubDirectory;
        private int actionId;

        public Info(Uri uri, String targetSubDirectory, int actionId) {
            this.uri = uri;
            this.targetSubDirectory = targetSubDirectory;
            this.actionId = actionId;
        }
    }

    private LongSparseArray<Info> downloads = new LongSparseArray<>();

    private Context parentContext;
    private MessageDigest messageDigest;
    private DownloadManager downloadManager;
    private Toaster toaster;

    public Downloader(Context parent, Toaster toaster)
            throws NoSuchAlgorithmException {
        this.parentContext = parent;
        this.messageDigest = MessageDigest.getInstance(HASH_ALGO);
        this.downloadManager = (DownloadManager) parentContext.getSystemService(Context.DOWNLOAD_SERVICE);
        this.toaster = toaster;
    }

    private String getMd5(byte[] data) {
        messageDigest.reset();
        messageDigest.update(data, 0, data.length);
        byte[] digest = messageDigest.digest();
        return String.format("%032x", new BigInteger(1, digest));
    }

    private String getMd5(String data) {
        return getMd5(data.getBytes());
    }

    public void queue(String urlString, String subDirectory) {
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
        Info info = new Info(uri, subDirectory, ActionType.NONE);
        // prepare
        String targetName = getMd5(uri.toString());
        DownloadManager.Request request = new DownloadManager.Request(uri)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_HIDDEN)
                .setVisibleInDownloadsUi(false)
                .setDestinationInExternalFilesDir(parentContext, DOWNLOADING_SUB_DIRECTORY, targetName);
        // queue
        long resultId = downloadManager.enqueue(request);
        downloads.put(resultId, info);
        Log.d(TAG, "Downloading URI " + uri.toString() + " as " + targetName + " with id " + Long.toString(resultId));
    }

    private void moveToTarget(long downloadId) {
        // find our own info
        Info info = downloads.get(downloadId);
        if (info == null) {
            toaster.display("Unknown download, ignore and remove");
        } else {
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
                        File dst = new File(parentContext.getExternalFilesDir(info.targetSubDirectory), info.uri.getPath());
                        boolean r = dst.getParentFile().mkdirs();
                        Log.d(TAG, Boolean.toString(r));
                        if (!src.renameTo(dst)) {
                            Log.e(TAG, "Renaming temporary download " + src.toString() + " to final " + dst.toString() + " failed");
                            toaster.display("Moving file to destination failed");
                        } else {
                            toaster.display("Download succeeded");
                        }
                        break;
                }
            }
            // close database query
            cursor.close();
        }
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

    public void finalize(long downloadId) {
        // try to move it to destination
        moveToTarget(downloadId);
        // prune given download anyway
        downloadManager.remove(downloadId);
        // prune all other downloads
        cleanupDownloads();
    }
}
