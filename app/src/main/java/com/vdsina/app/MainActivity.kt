package com.vdsina.app

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.vdsina.app.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var profileManager: ProfileManager
    private var autoLoginAttempted = false

    private val profilesLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            autoLoginAttempted = false
            loadCurrentProfile()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)

        profileManager = ProfileManager.getInstance(this)

        setupWebView()
        setupBackNavigation()

        if (savedInstanceState == null) {
            loadCurrentProfile()
        }
    }

    override fun onResume() {
        super.onResume()
        updateTitle()
    }

    override fun onPause() {
        super.onPause()
        val profile = profileManager.getCurrentProfile()
        profileManager.saveCookiesForCurrentProfile(profile.url)
    }

    private fun updateTitle() {
        val profile = profileManager.getCurrentProfile()
        supportActionBar?.subtitle = profile.name
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)

        binding.webView.apply {
            cookieManager.setAcceptThirdPartyCookies(this, true)

            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                @Suppress("DEPRECATION")
                databaseEnabled = true
                cacheMode = WebSettings.LOAD_DEFAULT
                setSupportZoom(true)
                builtInZoomControls = true
                displayZoomControls = false
                loadWithOverviewMode = true
                useWideViewPort = true
                mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                userAgentString = settings.userAgentString.replace("; wv", "")
            }

            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    binding.progressBar.visibility = View.VISIBLE
                    binding.progressBar.progress = 0
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    binding.progressBar.visibility = View.GONE

                    cookieManager.flush()

                    val isLoginPage = url?.contains("/login") == true
                    if (isLoginPage) {
                        autoLoginAttempted = false
                    }

                    if (!autoLoginAttempted) {
                        checkAndPerformAutoLogin(view)
                    }
                }

                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: WebResourceError?
                ) {
                    super.onReceivedError(view, request, error)
                    if (request?.isForMainFrame == true) {
                        binding.progressBar.visibility = View.GONE
                    }
                }
            }

            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    super.onProgressChanged(view, newProgress)
                    binding.progressBar.progress = newProgress
                    if (newProgress == 100) {
                        binding.progressBar.visibility = View.GONE
                    }
                }
            }
        }
    }

    private fun checkAndPerformAutoLogin(view: WebView?) {
        val profile = profileManager.getCurrentProfile()
        val login = profile.login
        val password = profile.password

        if (login.isEmpty() || password.isEmpty()) {
            return
        }

        val escapedLogin = login.replace("\\", "\\\\").replace("'", "\\'").replace("\"", "\\\"")
        val escapedPassword = password.replace("\\", "\\\\").replace("'", "\\'").replace("\"", "\\\"")

        val checkAndFillScript = """
            (function() {
                var attempts = 0;
                var maxAttempts = 20;
                
                function setNativeValue(element, value) {
                    var lastValue = element.value;
                    element.value = value;
                    var event = new Event('input', { bubbles: true });
                    var tracker = element._valueTracker;
                    if (tracker) {
                        tracker.setValue(lastValue);
                    }
                    element.dispatchEvent(event);
                    element.dispatchEvent(new Event('change', { bubbles: true }));
                }
                
                function tryLogin() {
                    var selectors = [
                        'input[type="email"]',
                        'input[name="email"]',
                        'input[name="login"]',
                        'input[name="username"]',
                        'input[autocomplete="email"]',
                        'input[autocomplete="username"]',
                        'input[placeholder*="mail"]',
                        'input[placeholder*="логин"]',
                        'input[placeholder*="Email"]'
                    ];
                    
                    var emailField = null;
                    for (var i = 0; i < selectors.length; i++) {
                        emailField = document.querySelector(selectors[i]);
                        if (emailField && emailField.offsetParent !== null) break;
                        emailField = null;
                    }
                    
                    var passwordField = document.querySelector('input[type="password"]');
                    
                    if (emailField && passwordField && emailField.offsetParent !== null) {
                        setNativeValue(emailField, '$escapedLogin');
                        setNativeValue(passwordField, '$escapedPassword');
                        
                        setTimeout(function() {
                            var form = emailField.closest('form');
                            var submitBtn = document.querySelector('button[type="submit"], input[type="submit"]');
                            if (!submitBtn && form) {
                                submitBtn = form.querySelector('button');
                            }
                            if (!submitBtn) {
                                submitBtn = document.querySelector('button.btn-primary, button.login-btn, button[class*="submit"], button[class*="login"]');
                            }
                            
                            if (submitBtn) {
                                submitBtn.click();
                            } else if (form) {
                                form.submit();
                            }
                        }, 300);
                        return true;
                    }
                    
                    attempts++;
                    if (attempts < maxAttempts) {
                        setTimeout(tryLogin, 500);
                    }
                    return false;
                }
                
                setTimeout(tryLogin, 1000);
                return 'started';
            })();
        """.trimIndent()

        view?.evaluateJavascript(checkAndFillScript) {
            autoLoginAttempted = true
        }
    }

    private fun setupBackNavigation() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.webView.canGoBack()) {
                    binding.webView.goBack()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    private fun loadCurrentProfile() {
        val profile = profileManager.getCurrentProfile()
        clearWebViewData()
        profileManager.loadCookiesForProfile(profile) {
            runOnUiThread {
                binding.webView.loadUrl(profile.url)
                updateTitle()
            }
        }
    }

    private fun clearWebViewData() {
        binding.webView.clearHistory()
        binding.webView.clearCache(true)
        android.webkit.WebStorage.getInstance().deleteAllData()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_refresh -> {
                autoLoginAttempted = false
                binding.webView.reload()
                true
            }
            R.id.action_profiles -> {
                val profile = profileManager.getCurrentProfile()
                profileManager.saveCookiesForCurrentProfile(profile.url)
                profilesLauncher.launch(Intent(this, ProfilesActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        binding.webView.saveState(outState)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        binding.webView.restoreState(savedInstanceState)
    }
}
