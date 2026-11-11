package local.coloros.otastatic;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.ImageDecoder;
import android.graphics.Insets;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.provider.MediaStore;
import android.util.DisplayMetrics;
import android.util.Size;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import local.coloros.otastatic.Backgrounds.Slot;

public final class BackgroundActivity extends Activity {
    private static final int PICK_CARD = 10;
    private static final int PICK_UPDATE = 11;
    private static final int PICK_BOTH = 12;
    private static final int PICK_SETTINGS = 13;
    // Measured bounds of the background ImageView on the PLC110.
    private static final Size CARD_SIZE = new Size(1160, 1148);
    private int background, surface, raised, ink, muted, accent, accentSoft, accentInk, border;

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private Uri sourceUri;
    private Bitmap source;
    private Slot slot;
    private boolean cropBoth;
    private boolean saving;
    private CropView editor;
    private Size outputSize;
    private final Handler main = new Handler(Looper.getMainLooper());
    private boolean restarting;
    private ImageView restartButton;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        configurePalette();
        showHome();
        if (state != null && state.getString("slot") != null) {
            Uri uri = state.getParcelable("source");
            if (uri != null) {
                loadSource(uri, Slot.valueOf(state.getString("slot")),
                        state.getBoolean("both"), state.getFloatArray("position"));
            }
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        if (slot != null) {
            state.putString("slot", slot.name());
            state.putParcelable("source", sourceUri);
            state.putBoolean("both", cropBoth);
            if (editor != null) {
                state.putFloatArray("position", editor.getPosition());
            }
        }
    }

    @Override
    protected void onDestroy() {
        worker.shutdown();
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (saving) {
            return;
        }
        if (slot != null) {
            showHome();
        } else {
            super.onBackPressed();
        }
    }

    private void showHome() {
        sourceUri = null;
        source = null;
        slot = null;
        editor = null;
        outputSize = null;
        cropBoth = false;
        LinearLayout page = page();
        page.addView(homeToolbar());
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout content = column();
        content.setPadding(dp(20), dp(12), dp(20), dp(28));
        scroll.addView(content);
        page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        addText(content, "背景设置", 28, true);
        addText(content, "选一张喜欢的图，让设置更像你。", 14, false);
        addSlot(content, Slot.SETTINGS_PAGE, "应用于设置的各个页面");
        TextView otaTitle = text("OTA 背景", 21, ink, true);
        LinearLayout.LayoutParams titleSpace = new LinearLayout.LayoutParams(-1, -2);
        titleSpace.topMargin = dp(26);
        titleSpace.bottomMargin = dp(12);
        content.addView(otaTitle, titleSpace);
        TextView otaHint = text("选择自定义 OTA 图片后，对应动态背景会改为静态图片；恢复默认后恢复系统原生背景及动画。", 12, muted, false);
        otaHint.setLineSpacing(dp(3), 1);
        otaHint.setPadding(0, 0, 0, dp(12));
        content.addView(otaHint);
        LinearLayout together = row();
        together.setPadding(dp(16), dp(14), dp(14), dp(14));
        together.setBackground(touchBackground(accentSoft, 16, 0));
        together.setOnClickListener(v -> pick(PICK_BOTH));
        together.setContentDescription("用同一张原图设置两处 OTA 背景");
        together.setFocusable(true);
        together.addView(icon(R.drawable.ic_layers, accent), new LinearLayout.LayoutParams(dp(22), dp(22)));
        TextView sameImage = text("同一张原图，分别裁切", 14, accent, true);
        LinearLayout.LayoutParams sameText = new LinearLayout.LayoutParams(0, -2, 1f);
        sameText.leftMargin = dp(12);
        together.addView(sameImage, sameText);
        together.addView(icon(R.drawable.ic_arrow_right, accent), new LinearLayout.LayoutParams(dp(18), dp(18)));
        content.addView(together, new LinearLayout.LayoutParams(-1, -2));
        addSlot(content, Slot.CARD, "关于本机页面的 OTA 卡片");
        addSlot(content, Slot.UPDATE, "进入软件更新后的全屏背景");
        TextView hint = text("保存后可点右上角重启设置。\n软件更新背景需重新进入更新页面。", 12, muted, false);
        hint.setLineSpacing(dp(4), 1);
        hint.setPadding(dp(6), dp(20), dp(6), 0);
        content.addView(hint);
    }

