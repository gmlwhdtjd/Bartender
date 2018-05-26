package com.teambartender3.filters.FilterableCamera;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.arch.lifecycle.Lifecycle;
import android.arch.lifecycle.LifecycleObserver;
import android.arch.lifecycle.OnLifecycleEvent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.MeteringRectangle;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.MotionEvent;
import android.view.Surface;
import android.widget.Toast;

import com.gun0912.tedpermission.PermissionListener;
import com.gun0912.tedpermission.TedPermission;
import com.teambartender3.filters.R;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Created by huijonglee on 2018. 1. 12..
 *
 * This Class has been referenced and modified from Android Camera2Basic Sample.
 *
 * Original Code
 * https://github.com/googlesamples/android-Camera2Basic
 *
 * Modified by Hui-Jong Lee
 */
public class FCamera implements LifecycleObserver {

    /**
     * Conversion from screen rotation to JPEG orientation.
     */
    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();
    private static final String FRAGMENT_DIALOG = "dialog";

    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    /**
     * Tag for the {@link Log}.
     */
    private static final String TAG = "FCamera";

    /**
     * FCamera state: Showing camera preview.
     */
    private static final int STATE_PREVIEW = 0;

    /**
     * FCamera state: Waiting for the focus to be locked.
     */
    private static final int STATE_WAITING_LOCK = 1;

    /**
     * FCamera state: Waiting for the exposure to be precapture state.
     */
    private static final int STATE_WAITING_PRECAPTURE = 2;

    /**
     * FCamera state: Waiting for the exposure state to be something other than precapture.
     */
    private static final int STATE_WAITING_NON_PRECAPTURE = 3;

    /**
     * FCamera state: Picture was taken.
     */
    private static final int STATE_PICTURE_TAKEN = 4;

    /**
     * Max preview width that is guaranteed by Camera2 API
     */
    private static final int MAX_PREVIEW_WIDTH = 1920;

    /**
     * Max preview height that is guaranteed by Camera2 API
     */
    private static final int MAX_PREVIEW_HEIGHT = 1080;

    /**
     * ID of the current {@link CameraDevice}.
     */
    private String mCameraId;

    private CameraCharacteristics mCharacteristics;

    /**
     * An {@link FCameraPreview} for camera preview.
     */
    private FCameraPreview mFCameraPreview;

    /**
     * An {@link FCameraCapture} for camera capture.
     */
    private FCameraCapture mFCameraCapture;

    /**
     * A {@link CameraCaptureSession } for camera preview.
     */
    private CameraCaptureSession mCaptureSession;

    /**
     * A reference to the opened {@link CameraDevice}.
     */
    private CameraDevice mCameraDevice;

    private Callback mCallback;

    /**
     * true == back camera
     * false == front camera
     */
    private Boolean mCameraFacing;

    public enum Flash {
        AUTO,
        ON,
        OFF
    }

    /**
     * Whether the current camera device supports Flash or not.
     */
    private boolean mFlashSupported;

    private Flash mFlashSetting;

    private Surface mPreviewSurface;

    /**
     * The {@link Size} of camera preview.
     */
    private Size mPreviewSize;

    /**
     * An additional thread for running tasks that shouldn't block the UI.
     */
    private HandlerThread mBackgroundThread;

    /**
     * A {@link Handler} for running tasks in the background.
     */
    private Handler mBackgroundHandler;

    /**
     * An {@link ImageReader} that handles still image capture.
     */
    private ImageReader mImageReader;

