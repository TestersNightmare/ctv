package com.dex7er.ctvplayer;

public class Channel {
    private String name;
    private String url;
    private int pid;

    public Channel(String name, String url, int pid) {
        this.name = name;
        this.url = url;
        this.pid = pid;
    }

    public String getName() {
        return name;
    }

    public String getUrl() {
        return url;
    }

    public int getPid() {
        return pid;
    }
}