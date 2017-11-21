package io.github.smutty_tools.smutty_viewer;

import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;
import android.util.LongSparseArray;

import java.math.BigInteger;
import java.security.InvalidParameterException;
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
        private int action_id;
        private long downloadId;

        public Info(Uri uri, String targetSubDirectory, int actionType) {
            this.uri = uri;
            this.targetSubDirectory = targetSubDirectory;
            this.action_id = actionType;
            this.downloadId = -1;
        }

        public void setDownloadId(long downloadId) {
            this.downloadId = downloadId;
        }
    }

    private LongSparseArray<Info> downloads = new LongSparseArray<>();

    private Context parentContext;
    private MessageDigest messageDigest;
    private DownloadManager downloadManager;

    public Downloader(Context parent)
            throws NoSuchAlgorithmException {
        this.parentContext = parent;
        this.messageDigest = MessageDigest.getInstance(HASH_ALGO );
        this.downloadManager = (DownloadManager) parentContext.getSystemService(Context.DOWNLOAD_SERVICE);
    }

    public String getMd5(byte[] data) {
        messageDigest.reset();
        messageDigest.update(data, 0, data.length);
        byte[] digest = messageDigest.digest();
        return String.format("%032x", new BigInteger(1, digest));
    }

    public String getMd5(String data) {
        return getMd5(data.getBytes());
    }

    public void queue(String urlString, String subDirectory)
            throws InvalidParameterException {
        // build download information
        urlString = urlString.trim();
        subDirectory = subDirectory.trim();
        if (urlString.startsWith("//")) {
            urlString = "https:" + urlString;
        }
        Uri uri = Uri.parse(urlString.trim());
        if (uri.getPath().length() == 0 || uri.getPath().endsWith("/")) {
            throw new InvalidParameterException("Url local path must include file name");
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
        info.setDownloadId(resultId);
        downloads.put(resultId, info);
        Log.d(TAG, "Downloading URI " + uri.toString() + " as " + targetName + " with id " + Long.toString(resultId));
    }

    public void finalize(long downloadId) {
        if (downloadId == -1) {
            Log.d(TAG, "Download ID not provided");
            return;
        }
        Log.d(TAG, Long.toString(downloadId));
        // find our own info
        Info info = downloads.get(downloadId);
        if (info == null) {
            Log.d(TAG, "Download absent from known download list, ignore and remove it from DownloadManager");
            downloadManager.remove(downloadId);
            return;
        }
        // look up download DownloadManager for more info
        DownloadManager.Query query = new DownloadManager.Query().setFilterById(downloadId);
        Cursor cursor = downloadManager.query(query);
        if (!cursor.moveToFirst()) {
            Log.d(TAG,"No download found for this ID in DownloadManager");
            return;
        }
        int column_local_uri = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI);
        String local_uri = cursor.getString(column_local_uri);
        if (local_uri != null) {
            Log.d(TAG, "Local URI: " + local_uri);
        } else {
            Log.d(TAG, "Local URI: null");
        }
        int column_status = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS);
        // analyze status
        switch(cursor.getInt(column_status)) {
            case DownloadManager.STATUS_FAILED:
                Log.d(TAG, "Download failed");
                break;
            case DownloadManager.STATUS_PAUSED:
                Log.d(TAG, "Download paused");
                break;
            case DownloadManager.STATUS_PENDING:
                Log.d(TAG, "Download pending");
                break;
            case DownloadManager.STATUS_RUNNING:
                Log.d(TAG, "Download running");
                break;
            case DownloadManager.STATUS_SUCCESSFUL:
                Log.d(TAG, "Download successful");
                break;
        }
        cursor.close();
        // prune download
        downloadManager.remove(downloadId);
        // list remaining downloads
        query = new DownloadManager.Query();
        cursor = downloadManager.query(query);
        int column_id = cursor.getColumnIndex(DownloadManager.COLUMN_ID);
        while (cursor.moveToNext()) {
            long cur_id = cursor.getLong(column_id);
            String cur_local_uri = cursor.getString(column_local_uri);
            Log.d(TAG, "Id " + Long.toString(cur_id) + " " + cur_local_uri);
        }
        cursor.close();
    }
}