    private void addSlot(LinearLayout parent, Slot target, String description) {
        LinearLayout section = column();
        section.setBackground(rounded(surface, 24, border));
        section.setPadding(dp(18), dp(18), dp(18), dp(16));
        LinearLayout.LayoutParams box = new LinearLayout.LayoutParams(-1, -2);
        box.topMargin = dp(14);
        parent.addView(section, box);
        Bitmap saved = Backgrounds.preview(this, target);
        String title = target == Slot.SETTINGS_PAGE ? "设置全局背景"
                : target == Slot.CARD ? "关于本机卡片" : "软件更新背景";
        LinearLayout heading = row();
        heading.addView(text(title, 18, ink, true), new LinearLayout.LayoutParams(0, -2, 1f));
        TextView status = text(saved == null ? "默认" : "已设置", 11, saved == null ? muted : accent, true);
        status.setPadding(dp(9), dp(5), dp(9), dp(5));
        status.setBackground(rounded(saved == null ? raised : accentSoft, 20, 0));
        heading.addView(status);
        section.addView(heading);

        LinearLayout body = row();
        body.setGravity(Gravity.TOP);
        LinearLayout.LayoutParams bodySpace = new LinearLayout.LayoutParams(-1, -2);
        bodySpace.topMargin = dp(16);
        section.addView(body, bodySpace);
        LinearLayout controls = column();
        body.addView(controls, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView descriptionView = text(description, 13, muted, false);
        descriptionView.setLineSpacing(dp(3), 1);
        controls.addView(descriptionView);
        TextView dimensions = text(target == Slot.CARD ? dimensions(CARD_SIZE) : "跟随本机竖屏比例", 11, muted, false);
        dimensions.setPadding(0, dp(6), 0, dp(16));
        controls.addView(dimensions);
        Button choose = button(saved == null ? "选择图片" : "更换图片", v -> pick(target == Slot.SETTINGS_PAGE
                ? PICK_SETTINGS : target == Slot.CARD ? PICK_CARD : PICK_UPDATE));
        controls.addView(choose, new LinearLayout.LayoutParams(-1, dp(44)));
        Button reset = secondaryButton("恢复默认", v -> confirmReset(target));
        reset.setEnabled(saved != null);
        LinearLayout.LayoutParams resetSpace = new LinearLayout.LayoutParams(-1, dp(42));
        resetSpace.topMargin = dp(6);
        controls.addView(reset, resetSpace);

        FrameLayout frame = new FrameLayout(this);
        frame.setBackground(rounded(raised, 14, 0));
        frame.setClipToOutline(true);
        int width = target == Slot.CARD ? 104 : 82;
        int height = target == Slot.CARD ? 104 : 180;
        LinearLayout.LayoutParams imageSize = new LinearLayout.LayoutParams(dp(width), dp(height));
        imageSize.leftMargin = dp(16);
        body.addView(frame, imageSize);
        ImageView preview = new ImageView(this);
        preview.setScaleType(ImageView.ScaleType.CENTER_CROP);
        preview.setContentDescription(title + "预览");
        if (saved != null) {
            preview.setImageBitmap(saved);
            frame.addView(preview, new FrameLayout.LayoutParams(-1, -1));
        } else {
            ImageView placeholder = icon(R.drawable.ic_image, muted);
            FrameLayout.LayoutParams centered = new FrameLayout.LayoutParams(dp(28), dp(28), Gravity.CENTER);
            frame.addView(placeholder, centered);
        }
        if (target == Slot.SETTINGS_PAGE) {
            addOpacitySlider(section, saved == null ? null : preview);
        }
    }

    private void confirmReset(Slot target) {
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("恢复默认背景")
                .setMessage(target == Slot.SETTINGS_PAGE
                        ? "将移除设置自定义背景，恢复系统原生外观。"
                        : "将移除这处自定义图片，恢复系统原生背景及动画。")
                .setNegativeButton("取消", null)
                .setPositiveButton("恢复默认", (ignored, which) -> {
                    Backgrounds.reset(this, target);
                    ScopeRestart.closeForReset(this, target);
                    showHome();
                    message("已恢复系统默认，重新进入页面查看");
                }).create();
        dialog.setOnShowListener(ignored -> {
            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawable(rounded(surface, 24, 0));
            }
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(accent);
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(muted);
        });
        dialog.show();
    }

