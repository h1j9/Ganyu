package local.coloros.otastatic;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.util.Log;
import android.webkit.WebView;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import local.coloros.otastatic.StaticOtaCard.ScreenBackground;

final class WebUpdateBackground extends XC_MethodHook {
    private static final String HOST = "h5fs.coloros.com";
    private static final String PAGE = "/static/index.html";
    private static final String STYLE_ID = "local-coloros-ota-background";
    private static final String TRANSPARENT_BACKGROUND =
            "(()=>{if(location.origin!=='https://" + HOST + "'||!location.pathname.endsWith('"
            + PAGE + "')||document.getElementById('" + STYLE_ID + "'))return;"
            + "const style=document.createElement('style');style.id='" + STYLE_ID + "';"
            + "style.textContent='"
            + "html:has(.ota-main .c17-base-card-bg),"
            + "body:has(.ota-main .c17-base-card-bg),"
            + "#app:has(.ota-main .c17-base-card-bg){background:transparent!important;}"
            + ".ota-main img.c17-base-card-bg{visibility:hidden!important;}';"
            + "(document.head||document.documentElement).appendChild(style);})()";

    private WebUpdateBackground() {
        super(PRIORITY_HIGHEST);
    }

    static void install(ClassLoader loader) {
        try {
            Class<?> client = Class.forName("kd.k", false, loader);
            WebUpdateBackground hook = new WebUpdateBackground();
            XposedBridge.hookMethod(client.getDeclaredMethod("onPageCommitVisible", WebView.class, String.class), hook);
            XposedBridge.hookMethod(client.getDeclaredMethod("onPageFinished", WebView.class, String.class), hook);
            XposedBridge.log("ColorOS OTA Background: Web result background hooks installed");
        } catch (Throwable failure) {
            Log.w("ColorOS-OtaBackground", "Unable to install web result background hooks", failure);
        }
    }

    @Override
    protected void afterHookedMethod(MethodHookParam param) {
        WebView web = (WebView) param.args[0];
        if (param.hasThrowable() || !isUpdatePage((String) param.args[1]) || !isUpdatePage(web.getUrl())) {
            return;
        }
        int webId = web.getResources().getIdentifier("webview", "id", Backgrounds.OTA);
        if (webId == 0 || web.getId() != webId) {
            return;
        }
        Bitmap custom = Backgrounds.load(web.getContext(), Backgrounds.Slot.UPDATE);
        Drawable background = web.getBackground();
        if (custom == null) {
            if (background instanceof ScreenBackground) {
                Drawable original = ((ScreenBackground) background).original;
                web.setBackgroundColor(original instanceof ColorDrawable
                        ? ((ColorDrawable) original).getColor() : Color.TRANSPARENT);
                web.setBackground(original);
            }
            web.evaluateJavascript("(()=>{const style=document.getElementById('" + STYLE_ID
                    + "');if(style)style.remove();})()", null);
            return;
        }
        Drawable original = background instanceof ScreenBackground
                ? ((ScreenBackground) background).original : background;
        web.setBackgroundColor(Color.TRANSPARENT);
        web.setBackground(new ScreenBackground(custom, original));
        web.evaluateJavascript(TRANSPARENT_BACKGROUND, null);
    }

    private static boolean isUpdatePage(String url) {
        if (url == null) {
            return false;
        }
        Uri uri = Uri.parse(url);
        String path = uri.getPath();
        return "https".equals(uri.getScheme()) && HOST.equals(uri.getHost())
                && (uri.getPort() == -1 || uri.getPort() == 443)
                && path != null && path.endsWith(PAGE);
    }
}
