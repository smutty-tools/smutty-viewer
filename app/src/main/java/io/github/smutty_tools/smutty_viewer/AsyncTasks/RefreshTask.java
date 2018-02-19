package io.github.smutty_tools.smutty_viewer.AsyncTasks;

import android.os.AsyncTask;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import io.github.smutty_tools.smutty_viewer.Data.SmuttyPackage;
import io.github.smutty_tools.smutty_viewer.Decompress.Decompressor;
import io.github.smutty_tools.smutty_viewer.Exceptions.SmuttyException;
import io.github.smutty_tools.smutty_viewer.Tools.FileUtils;

public class RefreshTask extends AsyncTask<String, Void, Void> {

    static final String TAG = "RefreshTask";

    public interface FinishedNotifier {
        void refreshTaskFinished(RefreshTask task);
    }

    private WeakReference<ProgressBar> progressBarWeakReference;
    private WeakReference<FinishedNotifier> finishedNotifierWeakReference;

    private URL indexUrl;
    private URI baseUri;
    private File baseDirectory;
    private long totalBytes;
    private int progress;
    private int maximumProgress;
    private HashSet<File> indexFiles;
    private List<SmuttyPackage> packages;

    public RefreshTask(ProgressBar progressBar, FinishedNotifier finishedNotifier, File baseDirectory, String indexUrl) throws SmuttyException {
        this.progressBarWeakReference = new WeakReference<>(progressBar);
        this.finishedNotifierWeakReference = new WeakReference<>(finishedNotifier);
        this.baseDirectory = baseDirectory;
        Log.d(TAG, "baseDirectory: " + baseDirectory);
        try {
            this.indexUrl = new URL(indexUrl);
            Log.d(TAG, "indexUrl: " + baseDirectory);
        }
        catch (MalformedURLException e) {
            throw new SmuttyException(e);
        }
        try {
            this.baseUri = this.indexUrl.toURI().resolve(".");
            Log.d(TAG, "baseUri: " + baseUri);
        }
        catch (URISyntaxException e) {
            throw new SmuttyException(e);
        }
        this.totalBytes = 0;
        this.progress = 0;
        this.maximumProgress = 1;
        this.indexFiles = new HashSet<>();
        this.packages = new ArrayList<>();
    }

    private boolean isHashValid(File outputFile, String md5) throws FileNotFoundException {
        return FileUtils.FileMd5(outputFile).toLowerCase().equals(md5.toLowerCase());
    }

    private void fetchIndex() throws SmuttyException {
        Log.i(TAG, "Synchronizing index from " + indexUrl);
        // fetch index
        InputStream in;
        try {
            in = FileUtils.DownloadUrlToInputStream(indexUrl);
        }
        catch (IOException e) {
            throw new SmuttyException(e);
        }
        // decompress and parse
        byte[] content;
        try {
            content = Decompressor.extractXz(in);
        }
        catch (IOException e) {
            throw new SmuttyException(e);
        }
        // parse
        JSONArray indexContent;
        try {
            indexContent = new JSONArray(new String(content));
        }
        catch (JSONException e) {
            throw new SmuttyException(e);
        }
        Log.d(TAG, indexContent.toString());
        // build packages
        packages.clear();
        int nItems = indexContent.length();
        for (progress = 0; progress < nItems; progress++) {
            JSONObject packageInfo;
            try {
                packageInfo = indexContent.getJSONObject(progress);
            }
            catch (JSONException e) {
                throw new SmuttyException(e);
            }
            Log.d(TAG, packageInfo.toString());
            // store in database
            SmuttyPackage pkg;
            try {
                pkg = SmuttyPackage.fromJson(packageInfo);
            }
            catch (JSONException e) {
                throw new SmuttyException(e);
            }
            packages.add(pkg);
        }
    }

