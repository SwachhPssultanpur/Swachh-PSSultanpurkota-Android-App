package in.gov.swachsultanpur.app;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;
import androidx.core.content.FileProvider;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int REQUEST_LOCATION = 201;
    private static final int REQUEST_CAMERA = 202;
    private static final int REQUEST_FILE = 203;
    private static final String APP_URL = "https://script.google.com/macros/s/AKfycbyGFgfkYDH6ESrY2kCwtoGPClUCvY20nHCSKvocMVlQrtkvTFzLuLuKcBt_1G3HUwzMCQ/exec";

    private WebView webView;
    private ValueCallback<Uri[]> filePathCallback;
    private Uri cameraPhotoUri;
    private File cameraPhotoFile;
    private String pendingNativeAction = "";
    private LocationManager locationManager;
    private LocationListener locationListener;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean locationTimedOut = false;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        webView = new WebView(this);
        setContentView(webView);
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setGeolocationEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setJavaScriptCanOpenWindowsAutomatically(true);
        s.setSupportMultipleWindows(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        webView.addJavascriptInterface(new NativeBridge(), "SwachhNative");
        webView.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                installNativeHooks();
            }
        });
        webView.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (filePathCallback != null) filePathCallback.onReceiveValue(null);
                filePathCallback = callback;
                openGalleryOnly();
                return true;
            }
        });
        if (state == null) webView.loadUrl(APP_URL); else webView.restoreState(state);
    }

    private void installNativeHooks() {
        String js = "(function(){"
            + "window.openCamera=function(){SwachhNative.openCamera();};"
            + "window.getGPS=function(){var b=document.getElementById('gpsBtn');var t=document.getElementById('gpsText');if(b)b.disabled=true;if(t)t.textContent='Android GPS से लोकेशन प्राप्त हो रही है...';SwachhNative.getLocation();};"
            + "window.onNativePhoto=function(data){var p=document.getElementById('preview');if(p){p.src=data;p.classList.remove('hidden');}PHOTO_DATA=data;var m=document.getElementById('msg');if(m){m.textContent='कैमरे से फोटो ले ली गई है। अब GPS लेकर अपलोड करें।';m.className='ok';}};"
            + "window.onNativeLocation=function(lat,lng,acc){GPS={lat:lat,lng:lng,accuracy:acc};var b=document.getElementById('gpsBtn');var t=document.getElementById('gpsText');if(b)b.disabled=false;if(t)t.textContent='लोकेशन मिली: '+lat.toFixed(6)+', '+lng.toFixed(6)+' | Accuracy: '+Math.round(acc)+' मीटर';};"
            + "window.onNativeCameraError=function(message){var m=document.getElementById('msg');if(m){m.textContent=message;m.className='err';}};"
            + "window.onNativeLocationError=function(message){var b=document.getElementById('gpsBtn');var t=document.getElementById('gpsText');if(b)b.disabled=false;if(t)t.textContent=message;};"
            + "})();";
        webView.evaluateJavascript(js, null);
    }

    public class NativeBridge {
        @JavascriptInterface public void openCamera() { runOnUiThread(() -> startNativeCamera()); }
        @JavascriptInterface public void getLocation() { runOnUiThread(() -> startNativeLocation()); }
    }

    private void startNativeCamera() {
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            pendingNativeAction = "camera";
            requestPermissions(new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA);
            return;
        }
        launchCameraIntent();
    }

    private void launchCameraIntent() {
        try {
            cameraPhotoFile = createImageFile();
            cameraPhotoUri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", cameraPhotoFile);
            Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            intent.putExtra(MediaStore.EXTRA_OUTPUT, cameraPhotoUri);
            intent.setClipData(ClipData.newRawUri("swachh_camera", cameraPhotoUri));
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            if (intent.resolveActivity(getPackageManager()) == null) throw new ActivityNotFoundException();
            startActivityForResult(intent, REQUEST_FILE);
        } catch (Exception e) {
            nativeCameraError("कैमरा नहीं खुल सका: " + safe(e.getMessage()));
        }
    }

    private void openGalleryOnly() {
        Intent pick = new Intent(Intent.ACTION_GET_CONTENT);
        pick.addCategory(Intent.CATEGORY_OPENABLE);
        pick.setType("image/*");
        try { startActivityForResult(Intent.createChooser(pick, "Gallery से फोटो चुनें"), REQUEST_FILE); }
        catch (ActivityNotFoundException e) { finishFileSelection(null); }
    }

    private void startNativeLocation() {
        if (!hasLocationPermission()) {
            pendingNativeAction = "location";
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, REQUEST_LOCATION);
            return;
        }
        try {
            locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
            Location best = null;
            for (String provider : locationManager.getProviders(true)) {
                Location l = locationManager.getLastKnownLocation(provider);
                if (l != null && (best == null || l.getTime() > best.getTime())) best = l;
            }
            if (best != null && System.currentTimeMillis() - best.getTime() < 60000) {
                sendLocation(best);
                return;
            }
            stopLocationUpdates();
            locationTimedOut = false;
            locationListener = new LocationListener() {
                @Override public void onLocationChanged(Location location) { if (!locationTimedOut) { locationTimedOut = true; stopLocationUpdates(); sendLocation(location); } }
                @Override public void onProviderEnabled(String provider) {}
                @Override public void onProviderDisabled(String provider) {}
                @SuppressWarnings("deprecation") @Override public void onStatusChanged(String provider, int status, Bundle extras) {}
            };
            boolean requested = false;
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0, locationListener);
                requested = true;
            }
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 1000, 0, locationListener);
                requested = true;
            }
            if (!requested) { nativeJsError("मोबाइल की Location/GPS सेटिंग बंद है। उसे चालू करके दोबारा प्रयास करें।"); return; }
            handler.postDelayed(() -> {
                if (!locationTimedOut) {
                    locationTimedOut = true;
                    Location last = null;
                    try {
                        for (String p : locationManager.getProviders(true)) {
                            Location l = locationManager.getLastKnownLocation(p);
                            if (l != null && (last == null || l.getTime() > last.getTime())) last = l;
                        }
                    } catch (SecurityException ignored) {}
                    stopLocationUpdates();
                    if (last != null) sendLocation(last); else nativeJsError("GPS लोकेशन नहीं मिली। खुले स्थान पर जाकर GPS चालू करके फिर प्रयास करें।");
                }
            }, 30000);
        } catch (SecurityException e) {
            nativeJsError("Location की अनुमति नहीं मिली। App Settings में Location Allow करें।");
        } catch (Exception e) {
            nativeJsError("GPS त्रुटि: " + safe(e.getMessage()));
        }
    }

    private void sendLocation(Location l) {
        double lat = l.getLatitude(), lng = l.getLongitude();
        float acc = l.hasAccuracy() ? l.getAccuracy() : 0f;
        runOnUiThread(() -> webView.evaluateJavascript("window.onNativeLocation(" + lat + "," + lng + "," + acc + ");", null));
    }

    private void stopLocationUpdates() {
        if (locationManager != null && locationListener != null) {
            try { locationManager.removeUpdates(locationListener); } catch (SecurityException ignored) {}
        }
        locationListener = null;
    }

    private boolean hasLocationPermission() {
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            || checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private void nativeCameraError(String message) {
        String escaped = message.replace("\\", "\\\\").replace("'", "\\'").replace("\n", " ");
        if (webView != null) webView.post(() -> webView.evaluateJavascript("window.onNativeCameraError('" + escaped + "');", null));
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private void nativeJsError(String message) {
        String escaped = message.replace("\\", "\\\\").replace("'", "\\'").replace("\n", " ");
        if (webView != null) webView.post(() -> webView.evaluateJavascript("window.onNativeLocationError('" + escaped + "');", null));
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private File createImageFile() throws Exception {
        String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        File dir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        if (dir == null) dir = getCacheDir();
        if (!dir.exists()) dir.mkdirs();
        return File.createTempFile("SWACHH_" + stamp + "_", ".jpg", dir);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_FILE) return;
        if (cameraPhotoUri != null) {
            if (resultCode == RESULT_OK && cameraPhotoFile != null && cameraPhotoFile.exists() && cameraPhotoFile.length() > 0) {
                deliverPhotoAsBase64(cameraPhotoFile);
            } else nativeCameraError("कैमरे से फोटो नहीं मिली। कृपया दोबारा फोटो लें।");
            cameraPhotoUri = null; cameraPhotoFile = null;
        } else {
            Uri selected = resultCode == RESULT_OK && data != null ? data.getData() : null;
            finishFileSelection(selected == null ? null : new Uri[]{selected});
        }
    }

    private void deliverPhotoAsBase64(File file) {
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options(); bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
            int sample = 1;
            while (bounds.outWidth / sample > 1600 || bounds.outHeight / sample > 1600) sample *= 2;
            BitmapFactory.Options opts = new BitmapFactory.Options(); opts.inSampleSize = sample;
            Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath(), opts);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, 78, out);
            bitmap.recycle();
            String b64 = android.util.Base64.encodeToString(out.toByteArray(), android.util.Base64.NO_WRAP);
            String js = "window.onNativePhoto('data:image/jpeg;base64," + b64 + "');";
            webView.post(() -> webView.evaluateJavascript(js, null));
        } catch (Exception e) { nativeCameraError("फोटो पढ़ने में समस्या: " + safe(e.getMessage())); }
    }

    private void finishFileSelection(Uri[] result) {
        if (filePathCallback != null) { filePathCallback.onReceiveValue(result); filePathCallback = null; }
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        boolean granted = results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED;
        if (requestCode == REQUEST_CAMERA) {
            String action = pendingNativeAction; pendingNativeAction = "";
            if (granted && "camera".equals(action)) launchCameraIntent();
            else if (!granted) nativeCameraError("कैमरा अनुमति नहीं मिली। App Settings में Camera Allow करें।");
        } else if (requestCode == REQUEST_LOCATION) {
            granted = hasLocationPermission();
            String action = pendingNativeAction; pendingNativeAction = "";
            if (granted && "location".equals(action)) startNativeLocation();
            else if (!granted) nativeJsError("लोकेशन अनुमति नहीं मिली। App Settings में Location Allow करें।");
        }
    }

    private String safe(String s) { return s == null ? "अज्ञात त्रुटि" : s; }

    @Override protected void onSaveInstanceState(Bundle out) { super.onSaveInstanceState(out); if (webView != null) webView.saveState(out); }
    @Override public void onBackPressed() { if (webView != null && webView.canGoBack()) webView.goBack(); else super.onBackPressed(); }
    @Override protected void onDestroy() { stopLocationUpdates(); handler.removeCallbacksAndMessages(null); if (webView != null) { webView.stopLoading(); webView.destroy(); webView = null; } super.onDestroy(); }
}
