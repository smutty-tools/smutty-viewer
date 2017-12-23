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
import java.util.Arrays;
import java.util.HashSet;

import io.github.smutty_tools.smutty_viewer.Data.AppDatabase;
import io.github.smutty_tools.smutty_viewer.Data.SmuttyPackage;
import io.github.smutty_tools.smutty_viewer.Data.SmuttyPackageDao;
import io.github.smutty_tools.smutty_viewer.Decompress.Decompressor;
import io.github.smutty_tools.smutty_viewer.Exceptions.SmuttyException;
import io.github.smutty_tools.smutty_viewer.Tools.LogEntry;
import io.github.smutty_tools.smutty_viewer.Tools.LogProgressBundle;
import io.github.smutty_tools.smutty_viewer.Tools.Logger;
import io.github.smutty_tools.smutty_viewer.Tools.Logger.Level;
import io.github.smutty_tools.smutty_viewer.Tools.Utils;

public class RefreshIndexTask extends AsyncTask<String, LogProgressBundle, Void> {

    public interface FinishedNotifier {
        void taskFinished(RefreshIndexTask task);
    }

    public static final String TAG = "RefreshIndexTask";

    private WeakReference<Logger> loggerWeakReference;
    private WeakReference<ProgressBar> progressBarWeakReference;
    private WeakReference<FinishedNotifier> finishedNotifierWeakReference;

    private AppDatabase appDatabase;
    private URI baseUri;
    private File baseDirectory;
    private long totalBytes;
    private int progress;
    private int maximumProgress;
    private HashSet<File> indexFiles;

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
        this.indexFiles = new HashSet<>();
    }

    private void publishMessage(int level, Object... objects) {
        LogEntry logEntry = new LogEntry(level, TextUtils.join(" ", objects));
        publishProgress(new LogProgressBundle(logEntry, progress, maximumProgress));
    }

    private boolean isHashValid(File outputFile, String md5) {
        return Utils.FileMd5(outputFile).toLowerCase().equals(md5.toLowerCase());
    }

    private void downloadPackage(String packageName, String hash) throws IOException, SmuttyException {
        baseDirectory.mkdirs();
        // TODO: use subdirectories to distribute directory load : abcdefgh => a/b/c/d/abcdefgh
        File outputFile = new File(baseDirectory, hash);
        if (outputFile.exists() && isHashValid(outputFile, hash)) {
            publishMessage(Level.DEBUG, "File " + packageName + " exists with valid hash");
        } else {
            URL url = baseUri.resolve(packageName).toURL();
            Utils.DownloadUrlToFile(url, outputFile);
            publishMessage(Level.DEBUG, "File " + packageName + " downloaded");
            if (!isHashValid(outputFile, hash)) {
                throw new SmuttyException("Package file has invalid checksum");
            }
        }
        totalBytes += outputFile.length();
        indexFiles.remove(outputFile);
    }

    private void refreshIndex(String indexUrl) throws IOException, JSONException, URISyntaxException, SmuttyException {
        progress = 0;
        maximumProgress = 1;
        publishMessage(Level.INFO, "Synchronizing index from " + indexUrl);
        URL url = new URL(indexUrl);
        baseUri = url.toURI().resolve(".");
        // fetch index
        URLConnection urlConnection = url.openConnection();
        InputStream in = urlConnection.getInputStream();
        // decompress and parse
        byte[] content = Decompressor.extractXz(in);
        JSONArray jsonArray = new JSONArray(new String(content));
        // setup progress bar
        int nItems = jsonArray.length();
        maximumProgress = nItems;
        publishMessage(Level.INFO, "Index references " + nItems + " packages");
        // check cancel between operations
        if (isCancelled()) {
            return;
        }
        // clearing packages from database
        SmuttyPackageDao pkgDao = appDatabase.smuttyPackageDao();
        publishMessage(Level.INFO, "Clearing package database");
        pkgDao.truncate();
        // process each entry and act accordingly
        publishMessage(Level.INFO, "Checking packages status...");
        for (progress = 0; progress < nItems; progress++) {
            JSONObject jsonObject = jsonArray.getJSONObject(progress);
            // store in database
            SmuttyPackage pkg = SmuttyPackage.fromJson(jsonObject);
            pkgDao.insert(pkg);
            // download package file if necessary
            String packageFile = pkg.getPackageFile();
            downloadPackage(packageFile, pkg.getMd5());
            // check cancel between operations
            if (isCancelled()) {
                return;
            }
        }
        publishMessage(Level.INFO, "Total index size", (long) Math.ceil((double) totalBytes / 1048576), "Mbytes");
    }

    private void searchExistingFiles() {
        indexFiles.clear();
        indexFiles.addAll(Arrays.asList(baseDirectory.listFiles()));
        publishMessage(Level.INFO, "Found", indexFiles.size(), "packages on disk");
    }

    private void removeUnusedFiles() {
        publishMessage(Level.INFO, "Deleting", indexFiles.size(), "obsolete packages");
        for (File file : indexFiles) {
            publishMessage(Level.DEBUG, "Deleting unused file " + file.toString());
            file.delete();
        }
        indexFiles.clear();
    }

    @Override
    protected Void doInBackground(String... strings) {
        try {
            publishMessage(Level.INFO, "Listing current files");
            searchExistingFiles();
            for (String str : strings) {
                refreshIndex(str);
            }
            removeUnusedFiles();
        }
        catch (SmuttyException e) {
            publishMessage(Level.ERROR, e.getMessage());
        }
        catch (Exception e) {
            publishMessage(Level.ERROR, e.getMessage());
            Log.e(TAG, Log.getStackTraceString(e));
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
    protected void onProgressUpdate(LogProgressBundle... values) {
        for (LogProgressBundle bundle : values) {
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
