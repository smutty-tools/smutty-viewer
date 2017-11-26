package io.github.smutty_tools.smutty_viewer.Activities;


import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.TextView;

import java.io.File;

import io.github.smutty_tools.smutty_viewer.Decompress.Decompressor;
import io.github.smutty_tools.smutty_viewer.Decompress.FinishedDecompressionReceiver;
import io.github.smutty_tools.smutty_viewer.Downloads.FinishedDownloadReceiver;
import io.github.smutty_tools.smutty_viewer.Downloads.Downloader;
import io.github.smutty_tools.smutty_viewer.R;
import io.github.smutty_tools.smutty_viewer.Tools.Toaster;
import io.github.smutty_tools.smutty_viewer.Tools.UiLogger;

/**
 * Main activity class
 */
public class MainActivity extends AppCompatActivity implements FinishedDownloadReceiver, FinishedDecompressionReceiver {

    private static final String TAG = "MainActivity";

    interface StateMachineSteps {
        int DOWNLOAD_INDEX = 1;
        int DECOMPRESS_INDEX = 2;
    }

    private UiLogger uiLogger = null;
    private Downloader downloader = null;
    private Decompressor decompressor = null;
    private SharedPreferences settings = null;
    private Toaster toaster = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        // our UI uiLogger
        uiLogger = new UiLogger((TextView) findViewById(R.id.textViewLogContent));
        // our toaster for messages
        toaster = new Toaster(this);
        // our download manager wrapper
        downloader = new Downloader(this, toaster, uiLogger, this);
        // our decompressor
        decompressor = new Decompressor(toaster, uiLogger, this);
        // accesses settings
        settings = PreferenceManager.getDefaultSharedPreferences(this);
        // callback for finished downloads
        registerReceiver(downloader, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
    }

    @Override
    protected void onDestroy() {
        // callback for finished downloads
        unregisterReceiver(downloader);
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
        downloader.queue(indexUrl, "index", StateMachineSteps.DOWNLOAD_INDEX);
    }

    @Override
    public void downloadFinished(String urlString, String subDirectory, File storedFile, int actionId) {
        // log progress
        uiLogger.info("Download " + urlString +
                " finished successfully and stored in " + storedFile.toString() +
                " and next requested action is " + Integer.toString(actionId));
        File downloadedFile = downloader.getStoragePathFile(urlString, subDirectory);
        if (downloadedFile == null) {
            toaster.display("Invalid download URI on callback : " + urlString);
            return;
        }
        // update state machine action
        switch(actionId) {
            case StateMachineSteps.DOWNLOAD_INDEX:
                // queue decompression task
                decompressor.queue(downloadedFile, StateMachineSteps.DECOMPRESS_INDEX);
                break;
        }
    }

    @Override
    public void decompressionFinished(File storedFile, byte[] content, int nextAction) {
        uiLogger.info("Decompression of " + storedFile +
                " finished successfully and content is " + content.length +
                " bytes long, and next requested action is " + Integer.toString(nextAction));
    }
}
