/* Native cursor drawn above TextureView so relative input remains visible during video playback. */
package dev.applescreen.prototype;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

/** Draws a high-contrast local pointer without intercepting touches intended for the WebView. */
final class PointerOverlay extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path arrow = new Path();
    private final float density;
    private float x, y;
    private boolean shown;
    PointerOverlay(Context context) {
        super(context);
        setClickable(false);
        density = getResources().getDisplayMetrics().density;
        arrow.moveTo(0, 0); arrow.lineTo(0, 19); arrow.lineTo(5, 14);
        arrow.lineTo(10, 23); arrow.lineTo(14, 21); arrow.lineTo(9, 12);
        arrow.lineTo(18, 12); arrow.close();
    }
    void position(float x, float y, boolean shown) {
        this.x = x; this.y = y; this.shown = shown; invalidate();
    }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (!shown) return;
        canvas.save(); canvas.translate(x, y); canvas.scale(density, density);
        paint.setStyle(Paint.Style.FILL); paint.setColor(0xffffffff); canvas.drawPath(arrow, paint);
        paint.setStyle(Paint.Style.STROKE); paint.setColor(0xff10151d); paint.setStrokeWidth(1.2f); canvas.drawPath(arrow, paint);
        canvas.restore();
    }
}
