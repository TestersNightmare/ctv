package com.dex7er.ctvplayer;

import android.annotation.SuppressLint;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Toast;
import android.speech.tts.TextToSpeech;


import androidx.appcompat.app.AppCompatActivity;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import com.bumptech.glide.Glide;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private static final String DEFAULT_URL = "https://www.yangshipin.cn/tv/home?pid=600001859";
    private static final String GITHUB_URL = "https://raw.githubusercontent.com/TestersNightmare/ctv/master/app/src/main/assets/channels.json";
    private static final String GITEE_URL = "https://gitee.com/magasb/ctvplayer/raw/master/app/src/main/assets/channels.json";
    private static final String PREFS_NAME = "CTVPlayerPrefs";
    private static final String CHANNELS_KEY = "channels";
    private static final String HISTORY_KEY = "play_history";
    private static final int MAX_HISTORY = 50;

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

    private static final String[][] DESKTOP_RESOLUTIONS = {
            {"1920", "1080"},
            {"2560", "1440"},
            {"3440", "1440"},
            {"3840", "2160"},
            {"2560", "1600"},
            {"3840", "1600"},
            {"5120", "2880"},
            {"1920", "1200"}
    };
    private View aboutPage;
    private FrameLayout rightDrawerContainer;
    private WebView webView;
    private ProgressBar progressBar;
    private DrawerLayout drawerLayout;
    private RecyclerView channelList;
    private RecyclerView historyList;
    private FrameLayout simpleLoadingLayout;
    private ImageView currentChannelIcon;
    private ProgressBar loadingSpinner;
    private List<Channel> channels = new ArrayList<>();
    private List<ChannelGroup> channelGroups = new ArrayList<>();
    private List<PlayHistory> playHistory = new ArrayList<>();
    private SharedPreferences prefs;
    private Gson gson;
    private boolean isConfigValid = false;
    private Random random = new Random();
    private Handler handler = new Handler(Looper.getMainLooper());
    private static final long VIDEO_CHECK_INTERVAL = 500;
    private static final int MAX_RELOAD_ATTEMPTS = 3;
    private int currentReloadAttempts = 0;
    private TextToSpeech tts;
    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        gson = new Gson();
        loadPlayHistory();

        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                int result = tts.setLanguage(Locale.SIMPLIFIED_CHINESE);
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.e(TAG, "TTS: 中文语言不可用");
                    Toast.makeText(this, "设备不支持中文语音播报", Toast.LENGTH_SHORT).show();
                } else {
                    Log.d(TAG, "TTS: 初始化成功");
                    tts.setSpeechRate(1.0f);
                    tts.setPitch(1.0f);
                }
            } else {
                Log.e(TAG, "TTS: 初始化失败，状态码: " + status);
                Toast.makeText(this, "语音播报初始化失败", Toast.LENGTH_SHORT).show();
            }
        });
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
        historyList = findViewById(R.id.history_list);
        aboutPage = findViewById(R.id.about_page);
        rightDrawerContainer = (FrameLayout) aboutPage.getParent();

        GridLayoutManager channelLayoutManager = new GridLayoutManager(this, 3);
        channelLayoutManager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                return position == 0 ? 3 : 1; // 顶部按钮占满一行
            }
        });
        channelList.setLayoutManager(channelLayoutManager);

        GridLayoutManager historyLayoutManager = new GridLayoutManager(this, 3);
        historyLayoutManager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                return 1;
            }
        });
        historyList.setLayoutManager(historyLayoutManager);
    }

    private void initSimpleLoadingLayout() {
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        int base = Math.min(metrics.widthPixels, metrics.heightPixels);

        int iconSize = (int) (base * 0.35f);
        int spinnerSize = (int) (base * 0.30f);
        int iconMargin = (int) (base * 0.05f);

        simpleLoadingLayout = new FrameLayout(this);
        simpleLoadingLayout.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        simpleLoadingLayout.setBackgroundColor(0xCC000000);
        simpleLoadingLayout.setVisibility(View.GONE);

        LinearLayout center = new LinearLayout(this);
        center.setOrientation(LinearLayout.VERTICAL);
        center.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        center.setLayoutParams(cp);

        currentChannelIcon = new ImageView(this);
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(iconSize, iconSize);
        ip.bottomMargin = iconMargin;
        currentChannelIcon.setLayoutParams(ip);
        currentChannelIcon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        currentChannelIcon.setImageResource(R.drawable.ic_tv_default);

        loadingSpinner = new ProgressBar(this);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(spinnerSize, spinnerSize);
        loadingSpinner.setLayoutParams(sp);

        center.addView(currentChannelIcon);
        center.addView(loadingSpinner);
        simpleLoadingLayout.addView(center);

        FrameLayout mainLayout = findViewById(android.R.id.content);
        mainLayout.addView(simpleLoadingLayout);
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebView() {
        WebSettings ws = webView.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setLoadWithOverviewMode(true);
        ws.setUseWideViewPort(true);
        ws.setMediaPlaybackRequiresUserGesture(false);
        ws.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);

        String ua = getRandomDesktopUserAgent();
        ws.setUserAgentString(ua);
        Log.d(TAG, "WebView UA: " + ua);

        webView.addJavascriptInterface(new Object() {
            @JavascriptInterface
            public void onVideoReady() {
                handler.postDelayed(() -> {
                    hideSimpleLoading();
                    currentReloadAttempts = 0;
                }, 1000);
            }

            @JavascriptInterface
            public void onVideoError(String error) {
                Log.e(TAG, "Video loading error: " + error);
                if (currentReloadAttempts < MAX_RELOAD_ATTEMPTS) {
                    currentReloadAttempts++;
                    Log.d(TAG, "Retrying load channel, attempt: " + currentReloadAttempts);
                    handler.postDelayed(() -> webView.reload(), 1000);
                } else {
                    runOnUiThread(() -> {
                        hideSimpleLoading();
                        Toast.makeText(MainActivity.this, "无法加载视频，请重试", Toast.LENGTH_SHORT).show();
                    });
                    currentReloadAttempts = 0;
                }
            }
        }, "AndroidBridge");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, String url) {
                v.loadUrl(url);
                return true;
            }

            @Override
            public void onPageStarted(WebView v, String url, android.graphics.Bitmap favicon) {
                super.onPageStarted(v, url, favicon);
                setupDesktopEnvironment();
                currentReloadAttempts = 0;
            }

            @Override
            public void onPageFinished(WebView v, String url) {
                super.onPageFinished(v, url);
                progressBar.setVisibility(View.GONE);
                setupDesktopEnvironment();

                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    String js =
                            "(function(){\n" +
                                    "  function displayVideo(video){\n" +
                                    "    document.body.innerHTML = '<div style=\"margin:0;padding:0;width:100%;height:100vh;overflow:hidden;\">'+video.outerHTML+'</div>';\n" +
                                    "    var v = document.querySelector('video');\n" +
                                    "    v.style.cssText = 'width:100%;height:100%;object-fit:contain;position:absolute;top:0;left:0;z-index:9999;';\n" +
                                    "    v.controls = true;\n" +
                                    "    v.play();\n" +
                                    "    if(v.requestFullscreen) v.requestFullscreen();\n" +
                                    "    AndroidBridge.onVideoReady();\n" +
                                    "  }\n" +
                                    "  var vid = document.querySelector('video');\n" +
                                    "  if(vid) displayVideo(vid);\n" +
                                    "  else {\n" +
                                    "    var obs = new MutationObserver(function(){\n" +
                                    "      var v2 = document.querySelector('video');\n" +
                                    "      if(v2){ displayVideo(v2); obs.disconnect(); }\n" +
                                    "    });\n" +
                                    "    obs.observe(document.body,{ childList:true, subtree:true });\n" +
                                    "  }\n" +
                                    "})();";
                    v.evaluateJavascript(js, null);
                }, 800);
            }

            @Override
            public void onReceivedError(WebView v, WebResourceRequest req, WebResourceError err) {
                super.onReceivedError(v, req, err);
                Log.e(TAG, "WebView error: " + err.getDescription());
                if (currentReloadAttempts < MAX_RELOAD_ATTEMPTS) {
                    currentReloadAttempts++;
                    Log.d(TAG, "Retrying load channel due to error, attempt: " + currentReloadAttempts);
                    handler.postDelayed(() -> webView.reload(), 1000);
                } else {
                    hideSimpleLoading();
                    if (isConfigValid) runOnUiThread(MainActivity.this::loadChannelsFromJson);
                    Toast.makeText(MainActivity.this, "页面加载失败，请重试", Toast.LENGTH_SHORT).show();
                    currentReloadAttempts = 0;
                }
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView v, int newProgress) {
                if (newProgress == 100) progressBar.setVisibility(View.GONE);
                else {
                    progressBar.setVisibility(View.VISIBLE);
                    progressBar.setProgress(newProgress);
                }
            }
        });

        webView.loadUrl(DEFAULT_URL);
        webView.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                float x = event.getX();
                int screenWidth = getResources().getDisplayMetrics().widthPixels;
                if (x > screenWidth * 0.7f) {
                    drawerLayout.openDrawer(historyList);
                    return true;
                } else if (x < screenWidth * 0.3f) {
                    drawerLayout.openDrawer(channelList);
                    return true;
                }
            }
            return false;
        });
    }

    private void checkVideoElement() {
        String js = "(function() {" +
                "  function displayVideo(video) {" +
                "    document.body.innerHTML = '<div style=\"margin:0;padding:0;width:100%;height:100vh;overflow:hidden;\">' + video.outerHTML + '</div>';" +
                "    var v = document.querySelector('video');" +
                "    v.style.cssText = 'width:100%;height:100%;object-fit:contain;position:absolute;top:0;left:0;z-index:9999;';" +
                "    v.controls = true;" +
                "    v.play().then(() => {" +
                "      if (v.requestFullscreen) v.requestFullscreen();" +
                "      AndroidBridge.onVideoReady();" +
                "    }).catch((err) => {" +
                "      AndroidBridge.onVideoError(err.message);" +
                "    });" +
                "  }" +
                "  var vid = document.querySelector('video');" +
                "  if (vid) {" +
                "    displayVideo(vid);" +
                "  } else {" +
                "    var attempts = 0;" +
                "    var maxAttempts = 10;" +
                "    var observer = new MutationObserver(function() {" +
                "      var v2 = document.querySelector('video');" +
                "      if (v2) {" +
                "        displayVideo(v2);" +
                "        observer.disconnect();" +
                "      } else if (attempts >= maxAttempts) {" +
                "        observer.disconnect();" +
                "        AndroidBridge.onVideoError('Video element not found after max attempts');" +
                "      }" +
                "      attempts++;" +
                "    });" +
                "    observer.observe(document.body, { childList: true, subtree: true });" +
                "  }" +
                "})();";
        webView.evaluateJavascript(js, null);
    }

    private String getRandomDesktopUserAgent() {
        return DESKTOP_USER_AGENTS[random.nextInt(DESKTOP_USER_AGENTS.length)];
    }

    private String[] getRandomDesktopResolution() {
        return DESKTOP_RESOLUTIONS[random.nextInt(DESKTOP_RESOLUTIONS.length)];
    }

    private void setupDesktopEnvironment() {
        String ua = getRandomDesktopUserAgent();
        String[] res = getRandomDesktopResolution();
        String script =
                "(function(){"
                        + "Object.defineProperty(window.screen,'width',{value:" + res[0] + "});"
                        + "Object.defineProperty(window.screen,'height',{value:" + res[1] + "});"
                        + "Object.defineProperty(window.screen,'availWidth',{value:" + res[0] + "});"
                        + "Object.defineProperty(window.screen,'availHeight',{value:" + (Integer.parseInt(res[1]) - 40) + "});"
                        + "Object.defineProperty(navigator,'platform',{value:'Win32'});"
                        + "Object.defineProperty(navigator,'maxTouchPoints',{value:0});"
                        + "Object.defineProperty(navigator,'userAgent',{value:'" + ua + "'});"
                        + "window.ontouchstart=undefined;})();";
        webView.evaluateJavascript(script, null);
    }

    private void loadChannels() {
        updateChannels();
    }

    private void loadChannelsFromConfig() {
        try {
            File configFile = new File(getFilesDir(), "channels.config");
            if (configFile.exists()) {
                FileInputStream fis = new FileInputStream(configFile);
                byte[] buffer = new byte[(int) configFile.length()];
                fis.read(buffer);
                fis.close();
                String json = new String(buffer);
                channelGroups = ChannelParser.parseGroupedJson(json);
                if (channelGroups != null && !channelGroups.isEmpty()) {
                    channels.clear();
                    for (ChannelGroup g : channelGroups) {
                        if (g.getChannels() != null) channels.addAll(g.getChannels());
                    }
                } else {
                    channels = ChannelParser.parseJson(json);
                }
                isConfigValid = true;
                validateChannels();
                return;
            }
        } catch (IOException e) {
            Log.e(TAG, "Failed to load channels.config: " + e.getMessage());
        }
        loadChannelsFromJson();
    }

    private void loadChannelsFromJson() {
        try {
            String json = readAssetFile("channels.json");
            if (json != null) {
                channelGroups = ChannelParser.parseGroupedJson(json);
                if (channelGroups != null && !channelGroups.isEmpty()) {
                    channels.clear();
                    for (ChannelGroup g : channelGroups) {
                        if (g.getChannels() != null) channels.addAll(g.getChannels());
                    }
                } else {
                    channels = ChannelParser.parseJson(json);
                }
                isConfigValid = false;
                validateChannels();
                return;
            }
        } catch (IOException ignored) {}
        channels = new ArrayList<>();
        setupChannelList();
    }

    private String readAssetFile(String fileName) throws IOException {
        try (InputStream is = getAssets().open(fileName);
             BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            return sb.toString();
        }
    }

    private void validateChannels() {
        if (channels.isEmpty()) {
            if (isConfigValid) loadChannelsFromJson();
            return;
        }
        new Thread(() -> {
            boolean allFailed = true;
            for (Channel c : channels) {
                if (c.getUrl() == null || c.getUrl().isEmpty()) continue;
                try {
                    Request req = new Request.Builder()
                            .url(c.getUrl())
                            .addHeader("User-Agent", getRandomDesktopUserAgent())
                            .build();
                    Response resp = client.newCall(req).execute();
                    if (resp.isSuccessful()) {
                        allFailed = false;
                        break;
                    }
                } catch (IOException ignored) {}
            }
            if (allFailed && isConfigValid) runOnUiThread(this::loadChannelsFromJson);
            else runOnUiThread(() -> {
                setupChannelList();
                if (!channels.isEmpty() && channels.get(0).getUrl() != null) {
                    onChannelClicked(channels.get(0));
                } else {
                    webView.loadUrl(DEFAULT_URL);
                }
            });
        }).start();
    }

