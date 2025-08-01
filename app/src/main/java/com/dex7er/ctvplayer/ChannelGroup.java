package com.dex7er.ctvplayer;

import java.util.List;

public class ChannelGroup {
    private String group_name;
    private String group_id;
    private List<Channel> channels;

    public ChannelGroup(String group_name, String group_id, List<Channel> channels) {
        this.group_name = group_name;
        this.group_id = group_id;
        this.channels = channels;
    }

    public String getGroupName() {
        return group_name;
    }

    public String getGroupId() {
        return group_id;
    }

    public List<Channel> getChannels() {
        return channels;
    }
}