package com.pixelpdf.app;

import android.content.ContentValues;
import android.content.Intent;
import android.content.IntentSender;
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
import com.huawei.hms.ads.InterstitialAd;
import com.huawei.hms.ads.reward.Reward;
import com.huawei.hms.ads.reward.RewardAd;
import com.huawei.hms.ads.reward.RewardAdLoadListener;
import com.huawei.hms.ads.reward.RewardAdStatusListener;
import com.huawei.hms.iap.Iap;
import com.huawei.hms.iap.entity.PurchaseIntentReq;
import java.io.OutputStream;

public class MainActivity extends AppCompatActivity {
    private WebView webView;
    private ValueCallback<Uri[]> filePathCallback;
    private static final String AD_REWARDED = "f95ziipjhl";
    private static final String AD_INTERSTITIAL = "w07e1f28c6";
    private static final int REQ_CODE_BUY = 6666;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        HwAds.init(this);
        webView = new WebView(this);
        setContentView(webView);
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true); s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true); s.setAllowContentAccess(true);
        s.setDatabaseEnabled(true); s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);

        webView.addJavascriptInterface(new Object() {
            @JavascriptInterface
            public void downloadFile(String b64, String name, String msg) { saveFile(b64, name, msg); }
            @JavascriptInterface
            public void showRewardedAd() { runOnUiThread(() -> loadRewarded()); }
            @JavascriptInterface
            public void showInterstitialAd() { runOnUiThread(() -> loadInterstitial()); }
            @JavascriptInterface
            public void buyProduct(String pid) { runOnUiThread(() -> startPurchase(pid)); }
        }, "AndroidBridge");

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onJsAlert(WebView v, String u, String m, JsResult r) {
                new AlertDialog.Builder(MainActivity.this).setMessage(m).setPositiveButton(android.R.string.ok, (d, w) -> r.confirm()).setCancelable(false).show();
                return true;
            }
            @Override
            public boolean onShowFileChooser(WebView w, ValueCallback<Uri[]> f, FileChooserParams p) {
                filePathCallback = f; Intent i = p.createIntent(); i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                startActivityForResult(Intent.createChooser(i, "Select Files"), 1); return true;
            }
        });

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView v, String u) {
                v.loadUrl("javascript:(function() {" +
                    "window.saveAs = function(b, n) { var r = new FileReader(); r.onloadend = function() { AndroidBridge.downloadFile(r.result, n, 'Saved'); }; r.readAsDataURL(b); };" +
                    "window.watchAd = function() { AndroidBridge.showRewardedAd(); };" +
                    "window.checkAndShowInterstitial = function() { AndroidBridge.showInterstitialAd(); };" +
                    "window.buyCredits = function(a, p) { AndroidBridge.buyProduct('credits_' + a); };" +
                    "window.openUpgradeModal = function() { AndroidBridge.buyProduct('pro_version'); };" +
                    "})()");
            }
        });
        webView.loadUrl("file:///android_asset/index.html");
    }

    private void saveFile(String b64, String name, String msg) {
        try {
            if (b64.contains(",")) b64 = b64.substring(b64.indexOf(",") + 1);
            byte[] bt = Base64.decode(b64, Base64.DEFAULT);
            ContentValues v = new ContentValues(); v.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
            v.put(MediaStore.MediaColumns.MIME_TYPE, name.endsWith(".pdf") ? "application/pdf" : "image/jpeg");
            if (Build.VERSION.SDK_INT >= 29) v.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
            Uri u = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
            if (u != null) { OutputStream o = getContentResolver().openOutputStream(u); o.write(bt); o.close();
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "✅ " + msg, Toast.LENGTH_SHORT).show()); }
        } catch (Exception e) {}
    }

    private void loadRewarded() {
        RewardAd ad = new RewardAd(this, AD_REWARDED);
        ad.loadAd(new AdParam.Builder().build(), new RewardAdLoadListener() {
            public void onRewardAdLoaded() { ad.show(MainActivity.this, new RewardAdStatusListener() {
                public void onRewarded(Reward r) { webView.loadUrl("javascript:grantReward();"); }
            }); }
        });
    }

    private void loadInterstitial() {
        InterstitialAd ad = new InterstitialAd(this); ad.setAdId(AD_INTERSTITIAL);
        ad.loadAd(new AdParam.Builder().build());
        ad.setAdListener(new com.huawei.hms.ads.AdListener() {
            @Override
            public void onAdLoaded() { if (ad.isLoaded()) ad.show(MainActivity.this); }
        });
    }

    private void startPurchase(String pid) {
        Iap.getIapClient(this).createPurchaseIntent(new PurchaseIntentReq(){{setProductId(pid);setPriceType(0);}})
        .addOnSuccessListener(res -> { try { res.getStatus().startResolutionForResult(MainActivity.this, REQ_CODE_BUY); } catch (Exception e) {} })
        .addOnFailureListener(e -> Toast.makeText(this, "Huawei IAP not available", 0).show());
    }

    @Override
    protected void onActivityResult(int r, int c, Intent d) {
        if (r == 1 && filePathCallback != null) {
            Uri[] res = null; if (c == RESULT_OK && d != null) {
                if (d.getClipData() != null) { res = new Uri[d.getClipData().getItemCount()]; for (int i=0; i<d.getClipData().getItemCount(); i++) res[i] = d.getClipData().getItemAt(i).getUri(); }
                else if (d.getData() != null) res = new Uri[]{d.getData()};
            }
            filePathCallback.onReceiveValue(res); filePathCallback = null;
        } else if (r == REQ_CODE_BUY && c == RESULT_OK) { Toast.makeText(this, "✅ Success!", 0).show(); webView.reload(); }
        else super.onActivityResult(r, c, d);
    }
}
