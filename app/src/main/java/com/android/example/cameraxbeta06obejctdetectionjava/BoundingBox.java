package com.android.example.cameraxbeta06obejctdetectionjava;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.widget.ImageView;

import java.util.List;

public class BoundingBox {

    private ImageView boundingBoxContainer;
    private int ContainerWidth;
    private int ContainerHeight;
    private float scaleW;
    private float scaleH;


    public BoundingBox(Activity activity, Context context) {
        boundingBoxContainer = activity.findViewById(R.id.boundingBox);
        ContainerWidth = boundingBoxContainer.getWidth();
        ContainerHeight = boundingBoxContainer.getHeight();

        int orientation = context.getResources().getConfiguration().orientation;

        if (orientation == Configuration.ORIENTATION_PORTRAIT) {
            scaleW = 4.8f;
            scaleH = 9.63f;
        } else {
            scaleW = 9.63f;
            scaleH = 4.8f;
        }
    }
    public void noBoundingBox(){
        boundingBoxContainer.setImageBitmap(null);
    }

    public void drawSingleBox(String label, Float confidence, Rect location) {

        Bitmap bitmap = Bitmap.createBitmap(
                ContainerWidth,
                ContainerHeight,
                Bitmap.Config.ARGB_8888);

        Canvas canvas = new Canvas(bitmap);
        Paint paintRect = new Paint();
        paintRect.setStyle(Paint.Style.STROKE);
        paintRect.setColor(Color.GREEN);
        paintRect.setStrokeWidth(10);

        Paint paintText = new Paint();
        paintText.setColor(Color.GREEN);
        paintText.setStrokeWidth(10);
        paintText.setTextSize(100f);

        canvas.drawRoundRect(
                location.left,
                location.top,
                location.right,
                location.bottom,
                50.0f,
                50.0f,
                paintRect);
        canvas.drawText( label + ": " + confidence,
                location.left,
                location.top,
                paintText);

        boundingBoxContainer.setImageBitmap(bitmap);
    }

    public void drawMultiBoxes(List<String> label, List<Float> confidence, List<RectF> location, int numberOfDetectedObejcts) {

        Bitmap bitmap = Bitmap.createBitmap(
                ContainerWidth,
                ContainerHeight,
                Bitmap.Config.ARGB_8888);

        Canvas canvas = new Canvas(bitmap);
        Paint paintRect = new Paint();
        paintRect.setStyle(Paint.Style.STROKE);
        paintRect.setStrokeWidth(10);

        Paint paintText = new Paint();
        paintText.setStrokeWidth(10);
        paintText.setTextSize(100f);

        if (location.size()<numberOfDetectedObejcts){ numberOfDetectedObejcts =location.size();}

        for (int i = 0; i < numberOfDetectedObejcts; i++) {
            switch (i){
                case 0:
                    paintRect.setColor(Color.GREEN);
                    paintText.setColor(Color.GREEN);
                    break;
                case 1:
                    paintRect.setColor(Color.BLUE);
                    paintText.setColor(Color.BLUE);
                    break;
                case 2:
                    paintRect.setColor(Color.YELLOW);
                    paintText.setColor(Color.YELLOW);
                    break;
                default:
                    paintRect.setColor(Color.RED);
                    paintText.setColor(Color.RED);
            }

        canvas.drawRoundRect(
                location.get(i).left * scaleW,
                location.get(i).top * scaleH,
                location.get(i).right * scaleW,
                location.get(i).bottom * scaleH,
                50.0f,
                50.0f,
                paintRect);
        canvas.drawText( label.get(i) + ": " + Math.round(confidence.get(i)*100) + "%",
                location.get(i).left * scaleW,
                location.get(i).top * scaleH,
                paintText);
        }
        boundingBoxContainer.setImageBitmap(bitmap);
    }
}
