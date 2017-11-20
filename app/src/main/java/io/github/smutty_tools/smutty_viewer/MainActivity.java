package io.github.smutty_tools.smutty_viewer;


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
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import java.security.InvalidParameterException;
import java.security.NoSuchAlgorithmException;

/**
 * Main activity class
 */
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    private Downloader downloader = null;
    private SharedPreferences settings = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        // our download manager wrapper
        try {
            downloader = new Downloader(this);
        } catch (NoSuchAlgorithmException e) {
            showToastMessage(e.getMessage());
            downloader = null;
        }
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

    public void showToastMessage(String message) {
        Context context = getApplicationContext();
        CharSequence text = message;
        int duration = Toast.LENGTH_SHORT;
        Toast toast = Toast.makeText(context, text, duration);
        toast.show();
    }

    private void refreshAction() {
        NetworkInfo ni = getActiveNetworkInfo();
        // check connectivity
        if (ni == null || !ni.isConnected()) {
            showToastMessage(getString(R.string.toast_network_unavailable));
            return;
        }
        // check wifi restrictions
        boolean sync_only_on_wifi = settings.getBoolean("sync_only_on_wifi", false);
        if (sync_only_on_wifi && ni.getType() != ConnectivityManager.TYPE_WIFI) {
            showToastMessage("Synchronization allowed only on wifi");
            return;
        }
        // download index file
        String indexUrl = settings.getString("sync_url", null);
        if (indexUrl == null || indexUrl.length() == 0) {
            showToastMessage("Sync URL not provided");
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
            showToastMessage("Downloader is unavailable");
            return;
        }
        // actually start download
        try {
            downloader.queue(url, "test_subdirectory");
        } catch (InvalidParameterException e) {
            showToastMessage(e.getMessage());
        }
    }
}
