package com.chengvar.zocode

import android.content.ClipboardManager
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

class MainActivity : AppCompatActivity() {

    private companion object {
        const val PREFS = "zocode"
        const val KEY_URL = "url"
        const val CONNECT_TIMEOUT_MS = 10_000L
        val BG = Color.parseColor("#1E1E1E")

        // 记录 SPA 路由栈 + 合成后退;顺带隐藏页面自绘滚动条
        val ROUTE_HOOK = """
            (function(){
              if(window.__zcInstalled) return;
              window.__zcInstalled=true;
              var s=document.createElement('style');
              s.textContent='::-webkit-scrollbar{width:0!important;height:0!important;display:none!important}';
              document.head.appendChild(s);
              window.__zcRoutes=[location.href];
              var push=history.pushState.bind(history), rep=history.replaceState.bind(history);
              function record(){
                var h=location.href;
                if(window.__zcRoutes[window.__zcRoutes.length-1]!==h) window.__zcRoutes.push(h);
              }
              function wrap(orig){return function(){var r=orig.apply(null,arguments);record();return r;};}
              history.pushState=wrap(push); history.replaceState=wrap(rep);
              window.addEventListener('hashchange',record,true);
              window.__zcBack=function(){
                if(window.__zcRoutes.length<2) return false;
                window.__zcRoutes.pop();
                push(null,'',window.__zcRoutes[window.__zcRoutes.length-1]);
                window.dispatchEvent(new PopStateEvent('popstate'));
                return true;
              };
            })()
        """.trimIndent()
    }

    private lateinit var webView: WebView
    private lateinit var homeView: View
    private lateinit var loadingView: View
    private lateinit var scrimView: View

    private val prefs by lazy { getSharedPreferences(PREFS, MODE_PRIVATE) }
    private val mainHandler = Handler(Looper.getMainLooper())

    private var mainFrameFailed = false
    private var connecting = false

    private val scanLauncher = registerForActivityResult(ScanContract()) { result ->
        result.contents?.takeIf { it.isNotEmpty() }?.let(::loadUrl)
    }

    private var filePathCallback: android.webkit.ValueCallback<Array<android.net.Uri>>? = null

