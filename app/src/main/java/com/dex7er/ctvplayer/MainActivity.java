package com.dex7er.ctvplayer;

import android.annotation.SuppressLint;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
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
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.GridLayoutManager;
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
import java.util.Random;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import android.util.Log;
import android.widget.Toast;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private static final String DEFAULT_URL = "https://www.yangshipin.cn/tv/home?pid=600001859";
    private static final String CHANNELS_URL = "https://gitee.com/magasb/ctvplayer/raw/master/app/src/main/assets/channels.json";
    private static final String PREFS_NAME = "CTVPlayerPrefs";
    private static final String CHANNELS_KEY = "channels";

    // 桌面 UserAgent 配置
    private static final String[] DESKTOP_USER_AGENTS = {
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:109.0) Gecko/20100101 Firefox/120.0",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:109.0) Gecko/20100101 Firefox/119.0",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.1 Safari/605.1.15",
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
            "Mozilla/5.0 (X11; Ubuntu; Linux x86_64; rv:109.0) Gecko/20100101 Firefox/120.0"
    };

    // 桌面分辨率配置
    private static final String[][] DESKTOP_RESOLUTIONS = {
            {"1920", "1080"},  // 1080P
            {"2560", "1440"},  // 2K
            {"3440", "1440"},  // 3K 超宽屏
            {"3840", "2160"},  // 4K
            {"2560", "1600"},  // 2K 16:10
            {"3840", "1600"},  // 3K+ 超宽屏
            {"5120", "2880"},  // 5K
            {"1920", "1200"}   // 1080P+ 16:10
    };

    private WebView webView;
    private ProgressBar progressBar;
    private DrawerLayout drawerLayout;
    private RecyclerView channelList;

    // 简化加载界面组件
    private FrameLayout simpleLoadingLayout;
    private ImageView currentChannelIcon;
    private ProgressBar loadingSpinner;

    private List<Channel> channels = new ArrayList<>();
    private List<ChannelGroup> channelGroups = new ArrayList<>();
    private SharedPreferences prefs;
    private Gson gson;
    private boolean isConfigValid = false;
    private String currentChannelName;
    private Random random = new Random();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        gson = new Gson();
        initViews();
        initSimpleLoadingLayout();
        setupWebView();
        loadChannels();
        setImmersiveMode();
    }

    private void initViews() {
        webView = findViewById(R.id.webview);
        progressBar = findViewById(R.id.progress_bar);
        drawerLayout = findViewById(R.id.drawer_layout);
        channelList = findViewById(R.id.channel_list);

        // 使用 GridLayoutManager，每行3列
        GridLayoutManager gridLayoutManager = new GridLayoutManager(this, 3);
        gridLayoutManager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                // 更新按钮占据3列
                return position == channels.size() ? 3 : 1;
            }
        });
        channelList.setLayoutManager(gridLayoutManager);
    }

    private void initSimpleLoadingLayout() {
        simpleLoadingLayout = new FrameLayout(this);
        simpleLoadingLayout.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        simpleLoadingLayout.setBackgroundColor(0xCC000000);
        simpleLoadingLayout.setVisibility(View.GONE);

        LinearLayout centerLayout = new LinearLayout(this);
        centerLayout.setOrientation(LinearLayout.VERTICAL);
        centerLayout.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams centerParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT);
        centerParams.gravity = Gravity.CENTER;
        centerLayout.setLayoutParams(centerParams);

        currentChannelIcon = new ImageView(this);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(
                dpToPx(120), dpToPx(120));
        iconParams.bottomMargin = dpToPx(24);
        currentChannelIcon.setLayoutParams(iconParams);
        currentChannelIcon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        currentChannelIcon.setImageResource(R.drawable.ic_tv_default);

        loadingSpinner = new ProgressBar(this);
        LinearLayout.LayoutParams spinnerParams = new LinearLayout.LayoutParams(
                dpToPx(48), dpToPx(48));
        loadingSpinner.setLayoutParams(spinnerParams);

        centerLayout.addView(currentChannelIcon);
        centerLayout.addView(loadingSpinner);
        simpleLoadingLayout.addView(centerLayout);

        FrameLayout mainLayout = findViewById(android.R.id.content);
        mainLayout.addView(simpleLoadingLayout);
    }

    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }

    // 获取随机桌面 UserAgent
    private String getRandomDesktopUserAgent() {
        return DESKTOP_USER_AGENTS[random.nextInt(DESKTOP_USER_AGENTS.length)];
    }

    // 获取随机桌面分辨率
    private String[] getRandomDesktopResolution() {
        return DESKTOP_RESOLUTIONS[random.nextInt(DESKTOP_RESOLUTIONS.length)];
    }

    // 设置模拟桌面环境
    private void setupDesktopEnvironment() {
        String userAgent = getRandomDesktopUserAgent();
        String[] resolution = getRandomDesktopResolution();

        android.util.Log.d(TAG, "Setting desktop environment:");
        android.util.Log.d(TAG, "UserAgent: " + userAgent);
        android.util.Log.d(TAG, "Resolution: " + resolution[0] + "x" + resolution[1]);

        // 注入 JavaScript 来模拟桌面分辨率和环境
        String desktopScript =
                "(function() {" +
                        "Object.defineProperty(window.screen, 'width', { value: " + resolution[0] + ", writable: false });" +
                        "Object.defineProperty(window.screen, 'height', { value: " + resolution[1] + ", writable: false });" +
                        "Object.defineProperty(window.screen, 'availWidth', { value: " + resolution[0] + ", writable: false });" +
                        "Object.defineProperty(window.screen, 'availHeight', { value: " + (Integer.parseInt(resolution[1]) - 40) + ", writable: false });" +
                        "Object.defineProperty(navigator, 'platform', { value: 'Win32', writable: false });" +
                        "Object.defineProperty(navigator, 'maxTouchPoints', { value: 0, writable: false });" +
                        "Object.defineProperty(navigator, 'userAgent', { value: '" + userAgent + "', writable: false });" +
                        "window.ontouchstart = undefined;" +
                        "window.ontouchend = undefined;" +
                        "window.ontouchmove = undefined;" +
                        "window.ontouchcancel = undefined;" +
                        "console.log('Desktop environment simulated: ' + " + resolution[0] + " + 'x' + " + resolution[1] + ");" +
                        "})();";

        webView.evaluateJavascript(desktopScript, null);
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

        // 设置随机桌面 UserAgent
        String randomUserAgent = getRandomDesktopUserAgent();
        webSettings.setUserAgentString(randomUserAgent);
        android.util.Log.d(TAG, "WebView UserAgent set to: " + randomUserAgent);

        // 设置桌面视窗模式
        webSettings.setLoadWithOverviewMode(true);
        webSettings.setUseWideViewPort(true);
        webSettings.setLayoutAlgorithm(WebSettings.LayoutAlgorithm.NORMAL);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                view.loadUrl(url);
                return true;
            }

            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                // 在页面开始加载时设置桌面环境
                setupDesktopEnvironment();
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                progressBar.setVisibility(View.GONE);

                // 再次确保桌面环境设置
                setupDesktopEnvironment();

                // 延迟执行 JavaScript，确保 DOM 加载完成
                new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                    @Override
                    public void run() {
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
                                        "    newVideo.style.zIndex = '9999'; " +
                                        "    newVideo.controls = true; " +
                                        "    newVideo.play(); " +
                                        "    if (newVideo.requestFullscreen) newVideo.requestFullscreen(); " +
                                        "    else if (newVideo.webkitRequestFullscreen) newVideo.webkitRequestFullscreen(); " +
                                        "    else if (newVideo.mozRequestFullScreen) newVideo.mozRequestFullScreen(); " +
                                        "    else if (newVideo.msRequestFullscreen) newVideo.msRequestFullscreen(); " +
                                        "    return 'Video found and displayed in fullscreen'; " +
                                        "  } " +
                                        "  var video = document.querySelector('video'); " +
                                        "  if (video) { " +
                                        "    return displayVideoOnly(video); " +
                                        "  } else { " +
                                        "    var observer = new MutationObserver(function(mutations) { " +
                                        "      var video = document.querySelector('video'); " +
                                        "      if (video) { " +
                                        "        displayVideoOnly(video); " +
                                        "        observer.disconnect(); " +
                                        "      } " +
                                        "    }); " +
                                        "    observer.observe(document.body, { childList: true, subtree: true }); " +
                                        "    return 'Observing DOM for video'; " +
                                        "  } " +
                                        "})()",
                                new android.webkit.ValueCallback<String>() {
                                    @Override
                                    public void onReceiveValue(String result) {
                                        android.util.Log.d(TAG, "JavaScript result: " + result);
                                        runOnUiThread(new Runnable() {
                                            @Override
                                            public void run() {
                                                hideSimpleLoading();
                                            }
                                        });
                                    }
                                }
                        );
                    }
                }, 1000);
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                super.onReceivedError(view, request, error);
                android.util.Log.e(TAG, "WebView error: " + error.toString());
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        hideSimpleLoading();
                    }
                });
                if (isConfigValid) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            loadChannelsFromJson();
                        }
                    });
                }
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {
                // 全屏播放处理
                setContentView(view);
                setImmersiveMode();
                callback.onCustomViewHidden();
            }

            @Override
            public void onHideCustomView() {
                // 退出全屏播放处理
                setContentView(R.layout.activity_main);
                initViews();
                initSimpleLoadingLayout();
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
        if (channelsJson != null) {
            try {
                channelGroups = ChannelParser.parseGroupedJson(channelsJson);
                if (channelGroups != null && !channelGroups.isEmpty()) {
                    channels = new ArrayList<>();
                    for (ChannelGroup group : channelGroups) {
                        if (group.getChannels() != null) {
                            channels.addAll(group.getChannels());
                        }
                    }
                } else {
                    Type type = new TypeToken<List<Channel>>(){}.getType();
                    channels = gson.fromJson(channelsJson, type);
                }
            } catch (Exception e) {
                Type type = new TypeToken<List<Channel>>(){}.getType();
                channels = gson.fromJson(channelsJson, type);
            }
            if (channels == null) channels = new ArrayList<>();
            validateChannels();
        } else {
            loadChannelsFromConfig();
        }
    }

    private void loadChannelsFromConfig() {
        try {
            String json = readAssetFile("channels.config");
            if (json != null) {
                try {
                    channelGroups = ChannelParser.parseGroupedJson(json);
                    if (channelGroups != null && !channelGroups.isEmpty()) {
                        channels = new ArrayList<>();
                        for (ChannelGroup group : channelGroups) {
                            if (group.getChannels() != null) {
                                channels.addAll(group.getChannels());
                            }
                        }
                    } else {
                        channels = ChannelParser.parseJson(json);
                    }
                } catch (Exception e) {
                    channels = ChannelParser.parseJson(json);
                }
                isConfigValid = true;
                validateChannels();
            } else {
                loadChannelsFromJson();
            }
        } catch (IOException e) {
            loadChannelsFromJson();
        }
    }

    private void loadChannelsFromJson() {
        try {
            String json = readAssetFile("channels.json");
            if (json != null) {
                try {
                    channelGroups = ChannelParser.parseGroupedJson(json);
                    if (channelGroups != null && !channelGroups.isEmpty()) {
                        channels = new ArrayList<>();
                        for (ChannelGroup group : channelGroups) {
                            if (group.getChannels() != null) {
                                channels.addAll(group.getChannels());
                            }
                        }
                    } else {
                        channels = ChannelParser.parseJson(json);
                    }
                } catch (Exception e) {
                    channels = ChannelParser.parseJson(json);
                }
                isConfigValid = false;
                validateChannels();
            } else {
                channels = new ArrayList<>();
                setupChannelList();
            }
        } catch (IOException e) {
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
        if (channels.isEmpty()) {
            if (isConfigValid) {
                loadChannelsFromJson();
            }
            return;
        }

        new Thread(() -> {
            boolean allFailed = true;
            for (Channel channel : channels) {
                if (channel == null || channel.getUrl() == null || channel.getUrl().isEmpty()) {
                    continue;
                }
                try {
                    OkHttpClient client = new OkHttpClient();
                    Request request = new Request.Builder()
                            .url(channel.getUrl())
                            .addHeader("User-Agent", getRandomDesktopUserAgent())
                            .build();
                    Response response = client.newCall(request).execute();
                    if (response.isSuccessful()) {
                        allFailed = false;
                        break;
                    }
                } catch (IOException e) {
                    android.util.Log.e(TAG, "Channel validation failed: " + channel.getUrl());
                }
            }
            if (allFailed && isConfigValid) {
                runOnUiThread(() -> loadChannelsFromJson());
            } else {
                runOnUiThread(() -> {
                    setupChannelList();
                    if (!channels.isEmpty() && channels.get(0).getUrl() != null) {
                        loadChannel(channels.get(0));
                    } else {
                        webView.loadUrl(DEFAULT_URL);
                    }
                });
            }
        }).start();
    }

    private void setupChannelList() {
        TwoColumnChannelAdapter adapter = new TwoColumnChannelAdapter(
                this,
                channelGroups,
                new TwoColumnChannelAdapter.OnChannelClickListener() {
                    @Override
                    public void onChannelClick(Channel channel) {
                        onChannelClicked(channel);
                    }
                },
                new TwoColumnChannelAdapter.OnUpdateButtonClickListener() {
                    @Override
                    public void onUpdateButtonClick() {
                        updateChannels();
                    }
                }
        );
        channelList.setAdapter(adapter);
    }

    private void onChannelClicked(Channel channel) {
        android.util.Log.d(TAG, "Channel clicked: " + channel.getName());

        // 1. 立即关闭侧边栏
        drawerLayout.closeDrawer(channelList, false);

        // 2. 显示简化加载界面
        showSimpleLoading(channel);

        // 3. 后台加载频道（会自动应用新的随机桌面环境）
        loadChannelInBackground(channel);
    }

    private void showSimpleLoading(Channel channel) {
        loadChannelIcon(currentChannelIcon, channel);
        simpleLoadingLayout.setVisibility(View.VISIBLE);
        webView.setVisibility(View.GONE);
    }

    private void loadChannelIcon(ImageView iconView, Channel channel) {
        String icUrl = channel.getIcUrl();
        if (icUrl != null && !icUrl.isEmpty()) {
            String iconName = icUrl.replace(".png", "").replace(".jpg", "").replace(".jpeg", "");
            int resourceId = getResources().getIdentifier(iconName, "mipmap", getPackageName());

            if (resourceId != 0) {
                iconView.setImageResource(resourceId);
            } else {
                resourceId = getResources().getIdentifier(iconName, "drawable", getPackageName());
                if (resourceId != 0) {
                    iconView.setImageResource(resourceId);
                } else {
                    iconView.setImageResource(R.drawable.ic_tv_default);
                }
            }
        } else {
            iconView.setImageResource(R.drawable.ic_tv_default);
        }
    }

    private void loadChannelInBackground(Channel channel) {
        if (channel == null || channel.getUrl() == null || channel.getUrl().isEmpty()) {
            hideSimpleLoading();
            return;
        }

        currentChannelName = channel.getName() != null ? channel.getName() : "未知频道";

        // 为新频道设置新的随机桌面环境
        String newUserAgent = getRandomDesktopUserAgent();
        webView.getSettings().setUserAgentString(newUserAgent);
        android.util.Log.d(TAG, "Loading channel with new UserAgent: " + newUserAgent);

        webView.loadUrl(channel.getUrl());
    }

    private void hideSimpleLoading() {
        simpleLoadingLayout.setVisibility(View.GONE);
        webView.setVisibility(View.VISIBLE);
    }

    private void loadChannel(Channel channel) {
        if (channel == null || channel.getUrl() == null || channel.getUrl().isEmpty()) {
            return;
        }

        // 显示简化加载界面
        currentChannelName = channel.getName() != null ? channel.getName() : "未知频道";
        showSimpleLoading(channel);

        // 为新频道设置新的随机桌面环境
        String newUserAgent = getRandomDesktopUserAgent();
        webView.getSettings().setUserAgentString(newUserAgent);
        android.util.Log.d(TAG, "Loading channel with UserAgent: " + newUserAgent);

        // 加载频道 URL
        webView.loadUrl(channel.getUrl());
        drawerLayout.closeDrawer(channelList);
    }

    private void updateChannels() {
        Log.d(TAG, "Update channels method called");

        runOnUiThread(() -> {
            Toast.makeText(MainActivity.this, "正在更新频道列表...", Toast.LENGTH_SHORT).show();
        });

        new Thread(() -> {
            try {
                Log.d(TAG, "Starting to update channels from: " + CHANNELS_URL);
                OkHttpClient client = new OkHttpClient();
                Request request = new Request.Builder()
                        .url(CHANNELS_URL)
                        .addHeader("User-Agent", getRandomDesktopUserAgent())
                        .build();
                Response response = client.newCall(request).execute();

                if (response.isSuccessful() && response.body() != null) {
                    String content = response.body().string();
                    Log.d(TAG, "Downloaded content length: " + content.length());
                    Log.d(TAG, "Content preview: " + content.substring(0, Math.min(300, content.length())));

                    // 直接解析内容，不需要extractJsonFromHtml
                    String json = content.trim();

                    // 验证JSON格式
                    if (json.startsWith("[") && json.endsWith("]")) {
                        try {
                            // 解析分组数据
                            channelGroups = ChannelParser.parseGroupedJson(json);
                            if (channelGroups != null && !channelGroups.isEmpty()) {
                                channels = new ArrayList<>();
                                for (ChannelGroup group : channelGroups) {
                                    if (group.getChannels() != null) {
                                        channels.addAll(group.getChannels());
                                    }
                                }

                                Log.d(TAG, "Successfully parsed " + channelGroups.size() + " groups, " + channels.size() + " channels");

                                // 保存到本地
                                prefs.edit().putString(CHANNELS_KEY, json).apply();
                                saveChannelsToConfig(json);

                                runOnUiThread(() -> {
                                    setupChannelList();
                                    Toast.makeText(MainActivity.this, "频道列表更新成功，共" + channels.size() + "个频道", Toast.LENGTH_SHORT).show();

                                    // 加载第一个频道
                                    if (!channels.isEmpty() && channels.get(0).getUrl() != null) {
                                        loadChannel(channels.get(0));
                                    } else {
                                        webView.loadUrl(DEFAULT_URL);
                                    }
                                });
                                return;
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Failed to parse grouped JSON: " + e.getMessage(), e);
                        }
                    }

                    runOnUiThread(() -> {
                        Toast.makeText(MainActivity.this, "更新失败：数据格式错误", Toast.LENGTH_SHORT).show();
                    });

                } else {
                    Log.e(TAG, "HTTP request failed: " + (response != null ? response.code() : "null response"));
                    runOnUiThread(() -> {
                        Toast.makeText(MainActivity.this, "更新失败：网络错误", Toast.LENGTH_SHORT).show();
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

    private String extractJsonFromHtml(String html) {
        int start = html.indexOf("<pre>");
        int end = html.indexOf("</pre>");
        if (start != -1 && end != -1) {
            return html.substring(start + 5, end).trim();
        }
        return null;
    }

    private void saveChannelsToConfig(String json) {
        try {
            File file = new File(getFilesDir(), "channels.config");
            try (FileOutputStream fos = new FileOutputStream(file)) {
                fos.write(json.getBytes());
            }
        } catch (IOException e) {
            android.util.Log.e(TAG, "Save channels config failed: " + e.getMessage());
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
            drawerLayout.closeDrawer(channelList);
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