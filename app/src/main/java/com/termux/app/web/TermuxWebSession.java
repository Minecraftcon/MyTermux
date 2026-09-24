package com.termux.app.web;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.webkit.ConsoleMessage;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.shared.logger.Logger;

import java.util.UUID;

public class TermuxWebSession {

    private static final String LOG_TAG = "TermuxWebSession";

    public interface SessionCallback {
        void onTitleChanged(@NonNull TermuxWebSession session, @NonNull String title);
        void onUrlChanged(@NonNull TermuxWebSession session, @NonNull String url);
        void onProgressChanged(@NonNull TermuxWebSession session, int progress);
        void onSessionClosed(@NonNull TermuxWebSession session);
    }

    private final String mId;
    private String mTitle;
    private String mUrl;
    private WebView mWebView;
    private SessionCallback mCallback;
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());

    @SuppressLint("SetJavaScriptEnabled")
    public TermuxWebSession(@NonNull Context context, @Nullable String initialUrl, @Nullable String customTitle) {
        this.mId = UUID.randomUUID().toString();
        this.mTitle = TextUtils.isEmpty(customTitle) ? "Web View" : customTitle;
        this.mUrl = TextUtils.isEmpty(initialUrl) ? "about:blank" : initialUrl;

        initWebView(context);
        if (!TextUtils.isEmpty(initialUrl)) {
            loadUrl(initialUrl);
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void initWebView(@NonNull Context context) {
        WebView.setWebContentsDebuggingEnabled(true);
        mWebView = new WebView(context);

        WebSettings settings = mWebView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setAllowFileAccessFromFileURLs(true);
        settings.setAllowUniversalAccessFromFileURLs(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);

        mWebView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                String scheme = uri != null ? uri.getScheme() : null;
                if ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme) ||
                    "file".equalsIgnoreCase(scheme) || "data".equalsIgnoreCase(scheme) ||
                    "about".equalsIgnoreCase(scheme)) {
                    return false; // Load inside WebView
                }
                return false;
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                mUrl = url;
                if (mCallback != null) {
                    mCallback.onUrlChanged(TermuxWebSession.this, url);
                }
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                mUrl = url;
                String pageTitle = view.getTitle();
                if (!TextUtils.isEmpty(pageTitle)) {
                    mTitle = pageTitle;
                    if (mCallback != null) {
                        mCallback.onTitleChanged(TermuxWebSession.this, pageTitle);
                    }
                }
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                Logger.logWarn(LOG_TAG, "WebView error: " + error.getDescription());
            }
        });

        mWebView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onReceivedTitle(WebView view, String title) {
                super.onReceivedTitle(view, title);
                if (!TextUtils.isEmpty(title)) {
                    mTitle = title;
                    if (mCallback != null) {
                        mCallback.onTitleChanged(TermuxWebSession.this, title);
                    }
                }
            }

            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                super.onProgressChanged(view, newProgress);
                if (mCallback != null) {
                    mCallback.onProgressChanged(TermuxWebSession.this, newProgress);
                }
            }

            @Override
            public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
                Logger.logDebug(LOG_TAG, "[" + consoleMessage.messageLevel() + "] " +
                    consoleMessage.message() + " (" + consoleMessage.sourceId() + ":" + consoleMessage.lineNumber() + ")");
                return true;
            }
        });
    }

    public void setCallback(@Nullable SessionCallback callback) {
        this.mCallback = callback;
    }

    @NonNull
    public String getId() {
        return mId;
    }

    @NonNull
    public String getTitle() {
        return mTitle;
    }

    public void setTitle(@NonNull String title) {
        this.mTitle = title;
        if (mCallback != null) {
            mCallback.onTitleChanged(this, title);
        }
    }

    @NonNull
    public String getUrl() {
        return mUrl != null ? mUrl : "";
    }

    @Nullable
    public WebView getWebView() {
        return mWebView;
    }

    public void loadUrl(@NonNull final String url) {
        mMainHandler.post(() -> {
            if (mWebView != null) {
                mUrl = url;
                mWebView.loadUrl(url);
            }
        });
    }

    public void loadHtml(@NonNull final String html, @Nullable final String baseUrl) {
        mMainHandler.post(() -> {
            if (mWebView != null) {
                mWebView.loadDataWithBaseURL(baseUrl != null ? baseUrl : "file:///data/data/com.termux/files/home/",
                    html, "text/html", "UTF-8", null);
            }
        });
    }

    public void evaluateJavascript(@NonNull final String script, @Nullable final ValueCallback<String> resultCallback) {
        mMainHandler.post(() -> {
            if (mWebView != null) {
                mWebView.evaluateJavascript(script, resultCallback);
            }
        });
    }

    public boolean canGoBack() {
        return mWebView != null && mWebView.canGoBack();
    }

    public void goBack() {
        if (mWebView != null && mWebView.canGoBack()) {
            mWebView.goBack();
        }
    }

    public boolean canGoForward() {
        return mWebView != null && mWebView.canGoForward();
    }

    public void goForward() {
        if (mWebView != null && mWebView.canGoForward()) {
            mWebView.goForward();
        }
    }

    public void reload() {
        if (mWebView != null) {
            mWebView.reload();
        }
    }

    public void destroy() {
        mMainHandler.post(() -> {
            if (mCallback != null) {
                mCallback.onSessionClosed(this);
            }
            if (mWebView != null) {
                mWebView.stopLoading();
                mWebView.clearHistory();
                mWebView.destroy();
                mWebView = null;
            }
        });
    }
}
