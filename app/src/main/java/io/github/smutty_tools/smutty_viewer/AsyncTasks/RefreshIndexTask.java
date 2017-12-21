package io.github.smutty_tools.smutty_viewer.AsyncTasks;

import android.os.AsyncTask;
import android.text.TextUtils;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;

import io.github.smutty_tools.smutty_viewer.Data.AppDatabase;
import io.github.smutty_tools.smutty_viewer.Data.SmuttyPackage;
import io.github.smutty_tools.smutty_viewer.Data.SmuttyPackageDao;
import io.github.smutty_tools.smutty_viewer.Decompress.Decompressor;
import io.github.smutty_tools.smutty_viewer.Tools.LogEntry;
import io.github.smutty_tools.smutty_viewer.Tools.Logger.Level;

public abstract class RefreshIndexTask extends AsyncTask<String, LogEntry, Void> {

    public static String TAG="RefreshIndexTask";

    private AppDatabase appDatabase;
    private URI baseUri;
    private File baseDirectory;
    private long totalBytes;

    public RefreshIndexTask(AppDatabase appDatabase, File baseDirectory) {
        this.appDatabase = appDatabase;
        this.baseDirectory = baseDirectory;
        this.baseUri = null;
        this.totalBytes = 0;
    }

    private void publishMessage(int level, Object... objects) {
        publishProgress(new LogEntry(level, TextUtils.join(" ", objects)));
    }

    private void downloadPackage(String packageName) throws IOException {
        baseDirectory.mkdirs();
        File outputFile = new File(baseDirectory, packageName);
        if (outputFile.exists()) {
            // TODO: check actual md5 content
            Log.d(TAG, "Skipping download, file already exists for package " + packageName);
            totalBytes += outputFile.length();
            return;
        }
        URL url = baseUri.resolve(packageName).toURL();
        URLConnection urlConnection = url.openConnection();
        int contentLength = urlConnection.getContentLength();
        Log.d(TAG, packageName + " has a size of " + Integer.toString(contentLength));
        byte[] buffer = new byte[8 * 1024];
        int bytesRead;
        InputStream in = urlConnection.getInputStream();
        OutputStream outStream = new FileOutputStream(outputFile);
        while ((bytesRead = in.read(buffer)) != -1) {
            outStream.write(buffer, 0, bytesRead);
        }
        outStream.close();
        totalBytes += contentLength;
    }

    private void refreshIndex(String indexUrl) throws IOException, JSONException, URISyntaxException {
        publishMessage(Level.INFO, "Synchronizing index");
        URL url = new URL(indexUrl);
        baseUri = url.toURI().resolve(".");
        Log.d(TAG, "baseUri="+baseUri.toString());
        // fetch index
        URLConnection urlConnection = url.openConnection();
        InputStream in = urlConnection.getInputStream();
        // decompress and parse
        byte[] content = Decompressor.extractXz(in);
        JSONArray jsonArray = new JSONArray(new String(content));
        int nItems = jsonArray.length();
        publishMessage(Level.INFO, nItems, "packages in index");
        // store
        for (int i=0; i<nItems; i++) {
            JSONObject jsonObject = jsonArray.getJSONObject(i);
            SmuttyPackage pkg = SmuttyPackage.fromJson(jsonObject);
            SmuttyPackageDao pkgDao = appDatabase.smuttyPackageDao();
            pkgDao.insert(pkg);
            downloadPackage(pkg.getPackageFile());
        }
        publishMessage(Level.INFO, "Total index size", (long) Math.ceil((double)totalBytes/1048576), "Mbytes");
    }

    @Override
    protected Void doInBackground(String... strings) {
        try {


            for (String str : strings) {
                refreshIndex(str);
            }
        }
        catch (Exception e) {
            Log.d(TAG, e.toString());
            cancel(false);
        }
        return null;
    }
}
