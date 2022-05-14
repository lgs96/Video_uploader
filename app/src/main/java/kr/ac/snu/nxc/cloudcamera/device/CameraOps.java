package kr.ac.snu.nxc.cloudcamera.device;

import android.annotation.SuppressLint;
import android.content.Context;
import android.hardware.SensorManager;
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
import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.util.Range;
import android.util.Rational;
import android.util.Size;
import android.util.SizeF;
import android.util.SparseIntArray;
import android.view.OrientationEventListener;
import android.view.Surface;
import android.view.TextureView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;

import kr.ac.snu.nxc.cloudcamera.CodecActivity;
import kr.ac.snu.nxc.cloudcamera.StreamInferenceManager;
import kr.ac.snu.nxc.cloudcamera.codec.CCVideoStreamWriter;
import kr.ac.snu.nxc.cloudcamera.codec.CCVideoWriter;
import kr.ac.snu.nxc.cloudcamera.util.CCConstants;
import kr.ac.snu.nxc.cloudcamera.util.CCImage;
import kr.ac.snu.nxc.cloudcamera.util.CCLog;
import kr.ac.snu.nxc.cloudcamera.util.CCUtils;

import static kr.ac.snu.nxc.cloudcamera.util.CCConstants.ShutterAction;
import static kr.ac.snu.nxc.cloudcamera.util.CCConstants.ShutterAction.FULL_FRAME_DUMP;
import static kr.ac.snu.nxc.cloudcamera.util.CCConstants.ShutterAction.JPG_SHOT;
import static kr.ac.snu.nxc.cloudcamera.util.CCConstants.ShutterAction.PREVIEW_DUMP;
import static kr.ac.snu.nxc.cloudcamera.util.CCConstants.ShutterAction.YUV_SHOT;

public class CameraOps extends Thread {
    private final static String TAG = "CameraOps";

    private Object mPreviewLock = new Object();
    private Object mLock = new Object();

    private Size mPreviewSize = null;
    private Size mFullFrameSize = null;
    private Size mJpegSize = null;

    private Context mContext;
    private CameraDevice mCameraDevice;
    private CaptureRequest.Builder mPreviewBuilder;
    private CameraCaptureSession mCameraSession;
    private TextureView mTextureView;

    //Normal = 0, front = 1, wide = 2
    private String mCameraId = "0";

    private PictureCallbackInterface mPictureCallbackListener;

    private Handler mPreviewHandler = null;
    private Handler mFullFrameHandler = null;
    private Handler mPreviewImageHandler = null;
    private Handler mCaptureHandler = null;
    private Handler mPictureCallbackHandler = null;

    CameraManager mCameraManager = null;
    OutputManager mOutputManager = null;

    //YuvShot
    private int mCaptureCount = 0;
    private boolean mEnabledHdr = false;
    private int mMinExposure;
    private int mMaxExposure;
    private Range<Integer> mHdrFpsRange = null;
    private boolean mInitializedHDR = false;

    //Dump
    private String mDumpDirPath = null;
    private boolean mRunningDump = false;
    private String mVideoPath = null;

    private boolean mEnabledObjectDetection = false;
    private boolean mReadyForObjectDetectionProcess = false;

    MultiFrameBufferManager mMultiFrameBufferManager = null;

    ShutterAction mShutterAction = JPG_SHOT;

    private static final SparseIntArray ORIENTATIONS = new SparseIntArray(4);

    private int mShotOrientation;
    private int mOrientation = 0;
    OrientationEventListener mOrientationListener = null;

    private boolean mEnabledCloudShot = false;
    private boolean mCloudShotDebug = false;

    private long mCaptureTime;

