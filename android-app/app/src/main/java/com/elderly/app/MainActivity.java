package com.elderly.app;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Insets;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Message;
import android.view.View;
import android.view.WindowInsets;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.CookieManager;

/**
 * 智慧助老数字学习系统 —— Android WebView 套壳
 * 整个 App 仅包含一个全屏 WebView，加载 Vue3+Vant4 开发的 H5 应用。
 */
public class MainActivity extends Activity {

    /**
     * H5 应用地址。
     * 发布时用 -PappBaseUrl=https://服务器地址/ 指定已验证的 HTTPS 源站。
     * 页面与接口由云服务器提供，手机不再需要与开发电脑处于同一网络。
     */
    private static final String BASE_URL = BuildConfig.APP_BASE_URL;

    private WebView webView;
    private SystemSpeech systemSpeech;
    private boolean appPaused;
    private ValueCallback<Uri[]> filePathCallback;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        configureSafeArea();

        webView = findViewById(R.id.webview);
        CookieManager.getInstance().setAcceptCookie(true);
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);          // H5 应用必须
        s.setDomStorageEnabled(true);          // localStorage 保存登录 token
        s.setMediaPlaybackRequiresUserGesture(false); // 允许 TTS 音频自动播放
        s.setTextZoom(100);                    // 字号由应用内适老化设置控制，不随系统字体缩放
        s.setSupportMultipleWindows(true);     // 支持 window.open（政务网站跳转）

        // 应用内导航全部留在 WebView 中
        webView.setWebViewClient(new WebViewClient());
        // 安装包下载由系统浏览器处理，避免 WebView 点击后没有反馈。
        webView.setDownloadListener((url, userAgent, contentDisposition, mimeType, contentLength) -> {
            Uri download = Uri.parse(url);
            if ("https".equals(download.getScheme())) {
                startActivity(new Intent(Intent.ACTION_VIEW, download));
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            // window.open（政务网站跳转等外部链接）交给系统浏览器打开，便于老人按返回键回到 App
            @Override
            public boolean onCreateWindow(WebView view, boolean isDialog,
                                          boolean isUserGesture, Message resultMsg) {
                WebView temp = new WebView(view.getContext());
                temp.setWebViewClient(new WebViewClient() {
                    @Override
                    public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest request) {
                        startActivity(new Intent(Intent.ACTION_VIEW, request.getUrl()));
                        return true;
                    }
                });
                WebView.WebViewTransport transport = (WebView.WebViewTransport) resultMsg.obj;
                transport.setWebView(temp);
                resultMsg.sendToTarget();
                return true;
            }

            // 个人中心头像等文件上传
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback,
                                             FileChooserParams params) {
                filePathCallback = callback;
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                String[] types = params.getAcceptTypes();
                intent.setType(types.length == 1 && !types[0].isEmpty() ? types[0] : "*/*");
                if (types.length > 1) intent.putExtra(Intent.EXTRA_MIME_TYPES, types);
                startActivityForResult(Intent.createChooser(intent, "选择图片"), 1);
                return true;
            }
        });

        systemSpeech = new SystemSpeech(this, webView, BASE_URL);
        webView.loadUrl(BASE_URL);
    }

    /** Keep the entire WebView (including fixed headers and popups) outside system UI. */
    private void configureSafeArea() {
        // Older Android versions already fit this non-fullscreen window to system bars.
        // Android 15 enforces edge-to-edge for target SDK 35, so handle it explicitly.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return;

        getWindow().setDecorFitsSystemWindows(false);
        View container = findViewById(R.id.webview_container);
        container.setOnApplyWindowInsetsListener((view, windowInsets) -> {
            int handledTypes = WindowInsets.Type.systemBars()
                    | WindowInsets.Type.displayCutout() | WindowInsets.Type.ime();
            Insets safeArea = windowInsets.getInsets(handledTypes);
            // Assign absolute padding each time: rotation and keyboard changes must not accumulate.
            view.setPadding(safeArea.left, safeArea.top, safeArea.right, safeArea.bottom);
            // Forward zeroed insets so WebView updates without applying the same space twice.
            return new WindowInsets.Builder(windowInsets)
                    .setInsets(handledTypes, Insets.NONE)
                    .setInsetsIgnoringVisibility(WindowInsets.Type.systemBars()
                            | WindowInsets.Type.displayCutout(), Insets.NONE)
                    .setDisplayCutout(null)
                    .build();
        });
        container.requestApplyInsets();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 1 && filePathCallback != null) {
            Uri uri = (resultCode == RESULT_OK && data != null) ? data.getData() : null;
            filePathCallback.onReceiveValue(uri != null ? new Uri[]{uri} : null);
            filePathCallback = null;
        }
    }

    // 返回键优先回退 H5 页面历史，避免直接退出 App
    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onPause() {
        appPaused = true;
        if (systemSpeech != null) systemSpeech.stop();
        if (webView != null) {
            webView.evaluateJavascript("window.dispatchEvent(new Event('elderly:background'));document.querySelectorAll('audio,video').forEach(function(m){m.pause()});if(window.speechSynthesis)window.speechSynthesis.cancel();",
                    ignored -> { if (appPaused && webView != null) webView.pauseTimers(); });
            webView.onPause();
        }
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        appPaused = false;
        if (webView != null) {
            webView.resumeTimers();
            webView.onResume();
            webView.evaluateJavascript("window.dispatchEvent(new Event('elderly:foreground'));",null);
        }
    }

    @Override
    protected void onStop() {
        CookieManager.getInstance().flush();
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        if (systemSpeech != null) systemSpeech.shutdown();
        if (webView != null) {
            webView.destroy();
        }
        super.onDestroy();
    }
}
