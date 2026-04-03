package com.dex7er.ctvplayer;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.speech.tts.TextToSpeech;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
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


public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private static final String DEFAULT_URL = "https://www.yangshipin.cn/tv/home?pid=600001859";
    private static final String GITHUB_URL = "https://raw.githubusercontent.com/TestersNightmare/ctv/master/app/src/main/assets/channels.json";
    private static final String CODEUP_URL = "https://codeup.aliyun.com/69ce4fe6f4aadfa1ed7d4a8e/ctv/raw/master/app/src/main/assets/channels.json";
    private static final String PREFS_NAME = "CTVPlayerPrefs";
    private static final String CHANNELS_KEY = "channels";
    private static final String HISTORY_KEY = "play_history";
    private static final int MAX_HISTORY = 50;
    private static final String DEFAULT_URL_KEY = "DEFAULT_URL";
    private static final int REQ_STORAGE_PERMISSION = 100;

    /** 暂停超过此时间后自动开始轮播 (测试用 10秒，正式改为 5 * 60 * 1000L) */
    private static final long PAUSE_TO_CAROUSEL_DELAY =  5 * 60 * 1000L;

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
            {"1920", "1080"}, {"2560", "1440"}, {"3440", "1440"}, {"3840", "2160"},
            {"2560", "1600"}, {"3840", "1600"}, {"5120", "2880"}, {"1920", "1200"}
    };

    // ─────────────────────────── views ───────────────────────────────────

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

    // ─────────────────────────── data ────────────────────────────────────

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
    /** 快速重试耗尽后，每隔此时间静默重试一次，直到用户换台或播放成功 */
    private static final long SLOW_RETRY_INTERVAL_MS = 5_000L;
    private Runnable retryRunnable;
    private TextToSpeech tts;
    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build();

    // ─────────────────────────── channel navigation ──────────────────────

    /** Index of currently playing channel within the flat {@code channels} list. */
    private int currentChannelIndex = -1;

    // ─────────────────────────── video state ─────────────────────────────

    private boolean isVideoPaused = false;
    private boolean isLoadingChannel = false; // true：正在加载频道（区别于暂停状态）
    private boolean isPauseScreen = false;    // true：当前显示的是暂停覆盖层
    private ImageView pausePlayButton;        // 暂停页中央的播放图标
    private Runnable pauseToCarouselRunnable;

    // ─────────────────────────── carousel ────────────────────────────────

    private CarouselManager carouselManager;
    private boolean isCarouselMode = false;
    private boolean carouselPendingPermission = false; // 等待权限授予后再启动轮播

    /** Overlay shown inside carousel mode when the user presses MENU. */
    private View imageListOverlay;
    private RecyclerView imageListRecycler;
    private ImageThumbAdapter imageListAdapter;

    // ─────────────────────────── lifecycle ───────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Keep screen on while app is in foreground
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // Allow content to extend into notch/cutout area (Android 9+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            android.view.WindowManager.LayoutParams lp = getWindow().getAttributes();
            lp.layoutInDisplayCutoutMode =
                    android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            getWindow().setAttributes(lp);
        }

        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        gson  = new Gson();
        loadPlayHistory();

        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                int result = tts.setLanguage(Locale.SIMPLIFIED_CHINESE);
                if (result == TextToSpeech.LANG_MISSING_DATA ||
                    result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.e(TAG, "TTS: 中文语言不可用");
                } else {
                    tts.setSpeechRate(1.0f);
                    tts.setPitch(1.0f);
                }
            }
        });

        initViews();
        initSimpleLoadingLayout();
        setupWebView();
        loadChannels();
        setImmersiveMode();

        // Carousel manager (images are scanned lazily on first start)
        carouselManager = new CarouselManager(this);
        carouselManager.setEventListener(() -> isCarouselMode = false);

        // Request storage permission for local image access
        checkAndRequestStoragePermission();

        // Suggest setting this app as the default launcher
        checkDefaultLauncher();


    }

    private void initViews() {
        webView    = findViewById(R.id.webview);
        webView.setVisibility(View.GONE);
        progressBar = findViewById(R.id.progress_bar);
        progressBar.setVisibility(View.GONE);
        drawerLayout = findViewById(R.id.drawer_layout);
        channelList  = findViewById(R.id.channel_list);
        historyList  = findViewById(R.id.history_list);
        aboutPage    = findViewById(R.id.about_page);
        rightDrawerContainer = (FrameLayout) aboutPage.getParent();

        GridLayoutManager channelLayoutManager = new GridLayoutManager(this, 3);
        channelLayoutManager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override public int getSpanSize(int position) { return position == 0 ? 3 : 1; }
        });
        channelList.setLayoutManager(channelLayoutManager);

        GridLayoutManager historyLayoutManager = new GridLayoutManager(this, 3);
        historyLayoutManager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override public int getSpanSize(int position) { return 1; }
        });
        historyList.setLayoutManager(historyLayoutManager);

        // 抽屉动画结束后将焦点移入列表，确保遥控器方向键可立即导航
        drawerLayout.addDrawerListener(new DrawerLayout.SimpleDrawerListener() {
            @Override
            public void onDrawerOpened(View drawerView) {
                if (drawerView == channelList) {
                    channelList.post(() -> channelList.requestFocus());
                } else {
                    // 右侧抽屉：历史列表可见时聚焦历史，否则忽略（关于页无需焦点）
                    if (historyList.getVisibility() == View.VISIBLE) {
                        historyList.post(() -> historyList.requestFocus());
                    }
                }
            }
        });
    }

    private void initSimpleLoadingLayout() {
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        int base = Math.min(metrics.widthPixels, metrics.heightPixels);

        int iconSize    = (int) (base * 0.35f);
        int spinnerSize = (int) (base * 0.30f);
        int iconMargin  = (int) (base * 0.05f);

        simpleLoadingLayout = new FrameLayout(this);
        simpleLoadingLayout.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        simpleLoadingLayout.setVisibility(View.VISIBLE);

        // Background ad image
        ImageView adImage = new ImageView(this);
        adImage.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        adImage.setScaleType(ImageView.ScaleType.CENTER_CROP);
        adImage.setImageResource(R.drawable.ad);
        simpleLoadingLayout.addView(adImage);

        // Center: channel icon + spinner
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
        currentChannelIcon.setVisibility(View.GONE);

        loadingSpinner = new ProgressBar(this);
        loadingSpinner.setLayoutParams(new LinearLayout.LayoutParams(spinnerSize, spinnerSize));

        center.addView(currentChannelIcon);
        center.addView(loadingSpinner);
        simpleLoadingLayout.addView(center);

        // 暂停覆盖层：居中大图标，点击恢复播放（仅在 isPauseScreen 时显示）
        int playBtnSize = (int) (base * 0.22f);
        pausePlayButton = new ImageView(this);
        FrameLayout.LayoutParams pbLp = new FrameLayout.LayoutParams(playBtnSize, playBtnSize, Gravity.CENTER);
        pausePlayButton.setLayoutParams(pbLp);
        pausePlayButton.setImageResource(android.R.drawable.ic_media_play);
        pausePlayButton.setScaleType(ImageView.ScaleType.FIT_CENTER);
        pausePlayButton.setBackgroundColor(0x88000000); // 半透明黑底，提升图标可见度
        pausePlayButton.setVisibility(View.GONE);
        pausePlayButton.setOnClickListener(v -> toggleVideoPlayPause());
        simpleLoadingLayout.addView(pausePlayButton);

        // Right-side remote control guide panel
        buildRemoteInfoPanel();

        // 广告/加载页面触摸分区：左 30% → 频道列表，右 30% → 历史记录
        // 解决加载页遮挡 WebView 导致 WebView.OnTouchListener 无法响应的问题
        simpleLoadingLayout.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                float x = event.getX();
                int w = getResources().getDisplayMetrics().widthPixels;
                if (x < w * 0.3f) {
                    drawerLayout.openDrawer(channelList);
                    return true;
                } else if (x > w * 0.7f) {
                    drawerLayout.openDrawer(rightDrawerContainer);
                    return true;
                }
            }
            return false;
        });

        // 必须挂在 DrawerLayout 的主内容 FrameLayout 内（drawerLayout.getChildAt(0)），
        // 而非 android.R.id.content。
        // 原因：drawerLayout 本身是 android.R.id.content 的子 View；
        // 若把 simpleLoadingLayout 加到 android.R.id.content，
        // 它会在 DrawerLayout 整体之上，导致抽屉面板（channelList / rightDrawerContainer）
        // 滑入后被完全遮挡，用户无法看到或触摸频道列表。
        // 加到 DrawerLayout 内容区后，DrawerLayout 自己的抽屉面板始终渲染在内容区之上。
        FrameLayout drawerContentFrame = (FrameLayout) drawerLayout.getChildAt(0);
        drawerContentFrame.addView(simpleLoadingLayout);
    }

    /** Builds and adds a semi-transparent remote-control tips panel to the ad page. */
    private void buildRemoteInfoPanel() {
        float dp = getResources().getDisplayMetrics().density;
        int pad = (int) (14 * dp);

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(Gravity.START);
        panel.setPadding(pad, pad, pad, pad);
        panel.setBackgroundColor(0xBB000000);

        int panelWidth = (int) (140 * dp);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                panelWidth, FrameLayout.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.END | Gravity.CENTER_VERTICAL;
        lp.rightMargin = (int) (12 * dp);
        panel.setLayoutParams(lp);

        String[] tips = {
            "⌨  遥控操作指南",
            "────────",
            "● 电视直播模式",
            "确认键  播放 / 暂停",
            "返回键  退出/返回",
            "菜单键  频道列表",
            "主页键  切换首页",
            "◄ ►键  上/下一频道",
            "",
            "────────",
            "● 图片轮播模式",
            "确认键  停止轮播",
            "◄ ►键  上/下一张",
            "菜单键  图片列表",
            "返回键  退出轮播"
        };

        for (String tip : tips) {
            TextView tv = new TextView(this);
            tv.setText(tip);
            tv.setTextColor(0xFFFFFFFF);
            tv.setTextSize(tip.startsWith("⌨") ? 14f : 12f);
            if (tip.startsWith("⌨")) tv.setTypeface(null, android.graphics.Typeface.BOLD);
            tv.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));
            panel.addView(tv);
        }

        simpleLoadingLayout.addView(panel);
    }

    // ─────────────────────────── WebView setup ───────────────────────────

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
                handler.post(() -> {
                    cancelRetry();        // 播放成功，停止所有重试
                    isLoadingChannel = false;
                    currentReloadAttempts = 0;
                    hideSimpleLoading();
                });
            }

            @JavascriptInterface
            public void onVideoError(String error) {
                Log.e(TAG, "Video loading error: " + error);
                if (currentReloadAttempts < MAX_RELOAD_ATTEMPTS) {
                    currentReloadAttempts++;
                    Log.d(TAG, "Retrying load channel (JS error), attempt: " + currentReloadAttempts);
                    handler.postDelayed(() -> webView.reload(), 1000);
                } else {
                    // 快速重试耗尽 → 转为静默慢速重试，保持加载页可见
                    currentReloadAttempts = 0;
                    Log.d(TAG, "Switching to slow retry after JS error");
                    handler.post(() -> scheduleRetry());
                }
            }

            /** Called from JS when the video element fires a 'pause' event. */
            @JavascriptInterface
            public void onVideoPaused() {
                handler.post(() -> {
                    isVideoPaused = true;
                    startPauseTimer();
                    // 仅在正常播放被暂停时显示暂停页（排除换台加载中的情况）
                    if (!isCarouselMode && !isLoadingChannel) {
                        showPauseScreen();
                    }
                });
            }

            /** Called from JS when the video element fires a 'play' or 'playing' event. */
            @JavascriptInterface
            public void onVideoPlaying() {
                handler.post(() -> {
                    isVideoPaused = false;
                    cancelPauseTimer();
                    hidePauseScreen(); // 恢复播放时收起暂停覆盖层
                });
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
                // ⚠ 不在此重置 currentReloadAttempts！
                // 每次 reload() 都会触发 onPageStarted，若在此清零，计数器将永远
                // 在 0→1 间震荡，永远无法到达 MAX_RELOAD_ATTEMPTS，
                // 导致 scheduleRetry() 永远不被调用。
                // 重置仅在两个合法时机发生：
                //   1. loadChannelInBackground()  —— 用户主动换台
                //   2. onVideoReady()             —— 视频播放成功
            }

            @Override
            public void onPageFinished(WebView v, String url) {
                super.onPageFinished(v, url);
                progressBar.setVisibility(View.GONE);
                setupDesktopEnvironment();

                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    String js =
                        "(function(){\n" +
                        // 防止同一页面被多次注入（onPageFinished 可能因重定向等被调用多次）
                        "  if(window.__ctvInjected) return; window.__ctvInjected = true;\n" +
                        "  var globalTimer = null;\n" +
                        // __reported：每次页面注入只上报一次错误，防止多条错误路径同时触发
                        "  var __reported = false;\n" +
                        "  function reportError(msg){\n" +
                        "    if(__reported) return; __reported = true;\n" +
                        "    AndroidBridge.onVideoError(msg);\n" +
                        "  }\n" +
                        // displayVideo：直接操作原始 video 元素，不替换 body.innerHTML。
                        // 替换 body 会销毁 MSE（Media Source Extensions）上下文，
                        // 导致 blob:// 流地址失效，所有 MSE 流永远无法播放。
                        // 改为 position:fixed 覆盖全屏，保留原始 MediaSource 及播放上下文。
                        "  function displayVideo(video){\n" +
                        "    if(globalTimer){ clearTimeout(globalTimer); globalTimer = null; }\n" +
                        // 将原始 video 元素固定覆盖全屏（保留 MSE 上下文，不克隆）
                        "    video.style.cssText = 'position:fixed;top:0;left:0;width:100vw;height:100vh;" +
                                "z-index:999999;background:#000;object-fit:contain;';\n" +
                        "    video.controls = true;\n" +
                        "    var v = video;\n" +
                        "    var notifyReady = function(){\n" +
                        "      if(!__reported && !v.__notified){ v.__notified=true; AndroidBridge.onVideoReady(); }\n" +
                        "    };\n" +
                        // 若视频已在播放（我们的 JS 延迟 800ms 注入，可能已经开始了）
                        "    if(v.currentTime > 0 && !v.paused && !v.ended){ notifyReady(); return; }\n" +
                        "    v.addEventListener('playing',   notifyReady);\n" +
                        "    v.addEventListener('timeupdate', function(){ if(v.currentTime>0) notifyReady(); });\n" +
                        // pause/play/playing 事件：仅在确认播放成功后才上报状态变化
                        "    v.addEventListener('pause',   function(){ if(v.__notified) AndroidBridge.onVideoPaused(); });\n" +
                        "    v.addEventListener('play',    function(){ if(v.__notified) AndroidBridge.onVideoPlaying(); });\n" +
                        "    v.addEventListener('playing', function(){ if(v.__notified) AndroidBridge.onVideoPlaying(); });\n" +
                        // 视频元素自身错误（流地址无效、解码失败等）
                        "    v.addEventListener('error', function(){ reportError('verr:'+(v.error?v.error.code:0)); });\n" +
                        // 8 秒超时：若视频仍未播放则判定失败，触发 Java 端重试
                        "    setTimeout(function(){\n" +
                        "      if(!v.__notified){\n" +
                        "        if(v.currentTime > 0){ notifyReady(); }\n" +
                        "        else{ reportError('timeout'); }\n" +
                        "      }\n" +
                        "    }, 8000);\n" +
                        // 若视频当前暂停，尝试播放；已在播放则不重复调用 play()
                        "    if(v.paused || v.ended){\n" +
                        "      var p = v.play();\n" +
                        "      if(p !== undefined){ p.catch(function(e){ reportError('play:'+e); }); }\n" +
                        "    }\n" +
                        // ⚠ 不调用 requestFullscreen()：
                        //   App 已通过 immersive mode 全屏；WebView 内调用此 API
                        //   在 Android 上不支持，且会导致 play() 被中止（AbortError）
                        "  }\n" +
                        "  var vid = document.querySelector('video');\n" +
                        "  if(vid){ displayVideo(vid); }\n" +
                        "  else {\n" +
                        // 等待视频元素出现，最多等 10 秒
                        "    globalTimer = setTimeout(function(){\n" +
                        "      obs.disconnect();\n" +
                        "      reportError('novideo');\n" +
                        "    }, 10000);\n" +
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
                // 只处理主框架错误，忽略页面内子资源（图片/脚本等）的加载失败
                if (!req.isForMainFrame()) return;
                Log.e(TAG, "WebView main-frame error: " + err.getDescription());
                if (currentReloadAttempts < MAX_RELOAD_ATTEMPTS) {
                    currentReloadAttempts++;
                    Log.d(TAG, "Retrying load channel (page error), attempt: " + currentReloadAttempts);
                    handler.postDelayed(() -> webView.reload(), 1000);
                } else {
                    // 快速重试耗尽 → 转为静默慢速重试，保持加载页可见
                    currentReloadAttempts = 0;
                    Log.d(TAG, "Switching to slow retry after page error");
                    scheduleRetry();
                }
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView v, int newProgress) {
                progressBar.setVisibility(View.GONE);
            }
        });

        String startUrl = prefs.getString(DEFAULT_URL_KEY, DEFAULT_URL);
        webView.loadUrl(startUrl);

        webView.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                float x = event.getX();
                int screenWidth = getResources().getDisplayMetrics().widthPixels;
                if (x > screenWidth * 0.7f) {
                    drawerLayout.openDrawer(rightDrawerContainer);
                    return true;
                } else if (x < screenWidth * 0.3f) {
                    drawerLayout.openDrawer(channelList);
                    return true;
                }
            }
            return false;
        });
    }

    // ─────────────────────────── remote control ──────────────────────────

    /**
     * Central remote-control dispatcher.
     * Priority: loading/ad page → image list overlay → carousel mode → drawer open → normal video mode.
     */
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() != KeyEvent.ACTION_DOWN) return super.dispatchKeyEvent(event);

        int keyCode = event.getKeyCode();

        // ── 1. Loading/ad page stuck – provide escape hatch ───────────────
        if (simpleLoadingLayout != null &&
                simpleLoadingLayout.getVisibility() == View.VISIBLE) {
            switch (keyCode) {
                case KeyEvent.KEYCODE_BACK:
                    forceExitLoadingPage();
                    return true;
                // MENU / HOME / SETTINGS → 退出加载并打开频道列表
                case KeyEvent.KEYCODE_HOME:
                case KeyEvent.KEYCODE_MENU:
                case KeyEvent.KEYCODE_SETTINGS:
                    forceExitLoadingPage();
                    drawerLayout.openDrawer(channelList);
                    return true;
                // 方向键左 / 上 → 频道列表浮于加载页之上（无需退出加载）
                case KeyEvent.KEYCODE_DPAD_LEFT:
                case KeyEvent.KEYCODE_DPAD_UP:
                    drawerLayout.openDrawer(channelList);
                    return true;
                // 方向键右 / 下 → 历史记录浮于加载页之上
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    showHistoryPage();
                    return true;
            }
        }

        // ── 2. Image list overlay (carousel image-picker sub-menu) ─────────
        if (imageListOverlay != null) {
            int cols  = 6;
            int count = imageListAdapter != null ? imageListAdapter.getItemCount() : 0;
            int sel   = imageListAdapter != null ? imageListAdapter.getSelectedPos() : 0;
            switch (keyCode) {
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    if (sel < count - 1) updateImageListSelection(sel + 1);
                    return true;
                case KeyEvent.KEYCODE_DPAD_LEFT:
                    if (sel > 0) updateImageListSelection(sel - 1);
                    return true;
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    if (sel + cols < count) updateImageListSelection(sel + cols);
                    return true;
                case KeyEvent.KEYCODE_DPAD_UP:
                    if (sel - cols >= 0) updateImageListSelection(sel - cols);
                    return true;
                case KeyEvent.KEYCODE_DPAD_CENTER:
                case KeyEvent.KEYCODE_ENTER:
                    if (carouselManager != null) carouselManager.showImageFromIndex(sel);
                    hideCarouselImageList();
                    return true;
                case KeyEvent.KEYCODE_BACK:
                case KeyEvent.KEYCODE_MENU:
                    hideCarouselImageList();
                    return true;
            }
            return true; // consume all other keys while list is open
        }

        // ── 3. Carousel mode ──────────────────────────────────────────────
        if (isCarouselMode) {
            switch (keyCode) {
                case KeyEvent.KEYCODE_DPAD_LEFT:
                    if (carouselManager != null) carouselManager.prev();
                    return true;
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    if (carouselManager != null) carouselManager.next();
                    return true;
                case KeyEvent.KEYCODE_DPAD_CENTER:
                case KeyEvent.KEYCODE_ENTER:
                    stopCarousel();
                    return true;
                case KeyEvent.KEYCODE_MENU:
                    showCarouselImageList();
                    return true;
                case KeyEvent.KEYCODE_BACK:
                    stopCarousel();
                    return true;
                case KeyEvent.KEYCODE_HOME:
                    stopCarousel();
                    drawerLayout.openDrawer(channelList);
                    return true;
            }
            return true; // consume unhandled keys in carousel mode
        }

        // ── 4. Drawer open – let RecyclerView handle D-pad focus natively ─
        if (drawerLayout.isDrawerOpen(channelList) ||
                drawerLayout.isDrawerOpen(rightDrawerContainer)) {
            switch (keyCode) {
                case KeyEvent.KEYCODE_BACK:
                    if (drawerLayout.isDrawerOpen(channelList))
                        drawerLayout.closeDrawer(channelList);
                    if (drawerLayout.isDrawerOpen(rightDrawerContainer))
                        drawerLayout.closeDrawer(rightDrawerContainer);
                    return true;
                case KeyEvent.KEYCODE_HOME:
                    drawerLayout.closeDrawer(channelList);
                    drawerLayout.closeDrawer(rightDrawerContainer);
                    return true;
            }
            return super.dispatchKeyEvent(event); // pass D-pad to RecyclerView items
        }

        // ── 5. Normal video mode ──────────────────────────────────────────
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
                toggleVideoPlayPause();
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                playPrevChannel();
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                playNextChannel();
                return true;
            case KeyEvent.KEYCODE_MENU:
            case KeyEvent.KEYCODE_SETTINGS:  // 部分品牌遥控器以 SETTINGS 键代替 MENU
                drawerLayout.openDrawer(channelList);
                return true;
            case KeyEvent.KEYCODE_HOME:
                // Open channel list as the app "home page"
                drawerLayout.openDrawer(channelList);
                return true;
        }

        return super.dispatchKeyEvent(event);
    }

    // ─────────────────────────── video controls ──────────────────────────

    private void toggleVideoPlayPause() {
        webView.evaluateJavascript(
            "(function(){var v=document.querySelector('video');if(v){if(v.paused)v.play();else v.pause();}})()",
            null);
    }

    private void playNextChannel() {
        if (channels.isEmpty()) return;
        currentChannelIndex = (currentChannelIndex + 1) % channels.size();
        onChannelClicked(channels.get(currentChannelIndex));
    }

    private void playPrevChannel() {
        if (channels.isEmpty()) return;
        if (currentChannelIndex < 0) currentChannelIndex = 0;
        currentChannelIndex = (currentChannelIndex - 1 + channels.size()) % channels.size();
        onChannelClicked(channels.get(currentChannelIndex));
    }

    // ─────────────────────────── pause-to-carousel timer ─────────────────

    private void startPauseTimer() {
        cancelPauseTimer();
        pauseToCarouselRunnable = () -> {
            if (isVideoPaused && !isCarouselMode) {
                // Dismiss stuck loading/ad page first
                if (simpleLoadingLayout.getVisibility() == View.VISIBLE) {
                    simpleLoadingLayout.setVisibility(View.GONE);
                }
                startCarousel();
            }
        };
        handler.postDelayed(pauseToCarouselRunnable, PAUSE_TO_CAROUSEL_DELAY);
    }

    private void cancelPauseTimer() {
        if (pauseToCarouselRunnable != null) {
            handler.removeCallbacks(pauseToCarouselRunnable);
            pauseToCarouselRunnable = null;
        }
    }

    // ─────────────────────────── auto retry ──────────────────────────────

    /**
     * 在 SLOW_RETRY_INTERVAL_MS 后静默重载 WebView，重置快速重试计数。
     * 每次慢速重试触发后，如果页面再次失败，onReceivedError / onVideoError 会再次调用此方法，
     * 形成"无限重试"闭环，直到用户换台（cancelRetry）或播放成功（onVideoReady）。
     */
    private void scheduleRetry() {
        cancelRetry();
        retryRunnable = () -> {
            currentReloadAttempts = 0; // 重置计数，让快速重试重新工作
            Log.d(TAG, "Slow retry: reloading WebView");
            webView.reload();
        };
        handler.postDelayed(retryRunnable, SLOW_RETRY_INTERVAL_MS);
    }

    private void cancelRetry() {
        if (retryRunnable != null) {
            handler.removeCallbacks(retryRunnable);
            retryRunnable = null;
        }
    }

    // ─────────────────────────── pause screen ────────────────────────────

    /**
     * 视频暂停时覆盖广告页面（不隐藏 WebView）。
     * 中央显示播放图标，左右触摸分区已由 simpleLoadingLayout 的 OnTouchListener 处理。
     */
    private void showPauseScreen() {
        if (isPauseScreen) return; // 已显示，避免重复
        isPauseScreen = true;
        // 只显示覆盖层，不隐藏 WebView（视频仍在后面，恢复播放更流畅）
        loadingSpinner.setVisibility(View.GONE);
        currentChannelIcon.setVisibility(View.GONE);
        pausePlayButton.setVisibility(View.VISIBLE);
        simpleLoadingLayout.animate().cancel();
        simpleLoadingLayout.setAlpha(1f);
        simpleLoadingLayout.setVisibility(View.VISIBLE);
    }

    /** 恢复播放时收起暂停覆盖层。 */
    private void hidePauseScreen() {
        if (!isPauseScreen) return;
        isPauseScreen = false;
        pausePlayButton.setVisibility(View.GONE);
        simpleLoadingLayout.setVisibility(View.GONE);
        // 为下次加载恢复 spinner 可见性
        loadingSpinner.setVisibility(View.VISIBLE);
    }

    // ─────────────────────────── carousel ────────────────────────────────

    /** Scan images in background and start the fullscreen photo carousel. */
    private void startCarousel() {
        // 先检查存储权限，没有就先申请，授予后自动继续
        String perm = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                ? Manifest.permission.READ_MEDIA_IMAGES
                : Manifest.permission.READ_EXTERNAL_STORAGE;
        if (ContextCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED) {
            carouselPendingPermission = true;
            ActivityCompat.requestPermissions(this, new String[]{perm}, REQ_STORAGE_PERMISSION);
            return;
        }

        // Pause video audio
        webView.evaluateJavascript(
            "(function(){var v=document.querySelector('video');if(v)v.pause();})()", null);
        cancelPauseTimer();
        isCarouselMode = true;

        new Thread(() -> {
            carouselManager.scanImages(); // MediaStore scan (off main thread)
            runOnUiThread(() -> {
                if (carouselManager.getImages().isEmpty()) {
                    Toast.makeText(this, "未在 DCIM / 图片 / 截图 找到图片", Toast.LENGTH_SHORT).show();
                    isCarouselMode = false;
                    return;
                }
                FrameLayout mainLayout = findViewById(android.R.id.content);
                carouselManager.start(mainLayout, 0);
            });
        }).start();
    }

    /** Stop the carousel and resume video playback. */
    private void stopCarousel() {
        if (carouselManager != null && carouselManager.isRunning()) {
            carouselManager.stop();
        }
        isCarouselMode = false;
        hideCarouselImageList();
        // Resume video
        webView.evaluateJavascript(
            "(function(){var v=document.querySelector('video');if(v)v.play();})()", null);
    }

    // ─────────────────────────── carousel image list ─────────────────────

    private void showCarouselImageList() {
        if (imageListOverlay != null || carouselManager == null) return;
        List<CarouselManager.ImageItem> images = carouselManager.getImages();
        if (images.isEmpty()) return;

        FrameLayout mainLayout = findViewById(android.R.id.content);
        float dp       = getResources().getDisplayMetrics().density;
        int thumbSize  = (int) (100 * dp);
        int pad        = (int) (12 * dp);
        int topMargin  = getResources().getDisplayMetrics().heightPixels / 3;

        FrameLayout overlay = new FrameLayout(this);
        overlay.setBackgroundColor(0xCC000000);
        overlay.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        RecyclerView rv = new RecyclerView(this);
        FrameLayout.LayoutParams rvLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
        rvLp.topMargin = topMargin;
        rv.setLayoutParams(rvLp);
        rv.setPadding(pad, pad, pad, pad);
        rv.setClipToPadding(false);
        rv.setLayoutManager(new GridLayoutManager(this, 6));

        imageListAdapter = new ImageThumbAdapter(images, thumbSize);
        rv.setAdapter(imageListAdapter);

        overlay.addView(rv);
        mainLayout.addView(overlay);

        imageListOverlay  = overlay;
        imageListRecycler = rv;
        imageListAdapter.setSelected(0);
    }

    private void hideCarouselImageList() {
        if (imageListOverlay != null) {
            ViewGroup parent = (ViewGroup) imageListOverlay.getParent();
            if (parent != null) parent.removeView(imageListOverlay);
            imageListOverlay  = null;
            imageListRecycler = null;
            imageListAdapter  = null;
        }
    }

    private void updateImageListSelection(int newPos) {
        if (imageListAdapter == null || imageListRecycler == null) return;
        imageListAdapter.setSelected(newPos);
        imageListRecycler.scrollToPosition(newPos);
    }

    // ─────────────────────────── image thumb adapter ─────────────────────

    private class ImageThumbAdapter extends RecyclerView.Adapter<ImageThumbAdapter.VH> {

        private final List<CarouselManager.ImageItem> items;
        private final int thumbSize;
        private int selectedPos = 0;

        ImageThumbAdapter(List<CarouselManager.ImageItem> items, int thumbSize) {
            this.items    = items;
            this.thumbSize = thumbSize;
        }

        void setSelected(int pos) {
            int old = selectedPos;
            selectedPos = pos;
            notifyItemChanged(old);
            notifyItemChanged(selectedPos);
        }

        int getSelectedPos() { return selectedPos; }

        @Override
        public VH onCreateViewHolder(ViewGroup parent, int viewType) {
            FrameLayout fl = new FrameLayout(MainActivity.this);
            fl.setLayoutParams(new RecyclerView.LayoutParams(thumbSize, thumbSize));
            fl.setPadding(3, 3, 3, 3);
            ImageView iv = new ImageView(MainActivity.this);
            iv.setLayoutParams(new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT));
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            fl.addView(iv);
            return new VH(fl);
        }

        @Override
        public void onBindViewHolder(VH holder, int pos) {
            FrameLayout fl = (FrameLayout) holder.itemView;
            ImageView iv  = (ImageView) fl.getChildAt(0);
            Glide.with(MainActivity.this)
                    .load(items.get(pos).uri)
                    .thumbnail(0.15f)
                    .into(iv);
            fl.setBackgroundColor(pos == selectedPos ? 0xFF2196F3 : Color.TRANSPARENT);
            fl.setOnClickListener(v -> {
                if (carouselManager != null) carouselManager.showImageFromIndex(pos);
                hideCarouselImageList();
            });
        }

        @Override public int getItemCount() { return items.size(); }

        class VH extends RecyclerView.ViewHolder { VH(View v) { super(v); } }
    }

    // ─────────────────────────── loading page helpers ────────────────────

    /**
     * Immediately hides the ad/loading page without animation.
     * Escape hatch when the page is stuck (e.g., network failure).
     */
    private void forceExitLoadingPage() {
        cancelRetry();
        isPauseScreen = false;
        isLoadingChannel = false;
        pausePlayButton.setVisibility(View.GONE);
        loadingSpinner.setVisibility(View.VISIBLE);
        simpleLoadingLayout.animate().cancel();
        simpleLoadingLayout.setAlpha(1f);
        simpleLoadingLayout.setVisibility(View.GONE);
        webView.setVisibility(View.VISIBLE);
    }

    private void hideSimpleLoading() {
        isPauseScreen = false;
        isLoadingChannel = false;
        cancelRetry();
        pausePlayButton.setVisibility(View.GONE);
        loadingSpinner.setVisibility(View.VISIBLE);
        webView.setVisibility(View.VISIBLE);
        simpleLoadingLayout.animate()
                .alpha(0f)
                .setDuration(500)
                .withEndAction(() -> {
                    simpleLoadingLayout.setVisibility(View.GONE);
                    simpleLoadingLayout.setAlpha(1f);
                })
                .start();
    }

    // ─────────────────────────── desktop env helpers ──────────────────────

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

    // ─────────────────────────── channel list setup ───────────────────────

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
                    for (ChannelGroup g : channelGroups)
                        if (g.getChannels() != null) channels.addAll(g.getChannels());
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
                    for (ChannelGroup g : channelGroups)
                        if (g.getChannels() != null) channels.addAll(g.getChannels());
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
                    if (resp.isSuccessful()) { allFailed = false; break; }
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

    private void setupChannelList() {
        LinearLayout buttonContainer = new LinearLayout(this);
        buttonContainer.setOrientation(LinearLayout.HORIZONTAL);
        buttonContainer.setGravity(Gravity.CENTER);
        buttonContainer.setPadding(16, 16, 16, 16);
        // 让焦点穿透到子按钮，而不是停在容器本身
        buttonContainer.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);

        int containerWidthPx = (int) (300 * getResources().getDisplayMetrics().density);
        int buttonSize = (containerWidthPx - 64) / 3;
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(buttonSize, buttonSize);
        buttonParams.setMargins(10, 8, 10, 8);

        // ── 轮播按钮（替换原历史按钮） ────────────────────────────────────
        ImageView carouselButton = new ImageView(this);
        carouselButton.setLayoutParams(buttonParams);
        carouselButton.setImageResource(R.drawable.ic_history);
        carouselButton.setScaleType(ImageView.ScaleType.FIT_CENTER);
        carouselButton.setPadding(4, 4, 4, 4);
        carouselButton.setFocusable(true);
        carouselButton.setClickable(true);
        carouselButton.setBackgroundResource(R.drawable.channel_item_bg);
        carouselButton.setOnClickListener(v -> {
            drawerLayout.closeDrawer(channelList, false);
            startCarousel();
        });
        buttonContainer.addView(carouselButton);

        // ── 更新按钮 ──────────────────────────────────────────────────────
        ImageView updateButton = new ImageView(this);
        updateButton.setLayoutParams(buttonParams);
        updateButton.setImageResource(R.drawable.ic_update);
        updateButton.setScaleType(ImageView.ScaleType.FIT_CENTER);
        updateButton.setPadding(4, 4, 4, 4);
        updateButton.setFocusable(true);
        updateButton.setClickable(true);
        updateButton.setBackgroundResource(R.drawable.channel_item_bg);
        updateButton.setOnClickListener(v -> updateChannels());
        buttonContainer.addView(updateButton);

        // ── 关于按钮 ──────────────────────────────────────────────────────
        ImageView aboutButton = new ImageView(this);
        aboutButton.setLayoutParams(buttonParams);
        aboutButton.setImageResource(R.drawable.ic_about);
        aboutButton.setScaleType(ImageView.ScaleType.FIT_CENTER);
        aboutButton.setPadding(4, 4, 4, 4);
        aboutButton.setFocusable(true);
        aboutButton.setClickable(true);
        aboutButton.setBackgroundResource(R.drawable.channel_item_bg);
        aboutButton.setOnClickListener(v -> showAboutPage());
        buttonContainer.addView(aboutButton);

        // Flat channel list for adapter
        List<Channel> flatChannels = new ArrayList<>();
        for (ChannelGroup group : channelGroups)
            if (group.getChannels() != null) flatChannels.addAll(group.getChannels());

        RecyclerView.Adapter<RecyclerView.ViewHolder> adapter =
                new ChannelAdapter(this, flatChannels, this::onChannelClicked);
        channelList.setAdapter(new HeaderFooterAdapter(adapter, buttonContainer, null));
        setupHistoryList();
    }

    private void showAboutPage() {
        // Update version number dynamically
        TextView tvVersion = aboutPage.findViewById(R.id.tv_version);
        if (tvVersion != null) {
            try {
                String versionName = getPackageManager()
                        .getPackageInfo(getPackageName(), 0).versionName;
                tvVersion.setText("看电视 | CTVPlayer | v" + versionName);
            } catch (PackageManager.NameNotFoundException ignored) {}
        }

        if (drawerLayout.isDrawerOpen(rightDrawerContainer)) {
            if (aboutPage.getVisibility() == View.VISIBLE) {
                drawerLayout.closeDrawer(rightDrawerContainer);
            } else {
                historyList.setVisibility(View.GONE);
                aboutPage.setVisibility(View.VISIBLE);
            }
        } else {
            historyList.setVisibility(View.GONE);
            aboutPage.setVisibility(View.VISIBLE);
            drawerLayout.openDrawer(rightDrawerContainer);
        }
    }

    private void showHistoryPage() {
        if (drawerLayout.isDrawerOpen(rightDrawerContainer)) {
            if (historyList.getVisibility() == View.VISIBLE) {
                drawerLayout.closeDrawer(rightDrawerContainer);
            } else {
                aboutPage.setVisibility(View.GONE);
                historyList.setVisibility(View.VISIBLE);
            }
        } else {
            aboutPage.setVisibility(View.GONE);
            historyList.setVisibility(View.VISIBLE);
            drawerLayout.openDrawer(rightDrawerContainer);
        }
    }

    private void setupHistoryList() {
        HistoryAdapter adapter = new HistoryAdapter(this, playHistory, history -> {
            drawerLayout.closeDrawer(rightDrawerContainer);
            showSimpleLoading(history);
            speakChannelName(history.getName());
            loadChannelInBackground(history);
        });
        historyList.setAdapter(adapter);
    }

    // ─────────────────────────── channel click / loading ─────────────────

    private void onChannelClicked(Channel channel) {
        currentChannelIndex = channels.indexOf(channel);
        drawerLayout.closeDrawer(channelList, false);
        showSimpleLoading(channel);
        speakChannelName(channel.getName());
        addPlayHistory(channel);
        loadChannelInBackground(channel);
    }

    private void speakChannelName(String name) {
        String channelName = name != null ? name : "未知频道";
        String ttsText = "正在播放" + channelName;
        if (tts != null && tts.isLanguageAvailable(Locale.SIMPLIFIED_CHINESE) >= 0) {
            tts.speak(ttsText, TextToSpeech.QUEUE_FLUSH, null, null);
            Log.d(TAG, "TTS: " + ttsText);
        }
    }

    private void showSimpleLoading(Channel channel) {
        isPauseScreen = false;
        isLoadingChannel = true;
        pausePlayButton.setVisibility(View.GONE);
        loadingSpinner.setVisibility(View.VISIBLE);
        loadChannelIcon(currentChannelIcon, channel);
        simpleLoadingLayout.animate().cancel();
        simpleLoadingLayout.setAlpha(1f);
        simpleLoadingLayout.setVisibility(View.VISIBLE);
        webView.setVisibility(View.GONE);
    }

    private void showSimpleLoading(PlayHistory history) {
        isPauseScreen = false;
        isLoadingChannel = true;
        pausePlayButton.setVisibility(View.GONE);
        loadingSpinner.setVisibility(View.VISIBLE);
        loadChannelIcon(currentChannelIcon, history);
        simpleLoadingLayout.animate().cancel();
        simpleLoadingLayout.setAlpha(1f);
        simpleLoadingLayout.setVisibility(View.VISIBLE);
        webView.setVisibility(View.GONE);
    }

    private void loadChannelIcon(ImageView iv, Channel channel) {
        String icUrl = channel.getIcUrl();
        if (icUrl != null && !icUrl.isEmpty()) {
            String name = icUrl.substring(icUrl.lastIndexOf('/') + 1).replaceAll("\\..*$", "");
            int resId = getResources().getIdentifier(name, "drawable", getPackageName());
            if (resId != 0) iv.setImageResource(resId);
            else Glide.with(this).load(icUrl).error(R.drawable.ic_tv_default).into(iv);
        } else {
            iv.setImageResource(R.drawable.ic_tv_default);
        }
    }

    private void loadChannelIcon(ImageView iv, PlayHistory history) {
        String icUrl = history.getIcUrl();
        if (icUrl != null && !icUrl.isEmpty()) {
            String name = icUrl.substring(icUrl.lastIndexOf('/') + 1).replaceAll("\\..*$", "");
            int resId = getResources().getIdentifier(name, "drawable", getPackageName());
            if (resId != 0) iv.setImageResource(resId);
            else Glide.with(this).load(icUrl).error(R.drawable.ic_tv_default).into(iv);
        } else {
            iv.setImageResource(R.drawable.ic_tv_default);
        }
    }

    private void loadChannelInBackground(Channel channel) {
        if (channel.getUrl() == null || channel.getUrl().isEmpty()) { hideSimpleLoading(); return; }
        cancelRetry(); // 换台时停止上一频道的所有重试
        currentReloadAttempts = 0;
        webView.getSettings().setUserAgentString(getRandomDesktopUserAgent());
        Log.d(TAG, "Loading channel: " + channel.getName());
        webView.loadUrl(channel.getUrl());
    }

    private void loadChannelInBackground(PlayHistory history) {
        if (history.getUrl() == null || history.getUrl().isEmpty()) { hideSimpleLoading(); return; }
        cancelRetry(); // 换台时停止上一频道的所有重试
        currentReloadAttempts = 0;
        webView.getSettings().setUserAgentString(getRandomDesktopUserAgent());
        Log.d(TAG, "Loading history channel: " + history.getName());
        webView.loadUrl(history.getUrl());
    }

    // ─────────────────────────── history ─────────────────────────────────

    private void addPlayHistory(Channel channel) {
        String url   = channel.getUrl();
        String icUrl = channel.getIcUrl();
        String name  = channel.getName() != null ? channel.getName() : "未知频道";

        for (int i = playHistory.size() - 1; i >= 0; i--) {
            PlayHistory h = playHistory.get(i);
            if (h != null && name.equals(h.getName())) playHistory.remove(i);
        }
        playHistory.add(0, new PlayHistory(url, icUrl, System.currentTimeMillis(), name));
        if (playHistory.size() > MAX_HISTORY)
            playHistory = playHistory.subList(0, MAX_HISTORY);

        prefs.edit()
                .putString(HISTORY_KEY, gson.toJson(playHistory))
                .putString(DEFAULT_URL_KEY, url)
                .apply();
        setupHistoryList();
    }

    private void loadPlayHistory() {
        String historyJson = prefs.getString(HISTORY_KEY, null);
        if (historyJson != null) {
            Type listType = new TypeToken<List<PlayHistory>>(){}.getType();
            playHistory = gson.fromJson(historyJson, listType);
            if (playHistory == null) playHistory = new ArrayList<>();
            Collections.sort(playHistory,
                    (h1, h2) -> Long.compare(h2.getTimestamp(), h1.getTimestamp()));
            if (playHistory.size() > MAX_HISTORY)
                playHistory = playHistory.subList(0, MAX_HISTORY);
        }
    }

    // ─────────────────────────── remote channel update ───────────────────

    private void updateChannels() {
        Toast.makeText(this, "正在更新频道列表...", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            String content = null;
            try {
                Request req = new Request.Builder().url(GITHUB_URL)
                        .addHeader("User-Agent", getRandomDesktopUserAgent()).build();
                Response resp = client.newCall(req).execute();
                if (resp.isSuccessful() && resp.body() != null) {
                    content = resp.body().string().trim();
                    Log.d(TAG, "Fetched from GitHub");
                } else {
                    Log.e(TAG, "GitHub fetch failed, code: " + resp.code());
                }
            } catch (IOException e) {
                Log.e(TAG, "GitHub fetch error: " + e.getMessage());
            }

            if (content == null) {
                try {
                    Request req = new Request.Builder().url(CODEUP_URL)
                            .addHeader("User-Agent", getRandomDesktopUserAgent()).build();
                    Response resp = client.newCall(req).execute();
                    if (resp.isSuccessful() && resp.body() != null) {
                        content = resp.body().string().trim();
                        Log.d(TAG, "Fetched from Codeup");
                    } else {
                        Log.e(TAG, "Codeup fetch failed, code: " + resp.code());
                    }
                } catch (IOException e) {
                    Log.e(TAG, "Codeup fetch error: " + e.getMessage());
                }
            }

            if (content != null && content.startsWith("[") && content.endsWith("]")) {
                channelGroups = ChannelParser.parseGroupedJson(content);
                channels.clear();
                if (channelGroups != null)
                    for (ChannelGroup g : channelGroups)
                        if (g.getChannels() != null) channels.addAll(g.getChannels());
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
                        if (channelGroups != null)
                            for (ChannelGroup g : channelGroups)
                                if (g.getChannels() != null) channels.addAll(g.getChannels());
                        if (!channels.isEmpty()) {
                            Log.d(TAG, "Loaded " + channels.size() + " channels from SharedPreferences");
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
                            fis.read(buffer); fis.close();
                            channelsJson = new String(buffer);
                            channelGroups = ChannelParser.parseGroupedJson(channelsJson);
                            channels.clear();
                            if (channelGroups != null)
                                for (ChannelGroup g : channelGroups)
                                    if (g.getChannels() != null) channels.addAll(g.getChannels());
                            if (!channels.isEmpty()) {
                                Log.d(TAG, "Loaded " + channels.size() + " channels from channels.config");
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
                    if (tts != null && tts.isLanguageAvailable(Locale.SIMPLIFIED_CHINESE) >= 0)
                        tts.speak(errorMsg, TextToSpeech.QUEUE_FLUSH, null, null);
                    Log.e(TAG, "All sources failed");
                }
            });
        }).start();
    }

    private void saveChannelsToConfig(String json) {
        try (FileOutputStream fos = new FileOutputStream(
                new File(getFilesDir(), "channels.config"))) {
            fos.write(json.getBytes());
        } catch (IOException e) {
            Log.e(TAG, "Save channels config failed", e);
        }
    }

    // ─────────────────────────── permissions & launcher ──────────────────

    private void checkAndRequestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.READ_MEDIA_IMAGES},
                        REQ_STORAGE_PERMISSION);
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                        REQ_STORAGE_PERMISSION);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_STORAGE_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // 权限已授予：如果是轮播触发的申请，继续启动轮播
                if (carouselPendingPermission) {
                    carouselPendingPermission = false;
                    startCarousel();
                }
            } else {
                carouselPendingPermission = false;
                Toast.makeText(this, "未获得存储权限，无法读取本地图片", Toast.LENGTH_SHORT).show();
            }
        }
    }

    /** Shows a dialog suggesting this app be set as the default launcher. */
    private void checkDefaultLauncher() {
        SharedPreferences prefs = getSharedPreferences("app_prefs", MODE_PRIVATE);
        if (prefs.getBoolean("launcher_dialog_dismissed", false)) return;

        Intent homeIntent = new Intent(Intent.ACTION_MAIN);
        homeIntent.addCategory(Intent.CATEGORY_HOME);
        ResolveInfo resolveInfo = getPackageManager()
                .resolveActivity(homeIntent, PackageManager.MATCH_DEFAULT_ONLY);
        if (resolveInfo != null &&
                !getPackageName().equals(resolveInfo.activityInfo.packageName)) {
            new MaterialAlertDialogBuilder(this)
                    .setIcon(R.mipmap.ic_launcher_round)
                    .setTitle("设为默认桌面")
                    .setMessage("将此应用设为默认桌面，打开电视或者平板就可以直接播放上次播放的频道。")
                    .setPositiveButton("去设置", (d, w) -> startActivity(homeIntent))
                    .setNegativeButton("暂不", (d, w) ->
                            prefs.edit().putBoolean("launcher_dialog_dismissed", true).apply())
                    .show();
        }
    }

    // ─────────────────────────── immersive mode ───────────────────────────

    private void setImmersiveMode() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE          |
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN      |
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION        |
                View.SYSTEM_UI_FLAG_FULLSCREEN              |
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        );
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) setImmersiveMode();
    }

    // ─────────────────────────── back-press ──────────────────────────────

    @Override
    public void onBackPressed() {
        // 1. Close image list overlay inside carousel
        if (imageListOverlay != null) {
            hideCarouselImageList();
            return;
        }
        // 2. Stop carousel → return to video
        if (isCarouselMode) {
            stopCarousel();
            return;
        }
        // 3. Force-exit stuck loading/ad page → open channel list
        if (simpleLoadingLayout != null &&
                simpleLoadingLayout.getVisibility() == View.VISIBLE) {
            forceExitLoadingPage();
            drawerLayout.openDrawer(channelList);
            return;
        }
        // 4. Close open drawers
        if (drawerLayout.isDrawerOpen(channelList)) {
            drawerLayout.closeDrawer(channelList);
            return;
        }
        if (drawerLayout.isDrawerOpen(rightDrawerContainer)) {
            drawerLayout.closeDrawer(rightDrawerContainer);
            return;
        }
        // 5. WebView back stack
        if (webView.canGoBack()) {
            webView.goBack();
            return;
        }
        // 6. Exit app
        super.onBackPressed();
    }

    // ─────────────────────────── cleanup ─────────────────────────────────

    @Override
    protected void onDestroy() {
        cancelPauseTimer();
        cancelRetry();
        if (carouselManager != null && carouselManager.isRunning()) carouselManager.stop();
        if (webView != null) webView.destroy();
        if (tts != null) { tts.stop(); tts.shutdown(); }
        super.onDestroy();
    }
}