    /**
     * This a callback object for the {@link ImageReader}. "onImageAvailable" will be called when a
     * still image is ready to be saved.
     */
    private final ImageReader.OnImageAvailableListener mOnImageAvailableListener
            = new ImageReader.OnImageAvailableListener() {

        @Override
        public void onImageAvailable(final ImageReader reader) {
            mBackgroundHandler.post(new Runnable() {
                @Override
                public void run() {
                    Image image = reader.acquireNextImage();
                    ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                    byte[] bytes = new byte[buffer.remaining()];
                    buffer.get(bytes);
                    Bitmap bitmapImage = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, null);

                    mFCameraCapture.onDrawFilter(bitmapImage);

                    image.close();
                }
            });
        }

    };

    /**
     * {@link CaptureRequest.Builder} for the camera preview
     */
    private CaptureRequest.Builder mPreviewRequestBuilder;

    /**
     * {@link CaptureRequest} generated by {@link #mPreviewRequestBuilder}
     */
    private CaptureRequest mPreviewRequest;

    /**
     * The current state of camera state for taking pictures.
     *
     * @see #mCaptureCallback
     */
    private int mState = STATE_PREVIEW;

    /**
     * A {@link Semaphore} to prevent the app from exiting before closing the camera.
     */
    private Semaphore mCameraOpenCloseLock = new Semaphore(1);

    private PermissionListener permissionlistener = new PermissionListener() {
        @Override
        public void onPermissionGranted() {
            Toast.makeText(mActivity, "Permission Granted", Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onPermissionDenied(ArrayList<String> deniedPermissions) {
            Toast.makeText(mActivity, "Permission Denied\n" + deniedPermissions.toString(), Toast.LENGTH_SHORT).show();
        }
    };

    /**
     * {@link CameraDevice.StateCallback} is called when {@link CameraDevice} changes its state.
     *
     * This Callback has been referenced and modified from Android Camera2Basic Sample.
     *
     * Original Code
     * https://github.com/googlesamples/android-Camera2Basic
     *
     * Modified by Hui-Jong Lee
     */
    private final CameraDevice.StateCallback mStateCallback = new CameraDevice.StateCallback() {

        @Override
        public void onOpened(@NonNull CameraDevice cameraDevice) {
            // This method is called when the camera is opened.  We start camera preview here.
            mCameraOpenCloseLock.release();
            mCameraDevice = cameraDevice;
            if (mCallback != null) {
                Handler mainHandler = new Handler(mActivity.getMainLooper());
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        mCallback.onOpened();
                    }
                });
            }
            createCameraPreviewSession();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice cameraDevice) {
            mCameraOpenCloseLock.release();
            cameraDevice.close();
            mCameraDevice = null;
        }

        @Override
        public void onError(@NonNull CameraDevice cameraDevice, int error) {
            mCameraOpenCloseLock.release();
            cameraDevice.close();
            mCameraDevice = null;
            if (null != mActivity) {
                mActivity.finish();
            }
        }

    };

    /**
     * A {@link CameraCaptureSession.CaptureCallback} that handles events related to JPEG capture.
     */
    private CameraCaptureSession.CaptureCallback mCaptureCallback
            = new CameraCaptureSession.CaptureCallback() {

        private void process(CaptureResult result) {
            switch (mState) {
                case STATE_PREVIEW: {
                    // We have nothing to do when the camera preview is working normally.
                    break;
                }
                case STATE_WAITING_LOCK: {
                    Integer afState = result.get(CaptureResult.CONTROL_AF_STATE);
                    if (afState == null) {
                        mState = STATE_PICTURE_TAKEN;
                        captureStillPicture();
                    } else if (CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED == afState ||
                            CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED == afState) {
                        // CONTROL_AE_STATE can be null on some devices
                        Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
                        if (aeState == null ||
                                aeState == CaptureResult.CONTROL_AE_STATE_CONVERGED) {
                            mState = STATE_PICTURE_TAKEN;
                            captureStillPicture();
                        } else {
                            runPrecaptureSequence();
                        }
                    }
                    break;
                }
                case STATE_WAITING_PRECAPTURE: {
                    // CONTROL_AE_STATE can be null on some devices
                    Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
                    if (aeState == null ||
                            aeState == CaptureResult.CONTROL_AE_STATE_PRECAPTURE ||
                            aeState == CaptureRequest.CONTROL_AE_STATE_FLASH_REQUIRED) {
                        mState = STATE_WAITING_NON_PRECAPTURE;
                    }
                    break;
                }
                case STATE_WAITING_NON_PRECAPTURE: {
                    // CONTROL_AE_STATE can be null on some devices
                    Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
                    if (aeState == null || aeState != CaptureResult.CONTROL_AE_STATE_PRECAPTURE) {
                        mState = STATE_PICTURE_TAKEN;
                        captureStillPicture();
                    }
                    break;
                }
            }
        }

        @Override
        public void onCaptureProgressed(@NonNull CameraCaptureSession session,
                                        @NonNull CaptureRequest request,
                                        @NonNull CaptureResult partialResult) {
            process(partialResult);
        }

        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                       @NonNull CaptureRequest request,
                                       @NonNull TotalCaptureResult result) {
            process(result);
        }

    };


    private final FragmentActivity mActivity;

    public FCamera(FragmentActivity activity,
                   FCameraPreview fCameraPreview,
                   FCameraCapture fCameraCapture) {
        activity.getLifecycle().addObserver(this);

        mActivity = activity;
        mFCameraPreview = fCameraPreview;
        mFCameraPreview.setFCamera(this);
        mFCameraPreview.setCallback(new FCameraPreview.Callback() {
            @Override
            public void onSurfaceCreated(int width, int height) {
                openCamera(width, height);
            }
        });

        mCameraFacing = true;

        mFCameraCapture = fCameraCapture;
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_RESUME)
    public void onResume() {
        startBackgroundThread();
        mFCameraCapture.onResume();
        mFCameraPreview.onResume();
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_PAUSE)
    public void onPause() {
        mFCameraPreview.onPause();
        mFCameraCapture.onPause();
        closeCamera();
        stopBackgroundThread();
    }

    public void setCallback(Callback callback){
        mCallback = callback;
    }

    /**
     * Initiate a still image capture.
     *
     * This method has been referenced from Android Camera2Basic Sample.
     */
    public void takePicture() {
        lockFocus();
    }

    /**
     * Switching between front and back camera
     *
     * just close camera and restart other camera
     *
     * Created by huijonglee on 2018. 2. 12..
     */
    public void switchCameraFacing() {
        closeCamera();
        mFCameraCapture.clear();

        mCameraFacing = !mCameraFacing;

        openCamera(mFCameraPreview.getWidth(), mFCameraPreview.getHeight());
    }

    public void setFlashSetting(Flash flash) {
        if (mFlashSupported) {
            mFlashSetting = flash;
            try {
                //first stop the existing repeating request
                mCaptureSession.stopRepeating();

                mPreviewRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                mPreviewRequestBuilder.addTarget(mPreviewSurface);

                setAutoFlash(mPreviewRequestBuilder);
                mPreviewRequest = mPreviewRequestBuilder.build();

                mCaptureSession.setRepeatingRequest(mPreviewRequest, mCaptureCallback, mBackgroundHandler);
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }
        else
            mFlashSetting = Flash.OFF;
    }

    public Flash getFlashSetting() {
        return mFlashSetting;
    }

    private boolean mManualFocusEngaged;

    /**
     * This method has been referenced from royshil/AndroidCamera2TouchToFocus.java
     *
     * Original Code
     * https://gist.github.com/royshil/8c760c2485257c85a11cafd958548482
     *
     * Modified by Hui-Jong Lee
     * @param event
     */
    void touchToFocus(final MotionEvent event) {
        if (mCharacteristics != null) {
            final Rect sensorArraySize = mCharacteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);

            if (sensorArraySize != null && !mManualFocusEngaged && mFlashSetting == Flash.OFF) {
                Log.d(TAG, "touchToFocus: " + event.getX() + ", " + event.getY());
                Log.d(TAG, "touchToFocus: " + mFCameraPreview.getWidth() + ", " + mFCameraPreview.getHeight());
                Log.d(TAG, "touchToFocus: " + sensorArraySize.width() + ", " + sensorArraySize.height());

                int x, y;
                int orientation = mCharacteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);

                switch (orientation) {
                    case 0:
                        x = (int) ((event.getX() / (float) mFCameraPreview.getWidth() * (float) sensorArraySize.width()));
                        y = (int) ((event.getY() / (float) mFCameraPreview.getHeight() * (float) sensorArraySize.height()));
                        break;
                    case 90:
                        x = (int) ((event.getY() / (float) mFCameraPreview.getHeight()) * (float) sensorArraySize.width());
                        y = (int) ((1.0f - (event.getX() / (float) mFCameraPreview.getWidth())) * (float) sensorArraySize.height());
                        break;
                    case 180:
                        x = (int) ((1.0f - (event.getX() / (float) mFCameraPreview.getWidth())) * (float) sensorArraySize.width());
                        y = (int) ((1.0f - (event.getY() / (float) mFCameraPreview.getHeight())) * (float) sensorArraySize.height());
                        break;
                    case 270:
                        x = (int) ((event.getY() / (float) mFCameraPreview.getHeight()) * (float) sensorArraySize.width());
                        y = (int) ((event.getX() / (float) mFCameraPreview.getWidth()) * (float) sensorArraySize.height());
                        break;
                    default:
                        x = (int) (0.5 * (float) sensorArraySize.width());
                        y = (int) (0.5 * (float) sensorArraySize.height());
                }

                if (!mCameraFacing) {   // front camera
                    switch (orientation) {
                        case 0:
                        case 180:
                            x = sensorArraySize.width() - x;
                            break;
                        case 90:
                        case 270:
                            y = sensorArraySize.height() - y;
                            break;
                    }
                }

                Log.d(TAG, "touchToFocus: " + orientation);
                Log.d(TAG, "touchToFocus: " + x + ", " + y);

                final int halfTouchWidth = 150;
                final int halfTouchHeight = 150;
                MeteringRectangle focusAreaTouch = new MeteringRectangle(
                        Math.max(x - halfTouchWidth, 0),
                        Math.max(y - halfTouchHeight, 0),
                        halfTouchWidth * 2,
                        halfTouchHeight * 2,
                        MeteringRectangle.METERING_WEIGHT_MAX - 1);

                try {

                    CameraCaptureSession.CaptureCallback captureCallbackHandler = new CameraCaptureSession.CaptureCallback() {
                        @Override
                        public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
                            super.onCaptureCompleted(session, request, result);

                            if (request.getTag() == "FOCUS_TAG") {
                                mManualFocusEngaged = false;

                                try {
                                    //the focus trigger is complete -
                                    //resume repeating (preview surface will get frames), clear AF trigger
                                    mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_IDLE);
                                    mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER, CameraMetadata.CONTROL_AE_PRECAPTURE_TRIGGER_IDLE);
                                    mCaptureSession.setRepeatingRequest(mPreviewRequestBuilder.build(), mCaptureCallback, mBackgroundHandler);
                                } catch (CameraAccessException e) {
                                    e.printStackTrace();
                                }
                            }
                        }

                        @Override
                        public void onCaptureFailed(CameraCaptureSession session, CaptureRequest request, CaptureFailure failure) {
                            super.onCaptureFailed(session, request, failure);
                            Log.e(TAG, "Manual AF failure: " + failure);
                            mManualFocusEngaged = false;
                        }
                    };

                    //first stop the existing repeating request
                    mCaptureSession.stopRepeating();

                    //cancel any existing AF trigger (repeated touches, etc.)
                    mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_CANCEL);
                    mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF);
                    mCaptureSession.capture(mPreviewRequestBuilder.build(), captureCallbackHandler, mBackgroundHandler);

                    mPreviewRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);

                    //Now add a new AF trigger with focus region
                    if ( mCharacteristics.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AF) >= 1){
                        mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_REGIONS, new MeteringRectangle[]{focusAreaTouch});
                    }
                    mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO);
                    mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_START);

                    //Now add a new AF trigger with focus region
                    if ( mCharacteristics.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AE) >= 1){
                        mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_REGIONS, new MeteringRectangle[]{focusAreaTouch});
                    }
                    mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
                    mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER, CameraMetadata.CONTROL_AE_PRECAPTURE_TRIGGER_START);

                    mPreviewRequestBuilder.setTag("FOCUS_TAG"); //we'll capture this later for resuming the preview

                    //then we ask for a single request (not repeating!)
                    mCaptureSession.capture(mPreviewRequestBuilder.build(), captureCallbackHandler, mBackgroundHandler);

                    if (mCallback != null){
                        Handler mainHandler = new Handler(mActivity.getMainLooper());
                        mainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                mCallback.onTouchToFocus(event);
                            }
                        });
                    }

                    mManualFocusEngaged = true;
                } catch (CameraAccessException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * Shows a {@link Toast} on the UI thread.
     *
     * @param text The message to show
     *
     * This method has been referenced and modified from Android Camera2Basic Sample.
     *
     * Original Code
     * https://github.com/googlesamples/android-Camera2Basic
     *
     * Modified by Hui-Jong Lee
     */
    private void showToast(final String text) {
        if (mActivity != null) {
            mActivity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(mActivity, text, Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    /**
     * Given {@code choices} of {@code Size}s supported by a camera, choose the smallest one that
     * is at least as large as the respective texture view size, and that is at most as large as the
     * respective max size, and whose aspect ratio matches with the specified value. If such size
     * doesn't exist, choose the largest one that is at most as large as the respective max size,
     * and whose aspect ratio matches with the specified value.
     *
     * @param choices           The list of sizes that the camera supports for the intended output
     *                          class
     * @param textureViewWidth  The width of the texture view relative to sensor coordinate
     * @param textureViewHeight The height of the texture view relative to sensor coordinate
     * @param maxWidth          The maximum width that can be chosen
     * @param maxHeight         The maximum height that can be chosen
     * @param aspectRatio       The aspect ratio
     * @return The optimal {@code Size}, or an arbitrary one if none were big enough
     *
     * This method has been referenced from Android Camera2Basic Sample.
     */
    private static Size chooseOptimalSize(Size[] choices,
                                          int textureViewWidth, int textureViewHeight,
                                          int maxWidth, int maxHeight,
                                          Size aspectRatio) {

        // Collect the supported resolutions that are at least as big as the preview Surface
        List<Size> bigEnough = new ArrayList<>();
        // Collect the supported resolutions that are smaller than the preview Surface
        List<Size> notBigEnough = new ArrayList<>();
        int w = aspectRatio.getWidth();
        int h = aspectRatio.getHeight();
        for (Size option : choices) {
            if (option.getWidth() <= maxWidth && option.getHeight() <= maxHeight &&
                    option.getHeight() == option.getWidth() * h / w) {
                if (option.getWidth() >= textureViewWidth &&
                        option.getHeight() >= textureViewHeight) {
                    bigEnough.add(option);
                } else {
                    notBigEnough.add(option);
                }
            }
        }

        // Pick the smallest of those big enough. If there is no one big enough, pick the
        // largest of those not big enough.
        if (bigEnough.size() > 0) {
            return Collections.min(bigEnough, new CompareSizesByArea());
        } else if (notBigEnough.size() > 0) {
            return Collections.max(notBigEnough, new CompareSizesByArea());
        } else {
            Log.e(TAG, "Couldn't find any suitable preview size");
            return choices[0];
        }
    }

    /**
     * Sets up member variables related to camera.
     *
     * @param width  The width of available size for camera preview
     * @param height The height of available size for camera preview
     *
     * This method has been referenced and modified from Android Camera2Basic Sample.
     *
     * Original Code
     * https://github.com/googlesamples/android-Camera2Basic
     *
     * Modified by Hui-Jong Lee
     */
    @SuppressWarnings("SuspiciousNameCombination")
    private void setUpCameraOutputs(int width, int height) {
        CameraManager manager = (CameraManager) mActivity.getSystemService(Context.CAMERA_SERVICE);
        try {
            for (String cameraId : manager.getCameraIdList()) {
                CameraCharacteristics characteristics
                        = manager.getCameraCharacteristics(cameraId);

                Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);

                // Witch Camera do you want?
                Integer targetFacing;
                if (mCameraFacing)
                    targetFacing = CameraCharacteristics.LENS_FACING_BACK;
                else
                    targetFacing = CameraCharacteristics.LENS_FACING_FRONT;

                if (facing != null && !facing.equals(targetFacing))
                    continue;

                StreamConfigurationMap map = characteristics.get(
                        CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                if (map == null) {
                    continue;
                }

                // For still image captures, we use the largest available size.
                Size largest = Collections.max(
                        Arrays.asList(map.getOutputSizes(ImageFormat.JPEG)),
                        new CompareSizesByArea());

                mFCameraCapture.setCameraCharacteristics(characteristics, largest);

                mImageReader = ImageReader.newInstance(largest.getWidth(), largest.getHeight(),
                        ImageFormat.JPEG, /*maxImages*/2);

                mImageReader.setOnImageAvailableListener(
                        mOnImageAvailableListener, mBackgroundHandler);

                //noinspection ConstantConditions
                int sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
                boolean swappedDimensions = false;

                if (sensorOrientation == 90 || sensorOrientation == 270) {
                    swappedDimensions = true;
                }

                Point displaySize = new Point();
                mActivity.getWindowManager().getDefaultDisplay().getSize(displaySize);
                int rotatedPreviewWidth = width;
                int rotatedPreviewHeight = height;
                int maxPreviewWidth = displaySize.x;
                int maxPreviewHeight = displaySize.y;

                if (swappedDimensions) {
                    rotatedPreviewWidth = height;
                    rotatedPreviewHeight = width;
                    maxPreviewWidth = displaySize.y;
                    maxPreviewHeight = displaySize.x;
                }

                if (maxPreviewWidth > MAX_PREVIEW_WIDTH) {
                    maxPreviewWidth = MAX_PREVIEW_WIDTH;
                }

                if (maxPreviewHeight > MAX_PREVIEW_HEIGHT) {
                    maxPreviewHeight = MAX_PREVIEW_HEIGHT;
                }

                // Danger, W.R.! Attempting to use too large a preview size could  exceed the camera
                // bus' bandwidth limitation, resulting in gorgeous previews but the storage of
                // garbage capture data.
                mPreviewSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class),
                        rotatedPreviewWidth, rotatedPreviewHeight, maxPreviewWidth,
                        maxPreviewHeight, largest);

                // We fit the aspect ratio of TextureView to the size of preview we picked.
                int orientation = mActivity.getResources().getConfiguration().orientation;
                if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                    mFCameraPreview.setAspectRatio(
                            mPreviewSize.getWidth(), mPreviewSize.getHeight());
                } else {
                    mFCameraPreview.setAspectRatio(
                            mPreviewSize.getHeight(), mPreviewSize.getWidth());
                }

                // Check if the flash is supported.
                Boolean available = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                mFlashSupported = available == null ? false : available;
                mFlashSetting = Flash.OFF;

                mCameraId = cameraId;
                mCharacteristics = characteristics;
                return;
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        } catch (NullPointerException e) {
            // Currently an NPE is thrown when the Camera2API is used but not supported on the
            // device this code runs.
            ErrorDialog
                    .newInstance(mActivity.getString(R.string.camera_error))
                    .show(mActivity.getSupportFragmentManager(), FRAGMENT_DIALOG);
        }
    }

    /**
     * Opens the camera specified by {@link FCamera#mCameraId}.
     *
     * This method has been referenced and modified from Android Camera2Basic Sample.
     *
     * Original Code
     * https://github.com/googlesamples/android-Camera2Basic
     *
     * Modified by Hui-Jong Lee
     *
     */
    private void openCamera(final int width, final int height) {
        //Permission Check
        if (ContextCompat.checkSelfPermission(mActivity, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            TedPermission.with(mActivity)
                    .setPermissionListener(permissionlistener)
                    .setDeniedMessage("If you reject permission,you can not use this service\n\nPlease turn on permissions at [Setting] > [Permission]")
                    .setPermissions(Manifest.permission.CAMERA)
                    .check();
            return;
        }

        setUpCameraOutputs(width, height);

        CameraManager manager = (CameraManager) mActivity.getSystemService(Context.CAMERA_SERVICE);
        try {
            if (!mCameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                throw new RuntimeException("Time out waiting to lock camera opening.");
            }
            manager.openCamera(mCameraId, mStateCallback, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera opening.", e);
        }
    }

    /**
     * Closes the current {@link CameraDevice}.
     *
     * This method has been referenced from Android Camera2Basic Sample.
     */
    private void closeCamera() {
        synchronized (mCaptureSessionStateCallback) {
            if (mCallback != null){
                Handler mainHandler = new Handler(mActivity.getMainLooper());
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        mCallback.onClose();
                    }
                });
            }
            try {
                mCameraOpenCloseLock.acquire();
                if (null != mCaptureSession) {
                    mCaptureSession.close();
                    mCaptureSession = null;
                }
                if (null != mCameraDevice) {
                    mCameraDevice.close();
                    mCameraDevice = null;
                }
            } catch (InterruptedException e) {
                throw new RuntimeException("Interrupted while trying to lock camera closing.", e);
            } finally {
                mCameraOpenCloseLock.release();
            }
        }
    }

    /**
     * Starts a background thread and its {@link Handler}.
     *
     * This method has been referenced from Android Camera2Basic Sample.
     */
    private void startBackgroundThread() {
        mBackgroundThread = new HandlerThread("CameraBackground");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }

    /**
     * Stops the background thread and its {@link Handler}.
     *
     * This method has been referenced from Android Camera2Basic Sample.
     */
    private void stopBackgroundThread() {
        mBackgroundThread.quitSafely();
        try {
            mBackgroundThread.join();
            mBackgroundThread = null;
            mBackgroundHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * Creates a new {@link CameraCaptureSession} for camera preview.
     *
     * This method has been referenced from Android Camera2Basic Sample.
     */
    private void createCameraPreviewSession() {
        try {
            SurfaceTexture texture = mFCameraPreview.getInputSurfaceTexture();
            assert texture != null;

            // We configure the size of default buffer to be the size of camera preview we want.
            texture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());

            // This is the output Surface we need to start preview.
            mPreviewSurface = new Surface(texture);

            // We set up a CaptureRequest.Builder with the output Surface.
            mPreviewRequestBuilder
                    = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            mPreviewRequestBuilder.addTarget(mPreviewSurface);

            // Here, we create a CameraCaptureSession for camera preview.
            mCameraDevice.createCaptureSession(Arrays.asList(mPreviewSurface, mImageReader.getSurface()), mCaptureSessionStateCallback, null);

            mFCameraPreview.setCameraCharacteristics(mCharacteristics);

            if (mCallback != null){
                Handler mainHandler = new Handler(mActivity.getMainLooper());
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        mCallback.onStartPreview();
                    }
                });
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * Creates a new {@link CameraCaptureSession.StateCallback} for camera preview.
     * It is likely to be executed simultaneously with the camera exit function.
     * If it is run after the camera has shut down, race condition is occur.
     * so it is managed separately.
     *
     * This method has been referenced from Android Camera2Basic Sample.
     */
    final private CameraCaptureSession.StateCallback mCaptureSessionStateCallback
            = new CameraCaptureSession.StateCallback() {

        @Override
        public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
            synchronized (this) {
                // The camera is already closed
                if (null == mCameraDevice) {
                    return;
                }

                // When the session is ready, we start displaying the preview.
                mCaptureSession = cameraCaptureSession;
                try {
                    // Auto focus should be continuous for camera preview.
                    mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                            CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                    // Flash is automatically enabled when necessary.
                    setAutoFlash(mPreviewRequestBuilder);

                    // Finally, we start displaying the camera preview.
                    mPreviewRequest = mPreviewRequestBuilder.build();
                    mCaptureSession.setRepeatingRequest(mPreviewRequest,
                            mCaptureCallback, mBackgroundHandler);
                } catch (CameraAccessException e) {
                    e.printStackTrace();
                }
            }
        }

        @Override
        public void onConfigureFailed(
                @NonNull CameraCaptureSession cameraCaptureSession) {
            showToast("Failed");
        }
    };

    /**
     * Lock the focus as the first step for a still image capture.
     *
     * This method has been referenced from Android Camera2Basic Sample.
     */
    private void lockFocus() {
        try {
            // This is how to tell the camera to lock focus.
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER,
                    CameraMetadata.CONTROL_AF_TRIGGER_START);
            // Tell #mCaptureCallback to wait for the lock.
            mState = STATE_WAITING_LOCK;
            mCaptureSession.capture(mPreviewRequestBuilder.build(), mCaptureCallback,
                    mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * Run the precapture sequence for capturing a still image. This method should be called when
     * we get a response in {@link #mCaptureCallback} from {@link #lockFocus()}.
     *
     * This method has been referenced from Android Camera2Basic Sample.
     */
    private void runPrecaptureSequence() {
        try {
            // This is how to tell the camera to trigger.
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
                    CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START);
            setAutoFlash(mPreviewRequestBuilder);
            // Tell #mCaptureCallback to wait for the precapture sequence to be set.
            mState = STATE_WAITING_PRECAPTURE;
            mCaptureSession.capture(mPreviewRequestBuilder.build(), mCaptureCallback,
                    mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * Capture a still picture. This method should be called when we get a response in
     * {@link #mCaptureCallback} from both {@link #lockFocus()}.
     *
     * This method has been referenced and modified from Android Camera2Basic Sample.
     *
     * Original Code
     * https://github.com/googlesamples/android-Camera2Basic
     *
     * Modified by Hui-Jong Lee
     */
    private void captureStillPicture() {
        try {
            if (null == mActivity || null == mCameraDevice) {
                return;
            }
            // This is the CaptureRequest.Builder that we use to take a picture.
            final CaptureRequest.Builder captureBuilder =
                    mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureBuilder.addTarget(mImageReader.getSurface());

            // Use the same AE and AF modes as the preview.
            captureBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            setAutoFlash(captureBuilder);

            CameraCaptureSession.CaptureCallback CaptureCallback
                    = new CameraCaptureSession.CaptureCallback() {

                @Override
                public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                               @NonNull CaptureRequest request,
                                               @NonNull TotalCaptureResult result) {
                    unlockFocus();
                }
            };

            mCaptureSession.stopRepeating();
            mCaptureSession.abortCaptures();

            if (mCallback != null){
                Handler mainHandler = new Handler(mActivity.getMainLooper());
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        mCallback.onCapture();
                    }
                });
            }

            mCaptureSession.capture(captureBuilder.build(), CaptureCallback, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * Unlock the focus. This method should be called when still image capture sequence is
     * finished.
     *
     * This method has been referenced from Android Camera2Basic Sample.
     */
    private void unlockFocus() {
        try {
            // Reset the af, ae trigger
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER,
                    CameraMetadata.CONTROL_AF_TRIGGER_CANCEL);
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
                    CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_CANCEL);
            mCaptureSession.capture(mPreviewRequestBuilder.build(), mCaptureCallback,
                    mBackgroundHandler);
            // After this, the camera will go back to the normal state of preview.
            mState = STATE_PREVIEW;

            //reset normal setting
            mPreviewRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            mPreviewRequestBuilder.addTarget(mPreviewSurface);

            setAutoFlash(mPreviewRequestBuilder);

            mCaptureSession.setRepeatingRequest(mPreviewRequest, mCaptureCallback, mBackgroundHandler);

            if (mCallback != null){
                Handler mainHandler = new Handler(mActivity.getMainLooper());
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        mCallback.onCaptured();
                    }
                });
            }

        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * This method has been referenced from Android Camera2Basic Sample.
     *
     * @param requestBuilder
     */
    private void setAutoFlash(CaptureRequest.Builder requestBuilder) {
        if (mFlashSupported) {
            switch (mFlashSetting) {
                case AUTO:
                    requestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
                    break;
                case ON:
                    requestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_ALWAYS_FLASH);
                    break;
                case OFF:
                    requestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
                    break;
            }

        }
    }

    /**
     * Compares two {@code Size}s based on their areas.
     *
     * This Class has been referenced from Android Camera2Basic Sample.
     */
    static class CompareSizesByArea implements Comparator<Size> {

        @Override
        public int compare(Size lhs, Size rhs) {
            // We cast here to ensure the multiplications won't overflow
            return Long.signum((long) lhs.getWidth() * lhs.getHeight() -
                    (long) rhs.getWidth() * rhs.getHeight());
        }

    }

    /**
     * Shows an error message dialog.
     *
     * This Class has been referenced from Android Camera2Basic Sample.
     */
    public static class ErrorDialog extends DialogFragment {

        private static final String ARG_MESSAGE = "message";

        public static ErrorDialog newInstance(String message) {
            ErrorDialog dialog = new ErrorDialog();
            Bundle args = new Bundle();
            args.putString(ARG_MESSAGE, message);
            dialog.setArguments(args);
            return dialog;
        }

        @NonNull
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            final Activity activity = getActivity();
            return new AlertDialog.Builder(activity)
                    .setMessage(getArguments().getString(ARG_MESSAGE))
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            activity.finish();
                        }
                    })
                    .create();
        }

    }

    public interface Callback {
        void onOpened();
        void onStartPreview();
        void onTouchToFocus(MotionEvent event);
        void onCapture();
        void onCaptured();
        void onClose();
    }
}