// 在 MainActivity.java 的 setupChannelList() 方法中，替换按钮创建部分：

    private void setupChannelList() {
        // 创建按钮容器，使用与频道相同的网格布局样式
        LinearLayout buttonContainer = new LinearLayout(this);
        buttonContainer.setOrientation(LinearLayout.HORIZONTAL);
        buttonContainer.setGravity(Gravity.CENTER);
        buttonContainer.setPadding(16, 16, 16, 16);

        // 计算每个按钮的大小，参考频道图标的尺寸处理
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int containerWidth = 300; // drawer宽度300dp转像素
        int containerWidthPx = (int) (containerWidth * getResources().getDisplayMetrics().density);
        int buttonSize = (containerWidthPx - 64) / 3; // 减去padding，除以3个按钮

        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(buttonSize, buttonSize);
        buttonParams.setMargins(10, 8, 10, 8);

        // 历史按钮
        ImageView historyButton = new ImageView(this);
        historyButton.setLayoutParams(buttonParams);
        historyButton.setImageResource(R.drawable.ic_history);
        historyButton.setScaleType(ImageView.ScaleType.FIT_CENTER);
        historyButton.setPadding(4, 4, 4, 4);
        historyButton.setOnClickListener(v -> {
            // 切换到历史记录页面
            showHistoryPage();
        });
        buttonContainer.addView(historyButton);


        // 更新按钮
        ImageView updateButton = new ImageView(this);
        updateButton.setLayoutParams(buttonParams);
        updateButton.setImageResource(R.drawable.ic_update);
        updateButton.setScaleType(ImageView.ScaleType.FIT_CENTER);
        updateButton.setPadding(4, 4, 4, 4);
        updateButton.setOnClickListener(v -> updateChannels());
        buttonContainer.addView(updateButton);

        // 关于按钮
        ImageView aboutButton = new ImageView(this);
        aboutButton.setLayoutParams(buttonParams);
        aboutButton.setImageResource(R.drawable.ic_about);
        aboutButton.setScaleType(ImageView.ScaleType.FIT_CENTER);
        aboutButton.setPadding(4, 4, 4, 4);
        aboutButton.setOnClickListener(v -> {
            // 切换到关于页面
            showAboutPage();
        });
        buttonContainer.addView(aboutButton);

        // 显式声明 ChannelAdapter 类型
        List<Channel> flatChannels = new ArrayList<>();
        for (ChannelGroup group : channelGroups) {
            if (group.getChannels() != null) {
                flatChannels.addAll(group.getChannels());
            }
        }

        // 显式声明 ChannelAdapter 类型
        RecyclerView.Adapter<RecyclerView.ViewHolder> adapter = new ChannelAdapter(this, flatChannels, this::onChannelClicked);
        channelList.setAdapter(new HeaderFooterAdapter(adapter, buttonContainer, null));
        setupHistoryList();
    }
    private void showAboutPage() {
        if (drawerLayout.isDrawerOpen(rightDrawerContainer)) {
            // 如果右侧抽屉已打开，检查当前显示的页面
            if (aboutPage.getVisibility() == View.VISIBLE) {
                // 如果关于页面已显示，关闭抽屉
                drawerLayout.closeDrawer(rightDrawerContainer);
            } else {
                // 切换到关于页面
                historyList.setVisibility(View.GONE);
                aboutPage.setVisibility(View.VISIBLE);
            }
        } else {
            // 打开抽屉并显示关于页面
            historyList.setVisibility(View.GONE);
            aboutPage.setVisibility(View.VISIBLE);
            drawerLayout.openDrawer(rightDrawerContainer);
        }
    }

    private void showHistoryPage() {
        if (drawerLayout.isDrawerOpen(rightDrawerContainer)) {
            // 如果右侧抽屉已打开，检查当前显示的页面
            if (historyList.getVisibility() == View.VISIBLE) {
                // 如果历史页面已显示，关闭抽屉
                drawerLayout.closeDrawer(rightDrawerContainer);
            } else {
                // 切换到历史页面
                aboutPage.setVisibility(View.GONE);
                historyList.setVisibility(View.VISIBLE);
            }
        } else {
            // 打开抽屉并显示历史页面
            aboutPage.setVisibility(View.GONE);
            historyList.setVisibility(View.VISIBLE);
            drawerLayout.openDrawer(rightDrawerContainer);
        }
    }
    private void setupHistoryList() {
        HistoryAdapter adapter = new HistoryAdapter(this, playHistory, history -> {
            drawerLayout.closeDrawer(rightDrawerContainer); // 修改这里
            showSimpleLoading(history);
            String channelName = history.getName() != null ? history.getName() : "未知频道";
            String ttsText = "正在播放" + channelName;
            if (tts != null && tts.isLanguageAvailable(Locale.SIMPLIFIED_CHINESE) >= 0) {
                tts.speak(ttsText, TextToSpeech.QUEUE_FLUSH, null, null);
                Log.d(TAG, "TTS: 播报 - " + ttsText);
            } else {
                Log.w(TAG, "TTS: 无法播报，语言不可用");
            }
            loadChannelInBackground(history);
        });
        historyList.setAdapter(adapter);
    }

    private void onChannelClicked(Channel channel) {
        drawerLayout.closeDrawer(channelList, false);
        showSimpleLoading(channel);
        String channelName = channel.getName() != null ? channel.getName() : "未知频道";
        String ttsText = "正在播放" + channelName;
        if (tts != null && tts.isLanguageAvailable(Locale.SIMPLIFIED_CHINESE) >= 0) {
            tts.speak(ttsText, TextToSpeech.QUEUE_FLUSH, null, null);
            Log.d(TAG, "TTS: 播报 - " + ttsText);
        } else {
            Log.w(TAG, "TTS: 无法播报，语言不可用");
        }
        addPlayHistory(channel);
        loadChannelInBackground(channel);
    }

    private void showSimpleLoading(Channel channel) {
        loadChannelIcon(currentChannelIcon, channel);
        simpleLoadingLayout.setVisibility(View.VISIBLE);
        webView.setVisibility(View.GONE);
    }

    private void showSimpleLoading(PlayHistory history) {
        loadChannelIcon(currentChannelIcon, history);
        simpleLoadingLayout.setVisibility(View.VISIBLE);
        webView.setVisibility(View.GONE);
    }

    private void loadChannelIcon(ImageView iv, Channel channel) {
        String icUrl = channel.getIcUrl();
        if (icUrl != null && !icUrl.isEmpty()) {
            String name = icUrl.substring(icUrl.lastIndexOf('/') + 1).replaceAll("\\..*$", "");
            int resId = getResources().getIdentifier(name, "drawable", getPackageName());
            if (resId != 0) {
                iv.setImageResource(resId);
            } else {
                Glide.with(this).load(icUrl).error(R.drawable.ic_tv_default).into(iv);
            }
        } else {
            iv.setImageResource(R.drawable.ic_tv_default);
        }
    }

    private void loadChannelIcon(ImageView iv, PlayHistory history) {
        String icUrl = history.getIcUrl();
        if (icUrl != null && !icUrl.isEmpty()) {
            String name = icUrl.substring(icUrl.lastIndexOf('/') + 1).replaceAll("\\..*$", "");
            int resId = getResources().getIdentifier(name, "drawable", getPackageName());
            if (resId != 0) {
                iv.setImageResource(resId);
            } else {
                Glide.with(this).load(icUrl).error(R.drawable.ic_tv_default).into(iv);
            }
        } else {
            iv.setImageResource(R.drawable.ic_tv_default);
        }
    }

    private void loadChannelInBackground(Channel channel) {
        if (channel.getUrl() == null || channel.getUrl().isEmpty()) {
            hideSimpleLoading();
            return;
        }
        String ua = getRandomDesktopUserAgent();
        webView.getSettings().setUserAgentString(ua);
        Log.d(TAG, "Loading channel with UA: " + ua);
        webView.loadUrl(channel.getUrl());
    }

    private void loadChannelInBackground(PlayHistory history) {
        if (history.getUrl() == null || history.getUrl().isEmpty()) {
            hideSimpleLoading();
            return;
        }
        String ua = getRandomDesktopUserAgent();
        webView.getSettings().setUserAgentString(ua);
        Log.d(TAG, "Loading history channel with UA: " + ua);
        webView.loadUrl(history.getUrl());
    }

    private void hideSimpleLoading() {
        simpleLoadingLayout.setVisibility(View.GONE);
        webView.setVisibility(View.VISIBLE);
    }

    private void addPlayHistory(Channel channel) {
        String url = channel.getUrl();
        String icUrl = channel.getIcUrl();
        String name = channel.getName() != null ? channel.getName() : "未知频道";
        playHistory.add(0, new PlayHistory(url, icUrl, System.currentTimeMillis(), name));
        if (playHistory.size() > MAX_HISTORY) {
            playHistory = playHistory.subList(0, MAX_HISTORY);
        }
        prefs.edit().putString(HISTORY_KEY, gson.toJson(playHistory)).apply();
        setupHistoryList();
    }

    private void loadPlayHistory() {
        String historyJson = prefs.getString(HISTORY_KEY, null);
        if (historyJson != null) {
            Type listType = new TypeToken<List<PlayHistory>>(){}.getType();
            playHistory = gson.fromJson(historyJson, listType);
            if (playHistory == null) playHistory = new ArrayList<>();
            Collections.sort(playHistory, (h1, h2) -> Long.compare(h2.getTimestamp(), h1.getTimestamp()));
            if (playHistory.size() > MAX_HISTORY) {
                playHistory = playHistory.subList(0, MAX_HISTORY);
            }
        }
    }

    private void updateChannels() {
        Toast.makeText(this, "正在更新频道列表...", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            String content = null;
            try {
                Request req = new Request.Builder()
                        .url(GITHUB_URL)
                        .addHeader("User-Agent", getRandomDesktopUserAgent())
                        .build();
                Response resp = client.newCall(req).execute();
                if (resp.isSuccessful() && resp.body() != null) {
                    content = resp.body().string().trim();
                    Log.d(TAG, "Fetched from GitHub: " + content);
                } else {
                    Log.e(TAG, "GitHub fetch failed, HTTP code: " + (resp != null ? resp.code() : "unknown"));
                }
            } catch (IOException e) {
                Log.e(TAG, "GitHub fetch error: " + e.getMessage());
            }

            if (content == null) {
                try {
                    Request req = new Request.Builder()
                            .url(GITEE_URL)
                            .addHeader("User-Agent", getRandomDesktopUserAgent())
                            .build();
                    Response resp = client.newCall(req).execute();
                    if (resp.isSuccessful() && resp.body() != null) {
                        content = resp.body().string().trim();
                        Log.d(TAG, "Fetched from Gitee: " + content);
                    } else {
                        Log.e(TAG, "Gitee fetch failed, HTTP code: " + (resp != null ? resp.code() : "unknown"));
                    }
                } catch (IOException e) {
                    Log.e(TAG, "Gitee fetch error: " + e.getMessage());
                }
            }

            if (content != null && content.startsWith("[") && content.endsWith("]")) {
                channelGroups = ChannelParser.parseGroupedJson(content);
                channels.clear();
                if (channelGroups != null) {
                    for (ChannelGroup g : channelGroups) {
                        if (g.getChannels() != null) channels.addAll(g.getChannels());
                    }
                }
                if (!channels.isEmpty()) {
                    prefs.edit().putString(CHANNELS_KEY, content).apply();
                    saveChannelsToConfig(content);
                    runOnUiThread(() -> {
                        setupChannelList();
                        Toast.makeText(this, "更新成功，共" + channels.size() + "个频道", Toast.LENGTH_SHORT).show();
                        if (!channels.isEmpty()) onChannelClicked(channels.get(0));
                    });
                    return;
                }
            }

            runOnUiThread(() -> {
                boolean openChannel = getResources().getBoolean(R.bool.openchannel);
                if (openChannel) {
                    String channelsJson = prefs.getString(CHANNELS_KEY, null);
                    if (channelsJson != null) {
                        channelGroups = ChannelParser.parseGroupedJson(channelsJson);
                        channels.clear();
                        if (channelGroups != null) {
                            for (ChannelGroup g : channelGroups) {
                                if (g.getChannels() != null) channels.addAll(g.getChannels());
                            }
                        }
                        if (!channels.isEmpty()) {
                            Log.d(TAG, "Loaded " + channels.size() + " channels from SharedPreferences (fallback)");
                            setupChannelList();
                            onChannelClicked(channels.get(0));
                            return;
                        }
                    }
                    try {
                        File configFile = new File(getFilesDir(), "channels.config");
                        if (configFile.exists()) {
                            FileInputStream fis = new FileInputStream(configFile);
                            byte[] buffer = new byte[(int) configFile.length()];
                            fis.read(buffer);
                            fis.close();
                            channelsJson = new String(buffer);
                            channelGroups = ChannelParser.parseGroupedJson(channelsJson);
                            channels.clear();
                            if (channelGroups != null) {
                                for (ChannelGroup g : channelGroups) {
                                    if (g.getChannels() != null) channels.addAll(g.getChannels());
                                }
                            }
                            if (!channels.isEmpty()) {
                                Log.d(TAG, "Loaded " + channels.size() + " channels from channels.config (fallback)");
                                prefs.edit().putString(CHANNELS_KEY, channelsJson).apply();
                                setupChannelList();
                                onChannelClicked(channels.get(0));
                                return;
                            }
                        }
                    } catch (IOException e) {
                        Log.e(TAG, "Failed to load channels.config (fallback): " + e.getMessage());
                    }
                    loadChannelsFromJson();
                } else {
                    String errorMsg = "网络无法接通";
                    Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show();
                    if (tts != null && tts.isLanguageAvailable(Locale.SIMPLIFIED_CHINESE) >= 0) {
                        tts.speak(errorMsg, TextToSpeech.QUEUE_FLUSH, null, null);
                        Log.d(TAG, "TTS: 播报 - " + errorMsg);
                    } else {
                        Log.w(TAG, "TTS: 无法播报，语言不可用");
                    }
                    Log.e(TAG, "All sources failed, openchannel: " + openChannel);
                    //channels = new ArrayList<>();
                    //setupChannelList();
                    //webView.loadUrl(DEFAULT_URL);
                }
            });
        }).start();
    }

    private void saveChannelsToConfig(String json) {
        try (FileOutputStream fos = new FileOutputStream(new File(getFilesDir(), "channels.config"))) {
            fos.write(json.getBytes());
        } catch (IOException e) {
            Log.e(TAG, "Save channels config failed", e);
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
        if (hasFocus) setImmersiveMode();
    }

    @Override
    public void onBackPressed() {
        if (drawerLayout.isDrawerOpen(channelList)) {
            drawerLayout.closeDrawer(channelList);
        } else if (drawerLayout.isDrawerOpen(rightDrawerContainer)) { // 修改这里
            drawerLayout.closeDrawer(rightDrawerContainer);
        } else if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        if (webView != null) webView.destroy();
        if (tts != null) {
            tts.stop();
            tts.shutdown();
            Log.d(TAG, "TTS: 已释放资源");
        }
        super.onDestroy();
    }
}
