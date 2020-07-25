package com.android.example.cameraxbeta06obejctdetectionjava;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraControl;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.graphics.RectF;
import android.media.MediaScannerConnection;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.google.common.util.concurrent.ListenableFuture;

import java.io.File;
import java.util.List;
import java.util.concurrent.ExecutionException;

public class MainActivity extends AppCompatActivity {

    //Permission
    private int REQUEST_CODE_PERMISSIONS = 101;
    private final String[] REQUIRED_PERMISSIONS = new String[]{
            "android.permission.CAMERA",
            "android.permission.WRITE_EXTERNAL_STORAGE",
            "android.permission.READ_EXTERNAL_STORAGE"};

    //Button and views
    private PreviewView mPreviewView;
    private Button mTakePictureButton;
    private SeekBar mZoomSlider;
    private TextView mZoomTextView;
    private ToggleButton mSwitchCameraButton;

    //variables to store config states
    private String mPhotoPath;
    private int mSwitchCameraState = CameraSelector.LENS_FACING_BACK;
    private float mZoomState = 0.0f;

    //Camera variable for image capture
    private ImageCapture mImageCapture;

    //number of shown detected objects
    private int NUMBER_DETECTED_OBJECTS = 2;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //Display Always On
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        //hide action und status bar, transparent navigation bar
        getSupportActionBar().hide();
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION);

        mPreviewView = findViewById(R.id.viewFinder);
        mTakePictureButton = findViewById(R.id.takePictureButton);
        mZoomSlider = findViewById(R.id.zoomSliderSeekBar);
        mSwitchCameraButton = findViewById(R.id.switchCameraButton);
        mZoomTextView = findViewById(R.id.zoomTextView);

        if (allPermissionsGranted()) {
            startCamera(); //start camera if permission has been granted by user
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS);
        }

        // Setup the listener for take photos and switch camera
        mTakePictureButton.setOnClickListener(view -> takePhoto());
        mSwitchCameraButton.setOnClickListener(view -> switchCamera());

    }
    //start camera
    private void startCamera() {
        ListenableFuture cameraProviderFuture = ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                // Camera provider is now guaranteed to be available
                ProcessCameraProvider cameraProvider = (ProcessCameraProvider) cameraProviderFuture.get();
                // Set up the view finder use case to display camera preview
                Preview preview = new Preview.Builder().build();

                CameraSelector cameraSelector = new CameraSelector.Builder()
                        .requireLensFacing(mSwitchCameraState)
                        .build();

                // Set up the capture use case to allow users to take photos
                mImageCapture = new ImageCapture.Builder()
                        .setTargetRotation(getWindowManager().getDefaultDisplay().getRotation())
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        //.setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                        .build();

                ImageAnalysis imageAnalysis =
                        new ImageAnalysis.Builder()
                                //.setTargetResolution(new Size(1280, 720))
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .build();

                ImageAnalyzerTensorflow imageAnalyzerTFLite = new ImageAnalyzerTensorflow(this);
                imageAnalysis.setAnalyzer(Runnable::run, new ImageAnalysis.Analyzer() {
                    @SuppressLint("UnsafeExperimentalUsageError")
                    @Override
                    public void analyze(@NonNull ImageProxy image) {
                        imageAnalyzerTFLite.analyzeImage(image);

                        List<RectF> location = imageAnalyzerTFLite.getDetectLocation();
                        List<String> label = imageAnalyzerTFLite.getDetectLabel();
                        List<Float> confidence = imageAnalyzerTFLite.getDetectConfidence();

                        BoundingBox boundingBox = new BoundingBox(MainActivity.this, MainActivity.this);

                        if (location.size() != 0) {
                            boundingBox.drawMultiBoxes(label, confidence, location, NUMBER_DETECTED_OBJECTS);
                        } else {
                            boundingBox.noBoundingBox();
                        }

                        //clear stream for next image
                        image.close();
                    }
                });


                // Unbind use cases before rebinding
                cameraProvider.unbindAll();
                // Bind use cases to camera
                // Attach use cases to the camera with the same lifecycle owner
                Camera camera = cameraProvider.bindToLifecycle(
                        ((LifecycleOwner) this),
                        cameraSelector,
                        preview,
                        mImageCapture,
                        imageAnalysis
                );

                //control zoom
                CameraControl cameraControl = camera.getCameraControl();
                controlZoom(cameraControl);

                preview.setSurfaceProvider(mPreviewView.createSurfaceProvider(camera.getCameraInfo()));

            } catch (InterruptedException | ExecutionException e) {
                // Currently no exceptions thrown. cameraProviderFuture.get() should
                // not block since the listener is being called, so no need to
                // handle InterruptedException.
            }
        }, ContextCompat.getMainExecutor(this));
    }

    //take a photo and save it on media storage --> add android:requestLegacyExternalStorage="true" in manifest
    private void takePhoto() {
        //Create Folder Dir
        File mImageDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "CameraX");
        boolean isDirectoryCreated = mImageDir.exists() || mImageDir.mkdirs();

        if (isDirectoryCreated) {
            // Create file with path
            File file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES) + "/CameraX", System.currentTimeMillis() + ".png");
            ImageCapture.OutputFileOptions.Builder outputFileOptionsBuilder =
                    new ImageCapture.OutputFileOptions.Builder(file);

            mImageCapture.takePicture(outputFileOptionsBuilder.build(), Runnable::run, new ImageCapture.OnImageSavedCallback() {
                @Override
                public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {

                    //scan for new image an show in gallery icon
                    MediaScannerConnection.scanFile(MainActivity.this,
                            new String[]{file.toString()}, null,
                            (path, uri) -> {
                                mPhotoPath = path;

                                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Picture saved: " + mPhotoPath,
                                        Toast.LENGTH_LONG).show());
                            });
                }
                @Override
                public void onError(@NonNull ImageCaptureException exception) {
                    exception.printStackTrace();

                }
            });
        }
    }

    //switch between front and back camera
    private void switchCamera() {
        if (!mSwitchCameraButton.isChecked()) {
            mSwitchCameraState = CameraSelector.LENS_FACING_BACK;
            startCamera();
        } else {
            mSwitchCameraState = CameraSelector.LENS_FACING_FRONT;
            startCamera();
        }
    }

    //control zoom doesn't work on emulator
    private void controlZoom(CameraControl cameraControl) {
        cameraControl.setLinearZoom(mZoomState);
        mZoomSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                mZoomState = i/ Float.parseFloat("4");
                cameraControl.setLinearZoom(mZoomState);
                mZoomTextView.setText(mZoomState * 8 + "x");
                mZoomTextView.setVisibility(View.VISIBLE);
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                mZoomTextView.postDelayed(() -> mZoomTextView.setVisibility(View.INVISIBLE), 3000);
            }
        });
    }

    //Permission for Camera and Storage
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera();
            } else {
                Toast.makeText(this, "Permissions not granted by the user.", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }
    private boolean allPermissionsGranted() {
        for (String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }
}