    private val fileChooserLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            filePathCallback?.onReceiveValue(
                WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data))
            filePathCallback = null
        }

    private val webBackCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            when {
                webView.canGoBack() -> webView.goBack()
                else -> webView.evaluateJavascript("window.__zcBack ? __zcBack() : false") { handled ->
                    if (handled != "true") showOnly(homeView)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        captureCrash()
        enableEdgeToEdge()

        val root = FrameLayout(this).apply {
            setBackgroundColor(BG)
            setContentView(this)
        }

        // 沉浸式:系统栏透明,内容整体避让,状态栏区域就是背景色,无割裂
        root.setOnApplyWindowInsetsListener { v, insets ->
            val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            v.setPadding(0, bars.top, 0, bars.bottom)
            scrimView.layoutParams.height = 0
            scrimView.requestLayout()
            insets
        }

        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            root.addView(this, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        }

        homeView = buildHome().also {
            column.addView(it, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
        }
        loadingView = buildLoading().also {
            column.addView(it, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
        }

        webView = WebView(this).apply {
            setBackgroundColor(BG)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.setAlgorithmicDarkeningAllowed(true) // 网页跟随深色
            isVerticalScrollBarEnabled = false
            isHorizontalScrollBarEnabled = false
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) = false
                override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                    if (!request.isForMainFrame) return
                    mainFrameFailed = true
                    // 连接后桌面端关掉:自动回主页让用户重新扫码/粘贴
                    if (!connecting) {
                        prefs.edit().remove(KEY_URL).apply()
                        runOnUiThread {
                            showOnly(homeView)
                            Toast.makeText(this@MainActivity, "连接已断开,请重新扫码或粘贴链接", Toast.LENGTH_LONG).show()
                        }
                    }
                }
                override fun onPageFinished(view: WebView, url: String) {
                    view.evaluateJavascript(ROUTE_HOOK, null)
                    if (connecting) finishConnect(failed = mainFrameFailed)
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onShowFileChooser(
                    webView: WebView,
                    callback: android.webkit.ValueCallback<Array<android.net.Uri>>,
                    params: FileChooserParams
                ): Boolean {
                    filePathCallback?.onReceiveValue(null)
                    filePathCallback = callback
                    fileChooserLauncher.launch(params.createIntent())
                    return true
                }
            }
            visibility = View.GONE
            column.addView(this, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
        }

        // 顶部玻璃遮罩
        scrimView = View(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(0xCC1E1E1E.toInt(), 0x661E1E1E.toInt(), 0x001E1E1E.toInt())
            )
            root.addView(this, FrameLayout.LayoutParams(MATCH_PARENT, 0, Gravity.TOP))
        }

        prefs.getString(KEY_URL, "")?.takeIf { it.isNotEmpty() }?.let(::startConnect)

        onBackPressedDispatcher.addCallback(this, webBackCallback)
    }

    private fun captureCrash() {
        val file = java.io.File(filesDir, "crash.txt")
        Thread.getDefaultUncaughtExceptionHandler()?.let { default ->
            Thread.setDefaultUncaughtExceptionHandler { t, e ->
                try {
                    file.writeText(android.util.Log.getStackTraceString(e))
                } catch (_: Exception) {}
                default.uncaughtException(t, e)
            }
        }
        if (file.exists()) {
            Toast.makeText(this, file.readText().lineSequence().firstOrNull() ?: "上次崩溃", Toast.LENGTH_LONG).show()
            file.delete()
        }
    }

    private fun buildHome(): View {
        val home = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(32), dp(32), dp(32), dp(32))
        }

        home.addView(ImageView(this).apply { setImageResource(R.mipmap.ic_launcher) },
            LinearLayout.LayoutParams(dp(96), dp(96)).apply { gravity = Gravity.CENTER })
        home.addView(textLabel("ZCode", 28f, bold = true),
            matchWrap(top = dp(20), bottom = dp(4)))
        home.addView(textLabel("远程控制", 15f, colorRes = R.color.text_secondary),
            matchWrap(bottom = dp(40)))

        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(roundButton("扫码连接") { launchScanner() },
            LinearLayout.LayoutParams(0, dp(52), 1f))
        row.addView(roundButton("粘贴连接") { showPasteDialog() },
            LinearLayout.LayoutParams(0, dp(52), 1f).apply { leftMargin = dp(12) })
        home.addView(row, matchWrap())

        home.addView(textLabel("在 ZCode 桌面端点击左下角电话图标生成二维码", 12f, R.color.text_secondary),
            matchWrap(top = dp(24)))
        return home
    }

    private fun launchScanner() {
        scanLauncher.launch(ScanOptions().apply {
            setDesiredBarcodeFormats(ScanOptions.QR_CODE)
            setPrompt("对准 ZCode 桌面端弹出的二维码")
            setBeepEnabled(false)
            setOrientationLocked(true)
            setCaptureActivity(PortraitCaptureActivity::class.java)
        })
    }

    private fun buildLoading(): View {
        val frame = FrameLayout(this).apply {
            setBackgroundColor(BG)
            visibility = View.GONE
        }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }
        box.addView(ProgressBar(this).apply {
            indeterminateDrawable.setColorFilter(
                ContextCompat.getColor(context, R.color.accent), PorterDuff.Mode.SRC_IN)
        }, LinearLayout.LayoutParams(dp(48), dp(48)))
        box.addView(textLabel("正在连接 ZCode…", 14f, R.color.text_secondary).apply {
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { topMargin = dp(16) })
        frame.addView(box, FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, Gravity.CENTER))
        loadingView = frame
        return frame
    }

    private fun showPasteDialog() {
        val input = EditText(this).apply {
            setSingleLine(true)
            hint = "粘贴 ZCode 远程控制链接"
            setTextColor(ContextCompat.getColor(context, R.color.text_primary))
            setHintTextColor(ContextCompat.getColor(context, R.color.text_secondary))
            background = ContextCompat.getDrawable(context, R.drawable.input_dark)
            setPadding(dp(16), dp(14), dp(16), dp(14))
            val clip = getSystemService(ClipboardManager::class.java)?.primaryClip?.getItemAt(0)?.text
            setText(clip)
            setSelection(text.length)
        }

        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = dp(24)
            setPadding(pad, pad, pad, dp(8))
        }
        box.addView(textLabel("连接 ZCode", 18f, bold = true),
            matchWrap(Gravity.START, bottom = dp(16)))
        box.addView(input, matchWrap(bottom = dp(20)))

        val btnRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val dialogRef = arrayOfNulls<AlertDialog>(1)

        btnRow.addView(roundButton("取消", secondary = true) { dialogRef[0]?.dismiss() },
            LinearLayout.LayoutParams(0, dp(46), 1f))
        btnRow.addView(Button(this).apply {
            text = "访问"
            isAllCaps = false
            textSize = 15f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.parseColor("#1E1E1E"))
            background = GradientDrawable().apply {
                color = android.content.res.ColorStateList.valueOf(
                    ContextCompat.getColor(context, R.color.accent))
                cornerRadius = dp(12).toFloat()
            }
            setOnClickListener {
                dialogRef[0]?.dismiss()
                loadUrl(input.text.toString().trim())
            }
        }, LinearLayout.LayoutParams(0, dp(46), 1f).apply { leftMargin = dp(12) })
        box.addView(btnRow, matchWrap())

        val dialog = AlertDialog.Builder(this).setView(box).create().apply {
            window?.setBackgroundDrawableResource(R.drawable.dialog_dark)
            window?.let { w ->
                try {
                    w.setBackgroundBlurRadius(60) // 真·毛玻璃,部分机型不支持
                } catch (e: Exception) {
                    android.util.Log.e("zcode", "blur unsupported", e)
                }
            }
        }
        dialogRef[0] = dialog
        dialog.show()
    }

    private fun roundButton(label: String, secondary: Boolean = false, onClick: () -> Unit): Button =
        Button(this).apply {
            text = label
            isAllCaps = false
            textSize = 15f
            setTextColor(ContextCompat.getColor(context,
                if (secondary) R.color.text_secondary else R.color.text_primary))
            background = ContextCompat.getDrawable(context, R.drawable.btn_dark)
            setOnClickListener { onClick() }
        }

    private fun textLabel(s: String, sp: Float, colorRes: Int = R.color.text_primary, bold: Boolean = false): TextView =
        TextView(this).apply {
            text = s
            textSize = sp
            if (bold) setTypeface(typeface, Typeface.BOLD)
            setTextColor(ContextCompat.getColor(context, colorRes))
        }

    private fun matchWrap(gravity: Int = Gravity.CENTER,
                          left: Int = 0, top: Int = 0, right: Int = 0, bottom: Int = 0) =
        LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
            this.gravity = gravity
            setMargins(left, top, right, bottom)
        }

    private fun dp(v: Int): Int = Math.round(v * resources.displayMetrics.density)

    private fun showOnly(target: View) {
        homeView.visibility = if (target == homeView) View.VISIBLE else View.GONE
        loadingView.visibility = if (target == loadingView) View.VISIBLE else View.GONE
        webView.visibility = if (target == webView) View.VISIBLE else View.GONE
        webBackCallback.isEnabled = target == webView
    }

    private fun startConnect(url: String) {
        connecting = true
        mainFrameFailed = false
        showOnly(loadingView)
        webView.loadUrl(url)
        mainHandler.postDelayed({
            if (connecting) {
                mainFrameFailed = true
                finishConnect(failed = true)
            }
        }, CONNECT_TIMEOUT_MS)
    }

    private fun finishConnect(failed: Boolean) {
        connecting = false
        mainHandler.removeCallbacksAndMessages(null)
        if (failed) {
            prefs.edit().remove(KEY_URL).apply()
            showOnly(homeView)
            Toast.makeText(this, "无法连接 ZCode,请重新扫码或粘贴链接", Toast.LENGTH_LONG).show()
        } else {
            showOnly(webView)
        }
    }

    private fun loadUrl(url: String) {
        if (url.isEmpty()) return
        val normalized = if (url.startsWith("http")) url else "https://$url"
        prefs.edit().putString(KEY_URL, normalized).apply()
        startConnect(normalized)
    }
}
