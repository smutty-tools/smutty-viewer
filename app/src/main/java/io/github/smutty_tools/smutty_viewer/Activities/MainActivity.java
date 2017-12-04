package io.github.smutty_tools.smutty_viewer.Activities;


import android.app.DownloadManager;
import android.arch.persistence.room.Room;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;

import io.github.smutty_tools.smutty_viewer.Data.AppDatabase;
import io.github.smutty_tools.smutty_viewer.Data.AppDatabase_Impl;
import io.github.smutty_tools.smutty_viewer.Data.IndexData;
import io.github.smutty_tools.smutty_viewer.Data.SmuttyPackage;
import io.github.smutty_tools.smutty_viewer.Data.SmuttyPackageDao;
import io.github.smutty_tools.smutty_viewer.Decompress.Decompressor;
import io.github.smutty_tools.smutty_viewer.Decompress.FinishedDecompressionReceiver;
import io.github.smutty_tools.smutty_viewer.Downloads.FinishedDownloadReceiver;
import io.github.smutty_tools.smutty_viewer.Downloads.Downloader;
import io.github.smutty_tools.smutty_viewer.R;
import io.github.smutty_tools.smutty_viewer.Tools.ContentStorage;
import io.github.smutty_tools.smutty_viewer.Tools.Logger;
import io.github.smutty_tools.smutty_viewer.Tools.UiLogger;

/**
 * Main activity class
 */
public class MainActivity extends AppCompatActivity implements FinishedDownloadReceiver, FinishedDecompressionReceiver, Logger {

    private static final String TAG = "MainActivity";

    interface StateMachineSteps {
        int DOWNLOAD_INDEX = 1;
        int DECOMPRESS_INDEX = 2;
    }

    private ContentStorage contentProvider = null;
    private UiLogger uiLogger = null;
    private Downloader downloader = null;
    private Decompressor decompressor = null;
    private SharedPreferences settings = null;
    private AppDatabase appDatabase = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        // initialize file storage
        contentProvider = new ContentStorage(Environment.getExternalStorageDirectory());
        // initialize db storage
        appDatabase = Room.databaseBuilder(getApplicationContext(), AppDatabase.class, "smutty_viewer").allowMainThreadQueries().build();
        // our UI uiLogger
        uiLogger = new UiLogger((TextView) findViewById(R.id.textViewLogContent));
        // our download manager wrapper
        downloader = new Downloader(contentProvider, (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE), this, this);
        // our decompressor
        decompressor = new Decompressor(this, this);
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
            displayToast(getString(R.string.toast_network_unavailable));
            return;
        }
        // check wifi restrictions
        boolean sync_only_on_wifi = settings.getBoolean("sync_only_on_wifi", false);
        if (sync_only_on_wifi && ni.getType() != ConnectivityManager.TYPE_WIFI) {
            displayToast("Synchronization allowed only on wifi");
            return;
        }
        // download index file
        String indexUrl = settings.getString("sync_url", null);
        if (indexUrl == null || indexUrl.length() == 0) {
            displayToast("Sync URL not provided");
            return;
        }
        // starting refresh
        this.info("Start refresh action");
        // start index download
        downloadIndex(indexUrl);
    }

    public void downloadIndex(String indexUrl) {
        this.info("Downloading index");
        downloader.queue(indexUrl, "index", StateMachineSteps.DOWNLOAD_INDEX);
    }

    @Override
    public void downloadFinished(String urlString, String subDirectory, File storedFile, int actionId) {
        // log progress
        this.info("Download", urlString, "finished successfully and stored in",
                storedFile.toString(), "and next requested action is", Integer.toString(actionId));
        File downloadedFile = downloader.getStoragePathFile(urlString, subDirectory);
        if (downloadedFile == null) {
            this.error("Invalid download URI on callback : ", urlString);
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
    public void decompressionFinished(File storedFile, byte[] content, int actionId) {
        this.info("Decompression of", storedFile, "finished successfully and content is",
                content.length, "bytes long, and next requested action is", actionId);
        // update state machine action
        switch(actionId) {
            case StateMachineSteps.DECOMPRESS_INDEX:
                JSONArray jsonArray = null;
                try {
                    jsonArray = new JSONArray(new String(content));
                    int nItems = jsonArray.length();
                    this.info(nItems, "data packages in index");
                    for (int i=0; i<nItems; i++) {
                        JSONObject jsonObject = jsonArray.getJSONObject(i);
                        this.info("IndexItem", jsonObject);
                        SmuttyPackage pkg = SmuttyPackage.fromJson(jsonObject);
                        SmuttyPackageDao pkgDao = appDatabase.smuttyPackageDao();
                        pkgDao.insert(pkg);
                        this.info("pkg inserted", pkg);
                    }
                } catch (JSONException e) {
                    this.error("Json error", e.getMessage());
                    return;
                }
                break;
        }
    }

    public void displayToast(String message) {
        int duration = Toast.LENGTH_SHORT;
        Toast toast = Toast.makeText(this, message, duration);
        toast.show();
    }

    interface LogLevel {
        int CRITICAL = 0;
        int ERROR = 1;
        int WARNING = 2;
        int INFO = 3;
        int DEBUG = 4;
    }

    private void log(int level, Object... objects) {
        String message = TextUtils.join(" ", objects);
        switch (level) {
            case LogLevel.CRITICAL:
                Log.e(TAG, message);
                uiLogger.critical(message);
                displayToast(message);
                break;
            case LogLevel.ERROR:
                Log.e(TAG, message);
                uiLogger.error(message);
                displayToast(message);
                break;
            case LogLevel.WARNING:
                Log.w(TAG, message);
                uiLogger.warning(message);
                break;
            case LogLevel.INFO:
                uiLogger.info(message);
                Log.i(TAG, message);
                break;
            case LogLevel.DEBUG:
//                uiLogger.debug(message);
                Log.d(TAG, message);
                break;
            default:
                throw new IllegalArgumentException("Logging level " + Integer.toString(level) + " is invalid");
        }
    }

    @Override
    public void debug(Object... objects) {
        log(LogLevel.DEBUG, objects);
    }

    @Override
    public void info(Object... objects) {
        log(LogLevel.INFO, objects);
    }

    @Override
    public void warning(Object... objects) {
        log(LogLevel.WARNING, objects);
    }

    @Override
    public void error(Object... objects) {
        log(LogLevel.ERROR, objects);
    }

    @Override
    public void critical(Object... objects) {
        log(LogLevel.CRITICAL, objects);
    }
}
