package com.chengvar.zocode;

import android.content.ClipboardManager;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanOptions;

public class MainActivity extends AppCompatActivity {

    private static final String PREFS = "zocode";
    private static final String KEY_URL = "url";
    private static final long CONNECT_TIMEOUT_MS = 10_000;

    private WebView webView;
    private View homeView;
    private View loadingView;
    private View scrimView;

    private final Handler timeoutHandler = new Handler(Looper.getMainLooper());
    private boolean mainFrameFailed = false;
    private boolean connecting = false;

    private final ActivityResultLauncher<ScanOptions> scanLauncher =
            registerForActivityResult(new ScanContract(), result -> {
                String text = result.getContents();
                if (text != null && !text.isEmpty()) {
                    loadUrl(text);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.parseColor("#1E1E1E"));
        setContentView(root);

        // 沉浸式:内容延伸到状态栏下,顶部叠一条半透明渐变遮罩(玻璃感)
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            Insets bars;
            if (Build.VERSION.SDK_INT >= 30) {
                bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            } else {
                bars = Insets.of(0, insets.getSystemWindowInsetTop(), 0, insets.getSystemWindowInsetBottom());
            }
            // 底部导航栏区域仍用 padding 避让,顶部让内容穿过去
            v.setPadding(0, 0, 0, bars.bottom);
            if (scrimView != null) {
                scrimView.getLayoutParams().height = bars.top;
                scrimView.requestLayout();
            }
            return insets;
        });

        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        root.addView(column, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        homeView = buildHome();
        column.addView(homeView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        column.addView(buildLoading(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        webView = new WebView(this);
        webView.setBackgroundColor(Color.parseColor("#1E1E1E"));
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.setVerticalScrollBarEnabled(false);
        webView.setHorizontalScrollBarEnabled(false);
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return false; // 所有导航留在 app 内
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame()) {
                    mainFrameFailed = true;
                }
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                if (connecting) {
                    finishConnect(mainFrameFailed);
                }
            }
        });
        webView.setWebChromeClient(new WebChromeClient());
        webView.setVisibility(View.GONE);
        column.addView(webView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        // 顶部玻璃遮罩:盖在内容之上,高度 = 状态栏高度
        scrimView = new View(this);
        scrimView.setBackground(new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xCC1E1E1E, 0x661E1E1E, 0x001E1E1E}));
        FrameLayout.LayoutParams scrimParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, Gravity.TOP);
        root.addView(scrimView, scrimParams);

        String saved = getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY_URL, "");
        if (!saved.isEmpty()) {
            // 启动即尝试恢复上次连接,失败则回主页
            startConnect(saved);
        }
    }

    private View buildLoading() {
        loadingView = new FrameLayout(this);
        loadingView.setBackgroundColor(Color.parseColor("#1E1E1E"));
        loadingView.setVisibility(View.GONE);

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams boxParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER);

        ProgressBar spinner = new ProgressBar(this);
        spinner.getIndeterminateDrawable().setColorFilter(
                ContextCompat.getColor(this, R.color.accent), PorterDuff.Mode.SRC_IN);
        spinner.setLayoutParams(new LinearLayout.LayoutParams(dp(48), dp(48)));
        box.addView(spinner);

        TextView tip = new TextView(this);
        tip.setText("正在连接 ZCode…");
        tip.setTextSize(14);
        tip.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
        LinearLayout.LayoutParams tipParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tipParams.topMargin = dp(16);
        box.addView(tip, tipParams);

        ((FrameLayout) loadingView).addView(box, boxParams);
        return loadingView;
    }

    private View buildHome() {
        LinearLayout home = new LinearLayout(this);
        home.setOrientation(LinearLayout.VERTICAL);
        home.setGravity(Gravity.CENTER);
        int pad = dp(32);
        home.setPadding(pad, pad, pad, pad);

        ImageView icon = new ImageView(this);
        icon.setImageResource(R.mipmap.ic_launcher);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(96), dp(96));
        iconParams.gravity = Gravity.CENTER;
        home.addView(icon, iconParams);

        home.addView(text("ZCode", 28, Typeface.BOLD, R.color.text_primary),
                matchWrap(Gravity.CENTER, 0, dp(20), 0, dp(4)));

        home.addView(text("远程控制", 15, Typeface.NORMAL, R.color.text_secondary),
                matchWrap(Gravity.CENTER, 0, 0, 0, dp(40)));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);

        Button scanButton = darkButton("扫码连接");
        scanButton.setOnClickListener(v -> {
            ScanOptions opts = new ScanOptions();
            opts.setDesiredBarcodeFormats(ScanOptions.QR_CODE);
            opts.setPrompt("对准 ZCode 桌面端弹出的二维码");
            opts.setBeepEnabled(false);
            opts.setOrientationLocked(true);
            opts.setCaptureActivity(PortraitCaptureActivity.class);
            scanLauncher.launch(opts);
        });
        row.addView(scanButton, new LinearLayout.LayoutParams(0, dp(52), 1f));

        Button pasteButton = darkButton("粘贴连接");
        pasteButton.setOnClickListener(v -> showPasteDialog());
        LinearLayout.LayoutParams pasteParams = new LinearLayout.LayoutParams(0, dp(52), 1f);
        pasteParams.leftMargin = dp(12);
        row.addView(pasteButton, pasteParams);

        home.addView(row, matchWrap(Gravity.CENTER, 0, 0, 0, 0));

        home.addView(text("在 ZCode 桌面端点击左下角电话图标生成二维码", 12, Typeface.NORMAL, R.color.text_secondary),
                matchWrap(Gravity.CENTER, 0, dp(24), 0, 0));

        return home;
    }

    private void showPasteDialog() {
        final AlertDialog[] dialogRef = new AlertDialog[1];
        final EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint("粘贴 ZCode 远程控制链接");
        input.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
        input.setHintTextColor(ContextCompat.getColor(this, R.color.text_secondary));
        input.setBackground(ContextCompat.getDrawable(this, R.drawable.input_dark));
        input.setPadding(dp(16), dp(14), dp(16), dp(14));
        ClipboardManager cm = getSystemService(ClipboardManager.class);
        if (cm != null && cm.getPrimaryClip() != null && cm.getPrimaryClip().getItemAt(0) != null
                && cm.getPrimaryClip().getItemAt(0).getText() != null) {
            input.setText(cm.getPrimaryClip().getItemAt(0).getText());
        }
        input.setSelection(input.getText().length());

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(24);
        box.setPadding(pad, pad, pad, dp(8));

        TextView title = new TextView(this);
        title.setText("连接 ZCode");
        title.setTextSize(18);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
        box.addView(title, matchWrap(Gravity.START, 0, 0, 0, dp(16)));

        box.addView(input, matchWrap(Gravity.CENTER, 0, 0, 0, dp(20)));

        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);

        Button cancel = new Button(this);
        cancel.setText("取消");
        cancel.setAllCaps(false);
        cancel.setTextSize(15);
        cancel.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
        cancel.setBackground(ContextCompat.getDrawable(this, R.drawable.btn_dark));
        cancel.setOnClickListener(v -> dialogRef[0].dismiss());
        btnRow.addView(cancel, new LinearLayout.LayoutParams(0, dp(46), 1f));

        Button ok = new Button(this);
        ok.setText("访问");
        ok.setAllCaps(false);
        ok.setTextSize(15);
        ok.setTypeface(Typeface.DEFAULT_BOLD);
        ok.setTextColor(Color.parseColor("#1E1E1E"));
        android.graphics.drawable.GradientDrawable accentBg = new android.graphics.drawable.GradientDrawable();
        accentBg.setColor(ContextCompat.getColor(this, R.color.accent));
        accentBg.setCornerRadius(dp(12));
        ok.setBackground(accentBg);
        ok.setOnClickListener(v -> {
            dialogRef[0].dismiss();
            loadUrl(input.getText().toString().trim());
        });
        LinearLayout.LayoutParams okParams = new LinearLayout.LayoutParams(0, dp(46), 1f);
        okParams.leftMargin = dp(12);
        btnRow.addView(ok, okParams);

        box.addView(btnRow, matchWrap(Gravity.CENTER, 0, 0, 0, 0));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(box)
                .create();
        dialogRef[0] = dialog;
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(R.drawable.dialog_dark);
        }
        dialog.show();
    }

    // ===== 页面状态切换 =====

    private void showOnly(View target) {
        homeView.setVisibility(target == homeView ? View.VISIBLE : View.GONE);
        loadingView.setVisibility(target == loadingView ? View.VISIBLE : View.GONE);
        webView.setVisibility(target == webView ? View.VISIBLE : View.GONE);
    }

    private void startConnect(String url) {
        connecting = true;
        mainFrameFailed = false;
        showOnly(loadingView);
        webView.loadUrl(url);
        timeoutHandler.postDelayed(() -> {
            if (connecting) {
                mainFrameFailed = true;
                finishConnect(true);
            }
        }, CONNECT_TIMEOUT_MS);
    }

    private void finishConnect(boolean failed) {
        connecting = false;
        timeoutHandler.removeCallbacksAndMessages(null);
        if (failed) {
            getSharedPreferences(PREFS, MODE_PRIVATE).edit().remove(KEY_URL).apply();
            showOnly(homeView);
            Toast.makeText(this, "无法连接 ZCode,请重新扫码或粘贴链接", Toast.LENGTH_LONG).show();
        } else {
            showOnly(webView);
        }
    }

    // ===== 通用 UI 构建 =====

    private TextView text(String s, int sp, int style, int colorRes) {
        TextView tv = new TextView(this);
        tv.setText(s);
        tv.setTextSize(sp);
        tv.setTypeface(Typeface.DEFAULT_BOLD, style);
        tv.setTextColor(ContextCompat.getColor(this, colorRes));
        return tv;
    }

    private Button darkButton(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(15);
        b.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
        b.setBackground(ContextCompat.getDrawable(this, R.drawable.btn_dark));
        return b;
    }

    private LinearLayout.LayoutParams matchWrap(int gravity, int l, int t, int r, int b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.gravity = gravity;
        p.setMargins(l, t, r, b);
        return p;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private void loadUrl(String url) {
        if (url.isEmpty()) {
            return;
        }
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "https://" + url;
        }
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(KEY_URL, url).apply();
        startConnect(url);
    }

    @Override
    public void onBackPressed() {
        if (webView.getVisibility() == View.VISIBLE && webView.canGoBack()) {
            webView.goBack();
            if (!webView.canGoBack()) {
                showOnly(homeView);
            }
        } else {
            super.onBackPressed();
        }
    }
}
