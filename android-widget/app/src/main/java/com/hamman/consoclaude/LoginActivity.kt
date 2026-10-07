package com.hamman.consoclaude

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast

/** Connexion à claude.ai dans un navigateur intégré, puis récupération du cookie de session. */
class LoginActivity : Activity() {

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val webView = WebView(this)
        setContentView(webView)
        CookieManager.getInstance().setAcceptCookie(true)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                val cookies = CookieManager.getInstance().getCookie("https://claude.ai") ?: return
                if ("sessionKey=" in cookies) {
                    UsageStore(this@LoginActivity).cookie = cookies
                    Toast.makeText(this@LoginActivity, "Connecté", Toast.LENGTH_SHORT).show()
                    setResult(RESULT_OK)
                    finish()
                }
            }
        }
        webView.loadUrl("https://claude.ai/login")
    }
}
