package com.dex7er.ctvplayer;

import android.content.Context;
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
import java.util.List;

public class ChannelAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
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
        this.channels = channels;
        this.channelListener = channelListener;
        this.updateListener = updateListener;
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
            Channel channel = channels.get(position);
            ChannelViewHolder channelHolder = (ChannelViewHolder) holder;
            channelHolder.channelName.setText(channel.getName() != null ? channel.getName() : "未知频道");

            // 图标加载逻辑
            String iconName = channel.getName() != null ? channel.getName().toLowerCase().replace(" ", "_") : "";
            int resourceId = context.getResources().getIdentifier(iconName, "drawable", context.getPackageName());
            String icUrl = channel.getIcUrl() != null ? channel.getIcUrl() : "";
            if (resourceId != 0) {
                // 本地图标存在
                channelHolder.channelIcon.setImageResource(resourceId);
                channelHolder.channelNameOnIcon.setVisibility(View.GONE);
                channelHolder.channelName.setVisibility(View.VISIBLE);
            } else if (!icUrl.isEmpty()) {
                // 加载远程图标
                Glide.with(context)
                        .load(icUrl)
                        .error(R.drawable.ic_tv_default)
                        .into(channelHolder.channelIcon);
                channelHolder.channelNameOnIcon.setVisibility(View.GONE);
                channelHolder.channelName.setVisibility(View.VISIBLE);
            } else {
                // 使用默认图标并显示名称
                channelHolder.channelIcon.setImageResource(R.drawable.ic_tv_default);
                channelHolder.channelNameOnIcon.setText(channel.getName() != null ? channel.getName() : "未知");
                channelHolder.channelNameOnIcon.setVisibility(View.VISIBLE);
                channelHolder.channelName.setVisibility(View.VISIBLE);
            }

            // 点击事件检查 URL
            channelHolder.itemView.setOnClickListener(v -> {
                String url = channel.getUrl();
                if (url != null && !url.isEmpty()) {
                    channelListener.onChannelClick(url);
                } else {
                    // 安全显示 Toast
                    Toast.makeText(context, context.getString(R.string.invalid_channel_url), Toast.LENGTH_SHORT).show();
                }
            });
        } else if (holder instanceof UpdateButtonViewHolder) {
            UpdateButtonViewHolder buttonHolder = (UpdateButtonViewHolder) holder;
            buttonHolder.updateButton.setOnClickListener(v -> updateListener.onUpdateButtonClick());
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
            channelName = itemView.findViewById(R.id.channel_name);
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