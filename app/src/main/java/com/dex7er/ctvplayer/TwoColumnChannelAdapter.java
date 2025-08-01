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
import androidx.recyclerview.widget.GridLayoutManager;
import java.util.ArrayList;
import java.util.List;

public class TwoColumnChannelAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private static final String TAG = "TwoColumnChannelAdapter";
    private static final int TYPE_CHANNEL = 0;
    private static final int TYPE_UPDATE_BUTTON = 1;

    private List<Channel> allChannels; // Flattened list of all channels
    private OnChannelClickListener channelListener;
    private OnUpdateButtonClickListener updateListener;
    private Context context;

    public interface OnChannelClickListener {
        void onChannelClick(String url);
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

        // Flatten all channels from all groups
        if (channelGroups != null) {
            for (ChannelGroup group : channelGroups) {
                if (group.getChannels() != null) {
                    allChannels.addAll(group.getChannels());
                }
            }
        }

        // Remove null channels
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
            if (position >= allChannels.size()) {
                Log.w(TAG, "Position " + position + " is out of bounds for channels list of size " + allChannels.size());
                return;
            }

            Channel channel = allChannels.get(position);
            if (channel == null) {
                Log.w(TAG, "Channel at position " + position + " is null");
                return;
            }

            ChannelViewHolder channelHolder = (ChannelViewHolder) holder;

            // Hide channel name text, only show icon
            if (channelHolder.channelName != null) {
                channelHolder.channelName.setVisibility(View.GONE);
            }
            if (channelHolder.channelNameOnIcon != null) {
                channelHolder.channelNameOnIcon.setVisibility(View.GONE);
            }

            // Load icon from mipmap directory
            String icUrl = channel.getIcUrl();
            if (icUrl != null && !icUrl.isEmpty()) {
                // Remove file extension and convert to resource name
                String iconName = icUrl.replace(".png", "").replace(".jpg", "").replace(".jpeg", "");
                int resourceId = context.getResources().getIdentifier(iconName, "mipmap", context.getPackageName());

                if (resourceId != 0) {
                    channelHolder.channelIcon.setImageResource(resourceId);
                } else {
                    // Fallback to drawable if not found in mipmap
                    resourceId = context.getResources().getIdentifier(iconName, "drawable", context.getPackageName());
                    if (resourceId != 0) {
                        channelHolder.channelIcon.setImageResource(resourceId);
                    } else {
                        channelHolder.channelIcon.setImageResource(R.drawable.ic_tv_default);
                    }
                }
            } else {
                channelHolder.channelIcon.setImageResource(R.drawable.ic_tv_default);
            }

            // Set click listener
            channelHolder.itemView.setOnClickListener(v -> {
                String url = channel.getUrl();
                if (url != null && !url.isEmpty()) {
                    Log.d(TAG, "Channel clicked: " + channel.getName() + " (" + url + ")");
                    channelListener.onChannelClick(url);
                } else {
                    Log.w(TAG, "Channel URL is invalid: " + channel.getName());
                    Toast.makeText(context, "频道地址无效", Toast.LENGTH_SHORT).show();
                }
            });

        } else if (holder instanceof UpdateButtonViewHolder) {
            ((UpdateButtonViewHolder) holder).updateButton.setOnClickListener(v -> {
                Log.d(TAG, "Update button clicked");
                if (updateListener != null) {
                    updateListener.onUpdateButtonClick();
                }
            });
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
        }
    }
}