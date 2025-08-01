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
import java.util.ArrayList;
import java.util.List;

public class TwoColumnChannelAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private static final String TAG = "TwoColumnChannelAdapter";
    private static final int TYPE_CHANNEL = 0;
    private static final int TYPE_UPDATE_BUTTON = 1;

    private List<Channel> allChannels;
    private OnChannelClickListener channelListener;
    private OnUpdateButtonClickListener updateListener;
    private Context context;

    public interface OnChannelClickListener {
        void onChannelClick(Channel channel);
    }

    public interface OnUpdateButtonClickListener {
        void onUpdateButtonClick();
    }

    public TwoColumnChannelAdapter(Context context, List<ChannelGroup> channelGroups,
                                   OnChannelClickListener channelListener,
                                   OnUpdateButtonClickListener updateListener) {
        this.context = context;
        this.channelListener = channelListener;
        this.updateListener = updateListener;
        this.allChannels = new ArrayList<>();

        if (channelGroups != null) {
            for (ChannelGroup group : channelGroups) {
                if (group.getChannels() != null) {
                    allChannels.addAll(group.getChannels());
                }
            }
        }
        allChannels.removeIf(c -> c == null);
        Log.d(TAG, "TwoColumnChannelAdapter created with " + allChannels.size() + " channels");
    }

    @Override
    public int getItemViewType(int position) {
        return position == allChannels.size() ? TYPE_UPDATE_BUTTON : TYPE_CHANNEL;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == TYPE_CHANNEL) {
            View channelView = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_channel, parent, false);
            return new ChannelViewHolder(channelView);
        } else {
            View buttonView = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_update_button, parent, false);
            return new UpdateButtonViewHolder(buttonView);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof ChannelViewHolder) {
            if (position >= allChannels.size()) return;

            Channel channel = allChannels.get(position);
            if (channel == null) return;

            ChannelViewHolder channelHolder = (ChannelViewHolder) holder;

            // 隐藏文字，只显示图标
            if (channelHolder.channelName != null) {
                channelHolder.channelName.setVisibility(View.GONE);
            }
            if (channelHolder.channelNameOnIcon != null) {
                channelHolder.channelNameOnIcon.setVisibility(View.GONE);
            }

            // 加载图标
            loadChannelIcon(channelHolder.channelIcon, channel);

            // 设置点击监听
            channelHolder.itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (channelListener != null) {
                        channelListener.onChannelClick(channel);
                    }
                }
            });

        } else if (holder instanceof UpdateButtonViewHolder) {
            UpdateButtonViewHolder updateHolder = (UpdateButtonViewHolder) holder;

            // 修复：确保更新按钮的点击监听器正确设置
            updateHolder.updateButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Log.d(TAG, "Update button clicked");
                    Toast.makeText(context, "正在更新频道列表...", Toast.LENGTH_SHORT).show();

                    if (updateListener != null) {
                        updateListener.onUpdateButtonClick();
                    } else {
                        Log.e(TAG, "updateListener is null!");
                    }
                }
            });
        }
    }

    private void loadChannelIcon(ImageView iconView, Channel channel) {
        String icUrl = channel.getIcUrl();
        if (icUrl != null && !icUrl.isEmpty()) {
            String iconName = icUrl.replace(".png", "").replace(".jpg", "").replace(".jpeg", "");
            int resourceId = context.getResources().getIdentifier(iconName, "mipmap", context.getPackageName());

            if (resourceId != 0) {
                iconView.setImageResource(resourceId);
            } else {
                resourceId = context.getResources().getIdentifier(iconName, "drawable", context.getPackageName());
                if (resourceId != 0) {
                    iconView.setImageResource(resourceId);
                } else {
                    iconView.setImageResource(R.drawable.ic_tv_default);
                }
            }
        } else {
            iconView.setImageResource(R.drawable.ic_tv_default);
        }
    }

    @Override
    public int getItemCount() {
        return allChannels.size() + 1; // +1 for update button
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
            if (updateButton == null) {
                Log.e(TAG, "update_button not found in layout!");
            }
        }
    }
}