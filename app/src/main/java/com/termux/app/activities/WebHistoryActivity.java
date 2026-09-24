package com.termux.app.activities;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.termux.R;
import com.termux.app.TermuxActivity;
import com.termux.app.web.history.TermuxWebHistoryManager;
import com.termux.app.web.history.WebHistoryItem;
import com.termux.shared.activity.media.AppCompatActivityUtils;
import com.termux.shared.theme.NightMode;

import java.util.ArrayList;
import java.util.List;

public class WebHistoryActivity extends AppCompatActivity implements TermuxWebHistoryManager.HistoryChangeListener {

    private TermuxWebHistoryManager mHistoryManager;
    private RecyclerView mRecyclerView;
    private WebHistoryAdapter mAdapter;
    private View mEmptyView;
    private EditText mSearchInput;
    private ImageButton mClearSearchBtn;
    private String mCurrentQuery = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AppCompatActivityUtils.setNightMode(this, NightMode.getAppNightMode().getName(), true);
        setContentView(R.layout.activity_web_history);

        Toolbar toolbar = findViewById(R.id.web_history_toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }

        mHistoryManager = TermuxWebHistoryManager.getInstance(this);

        mRecyclerView = findViewById(R.id.web_history_recycler_view);
        mEmptyView = findViewById(R.id.web_history_empty_view);
        mSearchInput = findViewById(R.id.web_history_search_input);
        mClearSearchBtn = findViewById(R.id.web_history_clear_search_btn);

        mRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        mAdapter = new WebHistoryAdapter(new ArrayList<>(), new WebHistoryAdapter.HistoryItemListener() {
            @Override
            public void onItemClicked(WebHistoryItem item) {
                openUrlInTermux(item.getUrl());
            }

            @Override
            public void onItemLongClicked(WebHistoryItem item) {
                showItemOptionsDialog(item);
            }

            @Override
            public void onDeleteClicked(WebHistoryItem item) {
                mHistoryManager.deleteItem(item.getId());
            }
        });
        mRecyclerView.setAdapter(mAdapter);

        mSearchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int count, int after) {
                mCurrentQuery = s.toString();
                mClearSearchBtn.setVisibility(mCurrentQuery.isEmpty() ? View.GONE : View.VISIBLE);
                updateHistoryList();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        mClearSearchBtn.setOnClickListener(v -> mSearchInput.setText(""));

        mHistoryManager.addListener(this);
        updateHistoryList();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mHistoryManager != null) {
            mHistoryManager.removeListener(this);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_web_history, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        } else if (item.getItemId() == R.id.menu_action_clear_all) {
            showClearAllDialog();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onHistoryChanged() {
        runOnUiThread(this::updateHistoryList);
    }

    private void updateHistoryList() {
        List<WebHistoryItem> items = mHistoryManager.searchHistory(mCurrentQuery);
        mAdapter.setItems(items);
        if (items.isEmpty()) {
            mEmptyView.setVisibility(View.VISIBLE);
            mRecyclerView.setVisibility(View.GONE);
        } else {
            mEmptyView.setVisibility(View.GONE);
            mRecyclerView.setVisibility(View.VISIBLE);
        }
    }

    private void openUrlInTermux(@NonNull String url) {
        Intent intent = new Intent("com.termux.app.OPEN_WEB_SESSION");
        intent.setPackage(getPackageName());
        intent.putExtra("url", url);
        intent.putExtra("newSession", true);
        sendBroadcast(intent);

        Intent activityIntent = new Intent(this, TermuxActivity.class);
        activityIntent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        startActivity(activityIntent);
        finish();
    }

    private void showItemOptionsDialog(@NonNull final WebHistoryItem item) {
        String[] options = new String[]{"Open in Web Session", "Copy URL", "Delete from History"};
        new AlertDialog.Builder(this)
            .setTitle(item.getTitle())
            .setItems(options, (dialog, which) -> {
                if (which == 0) {
                    openUrlInTermux(item.getUrl());
                } else if (which == 1) {
                    ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                    if (clipboard != null) {
                        ClipData clip = ClipData.newPlainText("URL", item.getUrl());
                        clipboard.setPrimaryClip(clip);
                        Toast.makeText(WebHistoryActivity.this, "URL copied to clipboard", Toast.LENGTH_SHORT).show();
                    }
                } else if (which == 2) {
                    mHistoryManager.deleteItem(item.getId());
                }
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void showClearAllDialog() {
        new AlertDialog.Builder(this)
            .setTitle("Clear All History")
            .setMessage("Are you sure you want to delete all browsing history?")
            .setPositiveButton("Clear", (dialog, which) -> {
                mHistoryManager.clearHistory();
                Toast.makeText(WebHistoryActivity.this, "Browsing history cleared", Toast.LENGTH_SHORT).show();
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    // RecyclerView Adapter
    private static class WebHistoryAdapter extends RecyclerView.Adapter<WebHistoryAdapter.ViewHolder> {

        interface HistoryItemListener {
            void onItemClicked(WebHistoryItem item);
            void onItemLongClicked(WebHistoryItem item);
            void onDeleteClicked(WebHistoryItem item);
        }

        private final List<WebHistoryItem> mItems;
        private final HistoryItemListener mListener;

        WebHistoryAdapter(List<WebHistoryItem> items, HistoryItemListener listener) {
            mItems = items;
            mListener = listener;
        }

        @SuppressLint("NotifyDataSetChanged")
        void setItems(List<WebHistoryItem> newItems) {
            mItems.clear();
            mItems.addAll(newItems);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_web_history, parent, false);
            return new ViewHolder(view);
        }

        private static final ThreadLocal<SimpleDateFormat> DATE_FORMAT =
            ThreadLocal.withInitial(() -> new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()));

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            WebHistoryItem item = mItems.get(position);
            holder.title.setText(item.getTitle());
            holder.url.setText(item.getUrl());

            String formattedTime = DATE_FORMAT.get().format(new Date(item.getTimestamp()));
            holder.time.setText(formattedTime);

            holder.itemView.setOnClickListener(v -> mListener.onItemClicked(item));
            holder.itemView.setOnLongClickListener(v -> {
                mListener.onItemLongClicked(item);
                return true;
            });
            holder.deleteBtn.setOnClickListener(v -> mListener.onDeleteClicked(item));
        }

        @Override
        public int getItemCount() {
            return mItems.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView title;
            TextView url;
            TextView time;
            ImageButton deleteBtn;

            ViewHolder(View itemView) {
                super(itemView);
                title = itemView.findViewById(R.id.history_item_title);
                url = itemView.findViewById(R.id.history_item_url);
                time = itemView.findViewById(R.id.history_item_time);
                deleteBtn = itemView.findViewById(R.id.history_item_delete_btn);
            }
        }
    }
}
