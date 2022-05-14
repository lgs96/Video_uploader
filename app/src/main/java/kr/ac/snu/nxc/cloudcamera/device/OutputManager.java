package kr.ac.snu.nxc.cloudcamera.device;

import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.media.ImageReader;
import android.os.Handler;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;

import java.util.ArrayList;

import kr.ac.snu.nxc.cloudcamera.util.CCLog;

import static kr.ac.snu.nxc.cloudcamera.util.CCConstants.ShutterAction;
import static kr.ac.snu.nxc.cloudcamera.util.CCConstants.ShutterAction.FULL_FRAME_DUMP;
import static kr.ac.snu.nxc.cloudcamera.util.CCConstants.ShutterAction.JPG_SHOT;
import static kr.ac.snu.nxc.cloudcamera.util.CCConstants.ShutterAction.PREVIEW_DUMP;
import static kr.ac.snu.nxc.cloudcamera.util.CCConstants.ShutterAction.YUV_SHOT;

public class OutputManager {
    private static final String TAG = "OutputManager";

    public static final int SURFACE_PREVIEW = 0;
    public static final int SURFACE_JPEG = 1;
    public static final int SURFACE_YUV = 2;
    public static final int SURFACE_PREVIEW_CALLBACK = 2;
    
    private static final int CONCURRENT_YUV = 14;
    private static final int CONCURRENT_PREVIEW = 10;

    private Handler mPreviewHandler;
    private Handler mFullFrameHandler;
    private Handler mPreviewImageHandler;
    private Handler mCaptureHandler;

    TextureView mTextureView;

    Size mPreviewSize;
    Size mFullFrameSize;
    Size mJpegSize;

    CameraOps mCameraOps = null;

    ImageReader mPreviewImageReader = null;
    ImageReader mFullFrameReader = null;
    ImageReader mJpgReader = null;

    Surface mPreviewSurface = null;
    Surface mPreviewImageSurface = null;
    Surface mFullFrameSurface = null;
    Surface mJpegSurface = null;

    //YuvCallback
    ShutterAction mShutterAction = JPG_SHOT;

    private ArrayList<Surface> mOutputSurfaces = new ArrayList<Surface>();

    public OutputManager(CameraOps cameraOps, Handler previewImageHandler, Handler fullFrameHandler, Handler captureHandler, TextureView textureView) {
        mCameraOps = cameraOps;
        mPreviewImageHandler = previewImageHandler;
        mFullFrameHandler = fullFrameHandler;
        mCaptureHandler = captureHandler;

        mTextureView = textureView;
    }

    public void setImageSize(Size previewSize, Size fullFrameSize, Size jpegSize) {
        mPreviewSize = previewSize;
        mFullFrameSize = fullFrameSize;
        mJpegSize = jpegSize;
    }

    public ArrayList<Surface> getOutputSurfaces() {
        return mOutputSurfaces;
    }

    public void preparePreviewTexture() {
        SurfaceTexture texture = mTextureView.getSurfaceTexture();
        if (null == texture) {
            CCLog.e(TAG, "texture null");
            return;
        }

        texture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
    }

    public Surface getPreviewSurface() {
        return mPreviewSurface;
    }

    public Surface getPreviewImageSurface() {
        return mPreviewImageSurface;
    }

    public Surface getFullFrameSurface() {
        return mFullFrameSurface;
    }

    public Surface getJpegSurface() {
        return mJpegSurface;
    }

    public Surface getShotSurface() {
        if (mShutterAction == YUV_SHOT) {
            return getFullFrameSurface();
        } else {
            return getJpegSurface();
        }
    }

    public void setPreviewSurface() {
        CCLog.d(TAG, "setPreviewSurface");
        SurfaceTexture texture = mTextureView.getSurfaceTexture();
        mPreviewSurface = new Surface(texture);
        //mPreviewOutputConfig = new OutputConfiguration(mPreviewSize, SurfaceTexture.class);
    }

    public void setPreviewImageReaderSurface() {
        CCLog.d(TAG, "setPreviewImageReaderSurface");
        mPreviewImageReader = ImageReader.newInstance(mPreviewSize.getWidth(), mPreviewSize.getHeight(), ImageFormat.YUV_420_888, CONCURRENT_PREVIEW);
        mPreviewImageReader.setOnImageAvailableListener(mCameraOps.getOnPreviewImageAvailableListener(), mPreviewImageHandler);
        mPreviewImageSurface = mPreviewImageReader.getSurface();
        //FullFrameOutputConfig = new OutputConfiguration(mPreviewImageHandler);
    }

