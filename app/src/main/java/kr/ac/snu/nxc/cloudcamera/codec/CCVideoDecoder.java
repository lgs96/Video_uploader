package kr.ac.snu.nxc.cloudcamera.codec;

import android.media.Image;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Process;

import java.io.IOException;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;

import kr.ac.snu.nxc.cloudcamera.library.ImageUtils;
import kr.ac.snu.nxc.cloudcamera.util.CCImage;
import kr.ac.snu.nxc.cloudcamera.util.CCLog;

public class CCVideoDecoder {
    private static final String TAG = "CCVideoDecoder";
    private static final int TIMEOUT_USEC = 10000;

    private final Object mLock = new Object();

    private boolean mInputDone = false;
    private boolean mEndOfStream = false;

    private Handler mDecoderHandler;

    MediaCodec mMediaCodec = null;
    MediaExtractor mMediaExtractor = null;
    private MediaFormat mMediaFormat;

    private int mOutputFrameCount = 0;

    private CCVideoDecoderListener mListener;

    public interface CCVideoDecoderListener {
        public void onError(String errorMsg);

        public void onFinish();

        public void onDrainOutputImage(int index, Image image);
    }

    public CCVideoDecoder(MediaExtractor mediaExtractor, MediaFormat mediaFormat) {
        mMediaExtractor = mediaExtractor;
        mMediaFormat = mediaFormat;
    }

    public void setListener(CCVideoDecoderListener listener) {
        mListener = listener;
    }

    public void start(final String mime) {
        synchronized (mLock) {
            HandlerThread handlerThread = new HandlerThread("CCVideoDecoder", Process.THREAD_PRIORITY_FOREGROUND);
            handlerThread.start();
            mDecoderHandler = new Handler(handlerThread.getLooper());
            mDecoderHandler.post(new Runnable() {
                @Override
                public void run() {
                    try {
                        initialize(mime);
                        decode();
                    } catch (IOException e) {
                        if (mListener != null) {
                            mListener.onError("Decoder IO Exception");
                        }
                        e.printStackTrace();
                        CCLog.e(TAG, e.getMessage());
                    }
                }
            });
        }
    }


    public void initialize(final String mime) throws IOException {
        CCLog.d(TAG, "Target MIME : " + mime);
        CCLog.d(TAG, "Target Color : " + MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar);

        mMediaCodec = MediaCodec.createDecoderByType(mime);
        showSupportedColorFormat(mMediaCodec.getCodecInfo().getCapabilitiesForType(mime));
        mMediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar);
    }

    public void decode() {
        CCLog.d(TAG, "decode");
        if (mMediaCodec == null) {
            CCLog.d(TAG, "mMediaCodec is null, return");
            if (mListener != null) {
                mListener.onError("Decoder is null");
            }
            return;
        }
        try {
            mMediaCodec.configure(mMediaFormat, null, null, 0);
            mMediaCodec.start();
            decodeVideoFromBuffer();
        } catch (Exception e) {
            e.printStackTrace();
            if (mListener != null) {
                mListener.onError("Decode Exception");
            }
            return;
        }
        CCLog.d(TAG, "finish");
        if (mListener != null) {
            mListener.onFinish();
        }
    }

    private void decodeVideoFromBuffer() {
        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
        final int width = mMediaFormat.getInteger(MediaFormat.KEY_WIDTH);
        final int height = mMediaFormat.getInteger(MediaFormat.KEY_HEIGHT);

        CCLog.d(TAG, "Video Decoding : " + width + "x" + height);
        CCLog.d(TAG, "Decoding START");

        mInputDone = false;
        mEndOfStream = false;
        while (!mEndOfStream) {
            putCodecInputBuffer();
            getCodecOutputBuffer(bufferInfo);
        }
        CCLog.d(TAG, "Decoding END mOutputFrameCount : " + mOutputFrameCount);
        return;
    }

    private void putCodecInputBuffer() {
        if (mInputDone) {
            return;
        }
        int inputBufferId = mMediaCodec.dequeueInputBuffer(TIMEOUT_USEC);
        CCLog.d(TAG, "dequeueInputBuffer : " + inputBufferId);

        if (inputBufferId >= 0) {
            ByteBuffer inputBuffer = mMediaCodec.getInputBuffer(inputBufferId);

            CCLog.d(TAG, "inputBuffer capacity : " + inputBuffer.capacity());

            int sampleSize = mMediaExtractor.readSampleData(inputBuffer, 0);
            if (sampleSize < 0) {
                CCLog.d(TAG, "queueInputBuffer EOS : " + inputBufferId);
                mMediaCodec.queueInputBuffer(inputBufferId, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                mInputDone = true;
            } else {
                CCLog.d(TAG, "queueInputBuffer Data : " + inputBufferId);
                long presentationTimeUs = mMediaExtractor.getSampleTime();
                mMediaCodec.queueInputBuffer(inputBufferId, 0, sampleSize, presentationTimeUs, 0);
                mMediaExtractor.advance();
            }
        }
    }

    private void getCodecOutputBuffer(MediaCodec.BufferInfo bufferInfo) {
        if (mEndOfStream) {
            return;
        }
        int outputBufferId = mMediaCodec.dequeueOutputBuffer(bufferInfo, TIMEOUT_USEC);

        if (outputBufferId >= 0) {
            if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                mEndOfStream = true;
            }

            if (bufferInfo.size != 0) {
                Image image = mMediaCodec.getOutputImage(outputBufferId);
                mListener.onDrainOutputImage(mOutputFrameCount, image);
                image.close();
                mMediaCodec.releaseOutputBuffer(outputBufferId, true);
                mOutputFrameCount++;
            }
        }
    }

    private void stopThread() {
        synchronized(mLock) {
            if (mDecoderHandler != null) {
                mDecoderHandler.getLooper().quitSafely();
                mDecoderHandler = null;
            }
            CCLog.d(TAG, "stopThread");
        }
    }

    public void close() {
        synchronized(mLock) {
            if (mMediaCodec != null) {
                mMediaCodec.stop();
                mMediaCodec.release();
                mMediaCodec = null;
                CCLog.d(TAG, "decoder close");
            }
            mMediaExtractor = null;
            stopThread();
            CCLog.d(TAG, "decoder close end");
        }
    }

    private void showSupportedColorFormat(MediaCodecInfo.CodecCapabilities caps) {
        System.out.print("supported color format: ");
        for (int c : caps.colorFormats) {
            CCLog.d(TAG, "Color : " + c);
        }
    }

}
