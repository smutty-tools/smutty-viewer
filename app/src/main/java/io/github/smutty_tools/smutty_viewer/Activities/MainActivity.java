package io.github.smutty_tools.smutty_viewer.Activities;


import android.arch.persistence.room.Room;
import android.content.Context;
import android.content.Intent;
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
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.security.InvalidParameterException;
import java.text.DateFormat;
import java.util.Date;

import io.github.smutty_tools.smutty_viewer.AsyncTasks.RefreshIndexTask;
import io.github.smutty_tools.smutty_viewer.Data.AppDatabase;
import io.github.smutty_tools.smutty_viewer.R;
import io.github.smutty_tools.smutty_viewer.Tools.Logger;

/**
 * Main activity class
 */
public class MainActivity extends AppCompatActivity implements Logger, RefreshIndexTask.FinishedNotifier {

    public static class WidgetCache {
        public final ProgressBar progressBar;
        public final TextView textView;

        public WidgetCache(MainActivity activity) {
            progressBar = (ProgressBar) activity.findViewById(R.id.progressBar);
            textView = (TextView) activity.findViewById(R.id.textViewLogContent);
        }

        public void updateProgress(int current, int maximum) {
            // progressBar.setMin(0); // API 26 minimum
            progressBar.setProgress(current);
            progressBar.setMax(maximum);
        }

        public void showProgress() {
            progressBar.setVisibility(View.VISIBLE);
        }

        public void hideProgress() {
            progressBar.setVisibility(View.GONE);
        }
    }

    private static final String TAG = "MainActivity";
    private static final String DIRECTORY_MAIN = "smutty-viewer";
    private static final String SUB_DIRECTORY_INDEX = "indexes";
    private static final String[] LEVELS = {
            "CRITICAL",
            "ERROR",
            "WARNING",
            "INFO",
            "DEBUG"
    };

    private SharedPreferences settings = null;
    private AppDatabase appDatabase = null;
    private RefreshIndexTask refreshIndexTask = null;
    private File storageDirectory = null;
    private WidgetCache widgetCache = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        appDatabase = Room.databaseBuilder(getApplicationContext(), AppDatabase.class, "smutty_viewer").build();
        settings = PreferenceManager.getDefaultSharedPreferences(this);
        storageDirectory = new File(Environment.getExternalStorageDirectory(), DIRECTORY_MAIN);
        widgetCache = new WidgetCache(this);
    }

    @Override
    protected void onDestroy() {
        // cancel AsyncTasks
        if (refreshIndexTask != null) {
            info("Canceling refresh index task");
            refreshIndexTask.cancel(true);
            refreshIndexTask = null;
        }
        super.onDestroy();
    }

    private void startRefreshAsyncTask(String indexUrl) {
        // skip if already running
        if (refreshIndexTask != null) {
            displayToast("Sync index already running");
            return;
        }
        refreshIndexTask = new RefreshIndexTask(this, widgetCache.progressBar, this, appDatabase, new File(storageDirectory, SUB_DIRECTORY_INDEX));
        refreshIndexTask.execute(indexUrl);
    }

    @Override
    public void taskFinished(RefreshIndexTask task) {
        if (!refreshIndexTask.equals(task)) {
            warning("Task finished", task, "different from task lanched", refreshIndexTask);
        }
        refreshIndexTask = null;
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

    public void displayToast(String message) {
        int duration = Toast.LENGTH_SHORT;
        Toast toast = Toast.makeText(this, message, duration);
        toast.show();
    }

    public void textViewLog(int level, String message) {
        if (level < Logger.Level.CRITICAL || level > Logger.Level.DEBUG) {
            throw new InvalidParameterException("Log level is outside allowed range");
        }
        StringBuilder stringBuilder = new StringBuilder();
        DateFormat dateFormat = DateFormat.getTimeInstance(DateFormat.MEDIUM);
        Date now = new Date();
        stringBuilder.setLength(0);
        stringBuilder.append(dateFormat.format(now));
        stringBuilder.append(" ");
        stringBuilder.append(LEVELS[level]);
        stringBuilder.append(" ");
        stringBuilder.append(message);
        stringBuilder.append("\n");
        widgetCache.textView.append(stringBuilder.toString());
    }

    @Override
    public void log(int level, Object... objects) {
        String message = TextUtils.join(" ", objects);
        switch (level) {
            case Logger.Level.CRITICAL:
                Log.e(TAG, message);
                textViewLog(level, message);
                displayToast(message);
                break;
            case Logger.Level.ERROR:
                Log.e(TAG, message);
                textViewLog(level, message);
                displayToast(message);
                break;
            case Logger.Level.WARNING:
                Log.w(TAG, message);
                textViewLog(level, message);
                break;
            case Logger.Level.INFO:
                textViewLog(level, message);
                Log.i(TAG, message);
                break;
            case Logger.Level.DEBUG:
                textViewLog(level, message);
                Log.d(TAG, message);
                break;
            default:
                throw new IllegalArgumentException("Logging level " + Integer.toString(level) + " is invalid");
        }
    }

    @Override
    public void debug(Object... objects) {
        log(Logger.Level.DEBUG, objects);
    }

    @Override
    public void info(Object... objects) {
        log(Logger.Level.INFO, objects);
    }

    @Override
    public void warning(Object... objects) {
        log(Logger.Level.WARNING, objects);
    }

    @Override
    public void error(Object... objects) {
        log(Logger.Level.ERROR, objects);
    }

    @Override
    public void critical(Object... objects) {
        log(Logger.Level.CRITICAL, objects);
    }
}
