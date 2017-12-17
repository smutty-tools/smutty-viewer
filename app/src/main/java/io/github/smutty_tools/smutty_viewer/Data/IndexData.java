package io.github.smutty_tools.smutty_viewer.Data;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import io.github.smutty_tools.smutty_viewer.Tools.Logger;

public class IndexData {

    public static final String TAG = "IndexData";

    private Logger logger;

    public IndexData(Logger logger) {
        this.logger = logger;
    }

    public void persistFromJsonString(String json) {
        JSONArray jsonArray = null;
        try {
            jsonArray = new JSONArray(json);
            int nItems = jsonArray.length();
            logger.info(nItems, "data packages in index");
            for (int i=0; i<nItems; i++) {
                JSONObject jsonObject = jsonArray.getJSONObject(i);
                logger.info("IndexItem", jsonObject);
            }
        } catch (JSONException e) {
            logger.error("Json error", e.getMessage());
            return;
        }
    }
}
