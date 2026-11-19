package com.dawson.trailmate;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.location.Location;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

public class RoutePreviewView extends View {

    private List<Location> points = new ArrayList<>();

    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint routePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint startPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint finishPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public RoutePreviewView(Context context) {
        super(context);
        initialize();
    }

    public RoutePreviewView(Context context, AttributeSet attributes) {
        super(context, attributes);
        initialize();
    }

    public RoutePreviewView(
            Context context,
            AttributeSet attributes,
            int defaultStyle
    ) {
        super(context, attributes, defaultStyle);
        initialize();
    }

    private void initialize() {
        gridPaint.setColor(Color.rgb(218, 228, 213));
        gridPaint.setStrokeWidth(2f);

        routePaint.setColor(Color.rgb(36, 84, 59));
        routePaint.setStyle(Paint.Style.STROKE);
        routePaint.setStrokeWidth(8f);
        routePaint.setStrokeCap(Paint.Cap.ROUND);
        routePaint.setStrokeJoin(Paint.Join.ROUND);

        startPaint.setColor(Color.rgb(46, 125, 50));
        startPaint.setStyle(Paint.Style.FILL);

        finishPaint.setColor(Color.rgb(198, 40, 40));
        finishPaint.setStyle(Paint.Style.FILL);

        textPaint.setColor(Color.rgb(83, 101, 85));
        textPaint.setTextSize(30f);
        textPaint.setTextAlign(Paint.Align.CENTER);
    }

    public void setPoints(List<Location> locations) {
        if (locations == null) {
            points = new ArrayList<>();
        } else {
            points = new ArrayList<>(locations);
        }

        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawColor(Color.rgb(241, 246, 238));

        float padding = 40f;
        float left = padding;
        float top = padding;
        float right = getWidth() - padding;
        float bottom = getHeight() - padding;

        if (right <= left || bottom <= top) {
            return;
        }

        for (int i = 1; i <= 3; i++) {
            float x = left + (right - left) * i / 4f;
            float y = top + (bottom - top) * i / 4f;
            canvas.drawLine(x, top, x, bottom, gridPaint);
            canvas.drawLine(left, y, right, y, gridPaint);
        }

        if (points.isEmpty()) {
            canvas.drawText(
                    "No GPS route recorded",
                    getWidth() / 2f,
                    getHeight() / 2f,
                    textPaint
            );
            return;
        }

        double minLatitude = Double.MAX_VALUE;
        double maxLatitude = -Double.MAX_VALUE;
        double minLongitude = Double.MAX_VALUE;
        double maxLongitude = -Double.MAX_VALUE;

        for (Location point : points) {
            minLatitude = Math.min(minLatitude, point.getLatitude());
            maxLatitude = Math.max(maxLatitude, point.getLatitude());
            minLongitude = Math.min(minLongitude, point.getLongitude());
            maxLongitude = Math.max(maxLongitude, point.getLongitude());
        }

        if (maxLatitude - minLatitude < 0.000001) {
            minLatitude -= 0.0005;
            maxLatitude += 0.0005;
        }

        if (maxLongitude - minLongitude < 0.000001) {
            minLongitude -= 0.0005;
            maxLongitude += 0.0005;
        }

        float availableWidth = right - left;
        float availableHeight = bottom - top;
        double latitudeSpan = maxLatitude - minLatitude;
        double longitudeSpan = maxLongitude - minLongitude;

        Path routePath = new Path();
        float firstX = 0f;
        float firstY = 0f;
        float lastX = 0f;
        float lastY = 0f;

        for (int i = 0; i < points.size(); i++) {
            Location point = points.get(i);

            float x = left + (float) (
                    (point.getLongitude() - minLongitude)
                            / longitudeSpan
                            * availableWidth
            );

            float y = top + (float) (
                    (maxLatitude - point.getLatitude())
                            / latitudeSpan
                            * availableHeight
            );

            if (i == 0) {
                firstX = x;
                firstY = y;
                routePath.moveTo(x, y);
            } else {
                routePath.lineTo(x, y);
            }

            lastX = x;
            lastY = y;
        }

        canvas.drawPath(routePath, routePaint);
        canvas.drawCircle(firstX, firstY, 13f, startPaint);
        canvas.drawCircle(lastX, lastY, 13f, finishPaint);
    }
}