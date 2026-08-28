package com.chengvar.zocode;

import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanOptions;

public class MainActivity extends AppCompatActivity {

    private static final String PREFS = "zocode";
    private static final String KEY_URL = "url";

    private WebView webView;
    private LinearLayout inputRow;
    private View homeView;
    private EditText urlInput;

    private final ActivityResultLauncher<ScanOptions> scanLauncher =
            registerForActivityResult(new ScanContract(), result -> {
                String text = result.getContents();
                if (text != null && !text.isEmpty()) {
                    urlInput.setText(text);
                    loadUrl(text);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#1E1E1E"));
        setContentView(root);

        // 安全区:内容避开状态栏/导航栏/刘海
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            Insets bars;
            if (Build.VERSION.SDK_INT >= 30) {
                bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            } else {
                bars = Insets.of(0, insets.getSystemWindowInsetTop(), 0, insets.getSystemWindowInsetBottom());
            }
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        // ===== 主页:居中品牌区 + 连接区 =====
        homeView = buildHome();
        root.addView(homeView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        // ===== WebView =====
        webView = new WebView(this);
        webView.setBackgroundColor(Color.parseColor("#1E1E1E"));
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return false; // 所有导航留在 app 内
            }
        });
        webView.setWebChromeClient(new WebChromeClient());
        webView.setVisibility(View.GONE);
        root.addView(webView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        String saved = getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY_URL, "");
        urlInput.setText(saved);
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

        TextView subtitle = text("远程控制", 15, Typeface.NORMAL, R.color.text_secondary);
        home.addView(subtitle, matchWrap(Gravity.CENTER, 0, 0, 0, dp(40)));

        // 输入框
        urlInput = new EditText(this);
        urlInput.setHint("粘贴或扫码 ZCode 远程控制链接");
        urlInput.setSingleLine(true);
        urlInput.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
        urlInput.setHintTextColor(ContextCompat.getColor(this, R.color.text_secondary));
        urlInput.setBackground(ContextCompat.getDrawable(this, R.drawable.input_dark));
        urlInput.setPadding(dp(16), dp(14), dp(16), dp(14));
        home.addView(urlInput, matchWrap(Gravity.CENTER, 0, 0, 0, dp(16)));

        // 按钮行:扫码 + 打开 各占一半
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);

        Button scanButton = darkButton("扫码连接");
        scanButton.setOnClickListener(v -> {
            ScanOptions opts = new ScanOptions();
            opts.setDesiredBarcodeFormats(ScanOptions.QR_CODE);
            opts.setPrompt("对准 ZCode 桌面端弹出的二维码");
            opts.setBeepEnabled(false);
            opts.setCaptureActivity(PortraitCaptureActivity.class);
            opts.setOrientationLocked(true);
            scanLauncher.launch(opts);
        });
        row.addView(scanButton, new LinearLayout.LayoutParams(0, dp(52), 1f));

        Button goButton = darkButton("打开");
        goButton.setOnClickListener(v -> loadUrl(urlInput.getText().toString().trim()));
        LinearLayout.LayoutParams goParams = new LinearLayout.LayoutParams(0, dp(52), 1f);
        goParams.leftMargin = dp(12);
        row.addView(goButton, goParams);

        home.addView(row, matchWrap(Gravity.CENTER, 0, 0, 0, 0));

        TextView tip = text("在 ZCode 桌面端点击左下角电话图标生成二维码", 12, Typeface.NORMAL, R.color.text_secondary);
        home.addView(tip, matchWrap(Gravity.CENTER, 0, dp(24), 0, 0));

        return home;
    }

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
        homeView.setVisibility(View.GONE);
        webView.setVisibility(View.VISIBLE);
        webView.loadUrl(url);
    }

    @Override
    public void onBackPressed() {
        if (webView.getVisibility() == View.VISIBLE && webView.canGoBack()) {
            webView.goBack();
            if (!webView.canGoBack()) {
                webView.setVisibility(View.GONE);
                homeView.setVisibility(View.VISIBLE);
            }
        } else {
            super.onBackPressed();
        }
    }
}
