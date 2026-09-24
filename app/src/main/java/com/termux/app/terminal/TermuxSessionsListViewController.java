package com.termux.app.terminal;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.text.style.ImageSpan;
import android.text.style.StyleSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.termux.R;
import com.termux.app.TermuxActivity;
import com.termux.app.web.TermuxWebSession;
import com.termux.app.web.TermuxWebSessionManager;
import com.termux.shared.termux.shell.command.runner.terminal.TermuxSession;
import com.termux.shared.theme.NightMode;
import com.termux.shared.theme.ThemeUtils;
import com.termux.terminal.TerminalSession;

import java.util.List;

public class TermuxSessionsListViewController extends BaseAdapter implements AdapterView.OnItemClickListener, AdapterView.OnItemLongClickListener {

    final TermuxActivity mActivity;
    final List<TermuxSession> mSessionList;

    public TermuxSessionsListViewController(TermuxActivity activity, List<TermuxSession> sessionList) {
        this.mActivity = activity;
        this.mSessionList = sessionList;
    }

    @Override
    public int getCount() {
        int termCount = (mSessionList != null) ? mSessionList.size() : 0;
        int webCount = TermuxWebSessionManager.getInstance().getWebSessionCount();
        return termCount + webCount;
    }

    @Override
    public Object getItem(int position) {
        int termCount = (mSessionList != null) ? mSessionList.size() : 0;
        if (position < termCount) {
            return mSessionList.get(position);
        } else {
            return TermuxWebSessionManager.getInstance().getWebSessionAt(position - termCount);
        }
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @SuppressLint("SetTextI18n")
    @NonNull
    @Override
    public View getView(int position, View convertView, @NonNull ViewGroup parent) {
        View sessionRowView = convertView;
        if (sessionRowView == null) {
            LayoutInflater inflater = mActivity.getLayoutInflater();
            sessionRowView = inflater.inflate(R.layout.item_terminal_sessions_list, parent, false);
        }

        TextView sessionTitleView = sessionRowView.findViewById(R.id.session_title);
        sessionTitleView.setCompoundDrawablesWithIntrinsicBounds(null, null, null, null);
        boolean shouldEnableDarkTheme = ThemeUtils.shouldEnableDarkTheme(mActivity, NightMode.getAppNightMode().getName());

        if (shouldEnableDarkTheme) {
            sessionTitleView.setBackground(
                ContextCompat.getDrawable(mActivity, R.drawable.session_background_black_selected)
            );
        }

        int termCount = (mSessionList != null) ? mSessionList.size() : 0;

        if (position < termCount) {
            TermuxSession termuxSession = mSessionList.get(position);
            TerminalSession sessionAtRow = termuxSession != null ? termuxSession.getTerminalSession() : null;
            if (sessionAtRow == null) {
                sessionTitleView.setText("null session");
                return sessionRowView;
            }

            String name = sessionAtRow.mSessionName;
            String sessionTitle = sessionAtRow.getTitle();

            String numberPart = "[" + (position + 1) + "] ";
            String sessionNamePart = (TextUtils.isEmpty(name) ? "" : name);
            String sessionTitlePart = (TextUtils.isEmpty(sessionTitle) ? "" : ((sessionNamePart.isEmpty() ? "" : "\n") + sessionTitle));

            String fullSessionTitle = numberPart + sessionNamePart + sessionTitlePart;
            SpannableString fullSessionTitleStyled = new SpannableString(fullSessionTitle);
            fullSessionTitleStyled.setSpan(new StyleSpan(Typeface.BOLD), 0, numberPart.length() + sessionNamePart.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            fullSessionTitleStyled.setSpan(new StyleSpan(Typeface.ITALIC), numberPart.length() + sessionNamePart.length(), fullSessionTitle.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

            sessionTitleView.setText(fullSessionTitleStyled);

            boolean sessionRunning = sessionAtRow.isRunning();
            if (sessionRunning) {
                sessionTitleView.setPaintFlags(sessionTitleView.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);
            } else {
                sessionTitleView.setPaintFlags(sessionTitleView.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            }
            int defaultColor = shouldEnableDarkTheme ? Color.WHITE : Color.BLACK;
            int color = sessionRunning || sessionAtRow.getExitStatus() == 0 ? defaultColor : Color.RED;
            sessionTitleView.setTextColor(color);
        } else {
            TermuxWebSession webSession = TermuxWebSessionManager.getInstance().getWebSessionAt(position - termCount);
            if (webSession == null) {
                sessionTitleView.setText("[WEB] null");
                return sessionRowView;
            }

            sessionTitleView.setPaintFlags(sessionTitleView.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);

            String iconPlaceholder = "\uFFFC ";
            String numberPart = "[" + (position + 1) + "] ";
            String titlePart = webSession.getTitle();
            String urlPart = "\n" + webSession.getUrl();

            String fullWebTitle = iconPlaceholder + numberPart + titlePart + urlPart;
            SpannableString fullWebTitleStyled = new SpannableString(fullWebTitle);

            int webAccentColor = shouldEnableDarkTheme ? Color.parseColor("#4DD0E1") : Color.parseColor("#00838F");

            Drawable globeDrawable = ContextCompat.getDrawable(mActivity, R.drawable.ic_lucide_globe);
            if (globeDrawable != null) {
                globeDrawable = globeDrawable.mutate();
                globeDrawable.setTint(webAccentColor);
                int iconSize = (int) (sessionTitleView.getTextSize() * 1.15f);
                globeDrawable.setBounds(0, 0, iconSize, iconSize);
                fullWebTitleStyled.setSpan(new ImageSpan(globeDrawable, ImageSpan.ALIGN_BOTTOM), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }

            int textBoldEnd = iconPlaceholder.length() + numberPart.length() + titlePart.length();
            fullWebTitleStyled.setSpan(new StyleSpan(Typeface.BOLD), iconPlaceholder.length(), textBoldEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            fullWebTitleStyled.setSpan(new StyleSpan(Typeface.ITALIC), textBoldEnd, fullWebTitle.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

            fullWebTitleStyled.setSpan(new ForegroundColorSpan(webAccentColor), iconPlaceholder.length(), iconPlaceholder.length() + numberPart.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

            sessionTitleView.setText(fullWebTitleStyled);
            sessionTitleView.setTextColor(shouldEnableDarkTheme ? Color.WHITE : Color.BLACK);
        }

        return sessionRowView;
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        int termCount = (mSessionList != null) ? mSessionList.size() : 0;
        if (position < termCount) {
            TermuxSession clickedSession = mSessionList.get(position);
            mActivity.showTerminalView();
            mActivity.getTermuxTerminalSessionClient().setCurrentSession(clickedSession.getTerminalSession());
        } else {
            TermuxWebSession webSession = TermuxWebSessionManager.getInstance().getWebSessionAt(position - termCount);
            if (webSession != null) {
                mActivity.showWebSession(webSession);
            }
        }
        mActivity.getDrawer().closeDrawers();
    }

    @Override
    public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
        int termCount = (mSessionList != null) ? mSessionList.size() : 0;
        if (position < termCount) {
            TermuxSession selectedSession = mSessionList.get(position);
            mActivity.getTermuxTerminalSessionClient().renameSession(selectedSession.getTerminalSession());
            return true;
        } else {
            final TermuxWebSession webSession = TermuxWebSessionManager.getInstance().getWebSessionAt(position - termCount);
            if (webSession == null) return false;

            CharSequence[] options = new CharSequence[]{"Reload", "Copy URL", "Close Session"};
            new AlertDialog.Builder(mActivity)
                .setTitle(webSession.getTitle())
                .setItems(options, (dialog, which) -> {
                    switch (which) {
                        case 0:
                            webSession.reload();
                            break;
                        case 1:
                            ClipboardManager clipboard = (ClipboardManager) mActivity.getSystemService(Context.CLIPBOARD_SERVICE);
                            if (clipboard != null) {
                                clipboard.setPrimaryClip(ClipData.newPlainText("URL", webSession.getUrl()));
                                Toast.makeText(mActivity, "URL copied to clipboard", Toast.LENGTH_SHORT).show();
                            }
                            break;
                        case 2:
                            boolean isActive = (mActivity.getCurrentWebSession() == webSession);
                            TermuxWebSessionManager.getInstance().removeWebSession(webSession);
                            if (isActive) {
                                mActivity.showTerminalView();
                            }
                            break;
                    }
                })
                .show();
            return true;
        }
    }
}