    StreamInferenceManager mInferenceManager;

    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    private CameraCaptureSession.CaptureCallback mPreviewCaptureListener = new CameraCaptureSession.CaptureCallback() {
        @Override
        public void onCaptureCompleted(CameraCaptureSession session,
                                       CaptureRequest request,
                                       TotalCaptureResult result) {
            //CCLog.d(TAG, "[Preview]onCaptureCompleted frameNumber : " + result.getFrameNumber() + " timestamp " + result.get(CaptureResult.SENSOR_TIMESTAMP));
        }

        @Override
        public void onCaptureStarted(CameraCaptureSession session,
                                     CaptureRequest request,
                                     long timestamp,
                                     long frameNumber) {
            //CCLog.d(TAG, "[Preview]onCaptureStarted timestamp : " + timestamp + " frameNumber : " + frameNumber);
        }

        @Override
        public void onCaptureFailed(CameraCaptureSession session,
                                    CaptureRequest request,
                                    CaptureFailure result) {
            //CCLog.e(TAG, "[Preview]onCaptureFAILed. frameNum : " + result.getFrameNumber() + ", reason : " + result.getReason());
        }

        @Override
        public void onCaptureProgressed(CameraCaptureSession session, CaptureRequest request,
                                        CaptureResult result) {
            super.onCaptureProgressed(session, request, result);
        }
    };

    private CameraCaptureSession.CaptureCallback mShotCaptureListener = new CameraCaptureSession.CaptureCallback() {
        @Override
        public void onCaptureCompleted(CameraCaptureSession session,
                                       CaptureRequest request,
                                       TotalCaptureResult result) {
            CCLog.d(TAG, "[Shot]onCaptureCompleted frameNumber : " + result.getFrameNumber() + " timestamp " + result.get(CaptureResult.SENSOR_TIMESTAMP));
        }

        @Override
        public void onCaptureStarted(CameraCaptureSession session,
                                     CaptureRequest request,
                                     long timestamp,
                                     long frameNumber) {
            CCLog.d(TAG, "[Shot]onCaptureStarted timestamp : " + timestamp + " frameNumber : " + frameNumber);
        }

        @Override
        public void onCaptureFailed(CameraCaptureSession session,
                                    CaptureRequest request,
                                    CaptureFailure result) {
            CCLog.e(TAG, "[Shot]onCaptureFailed. frameNum : " + result.getFrameNumber() + ", reason : " + result.getReason());
            mPictureCallbackHandler.post(new Runnable() {
                public void run() {
                    mPictureCallbackListener.onCaptureFail();
                    mMultiFrameBufferManager.clearBuffer();
                }
            });
        }

        @Override
        public void onCaptureProgressed(CameraCaptureSession session, CaptureRequest request,
                                        CaptureResult result) {
            super.onCaptureProgressed(session, request, result);
        }
    };

    public CameraOps(Context context, TextureView textureView, ShutterAction shutterAction,
                    boolean enabledCloudShot, boolean enabledHdr) {
        mContext = context;
        mTextureView = textureView;
        mShutterAction = shutterAction;
        mEnabledCloudShot = enabledCloudShot;
        mEnabledHdr = enabledHdr;
        //mTextureView.setSurfaceTextureListener(mSurfaceTextureListener);

        HandlerThread ht = new HandlerThread("CameraPreview");
        ht.start();
        mPreviewHandler = new Handler(ht.getLooper());

        ht = new HandlerThread("CameraCapture");
        ht.start();
        mCaptureHandler = new Handler(ht.getLooper());

        ht = new HandlerThread("PreviewImage");
        ht.start();
        mPreviewImageHandler = new Handler(ht.getLooper());

        ht = new HandlerThread("FullFrame");
        ht.start();
        mFullFrameHandler = new Handler(ht.getLooper());

        ht = new HandlerThread("PictureCallBack");
        ht.start();
        mPictureCallbackHandler = new Handler(ht.getLooper());

        mCameraId = CCUtils.getCameraId();

        mMultiFrameBufferManager = new MultiFrameBufferManager();

        mInferenceManager = new StreamInferenceManager(mContext);

        mOutputManager = new OutputManager(this, mPreviewImageHandler, mFullFrameHandler, mCaptureHandler, mTextureView);

        mOrientationListener = new OrientationEventListener(mContext, SensorManager.SENSOR_DELAY_UI) {
            public void onOrientationChanged(int orientation) {
                int degreeToOrientation = 0;
                if (315 < orientation || orientation < 45 ) {
                    degreeToOrientation = 0;
                } else if (orientation < 135) {
                    degreeToOrientation = 3;
                } else if (orientation < 225) {
                    degreeToOrientation = 2;
                } else {
                    degreeToOrientation = 1;
                }

                if (mOrientation != degreeToOrientation) {
                    CCLog.d(TAG, "Orientation Changed : " + degreeToOrientation);
                    mOrientation = degreeToOrientation;
                }
            }
        };

        if (mOrientationListener.canDetectOrientation()) {
            mOrientationListener.enable();
        }
    }

