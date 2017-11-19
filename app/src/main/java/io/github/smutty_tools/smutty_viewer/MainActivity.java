package io.github.smutty_tools.smutty_viewer;

import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

/**
 * Main activity class
 */
public class MainActivity extends AppCompatActivity implements DownloadCallbackInterface<String> {

    private static final String TAG = "MainActivity";

    // Keep a reference to the NetworkFragment, which owns the AsyncTask object
    // that is used to execute network ops.
    private NetworkFragment mNetworkFragment;

    // Boolean telling us whether a download is in progress, so we don't trigger overlapping
    // downloads with consecutive button clicks.
    private boolean mDownloading = false;

    // Class handling terminated downloads
    private BroadcastReceiver onComplete = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "onReceive");
            long referenceId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
            Log.d(TAG, Long.toString(referenceId));
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        // load fragment for AsyncTask-based download
        mNetworkFragment = NetworkFragment.getInstance(getSupportFragmentManager(), "https://ip.appspot.com/");
        // callback for DownloadManager finished downloads
        registerReceiver(onComplete, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(onComplete);
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

    @Override
    public NetworkInfo getActiveNetworkInfo() {
        ConnectivityManager connectivityManager =
                (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();
        if (networkInfo == null) {
            Log.d(TAG, "No network available");
        } else {
            Log.d(TAG, networkInfo.toString());
        }
        return networkInfo;
    }

    private void startDownload() {
        if (mNetworkFragment == null) {
            showToastMessage("No network fragment available");
            return;
        }
        if (mDownloading) {
            showToastMessage("A synchronization alread in progress");
            return;
        }
        // mark synchronization as in progress
        mDownloading = true;
        showToastMessage("Synchronization started");
        // start the download task
        mNetworkFragment.startDownload();
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
        if (ni == null || !ni.isConnected()) {
            showToastMessage(getString(R.string.toast_network_unavailable));
            return;
        }
        startDownload();
        // TODO: fetch metadata
        // TODO: fetch latest indexes
        // TODO: fetch content
        // TODO: ...
    }

    @Override
    public void updateFromDownload(String result) {
        Log.i(TAG, result);
    }

    @Override
    public void onProgressUpdate(int progressCode, int percentComplete) {
        Log.d(TAG, Integer.toString(progressCode));
        Log.d(TAG, Integer.toString(percentComplete));
        switch(progressCode) {
            // You can add UI behavior for progress updates here.
            case Progress.ERROR:
                break;
            case Progress.CONNECT_SUCCESS:
                break;
            case Progress.GET_INPUT_STREAM_SUCCESS:
                break;
            case Progress.PROCESS_INPUT_STREAM_IN_PROGRESS:
                break;
            case Progress.PROCESS_INPUT_STREAM_SUCCESS:
                break;
        }
    }

    @Override
    public void finishDownloading() {
        Log.v(TAG, "finishDownloading");
        mDownloading = false;
        if (mNetworkFragment != null) {
            mNetworkFragment.cancelDownload();
        }
    }

    public void downloadUrl(View view) {
        DownloadManager downloadManager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
        Log.d(TAG, "downloadURL");
        Log.d(TAG, Environment.DIRECTORY_DOWNLOADS);
        EditText editTextUrl = findViewById(R.id.edittextUrl);
        String url = editTextUrl.getText().toString();
        if (url.length() == 0) {
            showToastMessage("Empty url");
            return;
        }
        Uri uri = Uri.parse(url);
        DownloadManager.Request request = new DownloadManager.Request(uri)
                .setTitle("test url title")
                .setDescription("test url descr" + url)
                // TODO: setAllowedOverMetered based on wifi setting, defaults to true
                .setVisibleInDownloadsUi(true)
                // getExternalFilesDir("toto") = /storage/emulated/0/Android/data/io.github.smutty_tools.smutty_viewer/files/toto
                // getExternalCacheDir() = /storage/emulated/0/Android/data/io.github.smutty_tools.smutty_viewer/cache
                // Environment.getExternalStoragePublicDirectory("toto") = /storage/emulated/0/toto
                // getApplicationInfo().dataDir = /data/data/io.github.smutty_tools.smutty_viewer
                // getFilesDir() = /data/data/io.github.smutty_tools.smutty_viewer/files
                .setDestinationInExternalFilesDir(this, "toto", "titi");

        long ref_id = downloadManager.enqueue(request);
        Log.d(TAG, Long.toString(ref_id));
    }
}







/*
The sequence of events in the code so far is as follows:
The Activity starts a NetworkFragment and passes in a specified URL.
When a user action triggers the Activity's downloadData() method, the NetworkFragment executes the DownloadTask.
The AsyncTask method onPreExecute() runs first (on the UI thread) and cancels the task if the device is not connected to the Internet.
The AsyncTask method doInBackground() then runs on the background thread and calls the downloadUrl() method.
The downloadUrl() method takes a URL string as a parameter and uses an HttpsURLConnection object to fetch the web content as an InputStream.
The InputStream is passed to the readStream() method, which converts the stream to a string.
Finally, once the background work is complete, the AsyncTask's onPostExecute() method runs on the UI thread and uses the DownloadCallback to send the result back to the UI as a String.
*/
