package com.webconverger.KioskApp;

import android.app.ActionBar;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
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
import java.util.Timer;


public class MainActivity extends Activity {


    private static final String TAG = "WEBC";
    private WebView mWebView;
    private ImageView mloadingView;
    private String ID;
    private Timer t;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        // t = new Timer();


        try {
            PackageInfo pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            getActionBar().setTitle(getResources().getString(R.string.app_name) + " " + pInfo.versionName);
        } catch (Exception e) {

        }

        // Try avoid initial lock screen
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);

        // Get the Mac address of the wifi to build up machine identity
        WifiManager manager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        WifiInfo info = manager.getConnectionInfo();
        String macAddress = info.getMacAddress();
        ID = Build.SERIAL + ';' + macAddress;
        Log.d(TAG, "ID is: " + ID);

        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);

        // Setup layout
        setContentView(R.layout.activity_main);

        mloadingView = (ImageView) findViewById(R.id.loading);

        mWebView = (WebView) findViewById(R.id.kiosk_webview);

        // Show action bar when interacted with, in order to expose reset button
        mWebView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                ActionBar actionBar = getActionBar();

                if (event.getAction() == MotionEvent.ACTION_MOVE) {
                    // TODO: Reset timer
                    if (!actionBar.isShowing()) {
                        actionBar.show();
                    }
                }
                return false;
            }
        });

        WebSettings webSettings = mWebView.getSettings();

        // http://stackoverflow.com/a/14062315/4534
        webSettings.setSaveFormData(false);

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

        ActionBar actionBar = getActionBar();
        actionBar.hide();

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
            default:
                return true;
        }

    }

    @Override
    public void onBackPressed() {
        try {
            if (mWebView.canGoBack() == true) {
                mWebView.goBack();
            } else {
                MainActivity.super.onBackPressed();
            }
        }  catch (Exception e) {    }
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

    private class ConfigParser extends AsyncTask<Void, Void, Bundle> {

        @Override
        protected Bundle doInBackground(Void... params) {

            URL homePageUrl = null;

            HttpURLConnection urlConnection = null;

            try {
                final URL configUrl = new URL("https://config.webconverger.com/clients/install-config/" + ID);
                urlConnection = (HttpURLConnection) configUrl.openConnection();
                InputStream in = new BufferedInputStream(urlConnection.getInputStream());
                return parseINI(in);
            } catch (IOException ignore) {
                // TODO: retry?
                ignore.printStackTrace();
            } finally {
                urlConnection.disconnect();
            }
            return null;
        }

        private Bundle parseINI(InputStream in) {

            Bundle bundle = new Bundle();

            InputStreamReader reader;
            try {
                reader = new InputStreamReader(in, "UTF-8");
            } catch (UnsupportedEncodingException e1) {
                e1.printStackTrace();
                return bundle;
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
                Log.d(TAG, line);
                if (line.startsWith("homepage")) {
                    Log.d(TAG, "Parsing homepage");
                    String[] fields = line.split("=");
                    try {
                        result = new URL(fields[1]);
                        if (result != null) {
                            bundle.putString("homepage", result.toString());
                        }
                    } catch (MalformedURLException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                }
                if (line.startsWith("noblank")) {
                    bundle.putBoolean("noblank", true);

                }
                if (line.startsWith("unlock")) {
                    bundle.putBoolean("unlock", true);

                }

            }
            Log.d(TAG, "Returning bundle................");
            return bundle;
        }

        @Override
        protected void onPostExecute(Bundle bundle) {

            String homepage = null;

            if (bundle != null) {

                if (bundle.containsKey("unlock")) {
                    Log.d(TAG, "Has the key");
                    if (bundle.getBoolean("unlock")) {
                        Log.d(TAG, "Trying to unlock");
                        ActivityManager activityManager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
                        if (activityManager.isInLockTaskMode()) {
                            Log.d(TAG, "Now unlocking....");
                            stopLockTask();
                        }
                    }
                }

                if (bundle.containsKey("noblank") && bundle.getBoolean("noblank", false)) {
                    Log.d(TAG, "Setting up noblank");
                    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                }

                // TODO: Cache result/config in case kiosk loses internet (Same behaviour as PC version)
                homepage = bundle.getString("homepage", null);

            }


            if (homepage == null) {
                Log.d(TAG, "Homepage not defined");
                homepage = "https://config.webconverger.com/clients/?id=" + ID;
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
            mWebView.loadUrl(homepage);

            // Need to put this in a service to listen for inactivity to go full screen
            View decorView = getWindow().getDecorView();
            // Hide the status bar.
            int uiOptions = View.SYSTEM_UI_FLAG_FULLSCREEN;
            decorView.setSystemUiVisibility(uiOptions);
            // Remember that you should never show the action bar if the
            // status bar is hidden, so hide that too if necessary.
            ActionBar actionBar = getActionBar();
            actionBar.hide();
        }
    }




}
