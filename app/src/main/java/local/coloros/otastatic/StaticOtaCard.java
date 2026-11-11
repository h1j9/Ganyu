package local.coloros.otastatic;

import android.content.Context;
import android.app.Application;
import android.app.Instrumentation;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public final class StaticOtaCard implements IXposedHookLoadPackage {
    private static final String CARD =
            "com.oplus.settings.widget.preference.AboutDeviceOtaUpdatePreference";
    private static final String LOG_PREFIX = "ColorOS OTA Background: ";

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam loadPackage) {
        if (Backgrounds.SETTINGS.equals(loadPackage.packageName)) {
            SettingsWallpaper.install(loadPackage.classLoader);
            if (Backgrounds.SETTINGS.equals(loadPackage.processName)) {
                installCardHooks(loadPackage.classLoader);
            }
        } else if (Backgrounds.OTA.equals(loadPackage.packageName)) {
            installUpdateRestart();
            if ((Backgrounds.OTA + ":ui").equals(loadPackage.processName)) {
                installUpdateHook(loadPackage.classLoader);
                installUpdateResultHooks(loadPackage.classLoader);
                WebUpdateBackground.install(loadPackage.classLoader);
            }
        }
    }

    private void installUpdateRestart() {
        try {
            XposedBridge.hookMethod(Instrumentation.class.getDeclaredMethod(
                    "callApplicationOnCreate", Application.class), new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    Application app = (Application) param.args[0];
                    if (Backgrounds.OTA.equals(app.getPackageName())) {
                        ScopeRestart.install(app);
                    }
                }
            });
        } catch (ReflectiveOperationException | RuntimeException failure) {
            XposedBridge.log(LOG_PREFIX + "Unable to register update reset: " + failure);
        }
    }

    private void installCardHooks(ClassLoader classLoader) {
        try {
            Class<?> card = Class.forName(CARD, false, classLoader);
            Method setupVideo = card.getDeclaredMethod("setupTopVideo");
            Method showLastFrame = card.getDeclaredMethod("showTopVideoLastFrame");
            Method getVideoPath = card.getDeclaredMethod("getColorOSVideoPath");
            Method getContext = card.getMethod("getContext");
            if (setupVideo.getReturnType() != void.class || showLastFrame.getReturnType() != void.class
                    || getVideoPath.getReturnType() != String.class) {
                XposedBridge.log(LOG_PREFIX + "Unexpected OTA card method signatures");
                return;
            }

            showLastFrame.setAccessible(true);
            XC_MethodHook.Unhook setupHook =
                    XposedBridge.hookMethod(setupVideo, new StaticFrameHook(showLastFrame, getContext));
            try {
                XposedBridge.hookMethod(getVideoPath, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Exception {
                        Context context = (Context) getContext.invoke(param.thisObject);
                        if (Backgrounds.hasCustom(context, Backgrounds.Slot.CARD)) {
                            param.setResult(null);
                        }
                    }
                });
            } catch (Throwable failure) {
                setupHook.unhook();
                throw failure;
            }
            XposedBridge.log(LOG_PREFIX + "OTA card hooks installed");
            installCardImageHooks(card, showLastFrame);
        } catch (Throwable failure) {
            XposedBridge.log(LOG_PREFIX + "Unable to install OTA card hooks: " + failure);
        }
    }

    private void installCardImageHooks(Class<?> card, Method showLastFrame) {
        try {
            Field image = card.getDeclaredField("mAboutDeviceTopBg");
            Field mask = card.getDeclaredField("mAboutDeviceVideoMask");
            Method showStaticBackground = card.getDeclaredMethod("showStaticTopBackground");
            image.setAccessible(true);
            mask.setAccessible(true);
            CardImageHook hook = new CardImageHook(image, mask);
            XposedBridge.hookMethod(showLastFrame, hook);
            XposedBridge.hookMethod(showStaticBackground, hook);
        } catch (Throwable failure) {
            XposedBridge.log(LOG_PREFIX + "Unable to install custom card image hooks: " + failure);
        }
    }

    private void installUpdateHook(ClassLoader classLoader) {
        try {
            // The PLC110 OTA build uses this getter for the immersive background drawable.
            Class<?> visuals = Class.forName("n8.g", false, classLoader);
            Method getBackground = visuals.getDeclaredMethod("O", Context.class, boolean.class);
            if (getBackground.getReturnType() != Drawable.class) {
                return;
            }
            XposedBridge.hookMethod(getBackground, new UpdateImageHook());
            XposedBridge.log(LOG_PREFIX + "Software update background hook installed");
        } catch (Throwable failure) {
            XposedBridge.log(LOG_PREFIX + "Unable to install software update background hook: " + failure);
        }
    }

    private void installUpdateResultHooks(ClassLoader classLoader) {
        try {
            Class<?> result = Class.forName(
                    "com.oplus.otaui.activity.fragment.FragmentIdle", false, classLoader);
            Method createView = result.getDeclaredMethod(
                    "onCreateView", LayoutInflater.class, ViewGroup.class, Bundle.class);
            XposedBridge.hookMethod(createView, new UpdateResultHook(null));

            // On this build d(boolean) also rebinds the result background after layout changes.
            Method bindView = result.getDeclaredMethod("d", boolean.class);
            Field root = result.getDeclaredField("b");
            root.setAccessible(true);
            XposedBridge.hookMethod(bindView, new UpdateResultHook(root));
            XposedBridge.log(LOG_PREFIX + "Software update result background hooks installed");
        } catch (Throwable failure) {
            XposedBridge.log(LOG_PREFIX + "Unable to install software update result hooks: " + failure);
        }
    }

    private static final class StaticFrameHook extends XC_MethodHook {
        private final Method showLastFrame;
        private final Method getContext;

        StaticFrameHook(Method showLastFrame, Method getContext) {
            this.showLastFrame = showLastFrame;
            this.getContext = getContext;
        }

        @Override
        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
            Context context = (Context) getContext.invoke(param.thisObject);
            if (Backgrounds.hasCustom(context, Backgrounds.Slot.CARD)) {
                showLastFrame.invoke(param.thisObject);
                param.setResult(null);
            }
        }
    }

    private static final class CardImageHook extends XC_MethodHook {
        private final Field image;
        private final Field mask;

        CardImageHook(Field image, Field mask) {
            super(PRIORITY_HIGHEST);
            this.image = image;
            this.mask = mask;
        }

        @Override
        protected void afterHookedMethod(MethodHookParam param) throws IllegalAccessException {
            if (param.hasThrowable()) {
                return;
            }
            ImageView view = (ImageView) image.get(param.thisObject);
            if (view == null) {
                return;
            }
            Bitmap custom = Backgrounds.load(view.getContext(), Backgrounds.Slot.CARD);
            if (custom != null) {
                view.setImageBitmap(custom);
                View overlay = (View) mask.get(param.thisObject);
                if (overlay != null) {
                    overlay.setVisibility(View.INVISIBLE);
                }
            }
        }
    }

    private static final class UpdateImageHook extends XC_MethodHook {
        UpdateImageHook() {
            super(PRIORITY_HIGHEST);
        }

        @Override
        protected void afterHookedMethod(MethodHookParam param) {
            if (!Boolean.FALSE.equals(param.args[1]) || !(param.getResult() instanceof Drawable)) {
                return;
            }
            Drawable original = (Drawable) param.getResult();
            Bitmap custom = Backgrounds.load((Context) param.args[0], Backgrounds.Slot.UPDATE);
            if (custom != null) {
                param.setResult(new ScreenBackground(custom, original));
            }
        }
    }

    private static final class UpdateResultHook extends XC_MethodHook {
        private final Field rootField;

        UpdateResultHook(Field rootField) {
            super(PRIORITY_HIGHEST);
            this.rootField = rootField;
        }

        @Override
        protected void afterHookedMethod(MethodHookParam param) throws IllegalAccessException {
            if (param.hasThrowable()) {
                return;
            }
            Object result = rootField == null ? param.getResult() : rootField.get(param.thisObject);
            if (!(result instanceof View)) {
                return;
            }
            View root = (View) result;
            int backgroundId = root.getResources().getIdentifier("scroll_view_main", "id", Backgrounds.OTA);
            View background = backgroundId == 0 ? null : root.findViewById(backgroundId);
            Drawable original = background == null ? null : background.getBackground();
            if (background == null) {
                return;
            }
            Bitmap custom = Backgrounds.load(background.getContext(), Backgrounds.Slot.UPDATE);
            if (custom != null) {
                if (!(original instanceof ScreenBackground)) {
                    background.setBackground(new ScreenBackground(custom, original));
                }
            } else if (original instanceof ScreenBackground) {
                background.setBackground(((ScreenBackground) original).original);
            }
        }
    }

    static final class ScreenBackground extends Drawable {
        final Drawable original;
        private final Bitmap image;
        private final int intrinsicWidth;
        private final int intrinsicHeight;
        private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
        private final RectF target = new RectF();

        ScreenBackground(Bitmap image, Drawable original) {
            this.image = image;
            this.original = original;
            intrinsicWidth = original == null ? -1 : original.getIntrinsicWidth();
            intrinsicHeight = original == null ? -1 : original.getIntrinsicHeight();
        }

        @Override
        protected void onBoundsChange(Rect bounds) {
            float scale = Math.max(bounds.width() / (float) image.getWidth(),
                    bounds.height() / (float) image.getHeight());
            float halfWidth = image.getWidth() * scale / 2;
            float halfHeight = image.getHeight() * scale / 2;
            target.set(bounds.exactCenterX() - halfWidth, bounds.exactCenterY() - halfHeight,
                    bounds.exactCenterX() + halfWidth, bounds.exactCenterY() + halfHeight);
        }

        @Override
        public void draw(Canvas canvas) {
            canvas.save();
            canvas.clipRect(getBounds());
            canvas.drawBitmap(image, null, target, paint);
            canvas.restore();
        }

        @Override
        public int getIntrinsicWidth() {
            return intrinsicWidth;
        }

        @Override
        public int getIntrinsicHeight() {
            return intrinsicHeight;
        }

        @Override
        public void setAlpha(int alpha) {
            paint.setAlpha(alpha);
            invalidateSelf();
        }

        @Override
        public void setColorFilter(ColorFilter filter) {
            paint.setColorFilter(filter);
            invalidateSelf();
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }
}
