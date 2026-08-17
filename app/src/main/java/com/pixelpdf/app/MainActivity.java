package com.pixelpdf.app;

import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.webkit.JsResult;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.huawei.hms.ads.AdParam;
import com.huawei.hms.ads.HwAds;
import com.huawei.hms.ads.reward.Reward;
import com.huawei.hms.ads.reward.RewardAd;
import com.huawei.hms.ads.reward.RewardAdLoadListener;
import com.huawei.hms.ads.reward.RewardAdStatusListener;

import java.io.OutputStream;

public class MainActivity extends AppCompatActivity {
    private WebView webView;
    private ValueCallback<Uri[]> filePathCallback;
    
    private static final String AD_REWARDED = "f95ziipjhl";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        HwAds.init(this);
        webView = new WebView(this);
        setContentView(webView);

        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setDatabaseEnabled(true);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);

        webView.addJavascriptInterface(new Object() {
            @JavascriptInterface
            public void downloadFile(String b64, String name, String msg) {
                try {
                    if (b64 == null || b64.isEmpty()) return;
                    if (b64.contains(",")) b64 = b64.substring(b64.indexOf(",") + 1);
                    byte[] bt = Base64.decode(b64, Base64.DEFAULT);
                    
                    ContentValues v = new ContentValues();
                    v.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
                    String mime = "application/pdf";
                    if (name.endsWith(".jpg") || name.endsWith(".jpeg")) mime = "image/jpeg";
                    else if (name.endsWith(".png")) mime = "image/png";
                    v.put(MediaStore.MediaColumns.MIME_TYPE, mime);
                    
                    if (Build.VERSION.SDK_INT >= 29) {
                        v.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                    }

                    Uri u = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
                    if (u != null) {
                        OutputStream o = getContentResolver().openOutputStream(u);
                        o.write(bt);
                        o.close();
                        runOnUiThread(() -> Toast.makeText(MainActivity.this, "✅ Saved to Downloads", Toast.LENGTH_LONG).show());
                    }
                } catch (Exception e) {
                    runOnUiThread(() -> Toast.makeText(MainActivity.this, "Download error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                }
            }

            @JavascriptInterface
            public void showRewardedAd() {
                runOnUiThread(() -> loadRewarded());
            }

            @JavascriptInterface
            public void buyPro() {
                runOnUiThread(() -> new AlertDialog.Builder(MainActivity.this)
                    .setTitle("PRO Version")
                    .setMessage("In-App Purchases via Huawei Store will be active soon.")
                    .setPositiveButton("OK", null)
                    .show());
            }
        }, "AndroidBridge");

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onJsAlert(WebView view, String url, String message, JsResult result) {
                new AlertDialog.Builder(MainActivity.this)
                    .setMessage(message)
                    .setPositiveButton(android.R.string.ok, (dialog, which) -> result.confirm())
                    .setCancelable(false)
                    .create()
                    .show();
                return true;
            }

            @Override
            public boolean onShowFileChooser(WebView w, ValueCallback<Uri[]> f, FileChooserParams p) {
                filePathCallback = f;
                Intent i = p.createIntent();
                i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                startActivityForResult(Intent.createChooser(i, "Select Files"), 1);
                return true;
            }
        });

        webView.setWebViewClient(new WebViewClient());
        webView.loadUrl("file:///android_asset/index.html");
    }

    private void loadRewarded() {
        Toast.makeText(this, "Loading Huawei Ad...", Toast.LENGTH_SHORT).show();
        RewardAd ad = new RewardAd(this, AD_REWARDED);
        ad.loadAd(new AdParam.Builder().build(), new RewardAdLoadListener() {
            public void onRewardAdLoaded() {
                ad.show(MainActivity.this, new RewardAdStatusListener() {
                    public void onRewardAdOpened() {}
                    public void onRewardAdFailedToShow(int errorCode) {
                        Toast.makeText(MainActivity.this, "Failed to display ad.", Toast.LENGTH_SHORT).show();
                    }
                    public void onRewardAdClosed() {}
                    public void onRewarded(Reward r) {
                        webView.loadUrl("javascript:if(window.onAdRewarded) window.onAdRewarded();");
                    }
                });
            }
            public void onRewardAdFailedToLoad(int errorCode) {
                Toast.makeText(MainActivity.this, "No Ads available right now (Code: " + errorCode + ")", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    protected void onActivityResult(int r, int c, Intent d) {
        if (r == 1 && filePathCallback != null) {
            Uri[] res = null;
            if (c == RESULT_OK && d != null) {
                if (d.getClipData() != null) {
                    res = new Uri[d.getClipData().getItemCount()];
                    for (int i=0; i<d.getClipData().getItemCount(); i++) res[i] = d.getClipData().getItemAt(i).getUri();
                } else if (d.getData() != null) res = new Uri[]{d.getData()};
            }
            filePathCallback.onReceiveValue(res);
            filePathCallback = null;
        } else {
            super.onActivityResult(r, c, d);
        }
    }
}
