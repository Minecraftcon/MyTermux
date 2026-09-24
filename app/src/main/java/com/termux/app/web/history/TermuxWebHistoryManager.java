package com.termux.app.web.history;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;

import com.termux.shared.logger.Logger;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TermuxWebHistoryManager {

    private static final String LOG_TAG = "TermuxWebHistoryManager";
    public static final String PREF_SAVE_HISTORY = "web_save_history";
    public static final String PREF_CLEAR_CACHE_ON_EXIT = "web_clear_cache_on_exit";
    private static final int MAX_HISTORY_ENTRIES = 2000;

    public interface HistoryChangeListener {
        void onHistoryChanged();
    }

    private static TermuxWebHistoryManager sInstance;

    private final Context mContext;
    private final SharedPreferences mPrefs;
    private final List<WebHistoryItem> mHistory = new ArrayList<>();
    private final List<HistoryChangeListener> mListeners = new CopyOnWriteArrayList<>();
    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();
    private boolean mLoaded = false;

    private TermuxWebHistoryManager(@NonNull Context context) {
        mContext = context.getApplicationContext();
        mPrefs = PreferenceManager.getDefaultSharedPreferences(mContext);
        loadHistoryAsync();
    }

    public static synchronized TermuxWebHistoryManager getInstance(@NonNull Context context) {
        if (sInstance == null) {
            sInstance = new TermuxWebHistoryManager(context);
        }
        return sInstance;
    }

    public boolean isHistoryEnabled() {
        return mPrefs.getBoolean(PREF_SAVE_HISTORY, true);
    }

    public void setHistoryEnabled(boolean enabled) {
        mPrefs.edit().putBoolean(PREF_SAVE_HISTORY, enabled).apply();
    }

    public boolean isClearCacheOnExitEnabled() {
        return mPrefs.getBoolean(PREF_CLEAR_CACHE_ON_EXIT, false);
    }

    public void setClearCacheOnExitEnabled(boolean enabled) {
        mPrefs.edit().putBoolean(PREF_CLEAR_CACHE_ON_EXIT, enabled).apply();
    }

    public void addListener(@NonNull HistoryChangeListener listener) {
        if (!mListeners.contains(listener)) {
            mListeners.add(listener);
        }
    }

    public void removeListener(@NonNull HistoryChangeListener listener) {
        mListeners.remove(listener);
    }

    private void notifyListeners() {
        for (HistoryChangeListener listener : mListeners) {
            listener.onHistoryChanged();
        }
    }

    public void recordVisit(@Nullable final String url, @Nullable final String title) {
        if (!isHistoryEnabled()) {
            return;
        }
        if (url == null || url.trim().isEmpty()) {
            return;
        }
        final String trimmedUrl = url.trim();
        if (trimmedUrl.startsWith("about:blank") || trimmedUrl.startsWith("data:") || trimmedUrl.startsWith("javascript:")) {
            return;
        }

        mExecutor.execute(() -> {
            ensureLoaded();
            synchronized (mHistory) {
                long now = System.currentTimeMillis();
                // Check if last visit was the exact same URL within 15 seconds (debounce reloads/redirects)
                if (!mHistory.isEmpty()) {
                    WebHistoryItem latest = mHistory.get(0);
                    if (latest.getUrl().equalsIgnoreCase(trimmedUrl) && (now - latest.getTimestamp()) < 15000) {
                        if (title != null && !title.trim().isEmpty() && !title.equalsIgnoreCase(latest.getUrl())) {
                            latest.setTitle(title.trim());
                        }
                        latest.setTimestamp(now);
                        latest.incrementVisitCount();
                        saveHistoryLocked();
                        notifyListeners();
                        return;
                    }
                }

                WebHistoryItem item = new WebHistoryItem(trimmedUrl, title, now);
                mHistory.add(0, item);

                if (mHistory.size() > MAX_HISTORY_ENTRIES) {
                    mHistory.remove(mHistory.size() - 1);
                }

                saveHistoryLocked();
            }
            notifyListeners();
        });
    }

    @NonNull
    public List<WebHistoryItem> getHistory() {
        ensureLoaded();
        synchronized (mHistory) {
            return new ArrayList<>(mHistory);
        }
    }

    @NonNull
    public List<WebHistoryItem> searchHistory(@Nullable String query) {
        ensureLoaded();
        List<WebHistoryItem> results = new ArrayList<>();
        if (query == null || query.trim().isEmpty()) {
            return getHistory();
        }
        String lowerQuery = query.trim().toLowerCase();
        synchronized (mHistory) {
            for (WebHistoryItem item : mHistory) {
                if (item.getTitle().toLowerCase().contains(lowerQuery) ||
                    item.getUrl().toLowerCase().contains(lowerQuery)) {
                    results.add(item);
                }
            }
        }
        return results;
    }

    public void deleteItem(@NonNull final String id) {
        mExecutor.execute(() -> {
            ensureLoaded();
            synchronized (mHistory) {
                Iterator<WebHistoryItem> it = mHistory.iterator();
                boolean removed = false;
                while (it.hasNext()) {
                    if (it.next().getId().equals(id)) {
                        it.remove();
                        removed = true;
                        break;
                    }
                }
                if (removed) {
                    saveHistoryLocked();
                }
            }
            notifyListeners();
        });
    }

    public void clearHistory() {
        mExecutor.execute(() -> {
            ensureLoaded();
            synchronized (mHistory) {
                mHistory.clear();
                saveHistoryLocked();
            }
            notifyListeners();
        });
    }

    public void clearHistoryOlderThan(final long olderThanTimestamp) {
        mExecutor.execute(() -> {
            ensureLoaded();
            synchronized (mHistory) {
                Iterator<WebHistoryItem> it = mHistory.iterator();
                boolean changed = false;
                while (it.hasNext()) {
                    if (it.next().getTimestamp() < olderThanTimestamp) {
                        it.remove();
                        changed = true;
                    }
                }
                if (changed) {
                    saveHistoryLocked();
                }
            }
            notifyListeners();
        });
    }

    @NonNull
    public String exportHistoryJson() {
        ensureLoaded();
        synchronized (mHistory) {
            JSONArray arr = new JSONArray();
            for (WebHistoryItem item : mHistory) {
                try {
                    arr.put(item.toJson());
                } catch (Exception ignored) {}
            }
            return arr.toString();
        }
    }

    @NonNull
    private File getHistoryFile() {
        File homeDir = new File("/data/data/com.termux/files/home/.termux");
        if (!homeDir.exists()) {
            homeDir.mkdirs();
        }
        if (homeDir.exists() && homeDir.canWrite()) {
            return new File(homeDir, "web_history.json");
        }
        return new File(mContext.getFilesDir(), "web_history.json");
    }

    private void loadHistoryAsync() {
        mExecutor.execute(this::ensureLoaded);
    }

    private synchronized void ensureLoaded() {
        if (mLoaded) return;
        File file = getHistoryFile();
        if (!file.exists()) {
            mLoaded = true;
            return;
        }

        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] bytes = new byte[(int) file.length()];
            int read = fis.read(bytes);
            if (read > 0) {
                String jsonStr = new String(bytes, 0, read, StandardCharsets.UTF_8);
                JSONArray array = new JSONArray(jsonStr);
                synchronized (mHistory) {
                    mHistory.clear();
                    for (int i = 0; i < array.length(); i++) {
                        JSONObject obj = array.getJSONObject(i);
                        WebHistoryItem item = WebHistoryItem.fromJson(obj);
                        if (item != null) {
                            mHistory.add(item);
                        }
                    }
                }
            }
        } catch (Exception e) {
            Logger.logError(LOG_TAG, "Failed to load web history from " + file.getAbsolutePath() + ": " + e.getMessage());
        } finally {
            mLoaded = true;
        }
    }

    private void saveHistoryLocked() {
        File file = getHistoryFile();
        try {
            JSONArray array = new JSONArray();
            for (WebHistoryItem item : mHistory) {
                array.put(item.toJson());
            }
            String jsonStr = array.toString(2);
            try (FileOutputStream fos = new FileOutputStream(file)) {
                fos.write(jsonStr.getBytes(StandardCharsets.UTF_8));
                fos.flush();
            }
        } catch (Exception e) {
            Logger.logError(LOG_TAG, "Failed to save web history to " + file.getAbsolutePath() + ": " + e.getMessage());
        }
    }
}
