package com.termux.app;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.KeyEvent;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.view.animation.OvershootInterpolator;
import android.view.inputmethod.EditorInfo;
import com.termux.app.web.HoldProgressRingView;
import android.view.inputmethod.InputMethodManager;
import android.util.TypedValue;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.Toast;
import android.webkit.WebView;

import com.termux.R;
import com.termux.app.api.file.FileReceiverActivity;
import com.termux.app.terminal.TermuxActivityRootView;
import com.termux.app.terminal.TermuxTerminalSessionActivityClient;
import com.termux.app.terminal.io.TermuxTerminalExtraKeys;
import com.termux.app.web.TermuxWebSession;
import com.termux.app.web.TermuxWebSessionManager;
import com.termux.shared.activities.ReportActivity;
import com.termux.shared.activity.ActivityUtils;
import com.termux.shared.activity.media.AppCompatActivityUtils;
import com.termux.shared.data.IntentUtils;
import com.termux.shared.android.PermissionUtils;
import com.termux.shared.data.DataUtils;
import com.termux.shared.termux.TermuxConstants;
import com.termux.shared.termux.TermuxConstants.TERMUX_APP.TERMUX_ACTIVITY;
import com.termux.app.activities.HelpActivity;
import com.termux.app.activities.SettingsActivity;
import com.termux.shared.termux.crash.TermuxCrashUtils;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.app.terminal.TermuxSessionsListViewController;
import com.termux.app.terminal.io.TerminalToolbarViewPager;
import com.termux.app.terminal.TermuxTerminalViewClient;
import com.termux.shared.termux.extrakeys.ExtraKeysView;
import com.termux.shared.termux.interact.TextInputDialogUtils;
import com.termux.shared.logger.Logger;
import com.termux.shared.termux.TermuxUtils;
import com.termux.shared.termux.settings.properties.TermuxAppSharedProperties;
import com.termux.shared.termux.theme.TermuxThemeUtils;
import com.termux.shared.theme.NightMode;
import com.termux.shared.view.ViewUtils;
import com.termux.terminal.TerminalSession;
import com.termux.terminal.TerminalSessionClient;
import com.termux.view.TerminalView;
import com.termux.view.TerminalViewClient;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.viewpager.widget.ViewPager;

import java.util.Arrays;

/**
 * A terminal emulator activity.
 * <p/>
 * See
 * <ul>
 * <li>http://www.mongrel-phones.com.au/default/how_to_make_a_local_service_and_bind_to_it_in_android</li>
 * <li>https://code.google.com/p/android/issues/detail?id=6426</li>
 * </ul>
 * about memory leaks.
 */
public final class TermuxActivity extends AppCompatActivity implements ServiceConnection, TermuxWebSessionManager.WebSessionListListener {

    private static TermuxActivity sInstance;

    private FrameLayout mWebViewContainer;
    private TermuxWebSession mCurrentWebSession;
    private FrameLayout mWebViewControlsOverlay;
    private View mWebViewPillBar;
    private View mWebViewUrlBar;
    private FrameLayout mWebViewInfoContainer;
    private HoldProgressRingView mWebViewInfoRing;
    private ImageButton mWebViewInfoFab;
    private EditText mWebViewUrlEditText;
    private ImageButton mWebViewUrlGoButton;
    private ImageButton mWebViewBtnPrevious;
    private ImageButton mWebViewBtnNext;
    private ImageButton mWebViewBtnReload;
    private ImageButton mWebViewBtnLink;
    private ImageButton mWebViewBtnPanel;
    private ImageButton mWebViewBtnClose;

    /**
     * The connection to the {@link TermuxService}. Requested in {@link #onCreate(Bundle)} with a call to
     * {@link #bindService(Intent, ServiceConnection, int)}, and obtained and stored in
     * {@link #onServiceConnected(ComponentName, IBinder)}.
     */
    TermuxService mTermuxService;

    /**
     * The {@link TerminalView} shown in  {@link TermuxActivity} that displays the terminal.
     */
    TerminalView mTerminalView;

    /**
     *  The {@link TerminalViewClient} interface implementation to allow for communication between
     *  {@link TerminalView} and {@link TermuxActivity}.
     */
    TermuxTerminalViewClient mTermuxTerminalViewClient;

    /**
     *  The {@link TerminalSessionClient} interface implementation to allow for communication between
     *  {@link TerminalSession} and {@link TermuxActivity}.
     */
    TermuxTerminalSessionActivityClient mTermuxTerminalSessionActivityClient;

    /**
     * Termux app shared preferences manager.
     */
    private TermuxAppSharedPreferences mPreferences;

    /**
     * Termux app SharedProperties loaded from termux.properties
     */
    private TermuxAppSharedProperties mProperties;

    /**
     * The root view of the {@link TermuxActivity}.
     */
    TermuxActivityRootView mTermuxActivityRootView;

    /**
     * The space at the bottom of {@link @mTermuxActivityRootView} of the {@link TermuxActivity}.
     */
    View mTermuxActivityBottomSpaceView;

    /**
     * The terminal extra keys view.
     */
    ExtraKeysView mExtraKeysView;

    /**
     * The client for the {@link #mExtraKeysView}.
     */
    TermuxTerminalExtraKeys mTermuxTerminalExtraKeys;

    /**
     * The termux sessions list controller.
     */
    TermuxSessionsListViewController mTermuxSessionListViewController;

    /**
     * The {@link TermuxActivity} broadcast receiver for various things like terminal style configuration changes.
     */
    private final BroadcastReceiver mTermuxActivityBroadcastReceiver = new TermuxActivityBroadcastReceiver();

    /**
     * The last toast shown, used cancel current toast before showing new in {@link #showToast(String, boolean)}.
     */
    Toast mLastToast;

    /**
     * If between onResume() and onStop(). Note that only one session is in the foreground of the terminal view at the
     * time, so if the session causing a change is not in the foreground it should probably be treated as background.
     */
    private boolean mIsVisible;

    /**
     * If onResume() was called after onCreate().
     */
    private boolean mIsOnResumeAfterOnCreate = false;

    /**
     * If activity was restarted like due to call to {@link #recreate()} after receiving
     * {@link TERMUX_ACTIVITY#ACTION_RELOAD_STYLE}, system dark night mode was changed or activity
     * was killed by android.
     */
    private boolean mIsActivityRecreated = false;

    /**
     * The {@link TermuxActivity} is in an invalid state and must not be run.
     */
    private boolean mIsInvalidState;

    private int mNavBarHeight;

    private float mTerminalToolbarDefaultHeight;


    private static final int CONTEXT_MENU_SELECT_URL_ID = 0;
    private static final int CONTEXT_MENU_SHARE_TRANSCRIPT_ID = 1;
    private static final int CONTEXT_MENU_SHARE_SELECTED_TEXT = 10;
    private static final int CONTEXT_MENU_AUTOFILL_USERNAME = 11;
    private static final int CONTEXT_MENU_AUTOFILL_PASSWORD = 2;
    private static final int CONTEXT_MENU_RESET_TERMINAL_ID = 3;
    private static final int CONTEXT_MENU_KILL_PROCESS_ID = 4;
    private static final int CONTEXT_MENU_STYLING_ID = 5;
    private static final int CONTEXT_MENU_TOGGLE_KEEP_SCREEN_ON = 6;
    private static final int CONTEXT_MENU_HELP_ID = 7;
    private static final int CONTEXT_MENU_SETTINGS_ID = 8;
    private static final int CONTEXT_MENU_REPORT_ID = 9;

