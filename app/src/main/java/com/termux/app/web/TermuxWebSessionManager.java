package com.termux.app.web;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class TermuxWebSessionManager {

    public interface WebSessionListListener {
        void onWebSessionListUpdated();
    }

    private static TermuxWebSessionManager sInstance;

    private final List<TermuxWebSession> mWebSessions = new CopyOnWriteArrayList<>();
    private final List<WebSessionListListener> mListeners = new CopyOnWriteArrayList<>();
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());

    private TermuxWebSessionManager() {}

    public static synchronized TermuxWebSessionManager getInstance() {
        if (sInstance == null) {
            sInstance = new TermuxWebSessionManager();
        }
        return sInstance;
    }

    public void addListener(@NonNull WebSessionListListener listener) {
        if (!mListeners.contains(listener)) {
            mListeners.add(listener);
        }
    }

    public void removeListener(@NonNull WebSessionListListener listener) {
        mListeners.remove(listener);
    }

    private void notifyListeners() {
        mMainHandler.post(() -> {
            for (WebSessionListListener listener : mListeners) {
                listener.onWebSessionListUpdated();
            }
        });
    }

    @NonNull
    public TermuxWebSession createWebSession(@NonNull Context context, @Nullable String url, @Nullable String title) {
        final TermuxWebSession session = new TermuxWebSession(context, url, title);
        session.setCallback(new TermuxWebSession.SessionCallback() {
            @Override
            public void onTitleChanged(@NonNull TermuxWebSession s, @NonNull String newTitle) {
                notifyListeners();
            }

            @Override
            public void onUrlChanged(@NonNull TermuxWebSession s, @NonNull String newUrl) {
                notifyListeners();
            }

            @Override
            public void onProgressChanged(@NonNull TermuxWebSession s, int progress) {
            }

            @Override
            public void onSessionClosed(@NonNull TermuxWebSession s) {
                removeWebSession(s);
            }
        });

        mWebSessions.add(session);
        notifyListeners();
        return session;
    }

    public void removeWebSession(@NonNull TermuxWebSession session) {
        if (mWebSessions.remove(session)) {
            session.destroy();
            notifyListeners();
        }
    }

    public void removeWebSessionAt(int index) {
        if (index >= 0 && index < mWebSessions.size()) {
            TermuxWebSession session = mWebSessions.remove(index);
            session.destroy();
            notifyListeners();
        }
    }

    public int getWebSessionCount() {
        return mWebSessions.size();
    }

    @NonNull
    public List<TermuxWebSession> getWebSessions() {
        return Collections.unmodifiableList(new ArrayList<>(mWebSessions));
    }

    @Nullable
    public TermuxWebSession getWebSessionAt(int index) {
        if (index >= 0 && index < mWebSessions.size()) {
            return mWebSessions.get(index);
        }
        return null;
    }

    @Nullable
    public TermuxWebSession getWebSessionById(@NonNull String id) {
        for (TermuxWebSession session : mWebSessions) {
            if (session.getId().equals(id)) {
                return session;
            }
        }
        return null;
    }

    @Nullable
    public TermuxWebSession getLastWebSession() {
        if (mWebSessions.isEmpty()) return null;
        return mWebSessions.get(mWebSessions.size() - 1);
    }
}
