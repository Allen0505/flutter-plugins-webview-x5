package io.flutter.plugins.webviewflutter;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.os.Build;
import android.util.DisplayMetrics;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.PermissionRequest;
import android.webkit.WebView;
import android.widget.FrameLayout;

import androidx.annotation.RequiresApi;

import java.util.HashMap;
import java.util.Map;

import io.flutter.plugin.common.MethodChannel;

class FlutterWebChromeClient extends MultiWebViewChromeClient {

    private static final String TAG = "FlutterWebChromeClient";
    private final MethodChannel methodChannel;
    private boolean isFullscreen = false;
    private CustomViewCallback customViewCallback;
    private View customView;
    private InputAwareWebView webView;
    private boolean hasTitleReceivedCallback;

    FlutterWebChromeClient(MethodChannel methodChannel, InputAwareWebView webView) {
        this.methodChannel = methodChannel;
        this.webView = webView;
    }

    void setHasTitleReceivedCallback(boolean hasTitleReceivedCallback) {
        this.hasTitleReceivedCallback = hasTitleReceivedCallback;
    }

    @SuppressWarnings("SuspiciousNameCombination")
    @SuppressLint("SourceLockedOrientationActivity")
    @Override
    public void onShowCustomView(View view, CustomViewCallback callback) {
        super.onShowCustomView(view, callback);
        if (customView != null) {
            callback.onCustomViewHidden();
            return;
        }
        customView = view;
        Activity activity = WebViewFlutterPlugin.activityRef.get();
        if (activity != null) {
            activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        }

        WindowManager wm = (WindowManager) webView.getContext().getSystemService(Context.WINDOW_SERVICE);
        customView = new FullscreenVideoContainer(customView);
        wm.addView(customView, new WindowManager.LayoutParams());
        webView.setDispatchTouchEventDelegation(new View.OnTouchListener() {
            @SuppressLint("ClickableViewAccessibility")
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                return customView.dispatchTouchEvent(event);
            }
        });
        customViewCallback = callback;
        isFullscreen = true;
        onScreenOrientationChanged(true);
    }

    @SuppressLint("SourceLockedOrientationActivity")
    @Override
    public void onHideCustomView() {
        if (customView == null) return;
        Activity activity = WebViewFlutterPlugin.activityRef.get();
        if (activity != null) {
            activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        }
        WindowManager wm = (WindowManager) webView.getContext().getSystemService(Context.WINDOW_SERVICE);
        wm.removeView(customView);
        webView.setDispatchTouchEventDelegation(null);
        customViewCallback.onCustomViewHidden();
        customView = null;
        isFullscreen = false;
        onScreenOrientationChanged(false);
        super.onHideCustomView();
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    public void onPermissionRequest(PermissionRequest request) {
        request.grant(request.getResources());
    }

    @Override
    public void onReceivedTitle(WebView view, String title) {
        if (!hasTitleReceivedCallback) return;

        Map<String, Object> args = new HashMap<>();
        args.put("title", title);
        methodChannel.invokeMethod("onReceivedTitle", args);
    }

    boolean exitFullscreen() {
        if (isFullscreen) {
            onHideCustomView();
            return true;
        }
        return false;
    }

    private DisplayMetrics getMetricsDisplay(Activity activity) {
        WindowManager manager = activity.getWindowManager();
        DisplayMetrics outMetrics = new DisplayMetrics();
        manager.getDefaultDisplay().getMetrics(outMetrics);
        return outMetrics;
    }

    private void onScreenOrientationChanged(boolean isFullscreen) {
        Map<String, Object> args = new HashMap<>();
        args.put("isLandscape", isFullscreen);
        methodChannel.invokeMethod("onScreenOrientationChanged", args);
    }
}

// The given video View may not update measure after Window changes to fullscreen,
// so it'll layout below the status bar, which cause the touch delegation dislocating.
// Here, we force the video View to use the parent's size.
@SuppressLint("ViewConstructor")
class FullscreenVideoContainer extends FrameLayout {
    public FullscreenVideoContainer(View child) {
        super(child.getContext());
        addView(child, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(heightMeasureSpec), MeasureSpec.EXACTLY));
    }
}
