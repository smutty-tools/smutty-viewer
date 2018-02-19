package io.github.smutty_tools.smutty_viewer.Activities;


import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.net.MalformedURLException;

import io.github.smutty_tools.smutty_viewer.AsyncTasks.RefreshTask;
import io.github.smutty_tools.smutty_viewer.Exceptions.SmuttyException;
import io.github.smutty_tools.smutty_viewer.R;

/**
 * Main activity class
 */
public class MainActivity extends AppCompatActivity implements RefreshTask.FinishedNotifier {

    public static class WidgetCache {
        final ProgressBar progressBar;
        final TextView textView;

        WidgetCache(MainActivity activity) {
            progressBar = (ProgressBar) activity.findViewById(R.id.progressBar);
            textView = (TextView) activity.findViewById(R.id.textViewLogContent);
        }
    }

    private static final String TAG = "MainActivity";
    private static final String DIRECTORY_MAIN = "smutty-viewer";
    private static final String SUB_DIRECTORY_INDEX = "indexes";

    private SharedPreferences settings = null;
    private RefreshTask refreshTask = null;
    private File storageDirectory = null;
    private WidgetCache widgetCache = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        settings = PreferenceManager.getDefaultSharedPreferences(this);
        storageDirectory = new File(Environment.getExternalStorageDirectory(), DIRECTORY_MAIN);
        widgetCache = new WidgetCache(this);
    }

    @Override
    protected void onDestroy() {
        // cancel AsyncTasks
        if (refreshTask != null) {
            Log.i(TAG, "Canceling refresh task");
            refreshTask.cancel(true);
            refreshTask = null;
        }
        super.onDestroy();
    }

    private void startRefreshAsyncTask(String indexUrl) {
        // skip if already running
        if (refreshTask != null) {
            displayToast("Sync already running");
            return;
        }
        try {
            refreshTask = new RefreshTask(widgetCache.progressBar, this, new File(storageDirectory, SUB_DIRECTORY_INDEX), indexUrl);
        }
        catch (SmuttyException e) {
            Log.e(TAG, e.getMessage());
            displayToast("Error while refreshing");
        }
        refreshTask.execute();
    }

    @Override
    public void refreshTaskFinished(RefreshTask task) {
        if (refreshTask != null && !refreshTask.equals(task)) {
            Log.w(TAG, "Task finished" + task + "different from task lanched" + refreshTask);
        }
        refreshTask = null;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.options_menu, menu);
        return true; // display the menu
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_share:
                return true; // consume the event
            case R.id.action_refresh:
                refreshAction();
                return true; // consume the event
            case R.id.action_view:
                viewAction();
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
        ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
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
        // verify storage
        if (!Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
            displayToast("External storage not available for writing");
            return;
        }
        // setup task
        startRefreshAsyncTask(indexUrl);
    }

    private void viewAction() {
        Log.d(TAG, "viewAction");
    }

    public void displayToast(String message) {
        int duration = Toast.LENGTH_SHORT;
        Toast toast = Toast.makeText(this, message, duration);
        toast.show();
    }
}
