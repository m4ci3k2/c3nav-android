package de.c3nav.droid;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    public static final int PERM_REQUEST = 1;
    private WifiManager wifiManager;
    private WebView webView;
    private MobileClient mobileClient;
    private Map<String, Integer> lastLevelValues = new HashMap<>();
    private boolean permAsked = false;
    private WifiReceiver wifiReceiver;
    protected CustomSwipeToRefresh swipeLayout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mobileClient = new MobileClient();

        webView = (WebView) findViewById(R.id.webView);

        webView.getSettings().setSupportZoom(false);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setUserAgentString("c3navClient/Android/" + BuildConfig.VERSION_CODE);
        webView.addJavascriptInterface(mobileClient, "mobileclient");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                swipeLayout.setRefreshing(false);
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                swipeLayout.setRefreshing(true);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                Uri u = Uri.parse(url);
                if (Uri.parse(BuildConfig.WEB_URL).getHost().equals(u.getHost()) {
                    if (u.getPathSegments().get(0) != "api") {
                        return false;
                    }
                }
                Intent intent = new Intent(Intent.ACTION_VIEW, u);
                startActivity(intent);
                return true;
            }
        });

        String url_to_call = BuildConfig.WEB_URL;
        Intent intent = getIntent();
        Uri data = intent.getData();
        if (data != null) {
            Uri.Builder tmp_uri = data.buildUpon();
            tmp_uri.scheme("https");
            url_to_call = tmp_uri.build().toString();
        }

        webView.loadUrl(url_to_call);

        wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        wifiReceiver = new WifiReceiver();

        swipeLayout = (CustomSwipeToRefresh) findViewById(R.id.swipe_container);
        swipeLayout.setColorSchemeResources(R.color.colorPrimary);
        swipeLayout.setEnabled(true);
    }

    @Override
    protected void onPostResume() {
        super.onPostResume();
        wifiManager.startScan();
        registerReceiver(wifiReceiver, new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));
    }

    @Override
    protected void onPause() {
        unregisterReceiver(wifiReceiver);
        super.onPause();
    }

    class MobileClient {
        private JSONArray nearbyStations;

        @JavascriptInterface
        public String getNearbyStations() {
            if (this.nearbyStations != null) {
                return this.nearbyStations.toString();
            } else {
                return "[]";
            }
        }

        public void setNearbyStations(JSONArray nearbyStations) {
            this.nearbyStations = nearbyStations;
        }

        @JavascriptInterface
        public int getAppVersionCode() {
            return BuildConfig.VERSION_CODE;
        }

        @JavascriptInterface
        public void scanNow() {
            wifiManager.startScan();
        }

        @JavascriptInterface
        public void shareUrl(String url) {
            Intent i = new Intent(Intent.ACTION_SEND);
            i.setType("text/plain");
            i.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.app_name));
            i.putExtra(Intent.EXTRA_TEXT, url);
            startActivity(Intent.createChooser(i, getString(R.string.share)));
        }

        @JavascriptInterface
        public void createShortcut(String url, String title) {
            Intent shortcutIntent = new Intent(getApplicationContext(),
                    MainActivity.class);
            shortcutIntent.setAction(Intent.ACTION_MAIN);
            shortcutIntent.setData(Uri.parse(url));

            Intent addIntent = new Intent();
            addIntent.putExtra(Intent.EXTRA_SHORTCUT_INTENT, shortcutIntent);
            addIntent.putExtra(Intent.EXTRA_SHORTCUT_NAME, title);
            addIntent.putExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE,
                    Intent.ShortcutIconResource.fromContext(getApplicationContext(),
                            R.mipmap.ic_launcher));
            addIntent.setAction("com.android.launcher.action.INSTALL_SHORTCUT");
            getApplicationContext().sendBroadcast(addIntent);

            Toast.makeText(MainActivity.this, R.string.shortcut_created, Toast.LENGTH_SHORT).show();
        }
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.share:
                Intent i = new Intent(Intent.ACTION_SEND);
                i.setType("text/plain");
                i.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.app_name));
                i.putExtra(Intent.EXTRA_TEXT, webView.getUrl());
                startActivity(Intent.createChooser(i, getString(R.string.share)));
                return true;
            case R.id.refresh:
                webView.loadUrl(BuildConfig.WEB_URL);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    class WifiReceiver extends BroadcastReceiver {
        public void onReceive(Context c, Intent intent) {
            webView.post(new Runnable() {
                @Override
                public void run() {
                    int permissionCheck = ContextCompat.checkSelfPermission(MainActivity.this,
                            Manifest.permission.ACCESS_FINE_LOCATION);
                    if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
                        if (!permAsked) {
                            ActivityCompat.requestPermissions(MainActivity.this,
                                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                                    PERM_REQUEST);
                            permAsked = true;
                        }
                        return;
                    }
                    List<ScanResult> wifiList = wifiManager.getScanResults();
                    JSONArray ja = new JSONArray();
                    Map<String, Integer> newLevelValues = new HashMap<String, Integer>();
                    for (ScanResult result : wifiList) {
                        JSONObject jo = new JSONObject();
                        try {
                            jo.put("bssid", result.BSSID);
                            jo.put("ssid", result.SSID);
                            jo.put("level", result.level);
                            jo.put("frequency", result.frequency);

                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                                if (SystemClock.elapsedRealtime() - result.timestamp / 1000 > 1000) {
                                    continue;
                                }
                                jo.put("last", SystemClock.elapsedRealtime() - result.timestamp / 1000);
                            } else {
                                // Workaround for older devices: If the signal level did not change
                                // at all since the last scan, we will assume that it is a cached
                                // value and should not be used.
                                newLevelValues.put(result.BSSID, result.level);
                                if (lastLevelValues.containsKey(result.BSSID) && lastLevelValues.get(result.BSSID) == result.level) {
                                    Log.d("scan result", "Discard " + result.BSSID + " because level did not change");
                                    continue;
                                }
                            }
                            ja.put(jo);
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }
                    Log.d("scan result", ja.toString());
                    mobileClient.setNearbyStations(ja);
                    lastLevelValues = newLevelValues;
                    webView.loadUrl("javascript:nearby_stations_available();");
                }
            });
        }
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            finish();
        }
    }

    @Override
    public void onAttachedToWindow() {
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
    }
}
