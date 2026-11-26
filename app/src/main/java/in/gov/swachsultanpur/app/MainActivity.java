package in.gov.swachsultanpur.app;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.webkit.GeolocationPermissions;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int REQUEST_LOCATION = 201;
    private static final int REQUEST_CAMERA = 202;
    private static final int REQUEST_FILE = 203;

    private static final String APP_URL =
        "https://script.google.com/macros/s/AKfycbyGFgfkYDH6ESrY2kCwtoGPClUCvY20nHCSKvocMVlQrtkvTFzLuLuKcBt_1G3HUwzMCQ/exec";

    private WebView webView;
    private ValueCallback<Uri[]> filePathCallback;
    private Uri cameraPhotoUri;
    private GeolocationPermissions.Callback pendingGeoCallback;
    private String pendingGeoOrigin;
    private PermissionRequest pendingWebPermission;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        webView = new WebView(this);
        setContentView(webView);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setGeolocationEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(true);
        settings.setSupportMultipleWindows(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);

        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onGeolocationPermissionsShowPrompt(
                    String origin, GeolocationPermissions.Callback callback) {
                if (!isTrustedGoogleOrigin(origin)) {
                    callback.invoke(origin, false, false);
                    return;
                }
                if (hasLocationPermission()) {
                    callback.invoke(origin, true, true);
                } else {
                    pendingGeoOrigin = origin;
                    pendingGeoCallback = callback;
                    requestPermissions(new String[] {
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    }, REQUEST_LOCATION);
                }
            }

            @Override
            public void onPermissionRequest(PermissionRequest request) {
                // Allow web camera capture only after Android camera permission is granted.
                boolean asksForVideo = false;
                for (String resource : request.getResources()) {
                    if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(resource)) {
                        asksForVideo = true;
                    }
                }
                if (asksForVideo && checkSelfPermission(Manifest.permission.CAMERA)
                        != PackageManager.PERMISSION_GRANTED) {
                    pendingWebPermission = request;
                    requestPermissions(new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA);
                } else {
                    runOnUiThread(() -> request.grant(request.getResources()));
                }
            }

            @Override
            public void onPermissionRequestCanceled(PermissionRequest request) {
                if (pendingWebPermission == request) pendingWebPermission = null;
            }

            @Override
            public boolean onShowFileChooser(WebView view,
                    ValueCallback<Uri[]> callback,
                    FileChooserParams params) {
                if (filePathCallback != null) filePathCallback.onReceiveValue(null);
                filePathCallback = callback;
                // The HTML camera input has capture="environment". Open the
                // native camera directly for that input; do not show Gallery first.
                if (params != null && params.isCaptureEnabled()) {
                    openCameraOnly();
                } else {
                    openGalleryOnly();
                }
                return true;
            }
        });

        if (savedInstanceState == null) {
            webView.loadUrl(APP_URL);
        } else {
            webView.restoreState(savedInstanceState);
        }

    }

    private boolean isTrustedGoogleOrigin(String origin) {
        if (origin == null) return false;
        return origin.startsWith("https://script.google.com/") ||
               origin.equals("https://script.google.com") ||
               origin.startsWith("https://script.googleusercontent.com/") ||
               origin.equals("https://script.googleusercontent.com");
    }

    private boolean hasLocationPermission() {
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED ||
               checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED;
    }

    private void openCameraOnly() {
        try {
            File photo = createImageFile();
            cameraPhotoUri = FileProvider.getUriForFile(
                this, getPackageName() + ".fileprovider", photo);

            Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, cameraPhotoUri);
            cameraIntent.setClipData(ClipData.newRawUri("camera_photo", cameraPhotoUri));
            cameraIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION |
                                  Intent.FLAG_GRANT_WRITE_URI_PERMISSION);

            if (cameraIntent.resolveActivity(getPackageManager()) != null) {
                startActivityForResult(cameraIntent, REQUEST_FILE);
            } else {
                cameraPhotoUri = null;
                Toast.makeText(this, "कैमरा ऐप उपलब्ध नहीं है। Gallery से फोटो चुनें।",
                    Toast.LENGTH_LONG).show();
                openGalleryOnly();
            }
        } catch (Exception e) {
            cameraPhotoUri = null;
            Toast.makeText(this, "कैमरा नहीं खुल सका: " + e.getMessage(),
                Toast.LENGTH_LONG).show();
            openGalleryOnly();
        }
    }

    private void openGalleryOnly() {
        Intent pickIntent = new Intent(Intent.ACTION_GET_CONTENT);
        pickIntent.addCategory(Intent.CATEGORY_OPENABLE);
        pickIntent.setType("image/*");
        try {
            startActivityForResult(Intent.createChooser(pickIntent, "Gallery से फोटो चुनें"), REQUEST_FILE);
        } catch (ActivityNotFoundException e) {
            finishFileSelection(null);
            Toast.makeText(this, "Gallery ऐप उपलब्ध नहीं है।", Toast.LENGTH_LONG).show();
        }
    }

    private File createImageFile() throws IOException {
        String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        File dir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        if (dir == null) dir = getCacheDir();
        if (!dir.exists()) dir.mkdirs();
        return File.createTempFile("SWACHH_" + stamp + "_", ".jpg", dir);
    }

    private void finishFileSelection(Uri[] results) {
        if (filePathCallback != null) {
            filePathCallback.onReceiveValue(results);
            filePathCallback = null;
        }
        cameraPhotoUri = null;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_FILE) {
            Uri[] results = null;
            if (resultCode == RESULT_OK) {
                if (data != null && data.getData() != null) {
                    results = new Uri[]{data.getData()};
                } else if (cameraPhotoUri != null) {
                    // Return the output URI; WebView reads it through FileProvider.
                    results = new Uri[]{cameraPhotoUri};
                }
            }
            finishFileSelection(results);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
            int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_LOCATION) {
            boolean granted = hasLocationPermission();
            if (pendingGeoCallback != null && pendingGeoOrigin != null) {
                pendingGeoCallback.invoke(pendingGeoOrigin, granted, granted);
                pendingGeoCallback = null;
                pendingGeoOrigin = null;
            }
            if (webView != null && !granted) {
                Toast.makeText(this, "लोकेशन अनुमति नहीं मिली। App Settings में Location Allow करें।",
                        Toast.LENGTH_LONG).show();
            }
        } else if (requestCode == REQUEST_CAMERA) {
            if (pendingWebPermission != null) {
                if (checkSelfPermission(Manifest.permission.CAMERA)
                        == PackageManager.PERMISSION_GRANTED) {
                    pendingWebPermission.grant(pendingWebPermission.getResources());
                } else {
                    pendingWebPermission.deny();
                }
                pendingWebPermission = null;
            }
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (webView != null) webView.saveState(outState);
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.stopLoading();
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}
