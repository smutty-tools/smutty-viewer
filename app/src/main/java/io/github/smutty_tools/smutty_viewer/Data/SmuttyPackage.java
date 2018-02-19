package io.github.smutty_tools.smutty_viewer.Data;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.net.URL;

import io.github.smutty_tools.smutty_viewer.Tools.FileUtils;

public class SmuttyPackage {

    private String contentType;
    private int minId;
    private int maxId;
    private String hashDigest;

    private File localFile;
    private URL remoteURL;

    // TODO: class property for directory storage ?

    public static SmuttyPackage fromJson(JSONObject jsonObject) throws JSONException {
        return new SmuttyPackage(
            jsonObject.getString("content_type"),
            jsonObject.getInt("min_id"),
            jsonObject.getInt("max_id"),
            jsonObject.getString("hash_digest"));
    }

    public SmuttyPackage(String contentType, int minId, int maxId, String hashDigest) {
        this.contentType = contentType;
        this.minId = minId;
        this.maxId = maxId;
        this.hashDigest = hashDigest;
        this.localFile = null;
        this.remoteURL = null;
    }

    public boolean isLocalFileValid() {
        return localFile.exists() && FileUtils.IsFileMd5Valid(localFile, hashDigest);
    }

    public String getName() {
        StringBuffer buf = new StringBuffer(contentType);
        buf.append('-');
        buf.append(Integer.toString(minId));
        buf.append('-');
        buf.append(Integer.toString(maxId));
        buf.append('-');
        buf.append(hashDigest);
        return buf.toString();
    }

    public String getPackageFileName() {
        StringBuffer buf = new StringBuffer(getName());
        buf.append(".jsonl.xz");
        return buf.toString();
    }

    public String getContentType() {
        return contentType;
    }

    public int getMinId() {
        return minId;
    }

    public int getMaxId() {
        return maxId;
    }

    public String getHashDigest() {
        return hashDigest;
    }

    public File getLocalFile() {
        return localFile;
    }

    public void setLocalFile(File localFile) {
        this.localFile = localFile;
    }

    public URL getRemoteURL() {
        return remoteURL;
    }

    public void setRemoteURL(URL remoteURL) {
        this.remoteURL = remoteURL;
    }
}
