package com.dex7er.ctvplayer;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class ChannelIconAdapter extends RecyclerView.Adapter<ChannelIconAdapter.ChannelIconViewHolder> {
    private Context context;
    private List<Channel> channels;
    private OnChannelClickListener channelListener;

    public interface OnChannelClickListener {
        void onChannelClick(Channel channel); // 修改：接收 Channel 对象而不是 String
    }

    public ChannelIconAdapter(Context context, List<Channel> channels, OnChannelClickListener listener) {
        this.context = context;
        this.channels = channels;
        this.channelListener = listener;
    }

    @NonNull
    @Override
    public ChannelIconViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_channel_icon, parent, false);
        return new ChannelIconViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ChannelIconViewHolder holder, int position) {
        Channel channel = channels.get(position);

        // 加载频道图标
        String icUrl = channel.getIcUrl();
        if (icUrl != null && !icUrl.isEmpty()) {
            String iconName = icUrl.replace(".png", "").replace(".jpg", "").replace(".jpeg", "");
            int resourceId = context.getResources().getIdentifier(iconName, "mipmap", context.getPackageName());

            if (resourceId != 0) {
                holder.channelIcon.setImageResource(resourceId);
            } else {
                resourceId = context.getResources().getIdentifier(iconName, "drawable", context.getPackageName());
                if (resourceId != 0) {
                    holder.channelIcon.setImageResource(resourceId);
                } else {
                    holder.channelIcon.setImageResource(R.drawable.ic_tv_default);
                }
            }
        } else {
            holder.channelIcon.setImageResource(R.drawable.ic_tv_default);
        }

        // 设置点击监听器
        holder.itemView.setOnClickListener(v -> {
            if (channelListener != null) {
                channelListener.onChannelClick(channel); // 修改：传递 Channel 对象
            }
        });
    }

    @Override
    public int getItemCount() {
        return channels.size();
    }

    static class ChannelIconViewHolder extends RecyclerView.ViewHolder {
        ImageView channelIcon;

        ChannelIconViewHolder(View itemView) {
            super(itemView);
            channelIcon = itemView.findViewById(R.id.channel_icon);
        }
    }
}