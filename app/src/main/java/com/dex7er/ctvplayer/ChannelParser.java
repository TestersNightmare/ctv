package com.dex7er.ctvplayer;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class ChannelParser {
    public static List<Channel> parseJson(String jsonContent) {
        Gson gson = new Gson();
        Type type = new TypeToken<List<Channel>>(){}.getType();
        List<Channel> channels = gson.fromJson(jsonContent, type);
        if (channels == null) {
            return new ArrayList<>();
        }
        // 过滤无效频道并确保 URL 不为 null
        return channels.stream()
                .filter(channel -> channel != null && channel.getName() != null && channel.getPid() != 0)
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
    }
}