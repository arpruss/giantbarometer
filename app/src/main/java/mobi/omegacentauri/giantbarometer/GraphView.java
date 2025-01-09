package mobi.omegacentauri.giantbarometer;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.View;

import java.util.List;

public class GraphView extends View {
    private final Paint backPaint;
    private final float labelX;
    RectF bounds = new RectF();
    float lineSpacing = 1.05f;
    static final double graphPixelMin = 0.1;
    static final double strokeThicknessDP = 2;
    float letterSpacing = 1f;
    Paint paint;
    Paint basePaint;

    Paint labelPaint;
    Paint hLinePaint;
    List<Analysis.TimedDatum<Double>> data;
    String[] lines;
    float yOffsets[];
    float xOffsets[];
    double valueScale = 1.;
    static final float BASE_FONT_SIZE = 50f;
    private float maxAspect = 1f;

    float nudge;

    public GraphView(Context context, AttributeSet attrs) {
        super(context, attrs);

        backPaint = new Paint();
        backPaint.setColor(Color.BLACK);
        
        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        DisplayMetrics dm = getResources().getDisplayMetrics() ;
        float strokeWidth = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, (float) strokeThicknessDP, dm);
        paint.setStrokeWidth(strokeWidth);

        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(Color.WHITE);

        labelPaint = new Paint();
        labelPaint.setColor(Color.DKGRAY);
        labelPaint.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, (float) 20, dm));

        hLinePaint = new Paint();
        hLinePaint.setColor(labelPaint.getColor());
        nudge = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, (float) 1, dm);
        hLinePaint.setStrokeWidth(nudge);

        labelX = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, (float) 20, dm);

        basePaint = new Paint();
        basePaint.setTextSize(BASE_FONT_SIZE);

        setData(null, true);
    }

    public void setValueScale(double s) {
        valueScale = s;
    }


    public void setData(List<Analysis.TimedDatum<Double>> d, boolean force) {
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
        double yEnd = Double.NEGATIVE_INFINITY;
        for (Analysis.TimedDatum<Double> d : data) {
            double y = d.value * valueScale;
            if (y < yStart)
                yStart = y;
            if (yEnd < y)
                yEnd = y;
        }
        yStart = Math.floor(yStart);
        yEnd = Math.ceil(yEnd);
        if (yEnd == yStart)
            yEnd = yStart + 1;
        yScale = h / (yEnd - yStart);

        double prevX = 0;
        double prevY = 0;
        boolean first = true;
        for(Analysis.TimedDatum<Double> d : data) {
            double x = (d.time - xStart) * xScale;
            double y = (d.value * valueScale - yStart) * yScale;
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
        Rect bounds = new Rect();
        labelPaint.getTextBounds("0", 0,1,bounds);
        canvas.drawLine(0,0, (float) (w-1),0,hLinePaint);
        canvas.drawLine(0, (float) (h-1), (float) (w-1), (float) (h-1),hLinePaint);
        canvas.drawText(""+(int)yEnd, (float) labelX, (float) bounds.height()+2*nudge, labelPaint);
        canvas.drawText(""+(int)yStart, (float) labelX, (float) h-2*nudge, labelPaint);
    }

}