    private static final String ARG_TERMINAL_TOOLBAR_TEXT_INPUT = "terminal_toolbar_text_input";
    private static final String ARG_ACTIVITY_RECREATED = "activity_recreated";

    private static final String LOG_TAG = "TermuxActivity";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Logger.logDebug(LOG_TAG, "onCreate");
        mIsOnResumeAfterOnCreate = true;

        if (savedInstanceState != null)
            mIsActivityRecreated = savedInstanceState.getBoolean(ARG_ACTIVITY_RECREATED, false);

        // Delete ReportInfo serialized object files from cache older than 14 days
        ReportActivity.deleteReportInfoFilesOlderThanXDays(this, 14, false);

        // Load Termux app SharedProperties from disk
        mProperties = TermuxAppSharedProperties.getProperties();
        reloadProperties();

        setActivityTheme();

        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_termux);

        sInstance = this;
        TermuxWebSessionManager.getInstance().addListener(this);

        // Load termux shared preferences
        // This will also fail if TermuxConstants.TERMUX_PACKAGE_NAME does not equal applicationId
        mPreferences = TermuxAppSharedPreferences.build(this, true);
        if (mPreferences == null) {
            // An AlertDialog should have shown to kill the app, so we don't continue running activity code
            mIsInvalidState = true;
            return;
        }

        setMargins();

        mTermuxActivityRootView = findViewById(R.id.activity_termux_root_view);
        mTermuxActivityRootView.setActivity(this);
        mTermuxActivityBottomSpaceView = findViewById(R.id.activity_termux_bottom_space_view);
        mTermuxActivityRootView.setOnApplyWindowInsetsListener(new TermuxActivityRootView.WindowInsetsListener());

        View content = findViewById(android.R.id.content);
        content.setOnApplyWindowInsetsListener((v, insets) -> {
            mNavBarHeight = insets.getSystemWindowInsetBottom();
            return insets;
        });

        if (mProperties.isUsingFullScreen()) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        }

        setTermuxTerminalViewAndClients();

        setTerminalToolbarView(savedInstanceState);

        setSettingsButtonView();

        setNewSessionButtonView();

        setToggleKeyboardView();

        registerForContextMenu(mTerminalView);

        FileReceiverActivity.updateFileReceiverActivityComponentsState(this);

        try {
            // Start the {@link TermuxService} and make it run regardless of who is bound to it
            Intent serviceIntent = new Intent(this, TermuxService.class);
            startService(serviceIntent);

            // Attempt to bind to the service, this will call the {@link #onServiceConnected(ComponentName, IBinder)}
            // callback if it succeeds.
            if (!bindService(serviceIntent, this, 0))
                throw new RuntimeException("bindService() failed");
        } catch (Exception e) {
            Logger.logStackTraceWithMessage(LOG_TAG,"TermuxActivity failed to start TermuxService", e);
            Logger.showToast(this,
                getString(e.getMessage() != null && e.getMessage().contains("app is in background") ?
                    R.string.error_termux_service_start_failed_bg : R.string.error_termux_service_start_failed_general),
                true);
            mIsInvalidState = true;
            return;
        }

        // Send the {@link TermuxConstants#BROADCAST_TERMUX_OPENED} broadcast to notify apps that Termux
        // app has been opened.
        TermuxUtils.sendTermuxOpenedBroadcast(this);
    }

    @Override
    public void onStart() {
        super.onStart();

        Logger.logDebug(LOG_TAG, "onStart");

        if (mIsInvalidState) return;

        mIsVisible = true;

        if (mTermuxTerminalSessionActivityClient != null)
            mTermuxTerminalSessionActivityClient.onStart();

        if (mTermuxTerminalViewClient != null)
            mTermuxTerminalViewClient.onStart();

        if (mPreferences.isTerminalMarginAdjustmentEnabled())
            addTermuxActivityRootViewGlobalLayoutListener();

        registerTermuxActivityBroadcastReceiver();
    }

    @Override
    public void onResume() {
        super.onResume();

        Logger.logVerbose(LOG_TAG, "onResume");

        if (mIsInvalidState) return;

        if (mTermuxTerminalSessionActivityClient != null)
            mTermuxTerminalSessionActivityClient.onResume();

        if (mTermuxTerminalViewClient != null)
            mTermuxTerminalViewClient.onResume();

        // Check if a crash happened on last run of the app or if a plugin crashed and show a
        // notification with the crash details if it did
        TermuxCrashUtils.notifyAppCrashFromCrashLogFile(this, LOG_TAG);

        mIsOnResumeAfterOnCreate = false;
    }

    @Override
    protected void onStop() {
        super.onStop();

        Logger.logDebug(LOG_TAG, "onStop");

        if (mIsInvalidState) return;

        mIsVisible = false;

        if (mTermuxTerminalSessionActivityClient != null)
            mTermuxTerminalSessionActivityClient.onStop();

        if (mTermuxTerminalViewClient != null)
            mTermuxTerminalViewClient.onStop();

        removeTermuxActivityRootViewGlobalLayoutListener();

        unregisterTermuxActivityBroadcastReceiver();
        getDrawer().closeDrawers();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        TermuxWebSessionManager.getInstance().removeListener(this);
        if (sInstance == this) {
            sInstance = null;
        }

        Logger.logDebug(LOG_TAG, "onDestroy");

        if (mIsInvalidState) return;

        if (mTermuxService != null) {
            // Do not leave service and session clients with references to activity.
            mTermuxService.unsetTermuxTerminalSessionClient();
            mTermuxService = null;
        }

        try {
            unbindService(this);
        } catch (Exception e) {
            // ignore.
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle savedInstanceState) {
        Logger.logVerbose(LOG_TAG, "onSaveInstanceState");

        super.onSaveInstanceState(savedInstanceState);
        saveTerminalToolbarTextInput(savedInstanceState);
        savedInstanceState.putBoolean(ARG_ACTIVITY_RECREATED, true);
    }





    /**
     * Part of the {@link ServiceConnection} interface. The service is bound with
     * {@link #bindService(Intent, ServiceConnection, int)} in {@link #onCreate(Bundle)} which will cause a call to this
     * callback method.
     */
    @Override
    public void onServiceConnected(ComponentName componentName, IBinder service) {
        Logger.logDebug(LOG_TAG, "onServiceConnected");

        mTermuxService = ((TermuxService.LocalBinder) service).service;

        setTermuxSessionsListView();

        final Intent intent = getIntent();
        setIntent(null);

        if (mTermuxService.isTermuxSessionsEmpty()) {
            if (mIsVisible) {
                TermuxInstaller.setupBootstrapIfNeeded(TermuxActivity.this, () -> {
                    if (mTermuxService == null) return; // Activity might have been destroyed.
                    try {
                        boolean launchFailsafe = false;
                        if (intent != null && intent.getExtras() != null) {
                            launchFailsafe = intent.getExtras().getBoolean(TERMUX_ACTIVITY.EXTRA_FAILSAFE_SESSION, false);
                        }
                        mTermuxTerminalSessionActivityClient.addNewSession(launchFailsafe, null);
                    } catch (WindowManager.BadTokenException e) {
                        // Activity finished - ignore.
                    }
                });
            } else {
                // The service connected while not in foreground - just bail out.
                finishActivityIfNotFinishing();
            }
        } else {
            // If termux was started from launcher "New session" shortcut and activity is recreated,
            // then the original intent will be re-delivered, resulting in a new session being re-added
            // each time.
            if (!mIsActivityRecreated && intent != null && Intent.ACTION_RUN.equals(intent.getAction())) {
                // Android 7.1 app shortcut from res/xml/shortcuts.xml.
                boolean isFailSafe = intent.getBooleanExtra(TERMUX_ACTIVITY.EXTRA_FAILSAFE_SESSION, false);
                mTermuxTerminalSessionActivityClient.addNewSession(isFailSafe, null);
            } else {
                mTermuxTerminalSessionActivityClient.setCurrentSession(mTermuxTerminalSessionActivityClient.getCurrentStoredSessionOrLast());
            }
        }

        // Update the {@link TerminalSession} and {@link TerminalEmulator} clients.
        mTermuxService.setTermuxTerminalSessionClient(mTermuxTerminalSessionActivityClient);
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        Logger.logDebug(LOG_TAG, "onServiceDisconnected");

        // Respect being stopped from the {@link TermuxService} notification action.
        finishActivityIfNotFinishing();
    }






    private void reloadProperties() {
        mProperties.loadTermuxPropertiesFromDisk();

        if (mTermuxTerminalViewClient != null)
            mTermuxTerminalViewClient.onReloadProperties();
    }



    private void setActivityTheme() {
        // Update NightMode.APP_NIGHT_MODE
        TermuxThemeUtils.setAppNightMode(mProperties.getNightMode());

        // Set activity night mode. If NightMode.SYSTEM is set, then android will automatically
        // trigger recreation of activity when uiMode/dark mode configuration is changed so that
        // day or night theme takes affect.
        AppCompatActivityUtils.setNightMode(this, NightMode.getAppNightMode().getName(), true);
    }

    private void setMargins() {
        RelativeLayout relativeLayout = findViewById(R.id.activity_termux_root_relative_layout);
        int marginHorizontal = mProperties.getTerminalMarginHorizontal();
        int marginVertical = mProperties.getTerminalMarginVertical();
        ViewUtils.setLayoutMarginsInDp(relativeLayout, marginHorizontal, marginVertical, marginHorizontal, marginVertical);
    }



    public void addTermuxActivityRootViewGlobalLayoutListener() {
        getTermuxActivityRootView().getViewTreeObserver().addOnGlobalLayoutListener(getTermuxActivityRootView());
    }

    public void removeTermuxActivityRootViewGlobalLayoutListener() {
        if (getTermuxActivityRootView() != null)
            getTermuxActivityRootView().getViewTreeObserver().removeOnGlobalLayoutListener(getTermuxActivityRootView());
    }



    private void setTermuxTerminalViewAndClients() {
        // Set termux terminal view and session clients
        mTermuxTerminalSessionActivityClient = new TermuxTerminalSessionActivityClient(this);
        mTermuxTerminalViewClient = new TermuxTerminalViewClient(this, mTermuxTerminalSessionActivityClient);

        // Set termux terminal view
        mTerminalView = findViewById(R.id.terminal_view);
        mTerminalView.setTerminalViewClient(mTermuxTerminalViewClient);

        mWebViewContainer = findViewById(R.id.web_view_container);
        setupWebViewControls();

        if (mTermuxTerminalViewClient != null)
            mTermuxTerminalViewClient.onCreate();

        if (mTermuxTerminalSessionActivityClient != null)
            mTermuxTerminalSessionActivityClient.onCreate();
    }

    private void setTermuxSessionsListView() {
        ListView termuxSessionsListView = findViewById(R.id.terminal_sessions_list);
        mTermuxSessionListViewController = new TermuxSessionsListViewController(this, mTermuxService.getTermuxSessions());
        termuxSessionsListView.setAdapter(mTermuxSessionListViewController);
        termuxSessionsListView.setOnItemClickListener(mTermuxSessionListViewController);
        termuxSessionsListView.setOnItemLongClickListener(mTermuxSessionListViewController);
    }



    private void setTerminalToolbarView(Bundle savedInstanceState) {
        mTermuxTerminalExtraKeys = new TermuxTerminalExtraKeys(this, mTerminalView,
            mTermuxTerminalViewClient, mTermuxTerminalSessionActivityClient);

        final ViewPager terminalToolbarViewPager = getTerminalToolbarViewPager();
        if (mPreferences.shouldShowTerminalToolbar()) terminalToolbarViewPager.setVisibility(View.VISIBLE);

        ViewGroup.LayoutParams layoutParams = terminalToolbarViewPager.getLayoutParams();
        mTerminalToolbarDefaultHeight = layoutParams.height;

        setTerminalToolbarHeight();

        String savedTextInput = null;
        if (savedInstanceState != null)
            savedTextInput = savedInstanceState.getString(ARG_TERMINAL_TOOLBAR_TEXT_INPUT);

        terminalToolbarViewPager.setAdapter(new TerminalToolbarViewPager.PageAdapter(this, savedTextInput));
        terminalToolbarViewPager.addOnPageChangeListener(new TerminalToolbarViewPager.OnPageChangeListener(this, terminalToolbarViewPager));
    }

    private void setTerminalToolbarHeight() {
        final ViewPager terminalToolbarViewPager = getTerminalToolbarViewPager();
        if (terminalToolbarViewPager == null) return;

        ViewGroup.LayoutParams layoutParams = terminalToolbarViewPager.getLayoutParams();
        layoutParams.height = Math.round(mTerminalToolbarDefaultHeight *
            (mTermuxTerminalExtraKeys.getExtraKeysInfo() == null ? 0 : mTermuxTerminalExtraKeys.getExtraKeysInfo().getMatrix().length) *
            mProperties.getTerminalToolbarHeightScaleFactor());
        terminalToolbarViewPager.setLayoutParams(layoutParams);
    }

    public void toggleTerminalToolbar() {
        final ViewPager terminalToolbarViewPager = getTerminalToolbarViewPager();
        if (terminalToolbarViewPager == null) return;

        final boolean showNow = mPreferences.toogleShowTerminalToolbar();
        Logger.showToast(this, (showNow ? getString(R.string.msg_enabling_terminal_toolbar) : getString(R.string.msg_disabling_terminal_toolbar)), true);
        terminalToolbarViewPager.setVisibility(showNow ? View.VISIBLE : View.GONE);
        if (showNow && isTerminalToolbarTextInputViewSelected()) {
            // Focus the text input view if just revealed.
            findViewById(R.id.terminal_toolbar_text_input).requestFocus();
        }
    }

    private void saveTerminalToolbarTextInput(Bundle savedInstanceState) {
        if (savedInstanceState == null) return;

        final EditText textInputView = findViewById(R.id.terminal_toolbar_text_input);
        if (textInputView != null) {
            String textInput = textInputView.getText().toString();
            if (!textInput.isEmpty()) savedInstanceState.putString(ARG_TERMINAL_TOOLBAR_TEXT_INPUT, textInput);
        }
    }



    private void setSettingsButtonView() {
        ImageButton settingsButton = findViewById(R.id.settings_button);
        settingsButton.setOnClickListener(v -> {
            ActivityUtils.startActivity(this, new Intent(this, SettingsActivity.class));
        });
    }

    private void setNewSessionButtonView() {
        View newSessionButton = findViewById(R.id.new_session_button);
        newSessionButton.setOnClickListener(v -> mTermuxTerminalSessionActivityClient.addNewSession(false, null));
        newSessionButton.setOnLongClickListener(v -> {
            TextInputDialogUtils.textInput(TermuxActivity.this, R.string.title_create_named_session, null,
                R.string.action_create_named_session_confirm, text -> mTermuxTerminalSessionActivityClient.addNewSession(false, text),
                R.string.action_new_session_failsafe, text -> mTermuxTerminalSessionActivityClient.addNewSession(true, text),
                -1, null, null);
            return true;
        });
    }

    private void setToggleKeyboardView() {
        findViewById(R.id.toggle_keyboard_button).setOnClickListener(v -> {
            mTermuxTerminalViewClient.onToggleSoftKeyboardRequest();
            getDrawer().closeDrawers();
        });

        findViewById(R.id.toggle_keyboard_button).setOnLongClickListener(v -> {
            toggleTerminalToolbar();
            return true;
        });
    }





    @SuppressLint("RtlHardcoded")
    @Override
    public void onBackPressed() {
        if (getDrawer().isDrawerOpen(Gravity.LEFT)) {
            getDrawer().closeDrawers();
        } else if (isWebViewUrlBarVisible()) {
            hideWebViewUrlBar();
        } else if (isWebViewPillBarVisible()) {
            collapseWebViewPillBar();
        } else if (mCurrentWebSession != null && mCurrentWebSession.canGoBack()) {
            mCurrentWebSession.goBack();
        } else if (mCurrentWebSession != null) {
            showTerminalView();
        } else {
            finishActivityIfNotFinishing();
        }
    }

    public void finishActivityIfNotFinishing() {
        // prevent duplicate calls to finish() if called from multiple places
        if (!TermuxActivity.this.isFinishing()) {
            finish();
        }
    }

    /** Show a toast and dismiss the last one if still visible. */
    public void showToast(String text, boolean longDuration) {
        if (text == null || text.isEmpty()) return;
        if (mLastToast != null) mLastToast.cancel();
        mLastToast = Toast.makeText(TermuxActivity.this, text, longDuration ? Toast.LENGTH_LONG : Toast.LENGTH_SHORT);
        mLastToast.setGravity(Gravity.TOP, 0, 0);
        mLastToast.show();
    }



    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        TerminalSession currentSession = getCurrentSession();
        if (currentSession == null) return;

        boolean autoFillEnabled = mTerminalView.isAutoFillEnabled();

        menu.add(Menu.NONE, CONTEXT_MENU_SELECT_URL_ID, Menu.NONE, R.string.action_select_url);
        menu.add(Menu.NONE, CONTEXT_MENU_SHARE_TRANSCRIPT_ID, Menu.NONE, R.string.action_share_transcript);
        if (!DataUtils.isNullOrEmpty(mTerminalView.getStoredSelectedText()))
            menu.add(Menu.NONE, CONTEXT_MENU_SHARE_SELECTED_TEXT, Menu.NONE, R.string.action_share_selected_text);
        if (autoFillEnabled)
            menu.add(Menu.NONE, CONTEXT_MENU_AUTOFILL_USERNAME, Menu.NONE, R.string.action_autofill_username);
        if (autoFillEnabled)
            menu.add(Menu.NONE, CONTEXT_MENU_AUTOFILL_PASSWORD, Menu.NONE, R.string.action_autofill_password);
        menu.add(Menu.NONE, CONTEXT_MENU_RESET_TERMINAL_ID, Menu.NONE, R.string.action_reset_terminal);
        menu.add(Menu.NONE, CONTEXT_MENU_KILL_PROCESS_ID, Menu.NONE, getResources().getString(R.string.action_kill_process, getCurrentSession().getPid())).setEnabled(currentSession.isRunning());
        menu.add(Menu.NONE, CONTEXT_MENU_STYLING_ID, Menu.NONE, R.string.action_style_terminal);
        menu.add(Menu.NONE, CONTEXT_MENU_TOGGLE_KEEP_SCREEN_ON, Menu.NONE, R.string.action_toggle_keep_screen_on).setCheckable(true).setChecked(mPreferences.shouldKeepScreenOn());
        menu.add(Menu.NONE, CONTEXT_MENU_HELP_ID, Menu.NONE, R.string.action_open_help);
        menu.add(Menu.NONE, CONTEXT_MENU_SETTINGS_ID, Menu.NONE, R.string.action_open_settings);
        menu.add(Menu.NONE, CONTEXT_MENU_REPORT_ID, Menu.NONE, R.string.action_report_issue);
    }

    /** Hook system menu to show context menu instead. */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        mTerminalView.showContextMenu();
        return false;
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        TerminalSession session = getCurrentSession();

        switch (item.getItemId()) {
            case CONTEXT_MENU_SELECT_URL_ID:
                mTermuxTerminalViewClient.showUrlSelection();
                return true;
            case CONTEXT_MENU_SHARE_TRANSCRIPT_ID:
                mTermuxTerminalViewClient.shareSessionTranscript();
                return true;
            case CONTEXT_MENU_SHARE_SELECTED_TEXT:
                mTermuxTerminalViewClient.shareSelectedText();
                return true;
            case CONTEXT_MENU_AUTOFILL_USERNAME:
                mTerminalView.requestAutoFillUsername();
                return true;
            case CONTEXT_MENU_AUTOFILL_PASSWORD:
                mTerminalView.requestAutoFillPassword();
                return true;
            case CONTEXT_MENU_RESET_TERMINAL_ID:
                onResetTerminalSession(session);
                return true;
            case CONTEXT_MENU_KILL_PROCESS_ID:
                showKillSessionDialog(session);
                return true;
            case CONTEXT_MENU_STYLING_ID:
                showStylingDialog();
                return true;
            case CONTEXT_MENU_TOGGLE_KEEP_SCREEN_ON:
                toggleKeepScreenOn();
                return true;
            case CONTEXT_MENU_HELP_ID:
                ActivityUtils.startActivity(this, new Intent(this, HelpActivity.class));
                return true;
            case CONTEXT_MENU_SETTINGS_ID:
                ActivityUtils.startActivity(this, new Intent(this, SettingsActivity.class));
                return true;
            case CONTEXT_MENU_REPORT_ID:
                mTermuxTerminalViewClient.reportIssueFromTranscript();
                return true;
            default:
                return super.onContextItemSelected(item);
        }
    }

    @Override
    public void onContextMenuClosed(Menu menu) {
        super.onContextMenuClosed(menu);
        // onContextMenuClosed() is triggered twice if back button is pressed to dismiss instead of tap for some reason
        mTerminalView.onContextMenuClosed(menu);
    }

    private void showKillSessionDialog(TerminalSession session) {
        if (session == null) return;

        final AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setIcon(android.R.drawable.ic_dialog_alert);
        b.setMessage(R.string.title_confirm_kill_process);
        b.setPositiveButton(android.R.string.yes, (dialog, id) -> {
            dialog.dismiss();
            session.finishIfRunning();
        });
        b.setNegativeButton(android.R.string.no, null);
        b.show();
    }

    private void onResetTerminalSession(TerminalSession session) {
        if (session != null) {
            session.reset();
            showToast(getResources().getString(R.string.msg_terminal_reset), true);

            if (mTermuxTerminalSessionActivityClient != null)
                mTermuxTerminalSessionActivityClient.onResetTerminalSession();
        }
    }

    private void showStylingDialog() {
        Intent stylingIntent = new Intent();
        stylingIntent.setClassName(TermuxConstants.TERMUX_STYLING_PACKAGE_NAME, TermuxConstants.TERMUX_STYLING_APP.TERMUX_STYLING_ACTIVITY_NAME);
        try {
            startActivity(stylingIntent);
        } catch (ActivityNotFoundException | IllegalArgumentException e) {
            // The startActivity() call is not documented to throw IllegalArgumentException.
            // However, crash reporting shows that it sometimes does, so catch it here.
            new AlertDialog.Builder(this).setMessage(getString(R.string.error_styling_not_installed))
                .setPositiveButton(R.string.action_styling_install,
                    (dialog, which) -> ActivityUtils.startActivity(this, new Intent(Intent.ACTION_VIEW, Uri.parse(TermuxConstants.TERMUX_STYLING_FDROID_PACKAGE_URL))))
                .setNegativeButton(android.R.string.cancel, null).show();
        }
    }
    private void toggleKeepScreenOn() {
        if (mTerminalView.getKeepScreenOn()) {
            mTerminalView.setKeepScreenOn(false);
            mPreferences.setKeepScreenOn(false);
        } else {
            mTerminalView.setKeepScreenOn(true);
            mPreferences.setKeepScreenOn(true);
        }
    }



    /**
     * For processes to access primary external storage (/sdcard, /storage/emulated/0, ~/storage/shared),
     * termux needs to be granted legacy WRITE_EXTERNAL_STORAGE or MANAGE_EXTERNAL_STORAGE permissions
     * if targeting targetSdkVersion 30 (android 11) and running on sdk 30 (android 11) and higher.
     */
    public void requestStoragePermission(boolean isPermissionCallback) {
        new Thread() {
            @Override
            public void run() {
                // Do not ask for permission again
                int requestCode = isPermissionCallback ? -1 : PermissionUtils.REQUEST_GRANT_STORAGE_PERMISSION;

                // If permission is granted, then also setup storage symlinks.
                if(PermissionUtils.checkAndRequestLegacyOrManageExternalStoragePermission(
                    TermuxActivity.this, requestCode, !isPermissionCallback)) {
                    if (isPermissionCallback)
                        Logger.logInfoAndShowToast(TermuxActivity.this, LOG_TAG,
                            getString(com.termux.shared.R.string.msg_storage_permission_granted_on_request));

                    TermuxInstaller.setupStorageSymlinks(TermuxActivity.this);
                } else {
                    if (isPermissionCallback)
                        Logger.logInfoAndShowToast(TermuxActivity.this, LOG_TAG,
                            getString(com.termux.shared.R.string.msg_storage_permission_not_granted_on_request));
                }
            }
        }.start();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Logger.logVerbose(LOG_TAG, "onActivityResult: requestCode: " + requestCode + ", resultCode: "  + resultCode + ", data: "  + IntentUtils.getIntentString(data));
        if (requestCode == PermissionUtils.REQUEST_GRANT_STORAGE_PERMISSION) {
            requestStoragePermission(true);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        Logger.logVerbose(LOG_TAG, "onRequestPermissionsResult: requestCode: " + requestCode + ", permissions: "  + Arrays.toString(permissions) + ", grantResults: "  + Arrays.toString(grantResults));
        if (requestCode == PermissionUtils.REQUEST_GRANT_STORAGE_PERMISSION) {
            requestStoragePermission(true);
        }
    }



    public int getNavBarHeight() {
        return mNavBarHeight;
    }

    public TermuxActivityRootView getTermuxActivityRootView() {
        return mTermuxActivityRootView;
    }

    public View getTermuxActivityBottomSpaceView() {
        return mTermuxActivityBottomSpaceView;
    }

    public ExtraKeysView getExtraKeysView() {
        return mExtraKeysView;
    }

    public TermuxTerminalExtraKeys getTermuxTerminalExtraKeys() {
        return mTermuxTerminalExtraKeys;
    }

    public void setExtraKeysView(ExtraKeysView extraKeysView) {
        mExtraKeysView = extraKeysView;
    }

    public DrawerLayout getDrawer() {
        return (DrawerLayout) findViewById(R.id.drawer_layout);
    }


    public ViewPager getTerminalToolbarViewPager() {
        return (ViewPager) findViewById(R.id.terminal_toolbar_view_pager);
    }

    public float getTerminalToolbarDefaultHeight() {
        return mTerminalToolbarDefaultHeight;
    }

    public boolean isTerminalViewSelected() {
        return getTerminalToolbarViewPager().getCurrentItem() == 0;
    }

    public boolean isTerminalToolbarTextInputViewSelected() {
        return getTerminalToolbarViewPager().getCurrentItem() == 1;
    }


    public void termuxSessionListNotifyUpdated() {
        if (mTermuxSessionListViewController != null) {
            mTermuxSessionListViewController.notifyDataSetChanged();
        }
    }

    public boolean isVisible() {
        return mIsVisible;
    }

    public boolean isOnResumeAfterOnCreate() {
        return mIsOnResumeAfterOnCreate;
    }

    public boolean isActivityRecreated() {
        return mIsActivityRecreated;
    }



    public TermuxService getTermuxService() {
        return mTermuxService;
    }

    public static TermuxActivity getActivity() {
        return sInstance;
    }

    public FrameLayout getWebViewContainer() {
        return mWebViewContainer;
    }

    public TermuxWebSession getCurrentWebSession() {
        return mCurrentWebSession;
    }

    public boolean isWebSessionActive() {
        return mCurrentWebSession != null;
    }

    public void showWebSession(@NonNull final TermuxWebSession webSession) {
        runOnUiThread(() -> {
            mCurrentWebSession = webSession;
            if (mWebViewContainer != null) {
                mWebViewContainer.removeAllViews();
                WebView wv = webSession.getWebView();
                if (wv != null) {
                    if (wv.getParent() instanceof ViewGroup) {
                        ((ViewGroup) wv.getParent()).removeView(wv);
                    }
                    mWebViewContainer.addView(wv, new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                }
                mWebViewContainer.setVisibility(View.VISIBLE);
                mWebViewContainer.bringToFront();
                if (wv != null) {
                    wv.requestFocus();
                }
            }
            if (mWebViewControlsOverlay != null) {
                mWebViewControlsOverlay.setVisibility(View.VISIBLE);
                mWebViewControlsOverlay.bringToFront();
                if (mWebViewInfoContainer != null) {
                    mWebViewInfoContainer.setVisibility(View.VISIBLE);
                    mWebViewInfoContainer.setScaleX(1f);
                    mWebViewInfoContainer.setScaleY(1f);
                    mWebViewInfoContainer.setAlpha(1f);
                } else if (mWebViewInfoFab != null) {
                    mWebViewInfoFab.setVisibility(View.VISIBLE);
                    mWebViewInfoFab.setScaleX(1f);
                    mWebViewInfoFab.setScaleY(1f);
                    mWebViewInfoFab.setAlpha(1f);
                }
                if (mWebViewInfoRing != null) {
                    mWebViewInfoRing.setVisibility(View.INVISIBLE);
                    mWebViewInfoRing.reset();
                }
                if (mWebViewPillBar != null) {
                    mWebViewPillBar.setVisibility(View.GONE);
                }
                if (mWebViewUrlBar != null) {
                    mWebViewUrlBar.setVisibility(View.GONE);
                }
            }
            if (mTerminalView != null) {
                mTerminalView.setVisibility(View.GONE);
            }
            final ViewPager terminalToolbarViewPager = getTerminalToolbarViewPager();
            if (terminalToolbarViewPager != null) {
                terminalToolbarViewPager.setVisibility(View.GONE);
            }

            DrawerLayout drawer = getDrawer();
            if (drawer != null) {
                drawer.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED, Gravity.LEFT);
            }
        });
    }

    public void showTerminalView() {
        runOnUiThread(() -> {
            mCurrentWebSession = null;
            if (mWebViewControlsOverlay != null) {
                hideWebViewUrlBar();
                mWebViewControlsOverlay.setVisibility(View.GONE);
                if (mWebViewPillBar != null) mWebViewPillBar.setVisibility(View.GONE);
                if (mWebViewUrlBar != null) mWebViewUrlBar.setVisibility(View.GONE);
            }
            if (mWebViewContainer != null) {
                mWebViewContainer.setVisibility(View.GONE);
                mWebViewContainer.removeAllViews();
            }
            if (mTerminalView != null) {
                mTerminalView.setVisibility(View.VISIBLE);
                mTerminalView.bringToFront();
                mTerminalView.requestFocus();
            }
            final ViewPager terminalToolbarViewPager = getTerminalToolbarViewPager();
            if (terminalToolbarViewPager != null && mPreferences != null && mPreferences.shouldShowTerminalToolbar()) {
                terminalToolbarViewPager.setVisibility(View.VISIBLE);
            }

            DrawerLayout drawer = getDrawer();
            if (drawer != null) {
                drawer.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED, Gravity.LEFT);
            }
        });
    }

    private void setupWebViewControls() {
        mWebViewControlsOverlay = findViewById(R.id.web_view_controls_overlay);
        mWebViewPillBar = findViewById(R.id.web_view_pill_bar);
        mWebViewUrlBar = findViewById(R.id.web_view_url_bar);
        mWebViewInfoContainer = findViewById(R.id.web_view_info_container);
        mWebViewInfoRing = findViewById(R.id.web_view_info_ring);
        mWebViewInfoFab = findViewById(R.id.web_view_info_fab);
        mWebViewUrlEditText = findViewById(R.id.web_view_url_edit_text);
        mWebViewUrlGoButton = findViewById(R.id.web_view_url_go_button);
        mWebViewBtnPrevious = findViewById(R.id.web_view_btn_previous);
        mWebViewBtnNext = findViewById(R.id.web_view_btn_next);
        mWebViewBtnReload = findViewById(R.id.web_view_btn_reload);
        mWebViewBtnLink = findViewById(R.id.web_view_btn_link);
        mWebViewBtnPanel = findViewById(R.id.web_view_btn_panel);
        mWebViewBtnClose = findViewById(R.id.web_view_btn_close);

        DrawerLayout drawer = getDrawer();
        if (drawer != null) {
            drawer.addDrawerListener(new DrawerLayout.SimpleDrawerListener() {
                @Override
                public void onDrawerClosed(@NonNull View drawerView) {
                    if (isWebSessionActive()) {
                        drawer.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED, Gravity.LEFT);
                    }
                }

                @Override
                public void onDrawerOpened(@NonNull View drawerView) {
                    if (isWebSessionActive()) {
                        drawer.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED, Gravity.LEFT);
                    }
                }
            });
        }

        if (mWebViewInfoFab != null) {
            final int HOLD_DURATION_MS = 800;
            final float[] downCoords = new float[2];
            final boolean[] holdCompleted = new boolean[1];
            final ValueAnimator[] holdAnimator = new ValueAnimator[1];

            mWebViewInfoFab.setOnTouchListener((v, event) -> {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        downCoords[0] = event.getRawX();
                        downCoords[1] = event.getRawY();
                        holdCompleted[0] = false;

                        v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                        v.animate().scaleX(0.92f).scaleY(0.92f).setDuration(100).start();

                        if (mWebViewInfoRing != null) {
                            mWebViewInfoRing.setVisibility(View.VISIBLE);
                            mWebViewInfoRing.setAlpha(1f);
                            mWebViewInfoRing.setProgress(0f);
                        }

                        if (holdAnimator[0] != null) {
                            holdAnimator[0].cancel();
                        }

                        holdAnimator[0] = ValueAnimator.ofFloat(0f, 1f);
                        holdAnimator[0].setDuration(HOLD_DURATION_MS);
                        holdAnimator[0].setInterpolator(new LinearInterpolator());
                        holdAnimator[0].addUpdateListener(anim -> {
                            float progress = (float) anim.getAnimatedValue();
                            if (mWebViewInfoRing != null) {
                                mWebViewInfoRing.setProgress(progress);
                            }
                        });
                        holdAnimator[0].addListener(new AnimatorListenerAdapter() {
                            private boolean mCanceled = false;

                            @Override
                            public void onAnimationCancel(Animator animation) {
                                mCanceled = true;
                            }

                            @Override
                            public void onAnimationEnd(Animator animation) {
                                if (!mCanceled && !holdCompleted[0]) {
                                    holdCompleted[0] = true;
                                    v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                                    v.animate().scaleX(1.15f).scaleY(1.15f).setDuration(120)
                                        .withEndAction(() -> v.animate().scaleX(1f).scaleY(1f).setDuration(100).start())
                                        .start();

                                    if (mWebViewInfoRing != null) {
                                        mWebViewInfoRing.animate().alpha(0f).setDuration(160)
                                            .withEndAction(() -> {
                                                if (mWebViewInfoRing != null) {
                                                    mWebViewInfoRing.setVisibility(View.INVISIBLE);
                                                    mWebViewInfoRing.reset();
                                                }
                                            }).start();
                                    }

                                    DrawerLayout drawerLayout = getDrawer();
                                    if (drawerLayout != null) {
                                        drawerLayout.openDrawer(Gravity.LEFT);
                                    }
                                }
                            }
                        });
                        holdAnimator[0].start();
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        float dx = Math.abs(event.getRawX() - downCoords[0]);
                        float dy = Math.abs(event.getRawY() - downCoords[1]);
                        float touchSlop = ViewConfiguration.get(this).getScaledTouchSlop() * 1.5f;
                        if (dx > touchSlop || dy > touchSlop) {
                            if (holdAnimator[0] != null) {
                                holdAnimator[0].cancel();
                                holdAnimator[0] = null;
                            }
                            v.animate().scaleX(1f).scaleY(1f).setDuration(100).start();
                            if (mWebViewInfoRing != null) {
                                mWebViewInfoRing.animate().alpha(0f).setDuration(150)
                                    .withEndAction(() -> {
                                        if (mWebViewInfoRing != null) {
                                            mWebViewInfoRing.setVisibility(View.INVISIBLE);
                                            mWebViewInfoRing.reset();
                                        }
                                    }).start();
                            }
                        }
                        return true;

                    case MotionEvent.ACTION_UP:
                        v.animate().scaleX(1f).scaleY(1f).setDuration(100).start();
                        if (holdAnimator[0] != null && holdAnimator[0].isRunning()) {
                            long playTime = holdAnimator[0].getCurrentPlayTime();
                            holdAnimator[0].cancel();
                            holdAnimator[0] = null;

                            if (mWebViewInfoRing != null) {
                                mWebViewInfoRing.animate().alpha(0f).setDuration(150)
                                    .withEndAction(() -> {
                                        if (mWebViewInfoRing != null) {
                                            mWebViewInfoRing.setVisibility(View.INVISIBLE);
                                            mWebViewInfoRing.reset();
                                        }
                                    }).start();
                            }

                            // Quick tap (< 350ms): expand the pill bar!
                            if (!holdCompleted[0] && playTime < 350) {
                                expandWebViewPillBar();
                            }
                        }
                        return true;

                    case MotionEvent.ACTION_CANCEL:
                        if (holdAnimator[0] != null) {
                            holdAnimator[0].cancel();
                            holdAnimator[0] = null;
                        }
                        v.animate().scaleX(1f).scaleY(1f).setDuration(100).start();
                        if (mWebViewInfoRing != null) {
                            mWebViewInfoRing.setVisibility(View.INVISIBLE);
                            mWebViewInfoRing.reset();
                        }
                        return true;
                }
                return false;
            });
        }

        if (mWebViewBtnClose != null) {
            mWebViewBtnClose.setOnClickListener(v -> collapseWebViewPillBar());
        }

        if (mWebViewBtnPrevious != null) {
            mWebViewBtnPrevious.setOnClickListener(v -> {
                animateButtonBounce(v);
                if (mCurrentWebSession != null && mCurrentWebSession.canGoBack()) {
                    mCurrentWebSession.goBack();
                }
            });
        }

        if (mWebViewBtnNext != null) {
            mWebViewBtnNext.setOnClickListener(v -> {
                animateButtonBounce(v);
                if (mCurrentWebSession != null && mCurrentWebSession.canGoForward()) {
                    mCurrentWebSession.goForward();
                }
            });
        }

        if (mWebViewBtnReload != null) {
            mWebViewBtnReload.setOnClickListener(v -> {
                v.animate().rotationBy(360f).setDuration(400)
                    .setInterpolator(new DecelerateInterpolator()).start();
                if (mCurrentWebSession != null) {
                    mCurrentWebSession.reload();
                }
            });
        }

        if (mWebViewBtnPanel != null) {
            mWebViewBtnPanel.setOnClickListener(v -> {
                animateButtonBounce(v);
                if (getDrawer() != null) {
                    if (getDrawer().isDrawerOpen(Gravity.LEFT)) {
                        getDrawer().closeDrawers();
                    } else {
                        getDrawer().openDrawer(Gravity.LEFT);
                    }
                }
            });
        }

        if (mWebViewBtnLink != null) {
            mWebViewBtnLink.setOnClickListener(v -> {
                animateButtonBounce(v);
                toggleWebViewUrlBar();
            });
        }

        if (mWebViewUrlGoButton != null) {
            mWebViewUrlGoButton.setOnClickListener(v -> {
                animateButtonBounce(v);
                navigateToEnteredUrl();
            });
        }

        if (mWebViewUrlEditText != null) {
            mWebViewUrlEditText.setOnEditorActionListener((v, actionId, event) -> {
                if (actionId == EditorInfo.IME_ACTION_GO ||
                    (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_DOWN)) {
                    navigateToEnteredUrl();
                    return true;
                }
                return false;
            });
        }
    }

    private void expandWebViewPillBar() {
        View target = mWebViewInfoContainer != null ? mWebViewInfoContainer : mWebViewInfoFab;
        if (target == null || mWebViewPillBar == null) return;
        if (mWebViewInfoRing != null) {
            mWebViewInfoRing.setVisibility(View.INVISIBLE);
            mWebViewInfoRing.reset();
        }
        target.animate()
            .scaleX(0f)
            .scaleY(0f)
            .alpha(0f)
            .setDuration(160)
            .setInterpolator(new AccelerateInterpolator())
            .withEndAction(() -> {
                target.setVisibility(View.GONE);
                mWebViewPillBar.setVisibility(View.VISIBLE);
                mWebViewPillBar.setAlpha(0f);
                mWebViewPillBar.setTranslationY(dpToPx(36));
                mWebViewPillBar.setScaleX(0.85f);
                mWebViewPillBar.setScaleY(0.85f);
                mWebViewPillBar.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(260)
                    .setInterpolator(new OvershootInterpolator(1.2f))
                    .start();
            })
            .start();
    }

    private void collapseWebViewPillBar() {
        View target = mWebViewInfoContainer != null ? mWebViewInfoContainer : mWebViewInfoFab;
        if (mWebViewPillBar == null || target == null) return;
        hideWebViewUrlBar();
        if (mWebViewInfoRing != null) {
            mWebViewInfoRing.setVisibility(View.INVISIBLE);
            mWebViewInfoRing.reset();
        }
        mWebViewPillBar.animate()
            .alpha(0f)
            .translationY(dpToPx(36))
            .scaleX(0.85f)
            .scaleY(0.85f)
            .setDuration(180)
            .setInterpolator(new AccelerateInterpolator())
            .withEndAction(() -> {
                mWebViewPillBar.setVisibility(View.GONE);
                target.setVisibility(View.VISIBLE);
                target.setScaleX(0f);
                target.setScaleY(0f);
                target.setAlpha(0f);
                target.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .alpha(1f)
                    .setDuration(220)
                    .setInterpolator(new OvershootInterpolator(1.3f))
                    .start();
            })
            .start();
    }

    private void toggleWebViewUrlBar() {
        if (mWebViewUrlBar == null) return;
        if (mWebViewUrlBar.getVisibility() == View.VISIBLE) {
            hideWebViewUrlBar();
        } else {
            showWebViewUrlBar();
        }
    }

    private void showWebViewUrlBar() {
        if (mWebViewUrlBar == null || mWebViewUrlEditText == null) return;
        String currentUrl = "";
        if (mCurrentWebSession != null && mCurrentWebSession.getWebView() != null) {
            currentUrl = mCurrentWebSession.getWebView().getUrl();
        }
        if (currentUrl == null) currentUrl = "";
        mWebViewUrlEditText.setText(currentUrl);
        mWebViewUrlEditText.selectAll();

        mWebViewUrlBar.setVisibility(View.VISIBLE);
        mWebViewUrlBar.setAlpha(0f);
        mWebViewUrlBar.setTranslationY(dpToPx(24));
        mWebViewUrlBar.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(220)
            .setInterpolator(new DecelerateInterpolator())
            .start();

        mWebViewUrlEditText.requestFocus();
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.showSoftInput(mWebViewUrlEditText, InputMethodManager.SHOW_IMPLICIT);
        }
    }

    private void hideWebViewUrlBar() {
        if (mWebViewUrlBar == null || mWebViewUrlBar.getVisibility() != View.VISIBLE) return;
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null && mWebViewUrlEditText != null) {
            imm.hideSoftInputFromWindow(mWebViewUrlEditText.getWindowToken(), 0);
        }
        mWebViewUrlBar.animate()
            .alpha(0f)
            .translationY(dpToPx(24))
            .setDuration(160)
            .setInterpolator(new AccelerateInterpolator())
            .withEndAction(() -> mWebViewUrlBar.setVisibility(View.GONE))
            .start();
    }

    private void navigateToEnteredUrl() {
        if (mWebViewUrlEditText == null || mCurrentWebSession == null || mCurrentWebSession.getWebView() == null) return;
        String raw = mWebViewUrlEditText.getText().toString().trim();
        if (raw.isEmpty()) return;
        String targetUrl = raw;
        if (!targetUrl.startsWith("http://") && !targetUrl.startsWith("https://") &&
            !targetUrl.startsWith("file://") && !targetUrl.startsWith("javascript:")) {
            if (targetUrl.startsWith("localhost") || targetUrl.startsWith("127.0.0.1") ||
                targetUrl.matches("^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}(:\\d+)?.*") ||
                targetUrl.matches("^[a-zA-Z0-9.-]+:\\d+.*")) {
                targetUrl = "http://" + targetUrl;
            } else {
                targetUrl = "https://" + targetUrl;
            }
        }
        hideWebViewUrlBar();
        mCurrentWebSession.getWebView().loadUrl(targetUrl);
    }

    private void animateButtonBounce(View view) {
        if (view == null) return;
        view.animate().scaleX(0.82f).scaleY(0.82f).setDuration(70)
            .withEndAction(() -> view.animate().scaleX(1f).scaleY(1f).setDuration(100).start())
            .start();
    }

    private float dpToPx(float dp) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, getResources().getDisplayMetrics());
    }

    private boolean isWebViewUrlBarVisible() {
        return mWebViewUrlBar != null && mWebViewUrlBar.getVisibility() == View.VISIBLE;
    }

    private boolean isWebViewPillBarVisible() {
        return mWebViewPillBar != null && mWebViewPillBar.getVisibility() == View.VISIBLE;
    }

    @Override
    public void onWebSessionListUpdated() {
        runOnUiThread(this::termuxSessionListNotifyUpdated);
    }

    public TerminalView getTerminalView() {
        return mTerminalView;
    }

    public TermuxTerminalViewClient getTermuxTerminalViewClient() {
        return mTermuxTerminalViewClient;
    }

    public TermuxTerminalSessionActivityClient getTermuxTerminalSessionClient() {
        return mTermuxTerminalSessionActivityClient;
    }

    @Nullable
    public TerminalSession getCurrentSession() {
        if (mTerminalView != null)
            return mTerminalView.getCurrentSession();
        else
            return null;
    }

    public TermuxAppSharedPreferences getPreferences() {
        return mPreferences;
    }

    public TermuxAppSharedProperties getProperties() {
        return mProperties;
    }




    public static void updateTermuxActivityStyling(Context context, boolean recreateActivity) {
        // Make sure that terminal styling is always applied.
        Intent stylingIntent = new Intent(TERMUX_ACTIVITY.ACTION_RELOAD_STYLE);
        stylingIntent.putExtra(TERMUX_ACTIVITY.EXTRA_RECREATE_ACTIVITY, recreateActivity);
        context.sendBroadcast(stylingIntent);
    }

    private void registerTermuxActivityBroadcastReceiver() {
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(TERMUX_ACTIVITY.ACTION_NOTIFY_APP_CRASH);
        intentFilter.addAction(TERMUX_ACTIVITY.ACTION_RELOAD_STYLE);
        intentFilter.addAction(TERMUX_ACTIVITY.ACTION_REQUEST_PERMISSIONS);

        registerReceiver(mTermuxActivityBroadcastReceiver, intentFilter);
    }

    private void unregisterTermuxActivityBroadcastReceiver() {
        unregisterReceiver(mTermuxActivityBroadcastReceiver);
    }

    private void fixTermuxActivityBroadcastReceiverIntent(Intent intent) {
        if (intent == null) return;

        String extraReloadStyle = intent.getStringExtra(TERMUX_ACTIVITY.EXTRA_RELOAD_STYLE);
        if ("storage".equals(extraReloadStyle)) {
            intent.removeExtra(TERMUX_ACTIVITY.EXTRA_RELOAD_STYLE);
            intent.setAction(TERMUX_ACTIVITY.ACTION_REQUEST_PERMISSIONS);
        }
    }

    class TermuxActivityBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null) return;

            if (mIsVisible) {
                fixTermuxActivityBroadcastReceiverIntent(intent);

                switch (intent.getAction()) {
                    case TERMUX_ACTIVITY.ACTION_NOTIFY_APP_CRASH:
                        Logger.logDebug(LOG_TAG, "Received intent to notify app crash");
                        TermuxCrashUtils.notifyAppCrashFromCrashLogFile(context, LOG_TAG);
                        return;
                    case TERMUX_ACTIVITY.ACTION_RELOAD_STYLE:
                        Logger.logDebug(LOG_TAG, "Received intent to reload styling");
                        reloadActivityStyling(intent.getBooleanExtra(TERMUX_ACTIVITY.EXTRA_RECREATE_ACTIVITY, true));
                        return;
                    case TERMUX_ACTIVITY.ACTION_REQUEST_PERMISSIONS:
                        Logger.logDebug(LOG_TAG, "Received intent to request storage permissions");
                        requestStoragePermission(false);
                        return;
                    default:
                }
            }
        }
    }

    private void reloadActivityStyling(boolean recreateActivity) {
        if (mProperties != null) {
            reloadProperties();

            if (mExtraKeysView != null) {
                mExtraKeysView.setButtonTextAllCaps(mProperties.shouldExtraKeysTextBeAllCaps());
                mExtraKeysView.reload(mTermuxTerminalExtraKeys.getExtraKeysInfo(), mTerminalToolbarDefaultHeight);
            }

            // Update NightMode.APP_NIGHT_MODE
            TermuxThemeUtils.setAppNightMode(mProperties.getNightMode());
        }

        setMargins();
        setTerminalToolbarHeight();

        FileReceiverActivity.updateFileReceiverActivityComponentsState(this);

        if (mTermuxTerminalSessionActivityClient != null)
            mTermuxTerminalSessionActivityClient.onReloadActivityStyling();

        if (mTermuxTerminalViewClient != null)
            mTermuxTerminalViewClient.onReloadActivityStyling();

        // To change the activity and drawer theme, activity needs to be recreated.
        // It will destroy the activity, including all stored variables and views, and onCreate()
        // will be called again. Extra keys input text, terminal sessions and transcripts will be preserved.
        if (recreateActivity) {
            Logger.logDebug(LOG_TAG, "Recreating activity");
            TermuxActivity.this.recreate();
        }
    }



    public static void startTermuxActivity(@NonNull final Context context) {
        ActivityUtils.startActivity(context, newInstance(context));
    }

    public static Intent newInstance(@NonNull final Context context) {
        Intent intent = new Intent(context, TermuxActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return intent;
    }

}
