package com.dex7er.ctvplayer;

import android.annotation.SuppressLint;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

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
    private static final String DEFAULT_URL = "https://www.yangshipin.cn/tv/home?pid=600001859";
    private static final String CHANNELS_URL = "https://gitee.com/magasb/ctvplayer/blob/master/app/src/main/assets/channels.json";
    private static final String PREFS_NAME = "CTVPlayerPrefs";
    private static final String CHANNELS_KEY = "channels";

    private WebView webView;
    private ProgressBar progressBar;
    private DrawerLayout drawerLayout;
    private RecyclerView channelList;
    private List<Channel> channels = new ArrayList<>();
    private SharedPreferences prefs;
    private Gson gson;
    private boolean isConfigValid = false;

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
        channelList.setLayoutManager(new LinearLayoutManager(this));
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
        webSettings.setUserAgentString("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/138.0.0.0 Safari/537.36 Edg/138.0.0.0");

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
                                "  function displayVideoOnly() { " +
                                "    var video = document.querySelector('video'); " +
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
                                "    return 'Video displayed'; " +
                                "  } " +
                                "  if (document.querySelector('video')) { " +
                                "    displayVideoOnly(); " +
                                "  } else { " +
                                "    var observer = new MutationObserver(function(mutations) { " +
                                "      if (document.querySelector('video')) { " +
                                "        displayVideoOnly(); " +
                                "        observer.disconnect(); " +
                                "      } " +
                                "    }); " +
                                "    observer.observe(document.body, { childList: true, subtree: true }); " +
                                "    setTimeout(function() { observer.disconnect(); }, 15000); " +
                                "    return 'Observing DOM for video'; " +
                                "  } " +
                                "})()",
                        result -> {
                            if (result != null && result.contains("No video element found")) {
                                Toast.makeText(MainActivity.this, getString(R.string.network_error), Toast.LENGTH_SHORT).show();
                                if (isConfigValid) {
                                    loadChannelsFromJson();
                                }
                            }
                        }
                );
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                super.onReceivedError(view, request, error);
                Toast.makeText(MainActivity.this, getString(R.string.network_error), Toast.LENGTH_SHORT).show();
                if (isConfigValid) {
                    loadChannelsFromJson();
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
        if (channelsJson != null) {
            Type type = new TypeToken<List<Channel>>(){}.getType();
            channels = gson.fromJson(channelsJson, type);
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
                channels = ChannelParser.parseJson(json);
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
                channels = ChannelParser.parseJson(json);
                isConfigValid = false;
                validateChannels();
            } else {
                Toast.makeText(this, getString(R.string.no_channels), Toast.LENGTH_SHORT).show();
                channels = new ArrayList<>();
                setupChannelList();
            }
        } catch (IOException e) {
            Toast.makeText(this, getString(R.string.no_channels), Toast.LENGTH_SHORT).show();
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
            } else {
                runOnUiThread(() -> Toast.makeText(this, getString(R.string.no_channels), Toast.LENGTH_SHORT).show());
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
                    Request request = new Request.Builder().url(channel.getUrl()).build();
                    Response response = client.newCall(request).execute();
                    if (response.isSuccessful()) {
                        allFailed = false;
                        break;
                    }
                } catch (IOException e) {
                    // Continue checking other channels
                }
            }
            if (allFailed && isConfigValid) {
                runOnUiThread(() -> {
                    Toast.makeText(this, getString(R.string.network_error), Toast.LENGTH_SHORT).show();
                    loadChannelsFromJson();
                });
            } else {
                runOnUiThread(() -> {
                    setupChannelList();
                    if (!channels.isEmpty() && channels.get(0).getUrl() != null) {
                        webView.loadUrl(channels.get(0).getUrl());
                    } else {
                        webView.loadUrl(DEFAULT_URL);
                        Toast.makeText(this, "无有效频道，使用默认地址", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        }).start();
    }

    private void setupChannelList() {
        ChannelAdapter adapter = new ChannelAdapter(
                this,
                channels,
                url -> {
                    if (url != null && !url.isEmpty()) {
                        webView.loadUrl(url);
                        drawerLayout.closeDrawer(channelList);
                        Toast.makeText(this, "切换到频道", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "频道地址无效", Toast.LENGTH_SHORT).show();
                    }
                },
                this::updateChannels
        );
        channelList.setAdapter(adapter);
    }

    private void updateChannels() {
        new Thread(() -> {
            try {
                OkHttpClient client = new OkHttpClient();
                Request request = new Request.Builder().url(CHANNELS_URL).build();
                Response response = client.newCall(request).execute();
                if (response.isSuccessful() && response.body() != null) {
                    String html = response.body().string();
                    String json = extractJsonFromHtml(html);
                    if (json != null) {
                        saveChannelsToConfig(json);
                        channels = ChannelParser.parseJson(json);
                        prefs.edit().putString(CHANNELS_KEY, json).apply();
                        runOnUiThread(() -> {
                            setupChannelList();
                            if (!channels.isEmpty() && channels.get(0).getUrl() != null) {
                                webView.loadUrl(channels.get(0).getUrl());
                            } else {
                                webView.loadUrl(DEFAULT_URL);
                                Toast.makeText(this, "无有效频道，使用默认地址", Toast.LENGTH_SHORT).show();
                            }
                            Toast.makeText(this, "频道更新成功", Toast.LENGTH_SHORT).show();
                        });
                    } else {
                        runOnUiThread(() -> Toast.makeText(this, "解析频道数据失败", Toast.LENGTH_SHORT).show());
                    }
                } else {
                    runOnUiThread(() -> Toast.makeText(this, "更新频道失败", Toast.LENGTH_SHORT).show());
                }
            } catch (IOException e) {
                runOnUiThread(() -> Toast.makeText(this, "网络错误: " + e.getMessage(), Toast.LENGTH_SHORT).show());
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
            runOnUiThread(() -> Toast.makeText(this, "保存频道数据失败", Toast.LENGTH_SHORT).show());
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