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
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.huawei.hms.ads.AdParam;
import com.huawei.hms.ads.HwAds;
import com.huawei.hms.ads.InterstitialAd;
import com.huawei.hms.ads.reward.Reward;
import com.huawei.hms.ads.reward.RewardAd;
import com.huawei.hms.ads.reward.RewardAdStatusListener;
import com.huawei.hms.iap.Iap;
import com.huawei.hms.iap.IapClient;
import com.huawei.hms.iap.entity.PurchaseIntentReq;
import com.huawei.hms.iap.entity.PurchaseIntentResult;
import com.huawei.hmf.tasks.OnFailureListener;
import com.huawei.hmf.tasks.OnSuccessListener;
import com.huawei.hmf.tasks.Task;

import java.io.OutputStream;

public class MainActivity extends AppCompatActivity {
    private WebView webView;
    private ValueCallback<Uri[]> filePathCallback;
    
    private static final String AD_REWARDED = "f95ziipjhl";
    private static final String AD_INTERSTITIAL = "w07e1f28c6";
    
    private RewardAd rewardAd;
    private InterstitialAd interstitialAd;
    private static final int REQ_CODE_BUY = 6666;

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
                saveFile(b64, name, msg);
            }

            @JavascriptInterface
            public void showRewardedAd() {
                runOnUiThread(() -> loadAndShowRewarded());
            }

            @JavascriptInterface
            public void showInterstitialAd() {
                runOnUiThread(() -> loadAndShowInterstitial());
            }

            @JavascriptInterface
            public void buyProduct(String productId) {
                runOnUiThread(() -> startPurchase(productId));
            }
        }, "Android");

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView w, ValueCallback<Uri[]> f, FileChooserParams p) {
                filePathCallback = f;
                Intent i = p.createIntent();
                i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                startActivityForResult(Intent.createChooser(i, "Select Files"), 1);
                return true;
            }
        });

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView v, String u) {
                injectJS(v);
            }
        });

        webView.loadUrl("file:///android_asset/index.html");
    }

    private void saveFile(String b64, String name, String msg) {
        try {
            if (b64.startsWith("data:")) b64 = b64.substring(b64.indexOf(",") + 1);
            byte[] bt = Base64.decode(b64, Base64.DEFAULT);
            ContentValues v = new ContentValues();
            v.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
            String mime = name.endsWith(".pdf") ? "application/pdf" : (name.endsWith(".txt") ? "text/plain" : "image/jpeg");
            v.put(MediaStore.MediaColumns.MIME_TYPE, mime);
            if (Build.VERSION.SDK_INT >= 29) v.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
            Uri u = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
            if (u != null) {
                OutputStream o = getContentResolver().openOutputStream(u);
                o.write(bt); o.close();
                runOnUiThread(() -> Toast.makeText(MainActivity.this, msg, Toast.LENGTH_SHORT).show());
            }
        } catch (Exception e) {
            runOnUiThread(() -> Toast.makeText(MainActivity.this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
        }
    }

    private void injectJS(WebView v) {
        v.loadUrl("javascript:(function() { " +
            "  window.saveAs = function(b, n) { " +
            "    var r = new FileReader(); " +
            "    r.onloadend = function() { Android.downloadFile(r.result, n, '✅ تم الحفظ بنجاح'); }; " +
            "    r.readAsDataURL(b); " +
            "  }; " +
            "  window.buyCredits = function(amount, price) { " +
            "    Android.buyProduct('credits_' + amount); " +
            "  }; " +
            "  window.openUpgradeModal = function() { " +
            "    Android.buyProduct('pro_version'); " +
            "  }; " +
            "  window.watchAd = function() { " +
            "    Android.showRewardedAd(); " +
            "  }; " +
            "  window.checkAndShowInterstitial = function() { " +
            "    Android.showInterstitialAd(); " +
            "  }; " +
            "})()");
    }

    private void loadAndShowRewarded() {
        rewardAd = new RewardAd(this, AD_REWARDED);
        rewardAd.loadAd(new AdParam.Builder().build(), new RewardAdStatusListener() {
            @Override
            public void onRewardAdLoaded() {
                rewardAd.show(MainActivity.this, new RewardAdStatusListener() {
                    @Override
                    public void onRewarded(Reward r) {
                        webView.loadUrl("javascript:grantReward();");
                    }
                });
            }
            @Override
            public void onAdFailedToLoad(int errorCode) {
                Toast.makeText(MainActivity.this, "Ad not ready (Code: " + errorCode + ")", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void loadAndShowInterstitial() {
        interstitialAd = new InterstitialAd(this);
        interstitialAd.setAdId(AD_INTERSTITIAL);
        interstitialAd.loadAd(new AdParam.Builder().build());
        interstitialAd.setAdListener(new com.huawei.hms.ads.AdListener() {
            @Override
            public void onAdLoaded() {
                if (interstitialAd.isLoaded()) interstitialAd.show(MainActivity.this);
            }
        });
    }

    private void startPurchase(String productId) {
        IapClient iapClient = Iap.getIapClient(this);
        PurchaseIntentReq req = new PurchaseIntentReq();
        req.setProductId(productId);
        req.setPriceType(0);
        
        Task<PurchaseIntentResult> task = iapClient.createPurchaseIntent(req);
        task.addOnSuccessListener(new OnSuccessListener<PurchaseIntentResult>() {
            @Override
            public void onSuccess(PurchaseIntentResult result) {
                if (result.getStatus() != null && result.getStatus().hasResolution()) {
                    try {
                        result.getStatus().startResolutionForResult(MainActivity.this, REQ_CODE_BUY);
                    } catch (IntentSender.SendIntentException e) {
                        Toast.makeText(MainActivity.this, "IAP Error", Toast.LENGTH_SHORT).show();
                    }
                }
            }
        }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(Exception e) {
                Toast.makeText(MainActivity.this, "Huawei IAP not available", Toast.LENGTH_SHORT).show();
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
        } else if (r == REQ_CODE_BUY) {
            if (c == RESULT_OK) {
                Toast.makeText(this, "✅ Purchase Successful!", Toast.LENGTH_LONG).show();
                webView.reload();
            }
        } else {
            super.onActivityResult(r, c, d);
        }
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }
}
