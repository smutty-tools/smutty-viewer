package io.github.smutty_tools.smutty_viewer.AsyncTasks;

import android.os.AsyncTask;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
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

    public RefreshIndexTask(AppDatabase appDatabase) {
        this.appDatabase = appDatabase;
    }

    private void publishMessage(int level, Object... objects) {
        publishProgress(new LogEntry(level, TextUtils.join(" ", objects)));
    }

    private void refreshIndex(String indexUrl) throws IOException, JSONException {
        publishMessage(Level.INFO, "Synchronizing index from ", indexUrl);
        // fetch
        URL url = new URL(indexUrl);
        URLConnection urlConnection = url.openConnection();
        InputStream in = urlConnection.getInputStream();
        // decompress and parse
        byte[] content = Decompressor.extractXz(in);
        JSONArray jsonArray = new JSONArray(new String(content));
        int nItems = jsonArray.length();
        publishMessage(Level.INFO, "Data packages in index", nItems);
        // store
        for (int i=0; i<nItems; i++) {
            JSONObject jsonObject = jsonArray.getJSONObject(i);
            SmuttyPackage pkg = SmuttyPackage.fromJson(jsonObject);
            SmuttyPackageDao pkgDao = appDatabase.smuttyPackageDao();
            pkgDao.insert(pkg);
            publishMessage(Level.INFO, "Packages entry stored", jsonObject);
        }
    }

    @Override
    protected Void doInBackground(String... strings) {
        try {
            for (String str : strings) {
                refreshIndex(str);
            }
        }
        catch (Exception e) {
            cancel(false);
        }
        return null;
    }
}
