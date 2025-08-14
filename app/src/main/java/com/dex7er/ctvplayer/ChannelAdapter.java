package com.dex7er.ctvplayer;

import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;

public class ChannelAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private static final String TAG = "ChannelAdapter";

    private List<Channel> channels;
    private OnChannelClickListener channelListener;
    private Context context;

    public interface OnChannelClickListener {
        void onChannelClick(Channel channel);
    }

    public ChannelAdapter(Context context, List<Channel> channels, OnChannelClickListener channelListener) {
        this.context = context;
        this.channels = channels == null ? new ArrayList<>() : new ArrayList<>(channels);
        this.channels.removeIf(c -> c == null);
        this.channelListener = channelListener;
        Log.d(TAG, "ChannelAdapter created with " + this.channels.size() + " channels");
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_channel, parent, false);
        return new ChannelViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (position >= channels.size()) {
            Log.w(TAG, "Position " + position + " is out of bounds for channels list of size " + channels.size());
            return;
        }

        Channel channel = channels.get(position);
        if (channel == null) {
            Log.w(TAG, "Channel at position " + position + " is null");
            ChannelViewHolder channelHolder = (ChannelViewHolder) holder;
            channelHolder.channelIcon.setImageResource(R.drawable.ic_tv_default);
            return;
        }

        ChannelViewHolder channelHolder = (ChannelViewHolder) holder;
        String icUrl = channel.getIcUrl();
        String channelName = channel.getName() != null ? channel.getName() : "未知频道";
        Log.d(TAG, "Loading icon for channel: " + channelName + ", icUrl: " + icUrl);

        // 移除 .png 扩展名以匹配本地资源
        String iconName = icUrl != null ? icUrl.replace(".png", "") : "";
        int resourceId = context.getResources().getIdentifier(iconName, "drawable", context.getPackageName());

        if (resourceId != 0) {
            Log.d(TAG, "Using local drawable resource: " + iconName);
            channelHolder.channelIcon.setImageResource(resourceId);
        } else {
            Log.w(TAG, "No drawable resource found for: " + iconName + ", using default");
            channelHolder.channelIcon.setImageResource(R.drawable.ic_tv_default);
        }

        channelHolder.itemView.setOnClickListener(v -> {
            String url = channel.getUrl() != null ? channel.getUrl() : "https://www.yangshipin.cn/tv/home?pid=" + channel.getPid();
            if (url != null && !url.isEmpty()) {
                Log.d(TAG, "Channel clicked: " + channelName + " (" + url + ")");
                channelListener.onChannelClick(channel);
            } else {
                Log.w(TAG, "Channel URL is invalid: " + channelName);
                Toast.makeText(context, context.getString(R.string.invalid_channel_url), Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public int getItemCount() {
        return channels.size();
    }

    static class ChannelViewHolder extends RecyclerView.ViewHolder {
        ImageView channelIcon;

        ChannelViewHolder(View itemView) {
            super(itemView);
            channelIcon = itemView.findViewById(R.id.channel_icon);
        }
    }
}
