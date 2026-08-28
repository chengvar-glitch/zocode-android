package com.chengvar.zocode;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;

public class MainActivity extends Activity {

    private static final String PREFS = "zocode";
    private static final String KEY_URL = "url";

    private WebView webView;
    private LinearLayout urlBar;
    private EditText urlInput;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);

        urlBar = new LinearLayout(this);
        urlBar.setOrientation(LinearLayout.HORIZONTAL);
        urlBar.setPadding(16, 16, 16, 16);
        urlBar.setBackgroundColor(Color.WHITE);

        urlInput = new EditText(this);
        urlInput.setHint("粘贴 ZCode 远程控制链接");
        urlInput.setSingleLine(true);
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        urlBar.addView(urlInput, inputParams);

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

        setContentView(root);

        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        String saved = prefs.getString(KEY_URL, "");
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
