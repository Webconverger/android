package com.webconverger.KioskApp;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ImageView;
import android.widget.Toast;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;


public class MainActivity extends Activity {


    private static final String TAG = "WEBC";
    private final String HOMEPAGE = "homepage";
    private WebView mWebView;
    private ImageView mloadingView;
    private String ID;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        // Not sure about this
        super.onCreate(savedInstanceState);

        WifiManager manager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        WifiInfo info = manager.getConnectionInfo();
        String macAddress = info.getMacAddress();
        ID = Build.SERIAL + ';' + macAddress;
        Log.d(TAG, "ID is: " + ID);

        // Keep screen on
        // Assuming deployment will be on a mounted Android device with power
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // Setup layout
        setContentView(R.layout.activity_main);

        mloadingView = (ImageView) findViewById(R.id.loading);

        mWebView = (WebView) findViewById(R.id.kiosk_webview);

        WebSettings webSettings = mWebView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        // Make links clickable
        mWebView.setWebViewClient(new WebViewClient());

        if (savedInstanceState != null) {
            mWebView.restoreState(savedInstanceState.getBundle("webviewstate"));
            final int state = savedInstanceState.getInt("appstate");

            if (state == 0) {
                mloadingView.setVisibility(View.GONE);
                mWebView.setVisibility(View.VISIBLE);
            } else {
                mloadingView.setVisibility(View.VISIBLE);
                mWebView.setVisibility(View.INVISIBLE);
            }

        } else {
            new ConfigParser().execute();
        }


        // Flash id
        Toast.makeText(this, ID, Toast.LENGTH_LONG).show();

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu items for use in the action bar
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_main, menu);
        return super.onCreateOptionsMenu(menu);


    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_reset:
                Log.d(TAG, "Action reset");
                new ConfigParser().execute();
                return true;
            case R.id.action_unlock:
                Log.d(TAG, "Action unlock");
                ActivityManager activityManager = (ActivityManager) this.getSystemService(Context.ACTIVITY_SERVICE);
                if (activityManager.isInLockTaskMode()) {
                    stopLockTask();
                }
                return true;
            default:
                return true;
        }

    }

    @Override
    public void onBackPressed() {
        if (mWebView.canGoBack() == true) {
            mWebView.goBack();
        } else {
            MainActivity.super.onBackPressed();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        Bundle tempSaveStat = new Bundle();
        mWebView.saveState(tempSaveStat);
        outState.putBundle("webviewstate", tempSaveStat);
        outState.putInt("appstate", mloadingView.getVisibility() == View.VISIBLE ? 1 : 0);
    }

    @Override
    protected void onResume() {

        super.onResume();


        //log.d(TAG, activityManager.isLockTaskPermitted("com.webconverger.myfirstapp");

        DevicePolicyManager mDPM = null;
        mDPM = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
        // mDeviceAdminSample = new ComponentName(this, DeviceAdminSample.class);

        if (mDPM.isDeviceOwnerApp(this.getPackageName())) {
            Log.d(TAG, "isDeviceOwnerApp: YES");
            ComponentName componentName = BasicDeviceAdminReceiver.getComponentName(this);
            Log.d(TAG, this.getPackageName());
            // TODO: Catch security exception if device Administrator not toggled
            mDPM.setLockTaskPackages(componentName, new String[]{this.getPackageName()});
        } else {
            Log.d(TAG, "isDeviceOwnerApp: NO");
        }

        if (mDPM.isLockTaskPermitted(this.getPackageName())) {
            Log.d(TAG, "isLockTaskPermitted: ALLOWED");
        } else {
            Log.d(TAG, "isLockTaskPermitted: NOT ALLOWED");
        }

        ActivityManager activityManager = (ActivityManager) this.getSystemService(Context.ACTIVITY_SERVICE);
        if (!activityManager.isInLockTaskMode()) {
            startLockTask();
        }


    }

    private class ConfigParser extends AsyncTask<Void, Void, URL> {

        @Override
        protected URL doInBackground(Void... params) {

            URL homePageUrl = null;

            HttpURLConnection urlConnection = null;

            try {
                final URL configUrl = new URL("https://config.webconverger.com/clients/install-config/" + ID);
                urlConnection = (HttpURLConnection) configUrl.openConnection();
                InputStream in = new BufferedInputStream(urlConnection.getInputStream());
                homePageUrl = parseINI(in);
            } catch (IOException ignore) {
                // TODO: retry?
                ignore.printStackTrace();
            } finally {
                urlConnection.disconnect();
            }
            return homePageUrl;
        }

        private URL parseINI(InputStream in) {
            InputStreamReader reader;
            try {
                reader = new InputStreamReader(in, "UTF-8");
            } catch (UnsupportedEncodingException e1) {
                e1.printStackTrace();
                return null;
            }
            char[] buffer = new char[255];
            StringBuilder builder = new StringBuilder(255);
            URL result = null;
            try {
                int read = 0;
                while ((read = reader.read(buffer)) != -1) {
                    builder.append(buffer, 0, read);
                }
            } catch (IOException ioe) {
            } finally {
                try {
                    reader.close();
                } catch (IOException e) {
                }
            }
            String config = builder.toString();
            // Log.d(TAG, config);
            String[] lines = config.split("\n");
            for (String line : lines) {
                // Log.d(TAG, line);
                if (line.startsWith(HOMEPAGE)) {
                    String[] fields = line.split("=");
                    try {
                        result = new URL(fields[1]);
                    } catch (MalformedURLException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                        result = null;
                    }
                }
            }
            return result;
        }

        @Override
        protected void onPostExecute(URL result) {
            if (result == null) {
                try {
                    result = new URL("https://config.webconverger.com/clients/?id=" + ID);
                } catch (MalformedURLException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }

            if (mloadingView == null || mWebView == null) {
                return;
            }

            mloadingView.setVisibility(View.GONE);

            // TODO: Ensure Webview reset to a clean slate
            // Work out other possible "fingerprinting" to be avoided
            mWebView.clearCache(true);
            mWebView.clearHistory();
            mWebView.clearFormData();

            // http://developer.android.com/reference/android/webkit/CookieManager.html
            CookieManager cm = CookieManager.getInstance();
            cm.removeAllCookies(null);
            // The slate must be clean

            mWebView.setVisibility(View.VISIBLE);
            // Log.d(TAG, result.toString());
            mWebView.loadUrl(result.toString());
        }
    }


}
