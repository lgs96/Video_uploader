package kr.ac.snu.nxc.cloudcamera.codec;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Process;
import android.os.SystemClock;

import java.io.IOException;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.concurrent.LinkedBlockingQueue;

import kr.ac.snu.nxc.cloudcamera.library.ImageUtils;
import kr.ac.snu.nxc.cloudcamera.util.CCImage;
import kr.ac.snu.nxc.cloudcamera.util.CCLog;

public class CCVideoStreamEncoder {
    private static final String TAG = "CCVideoStreamEncoder";
    
    private static final int TIMEOUT_USEC = 10000;

    private final Object mLock = new Object();

    private MediaFormat mMediaFormat;
    private MediaCodec mMediaCodec;
    private boolean mInputDone = false;
    private boolean mEndOfStream = false;
    private MediaFormat mOutputFormat;

    private LinkedList<CCImage> mCCImageList = null;

    private int mKeyFramePerSec = -1;
    private int mFPS = -1;
    private int mWidth = -1;
    private int mHeight = -1;
    private int mBitRate = -1;
    private int mBitRateMode = -1;
    private int mColorFormat;
    private long mPrevTimeStamp;

    private Handler mEncoderHandler;

    private int mOutputFrameCount = 0;

    private long mPtrusec = 0;

    private long mEncodingTime = 0;

    private CCVideoStreamEncoderListener mListener;

    private TRSEncoderListener mTRSListener;

    private int bufferSize = -1;

    private boolean mStarted = false;

    private boolean mFirstFrame = true;
    private boolean mFinish = false;

    public static LinkedBlockingQueue<CCImage> mEncodingQueue = new LinkedBlockingQueue<CCImage>(20);

    public interface CCVideoStreamEncoderListener {
        public void onError(String errorMsg);

        public void onFinish(long encodingTime);

        public void onOutputFormatChanged(MediaFormat format);

        public void onDrainOutputBuffer(MediaCodec.BufferInfo bufferInfo, ByteBuffer byteBuffer);
    }

    public interface TRSEncoderListener {
        public void onEncodedBuffer(MediaCodec.BufferInfo bufferInfo, ByteBuffer byteBuffer);
    }

//    public void setImageList(LinkedList<CCImage> imageList) {
//        mCCImageList = imageList;
//    }

    public void setSize(int width, int height) {
        mWidth = width;
        mHeight = height;
        bufferSize = mWidth * mHeight * 3 / 2;
    }

    public void setKeyFramePerSec(int keyFramePerSec) {
        mKeyFramePerSec = keyFramePerSec;
    }

    public void setFPS(int fps) {
        mFPS = fps;
    }

    public void setColorFormat(int colorFormat) {
        mColorFormat = colorFormat;
    }

    public void setBitrate(int bitrate) {
        mBitRate = bitrate;
    }

    public void setBitrateMode(int bitrateMode) {
        mBitRateMode = bitrateMode;
    }

    public void setListener(CCVideoStreamEncoderListener listener) {
        mListener = listener;
    }

    public void setTRSListener(TRSEncoderListener listener) {
        mTRSListener = listener;
    }

    public void start() {
        synchronized (mLock) {
            HandlerThread handlerThread = new HandlerThread("CCVideoEncoder", Process.THREAD_PRIORITY_FOREGROUND);
            handlerThread.start();
            mEncoderHandler = new Handler(handlerThread.getLooper());
            mEncoderHandler.post(new Runnable() {
                @Override
                public void run() {
                    try {
                        initialize();
                        encode();
                    } catch (IOException e) {
                        if (mListener != null) {
                            mListener.onError("Encoder IO Exception");
                        }
                        e.printStackTrace();
                        CCLog.e(TAG, e.getMessage());
                    }
                }
            });
        }
    }

    public void setNewMediaFormat(){

        synchronized(mLock) {
            CCLog.d(TAG, "Reset");
            mMediaCodec.stop();
            mMediaCodec.release();
            mMediaCodec = null;

            String type = MediaFormat.MIMETYPE_VIDEO_AVC;
            try {
                mMediaCodec = MediaCodec.createEncoderByType(type);
            }
            catch (Exception e){

            }
            mMediaFormat.setInteger(MediaFormat.KEY_WIDTH, 1920);
            mMediaFormat.setInteger(MediaFormat.KEY_HEIGHT, 1080);
            mMediaCodec.configure(mMediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            mMediaCodec.start();
        }
    }

    public void stop() {
        CCLog.d(TAG, "Stop");
        mFinish = true;
    }

    public void initialize() throws IOException {
        //mWidth = 1920;
        //mHeight = 1080;
        String type = MediaFormat.MIMETYPE_VIDEO_AVC;
        mMediaCodec = MediaCodec.createEncoderByType(type);
        mMediaFormat = MediaFormat.createVideoFormat(type, mWidth, mHeight);
        CCLog.d(TAG, "Codec Name = " + mMediaCodec.getCodecInfo().getName()
                + ", size : " + mMediaFormat.getInteger(MediaFormat.KEY_WIDTH) + "x" + mMediaFormat.getInteger(MediaFormat.KEY_HEIGHT));
        mMediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, mBitRate);
        mMediaFormat.setInteger(MediaFormat.KEY_BITRATE_MODE, mBitRateMode);
        mMediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, mFPS);
        mMediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, mColorFormat);
        mMediaFormat.setInteger(MediaFormat.KEY_COLOR_RANGE, MediaFormat.COLOR_RANGE_LIMITED);
        mMediaFormat.setInteger(MediaFormat.KEY_COLOR_STANDARD, MediaFormat.COLOR_STANDARD_BT601_NTSC);
        mMediaFormat.setInteger(MediaFormat.KEY_STRIDE, mWidth);

        //mMediaFormat.setInteger(MediaFormat.KEY_MAX_B_FRAMES, 1);
