package com.dex7er.ctvplayer;

import android.annotation.SuppressLint;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.appcompat.app.AppCompatActivity;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private List<ChannelGroup> channelGroups = new ArrayList<>();
    private static final String DEFAULT_URL = "https://www.yangshipin.cn/tv/home?pid=600001859";
    private static final String CHANNELS_URL = "https://gitee.com/magasb/ctvplayer/raw/master/app/src/main/assets/channels.json";
    private static final String PREFS_NAME = "CTVPlayerPrefs";
    private static final String CHANNELS_KEY = "channels";
    private static final String DESKTOP_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36";

    private WebView webView;
    private ProgressBar progressBar;
    private DrawerLayout drawerLayout;
    private RecyclerView channelList;
    private FrameLayout loadingLayout;
    private ImageView loadingChannelIcon;
    private TextView loadingChannelText;
    private ProgressBar loadingIndicator;
    private ImageView appBackgroundIcon;
    private List<Channel> channels = new ArrayList<>();
    private SharedPreferences prefs;
    private Gson gson;
    private boolean isConfigValid = false;
    private String currentChannelName;

    // 自定义透明度（可通过配置文件或代码调整）
    private float channelIconAlpha = 1.0f; // 默认不透明
    private float loadingIndicatorAlpha = 1.0f; // 默认不透明
    private float channelTextAlpha = 1.0f; // 默认不透明
    private float backgroundIconScale = 0.8f; // 背景图标缩放比例

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        gson = new Gson();
        initViews();
        setupWebView();
        loadChannels();
        setImmersiveMode();
    }

    private void initViews() {
        webView = findViewById(R.id.webview);
        progressBar = findViewById(R.id.progress_bar);
        drawerLayout = findViewById(R.id.drawer_layout);
        channelList = findViewById(R.id.channel_list);
        loadingLayout = findViewById(R.id.loading_layout);
        loadingChannelIcon = findViewById(R.id.loading_channel_icon);
        loadingChannelText = findViewById(R.id.loading_channel_text);
        loadingIndicator = findViewById(R.id.loading_indicator);
        appBackgroundIcon = findViewById(R.id.app_background_icon);
        channelList.setLayoutManager(new LinearLayoutManager(this));

        // 设置 WebView 初始透明度为 0%
        webView.setAlpha(0.0f);
        Log.d(TAG, "WebView alpha set to 0.0 at: " + System.currentTimeMillis());

        // 设置自定义透明度和缩放
        loadingChannelIcon.setAlpha(channelIconAlpha);
        loadingIndicator.setAlpha(loadingIndicatorAlpha);
        loadingChannelText.setAlpha(channelTextAlpha);
        appBackgroundIcon.setAlpha(1.0f); // 背景始终不透明
        appBackgroundIcon.setScaleX(backgroundIconScale);
        appBackgroundIcon.setScaleY(backgroundIconScale);
        // 预设置默认背景图标
        appBackgroundIcon.setImageResource(R.mipmap.app_logo);
        // 确保 loading_layout 在 WebView 之上
        loadingLayout.bringToFront();

        // 应用旋转动画
        Animation rotateAnimation = AnimationUtils.loadAnimation(this, R.anim.rotate_loading);
        loadingIndicator.startAnimation(rotateAnimation);

        Log.d(TAG, "Views initialized at: " + System.currentTimeMillis());
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebView() {
        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setLoadWithOverviewMode(true);
        webSettings.setUseWideViewPort(true);
        webSettings.setBuiltInZoomControls(false);
        webSettings.setDisplayZoomControls(false);
        webSettings.setSupportZoom(false);
        webSettings.setDefaultTextEncodingName("utf-8");
        webSettings.setMediaPlaybackRequiresUserGesture(false);
        webSettings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        webSettings.setUserAgentString(DESKTOP_UA);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                view.loadUrl(url);
                return true;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                progressBar.setVisibility(View.GONE);
                view.evaluateJavascript(
                        "javascript:(function() { " +
                                "  Object.defineProperty(navigator, 'userAgent', { " +
                                "    get: function() { return '" + DESKTOP_UA + "'; } " +
                                "  }); " +
                                "  Object.defineProperty(window, 'screen', { " +
                                "    value: { " +
                                "      width: 1920, " +
                                "      height: 1080, " +
                                "      availWidth: 1920, " +
                                "      availHeight: 1080 " +
                                "    }, writable: false " +
                                "  }); " +
                                "})()",
                        null
                );
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    view.evaluateJavascript(
                            "javascript:(function() { " +
                                    "  function displayVideoOnly(video) { " +
                                    "    if (!video) { " +
                                    "      return 'No video element found'; " +
                                    "    } " +
                                    "    document.body.innerHTML = '<div style=\"margin:0;padding:0;width:100%;height:100vh;overflow:hidden;\">' + video.outerHTML + '</div>'; " +
                                    "    document.body.style.margin = '0'; " +
                                    "    document.body.style.padding = '0'; " +
                                    "    var newVideo = document.querySelector('video'); " +
                                    "    newVideo.style.width = '100%'; " +
                                    "    newVideo.style.height = '100%'; " +
                                    "    newVideo.style.objectFit = 'contain'; " +
                                    "    newVideo.style.position = 'absolute'; " +
                                    "    newVideo.style.top = '0'; " +
                                    "    newVideo.style.left = '0'; " +
                                    "    newVideo.controls = true; " +
                                    "    newVideo.play(); " +
                                    "    if (newVideo.requestFullscreen) newVideo.requestFullscreen(); " +
                                    "    else if (newVideo.webkitRequestFullscreen) newVideo.webkitRequestFullscreen(); " +
                                    "    return 'Video found'; " +
                                    "  } " +
                                    "  var video = document.querySelector('video') || " +
                                    "    Array.from(document.querySelectorAll('iframe')).map(f => f.contentDocument?.querySelector('video')).find(v => v) || " +
                                    "    Array.from(document.querySelectorAll('*')).map(el => el.shadowRoot?.querySelector('video')).find(v => v); " +
                                    "  if (video) { " +
                                    "    return displayVideoOnly(video); " +
                                    "  } else { " +
                                    "    var observer = new MutationObserver(function(mutations) { " +
                                    "      var video = document.querySelector('video') || " +
                                    "        Array.from(document.querySelectorAll('iframe')).map(f => f.contentDocument?.querySelector('video')).find(v => v) || " +
                                    "        Array.from(document.querySelectorAll('*')).map(el => el.shadowRoot?.querySelector('video')).find(v => v); " +
                                    "      if (video) { " +
                                    "        displayVideoOnly(video); " +
                                    "        observer.disconnect(); " +
                                    "      } " +
                                    "    }); " +
                                    "    observer.observe(document.body, { childList: true, subtree: true }); " +
                                    "    return 'Observing DOM for video'; " +
                                    "  } " +
                                    "})()",
                            result -> {
                                Log.d(TAG, "JavaScript result: " + result);
                                if (result != null && result.contains("Video found")) {
                                    runOnUiThread(() -> {
                                        // 为 WebView 添加透明度动画：从 0.1 到 1.0
                                        AlphaAnimation alphaAnimation = new AlphaAnimation(0.1f, 1.0f);
                                        alphaAnimation.setDuration(500); // 500ms 动画
                                        alphaAnimation.setFillAfter(true);
                                        webView.startAnimation(alphaAnimation);
                                        webView.setAlpha(1.0f);
                                        Log.d(TAG, "WebView alpha set to 1.0 at: " + System.currentTimeMillis());

                                        // 隐藏 loading_layout
                                        Animation fadeOut = AnimationUtils.loadAnimation(MainActivity.this, R.anim.fade_out);
                                        loadingLayout.startAnimation(fadeOut);
                                        loadingLayout.setVisibility(View.GONE);
                                        Log.d(TAG, "Loading layout hidden at: " + System.currentTimeMillis());
                                    });
                                }
                            }
                    );
                }, 1000);
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                super.onReceivedError(view, request, error);
                Log.e(TAG, "WebView error: " + error.toString());
                if (isConfigValid) {
                    runOnUiThread(() -> loadChannelsFromJson());
                }
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {
                setContentView(view);
                setImmersiveMode();
                callback.onCustomViewHidden();
            }

            @Override
            public void onHideCustomView() {
                setContentView(R.layout.activity_main);
                initViews();
                setupWebView();
                loadChannels();
                setImmersiveMode();
            }

            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                if (newProgress == 100) {
                    progressBar.setVisibility(View.GONE);
                } else {
                    progressBar.setVisibility(View.VISIBLE);
                    progressBar.setProgress(newProgress);
                }
            }
        });

        webView.loadUrl(DEFAULT_URL);
        webView.setOnTouchListener((v, event) -> {
            drawerLayout.openDrawer(channelList);
            return false;
        });
    }

    private void loadChannels() {
        String channelsJson = prefs.getString(CHANNELS_KEY, null);
        if (channelsJson != null && !channelsJson.trim().isEmpty()) {
            Log.d(TAG, "Loading channels from preferences");
            try {
                Type type = new TypeToken<List<Channel>>(){}.getType();
                List<Channel> loadedChannels = gson.fromJson(channelsJson, type);
                if (loadedChannels != null) {
                    channels = loadedChannels;
                    // 过滤掉 null 频道
                    channels.removeIf(c -> c == null);
                    Log.d(TAG, "Loaded " + channels.size() + " channels from preferences");
                    validateChannels();
                } else {
                    Log.w(TAG, "Failed to parse channels from preferences, loading from config");
                    loadChannelsFromConfig();
                }
            } catch (Exception e) {
                Log.e(TAG, "Error parsing channels from preferences: " + e.getMessage());
                loadChannelsFromConfig();
            }
        } else {
            Log.d(TAG, "No channels in preferences, loading from config");
            loadChannelsFromConfig();
        }
    }

    private void loadChannelsFromConfig() {
        try {
            Log.d(TAG, "Attempting to load channels from config file");
            String json = readAssetFile("channels.config");
            if (json != null && !json.trim().isEmpty()) {
                channels = ChannelParser.parseJson(json);
                if (channels != null) {
                    channels.removeIf(c -> c == null);
                    isConfigValid = true;
                    Log.d(TAG, "Loaded " + channels.size() + " channels from config");
                    validateChannels();
                } else {
                    Log.w(TAG, "Failed to parse config, loading from JSON");
                    loadChannelsFromJson();
                }
            } else {
                Log.d(TAG, "No config file found, loading from JSON");
                loadChannelsFromJson();
            }
        } catch (IOException e) {
            Log.d(TAG, "Config file not found, loading from JSON: " + e.getMessage());
            loadChannelsFromJson();
        }
    }

    private void loadChannelsFromJson() {
        try {
            Log.d(TAG, "Attempting to load channels from assets JSON");
            String json = readAssetFile("channels.json");
            if (json != null && !json.trim().isEmpty()) {
                Log.d(TAG, "Assets JSON content preview: " + json.substring(0, Math.min(200, json.length())));
                channelGroups = ChannelParser.parseGroupedJson(json);
                if (channelGroups != null && !channelGroups.isEmpty()) {
                    isConfigValid = false;
                    Log.d(TAG, "Loaded " + channelGroups.size() + " channel groups from assets JSON");
                    
                    // Convert to flat list for backward compatibility
                    channels = new ArrayList<>();
                    for (ChannelGroup group : channelGroups) {
                        if (group.getChannels() != null) {
                            channels.addAll(group.getChannels());
                        }
                    }
                    channels.removeIf(c -> c == null);
                    validateChannels();
                } else {
                    Log.e(TAG, "Failed to parse channels from assets JSON");
                    channelGroups = new ArrayList<>();
                    channels = new ArrayList<>();
                    setupChannelList();
                }
            } else {
                Log.e(TAG, "Assets JSON file is empty or null");
                channelGroups = new ArrayList<>();
                channels = new ArrayList<>();
                setupChannelList();
            }
        } catch (IOException e) {
            Log.e(TAG, "Failed to read assets JSON: " + e.getMessage());
            channelGroups = new ArrayList<>();
            channels = new ArrayList<>();
            setupChannelList();
        }
    }

    private String readAssetFile(String fileName) throws IOException {
        try (InputStream inputStream = getAssets().open(fileName)) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
            StringBuilder builder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line);
            }
            return builder.toString();
        } catch (IOException e) {
            throw e;
        }
    }

    private void validateChannels() {
        if (channels == null || channels.isEmpty()) {
            Log.w(TAG, "No channels to validate");
            if (isConfigValid) {
                Log.d(TAG, "Config was valid but empty, trying JSON");
                loadChannelsFromJson();
            }
            return;
        }

        Log.d(TAG, "Validating " + channels.size() + " channels");
        new Thread(() -> {
            boolean allFailed = true;
            int validCount = 0;

            for (Channel channel : channels) {
                if (channel == null || channel.getUrl() == null || channel.getUrl().isEmpty()) {
                    continue;
                }
                try {
                    OkHttpClient client = new OkHttpClient();
                    Request request = new Request.Builder().url(channel.getUrl()).head().build(); // 使用 HEAD 请求更快
                    Response response = client.newCall(request).execute();
                    if (response.isSuccessful()) {
                        allFailed = false;
                        validCount++;
                        break; // 找到一个有效的就够了
                    }
                } catch (IOException e) {
                    Log.d(TAG, "Channel validation failed: " + channel.getUrl());
                }
            }

            Log.d(TAG, "Channel validation completed. Valid channels found: " + validCount);

            if (allFailed && isConfigValid) {
                Log.w(TAG, "All config channels failed, falling back to JSON");
                runOnUiThread(() -> loadChannelsFromJson());
            } else {
                runOnUiThread(() -> {
                    setupChannelList();
                    if (!channels.isEmpty() && channels.get(0).getUrl() != null) {
                        // 设置默认频道名称，防止 null
                        currentChannelName = channels.get(0).getName() != null ? channels.get(0).getName() : "未知频道";
                        Log.d(TAG, "Default channel set: " + currentChannelName + " at: " + System.currentTimeMillis());
                        loadChannel(channels.get(0));
                    } else {
                        Log.d(TAG, "No valid channels, loading default URL");
                        webView.loadUrl(DEFAULT_URL);
                    }
                });
            }
        }).start();
    }

    private void setupChannelList() {
        // Ensure no null channels or groups
        if (channelGroups != null) {
            for (ChannelGroup group : channelGroups) {
                if (group.getChannels() != null) {
                    group.getChannels().removeIf(c -> c == null);
                }
            }
        } else {
            channelGroups = new ArrayList<>();
        }

        if (channels != null) {
            channels.removeIf(c -> c == null);
        } else {
            channels = new ArrayList<>();
        }

        Log.d(TAG, "Setting up channel list with " + channelGroups.size() + " groups");

        // Set GridLayoutManager with 3 columns for channel icons
        GridLayoutManager gridLayoutManager = new GridLayoutManager(this, 3);
        gridLayoutManager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                // Update button spans all 3 columns
                if (position == channels.size()) {
                    return 3;
                }
                // Each channel icon spans 1 column
                return 1;
            }
        });
        channelList.setLayoutManager(gridLayoutManager);

        TwoColumnChannelAdapter adapter = new TwoColumnChannelAdapter(
                this,
                channelGroups,
                url -> {
                    if (url != null && !url.isEmpty()) {
                        Channel channel = channels.stream()
                                .filter((c) -> c != null && url.equals(c.getUrl()))
                                .findFirst()
                                .orElse(null);
                        if (channel != null) {
                            long startTime = System.currentTimeMillis();
                            Log.d(TAG, "Channel clicked at: " + startTime + ", name: " + channel.getName());
                            currentChannelName = channel.getName() != null ? channel.getName() : "未知频道";
                            loadingChannelText.setText(getString(R.string.loading_channel, "正在播放 " + currentChannelName));
                            loadingChannelIcon.setImageResource(R.drawable.ic_tv_default);
                            loadingChannelIcon.setAlpha(channelIconAlpha);
                            loadingIndicator.setAlpha(loadingIndicatorAlpha);
                            loadingChannelText.setAlpha(channelTextAlpha);
                            appBackgroundIcon.setAlpha(1.0f);
                            appBackgroundIcon.setScaleX(backgroundIconScale);
                            appBackgroundIcon.setScaleY(backgroundIconScale);
                            webView.setAlpha(0.1f);
                            Log.d(TAG, "WebView alpha reset to 0.1 at: " + System.currentTimeMillis());
                            loadingLayout.post(() -> {
                                loadingLayout.setVisibility(View.VISIBLE);
                                Log.d(TAG, "Loading layout visible at: " + System.currentTimeMillis());
                            });
                            loadChannel(channel);
                        }
                    }
                },
                this::updateChannels
        );
        channelList.setAdapter(adapter);
    }
    private void loadChannel(Channel channel) {
        if (channel == null || channel.getUrl() == null || channel.getUrl().isEmpty()) {
            Log.e(TAG, "Invalid channel or URL");
            return;
        }

        // 确保 currentChannelName 不为 null
        if (currentChannelName == null) {
            currentChannelName = channel.getName() != null ? channel.getName() : "未知频道";
            Log.d(TAG, "currentChannelName initialized in loadChannel: " + currentChannelName);
        }

        // 加载频道图标
        String iconName = currentChannelName.toLowerCase().replace(" ", "_");
        int resourceId = getResources().getIdentifier(iconName, "drawable", getPackageName());
        String icUrl = channel.getIcUrl() != null ? channel.getIcUrl() : "";
        if (resourceId != 0) {
            runOnUiThread(() -> {
                loadingChannelIcon.setImageResource(resourceId);
                Log.d(TAG, "Channel icon set from resource at: " + System.currentTimeMillis());
            });
        } else if (!icUrl.isEmpty()) {
            runOnUiThread(() -> {
                Glide.with(this).load(icUrl).error(R.drawable.ic_tv_default).into(loadingChannelIcon);
                Log.d(TAG, "Channel icon set from URL at: " + System.currentTimeMillis());
            });
        } else {
            runOnUiThread(() -> {
                loadingChannelIcon.setImageResource(R.drawable.ic_tv_default);
                Log.d(TAG, "Channel icon set to default at: " + System.currentTimeMillis());
            });
        }

        // 设置加载界面文本
        runOnUiThread(() -> {
            loadingChannelText.setText(getString(R.string.loading_channel, "正在播放 " + currentChannelName));
            Log.d(TAG, "Loading channel text set at: " + System.currentTimeMillis());
        });

        // 延迟加载 WebView，确保 loading_layout 先显示
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            Log.d(TAG, "Loading WebView URL at: " + System.currentTimeMillis() + ", URL: " + channel.getUrl());
            webView.loadUrl(channel.getUrl());
            drawerLayout.closeDrawer(channelList, false); // 禁用抽屉动画
            Log.d(TAG, "Drawer closed at: " + System.currentTimeMillis());
        }, 100); // 延迟 100ms
    }

    private void updateChannels() {
        Log.d(TAG, "Update channels button clicked");

        // 显示更新提示
        runOnUiThread(() -> {
            Toast.makeText(this, "正在更新频道列表...", Toast.LENGTH_SHORT).show();
        });

        new Thread(() -> {
            try {
                Log.d(TAG, "Starting to update channels from: " + CHANNELS_URL);
                OkHttpClient client = new OkHttpClient();
                Request request = new Request.Builder()
                        .url(CHANNELS_URL)
                        .addHeader("User-Agent", DESKTOP_UA)
                        .build();
                Response response = client.newCall(request).execute();

                if (response.isSuccessful() && response.body() != null) {
                    String json = response.body().string();
                    Log.d(TAG, "Downloaded content length: " + json.length());
                    Log.d(TAG, "Downloaded content preview: " + json.substring(0, Math.min(200, json.length())));

                    // 验证是否为有效的 JSON 数组
                    if (json.trim().startsWith("[") && json.trim().endsWith("]")) {
                        List<Channel> newChannels = ChannelParser.parseJson(json);

                        if (newChannels != null && !newChannels.isEmpty()) {
                            // 过滤掉 null 频道
                            newChannels.removeIf(c -> c == null);

                            if (!newChannels.isEmpty()) {
                                Log.d(TAG, "Successfully parsed " + newChannels.size() + " channels from download");

                                // 保存到本地文件和 SharedPreferences
                                saveChannelsToConfig(json);
                                prefs.edit().putString(CHANNELS_KEY, json).apply();

                                // 更新当前频道列表
                                channels = newChannels;
                                isConfigValid = true;

                                runOnUiThread(() -> {
                                    Toast.makeText(MainActivity.this, "频道列表更新成功，共 " + channels.size() + " 个频道", Toast.LENGTH_SHORT).show();
                                    setupChannelList();
                                    if (!channels.isEmpty() && channels.get(0).getUrl() != null) {
                                        currentChannelName = channels.get(0).getName() != null ? channels.get(0).getName() : "未知频道";
                                        webView.setAlpha(0.1f);
                                        loadChannel(channels.get(0));
                                    } else {
                                        webView.loadUrl(DEFAULT_URL);
                                    }
                                });
                            } else {
                                Log.e(TAG, "No valid channels found in downloaded JSON after filtering");
                                runOnUiThread(() -> {
                                    Toast.makeText(MainActivity.this, "下载的频道列表为空", Toast.LENGTH_SHORT).show();
                                });
                            }
                        } else {
                            Log.e(TAG, "Failed to parse channels from downloaded JSON");
                            runOnUiThread(() -> {
                                Toast.makeText(MainActivity.this, "频道列表解析失败", Toast.LENGTH_SHORT).show();
                            });
                        }
                    } else {
                        Log.e(TAG, "Downloaded content is not a valid JSON array");
                        Log.e(TAG, "Content starts with: " + json.substring(0, Math.min(100, json.length())));
                        runOnUiThread(() -> {
                            Toast.makeText(MainActivity.this, "下载的内容格式不正确", Toast.LENGTH_SHORT).show();
                        });
                    }
                } else {
                    Log.e(TAG, "HTTP request failed: " + response.code() + " " + response.message());
                    runOnUiThread(() -> {
                        Toast.makeText(MainActivity.this, "网络请求失败: " + response.code(), Toast.LENGTH_SHORT).show();
                    });
                }
            } catch (Exception e) {
                Log.e(TAG, "Update channels failed: " + e.getMessage(), e);
                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this, "更新频道列表失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    private void saveChannelsToConfig(String json) {
        try {
            File file = new File(getFilesDir(), "channels.config");
            try (FileOutputStream fos = new FileOutputStream(file)) {
                fos.write(json.getBytes());
            }
            Log.d(TAG, "Successfully saved channels to config file");
        } catch (IOException e) {
            Log.e(TAG, "Save channels config failed: " + e.getMessage());
        }
    }

    private void setImmersiveMode() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                        View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                        View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                        View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                        View.SYSTEM_UI_FLAG_FULLSCREEN |
                        View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        );
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            setImmersiveMode();
        }
    }

    @Override
    public void onBackPressed() {
        if (drawerLayout.isDrawerOpen(channelList)) {
            drawerLayout.closeDrawer(channelList, false);
        } else if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.destroy();
        }
        super.onDestroy();
    }
}