package kr.ac.snu.nxc.cloudcamera.codec;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;

import java.nio.ByteBuffer;
import java.util.LinkedList;

import kr.ac.snu.nxc.cloudcamera.StreamInferenceManager;
import kr.ac.snu.nxc.cloudcamera.codec.CCVideoStreamEncoder.CCVideoStreamEncoderListener;
import kr.ac.snu.nxc.cloudcamera.util.CCImage;
import kr.ac.snu.nxc.cloudcamera.util.CCLog;

public final class CCVideoStreamWriter {
    private static final String TAG = "CCVideoStreamWriter";

    private final Object mLock = new Object();

    public int FPS = 60;
    protected static final int KEY_FRAME_PER_SEC = 1;

    CCVideoStreamEncoder mEncoder = null;
    MediaMuxer mMediaMuxer = null;
    boolean mStart = false;
    int mTrackIndexOfVideo = 0;

    private int mWidth = -1;
    private int mHeight = -1;
    private int mFrameCount = 0;

    private String mSaveFilePath = null;

    LinkedList<CCImage> mCCImageList = new LinkedList<CCImage>();

    int mOutputIndex = 0;

    private int mIFramePerSec = 1;
    private float mBitPerPixel = -1;

    private CCVideoWriterListener mWriterListener;

    boolean mMuxerStarted = false;


    public interface CCVideoWriterListener {
        public void onFinish(long encodingTime);

        public void onError(String errorMsg);

        public void onEncodedBuffer(ByteBuffer buffer, int size);
    }

    public CCVideoStreamWriter(int width, int height, String path, CCVideoWriterListener listener) {
        mWidth = width;
        mHeight = height;
        mSaveFilePath = path;
        mWriterListener = listener;

        initBitPerPixel();
    }

    public void setNewMediaFormat () {
        mEncoder.setNewMediaFormat();
    }

    public void put(CCImage ccImage) {
        mEncoder.putEncodingBuffer(ccImage);
    }

    public void stop() {
        mEncoder.stop();
    }

    public void start() {
        CCLog.d(TAG, "CCVideoWriter start Image Count = " + mCCImageList.size());
        synchronized (mLock) {
            try {
                //mMediaMuxer = new MediaMuxer(mSaveFilePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
                mStart = true;
            } catch (Exception e) {
                mWriterListener.onError("MediaMuxer Exception");
                e.printStackTrace();
                return;
            }

            mFrameCount = mCCImageList.size();

            CCLog.d(TAG, "FPS/Bitrate/BPP: "+FPS +" "+ " " +getBitrate()+ " "+ mBitPerPixel);

            mEncoder = new CCVideoStreamEncoder();
//            mEncoder.setImageList(mCCImageList);
            mEncoder.setSize(mWidth, mHeight);
            mEncoder.setKeyFramePerSec(mIFramePerSec);
            mEncoder.setFPS(FPS);
            mEncoder.setListener(mListener);
            mEncoder.setColorFormat(MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar);
            mEncoder.setBitrate(getBitrate());
            mEncoder.setBitrateMode(MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR);
            mEncoder.start();
        }
    }

    /**
     This key is used by video encoders.
     A negative value means no key frames are requested after the first frame.
     A zero value means a stream containing all key frames is requested.
     */
    public void setIFramePerSec(int iFramePerSec) {
        mIFramePerSec = iFramePerSec;
    }

    public void setBitPerPixel(float bitPerPixel) {
        mBitPerPixel = bitPerPixel;
    }

    public void setBitRate (float bitrate) {
        mBitPerPixel = bitrate/(FPS * mWidth * mHeight);
        //mBitPerPixel = 0.18f;
        CCLog.d(TAG, "Bitrate: "+ bitrate+ " Bitperpixel: "+ mBitPerPixel + " " + FPS + " "+ mWidth + " " + mHeight);
    }