    public void setFullFrameReaderSurface() {
        CCLog.d(TAG, "setFullFrameReaderSurface");
        mFullFrameReader = ImageReader.newInstance(mFullFrameSize.getWidth(), mFullFrameSize.getHeight(), ImageFormat.YUV_420_888, CONCURRENT_YUV);
        mFullFrameReader.setOnImageAvailableListener(mCameraOps.getOnFullFrameAvailableListener(), mFullFrameHandler);
        mFullFrameSurface = mFullFrameReader.getSurface();
        //mFullFrameOutputConfig = new OutputConfiguration(mFullFrameSurface);
    }

    public void setJpgReaderSurface() {
        CCLog.d(TAG, "setJpgReaderSurface");
        mJpgReader = ImageReader.newInstance(mJpegSize.getWidth(), mJpegSize.getHeight(), ImageFormat.JPEG, 1);
        mJpgReader.setOnImageAvailableListener(mCameraOps.getOnJpegAvailableListener(), mCaptureHandler);
        mJpegSurface = mJpgReader.getSurface();
    }

    void configureOutputSurfaces(ShutterAction shutterAction) {
        CCLog.d(TAG, "SHUTTER_ACTION : " + shutterAction);

        mShutterAction = shutterAction;

        mOutputSurfaces.clear();

        setPreviewSurface();

        if (mShutterAction == PREVIEW_DUMP && mPreviewImageReader == null) {
            CCLog.d(TAG, "setPreviewImageReaderSurface");
            setPreviewImageReaderSurface();
        } else if (!(mShutterAction == PREVIEW_DUMP) && mPreviewImageReader != null) {
            CCLog.d(TAG, "closePreviewImageReader");
            closePreviewImageReader();
        }

        if ((mShutterAction == FULL_FRAME_DUMP || mShutterAction == YUV_SHOT) && mFullFrameReader == null) {
            CCLog.d(TAG, "setFullFrameReaderSurface");
            setFullFrameReaderSurface();
        } else if ((!(mShutterAction == FULL_FRAME_DUMP) &&  !(mShutterAction == YUV_SHOT)) && mFullFrameReader != null) {
            CCLog.d(TAG, "closeFullFrameReader");
            closeFullFrameReader();
        }

        if (mShutterAction == JPG_SHOT && mJpgReader == null) {
            CCLog.d(TAG, "setJpgReaderSurface");
            setJpgReaderSurface();
        } else if (!(mShutterAction == JPG_SHOT) && mJpgReader != null) {
            CCLog.d(TAG, "closeJpgReader");
            closeJpegReader();
        }

        CCLog.d(TAG, "add PreviewSurface");
        mOutputSurfaces.add(mPreviewSurface);
        if (mShutterAction == PREVIEW_DUMP) {
            CCLog.d(TAG, "add PreviewImageSurface");
            mOutputSurfaces.add(mPreviewImageSurface);
        }
        if ((mShutterAction == FULL_FRAME_DUMP || mShutterAction == YUV_SHOT)) {
            CCLog.d(TAG, "add FullFrameSurface");
            mOutputSurfaces.add(mFullFrameSurface);
        }

        if (mShutterAction == JPG_SHOT) {
            CCLog.d(TAG, "add JpegSurface");
            mOutputSurfaces.add(mJpegSurface);
        }
    }

    void close() {
        CCLog.d(TAG, "close");
        closePreviewImageReader();
        closeFullFrameReader();
        closeJpegReader();
    }

    void closePreviewImageReader() {
        if (mPreviewImageReader != null) {
            CCLog.d(TAG, "closePreviewImage");
            mPreviewImageReader.close();
            mPreviewImageReader = null;
            mPreviewImageSurface = null;
        }
    }

    void closeFullFrameReader() {
        if (mFullFrameReader != null) {
            CCLog.d(TAG, "closeFullFrameImage");
            mFullFrameReader.close();
            mFullFrameReader = null;
            mFullFrameSurface = null;
        }
    }

    void closeJpegReader() {
        if (mJpgReader != null) {
            CCLog.d(TAG, "closeJpegReader");
            mJpgReader.close();
            mJpgReader = null;
            mJpegSurface = null;
        }
    }
}
