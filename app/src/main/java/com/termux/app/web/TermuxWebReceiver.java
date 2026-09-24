package com.termux.app.web;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.termux.app.TermuxActivity;
import com.termux.shared.logger.Logger;

public class TermuxWebReceiver extends BroadcastReceiver {

    private static final String LOG_TAG = "TermuxWebReceiver";

    public static final String ACTION_OPEN_WEB_SESSION = "com.termux.app.OPEN_WEB_SESSION";

    public static final String EXTRA_URL = "url";
    public static final String EXTRA_HTML = "html";
    public static final String EXTRA_TITLE = "title";
    public static final String EXTRA_NEW_SESSION = "new_session";
    public static final String EXTRA_EVAL_JS = "eval_js";
    public static final String EXTRA_CLOSE_SESSION = "close_session";
    public static final String EXTRA_OPEN_DRAWER = "open_drawer";

    private final Handler mMainHandler = new Handler(Looper.getMainLooper());

    @Override
    public void onReceive(final Context context, final Intent intent) {
        if (intent == null) return;

        final String action = intent.getAction();
        if (!ACTION_OPEN_WEB_SESSION.equals(action) && !"com.termux.OPEN_WEB_SESSION".equals(action)) {
            return;
        }

        final String url = intent.getStringExtra(EXTRA_URL);
        final String html = intent.getStringExtra(EXTRA_HTML);
        final String title = intent.getStringExtra(EXTRA_TITLE);
        final boolean newSession = intent.getBooleanExtra(EXTRA_NEW_SESSION, true);
        final String evalJs = intent.getStringExtra(EXTRA_EVAL_JS);
        final boolean closeSession = intent.getBooleanExtra(EXTRA_CLOSE_SESSION, false);
        final boolean openDrawer = intent.getBooleanExtra(EXTRA_OPEN_DRAWER, false);

        Logger.logDebug(LOG_TAG, "Received web dispatch intent: url=" + url + ", title=" + title + ", newSession=" + newSession);

        mMainHandler.post(() -> {
            TermuxActivity activity = TermuxActivity.getActivity();
            TermuxWebSessionManager sessionManager = TermuxWebSessionManager.getInstance();

            if (openDrawer && activity != null) {
                activity.getDrawer().openDrawer(android.view.Gravity.LEFT);
                return;
            }

            // Handle session closure
            if (closeSession) {
                if (activity != null && activity.getCurrentWebSession() != null) {
                    sessionManager.removeWebSession(activity.getCurrentWebSession());
                    activity.showTerminalView();
                } else if (sessionManager.getWebSessionCount() > 0) {
                    sessionManager.removeWebSessionAt(sessionManager.getWebSessionCount() - 1);
                }
                return;
            }

            // Handle JS evaluation
            if (!TextUtils.isEmpty(evalJs)) {
                TermuxWebSession targetSession = null;
                if (activity != null && activity.getCurrentWebSession() != null) {
                    targetSession = activity.getCurrentWebSession();
                } else if (sessionManager.getWebSessionCount() > 0) {
                    targetSession = sessionManager.getWebSessionAt(0);
                }

                if (targetSession != null) {
                    targetSession.evaluateJavascript(evalJs, null);
                } else {
                    Logger.logWarn(LOG_TAG, "Cannot eval JS: No active web session found");
                }
                return;
            }

            // Handle URL or HTML loading
            if (TextUtils.isEmpty(url) && TextUtils.isEmpty(html)) {
                Logger.logWarn(LOG_TAG, "Neither URL nor HTML provided in web session dispatch");
                return;
            }

            TermuxWebSession webSession;
            Context appContext = context.getApplicationContext();

            if (!newSession && activity != null && activity.getCurrentWebSession() != null) {
                webSession = activity.getCurrentWebSession();
                if (!TextUtils.isEmpty(title)) {
                    webSession.setTitle(title);
                }
                if (!TextUtils.isEmpty(url)) {
                    webSession.loadUrl(url);
                } else {
                    webSession.loadHtml(html, null);
                }
            } else {
                webSession = sessionManager.createWebSession(appContext, url, title);
                if (!TextUtils.isEmpty(html)) {
                    webSession.loadHtml(html, null);
                }
            }

            if (activity != null && !activity.isFinishing()) {
                activity.showWebSession(webSession);
            } else {
                // Launch TermuxActivity to display the new web session
                Intent launchIntent = new Intent(appContext, TermuxActivity.class);
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                appContext.startActivity(launchIntent);
            }
        });
    }
}
