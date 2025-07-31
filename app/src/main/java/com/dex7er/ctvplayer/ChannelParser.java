package com.dex7er.ctvplayer;

import java.util.ArrayList;
import java.util.List;

public class ChannelParser {
    public static List<Channel> parseLinkFile(String content) {
        List<Channel> channels = new ArrayList<>();
        String[] lines = content.split("\n");
        for (String line : lines) {
            line = line.trim();
            if (!line.isEmpty()) {
                String[] parts = line.split("\\s+", 2);
                if (parts.length == 2) {
                    String name = parts[0];
                    String pidStr = parts[1];
                    try {
                        int pid = Integer.parseInt(pidStr);
                        String url = "https://www.yangshipin.cn/tv/home?pid=" + pidStr;
                        channels.add(new Channel(name, url, pid));
                    } catch (NumberFormatException e) {
                        // 忽略无效 PID
                    }
                }
            }
        }
        return channels;
    }
}