    public void setOnPictureCallbackListener(PictureCallbackInterface pictureCallbackListener) {
        mPictureCallbackListener = pictureCallbackListener;
    }

    public void changeConfiguration(ShutterAction shutterAction, int captureCount) {
        CCLog.d(TAG, "configureOutputSurfaces shutterAction : " + shutterAction);

        closeCamera(true);

        mShutterAction = shutterAction;
        mCaptureCount = captureCount;

        mOutputManager.configureOutputSurfaces(shutterAction);

        openCamera(mPreviewSize.getWidth(), mPreviewSize.getHeight());

    }

    private void calculateFOV(CameraManager cameraManager, String cameraId) {
        try {
//            for (final String cameraId : cameraManager.getCameraIdList()) {
            CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(cameraId);
            int cOrientation = characteristics.get(CameraCharacteristics.LENS_FACING);
            if (cOrientation == CameraCharacteristics.LENS_FACING_BACK) {
                float[] maxFocus = characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS);
                SizeF size = characteristics.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE);
                float w = size.getWidth();
                float h = size.getHeight();
                float horizonalAngle = (float) (2*Math.atan(w/(maxFocus[0]*2)));
                float verticalAngle = (float) (2*Math.atan(h/(maxFocus[0]*2)));

                CCLog.i(TAG, "FOV : " + horizonalAngle + ", " + verticalAngle);

                horizonalAngle = (float)Math.toDegrees(horizonalAngle);
                verticalAngle = (float)Math.toDegrees(verticalAngle);

                CCLog.i(TAG, "FOV Degree : " + horizonalAngle + ", " + verticalAngle);
            }
//            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }


    @SuppressLint("MissingPermission")
    public void openCamera(int previewWidth, int previewHeight) {
        mCameraManager = (CameraManager) mContext.getSystemService(Context.CAMERA_SERVICE);
        calculateFOV(mCameraManager, mCameraId);

        if (!mTextureView.isAvailable()) {
            CCLog.w(TAG, "openCamera after texture available");
            return;
        }
        CCLog.d(TAG, "openCamera START");
        try {
            synchronized (mPreviewLock) {
                initializedHDR();
                mPreviewSize = new Size(previewWidth, previewHeight);
                mFullFrameSize = CCUtils.getRequestFullFrameSize();
                mJpegSize = mFullFrameSize;

                CCLog.d(TAG, "PreviewSize : " + mPreviewSize.getWidth() + " x " + mPreviewSize.getHeight());
                CCLog.d(TAG, "FullFrameSize : " + mFullFrameSize.getWidth() + " x " + mFullFrameSize.getHeight());
                mOutputManager.setImageSize(mPreviewSize, mFullFrameSize, mJpegSize);

                mCameraManager.openCamera(mCameraId, mStateCallback, mPreviewHandler);
            }
        } catch (CameraAccessException e) {
            CCLog.e(TAG, "openCamera Error");
            e.printStackTrace();
        }
        CCLog.d(TAG, "openCamera END");
    }

    private void initializedHDR() throws CameraAccessException {
        if (!mInitializedHDR) {
            CameraCharacteristics characteristics = mCameraManager.getCameraCharacteristics(mCameraId);

            //HDR
            Rational ecStep = characteristics.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_STEP);
            float aeStep = (float) ecStep.getNumerator() / ecStep.getDenominator();
            CCLog.d(TAG, "AE Step : " + aeStep);

            Range<Integer> aeRange = characteristics.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE);
            CCLog.d(TAG, "AE Range : " + aeRange);
            mMinExposure = aeRange.getLower();
            mMaxExposure = aeRange.getUpper();

