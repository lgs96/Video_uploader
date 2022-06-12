package kr.ac.snu.nxc.cloudcamera.codec;

import android.media.Image;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.concurrent.LinkedBlockingQueue;

import kr.ac.snu.nxc.cloudcamera.codec.CCVideoDecoder.CCVideoDecoderListener;
import kr.ac.snu.nxc.cloudcamera.util.CCImage;
import kr.ac.snu.nxc.cloudcamera.util.CCLog;

import static kr.ac.snu.nxc.cloudcamera.util.CCConstants.MAX_CODEC_QUEUE_SIZE;

public class CCVideoReader {
    private static final String TAG = "CCVideoReader";

    private final Object mLock = new Object();

    MediaExtractor mExtractor = null;
    int mTrackIndexOfVideo = 0;

    CCVideoDecoder mDecoder = null;

    String mSavedFilePath = null;

    LinkedBlockingQueue<CCImage> mCodecFrameQueue = new LinkedBlockingQueue<CCImage>(MAX_CODEC_QUEUE_SIZE);

    private CCVideoReaderListener mReaderListener;

    // RL
    public int init_width;
    public int init_height;

    public interface CCVideoReaderListener {
        public void onFinish();

        public void onError(String errorMsg);

        public void onDecodedImage(int index, CCImage ccImage);

        public void closeWriter ();
    }

    public CCVideoReader(String path, CCVideoReaderListener listener) {
        mSavedFilePath = path;
        mReaderListener = listener;
    }

    public void initQueue(int width, int height) {
        CCLog.d(TAG, "initQueue " +  MAX_CODEC_QUEUE_SIZE);

        init_width = width;
        init_height = height;

        mCodecFrameQueue.clear();
        for (int i = 0; i < MAX_CODEC_QUEUE_SIZE; i++) {
            try {
                mCodecFrameQueue.put(new CCImage(width, height, width, 0));
            } catch (Exception e) {
            }
        }
    }

    public void returnQueueImage(CCImage usedImage) {
        try {
            CCLog.d(TAG, "initQueue ");
            //mCodecFrameQueue.put(usedImage);
            //mCodecFrameQueue.put(new CCImage(init_width, init_height, init_width, 0));
        } catch (Exception e) {

        }
    }

    public void start() {
        synchronized (mLock) {
            try {
                CCLog.d(TAG, "VideoReader initial operation start");
                mExtractor = new MediaExtractor();
                mExtractor.setDataSource(mSavedFilePath);

                mTrackIndexOfVideo = selectTrack(mExtractor);
                if (mTrackIndexOfVideo < 0) {
                    CCLog.e(TAG, "Error no video track");
                    if (mReaderListener != null) {
                        mReaderListener.onError("MediaExtractor no video track");
                    }
                    return;
                }
                mExtractor.selectTrack(mTrackIndexOfVideo);
                MediaFormat mediaFormat = mExtractor.getTrackFormat(mTrackIndexOfVideo);
                String mime = mediaFormat.getString(MediaFormat.KEY_MIME);
                //String mime = MediaFormat.MIMETYPE_VIDEO_VP9;

                CCLog.d(TAG, "CCVideoReader Decoder start");
                mDecoder = new CCVideoDecoder(mExtractor, mediaFormat);
                mDecoder.setListener(mListener);
                mDecoder.start(mime);
                CCLog.d(TAG, "CCVideoReader Decoder no error");
                CCLog.d(TAG, "VideoReader initial operation end");
            } catch (Exception e) {
                CCLog.e(TAG, "CCVideoReader Exception");
                if (mReaderListener != null) {
                    mReaderListener.onError("CCVideoReader Exception");
                }

                e.printStackTrace();
            }
        }
    }

    public void close() {
        synchronized (mLock) {
            mCodecFrameQueue.clear();

            CCLog.d(TAG, "Close Reader");
            if (mDecoder != null) {
                mDecoder.close();
                mDecoder = null;
            }
            CCLog.d(TAG, "Close Reader end");
        }
    }

    private static int selectTrack(MediaExtractor extractor) {
        int numTracks = extractor.getTrackCount();
        for (int i = 0; i < numTracks; i++) {
            MediaFormat format = extractor.getTrackFormat(i);
            String mime = format.getString(MediaFormat.KEY_MIME);
            if (mime.startsWith("video/")) {
                CCLog.d(TAG, "Extractor selected track " + i + " (" + mime + "): " + format);
                return i;
            }
        }
        return -1;
    }

    private CCVideoDecoderListener mListener = new CCVideoDecoderListener() {
        @Override
        public void onError(String errorMsg) {
            if (mReaderListener != null) {
                mReaderListener.onError(errorMsg);
            }
        }
        @Override
        public void onFinish() {
            if (mReaderListener != null) {
                mReaderListener.onFinish();
            }
        }
        @Override
        public void onDrainOutputImage(int index, Image image) {
            synchronized (mLock) {
                if (mReaderListener != null) {
                    try {
                        CCImage decodeImage = new CCImage(init_width, init_height, init_width, 0);//mCodecFrameQueue.take();
                        decodeImage.update(image);
                        mReaderListener.onDecodedImage(index, decodeImage);
                    } catch (Exception e) {
                    }
                }
            }
        }
        @Override
        public void closeEncoder(){
            if (mReaderListener != null) {
                if (mExtractor != null) {
                    mExtractor.release();
                    mExtractor = null;
                }
                mReaderListener.closeWriter();
            }
        }
    };
}
