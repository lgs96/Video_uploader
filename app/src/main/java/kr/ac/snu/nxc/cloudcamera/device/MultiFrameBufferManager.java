package kr.ac.snu.nxc.cloudcamera.device;

import android.media.Image;

import java.util.ArrayList;

import kr.ac.snu.nxc.cloudcamera.util.CCLog;

public class MultiFrameBufferManager {
    static final String TAG = "MultiFrameBufferManager";
    static final int MAX_BUFFER_COUNT = 10;

    protected final Object mLock = new Object();

    private Image[] mImages;
    private int mCaptureCount = 0;
    private int mImageCount = 0;
    private long mFullFrameInterval = 0;

    public MultiFrameBufferManager() {
        mImages = new Image[MAX_BUFFER_COUNT];
    }

    public void captureStart(int captureCount) {
        mFullFrameInterval = System.currentTimeMillis();
        mCaptureCount = captureCount;
    }

    public void captureFinish() {

    }

    public boolean isCaptureFinish() {
        return mCaptureCount == mImageCount;
    }

    public void clearBuffer() {
        synchronized (mLock) {
            for (int i = 0; i < mImages.length; i++) {
                if (mImages[i] != null) {
                    mImages[i].close();
                    mImages[i] = null;
                }
            }
        }
        mImageCount = 0;
        CCLog.d(TAG, "clearBuffer");
    }

    public int addImage(Image image) {
        synchronized (mLock) {
            mImages[mImageCount] = image;
            int frameIndex = mImageCount;

            mImageCount++;

            long currentTime = System.currentTimeMillis();
            long interval = currentTime - mFullFrameInterval;
            mFullFrameInterval = currentTime;
            CCLog.d(TAG, "FullFrameInterval : " + interval + " ms");

            return frameIndex;
        }
    }

    public ArrayList<Image> getImages(int count) {
        synchronized (mLock) {
            if (mImageCount == 0) {
                return null;
            }
            if (count != mImageCount) {
                CCLog.w(TAG, "Capture fail count : " + (count - mImageCount));
            }
            ArrayList<Image> images = new ArrayList<Image>(mImageCount);
            for (int i = 0; i < mImageCount; i++) {
                if (mImages[i] != null) {
                    images.add(mImages[i]);
                }
            }
            return images;
        }
    }
}
