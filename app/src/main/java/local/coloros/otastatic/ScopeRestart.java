package local.coloros.otastatic;

import android.app.Activity;
import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.util.Log;

/** A signature-protected request to exit a scope's own process, never its shared UID. */
final class ScopeRestart {
    static final String ACTION = "local.coloros.otastatic.RESTART_SETTINGS";
    static final String PERMISSION = "local.coloros.otastatic.permission.RESTART_SETTINGS";
    private static boolean installed;

    static void install(Application app) {
        if (installed || !Backgrounds.SETTINGS.equals(app.getPackageName())
                && !Backgrounds.OTA.equals(app.getPackageName())) {
            return;
        }
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (!ACTION.equals(intent.getAction())) {
                    return;
                }
                setResultCode(Activity.RESULT_OK);
                // Let the ordered-broadcast result reach the module before exiting.
                new Handler(Looper.getMainLooper()).postDelayed(
                        () -> Process.killProcess(Process.myPid()), 250);
            }
        };
        try {
            IntentFilter filter = new IntentFilter(ACTION);
            if (Build.VERSION.SDK_INT >= 33) {
                app.registerReceiver(receiver, filter, PERMISSION, null, Context.RECEIVER_EXPORTED);
            } else {
                registerLegacyReceiver(app, receiver, filter);
            }
            installed = true;
        } catch (RuntimeException failure) {
            Log.w("Ganyu-Restart", "Unable to register scope restart request", failure);
        }
    }

    @android.annotation.SuppressLint("UnspecifiedRegisterReceiverFlag")
    private static void registerLegacyReceiver(Application app, BroadcastReceiver receiver, IntentFilter filter) {
        // Called only below API 33, where the exported receiver flags are unavailable.
        // The signature-level sender permission is enforced on these versions too.
        app.registerReceiver(receiver, filter, PERMISSION, null);
    }

    static void closeForReset(Context context, Backgrounds.Slot slot) {
        String target = slot == Backgrounds.Slot.UPDATE ? Backgrounds.OTA : Backgrounds.SETTINGS;
        context.sendOrderedBroadcast(new Intent(ACTION).setPackage(target)
                .addFlags(Intent.FLAG_RECEIVER_FOREGROUND), null);
    }

    private ScopeRestart() {}
}
