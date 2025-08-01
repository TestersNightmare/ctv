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
import java.util.List;

public class ChannelIconAdapter extends RecyclerView.Adapter<ChannelIconAdapter.ChannelIconViewHolder> {
    private static final String TAG = "ChannelIconAdapter";
    
    private Context context;
    private List<Channel> channels;
    private TwoColumnChannelAdapter.OnChannelClickListener channelListener;

    public ChannelIconAdapter(Context context, List<Channel> channels, 
                            TwoColumnChannelAdapter.OnChannelClickListener channelListener) {
        this.context = context;
        this.channels = channels;
        this.channelListener = channelListener;
    }

    @NonNull
    @Override
    public ChannelIconViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_channel_icon, parent, false);
        return new ChannelIconViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ChannelIconViewHolder holder, int position) {
        Channel channel = channels.get(position);
        if (channel == null) {
            Log.w(TAG, "Channel at position " + position + " is null");
            holder.channelIcon.setImageResource(R.drawable.ic_tv_default);
            return;
        }

        // Load icon from mipmap directory
        String icUrl = channel.getIcUrl();
        if (icUrl != null && !icUrl.isEmpty()) {
            // Remove file extension and convert to resource name
            String iconName = icUrl.replace(".png", "").replace(".jpg", "").replace(".jpeg", "");
            int resourceId = context.getResources().getIdentifier(iconName, "mipmap", context.getPackageName());
            
            if (resourceId != 0) {
                holder.channelIcon.setImageResource(resourceId);
            } else {
                // Fallback to drawable if not found in mipmap
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

        // Set click listener
        holder.itemView.setOnClickListener(v -> {
            String url = channel.getUrl();
            if (url != null && !url.isEmpty()) {
                Log.d(TAG, "Channel clicked: " + channel.getName() + " (" + url + ")");
                channelListener.onChannelClick(url);
            } else {
                Log.w(TAG, "Channel URL is invalid: " + channel.getName());
                Toast.makeText(context, "频道地址无效", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public int getItemCount() {
        return channels != null ? channels.size() : 0;
    }

    static class ChannelIconViewHolder extends RecyclerView.ViewHolder {
        ImageView channelIcon;

        ChannelIconViewHolder(View itemView) {
            super(itemView);
            channelIcon = itemView.findViewById(R.id.channel_icon);
        }
    }
}