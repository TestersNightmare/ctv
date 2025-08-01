package com.dex7er.ctvplayer;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class ChannelParser {
    public static List<ChannelGroup> parseGroupedJson(String jsonContent) {
        Gson gson = new Gson();
        Type type = new TypeToken<List<ChannelGroup>>(){}.getType();
        List<ChannelGroup> groups = gson.fromJson(jsonContent, type);
        if (groups == null) {
            return new ArrayList<>();
        }
        
        // Filter out invalid groups and channels
        return groups.stream()
                .filter(group -> group != null && group.getChannels() != null)
                .map(group -> {
                    List<Channel> validChannels = group.getChannels().stream()
                            .filter(channel -> channel != null && channel.getName() != null && channel.getPid() != null)
                            .map(channel -> {
                                String url = channel.getUrl() != null ? channel.getUrl() :
                                        "https://www.yangshipin.cn/tv/home?pid=" + channel.getPid();
                                return new Channel(
                                        channel.getName(),
                                        url,
                                        channel.getPid(),
                                        channel.getIcUrl() != null ? channel.getIcUrl() : ""
                                );
                            })
                            .collect(Collectors.toList());
                    return new ChannelGroup(group.getGroupName(), group.getGroupId(), validChannels);
                })
                .collect(Collectors.toList());
    }

    // Keep the old method for backward compatibility
    public static List<Channel> parseJson(String jsonContent) {
        List<ChannelGroup> groups = parseGroupedJson(jsonContent);
        List<Channel> allChannels = new ArrayList<>();
        for (ChannelGroup group : groups) {
            if (group.getChannels() != null) {
                allChannels.addAll(group.getChannels());
            }
        }
        return allChannels;
    }
}