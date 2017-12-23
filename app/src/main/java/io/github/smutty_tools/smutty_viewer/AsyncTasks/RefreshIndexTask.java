package io.github.smutty_tools.smutty_viewer.AsyncTasks;

import android.os.AsyncTask;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;

import io.github.smutty_tools.smutty_viewer.Data.AppDatabase;
import io.github.smutty_tools.smutty_viewer.Data.SmuttyPackage;
import io.github.smutty_tools.smutty_viewer.Data.SmuttyPackageDao;
import io.github.smutty_tools.smutty_viewer.Decompress.Decompressor;
import io.github.smutty_tools.smutty_viewer.Tools.LogEntry;
import io.github.smutty_tools.smutty_viewer.Tools.Logger;
import io.github.smutty_tools.smutty_viewer.Tools.Logger.Level;
import io.github.smutty_tools.smutty_viewer.Tools.Utils;

public class RefreshIndexTask extends AsyncTask<String, RefreshIndexTask.ProgressBundle, Void> {

    public interface FinishedNotifier {
        void taskFinished(RefreshIndexTask task);
    }

    public static class ProgressBundle {

        public final LogEntry logEntry;
        public final int progress;
        public final int maxProgress;

        public ProgressBundle(LogEntry logEntry, int progress, int maxProgress) {
            this.logEntry = logEntry;
            this.progress = progress;
            this.maxProgress = maxProgress;
        }
    }

    public static String TAG = "RefreshIndexTask";

    private WeakReference<Logger> loggerWeakReference;
    private WeakReference<ProgressBar> progressBarWeakReference;
    private WeakReference<FinishedNotifier> finishedNotifierWeakReference;

    private AppDatabase appDatabase;
    private URI baseUri;
    private File baseDirectory;
    private long totalBytes;
    private int progress;
    private int maximumProgress;

    public RefreshIndexTask(Logger logger, ProgressBar progressBar, FinishedNotifier finishedNotifier, AppDatabase appDatabase, File baseDirectory) {
        this.loggerWeakReference = new WeakReference<>(logger);
        this.progressBarWeakReference = new WeakReference<>(progressBar);
        this.finishedNotifierWeakReference = new WeakReference<>(finishedNotifier);
        this.appDatabase = appDatabase;
        this.baseDirectory = baseDirectory;
        this.baseUri = null;
        this.totalBytes = 0;
        this.progress = 0;
        this.maximumProgress = 1;
    }

    private void publishMessage(int level, Object... objects) {
        LogEntry logEntry = new LogEntry(level, TextUtils.join(" ", objects));
        publishProgress(new ProgressBundle(logEntry, progress, maximumProgress));
    }

    private void downloadPackage(String packageName, String md5) throws IOException {
        baseDirectory.mkdirs();
        // TODO: use subdirectories to distribute directory load .../a/b/c/d/abcdefgh
        File outputFile = new File(baseDirectory, md5);
        if (outputFile.exists()) {
            Log.d(TAG, "Skipping download, file already exists for package " + packageName);
            totalBytes += outputFile.length();
            return;
        }
        URL url = baseUri.resolve(packageName).toURL();
        int contentLength = Utils.DownloadUrlToFile(url, outputFile);
        // TODO: check actual md5 content
        totalBytes += contentLength;
    }

    private void refreshIndex(String indexUrl) throws IOException, JSONException, URISyntaxException {
        progress = 0;
        maximumProgress = 1;
        publishMessage(Level.INFO, "Synchronizing index");
        URL url = new URL(indexUrl);
        baseUri = url.toURI().resolve(".");
        Log.d(TAG, "baseUri=" + baseUri.toString());
        // fetch index
        URLConnection urlConnection = url.openConnection();
        InputStream in = urlConnection.getInputStream();
        // decompress and parse
        byte[] content = Decompressor.extractXz(in);
        JSONArray jsonArray = new JSONArray(new String(content));
        // setup progress bar
        int nItems = jsonArray.length();
        maximumProgress = nItems;
        publishMessage(Level.INFO, nItems, "packages in index");
        // store in database
        for (progress = 0; progress < nItems; progress++) {

            JSONObject jsonObject = jsonArray.getJSONObject(progress);
            SmuttyPackage pkg = SmuttyPackage.fromJson(jsonObject);
            SmuttyPackageDao pkgDao = appDatabase.smuttyPackageDao();
            pkgDao.insert(pkg);
            String packageFile = pkg.getPackageFile();
            downloadPackage(packageFile, pkg.getMd5());
            publishMessage(Level.DEBUG, "File " + packageFile + " downloaded");
        }
        publishMessage(Level.INFO, "Total index size", (long) Math.ceil((double) totalBytes / 1048576), "Mbytes");
        // TODO: purge unused files
    }

    @Override
    protected Void doInBackground(String... strings) {
        try {
            for (String str : strings) {
                refreshIndex(str);
            }
        } catch (Exception e) {
            Log.d(TAG, e.toString());
            cancel(false);
        }
        return null;
    }

    @Override
    protected void onPreExecute() {
        ProgressBar progressBar = progressBarWeakReference.get();
        if (progressBar != null) {
            progressBar.setVisibility(View.VISIBLE);
        }
    }

    @Override
    protected void onProgressUpdate(ProgressBundle... values) {
        for (RefreshIndexTask.ProgressBundle bundle : values) {
            Logger logger = loggerWeakReference.get();
            LogEntry entry = bundle.logEntry;
            if (entry != null && logger != null) {
                logger.log(entry.level, entry.message);
            }
            ProgressBar progressBar = progressBarWeakReference.get();
            if (progressBar != null) {
                progressBar.setProgress(bundle.progress);
                progressBar.setMax((bundle.maxProgress));
            }
        }
    }

    @Override
    protected void onPostExecute(Void aVoid) {
        hideProgress();
        Logger logger = loggerWeakReference.get();
        if (logger != null) {
            logger.info("Sync index finished");
        }
        notifyFinished();
    }

    @Override
    protected void onCancelled(Void aVoid) {
        hideProgress();
        Logger logger = loggerWeakReference.get();
        if (logger != null) {
            logger.warning("Sync index cancelled");
        }
        notifyFinished();
    }

    private void hideProgress() {
        ProgressBar progressBar = progressBarWeakReference.get();
        if (progressBar != null) {
            progressBar.setVisibility(View.GONE);
        }
    }

    private void notifyFinished() {
        FinishedNotifier finishedNotifier = finishedNotifierWeakReference.get();
        if (finishedNotifier != null) {
            finishedNotifier.taskFinished(this);
        }
    }
}
