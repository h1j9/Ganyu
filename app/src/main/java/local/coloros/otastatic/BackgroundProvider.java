package local.coloros.otastatic;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;

import java.io.FileNotFoundException;
import java.io.IOException;

public final class BackgroundProvider extends ContentProvider {
    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        String caller = getCallingPackage();
        if (!"r".equals(mode)) {
            throw new SecurityException("Backgrounds are read-only");
        }
        for (Backgrounds.Slot slot : Backgrounds.Slot.values()) {
            if (!slot.uri.equals(uri)) {
                continue;
            }
            String allowed = slot == Backgrounds.Slot.UPDATE ? Backgrounds.OTA : Backgrounds.SETTINGS;
            if (!allowed.equals(caller) && !getContext().getPackageName().equals(caller)) {
                throw new SecurityException("Background is private");
            }
            try {
                return Backgrounds.open(getContext(), slot);
            } catch (FileNotFoundException missing) {
                throw missing;
            } catch (IOException failure) {
                FileNotFoundException error = new FileNotFoundException("Unable to open background");
                error.initCause(failure);
                throw error;
            }
        }
        throw new FileNotFoundException("Unknown background");
    }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        if (Backgrounds.BACKGROUND_STATE.equals(method)) {
            for (Backgrounds.Slot slot : Backgrounds.Slot.values()) {
                if (!slot.path.equals(arg)) {
                    continue;
                }
                String caller = getCallingPackage();
                String allowed = slot == Backgrounds.Slot.UPDATE ? Backgrounds.OTA : Backgrounds.SETTINGS;
                if (!allowed.equals(caller) && !getContext().getPackageName().equals(caller)) {
                    throw new SecurityException("Background state is private");
                }
                Bundle state = new Bundle();
                state.putBoolean(Backgrounds.ENABLED, Backgrounds.exists(getContext(), slot));
                return state;
            }
            throw new IllegalArgumentException("Unknown background");
        }
        if (!Backgrounds.SETTINGS_STYLE.equals(method)) {
            return super.call(method, arg, extras);
        }
        String caller = getCallingPackage();
        if (!Backgrounds.SETTINGS.equals(caller) && !getContext().getPackageName().equals(caller)) {
            throw new SecurityException("Settings background is private");
        }
        Bundle style = new Bundle();
        style.putInt(Backgrounds.OPACITY, Backgrounds.settingsOpacity(getContext()));
        style.putLong(Backgrounds.MODIFIED, Backgrounds.settingsImageModified(getContext()));
        return style;
    }

    @Override
    public String getType(Uri uri) {
        return "image/jpeg";
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException();
    }
}
