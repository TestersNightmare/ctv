package com.dex7er.ctvplayer;

import android.util.Log;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class ChannelParser {
    private static final String TAG = "ChannelParser";
    private static final String BASE_URL = "https://www.yangshipin.cn/tv/home?pid=";

    public static List<ChannelGroup> parseGroupedJson(String jsonString) {
        try {
            Log.d(TAG, "Parsing grouped JSON, length: " + jsonString.length());

            Gson gson = new Gson();
            JsonParser parser = new JsonParser();
            JsonElement element = parser.parse(jsonString);

            if (element.isJsonArray()) {
                JsonArray jsonArray = element.getAsJsonArray();
                List<ChannelGroup> groups = new ArrayList<>();

                for (JsonElement groupElement : jsonArray) {
                    if (groupElement.isJsonObject()) {
                        JsonObject groupObject = groupElement.getAsJsonObject();

                        String groupName = groupObject.has("group_name") ?
                                groupObject.get("group_name").getAsString() : "未知分组";
                        String groupId = groupObject.has("group_id") ?
                                groupObject.get("group_id").getAsString() : "";

                        List<Channel> channels = new ArrayList<>();

                        if (groupObject.has("channels") && groupObject.get("channels").isJsonArray()) {
                            JsonArray channelsArray = groupObject.getAsJsonArray("channels");

                            for (JsonElement channelElement : channelsArray) {
                                if (channelElement.isJsonObject()) {
                                    JsonObject channelObject = channelElement.getAsJsonObject();

                                    String name = channelObject.has("name") ?
                                            channelObject.get("name").getAsString() : "";
                                    String pid = channelObject.has("pid") ?
                                            channelObject.get("pid").getAsString() : "";
                                    String icUrl = channelObject.has("ic_url") ?
                                            channelObject.get("ic_url").getAsString() : "";

                                    // 将pid转换为完整URL
                                    String url = "";
                                    if (!pid.isEmpty()) {
                                        url = BASE_URL + pid;
                                    }

                                    if (!name.isEmpty() && !url.isEmpty()) {
                                        Channel channel = new Channel(name, url, icUrl);
                                        channels.add(channel);
                                        Log.d(TAG, "Added channel: " + name + " -> " + url);
                                    }
                                }
                            }
                        }

                        if (!channels.isEmpty()) {
                            ChannelGroup group = new ChannelGroup(groupName, groupId, channels);
                            groups.add(group);
                            Log.d(TAG, "Added group: " + groupName + " with " + channels.size() + " channels");
                        }
                    }
                }

                Log.d(TAG, "Successfully parsed " + groups.size() + " groups");
                return groups;
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to parse grouped JSON: " + e.getMessage(), e);
        }
        return null;
    }

    public static List<Channel> parseJson(String jsonString) {
        try {
            Log.d(TAG, "Parsing simple JSON, length: " + jsonString.length());

            Gson gson = new Gson();
            JsonParser parser = new JsonParser();
            JsonElement element = parser.parse(jsonString);

            List<Channel> channels = new ArrayList<>();

            if (element.isJsonArray()) {
                // 如果是分组格式，先解析为分组再展开
                List<ChannelGroup> groups = parseGroupedJson(jsonString);
                if (groups != null) {
                    for (ChannelGroup group : groups) {
                        if (group.getChannels() != null) {
                            channels.addAll(group.getChannels());
                        }
                    }
                } else {
                    // 尝试解析为简单频道列表
                    Type listType = new TypeToken<List<Channel>>(){}.getType();
                    List<Channel> parsedChannels = gson.fromJson(jsonString, listType);
                    if (parsedChannels != null) {
                        channels.addAll(parsedChannels);
                    }
                }
            } else if (element.isJsonObject()) {
                // 单个频道对象
                Channel channel = gson.fromJson(jsonString, Channel.class);
                if (channel != null) {
                    channels.add(channel);
                }
            }

            Log.d(TAG, "Successfully parsed " + channels.size() + " channels");
            return channels;
        } catch (Exception e) {
            Log.e(TAG, "Failed to parse JSON: " + e.getMessage(), e);
            return new ArrayList<>();
        }
    }
}