package com.chengvar.zocode;

import android.graphics.Color;
import android.graphics.Insets;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;

import androidx.activity.result.ActivityResultLauncher;
import androidx.appcompat.app.AppCompatActivity;

import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanOptions;

public class MainActivity extends AppCompatActivity {

    private static final String PREFS = "zocode";
    private static final String KEY_URL = "url";

    private WebView webView;
    private LinearLayout urlBar;
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
        root.setBackgroundColor(Color.WHITE);
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

        urlBar = new LinearLayout(this);
        urlBar.setOrientation(LinearLayout.HORIZONTAL);
        urlBar.setPadding(16, 16, 16, 16);

        urlInput = new EditText(this);
        urlInput.setHint("粘贴或扫码 ZCode 远程控制链接");
        urlInput.setSingleLine(true);
        urlBar.addView(urlInput, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Button scanButton = new Button(this);
        scanButton.setText("扫码");
        scanButton.setOnClickListener(v -> {
            ScanOptions opts = new ScanOptions();
            opts.setDesiredBarcodeFormats(ScanOptions.QR_CODE);
            opts.setPrompt("对准 ZCode 桌面端弹出的二维码");
            opts.setBeepEnabled(false);
            opts.setOrientationLocked(true);
            scanLauncher.launch(opts);
        });
        urlBar.addView(scanButton);

        Button goButton = new Button(this);
        goButton.setText("打开");
        goButton.setOnClickListener(v -> loadUrl(urlInput.getText().toString().trim()));
        urlBar.addView(goButton);

        root.addView(urlBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        webView = new WebView(this);
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
        if (!saved.isEmpty()) {
            loadUrl(saved);
        }
    }

    private void loadUrl(String url) {
        if (url.isEmpty()) {
            return;
        }
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "https://" + url;
        }
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(KEY_URL, url).apply();
        urlBar.setVisibility(View.GONE);
        webView.setVisibility(View.VISIBLE);
        webView.loadUrl(url);
    }

    @Override
    public void onBackPressed() {
        if (webView.getVisibility() == View.VISIBLE && webView.canGoBack()) {
            webView.goBack();
            if (!webView.canGoBack()) {
                urlBar.setVisibility(View.VISIBLE);
            }
        } else {
            super.onBackPressed();
        }
    }
}