    public boolean close() {
        boolean success = true;
        mStart = false;

        synchronized (mLock) {
            if (mMediaMuxer != null) {
                CCLog.d(TAG, "Media Muxer stop");
                if (mMuxerStarted) {
                    try {
                        mMediaMuxer.stop();
                        mMediaMuxer.release();
                        mMediaMuxer = null;
                    } catch (Exception e) {
                        success = false;
                        CCLog.e(TAG, "Stop fail");
                        e.printStackTrace();
                    }
                    mMuxerStarted = false;
                }
            }

            if (mEncoder != null) {
                CCLog.d(TAG, "Encoder Close from streamwriter");
                mEncoder.close();
                mEncoder = null;
            }
            mCCImageList.clear();
        }
        return success;
    }

    private void initBitPerPixel() {
        if ((mWidth *mHeight) >= (2400*1080)) {
            mBitPerPixel = 0.18f;
        }
        else if ((mWidth *mHeight) >= (1920*1080)) {
            mBitPerPixel = 0.25f;
        }
        else if ((mWidth *mHeight) >= (1440*1080)) {
            mBitPerPixel = 0.35f;
        }
        else if ((mWidth *mHeight) >= (1560*720)) {
            mBitPerPixel = 0.35f;
        } else {
            mBitPerPixel = 1.0f;
        }
    }

    public int getBitrate() {
        int bitrate = (int)(mBitPerPixel * FPS * mWidth * mHeight);
        CCLog.d(TAG, "BPP : " + mBitPerPixel + " bitrate : " + bitrate/1e6);

        return bitrate;
    }

    private CCVideoStreamEncoderListener mListener = new CCVideoStreamEncoderListener() {
        @Override
        public void onError(String errorMsg) {
            if (mWriterListener != null) {
                mWriterListener.onError(errorMsg);
            }
        }

        @Override
        public void onFinish(long encodingTime) {
            if (mWriterListener != null) {
                mWriterListener.onFinish(encodingTime);
            }
        }

        @Override
        public void onOutputFormatChanged(MediaFormat format) {
            CCLog.d(TAG, "onOutputFormatChanged");
            mTrackIndexOfVideo = mMediaMuxer.addTrack(format);
            mMediaMuxer.start();
            mMuxerStarted = true;
        }

        ByteBuffer mFrameBuffer = ByteBuffer.allocate(1024 * 1024 * 10);

        @Override
        public void onDrainOutputBuffer(MediaCodec.BufferInfo bufferInfo, ByteBuffer byteBuffer) {
            CCLog.d(TAG, "onDrainOutputBuffer : " + mOutputIndex);
            CCLog.d(TAG, "Buffer offset : " + bufferInfo.offset + " buffer size : " + bufferInfo.size);
            //if (mMediaMuxer != null) {
            if (mStart){
                synchronized(mLock) {
                    MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
                    info.set(bufferInfo.offset, bufferInfo.size, bufferInfo.presentationTimeUs, bufferInfo.flags);

                    try {
                        // This code saves file, not required for streaming.. (Goodsol to be fixed)
                        //mMediaMuxer.writeSampleData(mTrackIndexOfVideo, byteBuffer, info);
                        mOutputIndex++;

                        byteBuffer.clear();
                        byteBuffer.position(bufferInfo.offset);
                        byteBuffer.limit(bufferInfo.offset + bufferInfo.size);
                        ByteBuffer streamBuffer = ByteBuffer.allocate(bufferInfo.size);
                        streamBuffer.put(byteBuffer);
//                    byte[] socketWriteBuffer = mFrameBuffer.array();

                        mWriterListener.onEncodedBuffer(streamBuffer, bufferInfo.size);
                        //socket write [0] ~ [bufferInfo.size]
                    }
                    catch (Exception e){
                        CCLog.d(TAG, "Error during onDrainOutputBuffer: " + e);
                    }

                }
            }
        }
    };
}
