package com.dex7er.ctvplayer;

public class PlayHistory {
    String url;
    String icUrl;
    long timestamp;
    String name;

    public PlayHistory(String url, String icUrl, long timestamp, String name) {
        this.url = url;
        this.icUrl = icUrl;
        this.timestamp = timestamp;
        this.name = name;
    }

    public String getUrl() {
        return url;
    }

    public String getIcUrl() {
        return icUrl;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String getName() {
        return name;
    }
}