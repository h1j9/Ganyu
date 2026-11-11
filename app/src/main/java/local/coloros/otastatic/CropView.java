package local.coloros.otastatic;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

final class CropView extends View {
    private final Bitmap source;
    private final float aspect;
    private final RectF frame = new RectF();
    private final RectF imageBounds = new RectF();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final ScaleGestureDetector gestures;
    private float zoom = 1f;
    private float left;
    private float top;
    private float previousX;
    private float previousY;
    private float[] position;

    CropView(Context context, Bitmap source, float aspect, float[] position) {
        super(context);
        this.source = source;
        this.aspect = aspect;
        this.position = position;
        setContentDescription("裁切预览。拖动调整位置，双指缩放。");
        gestures = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                float oldScale = scale();
                zoom = clamp(zoom * detector.getScaleFactor(), 1f, 8f);
                float ratio = scale() / oldScale;
                float x = detector.getFocusX();
                float y = detector.getFocusY();
                left = x - (x - left) * ratio;
                top = y - (y - top) * ratio;
                constrain();
                invalidate();
                return true;
            }
        });
        gestures.setQuickScaleEnabled(false);
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        float[] saved = frame.isEmpty() ? position : getPosition();
        float padding = 16 * getResources().getDisplayMetrics().density;
        float w = Math.max(1, Math.min(width - 2 * padding, (height - 2 * padding) * aspect));
        float h = w / aspect;
        frame.set((width - w) / 2, (height - h) / 2, (width + w) / 2, (height + h) / 2);
        position = null;
        setPosition(saved);
    }

    float[] getPosition() {
        if (frame.isEmpty()) {
            return position;
        }
        return new float[] { zoom, (frame.centerX() - left) / (scale() * source.getWidth()),
                (frame.centerY() - top) / (scale() * source.getHeight()) };
    }

    void center() {
        setPosition(null);
        invalidate();
    }

    private void setPosition(float[] saved) {
        zoom = saved == null ? 1f : saved[0];
        float centerX = saved == null ? 0.5f : saved[1];
        float centerY = saved == null ? 0.5f : saved[2];
        left = frame.centerX() - centerX * source.getWidth() * scale();
        top = frame.centerY() - centerY * source.getHeight() * scale();
        constrain();
    }

    private float scale() {
        return zoom * Math.max(frame.width() / source.getWidth(), frame.height() / source.getHeight());
    }

    private void constrain() {
        left = clamp(left, frame.right - source.getWidth() * scale(), frame.left);
        top = clamp(top, frame.bottom - source.getHeight() * scale(), frame.top);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(value, max));
    }

    private void drawImage(Canvas canvas) {
        imageBounds.set(left, top, left + source.getWidth() * scale(), top + source.getHeight() * scale());
        paint.setColor(Color.WHITE);
        canvas.drawBitmap(source, null, imageBounds, paint);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (frame.isEmpty()) {
            return;
        }
        canvas.drawColor(Color.BLACK);
        drawImage(canvas);
        canvas.save();
        canvas.clipOutRect(frame);
        canvas.drawColor(0xaa101218);
        canvas.restore();
        paint.setColor(0x66ffffff);
        paint.setStrokeWidth(1f);
        for (int part = 1; part < 3; part++) {
            float x = frame.left + frame.width() * part / 3;
            float y = frame.top + frame.height() * part / 3;
            canvas.drawLine(x, frame.top, x, frame.bottom, paint);
            canvas.drawLine(frame.left, y, frame.right, y, paint);
        }
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2f);
        paint.setColor(Color.WHITE);
        canvas.drawRect(frame, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (frame.isEmpty()) {
            return false;
        }
        gestures.onTouchEvent(event);
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                previousX = event.getX();
                previousY = event.getY();
                return true;
            case MotionEvent.ACTION_MOVE:
                if (event.getPointerCount() == 1 && !gestures.isInProgress()) {
                    float x = event.getX();
                    float y = event.getY();
                    left += x - previousX;
                    top += y - previousY;
                    previousX = x;
                    previousY = y;
                    constrain();
                    invalidate();
                }
                return true;
            case MotionEvent.ACTION_POINTER_UP:
                int remaining = event.getActionIndex() == 0 ? 1 : 0;
                previousX = event.getX(remaining);
                previousY = event.getY(remaining);
                return true;
            case MotionEvent.ACTION_UP:
                performClick();
                return true;
            default:
                return true;
        }
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    Bitmap render(int width, int height) {
        if (frame.isEmpty()) {
            return null;
        }
        Bitmap cropped = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(cropped);
        canvas.drawColor(Color.BLACK);
        canvas.scale(width / frame.width(), height / frame.height());
        canvas.translate(-frame.left, -frame.top);
        drawImage(canvas);
        return cropped;
    }
}
