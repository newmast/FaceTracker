package com.google.android.gms.samples.vision.face.facetracker;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.graphics.Point;
import android.os.Bundle;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Display;
import android.view.View;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.vision.CameraSource;
import com.google.android.gms.vision.MultiProcessor;
import com.google.android.gms.vision.Tracker;
import com.google.android.gms.vision.face.Face;
import com.google.android.gms.vision.face.FaceDetector;
import com.google.android.gms.samples.vision.face.facetracker.ui.camera.CameraSourcePreview;
import com.google.android.gms.samples.vision.face.facetracker.ui.camera.GraphicOverlay;
import com.robotpajamas.blueteeth.BlueteethDevice;
import com.robotpajamas.blueteeth.BlueteethManager;
import com.robotpajamas.blueteeth.BlueteethResponse;
import com.robotpajamas.blueteeth.listeners.OnCharacteristicWriteListener;
import com.robotpajamas.blueteeth.listeners.OnConnectionChangedListener;
import com.robotpajamas.blueteeth.listeners.OnScanCompletedListener;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

public final class FaceTrackerActivity extends AppCompatActivity {
    private static final String TAG = "FaceTracker";

    private static final UUID CHARACTERISTIC_WRITE = UUID.fromString("01726f62-6f74-7061-6a61-6d61732e6361");
    private static final UUID SERVICE_TEST = UUID.fromString("00726f62-6f74-7061-6a61-6d61732e6361");

    private static int SCREEN_WIDTH;
    private static BlueteethDevice connectedDevice;

    private CameraSource mCameraSource = null;
    private CameraSourcePreview mPreview;
    private GraphicOverlay mGraphicOverlay;

    private static final int RC_HANDLE_GMS = 9001;
    private static final int RC_HANDLE_CAMERA_PERM = 2;

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        setContentView(R.layout.main);

        mPreview = (CameraSourcePreview) findViewById(R.id.preview);
        mGraphicOverlay = (GraphicOverlay) findViewById(R.id.faceOverlay);

