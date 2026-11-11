package local.coloros.otastatic;

import android.app.Activity;
import android.app.Application;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;

import java.lang.reflect.Field;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import local.coloros.otastatic.StaticOtaCard.ScreenBackground;

final class SettingsWallpaper implements Application.ActivityLifecycleCallbacks {
    private static final String TAG = "ColorOS-SettingsBg";
    // Matches Settings' coui_color_card_background_dark (#1affffff).
    private static final int CARD_FILL_ALPHA = 0x1a;
    private final Map<Activity, Page> pages = new IdentityHashMap<>();
    private long imageModified;
    private Bitmap image;

    static void install(ClassLoader loader) {
        installCardFillHook(loader);
        try {
            Class<?> application = Class.forName("com.oplus.settings.SettingsApplicationEx", false, loader);
            XposedBridge.hookMethod(application.getDeclaredMethod("onCreate"),
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            Application app = (Application) param.thisObject;
                            app.registerActivityLifecycleCallbacks(new SettingsWallpaper());
                            ScopeRestart.install(app);
                        }
                    });
        } catch (ReflectiveOperationException | RuntimeException failure) {
            Log.w(TAG, "Unable to register Settings window hook", failure);
        }
    }

    private static void installCardFillHook(ClassLoader loader) {
        try {
            Class<?> card = Class.forName("com.coui.appcompat.cardlist.COUICardListSelectedItemLayout", false, loader);
            Class<?> fill = Class.forName("com.coui.appcompat.cardlist.COUICardListSelectedItemLayout$1", false, loader);
            Field owner = fill.getDeclaredField("this$0");
            Field color = card.getDeclaredField("mCardBackgroundColor");
            owner.setAccessible(true);
            color.setAccessible(true);
            // COUI draws the card fill itself; this drawable's setAlpha() does nothing.
            // Tint its original rounded path translucently for this draw only.
            XposedBridge.hookMethod(fill.getDeclaredMethod("draw", Canvas.class), new XC_MethodHook() {
                private static final String ORIGINAL_COLOR = "ganyu.originalCardColor";

                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws IllegalAccessException {
                    View view = (View) owner.get(param.thisObject);
                    int mode = view.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
                    if (mode == Configuration.UI_MODE_NIGHT_YES) {
                        return;
                    }
                    int original = color.getInt(view);
                    if (!Page.isLightNeutral(original)) {
                        return;
                    }
                    View content = view.getRootView().findViewById(android.R.id.content);
                    if (content != null && content.getBackground() instanceof WallpaperDrawable) {
                        color.setInt(view, translucentCardColor(original));
                        param.setObjectExtra(ORIGINAL_COLOR, original);
                    }
                }

                @Override
                protected void afterHookedMethod(MethodHookParam param) throws IllegalAccessException {
                    Integer original = (Integer) param.getObjectExtra(ORIGINAL_COLOR);
                    if (original != null) {
                        View view = (View) owner.get(param.thisObject);
                        if (color.getInt(view) == translucentCardColor(original)) {
                            color.setInt(view, original);
                        }
                    }
                }
            });
        } catch (ReflectiveOperationException | RuntimeException failure) {
            Log.w(TAG, "Unable to install Settings card fill hook", failure);
        }
    }

    private static int translucentCardColor(int color) {
        return (color & 0x00ffffff) | (CARD_FILL_ALPHA << 24);
    }

    @Override
    public void onActivityPostResumed(Activity activity) {
        onActivityDestroyed(activity);
        if (activity.getWindow().isFloating()) {
            return;
        }
        View content = activity.findViewById(android.R.id.content);
        if (!(content instanceof ViewGroup)) {
            return;
        }
        try {
            Bundle style = activity.getContentResolver().call(Backgrounds.Slot.SETTINGS_PAGE.uri,
                    Backgrounds.SETTINGS_STYLE, null, null);
            if (style == null) {
                return;
            }
            long modified = style.getLong(Backgrounds.MODIFIED);
            int opacity = style.getInt(Backgrounds.OPACITY);
            if (modified == 0 || opacity == 0) {
                if (modified == 0) {
                    image = null;
                    imageModified = 0;
                }
                return;
            }
            if (image == null || modified != imageModified) {
                image = Backgrounds.load(activity, Backgrounds.Slot.SETTINGS_PAGE);
                imageModified = modified;
            }
            if (image != null) {
                Page page = new Page((ViewGroup) content, image, opacity,
                        activity.getClass().getName().startsWith("com.oplus.settings.privacy.Confirm"));
                pages.put(activity, page);
            }
        } catch (RuntimeException failure) {
            Log.w(TAG, "Unable to load the Settings background", failure);
        }
    }

    @Override
    public void onActivityDestroyed(Activity activity) {
        Page page = pages.remove(activity);
        if (page != null) {
            page.close();
        }
    }

    @Override public void onActivityCreated(Activity activity, Bundle state) {}
    @Override public void onActivityStarted(Activity activity) {}
    @Override public void onActivityResumed(Activity activity) {}
    @Override public void onActivityPaused(Activity activity) {}
    @Override public void onActivityStopped(Activity activity) {}
    @Override public void onActivitySaveInstanceState(Activity activity, Bundle state) {}

    private static final class WallpaperDrawable extends LayerDrawable {
        WallpaperDrawable(int base, Bitmap image, int opacity) {
            super(new Drawable[] {new ColorDrawable(base), new ScreenBackground(image, null)});
            getDrawable(1).setAlpha(Math.round(opacity * 255f / 100));
        }
    }

    private static final class FillChange {
        final Drawable original;
        final Drawable replacement;

        FillChange(Drawable original, Drawable replacement) {
            this.original = original;
            this.replacement = replacement;
        }

        void restore(View view) {
            if (view.getBackground() == replacement) {
                view.setBackground(original);
            }
        }
    }

    private static final class Page implements ViewTreeObserver.OnGlobalLayoutListener {
        private final ViewGroup content;
        private final WallpaperDrawable wallpaper;
        private final Map<View, FillChange> removed = new IdentityHashMap<>();
        private final int deviceInfoBackgroundId;
        private final boolean privacyAuthentication;
        private Drawable original;
        private View deviceInfoBackground;

        Page(ViewGroup content, Bitmap bitmap, int opacity, boolean privacyAuthentication) {
            this.content = content;
            this.privacyAuthentication = privacyAuthentication;
            original = content.getBackground();
            wallpaper = new WallpaperDrawable(baseColor(content), bitmap, opacity);
            deviceInfoBackgroundId = content.getResources()
                    .getIdentifier("device_info_list_page_bg", "id", Backgrounds.SETTINGS);
            content.getViewTreeObserver().addOnGlobalLayoutListener(this);
            onGlobalLayout();
        }

        @Override
        public void onGlobalLayout() {
            if (content.getBackground() != wallpaper) {
                original = content.getBackground();
                content.setBackground(wallpaper);
            }
            int minWidth = Math.round(content.getWidth() * 0.88f);
            int minHeight = Math.round(content.getHeight() * 0.55f);
            if (minWidth == 0 || minHeight == 0) {
                return;
            }
            Iterator<Map.Entry<View, FillChange>> iterator = removed.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<View, FillChange> entry = iterator.next();
                View view = entry.getKey();
                if (!view.isAttachedToWindow() || view.getWidth() < minWidth) {
                    entry.getValue().restore(view);
                    iterator.remove();
                } else if (view.getBackground() != entry.getValue().replacement) {
                    iterator.remove();
                }
            }
            removePageFills(content, minWidth, minHeight);
            View backdrop = deviceInfoBackgroundId == 0 ? null : content.findViewById(deviceInfoBackgroundId);
            if (backdrop != deviceInfoBackground) {
                restoreDeviceInfoBackground();
            }
            if (backdrop != null && backdrop.getVisibility() == View.VISIBLE) {
                backdrop.setVisibility(View.INVISIBLE);
                deviceInfoBackground = backdrop;
            }
        }

        private void removePageFills(ViewGroup parent, int minWidth, int minHeight) {
            for (int i = 0; i < parent.getChildCount(); i++) {
                View child = parent.getChildAt(i);
                if (!(child instanceof ViewGroup) || child.getVisibility() != View.VISIBLE) {
                    continue;
                }
                if (child.getWidth() >= minWidth && !removed.containsKey(child)) {
                    boolean night = (child.getResources().getConfiguration().uiMode
                            & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
                    boolean toolbar = privacyAuthentication
                            && (child.getClass().getName().contains("Toolbar")
                            || child.getClass().getName().endsWith("AppBarLayout"));
                    boolean page = child.getHeight() >= minHeight;
                    if (page || toolbar || !night) {
                        Drawable background = child.getBackground();
                        Drawable replacement = transparentFill(background, child, !page && !toolbar);
                        if (replacement != null) {
                            removed.put(child, new FillChange(background, replacement));
                            child.setBackground(replacement);
                        }
                    }
                }
                removePageFills((ViewGroup) child, minWidth, minHeight);
            }
        }

        void close() {
            content.getViewTreeObserver().removeOnGlobalLayoutListener(this);
            if (content.getBackground() == wallpaper) {
                content.setBackground(original);
            }
            for (Map.Entry<View, FillChange> entry : removed.entrySet()) {
                entry.getValue().restore(entry.getKey());
            }
            restoreDeviceInfoBackground();
        }

        private void restoreDeviceInfoBackground() {
            if (deviceInfoBackground != null && deviceInfoBackground.getVisibility() == View.INVISIBLE) {
                deviceInfoBackground.setVisibility(View.VISIBLE);
            }
            deviceInfoBackground = null;
        }

        private static int baseColor(View content) {
            if (content.getBackground() instanceof ColorDrawable) {
                int color = ((ColorDrawable) content.getBackground()).getColor();
                if (Color.alpha(color) == 255) {
                    return color;
                }
            }
            int mode = content.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
            return mode == Configuration.UI_MODE_NIGHT_YES ? 0xff161616 : 0xfff5f5f5;
        }

        private static Drawable transparentFill(Drawable background, View view, boolean lightOnly) {
            if (neutralFill(background, lightOnly)) {
                return new ColorDrawable(Color.TRANSPARENT);
            }
            if (!(background instanceof RippleDrawable) || background.getConstantState() == null) {
                return null;
            }
            RippleDrawable ripple = (RippleDrawable) background.getConstantState()
                    .newDrawable(view.getResources()).mutate();
            boolean changed = false;
            for (int i = 0; i < ripple.getNumberOfLayers(); i++) {
                if (ripple.getId(i) != android.R.id.mask && neutralFill(ripple.getDrawable(i), lightOnly)) {
                    ripple.setDrawable(i, new ColorDrawable(Color.TRANSPARENT));
                    changed = true;
                }
            }
            if (changed) {
                ripple.setState(background.getState());
            }
            return changed ? ripple : null;
        }

        private static boolean neutralFill(Drawable background, boolean lightOnly) {
            int color;
            if (background instanceof ColorDrawable) {
                color = ((ColorDrawable) background).getColor();
            } else if (background instanceof GradientDrawable
                    && ((GradientDrawable) background).getColor() != null
                    && ((GradientDrawable) background).getShape() == GradientDrawable.RECTANGLE) {
                color = ((GradientDrawable) background).getColor().getDefaultColor();
            } else {
                return false;
            }
            int min = Math.min(Color.red(color), Math.min(Color.green(color), Color.blue(color)));
            int max = Math.max(Color.red(color), Math.max(Color.green(color), Color.blue(color)));
            return Color.alpha(color) == 255 && max - min <= 20 && (min >= 215 || !lightOnly && max <= 75);
        }

        private static boolean isLightNeutral(int color) {
            int min = Math.min(Color.red(color), Math.min(Color.green(color), Color.blue(color)));
            int max = Math.max(Color.red(color), Math.max(Color.green(color), Color.blue(color)));
            return Color.alpha(color) == 255 && min >= 215 && max - min <= 20;
        }
    }
}
