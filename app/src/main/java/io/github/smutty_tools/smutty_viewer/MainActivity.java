package io.github.smutty_tools.smutty_viewer;


import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.TextView;

import org.tukaani.xz.XZInputStream;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

/**
 * Main activity class
 */
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    interface DownloadAction {
        int INDEX = 1;
    }

    private String[] downloadFolders = {
            "misc",
            "index"
    };

    private UiLogger uiLogger = null;
    private Downloader downloader = null;
    private SharedPreferences settings = null;
    private Toaster toaster = null;

    BroadcastReceiver onDownloadComplete = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            long downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
            Downloader.Info info = downloader.finalize(downloadId);
            if (info == null) {
                Log.w(TAG, "Download " + Long.toString(downloadId)+ " not found in hashMap");
                return;
            }
            if (!info.isSuccess()) {
                Log.i(TAG, "Download failed");
                return;
            }
            String downloadUri = info.getUri().toString();
            File downloadedFile = downloader.getStoragePathFile(downloadUri, info.getTargetSubDirectory());
            if (downloadedFile == null) {
                toaster.display("Invalid download URI on callback : " + downloadUri);
                return;
            }
            Log.i(TAG, "Download succeeded: " + downloadedFile.toString());
            Log.i(TAG, "Action id : " + info.getActionId());
            if (info.getActionId() == DownloadAction.INDEX) {
                byte[] xzBuffer = null;
                // decompress index file
                try {
                    FileInputStream fileIn = new FileInputStream(downloadedFile);
                    XZInputStream xzIn = new XZInputStream(fileIn);
                    // trigger reading the first block
                    int result = xzIn.read();
                    if (result == -1) {
                        throw new IOException("Error while reading first byte of compressed data");
                    }
                    Log.d(TAG, "First byte read is " + Integer.toString(result));
                    // setup buffer and handle already read byte
                    int uncompressedSize = xzIn.available() + 1;
                    Log.d(TAG, "UncompressedSize data length " + Integer.toString(uncompressedSize));
                    xzBuffer = new byte[uncompressedSize ];
                    xzBuffer[0] = (byte) result;
                    // read the rest of the file
                    result = xzIn.read(xzBuffer, 1, uncompressedSize - 1);
                    uiLogger.info("Extracted " + Integer.toString(uncompressedSize) + " bytes of data from compressed input");
                    if (result != uncompressedSize - 1) {
                        throw new IOException("Could not extract all of compressed data");
                    }
                    // ensure that final
                    result = xzIn.read();
                    if (result != -1) {
                        throw new IOException("Remaining data found after end of decompression");
                    }
                    // clean up
                    xzIn.close();
                    fileIn.close();
                } catch (IOException e) {
                    uiLogger.error(e.getMessage());
                    return;
                }
                if (xzBuffer == null) {
                    uiLogger.error("Nothing extracted from file");
                    return;
                }
                String content = new String(xzBuffer);
//                Log.d(TAG, content);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        // our UI uiLogger
        uiLogger = new UiLogger((TextView) findViewById(R.id.textViewLogContent));
        // our toaster for messages
        toaster = new Toaster(this);
        // our download manager wrapper
        downloader = new Downloader(this, toaster, uiLogger);
        // accesses settings
        settings = PreferenceManager.getDefaultSharedPreferences(this);
        // callback for finished downloads
        registerReceiver(onDownloadComplete, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));

    }

    @Override
    protected void onDestroy() {
        // callback for finished downloads
        unregisterReceiver(onDownloadComplete);
        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.options_menu, menu);
        return true; // display the menu
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
            case R.id.action_share:
                return true; // consume the event
            case R.id.action_refresh:
                refreshAction();
                return true; // consume the event
            case R.id.action_settings:
                Intent intent = new Intent(this, SettingsActivity.class);
                startActivity(intent);
                return true; // consume the event
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    public NetworkInfo getActiveNetworkInfo() {
        ConnectivityManager connectivityManager = (ConnectivityManager)
                getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager == null) {
            return null;
        }
        return connectivityManager.getActiveNetworkInfo();
    }

    private void refreshAction() {
        NetworkInfo ni = getActiveNetworkInfo();
        // check connectivity
        if (ni == null || !ni.isConnected()) {
            toaster.display(getString(R.string.toast_network_unavailable));
            return;
        }
        // check wifi restrictions
        boolean sync_only_on_wifi = settings.getBoolean("sync_only_on_wifi", false);
        if (sync_only_on_wifi && ni.getType() != ConnectivityManager.TYPE_WIFI) {
            toaster.display("Synchronization allowed only on wifi");
            return;
        }
        // download index file
        String indexUrl = settings.getString("sync_url", null);
        if (indexUrl == null || indexUrl.length() == 0) {
            toaster.display("Sync URL not provided");
            return;
        }
        // starting refresh
        uiLogger.info("Start refresh action");
        // start index download
        downloadIndex(indexUrl);
    }

    public void downloadIndex(String indexUrl) {
        uiLogger.info("Downloading index");
        downloader.queue(indexUrl, downloadFolders[DownloadAction.INDEX], DownloadAction.INDEX);
    }
}
