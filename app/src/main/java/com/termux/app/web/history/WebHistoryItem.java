package com.termux.app.web.history;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.UUID;

public class WebHistoryItem {

    private final String mId;
    private final String mUrl;
    private String mTitle;
    private long mTimestamp;
    private int mVisitCount;

    public WebHistoryItem(@NonNull String url, @Nullable String title, long timestamp) {
        this(UUID.randomUUID().toString(), url, title, timestamp, 1);
    }

    public WebHistoryItem(@NonNull String id, @NonNull String url, @Nullable String title, long timestamp, int visitCount) {
        mId = id;
        mUrl = url;
        mTitle = (title != null && !title.trim().isEmpty()) ? title.trim() : url;
        mTimestamp = timestamp;
        mVisitCount = Math.max(1, visitCount);
    }

    @NonNull
    public String getId() {
        return mId;
    }

    @NonNull
    public String getUrl() {
        return mUrl;
    }

    @NonNull
    public String getTitle() {
        return mTitle;
    }

    public void setTitle(@NonNull String title) {
        if (!title.trim().isEmpty()) {
            mTitle = title.trim();
        }
    }

    public long getTimestamp() {
        return mTimestamp;
    }

    public void setTimestamp(long timestamp) {
        mTimestamp = timestamp;
    }

    public int getVisitCount() {
        return mVisitCount;
    }

    public void incrementVisitCount() {
        mVisitCount++;
    }

    @NonNull
    public JSONObject toJson() throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put("id", mId);
        obj.put("url", mUrl);
        obj.put("title", mTitle);
        obj.put("timestamp", mTimestamp);
        obj.put("visitCount", mVisitCount);
        return obj;
    }

    @Nullable
    public static WebHistoryItem fromJson(@NonNull JSONObject obj) {
        try {
            String id = obj.optString("id", UUID.randomUUID().toString());
            String url = obj.getString("url");
            String title = obj.optString("title", url);
            long timestamp = obj.optLong("timestamp", System.currentTimeMillis());
            int visitCount = obj.optInt("visitCount", 1);
            return new WebHistoryItem(id, url, title, timestamp, visitCount);
        } catch (JSONException e) {
            return null;
        }
    }
}
