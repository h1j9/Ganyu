package local.coloros.otastatic;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.util.AtomicFile;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

final class Backgrounds {
    static final String SETTINGS = "com.android.settings";
    static final String OTA = "com.oplus.ota";
    static final String AUTHORITY = "local.coloros.otastatic.backgrounds";
    static final String SETTINGS_STYLE = "settingsStyle";
    static final String OPACITY = "opacity";
    static final String MODIFIED = "modified";
    static final String BACKGROUND_STATE = "backgroundState";
    static final String ENABLED = "enabled";

    enum Slot {
        CARD("card", "关于本机 · OTA 小卡片"),
        UPDATE("update", "软件更新 · 全屏背景"),
        SETTINGS_PAGE("settings", "系统设置 · 全局背景");

        final String path;
        final String title;
        final Uri uri;

        Slot(String path, String title) {
            this.path = path;
            this.title = title;
            uri = Uri.parse("content://" + AUTHORITY + "/" + path);
        }
    }

    private Backgrounds() {}

    private static AtomicFile file(Context context, Slot slot) {
        return new AtomicFile(new File(context.getFilesDir(), slot.path + ".jpg"));
    }

    static synchronized long settingsImageModified(Context context) {
        return file(context, Slot.SETTINGS_PAGE).getBaseFile().lastModified();
    }

    static synchronized boolean exists(Context context, Slot slot) {
        return file(context, slot).getBaseFile().isFile();
    }

    static boolean hasCustom(Context context, Slot slot) {
        try {
            android.os.Bundle state = context.getContentResolver().call(
                    slot.uri, BACKGROUND_STATE, slot.path, null);
            return state != null && state.getBoolean(ENABLED);
        } catch (RuntimeException failure) {
            // If the module is unavailable, let the host keep its original behavior.
            return false;
        }
    }

    static int settingsOpacity(Context context) {
        return context.getSharedPreferences(SETTINGS_STYLE, Context.MODE_PRIVATE).getInt(OPACITY, 45);
    }

    static void setSettingsOpacity(Context context, int percent) {
        context.getSharedPreferences(SETTINGS_STYLE, Context.MODE_PRIVATE)
                .edit().putInt(OPACITY, percent).apply();
    }

    static synchronized ParcelFileDescriptor open(Context context, Slot slot) throws IOException {
        try (FileInputStream stream = file(context, slot).openRead()) {
            return ParcelFileDescriptor.dup(stream.getFD());
        }
    }

    static synchronized void save(Context context, Slot slot, Bitmap bitmap) throws IOException {
        AtomicFile target = file(context, slot);
        FileOutputStream out = target.startWrite();
        try {
            if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 94, out)) {
                throw new IOException("Unable to encode background");
            }
            target.finishWrite(out);
        } catch (IOException | RuntimeException failure) {
            target.failWrite(out);
            throw failure;
        }
    }

    static synchronized void reset(Context context, Slot slot) {
        file(context, slot).delete();
    }

    static Bitmap preview(Context context, Slot slot) {
        try (ParcelFileDescriptor image = open(context, slot)) {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = 4;
            return BitmapFactory.decodeFileDescriptor(image.getFileDescriptor(), null, options);
        } catch (IOException failure) {
            return null;
        }
    }

    static Bitmap load(Context context, Slot slot) {
        try (ParcelFileDescriptor image =
                     context.getContentResolver().openFileDescriptor(slot.uri, "r")) {
            return image == null ? null : BitmapFactory.decodeFileDescriptor(image.getFileDescriptor());
        } catch (FileNotFoundException missing) {
            return null;
        } catch (IOException | RuntimeException failure) {
            Log.w("ColorOS-OtaBackground", "Unable to read " + slot.path, failure);
            return null;
        }
    }
}