    private void addOpacitySlider(LinearLayout section, ImageView preview) {
        View divider = new View(this);
        divider.setBackgroundColor(border);
        LinearLayout.LayoutParams dividerSpace = new LinearLayout.LayoutParams(-1, dp(1));
        dividerSpace.topMargin = dp(18);
        dividerSpace.bottomMargin = dp(16);
        section.addView(divider, dividerSpace);
        LinearLayout row = row();
        row.addView(text("图片不透明度", 13, ink, true), new LinearLayout.LayoutParams(0, -2, 1f));
        TextView label = text("", 13, accent, true);
        row.addView(label);
        section.addView(row);
        SeekBar opacity = new SeekBar(this);
        opacity.setMax(100);
        opacity.setProgress(Backgrounds.settingsOpacity(this));
        opacity.setContentDescription("设置背景图不透明度");
        opacity.setProgressTintList(ColorStateList.valueOf(accent));
        opacity.setThumbTintList(ColorStateList.valueOf(accent));
        opacity.setProgressBackgroundTintList(ColorStateList.valueOf(raised));
        opacity.setPadding(dp(5), 0, dp(5), 0);
        section.addView(opacity, new LinearLayout.LayoutParams(-1, dp(44)));
        showOpacity(label, preview, opacity.getProgress());
        opacity.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                showOpacity(label, preview, progress);
                if (fromUser) {
                    Backgrounds.setSettingsOpacity(BackgroundActivity.this, progress);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar bar) {}

            @Override
            public void onStopTrackingTouch(SeekBar bar) {}
        });
    }

    private static void showOpacity(TextView label, ImageView preview, int percent) {
        label.setText(percent + "%");
        if (preview != null) {
            preview.setImageAlpha(Math.round(percent * 255f / 100));
        }
    }

    private void pick(int request) {
        Intent gallery = new Intent(Intent.ACTION_PICK)
                .setDataAndType(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "image/*")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        // ColorOS/OnePlus Gallery exposes a native album selection activity for ACTION_PICK.
        for (String packageName : new String[] {"com.coloros.gallery3d", "com.oplus.gallery3d"}) {
            try {
                startActivityForResult(new Intent(gallery).setPackage(packageName), request);
                return;
            } catch (ActivityNotFoundException | SecurityException unavailable) {
                // Try the other OEM gallery, then the platform image picker.
            }
        }
        try {
            startActivityForResult(gallery, request);
        } catch (ActivityNotFoundException unavailable) {
            Intent fallback = Build.VERSION.SDK_INT >= 33
                    ? new Intent(MediaStore.ACTION_PICK_IMAGES).setType("image/*")
                    : new Intent(Intent.ACTION_GET_CONTENT).setType("image/*");
            try {
                startActivityForResult(fallback.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), request);
            } catch (ActivityNotFoundException | SecurityException failure) {
                message("找不到可用的相册，请确认系统相册已启用");
            }
        } catch (SecurityException failure) {
            message("无法打开相册，请确认系统相册已启用");
        }
    }

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (result != RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        if (request == PICK_CARD || request == PICK_UPDATE || request == PICK_BOTH || request == PICK_SETTINGS) {
            Slot first = request == PICK_SETTINGS ? Slot.SETTINGS_PAGE
                    : request == PICK_UPDATE ? Slot.UPDATE : Slot.CARD;
            loadSource(data.getData(), first,
                    request == PICK_BOTH, null);
        }
    }

    private void loadSource(Uri uri, Slot first, boolean both, float[] position) {
        sourceUri = uri;
        slot = first;
        cropBoth = both;
        addText(page(), "正在读取图片…", 18, true);
        worker.execute(() -> {
            try {
                Bitmap bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(getContentResolver(), uri),
                        (decoder, info, imageSource) -> {
                            int w = info.getSize().getWidth();
                            int h = info.getSize().getHeight();
                            double scale = Math.min(1, Math.min(4096d / Math.max(w, h),
                                    Math.sqrt(8_000_000d / ((double) w * h))));
                            if (scale < 1) {
                                decoder.setTargetSize(Math.max(1, (int) (w * scale)), Math.max(1, (int) (h * scale)));
                            }
                            decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
                        });
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed() || !uri.equals(sourceUri)) {
                        bitmap.recycle();
                        return;
                    }
                    source = bitmap;
                    showCrop(position);
                });
            } catch (IOException | RuntimeException failure) {
                runOnUiThread(() -> {
                    if (!isFinishing() && !isDestroyed() && uri.equals(sourceUri)) {
                        showHome();
                        message("图片读取失败");
                    }
                });
            }
        });
    }

    private void showCrop(float[] position) {
        LinearLayout page = page();
        LinearLayout header = column();
        header.setPadding(dp(20), dp(16), dp(20), dp(12));
        addText(header, cropBoth ? (slot == Slot.CARD ? "裁切卡片背景 · 1/2" : "裁切全屏背景 · 2/2") : "裁切图片", 22, true);
        outputSize = backgroundSize(slot);
        addText(header, dimensions(outputSize) + "  ·  拖动调整位置，双指缩放", 14, false);
        page.addView(header);
        editor = new CropView(this, source,
                outputSize.getWidth() / (float) outputSize.getHeight(), position);
        page.addView(editor, new LinearLayout.LayoutParams(-1, 0, 1f));
        LinearLayout actions = row();
        actions.setPadding(dp(20), dp(14), dp(20), dp(14));
        actions.addView(secondaryButton("取消", v -> onBackPressed()), new LinearLayout.LayoutParams(0, dp(48), 1));
        LinearLayout.LayoutParams centerSpace = new LinearLayout.LayoutParams(0, dp(48), 1);
        centerSpace.leftMargin = dp(8);
        actions.addView(secondaryButton("居中", v -> editor.center()), centerSpace);
        Button save = button(cropBoth && slot == Slot.CARD ? "保存并继续" : "保存", null);
        save.setOnClickListener(v -> saveCrop(save));
        LinearLayout.LayoutParams saveSpace = new LinearLayout.LayoutParams(0, dp(48), 1.4f);
        saveSpace.leftMargin = dp(8);
        actions.addView(save, saveSpace);
        page.addView(actions);
    }

    private void saveCrop(Button button) {
        Slot target = slot;
        Bitmap result = editor.render(outputSize.getWidth(), outputSize.getHeight());
        if (result == null) {
            return;
        }
        saving = true;
        button.setEnabled(false);
        worker.execute(() -> {
            boolean saved;
            try {
                Backgrounds.save(this, target, result);
                saved = true;
            } catch (IOException | RuntimeException failure) {
                saved = false;
            } finally {
                result.recycle();
            }
            boolean succeeded = saved;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                saving = false;
                if (!succeeded) {
                    message("保存失败，请重试");
                    button.setEnabled(true);
                } else if (cropBoth && target == Slot.CARD) {
                    slot = Slot.UPDATE;
                    showCrop(null);
                } else {
                    showHome();
                    message("背景已保存");
                }
            });
        });
    }

    private LinearLayout page() {
        LinearLayout root = column();
        root.setBackgroundColor(background);
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                Insets safe = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                view.setPadding(safe.left, safe.top, safe.right, safe.bottom);
            } else {
                view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                        insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            }
            return insets;
        });
        setContentView(root);
        root.requestApplyInsets();
        return root;
    }

    private Size backgroundSize(Slot target) {
        if (target == Slot.CARD) {
            return CARD_SIZE;
        }
        int width;
        int height;
        if (Build.VERSION.SDK_INT >= 30) {
            Rect bounds = getWindowManager().getMaximumWindowMetrics().getBounds();
            width = bounds.width();
            height = bounds.height();
        } else {
            DisplayMetrics metrics = new DisplayMetrics();
            getWindowManager().getDefaultDisplay().getRealMetrics(metrics);
            width = metrics.widthPixels;
            height = metrics.heightPixels;
        }
        return new Size(Math.min(width, height), Math.max(width, height));
    }

    private static String dimensions(Size size) {
        return size.getWidth() + " × " + size.getHeight();
    }

    private LinearLayout column() {
        LinearLayout view = new LinearLayout(this);
        view.setOrientation(LinearLayout.VERTICAL);
        return view;
    }

    private LinearLayout row() {
        LinearLayout view = new LinearLayout(this);
        view.setOrientation(LinearLayout.HORIZONTAL);
        view.setGravity(Gravity.CENTER_VERTICAL);
        return view;
    }

    private void configurePalette() {
        boolean night = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
        background = night ? 0xff101119 : 0xfff5f5fa;
        surface = night ? 0xff1b1d29 : 0xffffffff;
        raised = night ? 0xff252837 : 0xffeeeff7;
        ink = night ? 0xfff2f1f9 : 0xff202238;
        muted = night ? 0xff9ca2b9 : 0xff7c8192;
        accent = night ? 0xffbbb5ff : 0xff6b66d9;
        accentSoft = night ? 0xff302e49 : 0xffecebfe;
        accentInk = night ? 0xff211e35 : 0xffffffff;
        border = night ? 0xff2d3040 : 0xffe6e7ef;
    }

    private LinearLayout homeToolbar() {
        LinearLayout toolbar = row();
        toolbar.setPadding(dp(20), dp(12), dp(20), dp(10));
        ImageView avatar = new ImageView(this);
        avatar.setImageResource(R.mipmap.ic_launcher);
        avatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
        avatar.setBackground(rounded(raised, 14, 0));
        avatar.setClipToOutline(true);
        avatar.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        toolbar.addView(avatar, new LinearLayout.LayoutParams(dp(44), dp(44)));
        LinearLayout names = column();
        names.addView(text("绀羽", 23, ink, true));
        TextView caption = text("外观与背景", 11, muted, false);
        caption.setPadding(0, dp(2), 0, 0);
        names.addView(caption);
        LinearLayout.LayoutParams nameSpace = new LinearLayout.LayoutParams(0, -2, 1);
        nameSpace.leftMargin = dp(12);
        toolbar.addView(names, nameSpace);
        restartButton = icon(R.drawable.ic_rotate_cw, accent);
        restartButton.setPadding(dp(13), dp(13), dp(13), dp(13));
        restartButton.setBackground(touchBackground(accentSoft, 16, 0));
        restartButton.setContentDescription("重启设置");
        restartButton.setTooltipText("重启设置，使修改生效");
        restartButton.setFocusable(true);
        restartButton.setEnabled(!restarting);
        restartButton.setOnClickListener(v -> restartSettings());
        toolbar.addView(restartButton, new LinearLayout.LayoutParams(dp(48), dp(48)));
        return toolbar;
    }

    private void restartSettings() {
        if (restarting) {
            return;
        }
        restarting = true;
        ImageView clicked = restartButton;
        clicked.setEnabled(false);
        clicked.setAlpha(0.5f);
        clicked.animate().rotationBy(360).setDuration(650).start();
        Runnable timeout = () -> {
            finishRestartButton();
            message("设置未响应，请确认模块已启用并勾选设置作用域");
        };
        main.postDelayed(timeout, 8000);
        Intent request = new Intent(ScopeRestart.ACTION).setPackage(Backgrounds.SETTINGS)
                .addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
        try {
            sendOrderedBroadcast(request, null, new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    main.removeCallbacks(timeout);
                    boolean received = getResultCode() == RESULT_OK;
                    // All Settings receivers have acknowledged before this final callback.
                    // Allow their delayed exits to finish before opening a fresh Settings.
                    main.postDelayed(() -> {
                        finishRestartButton();
                        try {
                            startActivity(new Intent(Settings.ACTION_SETTINGS));
                            message(received ? "设置已重启" : "设置已打开");
                        } catch (RuntimeException failure) {
                            message("无法打开设置，请手动打开");
                        }
                    }, received ? 500 : 0);
                }
            }, main, RESULT_CANCELED, null, null);
        } catch (RuntimeException failure) {
            main.removeCallbacks(timeout);
            finishRestartButton();
            message("重启失败，请确认模块作用域已启用");
        }
    }

    private void finishRestartButton() {
        restarting = false;
        if (restartButton != null) {
            restartButton.setEnabled(true);
            restartButton.setAlpha(1f);
        }
    }

    private ImageView icon(int resource, int color) {
        ImageView view = new ImageView(this);
        view.setImageResource(resource);
        view.setColorFilter(color);
        return view;
    }

    private GradientDrawable rounded(int color, int radius, int stroke) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        if (stroke != 0) {
            drawable.setStroke(dp(1), stroke);
        }
        return drawable;
    }

    private RippleDrawable touchBackground(int color, int radius, int stroke) {
        return new RippleDrawable(ColorStateList.valueOf(0x286b66d9),
                rounded(color, radius, stroke), rounded(0xffffffff, radius, 0));
    }

    private TextView text(String value, int size, int color, boolean medium) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setIncludeFontPadding(false);
        if (medium) {
            view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        }
        return view;
    }

    private void addText(LinearLayout parent, String label, int size, boolean heading) {
        TextView text = text(label, size, heading ? ink : muted, heading);
        text.setPadding(0, dp(4), 0, dp(4));
        parent.addView(text);
    }

    private Button button(String label, View.OnClickListener click) {
        return styledButton(label, click, true);
    }

    private Button secondaryButton(String label, View.OnClickListener click) {
        return styledButton(label, click, false);
    }

    private Button styledButton(String label, View.OnClickListener click, boolean primary) {
        Button view = new Button(this);
        view.setText(label);
        view.setAllCaps(false);
        view.setTextSize(14);
        view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        view.setTextColor(new ColorStateList(new int[][] {
                new int[] {-android.R.attr.state_enabled}, new int[] {}
        }, new int[] {muted, primary ? accentInk : muted}));
        view.setBackgroundTintList(null);
        view.setBackground(touchBackground(primary ? accent : raised, 13, 0));
        view.setStateListAnimator(null);
        view.setElevation(0);
        view.setMinHeight(0);
        view.setMinimumHeight(0);
        view.setMinWidth(0);
        view.setMinimumWidth(0);
        view.setPadding(dp(12), 0, dp(12), 0);
        view.setIncludeFontPadding(false);
        view.setOnClickListener(click);
        return view;
    }

    private void message(String text) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
