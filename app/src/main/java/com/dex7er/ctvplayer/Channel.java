package com.dex7er.ctvplayer;

public class Channel {
    private String name;
    private String url;
    private String icUrl;
    private String pid; // 添加pid字段用于兼容

    // 默认构造函数（Gson需要）
    public Channel() {}

    // 完整构造函数
    public Channel(String name, String url, String icUrl) {
        this.name = name;
        this.url = url;
        this.icUrl = icUrl;
    }

    // 从pid构造URL的构造函数
    public Channel(String name, String pid, String icUrl, boolean isPid) {
        this.name = name;
        this.pid = pid;
        this.icUrl = icUrl;
        if (isPid && pid != null && !pid.isEmpty()) {
            this.url = "https://www.yangshipin.cn/tv/home?pid=" + pid;
        } else {
            this.url = pid; // 如果不是pid，直接当作url使用
        }
    }

    // Getter和Setter方法
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getIcUrl() {
        return icUrl;
    }

    public void setIcUrl(String icUrl) {
        this.icUrl = icUrl;
    }

    public String getPid() {
        return pid;
    }

    public void setPid(String pid) {
        this.pid = pid;
        // 当设置pid时，自动更新url
        if (pid != null && !pid.isEmpty()) {
            this.url = "https://www.yangshipin.cn/tv/home?pid=" + pid;
        }
    }

    @Override
    public String toString() {
        return "Channel{" +
                "name='" + name + '\'' +
                ", url='" + url + '\'' +
                ", icUrl='" + icUrl + '\'' +
                ", pid='" + pid + '\'' +
                '}';
    }
}