//        mMediaFormat.setInteger(MediaFormat.KEY_PROFILE, MediaCodecInfo.CodecProfileLevel.AVCProfileHigh);
//        mMediaFormat.setInteger(MediaFormat.KEY_LEVEL, MediaCodecInfo.CodecProfileLevel.AVCLevel41);

        float iFrameInterval = 1.0f;
        if (mKeyFramePerSec == 0) {
            iFrameInterval = 0;
        } else {
            iFrameInterval = 1 / (float)mKeyFramePerSec;
        }
        mMediaFormat.setFloat(MediaFormat.KEY_I_FRAME_INTERVAL, iFrameInterval);
        mPrevTimeStamp = 0;//mCCImageList.get(0).mTimestamp;

        CCLog.d(TAG, "Bitrate : " + mBitRate + "I Frame Interval : " + iFrameInterval
                + " COLOR_STANDARD_BT709 - COLOR_RANGE_FULL");
    }

    public void encode() {
        CCLog.d(TAG, "encode");
        mOutputFrameCount = 0;
        if (mMediaCodec == null) {
            CCLog.d(TAG, "mMediaCodec is null, return");
            if (mListener != null) {
                mListener.onError("Encoder is Null");
            }
            return;
        }
        try {
            long start = SystemClock.uptimeMillis();

            mMediaCodec.configure(mMediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            mMediaCodec.start();
            mStarted = true;
            mOutputFormat = mMediaCodec.getOutputFormat();

            long end = SystemClock.uptimeMillis();
            mEncodingTime += (end - start);

            encodeVideoFromBuffer();
        } catch (Exception e) {
            e.printStackTrace();
            if(mListener != null) {
                mListener.onError("Encode Exception");
            }
            return;
        }

        CCLog.d(TAG, "finish");
        if (mListener != null) {
            mListener.onFinish(mEncodingTime);
        }
        CCLog.d(TAG, "output frameCount = " + mOutputFrameCount);
    }

    private void encodeVideoFromBuffer() {
        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
        CCLog.d(TAG, "Encoding START");
        mInputDone = false;
        mEndOfStream = false;
        while (!mEndOfStream) {
            try {
                //CCLog.d(TAG, "Enter occurred in Encoder");
                CCLog.d(TAG, "TRS: encoding start bitrate: " + mBitRate);
                putCodecInputBuffer();
                getCodecOutputBuffer(bufferInfo);
                CCLog.d(TAG, "TRS: encoding end");
            }
            catch (Exception e){
                //CCLog.d(TAG, "Error occurred in Encoder");
            }
        }
        CCLog.d(TAG, "Encoding END mOutputFrameCount : " + mOutputFrameCount + ", bufferInfo.flags : " + bufferInfo.flags);
        return;
    }

    public void putEncodingBuffer(CCImage image) {
        CCLog.d(TAG, "Queue Size : " + mEncodingQueue.size());
        try {
            if (!mInputDone && mEncodingQueue.size() < 20) {
                mEncodingQueue.put(image);
            }
        } catch (Exception e) {
            CCLog.e(TAG, "Error : " + e.getMessage());
        }
    }

    private void putCodecInputBuffer() {
            if (mInputDone) {
                return;
            }

            if (mKeyFramePerSec == 0 || mFirstFrame) {
                CCLog.d(TAG, "Request Sync Input");
                Bundle bundle = new Bundle();
                bundle.putInt(MediaCodec.PARAMETER_KEY_REQUEST_SYNC_FRAME, 0);
                mMediaCodec.setParameters(bundle);
            }

            long start = SystemClock.uptimeMillis();
            int inputBufIndex = mMediaCodec.dequeueInputBuffer(TIMEOUT_USEC);
            long end = SystemClock.uptimeMillis();
            mEncodingTime += (end - start);

//        if (inputBufIndex >= 0 && mCCImageList.size() > 0) {
            CCImage ccImage = null;
            try {
                CCLog.d(TAG, "take");
                ccImage = mEncodingQueue.take();
            } catch (Exception e) {
                CCLog.e(TAG, "Error : " + e.getMessage());
            }
            if (inputBufIndex >= 0 && ccImage != null) {
                CCLog.d(TAG, "inputBuffer index : " + inputBufIndex);
                ByteBuffer inputBuffer = mMediaCodec.getInputBuffer(inputBufIndex);

//            CCImage ccImage = mCCImageList.removeFirst();
                CCLog.d(TAG, "inputBuffer capacity : " + inputBuffer.capacity() + " data : " + ccImage.mYuvBuffer.capacity());
                byte[] byteCCImage = ccImage.getYuvBuffer().array();
                if (byteCCImage != null) {
                    try {
                        CCLog.d(TAG, "Swap UV before encoding");
                        ImageUtils.swapUV(ccImage);

                        start = SystemClock.uptimeMillis();
                        //inputBuffer.put(byteCCImage, 0, ccImage.mYuvBuffer.capacity());
                        inputBuffer.put(ccImage.mYuvBuffer);
                        end = SystemClock.uptimeMillis();
                        mEncodingTime += (end - start);

                        CCLog.d(TAG, "Swap UV after encoding");
                        ImageUtils.swapUV(ccImage);

                    } catch (BufferOverflowException e) {
                        CCLog.e(TAG, "input data length = " + byteCCImage.length);
                        CCLog.e(TAG, "buffer capacity = " + inputBuffer.capacity());
                        CCLog.e(TAG, "buffer limit = " + inputBuffer.limit());
                        e.printStackTrace();
                        if (mListener != null) {
                            mListener.onError("BufferOverflowException");
                        }
                        return;
                    } catch (Exception e) {
                        e.printStackTrace();
                        if (mListener != null) {
                            mListener.onError("putCodecInputBuffer Exception");
                        }
                        return;
                    }

                    start = SystemClock.uptimeMillis();

                    if (mPrevTimeStamp == 0) {
                        mPrevTimeStamp = ccImage.mTimestamp;
                    }

                    mPtrusec += ((ccImage.mTimestamp - mPrevTimeStamp) / 1000);
                    mPrevTimeStamp = ccImage.mTimestamp;

                    int flag = 0;
                    if (mFinish) {
                        CCLog.d(TAG, "BUFFER_FLAG_END_OF_STREAM");
                        flag = flag | MediaCodec.BUFFER_FLAG_END_OF_STREAM;
                        mInputDone = true;
                        mEncodingQueue.clear();
                    }

                    if (mKeyFramePerSec == 0 || mFirstFrame) {
                        flag = flag | MediaCodec.BUFFER_FLAG_KEY_FRAME;
                        mFirstFrame = false;

                        CCLog.d(TAG, "FLAG : " + flag);
                    }
                    mMediaCodec.queueInputBuffer(inputBufIndex, 0, ccImage.mYuvBuffer.capacity(), mPtrusec, flag);
                    mOutputFrameCount++;

                    end = SystemClock.uptimeMillis();
                    mEncodingTime += (end - start);
                }
            }

    }

    private void getCodecOutputBuffer(MediaCodec.BufferInfo bufferInfo) {
        if (mEndOfStream) {
            return;
        }
        long start = SystemClock.uptimeMillis();

        CCLog.d(TAG, "resolution debugger: " + mMediaFormat.getInteger(MediaFormat.KEY_WIDTH) +" " + mMediaCodec.getOutputFormat());

        int outputBufferId = mMediaCodec.dequeueOutputBuffer(bufferInfo, TIMEOUT_USEC);
        if (outputBufferId >= 0) {
            CCLog.i(TAG, "outputBuffer = " + outputBufferId);

            ByteBuffer outputBuffer = mMediaCodec.getOutputBuffer(outputBufferId);
            if (mListener != null) {
                mListener.onDrainOutputBuffer(bufferInfo, outputBuffer);
            }
            if (mTRSListener != null){
                CCLog.d(TAG, "TRS: callback for upload");
                mTRSListener.onEncodedBuffer(bufferInfo, outputBuffer);
            }
            mMediaCodec.releaseOutputBuffer(outputBufferId, false);
            if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                CCLog.d(TAG, "video output EOS. frameCount : " + mOutputFrameCount);
                mEndOfStream = true;
            }
        } else if (outputBufferId == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
            mOutputFormat = mMediaCodec.getOutputFormat();
            CCLog.i(TAG, "onOutputFormatChanged = " + mOutputFormat.toString());
           // mListener.onOutputFormatChanged(mMediaCodec.getOutputFormat());
        }

        long end = SystemClock.uptimeMillis();
        mEncodingTime += (end - start);
    }

    private void stopThread() {
        synchronized(mLock) {
            if (mEncoderHandler != null) {
                mEncoderHandler.getLooper().quitSafely();
                mEncoderHandler = null;
            }
        }
    }

    public void close() {
        synchronized(mLock) {
            if (mMediaCodec != null) {
                if (mStarted) {
                    mMediaCodec.stop();
                    mStarted = false;
                }
                mMediaCodec.release();
                mMediaCodec = null;
                CCLog.d(TAG, "encoder close");
            }

            if (mCCImageList != null) {
                mCCImageList.clear();
                mCCImageList = null;
            }
            stopThread();
            if (mListener != null)
                mListener.onFinish(0);
        }
    }
}
