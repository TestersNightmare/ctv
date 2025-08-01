package com.dex7er.ctvplayer;

import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import java.util.ArrayList;
import java.util.List;

public class ChannelAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private static final String TAG = "ChannelAdapter";
    private static final int TYPE_CHANNEL = 0;
    private static final int TYPE_UPDATE_BUTTON = 1;

    private List<Channel> channels;
    private OnChannelClickListener channelListener;
    private OnUpdateButtonClickListener updateListener;
    private Context context;

    public interface OnChannelClickListener {
        void onChannelClick(String url);
    }

    public interface OnUpdateButtonClickListener {
        void onUpdateButtonClick();
    }

    public ChannelAdapter(Context context, List<Channel> channels, OnChannelClickListener channelListener, OnUpdateButtonClickListener updateListener) {
        this.context = context;
        this.channels = channels == null ? new ArrayList<>() : new ArrayList<>(channels);
        // 过滤掉 null，保证后续不崩溃
        this.channels.removeIf(c -> c == null);
        this.channelListener = channelListener;
        this.updateListener = updateListener;
        Log.d(TAG, "ChannelAdapter created with " + this.channels.size() + " channels");
    }

    @Override
    public int getItemViewType(int position) {
        return position == channels.size() ? TYPE_UPDATE_BUTTON : TYPE_CHANNEL;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == TYPE_CHANNEL) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_channel, parent, false);
            return new ChannelViewHolder(view);
        } else {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_update_button, parent, false);
            return new UpdateButtonViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof ChannelViewHolder) {
            if (position >= channels.size()) {
                Log.w(TAG, "Position " + position + " is out of bounds for channels list of size " + channels.size());
                return; // 防止越界
            }

            Channel channel = channels.get(position);
            if (channel == null) {
                Log.w(TAG, "Channel at position " + position + " is null");
                ChannelViewHolder channelHolder = (ChannelViewHolder) holder;
                channelHolder.channelIcon.setImageResource(R.drawable.ic_tv_default);
                channelHolder.channelNameOnIcon.setText("未知");
                channelHolder.channelNameOnIcon.setVisibility(View.VISIBLE);
                return;
            }

            ChannelViewHolder channelHolder = (ChannelViewHolder) holder;

            // 图标加载逻辑
            String channelName = channel.getName() != null ? channel.getName() : "未知频道";
            String iconName = channelName.toLowerCase().replace(" ", "_");
            int resourceId = context.getResources().getIdentifier(iconName, "drawable", context.getPackageName());
            String icUrl = channel.getIcUrl() != null ? channel.getIcUrl() : "";

            if (resourceId != 0) {
                // 本地图标存在
                channelHolder.channelIcon.setImageResource(resourceId);
                channelHolder.channelNameOnIcon.setVisibility(View.GONE);
            } else if (!icUrl.isEmpty()) {
                // 加载远程图标
                Glide.with(context)
                        .load(icUrl)
                        .error(R.drawable.ic_tv_default)
                        .into(channelHolder.channelIcon);
                channelHolder.channelNameOnIcon.setVisibility(View.GONE);
            } else {
                // 使用默认图标并显示名称
                channelHolder.channelIcon.setImageResource(R.drawable.ic_tv_default);
                channelHolder.channelNameOnIcon.setText(channelName);
                channelHolder.channelNameOnIcon.setVisibility(View.VISIBLE);
            }

            // 点击事件检查 URL
            channelHolder.itemView.setOnClickListener(v -> {
                String url = channel.getUrl();
                if (url != null && !url.isEmpty()) {
                    Log.d(TAG, "Channel clicked: " + channelName + " (" + url + ")");
                    channelListener.onChannelClick(url);
                } else {
                    // 安全显示 Toast
                    Log.w(TAG, "Channel URL is invalid: " + channelName);
                    Toast.makeText(context, context.getString(R.string.invalid_channel_url), Toast.LENGTH_SHORT).show();
                }
            });
        } else if (holder instanceof UpdateButtonViewHolder) {
            UpdateButtonViewHolder buttonHolder = (UpdateButtonViewHolder) holder;
            buttonHolder.updateButton.setOnClickListener(v -> {
                Log.d(TAG, "Update button clicked");
                if (updateListener != null) {
                    updateListener.onUpdateButtonClick();
                } else {
                    Log.w(TAG, "Update listener is null");
                }
            });
        }
    }

    @Override
    public int getItemCount() {
        return channels.size() + 1; // +1 for update button
    }

    static class ChannelViewHolder extends RecyclerView.ViewHolder {
        ImageView channelIcon;
        TextView channelName;
        TextView channelNameOnIcon;

        ChannelViewHolder(View itemView) {
            super(itemView);
            channelIcon = itemView.findViewById(R.id.channel_icon);
            channelNameOnIcon = itemView.findViewById(R.id.channel_name_on_icon);
        }
    }

    static class UpdateButtonViewHolder extends RecyclerView.ViewHolder {
        Button updateButton;

        UpdateButtonViewHolder(View itemView) {
            super(itemView);
            updateButton = itemView.findViewById(R.id.update_button);
        }
    }
}