        int rc = ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA);
        if (rc == PackageManager.PERMISSION_GRANTED) {
            createCameraSource();
        } else {
            requestCameraPermission();
        }
    }

    private void requestCameraPermission() {
        Log.w(TAG, "Camera permission is not granted. Requesting permission");

        final String[] permissions = new String[] { Manifest.permission.CAMERA };

        if (!ActivityCompat.shouldShowRequestPermissionRationale(this,
                Manifest.permission.CAMERA)) {
            ActivityCompat.requestPermissions(this, permissions, RC_HANDLE_CAMERA_PERM);
            return;
        }

        final Activity thisActivity = this;

        View.OnClickListener listener = new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                ActivityCompat.requestPermissions(thisActivity, permissions,
                        RC_HANDLE_CAMERA_PERM);
            }
        };

        Snackbar.make(mGraphicOverlay, R.string.permission_camera_rationale,
                Snackbar.LENGTH_INDEFINITE)
                .setAction(R.string.ok, listener)
                .show();
    }

    private void createCameraSource() {

        Context context = getApplicationContext();
        FaceDetector detector = new FaceDetector.Builder(context)
                .setClassificationType(FaceDetector.FAST_MODE)
                .build();

        detector.setProcessor(
                new MultiProcessor.Builder<>(new GraphicFaceTrackerFactory())
                        .build());

        Display display = getWindowManager().getDefaultDisplay();

        Point dimensions = new Point();
        display.getSize(dimensions);

        SCREEN_WIDTH = dimensions.x;

        mCameraSource = new CameraSource.Builder(context, detector)
                .setRequestedPreviewSize(dimensions.x, dimensions.y)
                .setFacing(CameraSource.CAMERA_FACING_FRONT)
                .setRequestedFps(30.0f)
                .build();
    }

    @Override
    protected void onResume() {
        super.onResume();

        startCameraSource();

        BlueteethManager.with(this).scanForPeripherals(15000, new OnScanCompletedListener() {
            @Override
            public void call(List<BlueteethDevice> blueteethDevices) {
                Log.d(TAG, "scan ended");
                for (final BlueteethDevice device : blueteethDevices) {
                    Log.d(TAG, device.getName());
                    if (device.getName().equals("HF-BL100-CU")) {
                        Log.d(TAG, "found module");
                        device.connect(true,
                            new OnConnectionChangedListener() {
                            @Override
                            public void call(boolean isConnected) {
                                if (isConnected) {
                                    Log.d(TAG, "connected");
                                    connectedDevice = device;
                                }
                                else
                                {
                                    Log.d(TAG, "disconnected");
                                    connectedDevice = null;
                                }
                            }
                        });
                    }
                }
            }
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        mPreview.stop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mCameraSource != null) {
            mCameraSource.release();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode != RC_HANDLE_CAMERA_PERM) {
            Log.d(TAG, "Got unexpected permission result: " + requestCode);
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
            return;
        }

        if (grantResults.length != 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "Camera permission granted - initialize the camera source");
            // we have permission, so create the camerasource
            createCameraSource();
            return;
        }

        Log.e(TAG, "Permission not granted: results len = " + grantResults.length +
                " Result code = " + (grantResults.length > 0 ? grantResults[0] : "(empty)"));

        DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                finish();
            }
        };

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Face Tracker sample")
                .setMessage(R.string.no_camera_permission)
                .setPositiveButton(R.string.ok, listener)
                .show();
    }

    private void startCameraSource() {

        int code = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(
                getApplicationContext());
        if (code != ConnectionResult.SUCCESS) {
            Dialog dlg =
                    GoogleApiAvailability.getInstance().getErrorDialog(this, code, RC_HANDLE_GMS);
            dlg.show();
        }

        if (mCameraSource != null) {
            try {
                mPreview.start(mCameraSource, mGraphicOverlay);
            } catch (IOException e) {
                Log.e(TAG, "Unable to start camera source.", e);
                mCameraSource.release();
                mCameraSource = null;
            }
        }
    }

    private class GraphicFaceTrackerFactory implements MultiProcessor.Factory<Face> {
        @Override
        public Tracker<Face> create(Face face) {
            return new GraphicFaceTracker(mGraphicOverlay);
        }
    }

    private class GraphicFaceTracker extends Tracker<Face> {
        private GraphicOverlay mOverlay;
        private FaceGraphic mFaceGraphic;

        GraphicFaceTracker(GraphicOverlay overlay) {
            mOverlay = overlay;
            mFaceGraphic = new FaceGraphic(overlay);
            mFaceGraphic.setColor(FaceGraphic.COLOR_CHOICES[4]);
        }

        @Override
        public void onNewItem(int faceId, Face item) {
            mFaceGraphic.setId(faceId);
        }

        @Override
        public void onUpdate(FaceDetector.Detections<Face> detectionResults, Face face) {
            float x = face.getPosition().x;
            float halfWidth = face.getWidth() / 2f;

            if ((x <= halfWidth) && (connectedDevice != null)) {
                Log.d(TAG, "sedning right");
                connectedDevice.writeCharacteristic(
                        new byte[]{ '1' },
                        CHARACTERISTIC_WRITE,
                        SERVICE_TEST,
                        new OnCharacteristicWriteListener() {
                            @Override
                            public void call(BlueteethResponse response) {
                                Log.d(TAG, "responded");
                            }
                        });
            } else if ((x >= SCREEN_WIDTH - halfWidth * 2) && (connectedDevice != null)) {
                // left
                Log.d(TAG, "sednign left");
                connectedDevice.writeCharacteristic(
                        new byte[]{ '2' },
                        CHARACTERISTIC_WRITE,
                        SERVICE_TEST,
                        new OnCharacteristicWriteListener() {
                            @Override
                            public void call(BlueteethResponse response) {
                                Log.d(TAG, "responded");
                            }
                        });
            }

            mOverlay.add(mFaceGraphic);

            if (connectedDevice != null) {
                mFaceGraphic.setColor(FaceGraphic.COLOR_CHOICES[2]);
            } else {
                mFaceGraphic.setColor(FaceGraphic.COLOR_CHOICES[4]);
            }

            mFaceGraphic.updateFace(face);

        }

        @Override
        public void onMissing(FaceDetector.Detections<Face> detectionResults) {
            mOverlay.remove(mFaceGraphic);
        }

        @Override
        public void onDone() {
            mOverlay.remove(mFaceGraphic);
        }
    }
}