            Range<Integer>[] fpsRanges = characteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES);
            Range<Integer> result = null;
            int maxDiffRange = 0;
            for (Range<Integer> fpsRange : fpsRanges) {
                CCLog.d(TAG, "FPS Range : " + fpsRange);
                int lower = fpsRange.getLower();
                int upper = fpsRange.getUpper();

                if (upper - lower > maxDiffRange) {
                    maxDiffRange = upper - lower;
                    mHdrFpsRange = fpsRange;
                }
            }
            CCLog.d(TAG, "HDR FPS Range : " + mHdrFpsRange);
        }
        mInitializedHDR = true;
    }

    private CameraDevice.StateCallback mStateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(CameraDevice cameraDevice) {
            CCLog.i(TAG, "onOpened");
            mCameraDevice = cameraDevice;
            preparePreview();
        }

        @Override
        public void onDisconnected(CameraDevice camera) {
            CCLog.e(TAG, "onDisconnected");
        }

        @Override
        public void onError(CameraDevice camera, int error) {
            CCLog.e(TAG, "onError");
        }
    };

    protected void preparePreview() {
        synchronized (mPreviewLock) {
            if (null == mCameraDevice || !mTextureView.isAvailable() || null == mPreviewSize) {
                CCLog.e(TAG, "preparePreview FAIL");
                return;
            }
            CCLog.d(TAG, "preparePreview START");
            mOutputManager.preparePreviewTexture();
            mOutputManager.configureOutputSurfaces(mShutterAction);
            try {
                mPreviewBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
            mPreviewBuilder.addTarget(mOutputManager.getPreviewSurface());

            if (mShutterAction == PREVIEW_DUMP) {
                mPreviewBuilder.addTarget(mOutputManager.getPreviewImageSurface());
            } else {
                mPreviewBuilder.removeTarget(mOutputManager.getPreviewImageSurface());
            }

            if (mShutterAction == FULL_FRAME_DUMP) {
                mPreviewBuilder.addTarget(mOutputManager.getFullFrameSurface());
            } else {
                mPreviewBuilder.removeTarget(mOutputManager.getFullFrameSurface());
            }

            try {
                mCameraDevice.createCaptureSession(mOutputManager.getOutputSurfaces(), getSessionCallback(), mPreviewHandler);
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }

            CCLog.d(TAG, "preparePreview END");
        }
    }

    CameraCaptureSession.StateCallback getSessionCallback() {
        return new CameraCaptureSession.StateCallback() {
            @Override
            public void onConfigured(CameraCaptureSession session) {
                mCameraSession = session;
                startPreview();
            }

            @Override
            public void onConfigureFailed(CameraCaptureSession session) {
                CCLog.e(TAG, "onConfigureFailed");
            }

            @Override
            public void onReady(CameraCaptureSession session) {
                CCLog.d(TAG, "onReady : " + session);
            }

            @Override
            public void onClosed(CameraCaptureSession session) {
                CCLog.d(TAG, "onClosed : " + session);
            }
        };
    }

    protected void startPreview() {
        if (mCameraDevice == null) {
            CCLog.e(TAG, "startPreview error");
            return;
        }
        try {
            synchronized (mPreviewLock) {
                CCLog.d(TAG, "startPreview START");
                mPreviewBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
                mCameraSession.setRepeatingRequest(mPreviewBuilder.build(), mPreviewCaptureListener, mPreviewHandler);
                CCLog.d(TAG, "startPreview END");

                mInferenceManager.setState(true);
            }
        } catch (CameraAccessException e) {
            CCLog.e(TAG, "startPreview FAIL");
            e.printStackTrace();
        }
    }

    protected void stopPreview() {
        synchronized (mPreviewLock) {
            if (mCameraSession != null) {
                try {
                    CCLog.d(TAG, "stopPreview");
                    mCameraSession.stopRepeating();
                } catch (CameraAccessException cae) {
                    CCLog.e(TAG, "stopPreview FAIL");
                }
            }
        }
    }

    private Runnable mDelayPreviewRunnable = new Runnable() {
        @Override
        public void run() {
            startPreview();
        }
    };

    public void takePicture() {
        if (null == mCameraDevice) {
            CCLog.e(TAG, "CameraDevice null");
            return;
        }

        synchronized (mLock) {
            if (mShutterAction == PREVIEW_DUMP || mShutterAction == FULL_FRAME_DUMP) {
                CCLog.w(TAG, "Current shutter action is " + mShutterAction);
                return;
            }
        }

        //int rotation = ((Activity) mContext).getWindowManager().getDefaultDisplay().getRotation();
        int jpegOrientation = ORIENTATIONS.get(mOrientation);
        mShotOrientation = CCUtils.getOrientationValueForRotation(jpegOrientation);
        CCLog.d(TAG, "Capture Rotation : " + mOrientation + " jpeg Orientation = " + jpegOrientation + "Shot : " + mShotOrientation);

        stopPreview();

        try {
            final CaptureRequest.Builder captureBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureBuilder.addTarget(mOutputManager.getShotSurface());
//            captureBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);

            captureBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_START);

            // Orientation
            captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, jpegOrientation);

            captureBuilder.set(CaptureRequest.EDGE_MODE, CaptureRequest.EDGE_MODE_OFF);
            captureBuilder.set(CaptureRequest.NOISE_REDUCTION_MODE, CaptureRequest.NOISE_REDUCTION_MODE_OFF);

            if (mEnabledHdr) {
                captureBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
                //captureBuilder.set(CaptureRequest.CONTROL_AE_LOCK, true);
                captureBuilder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, mHdrFpsRange);
            } else {
                captureBuilder.set(CaptureRequest.CONTROL_AE_LOCK, true);
            }


            int[] captureEV = null;
            if (mEnabledHdr) {
                captureEV = new int[mCaptureCount];
                int stepCount = mCaptureCount / 2;
                float fMaxExposure = (float)mMaxExposure;
                float fStep = fMaxExposure / stepCount;

                CCLog.d(TAG, "EV : " + mMinExposure + " ~ " + mMaxExposure + " Step : " + (int)fStep);

                int currEV = mMaxExposure;

                for (int i = 0; i < mCaptureCount; i++) {
                    captureEV[i] = 0;
                }

                for (int i = 0; i < mCaptureCount / 2; i++) {
                    captureEV[i+1] = currEV;
                    captureEV[mCaptureCount - i - 1] = currEV * -1;

                    currEV = (int)(currEV - fStep);
                }
            }

            if (mShutterAction == YUV_SHOT) {
                ArrayList<CaptureRequest> requestList = new ArrayList<CaptureRequest>(mCaptureCount);

                mMultiFrameBufferManager.captureStart(mCaptureCount);
                for (int i = 0; i < mCaptureCount; i++) {
                    if (mEnabledHdr) {
                        CCLog.d(TAG, "Capture EV[" + i + "] = " + captureEV[i]);
                        captureBuilder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, captureEV[i]);
                    }
                    requestList.add(captureBuilder.build());
                    //mCameraSession.capture(captureBuilder.build(), mShotCaptureListener, mCaptureHandler);
                }
                mCameraSession.captureBurst(requestList, mShotCaptureListener, mCaptureHandler);
            } else {
                mCameraSession.capture(captureBuilder.build(), mShotCaptureListener, mCaptureHandler);
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

        startPreview();
    }

    public void closeCamera(boolean restart) {
        CCLog.d(TAG, "closeCamera START");
        synchronized (mPreviewLock) {
            if (mCameraSession != null) {
                try {
                    mCameraSession.stopRepeating();
                } catch (CameraAccessException cae) {
                    CCLog.e(TAG, "stopRepeating FAIL");
                }
                mCameraSession.close();
            }
            if (mCameraDevice != null) {
                mCameraDevice.close();
                mCameraDevice = null;
                if (!restart) {
                    mOutputManager.close();
                }
            }
        }
        CCLog.d(TAG, "closeCamera End");
    }

    public ImageReader.OnImageAvailableListener getOnPreviewImageAvailableListener() {
        ImageReader.OnImageAvailableListener previewReaderListener = new ImageReader.OnImageAvailableListener() {
            @Override
            public void onImageAvailable(ImageReader reader) {
                Image image = reader.acquireNextImage();
                if (image == null) {
                    return;
                }
                CCLog.d(TAG, "[Preview] image w = " + image.getWidth() + ", h = " + image.getHeight() + " timestamp : " + image.getTimestamp());
                if (mEnabledObjectDetection) {
                    mPictureCallbackHandler.post(getPreviewCallbackRunnable(image));
                }

                if (mRunningDump) {
                    mPictureCallbackHandler.post(getDumpRunnable(image));
                }

                if (!mEnabledObjectDetection && !mRunningDump) {
                    image.close();
                }

            }
        };
        return previewReaderListener;
    }

    public ImageReader.OnImageAvailableListener getOnFullFrameAvailableListener() {
        ImageReader.OnImageAvailableListener fullFrameReaderListener = new ImageReader.OnImageAvailableListener() {
            @Override
            public void onImageAvailable(ImageReader reader) {
                Image image = reader.acquireNextImage();
                if (image == null) {
                    return;
                }
//                CCLog.d(TAG, "[FullFrame] image w = " + image.getWidth() + ", h = " + image.getHeight() + " timestamp : " + image.getTimestamp());
                if (mShutterAction == YUV_SHOT) {
                    int frameIndex = mMultiFrameBufferManager.addImage(image);

                    if (mMultiFrameBufferManager.isCaptureFinish()) {
                        mMultiFrameBufferManager.captureFinish();
                        mPictureCallbackHandler.post(getShotYuvRunnable());
                    }
                } else if (mShutterAction == FULL_FRAME_DUMP) {
                    if (mRunningDump) {
                        mPictureCallbackHandler.post(getDumpRunnable(new CCImage(image)));
                    }
                    image.close();
                }
            }
        };
        return fullFrameReaderListener;
    }

    public ImageReader.OnImageAvailableListener getOnJpegAvailableListener() {
        ImageReader.OnImageAvailableListener jpgReaderListener = new ImageReader.OnImageAvailableListener() {
            @Override
            public void onImageAvailable(ImageReader reader) {
                Image image = reader.acquireNextImage();
                if (image == null) {
                    return;
                }
                try {
                    CCLog.d(TAG, "[Jpeg] image w = " + image.getWidth() + ", h = " + image.getHeight() + " timestamp : " + image.getTimestamp());
                    ByteBuffer jpegBuffer = image.getPlanes()[0].getBuffer();
                    byte[] jpegBytes = new byte[jpegBuffer.capacity()];
                    jpegBuffer.get(jpegBytes);
                    mPictureCallbackHandler.post(getPictureCallbackRunnable(jpegBytes));
                } catch (Exception e) {
                    CCLog.e(TAG, "Jpeg FAIL");
                }
                image.close();
            }
        };
        return jpgReaderListener;
    }

    private Runnable getPictureCallbackRunnable(final byte[] jpegBytes) {
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                boolean needExifUpdate = false;
                int orientation = CCUtils.readExifOrientation(CCUtils.openExif(jpegBytes));

                CCLog.d(TAG, "jpeg orientation : " + orientation);
                mPictureCallbackListener.onCaptureCallback(jpegBytes, orientation, needExifUpdate);
            }
        };
        return runnable;
    }


    private Runnable getShotYuvRunnable() {
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
//                byte[] resultBytes = null;
                if (!mEnabledCloudShot) {
                    byte[] jpegBytes = null;
                    ArrayList<Image> images = mMultiFrameBufferManager.getImages(mCaptureCount);
                    String dateStr = CCUtils.getDateString();
                    String dirPath = CCUtils.getDumpPath(CCConstants.YUV_DIR, dateStr);

                    for (int i = 0; i < images.size(); i++) {
                        new CCImage(images.get(i)).dump(dirPath);
//                        CCImage.dump(images.get(i), dirPath);


                        if (i == 0) {
                            CCImage ccImage = new CCImage(images.get(i));
                            jpegBytes = ccImage.compressJpeg(CCConstants.JPEG_QUALITY);
                        }
                    }
                    mPictureCallbackListener.onCaptureCallback(jpegBytes, mShotOrientation, true);
                }

                mMultiFrameBufferManager.clearBuffer();
//
            }
        };
        return runnable;
    }

    private Runnable getPreviewCallbackRunnable(final Image image) {
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                synchronized (mLock) {
                    if (!mReadyForObjectDetectionProcess) {
                        image.close();
                        return;
                    }
                }
                CCImage ccImage = new CCImage(image);
                image.close();
                mPictureCallbackListener.onPreviewCallback(ccImage);
            }
        };
        return runnable;
    }

    private void saveFile(byte[] jpegBytes, String dateStr) {
        String dirPath = CCUtils.getPath(CCConstants.JPG_DIR);
        String fileName = dateStr + ".jpg";

        final File file = new File(dirPath, fileName);
        OutputStream output = null;
        try {
            output = new FileOutputStream(file);
            output.write(jpegBytes);
            output.close();
        } catch (Exception e) {
            Toast.makeText(mContext, "Failed", Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        }
    }

    //For Preview size
    private Runnable getDumpRunnable(final Image image) {
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                synchronized (mLock) {
                    if (image != null && mRunningDump) {
                        CCImage.dump(image, mDumpDirPath);
                        image.close();
                    }
                }
            }
        };
        return runnable;
    }


    //For Full-frame Preview size
    private Runnable getDumpRunnable(final CCImage ccImage) {
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                synchronized (mLock) {
//                    if (ccImage != null && mRunningDump) {
//                        ccImage.dump(mDumpDirPath);
//                    }
                    if (!mCodecFinish) {
                        CCLog.d(TAG, "Codec put");
                        mCCVideoWriter.put(ccImage);
                    } else {
                        CCLog.d(TAG, "Codec finish");
                    }
                }
            }
        };
        return runnable;
    }

    private byte[] compressYuvToJpeg(Image image) {
        CCImage resultImage = new CCImage(image);
        return resultImage.compressJpeg(CCConstants.JPEG_QUALITY);
    }

    boolean mCodecFinish = false;
    CCVideoStreamWriter mCCVideoWriter;
    CCVideoStreamWriter.CCVideoWriterListener mWriterListener = new CCVideoStreamWriter.CCVideoWriterListener() {
        @Override
        public void onFinish(long encodingTime) {
            CCLog.d(TAG, "Finish");
            closeEncoder();
            mInferenceManager.setState(false);
            mCodecFinish = true;
            mRunningDump = false;

            mInferenceManager.setState(true);
        }

        public void onError(String errorMsg) {
            CCLog.d(TAG, "onError : " + errorMsg);
            closeEncoder();
        }

        public void onEncodedBuffer(ByteBuffer buffer, int size) {
            mInferenceManager.inferenceImage(0, buffer.array(), size);
        }
    };

    public void closeEncoder() {
        mPictureCallbackHandler.post(new Runnable() {
            @Override
            public void run() {
                mCCVideoWriter.close();
            }
        });
    }

    public void startDump(String dirPath) {
        synchronized (mLock) {
            mRunningDump = true;
            mDumpDirPath = dirPath;
            mVideoPath = mDumpDirPath + System.currentTimeMillis() + ".mp4";
            mDumpDirPath = mVideoPath;
            mCCVideoWriter = new CCVideoStreamWriter(mFullFrameSize.getWidth(), mFullFrameSize.getHeight(), mVideoPath, mWriterListener);
            //SET Video Config
            mCCVideoWriter.setBitPerPixel(0.18f);
            mCodecFinish = false;
            mCCVideoWriter.start();
        }
    }

    public String getDumpPath() {
        return mDumpDirPath;
    }

    public void stopDump() {
        synchronized (mLock) {
            mDumpDirPath = null;
            mPictureCallbackHandler.post(new Runnable() {
                @Override
                public void run() {
                    CCLog.d(TAG, "Stop Dump");
                    mCCVideoWriter.stop();
                }
            });
        }
    }

    public void setHdr(boolean enabledHdr) {
        mEnabledHdr = enabledHdr;
    }


    public void setObjectDetection(boolean enabledObjectDetection) {
        mEnabledObjectDetection = enabledObjectDetection;
        if (mEnabledObjectDetection) {
            mReadyForObjectDetectionProcess = true;
        }
    }

    public void setReadyForObjectDetectionProcess(boolean ready) {
        mReadyForObjectDetectionProcess = ready;
    }

    public void setCaptureCount(int captureCount) {
        mCaptureCount = captureCount;
    }

}