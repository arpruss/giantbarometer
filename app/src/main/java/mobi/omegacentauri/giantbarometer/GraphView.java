package mobi.omegacentauri.giantbarometer;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.View;

import java.util.List;

public class GraphView extends View {
    private final Paint backPaint;
    RectF bounds = new RectF();
    float lineSpacing = 1.05f;
    static final double graphPixelMin = 0.1;
    static final double strokeThicknessDP = 2;
    float letterSpacing = 1f;
    MiniFont miniFont;
    Paint paint;
    Paint basePaint;
    List<BarometerActivity.TimedDatum> data;
    String[] lines;
    float yOffsets[];
    float xOffsets[];
    float scale = 0.98f;
    GetCenter getCenterX = null;
    GetCenter getCenterY = null;
    static final float BASE_FONT_SIZE = 50f;
    private float maxAspect = 1f;

    public GraphView(Context context, AttributeSet attrs) {
        super(context, attrs);

        backPaint = new Paint();
        backPaint.setColor(Color.BLACK);
        
        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        DisplayMetrics dm = getResources().getDisplayMetrics() ;
        float strokeWidth = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, (float) strokeThicknessDP, dm);
        paint.setStrokeWidth(strokeWidth);
        miniFont = new SansBold();

        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(Color.WHITE);

        basePaint = new Paint();
        basePaint.setTextSize(BASE_FONT_SIZE);

        setData(null, true);
    }

    public void setMaxAspect(float maxAspect) {
        if (this.maxAspect != maxAspect) {
            this.maxAspect = maxAspect;
            invalidate();
        }
    }

    public void setFont(MiniFont mf) {
        this.miniFont = mf;
        invalidate();
    }

    public void setScale(float scale) {
        this.scale = scale;
        invalidate();
    }

    public void setLineSpacing(float l) {
        lineSpacing = l;
        invalidate();
    }

    public void setLetterSpacing(float ls) {
        letterSpacing = ls;
        invalidate();
    }

    public void setData(List<BarometerActivity.TimedDatum> d, boolean force) {
        data = d;
        if (force)
            invalidate();
    }

    /*
    public void measureText(RectF bounds) {
        RectF lineBounds = new RectF();
        bounds.set(0,0,0,0);

        int n = tweakedLines.length;
        for (int i = 0 ; i < n ; i++) {
            String line = tweakedLines[i];
            miniFont.getTextBounds(paint, line, 0, line.length(), lineBounds);
            yOffsets[i] = bounds.bottom - lineBounds.top;
            bounds.bottom += (int)(lineBounds.height() * (i==n-1 ? 1 : lineSpacing));
            bounds.right = Math.max(bounds.right, lineBounds.width());
        }
    }
    */

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        double w = canvas.getWidth();
        double h = canvas.getHeight();
        canvas.drawRect(0,0, canvas.getWidth(), canvas.getHeight(), backPaint);

        if (data == null || data.size() == 0)
            return;

        double xScale;
        double xStart;
        double yScale;
        double yStart;
        xStart = data.get(0).time;
        int n = data.size();
        double xEnd = data.get(n-1).time;
        if (xStart == xEnd) {
            xScale = 1;
        }
        else {
            xScale =  w / (xEnd - xStart);
        }
        yStart = Double.POSITIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        for (BarometerActivity.TimedDatum d : data) {
            if (d.value < yStart)
                yStart = d.value;
            if (maxY < d.value)
                maxY = d.value;
        }
        if (yStart < maxY) {
            yScale = h / (maxY - yStart);
        }
        else {
            yScale = 1;
        }

        double prevX = 0;
        double prevY = 0;
        boolean first = true;
        for(BarometerActivity.TimedDatum d : data) {
            double x = (d.time - xStart) * xScale;
            double y = (d.value - yStart) * yScale;
            if (!first) {
                if (x-prevX >= graphPixelMin) {
                    canvas.drawLine((float) prevX, (float) (h - 1 - prevY), (float) x, (float) (h - 1 - y), paint);
                    prevX = x;
                    prevY = y;
                }
            }
            else {
                first = false;
                prevX = x;
                prevY = y;
            }
        }
    }

    private float adjustCenter(float canvasSize, GetCenter c, float size) {
        float canvasCenter = canvasSize / 2f;
        if (c == null)
            return canvasCenter;
        float drawingCenter = c.getCenter();
        if (canvasCenter == drawingCenter)
            return canvasCenter;
        float half = size / 2f;
        if (drawingCenter < canvasCenter) {
            if (half <= drawingCenter)
                return drawingCenter;
            else
                return half;
        }
        else {
            if (drawingCenter + half <= canvasSize) {
                return drawingCenter;
            }
            else
                return canvasSize - half;
        }
    }
    
    public void setBackColor(int color) {
        backPaint.setColor(color);
    }

    public void setTextColor(int textColor) {
        if (textColor != paint.getColor()) {
            paint.setColor(textColor);
            invalidate();
        }
    }

    static interface GetCenter {
        float getCenter();
    }
}
