package com.dex7er.ctvplayer;

import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.ViewHolder> {
    private static final String TAG = "HistoryAdapter";

    private final Context context;
    private final List<PlayHistory> historyList;
    private final OnHistoryClickListener listener;

    public interface OnHistoryClickListener {
        void onHistoryClick(PlayHistory history);
    }

    public HistoryAdapter(Context context, List<PlayHistory> historyList, OnHistoryClickListener listener) {
        this.context = context;
        this.historyList = historyList;
        this.listener = listener;
        Log.d(TAG, "HistoryAdapter created with " + historyList.size() + " items");
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_channel, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        PlayHistory history = historyList.get(position);
        String icUrl = history.getIcUrl();
        String channelName = history.getName() != null ? history.getName() : "未知频道";
        Log.d(TAG, "Loading icon for history: " + channelName + ", icUrl: " + icUrl);

        String iconName = icUrl != null ? icUrl.replace(".png", "") : "";
        int resId = context.getResources().getIdentifier(iconName, "drawable", context.getPackageName());

        if (resId != 0) {
            Log.d(TAG, "Using local drawable resource: " + iconName);
            holder.imageView.setImageResource(resId);
        } else {
            Log.w(TAG, "No drawable resource found for: " + iconName + ", using default");
            holder.imageView.setImageResource(R.drawable.ic_tv_default);
        }

        holder.itemView.setOnClickListener(v -> listener.onHistoryClick(history));
    }

    @Override
    public int getItemCount() {
        return historyList.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView imageView;

        ViewHolder(View itemView) {
            super(itemView);
            imageView = itemView.findViewById(R.id.channel_icon);
        }
    }
}