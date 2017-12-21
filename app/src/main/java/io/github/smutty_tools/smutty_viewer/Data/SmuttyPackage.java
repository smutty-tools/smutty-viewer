package io.github.smutty_tools.smutty_viewer.Data;

import android.arch.persistence.room.ColumnInfo;
import android.arch.persistence.room.Entity;
import android.arch.persistence.room.PrimaryKey;
import android.support.annotation.NonNull;

import org.json.JSONException;
import org.json.JSONObject;

@Entity(tableName = "packages")
public class SmuttyPackage {

    @PrimaryKey
    @NonNull
    private String md5;

    @ColumnInfo(name = "referenced_in_index")
    private boolean referencedInIndex;

    @ColumnInfo(name = "file_name")
    private String fileName;

    @ColumnInfo(name = "content_type")
    private String contentType;

    @ColumnInfo(name = "max_id")
    private int maxId;

    @ColumnInfo(name = "min_id")
    private int minId;

    @ColumnInfo(name = "has_tags")
    private boolean hasTags;

    public static SmuttyPackage fromJson(JSONObject jsonObject) throws JSONException {
        return new SmuttyPackage(
                jsonObject.getString("md5"),
                true, // referencedInIndex
                jsonObject.getString("file"),
                jsonObject.getString("type"),
                jsonObject.getInt("max_id"),
                jsonObject.getInt("min_id"),
                jsonObject.getBoolean("tags"));
    }

    public SmuttyPackage(String md5, boolean referencedInIndex, String fileName, String contentType, int maxId, int minId, boolean hasTags) {
        this.md5 = md5;
        this.referencedInIndex = referencedInIndex;
        this.fileName = fileName;
        this.contentType = contentType;
        this.maxId = maxId;
        this.minId = minId;
        this.hasTags = hasTags;
    }

    public String getMd5() {
        return md5;
    }

    public void setMd5(String md5) {
        this.md5 = md5;
    }

    public boolean isReferencedInIndex() {
        return referencedInIndex;
    }

    public void setReferencedInIndex(boolean referencedInIndex) {
        this.referencedInIndex = referencedInIndex;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public int getMaxId() {
        return maxId;
    }

    public void setMaxId(int maxId) {
        this.maxId = maxId;
    }

    public int getMinId() {
        return minId;
    }

    public void setMinId(int minId) {
        this.minId = minId;
    }

    public boolean isHasTags() {
        return hasTags;
    }

    public void setHasTags(boolean hasTags) {
        this.hasTags = hasTags;
    }

    public String getPackageFile() {
        StringBuffer buf = new StringBuffer(md5);
        buf.append('_');
        buf.append(fileName);
        return buf.toString();
    }
}
