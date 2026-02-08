package com.getcapacitor.community.admob.banner;

import android.app.Activity;
import android.content.Context;
import android.os.Build;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;
import androidx.annotation.NonNull;
import androidx.core.util.Supplier;
import com.getcapacitor.JSObject;
import com.getcapacitor.PluginCall;
import com.getcapacitor.community.admob.helpers.AdViewIdHelper;
import com.getcapacitor.community.admob.helpers.RequestHelper;
import com.getcapacitor.community.admob.models.AdMobPluginError;
import com.getcapacitor.community.admob.models.AdOptions;
import com.getcapacitor.community.admob.models.Executor;
import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.common.util.BiConsumer;

public class BannerExecutor extends Executor {

    private final JSObject emptyObject = new JSObject();
    private RelativeLayout mAdViewLayout;
    private AdView mAdView;
    private ViewGroup mViewGroup;

    public BannerExecutor(
        Supplier<Context> contextSupplier,
        Supplier<Activity> activitySupplier,
        BiConsumer<String, JSObject> notifyListenersFunction,
        String pluginLogTag
    ) {
        super(contextSupplier, activitySupplier, notifyListenersFunction, pluginLogTag, "BannerExecutor");
    }

    public void initialize() {
        // Use android.R.id.content (FrameLayout) to ensure LayoutParams work correctly
        mViewGroup = (ViewGroup) activitySupplier.get().findViewById(android.R.id.content);
    }

