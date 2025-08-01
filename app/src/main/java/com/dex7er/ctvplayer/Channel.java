package com.dex7er.ctvplayer;

public class Channel {
    private String name;
    private String url;
    private String pid; // Changed from int to String to match JSON
    private String ic_url;

    public Channel(String name, String url, String pid, String ic_url) {
        this.name = name;
        this.url = url;
        this.pid = pid;
        this.ic_url = ic_url;
    }

    public String getName() {
        return name;
    }

    public String getUrl() {
        return url;
    }

    public String getPid() {
        return pid;
    }

    public String getIcUrl() {
        return ic_url;
    }
}