    private void downloadPackage(String packageName, String hash) throws IOException, SmuttyException {
        // TODO: use subdirectories to distribute directory load : abcdefgh => a/b/c/d/abcdefgh
        File outputFile = new File(baseDirectory, hash);
        if (outputFile.exists() && isHashValid(outputFile, hash)) {
            Log.d(TAG, "File " + packageName + " exists with valid hash");
        } else {
            URL url = baseUri.resolve(packageName).toURL();
            FileUtils.DownloadUrlToFile(url, outputFile);
            Log.d(TAG, "File " + packageName + " downloaded");
            if (!isHashValid(outputFile, hash)) {
                throw new SmuttyException("Package file has invalid checksum");
            }
        }
        totalBytes += outputFile.length();
        indexFiles.remove(outputFile);
        publishProgress();
    }
/*

            String packageFile = pkg.getPackageFileName();
            downloadPackage(packageFile, pkg.getHashDigest());
*/

    private void searchExistingFiles() {
        Log.i(TAG, "Listing current files");
        indexFiles.clear();
        indexFiles.addAll(Arrays.asList(baseDirectory.listFiles()));
        Log.i(TAG, "Found " + Integer.toString(indexFiles.size()) + " packages on disk");
    }

    private void removeUnusedFiles() {
        Log.i(TAG, "Deleting " + Integer.toString(indexFiles.size()) + " obsolete packages");
        int count = 0;
        for (File file : indexFiles) {
            Log.d(TAG, "Deleting unused file " + file.toString());
            if (file.delete()) {
                count++;
            }
        }
        if (count != indexFiles.size()) {
            Log.d(TAG, "Only " + Integer.toString(count) + " files deleted, should have been " + Integer.toString(indexFiles.size()));
        }
        indexFiles.clear();
    }

    @Override
    protected Void doInBackground(String... strings) {
        long start = System.currentTimeMillis();
        try {
            // ensures target directory exists
            baseDirectory.mkdirs();
            if (!baseDirectory.exists() || !baseDirectory.isDirectory()) {
                throw new SmuttyException("Invalid storage " + baseDirectory.toString());
            }
/*
            // manage index files
            searchExistingFiles();
*/
            // startup
            progress = 0;
            maximumProgress = 1;
            publishProgress();
            // fetch index content
            fetchIndex();
            // setup progress bar
            progress++;
            maximumProgress += packages.size();
            publishProgress();
            Log.i(TAG, "Index references " + packages.size() + " packages");
            // check cancel between operations
            if (isCancelled()) {
                Log.d(TAG, "Task cancelation detected");
                return null;
            }

            // download packages if needed
            for (SmuttyPackage pkg : packages) {
                progress++;
                publishProgress();
                if (pkg.isLocalFileValid()) {
                    Log.d(TAG, "File " + packageName + " exists with valid hash");
                    continue;
                }

            }


//            refreshIndex();
//            removeUnusedFiles();
        }
        catch (SmuttyException e) {
            Log.e(TAG, e.getMessage());
        }
        catch (Exception e) {
            Log.e(TAG, e.getMessage());
            Log.e(TAG, Log.getStackTraceString(e));
        }
        long end = System.currentTimeMillis();
        float seconds = (float)(end-start)/1000.0f;
        Log.i(TAG, "Time spent: " + Integer.toString((int) seconds) + " seconds");
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
    protected void onProgressUpdate(Void... values) {
        ProgressBar progressBar = progressBarWeakReference.get();
        if (progressBar != null) {
            progressBar.setProgress(progress);
            progressBar.setMax(maximumProgress);
            Log.i(TAG, "Progress: " + Integer.toString(progress));
        }
    }

    @Override
    protected void onPostExecute(Void aVoid) {
        hideProgress();
        Log.i(TAG, "Sync finished");
        notifyFinished();
    }

    @Override
    protected void onCancelled(Void aVoid) {
        hideProgress();
        Log.w(TAG, "Sync cancelled");
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
            finishedNotifier.refreshTaskFinished(this);
        }
    }
}