    public void showBanner(final PluginCall call) {
        final AdOptions adOptions = AdOptions.getFactory().createBannerOptions(call);

        if (mAdView != null) {
            updateExistingAdView(adOptions);
            call.resolve();
            return;
        }

        // 1. Calculate Screen Metrics
        DisplayMetrics metrics = contextSupplier.get().getResources().getDisplayMetrics();
        float density = metrics.density;
        int screenWidthPixels = metrics.widthPixels;
        int screenWidthDp = (int) (screenWidthPixels / density);

        // 2. Create Views on UI Thread
        activitySupplier
            .get()
            .runOnUiThread(() -> {
                try {
                    mAdView = new AdView(contextSupplier.get());

                    // --- Adaptive Fallback Logic ---
                    AdSize finalSize;
                    AdSize adaptiveSize = AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(contextSupplier.get(), screenWidthDp);
                    String requestedSize = adOptions.adSize.toString();

                    // If specific sizes are requested but screen is too narrow, fallback to Adaptive
                    if ("ADAPTIVE_BANNER".equals(requestedSize)) {
                        finalSize = adaptiveSize;
                    } else if ("LEADERBOARD".equals(requestedSize) && screenWidthDp < 728) {
                        finalSize = adaptiveSize;
                    } else if ("FULL_BANNER".equals(requestedSize) && screenWidthDp < 468) {
                        finalSize = adaptiveSize;
                    } else {
                        finalSize = adOptions.adSize.getSize();
                    }

                    mAdView.setAdSize(finalSize);

                    // --- Layout Setup ---
                    mAdViewLayout = new RelativeLayout(contextSupplier.get());
                    mAdViewLayout.setGravity(Gravity.CENTER_HORIZONTAL); // Horizontal Centering

                    final FrameLayout.LayoutParams mAdViewLayoutParams = new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT, // Width Match Parent to allow Gravity centering
                        FrameLayout.LayoutParams.WRAP_CONTENT
                    );

                    // --- Positioning Gravity ---
                    if ("TOP_CENTER".equals(adOptions.position)) {
                        mAdViewLayoutParams.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
                    } else if ("CENTER".equals(adOptions.position)) {
                        mAdViewLayoutParams.gravity = Gravity.CENTER;
                    } else {
                        mAdViewLayoutParams.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
                    }

                    // --- Original Window Inset Logic (Restored) ---
                    // This logic attaches to DecorView, exactly as requested
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                        View rootView = activitySupplier.get().getWindow().getDecorView();
                        rootView.setOnApplyWindowInsetsListener((v, insets) -> {
                            int bottomInset = insets.getSystemWindowInsetBottom();
                            int topInset = insets.getSystemWindowInsetTop();

                            int densityMargin = (int) (adOptions.margin * density);

                            if ("TOP_CENTER".equals(adOptions.position)) {
                                mAdViewLayoutParams.setMargins(0, topInset + densityMargin, 0, 0);
                            } else if (!"CENTER".equals(adOptions.position)) {
                                mAdViewLayoutParams.setMargins(0, 0, 0, bottomInset + densityMargin);
                            }

                            // Update the layout params dynamically
                            mAdViewLayout.setLayoutParams(mAdViewLayoutParams);
                            return insets;
                        });
                    }

                    // Apply initial margins (important for versions < Android 15 or before Insets apply)
                    int densityMargin = (int) (adOptions.margin * density);
                    if ("TOP_CENTER".equals(adOptions.position)) {
                        mAdViewLayoutParams.setMargins(0, densityMargin, 0, 0);
                    } else if (!"CENTER".equals(adOptions.position)) {
                        mAdViewLayoutParams.setMargins(0, 0, 0, densityMargin);
                    }

                    mAdViewLayout.setLayoutParams(mAdViewLayoutParams);

                    loadAndAttachAd(adOptions);

                    call.resolve();
                } catch (Exception ex) {
                    call.reject(ex.getLocalizedMessage(), ex);
                }
            });
    }

    public void hideBanner(final PluginCall call) {
        if (mAdView == null) {
            call.reject("You tried to hide a banner that was never shown");
            return;
        }

        try {
            activitySupplier
                .get()
                .runOnUiThread(() -> {
                    if (mAdViewLayout != null && mAdView != null) {
                        mAdViewLayout.setVisibility(View.GONE);
                        mAdView.pause();
                        notifyListeners(BannerAdPluginEvents.SizeChanged.getWebEventName(), new BannerAdSizeInfo(0, 0));
                        call.resolve();
                    }
                });
        } catch (Exception ex) {
            call.reject(ex.getLocalizedMessage(), ex);
        }
    }

    public void resumeBanner(final PluginCall call) {
        try {
            activitySupplier
                .get()
                .runOnUiThread(() -> {
                    if (mAdViewLayout != null && mAdView != null) {
                        mAdViewLayout.setVisibility(View.VISIBLE);
                        mAdView.resume();
                        notifyListeners(BannerAdPluginEvents.SizeChanged.getWebEventName(), new BannerAdSizeInfo(mAdView));
                        Log.d(logTag, "Banner AD Resumed");
                    }
                });
            call.resolve();
        } catch (Exception ex) {
            call.reject(ex.getLocalizedMessage(), ex);
        }
    }

    public void removeBanner(final PluginCall call) {
        try {
            activitySupplier
                .get()
                .runOnUiThread(() -> {
                    if (mAdView != null) {
                        if (mViewGroup != null && mAdViewLayout != null) {
                            mViewGroup.removeView(mAdViewLayout);
                        }
                        if (mAdViewLayout != null) {
                            mAdViewLayout.removeView(mAdView);
                        }
                        mAdView.destroy();
                        mAdView = null;
                        mAdViewLayout = null;
                        Log.d(logTag, "Banner AD Removed");
                        notifyListeners(BannerAdPluginEvents.SizeChanged.getWebEventName(), new BannerAdSizeInfo(0, 0));
                    }
                });
            call.resolve();
        } catch (Exception ex) {
            call.reject(ex.getLocalizedMessage(), ex);
        }
    }

    private void updateExistingAdView(AdOptions adOptions) {
        activitySupplier
            .get()
            .runOnUiThread(() -> {
                if (mAdView != null) {
                    final AdRequest adRequest = RequestHelper.createRequest(adOptions);
                    mAdView.loadAd(adRequest);
                }
            });
    }

    private void loadAndAttachAd(AdOptions adOptions) {
        if (mAdView != null && mAdViewLayout != null) {
            final AdRequest adRequest = RequestHelper.createRequest(adOptions);
            AdViewIdHelper.assignIdToAdView(mAdView, adOptions, adRequest, logTag, contextSupplier.get());

            // Add AdView to Container
            mAdViewLayout.addView(mAdView);

            // Start Load
            mAdView.loadAd(adRequest);

            mAdView.setAdListener(
                new AdListener() {
                    @Override
                    public void onAdLoaded() {
                        if (mAdView != null) {
                            JSObject loadedAdInfo = new JSObject();
                            loadedAdInfo.put("isCollapsible", mAdView.isCollapsible());
                            notifyListeners(BannerAdPluginEvents.SizeChanged.getWebEventName(), new BannerAdSizeInfo(mAdView));
                            notifyListeners(BannerAdPluginEvents.Loaded.getWebEventName(), loadedAdInfo);
                        }
                        super.onAdLoaded();
                    }

                    @Override
                    public void onAdFailedToLoad(@NonNull LoadAdError adError) {
                        if (mAdView != null) {
                            if (mViewGroup != null) mViewGroup.removeView(mAdViewLayout);
                            if (mAdViewLayout != null) mAdViewLayout.removeView(mAdView);
                            mAdView.destroy();
                            mAdView = null;
                        }
                        notifyListeners(BannerAdPluginEvents.SizeChanged.getWebEventName(), new BannerAdSizeInfo(0, 0));
                        notifyListeners(BannerAdPluginEvents.FailedToLoad.getWebEventName(), new AdMobPluginError(adError));
                        super.onAdFailedToLoad(adError);
                    }

                    @Override
                    public void onAdOpened() {
                        notifyListeners(BannerAdPluginEvents.Opened.getWebEventName(), emptyObject);
                        super.onAdOpened();
                    }

                    @Override
                    public void onAdClosed() {
                        notifyListeners(BannerAdPluginEvents.Closed.getWebEventName(), emptyObject);
                        super.onAdClosed();
                    }

                    @Override
                    public void onAdImpression() {
                        notifyListeners(BannerAdPluginEvents.AdImpression.getWebEventName(), emptyObject);
                        super.onAdImpression();
                    }
                }
            );

            // Add Container to Main View
            mViewGroup.addView(mAdViewLayout);
        }
    }
}
