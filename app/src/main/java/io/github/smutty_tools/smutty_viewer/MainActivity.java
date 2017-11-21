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
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;

import java.security.NoSuchAlgorithmException;

/**
 * Main activity class
 */
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    private Downloader downloader = null;
    private SharedPreferences settings = null;
    private Toaster toaster = null;

    BroadcastReceiver onDownloadComplete = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (downloader == null) {
                toaster.display("Downloader is unavailable");
                return;
            }
            long downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
            downloader.finalize(downloadId);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        // our toaster for messages
        toaster = new Toaster(this);
        // our download manager wrapper
        try {
            downloader = new Downloader(this, toaster);
        } catch (NoSuchAlgorithmException e) {
            toaster.display(e.getMessage());
            downloader = null;
        }
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
        // actually start download
        downloadAction(indexUrl);
    }

    public void downloadUrl(View view) {
        EditText editTextUrl = findViewById(R.id.edittextUrl);
        String url = editTextUrl.getText().toString();
        downloadAction(url);
    }

    public void downloadAction(String url) {
        if (downloader == null) {
            toaster.display("Downloader is unavailable");
            return;
        }
        // actually start download
        downloader.queue(url, "test_subdirectory");
    }
}
