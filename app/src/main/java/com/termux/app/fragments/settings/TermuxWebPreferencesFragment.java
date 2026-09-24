package com.termux.app.fragments.settings;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.webkit.CookieManager;
import android.webkit.WebStorage;
import android.widget.Toast;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import com.termux.R;
import com.termux.app.activities.WebHistoryActivity;
import com.termux.app.web.history.TermuxWebHistoryManager;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

@Keep
public class TermuxWebPreferencesFragment extends PreferenceFragmentCompat {

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.termux_web_preferences, rootKey);

        final Context context = getContext();
        if (context == null) return;

        // View Browsing History
        Preference viewHistoryPref = findPreference("view_web_history");
        if (viewHistoryPref != null) {
            viewHistoryPref.setOnPreferenceClickListener(preference -> {
                Intent intent = new Intent(context, WebHistoryActivity.class);
                startActivity(intent);
                return true;
            });
        }

        // Clear Browsing History
        Preference clearHistoryPref = findPreference("clear_web_history");
        if (clearHistoryPref != null) {
            clearHistoryPref.setOnPreferenceClickListener(preference -> {
                showClearHistoryDialog(context);
                return true;
            });
        }

        // Clear Cache & Cookies
        Preference clearCachePref = findPreference("clear_web_cache_cookies");
        if (clearCachePref != null) {
            clearCachePref.setOnPreferenceClickListener(preference -> {
                showClearCacheDialog(context);
                return true;
            });
        }

        // Export History
        Preference exportPref = findPreference("export_web_history");
        if (exportPref != null) {
            exportPref.setOnPreferenceClickListener(preference -> {
                exportHistoryToFile(context);
                return true;
            });
        }
    }

    private void showClearHistoryDialog(@NonNull final Context context) {
        final String[] options = new String[]{"Past Hour", "Past 24 Hours", "All Time"};
        new AlertDialog.Builder(context)
            .setTitle("Clear Browsing History")
            .setItems(options, (dialog, which) -> {
                TermuxWebHistoryManager manager = TermuxWebHistoryManager.getInstance(context);
                long now = System.currentTimeMillis();
                if (which == 0) {
                    manager.clearHistoryOlderThan(now - (3600 * 1000L));
                    Toast.makeText(context, "Cleared history from past hour", Toast.LENGTH_SHORT).show();
                } else if (which == 1) {
                    manager.clearHistoryOlderThan(now - (24 * 3600 * 1000L));
                    Toast.makeText(context, "Cleared history from past 24 hours", Toast.LENGTH_SHORT).show();
                } else {
                    manager.clearHistory();
                    Toast.makeText(context, "All browsing history cleared", Toast.LENGTH_SHORT).show();
                }
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void showClearCacheDialog(@NonNull final Context context) {
        new AlertDialog.Builder(context)
            .setTitle("Clear Cache & Storage")
            .setMessage("This will remove all temporary web cache files, cookies, and website local storage. Continue?")
            .setPositiveButton("Clear", (dialog, which) -> {
                try {
                    WebStorage.getInstance().deleteAllData();
                    CookieManager.getInstance().removeAllCookies(null);
                    CookieManager.getInstance().flush();
                    Toast.makeText(context, "Web cache and storage cleared", Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    Toast.makeText(context, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void exportHistoryToFile(@NonNull final Context context) {
        try {
            TermuxWebHistoryManager manager = TermuxWebHistoryManager.getInstance(context);
            String json = manager.exportHistoryJson();
            File homeDir = new File("/data/data/com.termux/files/home/.termux");
            if (!homeDir.exists()) {
                homeDir.mkdirs();
            }
            File targetFile = new File(homeDir, "web_history.json");
            try (FileOutputStream fos = new FileOutputStream(targetFile)) {
                fos.write(json.getBytes(StandardCharsets.UTF_8));
                fos.flush();
            }
            Toast.makeText(context, "Exported to ~/.termux/web_history.json", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(context, "Export failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
}
