package com.android.example.cameraxbeta06obejctdetectionjava;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.YuvImage;
import android.media.Image;

import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.ImageProxy;

import org.tensorflow.lite.Interpreter;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class ImageAnalyzerTensorflow extends AppCompatActivity {

    private String assetModelName;
    private String assetLabelName;
    private boolean isQuant = true;

    private final Interpreter.Options tfLiteOptions = new Interpreter.Options();
    private Interpreter tfLite;
    private List<String> labelList;
    private ByteBuffer imgData = null;

    private int[] intValues;

    // Only return this many results.
    private static final int NUM_DETECTIONS = 10;
    // outputLocations: array of shape [Batchsize, NUM_DETECTIONS,4]
    // contains the location of detected boxes
    private float[][][] outputLocations;
    // outputClasses: array of shape [Batchsize, NUM_DETECTIONS]
    // contains the classes of detected boxes
    private float[][] outputClasses;
    // outputScores: array of shape [Batchsize, NUM_DETECTIONS]
    // contains the scores of detected boxes
    private float[][] outputScores;
    // numDetections: array of shape [Batchsize]
    // contains the number of detected boxes
    private float[] numDetections;

    //depends on size model
    private int DIM_IMG_SIZE = 300;
    private int DIM_PIXEL_SIZE = 3;

    private float IMAGE_MEAN = 128.0f;
    private float IMAGE_STD = 128.0f;

    private double MIN_CONFIDENCE = 0.6;

    List<String> detectLabel = new ArrayList<String>();
    List<Float> detectConfidence = new ArrayList<Float>();
    List<RectF> detectLocation = new ArrayList<RectF>();

    private Context mContext;

    public ImageAnalyzerTensorflow(Context context){
        mContext = context;

    }

    public List<RectF> getDetectLocation() {return detectLocation;}
    public List<String> getDetectLabel() {return detectLabel;}
    public List<Float> getDetectConfidence() {return detectConfidence;}

   public void analyzeImage(ImageProxy imageProxy) {

        if (isQuant) {
            assetModelName = "coco_ssd_mobilenet_v1_1.0_quant.tflite";
            assetLabelName = "coco_ssd_mobilenet_v1_1.0_quant.txt";
            imgData = ByteBuffer.allocateDirect(DIM_IMG_SIZE * DIM_IMG_SIZE * DIM_PIXEL_SIZE);
        } else {
            //insert name of float model if you want to use it
            assetModelName = "";
            assetLabelName = "";
            imgData = ByteBuffer.allocateDirect(4 * DIM_IMG_SIZE * DIM_IMG_SIZE * DIM_PIXEL_SIZE);
        }

        imgData.order(ByteOrder.nativeOrder());
        intValues = new int[DIM_IMG_SIZE * DIM_IMG_SIZE];

        try {
            tfLite = new Interpreter(loadModelFile(), tfLiteOptions);
            labelList = loadLabelList();
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        @SuppressLint("UnsafeExperimentalUsageError")
        Bitmap bitmap_orig = toBitmap(imageProxy.getImage());
        Bitmap bitmap = getResizedBitmap(bitmap_orig, DIM_IMG_SIZE, DIM_IMG_SIZE);
        convertBitmapToByteBuffer(bitmap);

        outputLocations = new float[1][NUM_DETECTIONS][4];
        outputClasses = new float[1][NUM_DETECTIONS];
        outputScores = new float[1][NUM_DETECTIONS];
        numDetections = new float[1];

        Object[] inputArray = {imgData};
        Map<Integer, Object> outputMap = new HashMap<>();
        outputMap.put(0, outputLocations);
        outputMap.put(1, outputClasses);
        outputMap.put(2, outputScores);
        outputMap.put(3, numDetections);

        // run tfLite
        tfLite.runForMultipleInputsOutputs(inputArray, outputMap);

        readOutput();
    }

    private void readOutput() {
        int numDetectionsOutput = Math.min(NUM_DETECTIONS, (int) numDetections[0]); // cast from float to integer, use min for safety

        final ArrayList<Classifier.Recognition> recognitions = new ArrayList<>(numDetectionsOutput);
        for (int i = 0; i < numDetectionsOutput; ++i) {
            final RectF detection =
                    new RectF(
                            outputLocations[0][i][1] * DIM_IMG_SIZE,
                            outputLocations[0][i][0] * DIM_IMG_SIZE,
                            outputLocations[0][i][3] * DIM_IMG_SIZE,
                            outputLocations[0][i][2] * DIM_IMG_SIZE);
            // SSD Mobilenet V1 Model assumes class 0 is background class
            // in label file and class labels start from 1 to number_of_classes+1,
            // while outputClasses correspond to class index from 0 to number_of_classes
            int labelOffset = 1;
            recognitions.add(
                    new Classifier.Recognition(
                            "" + i,
                            labelList.get((int) outputClasses[0][i] + labelOffset),
                            outputScores[0][i],
                            detection));
        }

        detectLocation.clear();
        detectLabel.clear();
        detectConfidence.clear();
        for (final Classifier.Recognition result : recognitions) {
            final RectF location = result.getLocation();
            if (location != null && result.getConfidence() >= MIN_CONFIDENCE) {

                detectLocation.add(result.getLocation());
                detectLabel.add(result.getTitle());
                detectConfidence.add(result.getConfidence());
            }
        }
    }

    private void convertBitmapToByteBuffer(Bitmap bitmap) {
        if (imgData == null) {
            return;
        }
        imgData.rewind();
        bitmap.getPixels(intValues, 0, bitmap.getWidth(), 0,0,bitmap.getWidth(), bitmap.getHeight());
        int pixel = 0;
        for (int i = 0; i < DIM_IMG_SIZE; i++){
            for (int j = 0; j < DIM_IMG_SIZE; j++){
                final int val = intValues[pixel++];
                if (isQuant){
                    imgData.put((byte) ((val >> 16) & 0xFF));
                    imgData.put((byte) ((val >> 8) & 0xFF));
                    imgData.put((byte) (val & 0xFF));
                } else {
                    imgData.putFloat((((val >> 16) & 0xFF)-IMAGE_MEAN)/IMAGE_STD);
                    imgData.putFloat((((val >> 8) & 0xFF)-IMAGE_MEAN)/IMAGE_STD);
                    imgData.putFloat(((val & 0xFF)-IMAGE_MEAN)/IMAGE_STD);
                }
            }
        }

    }

    private Bitmap getResizedBitmap(Bitmap bitmap_orig, int dim_img_size_x, int dim_img_size_y) {
        int width = bitmap_orig.getWidth();
        int height = bitmap_orig.getHeight();

        float scaleWidth =((float) dim_img_size_x) / width;
        float scaleHeigth = ((float) dim_img_size_y) / height;
        Matrix matrix = new Matrix();
        matrix.postScale(scaleWidth, scaleHeigth);

        int orientation = mContext.getResources().getConfiguration().orientation;
        if (orientation == Configuration.ORIENTATION_PORTRAIT) {
            matrix.postRotate(90);
        } else {
            matrix.postRotate(0);
        }

        Bitmap resizedBitmap = Bitmap.createBitmap(bitmap_orig, 0, 0, width, height, matrix, false);

        return resizedBitmap;
    }

    private static Bitmap toBitmap(Image image) {
        Image.Plane[] planes = image.getPlanes();
        ByteBuffer yBuffer = planes[0].getBuffer();
        ByteBuffer uBuffer = planes[1].getBuffer();
        ByteBuffer vBuffer = planes[2].getBuffer();

        int ySize = yBuffer.remaining();
        int uSize = uBuffer.remaining();
        int vSize = vBuffer.remaining();

        byte[] nv21 = new byte[ySize + uSize + vSize];
        //U and V are swapped
        yBuffer.get(nv21, 0, ySize);
        vBuffer.get(nv21, ySize, vSize);
        uBuffer.get(nv21, ySize + vSize, uSize);

        YuvImage yuvImage = new YuvImage(nv21, ImageFormat.NV21, image.getWidth(), image.getHeight(), null);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        yuvImage.compressToJpeg(new Rect(0, 0, yuvImage.getWidth(), yuvImage.getHeight()), 75, out);

        byte[] imageBytes = out.toByteArray();
        return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
    }

    //load tflite graph from file
    private MappedByteBuffer loadModelFile() throws IOException {

        AssetFileDescriptor fileDescriptor = mContext.getAssets().openFd(assetModelName);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());

        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }
    private List<String> loadLabelList() throws IOException {
        List<String> labelList = new ArrayList<String>();
        BufferedReader reader =
                new BufferedReader(new InputStreamReader(mContext.getAssets().open(assetLabelName)));

        String line;
        while ((line = reader.readLine()) != null) {
            labelList.add(line);
        }
        reader.close();
        return labelList;
    }
}



