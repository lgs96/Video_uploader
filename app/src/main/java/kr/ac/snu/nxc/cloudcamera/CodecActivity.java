package kr.ac.snu.nxc.cloudcamera;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.bottomsheet.BottomSheetBehavior;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;

import kr.ac.snu.nxc.cloudcamera.codec.CCVideoReader;
import kr.ac.snu.nxc.cloudcamera.codec.CCVideoStreamWriter;
import kr.ac.snu.nxc.cloudcamera.library.ImageUtils;
import kr.ac.snu.nxc.cloudcamera.util.CCConstants;
import kr.ac.snu.nxc.cloudcamera.util.CCImage;
import kr.ac.snu.nxc.cloudcamera.util.CCLog;
import kr.ac.snu.nxc.cloudcamera.util.CCUtils;

import static java.lang.Math.max;
import static java.lang.Math.min;
import static kr.ac.snu.nxc.cloudcamera.util.CCConstants.MAX_CODEC_QUEUE_SIZE;
import static kr.ac.snu.nxc.cloudcamera.util.CCConstants.TEMP_VIDEO_PATH;


public class CodecActivity extends AppCompatActivity implements InferenceCallback{
    private static final String TAG = "CodecActivity";
    private static final String AROHA_TAG = "AROHA";

    private static final int DOWNSAMPLE_RATIO = 8;

    private static final int DEFAULT_JPG_QUALITY = 95;
    private static final int MAM_JPG_QUALITY = 100;
    private static final int MIN_JPG_QUALITY = 5;

    private static final int ENCODE_ACTION_JPG = 1;
    private static final int ENCODE_ACTION_VIDEO = 2;
    private static final int ENCODE_NO_ACTION = 0;

    static final int MSG_IMAGE_LOADING = 0;
    static final int MSG_UPDATE_STATUS = MSG_IMAGE_LOADING + 1;
    static final int MSG_DECODE_FRAME = MSG_IMAGE_LOADING + 3;
    static final int MSG_UPDATE_PERF = MSG_IMAGE_LOADING + 4;

    private Context mContext = null;

    // AROHA Variables
    private double Alpha = 0.0;
    private double bw = 10.0;
    private double budget = 1333;

    private int t_tol = 5;
    private int stepSize = 5;
    private int stepScaling = 0;
    private int stepSizeQP = 5;

    private int windowSize = 20;
    private int windowCount = 0;

    private double margin = budget;
    private double avgMargin = 0.0;

    private int frameCount = 0;
    private int total_frameCount = 0;
    private int qp = 30;
    private String policy = "init";

    private ArrayList<Double> SendStartHistory = new ArrayList<Double>();
    private ArrayList<Double> RecvTimeHistory = new ArrayList<Double>();
    private ArrayList<Double> LatencyHistory = new ArrayList<Double>();
    private ArrayList<Double> EncodeTimeHistory = new ArrayList<Double>();
    private ArrayList<Double> SendTimeHistory = new ArrayList<Double>();
    private ArrayList<Integer> SendSizeHistory = new ArrayList<Integer>();
    private ArrayList<Double> BwHistory = new ArrayList<Double>();
    private ArrayList<Float> TransTimeHistory = new ArrayList<Float>();

    private ArrayList<Double> TransPredHistory = new ArrayList<Double>();
    private ArrayList<Double> WindowedLatencyHistory = new ArrayList<Double>();
    private ArrayList<String> PolicyHistory = new ArrayList<String>();

    public static LinkedBlockingQueue<Integer> SFQueue = new LinkedBlockingQueue<>(5);
    public static LinkedBlockingQueue<Integer> QPQueue = new LinkedBlockingQueue<>(5);
    //    public static LinkedBlockingQueue<byte[]> SendQueue = new LinkedBlockingQueue<byte[]>(20);
//    public static LinkedBlockingQueue<Pair<Integer, CCImage>> EncodingQueue = new LinkedBlockingQueue<Pair<Integer, CCImage>>(20);
    public static ArrayList<Integer> SFHistory = new ArrayList<Integer>();
    public static ArrayList<Integer> QPHistory = new ArrayList<Integer>();
    Queue<CCImage> mEncodingImageQueue = new LinkedList<CCImage>();

    Button mButtonJpgInference = null;
    Button mButtonVideoInference = null;

    TextView mTextViewStatus = null;
    TextView mTextViewPerf = null;
    EditText mEditBitrate = null;

    ImageView mImageViewDecodeFrame;
    ImageView mImageViewInferenceFrame;

    private LinearLayout mLayoutBottomSheet = null;
    private LinearLayout mLayoutGesture = null;
    private BottomSheetBehavior<LinearLayout> mSheetBehavior = null;
    protected ImageView mImageViewBottomSheetArrow = null;

    private ImageView mImageViewJpgQualityPlus = null;
    private ImageView mImageViewJpgQualityMinus = null;
    private TextView mTextViewJpgQuality = null;
    private int mJpgQuality = DEFAULT_JPG_QUALITY;

    String mVideoPath = null;
    String mStatusText = "";

    float mDownScaleRatio = 0.5f;

    LinkedBlockingQueue<CCImage> mDecodeFrameQueue = new LinkedBlockingQueue<CCImage>(MAX_CODEC_QUEUE_SIZE);
    LinkedBlockingQueue<Bitmap> mDecodeBitmapQueue = new LinkedBlockingQueue<Bitmap>(MAX_CODEC_QUEUE_SIZE);

    CloudInferenceManager mInferenceManager = null;
    boolean mIsVideoEncoding = false;

    CCVideoReader mCCVideoReader;
    int mWidth;
    int mHeight;

    boolean mDebugShowVideo = true;
    boolean mDebugShowInference = false;
    int mEncodingFrameIndex = 0;

    @Override
    public void onEncodingFinish(CCImage decodeImage, CCImage inferenceImage, double encodeTime) {
        mCCVideoReader.returnQueueImage(decodeImage);

        if (mDebugShowInference) {
            Bitmap bitmap = ImageUtils.convertDownScaledBitmap(inferenceImage, DOWNSAMPLE_RATIO);
            mImageViewInferenceFrame.setImageBitmap(bitmap);
        }
//        EncodeTimeHistory.add(encodeTime);
    }

    @Override
    public void onSendFinish(double send_start, double sendTime, int encodedSize) {
    }

    @Override
    public void onReceiveFinish(double alpha, double RecvTime, float TransTime) {
    }

    private Handler mVideoEncodingHandler = null;

    Handler mImageUpdateHandler = new Handler(new Handler.Callback() {
        @Override
        public boolean handleMessage(Message msg) {
            if (msg.what == MSG_DECODE_FRAME) {
                String status = "Video Decode : " + (total_frameCount) + " frame";
                mTextViewStatus.setText(status);
                try {
//                    int frameIndex = msg.arg1;

                    int frameIndex = total_frameCount;
                    CCLog.d(TAG, "Frame : " + frameIndex + " Queue remain : " + mDecodeFrameQueue.remainingCapacity());

                    if (mDebugShowVideo) {
                        mImageViewDecodeFrame.setImageBitmap(mDecodeBitmapQueue.take());
                    }

//                    CCLog.d(AROHA_TAG, "Frame Index [" + frameIndex + "]" + " mJpgQuality" + mJpgQuality + " mDownScaleRatio " + mDownScaleRatio);

                    CCImage decodeFrame = mDecodeFrameQueue.take();
//                    EncodingQueue.put(Pair.create(frameIndex, decodeFrame));

                    if (mIsVideoEncoding) {
                        mVideoEncodingHandler.post(getEncodingFrameRunnable(decodeFrame));
                    } else {
                        mInferenceManager.inferenceImage(frameIndex, decodeFrame);
                    }
                    total_frameCount += 1;

                } catch (Exception e) {

                }
            } else if (msg.what == MSG_UPDATE_STATUS) {
                mTextViewStatus.setText(mStatusText);
            }
            return true;
        }
    });

    HandlerThread mCodecThread = null;
    Handler mCodecHandler = null;
    boolean mEncoderFinish = false;
    boolean mDecoderFinish = false;
    private Object mLock = new Object();

    CCVideoStreamWriter mCCVideoWriter;
    CCVideoStreamWriter.CCVideoWriterListener mWriterListener = new CCVideoStreamWriter.CCVideoWriterListener() {
        @Override
        public void onFinish(long encodingTime) {
            CCLog.d(TAG, "Finish");
            closeEncoder();
//            mRunningDump = false;

//            mInferenceManager.setState(true);
        }

        public void onError(String errorMsg) {
            CCLog.d(TAG, "onError : " + errorMsg);
            closeEncoder();
        }

        public void onEncodedBuffer(ByteBuffer buffer, int size) {
            CCImage decodeImage = mEncodingImageQueue.poll();
            if (decodeImage != null) {
                mCCVideoReader.returnQueueImage(decodeImage);
            }
            mInferenceManager.inferenceImage(mEncodingFrameIndex, buffer.array(), size);

            mEncodingFrameIndex++;
        }
    };

    public void closeEncoder() {
        mCodecHandler.post(new Runnable() {
            @Override
            public void run() {
                mEncoderFinish = true;
                mCCVideoWriter.close();
            }
        });
    }

    private Runnable getEncodingFrameRunnable(final CCImage ccImage) {
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                synchronized (mLock) {
                    if (!mEncoderFinish) {
                        CCLog.d(TAG, "Codec put");
//                        CCImage cpyImage = new CCImage(ccImage);
                        mEncodingImageQueue.add(ccImage);
//                        mCCVideoReader.returnQueueImage(ccImage);
                        mCCVideoWriter.put(ccImage);
//                        mEncodingImageQueue.add(ccImage);
                    } else {
                        CCLog.d(TAG, "Codec finish");
                    }
                }
            }
        };
        return runnable;
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mContext = getApplicationContext();
        setContentView(R.layout.activity_codec);

        Intent intent = getIntent();
        ActionBar actionBar = getSupportActionBar();
        actionBar.setTitle("Inference Video");
        actionBar.setDisplayHomeAsUpEnabled(true);

        mVideoPath = intent.getStringExtra(CCConstants.KEY_VIDEO_PATH);
        CCLog.d(TAG, "YuvDirPath : " + mVideoPath);
        Toast.makeText(mContext, "Load " + mVideoPath, Toast.LENGTH_SHORT).show();

        HandlerThread ht = new HandlerThread("CameraPreview");
        ht.start();
        mVideoEncodingHandler = new Handler(ht.getLooper());

        mButtonJpgInference = (Button) findViewById(R.id.button_jpg_inference);
        mButtonVideoInference = (Button) findViewById(R.id.button_video_inference);
        mTextViewStatus = (TextView) findViewById(R.id.text_view_codec_status);
        mTextViewPerf = (TextView) findViewById(R.id.text_view_perf_status);
        mEditBitrate= (EditText) findViewById(R.id.edit_bitrate);
        mImageViewDecodeFrame = (ImageView) findViewById(R.id.image_view_decode_frame);
        mImageViewInferenceFrame = (ImageView) findViewById(R.id.image_view_inference_frame);

        mLayoutBottomSheet = findViewById(R.id.bottom_sheet_layout);
        mLayoutGesture = findViewById(R.id.layout_gesture);
        mSheetBehavior = BottomSheetBehavior.from(mLayoutBottomSheet);
        mImageViewBottomSheetArrow = findViewById(R.id.image_view_bottom_sheet_arrow);

        mImageViewJpgQualityPlus = (ImageView) findViewById(R.id.image_view_jpg_quality_plus);
        mImageViewJpgQualityMinus = (ImageView) findViewById(R.id.image_view_jpg_quality_minus);
        mTextViewJpgQuality = (TextView) findViewById(R.id.text_view_jpg_quality);
        mTextViewJpgQuality.setText("" + mJpgQuality);

        mInferenceManager = new CloudInferenceManager(mContext, this);
        mInferenceManager.setPerfTextView(mTextViewPerf);

        mCodecThread = new HandlerThread(("Codec"));
        mCodecThread.start();
        mCodecHandler = new Handler(mCodecThread.getLooper());

        mButtonJpgInference.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mIsVideoEncoding = false;
                mDecodeBitmapQueue.clear();
                mDecodeFrameQueue.clear();
                mInferenceManager.startInference(CCConstants.InferenceInputType.INFERENCE_JPG);
                mCodecHandler.post(new VideoDecoder());
            }
        });

        mButtonVideoInference.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mIsVideoEncoding = true;
                mDecodeBitmapQueue.clear();
                mDecodeFrameQueue.clear();
                mInferenceManager.startInference(CCConstants.InferenceInputType.INFERENCE_VIDEO);
                mEncodingFrameIndex = 0;
                mCodecHandler.post(new VideoDecoder());
//                mCodecHandler.post(new VideoDecoder());

                String tempVideoPath = TEMP_VIDEO_PATH + System.currentTimeMillis() + ".mp4";
                mCCVideoWriter = new CCVideoStreamWriter(mWidth, mHeight, tempVideoPath, mWriterListener);

                //SET Video Config

                if (mEditBitrate.getText().toString().trim().length() > 0){
                    float bitrate = Float.parseFloat(mEditBitrate.getText().toString());
                    bitrate = bitrate*1e6f;
                    mCCVideoWriter.setBitRate(bitrate);
                    mCCVideoWriter.start();
                }
                else{
                    mCCVideoWriter.setBitPerPixel(0.18f);
                    mEncoderFinish = false;
                    mCCVideoWriter.start();
                }
            }
        });

        ViewTreeObserver vto = mLayoutGesture.getViewTreeObserver();
        vto.addOnGlobalLayoutListener(
                new ViewTreeObserver.OnGlobalLayoutListener() {
                    @Override
                    public void onGlobalLayout() {
                        mLayoutGesture.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                        mSheetBehavior.setPeekHeight(mLayoutGesture.getMeasuredHeight());
                    }
                }
        );
        mSheetBehavior.setHideable(false);

        mSheetBehavior.addBottomSheetCallback(
                new BottomSheetBehavior.BottomSheetCallback() {
                    @Override
                    public void onStateChanged(@NonNull View bottomSheet, int newState) {
                        switch (newState) {
                            case BottomSheetBehavior.STATE_HIDDEN:
                                break;
                            case BottomSheetBehavior.STATE_EXPANDED: {
                                mImageViewBottomSheetArrow.setImageResource(R.drawable.bottom_sheet_down);
                            }
                            break;
                            case BottomSheetBehavior.STATE_COLLAPSED: {
                                mImageViewBottomSheetArrow.setImageResource(R.drawable.bottom_sheet_up);
                            }
                            break;
                            case BottomSheetBehavior.STATE_DRAGGING:
                                break;
                            case BottomSheetBehavior.STATE_SETTLING:
                                mImageViewBottomSheetArrow.setImageResource(R.drawable.bottom_sheet_up);
                                break;
                        }
                    }

                    @Override
                    public void onSlide(@NonNull View bottomSheet, float slideOffset) {
                    }
                }
        );

        mImageViewJpgQualityPlus.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setJpgQuality(mJpgQuality + 5);
            }
        });

        mImageViewJpgQualityMinus.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setJpgQuality(mJpgQuality - 5);
            }
        });
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()){
            case android.R.id.home:
                onBackPressed();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }
    @Override
    protected void onResume() {
        super.onResume();
        readVideoInfo();
        readThumbnail();
        mInferenceManager.setState(true);
    }

    @Override
    public void onPause() {
        mInferenceManager.setState(false);
        super.onPause();
    }

    private void readVideoInfo() {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        retriever.setDataSource(mVideoPath);
        mWidth = Integer.valueOf(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH));
        mHeight = Integer.valueOf(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT));
        retriever.release();

        mStatusText = "Read Video " + mWidth + "x" + mHeight;
        mTextViewStatus.setText(mStatusText);
    }

    private void readThumbnail() {
        Bitmap thumbnailBitmap = CCUtils.readThumbnail(mVideoPath, 640, 360);
        mImageViewDecodeFrame.setImageBitmap(thumbnailBitmap);
    }

    private void setJpgQuality(int jpgQuality) {
        if (jpgQuality > MAM_JPG_QUALITY) {
            jpgQuality = MAM_JPG_QUALITY;
        } else if (jpgQuality < MIN_JPG_QUALITY) {
            jpgQuality = MIN_JPG_QUALITY;
        }
        mJpgQuality = jpgQuality;
        mTextViewJpgQuality.setText("" + mJpgQuality);
    }

    public void runUpload (){
        mIsVideoEncoding = true;
        mDecodeBitmapQueue.clear();
        mDecodeFrameQueue.clear();
        mInferenceManager.startInference(CCConstants.InferenceInputType.INFERENCE_VIDEO);
        mEncodingFrameIndex = 0;
        mCodecHandler.post(new VideoDecoder());
//                mCodecHandler.post(new VideoDecoder());

        String tempVideoPath = TEMP_VIDEO_PATH + System.currentTimeMillis() + ".mp4";
        mCCVideoWriter = new CCVideoStreamWriter(mWidth, mHeight, tempVideoPath, mWriterListener);

        //SET Video Config

        if (mEditBitrate.getText().toString().trim().length() > 0){
            float bitrate = Float.parseFloat(mEditBitrate.getText().toString());
            bitrate = bitrate*1e6f;
            mCCVideoWriter.setBitRate(bitrate);
            mCCVideoWriter.start();
        }
        else{
            mCCVideoWriter.setBitPerPixel(0.18f);
            mEncoderFinish = false;
            mCCVideoWriter.start();
        }
    }

    public class VideoDecoder implements Runnable {
        CCVideoReader.CCVideoReaderListener mReaderListener = new CCVideoReader.CCVideoReaderListener() {
            @Override
            public void onFinish() {
                CCLog.d(TAG, "onFinish");
                mStatusText = "Decode Finish";
                mImageUpdateHandler.sendEmptyMessage(MSG_UPDATE_STATUS);
                //closeDecoder();
                mCodecHandler.post(new VideoDecoder());
            }

            public void onError(String errorMsg) {
                CCLog.d(TAG, "onError : " + errorMsg);
                mStatusText = "Video Decoding Fail : " + errorMsg;
                mImageUpdateHandler.sendEmptyMessage(MSG_UPDATE_STATUS);
                closeDecoder();

                //runUpload ();
            }

            public void onDecodedImage(int index, CCImage ccImage) {
                CCLog.d(TAG, "onDecodedImage : " + index);

                try {
                    Thread.sleep(16);
                    mDecodeFrameQueue.put(ccImage);

                    if (mDebugShowVideo) {
                        mDecodeBitmapQueue.put(ImageUtils.convertDownScaledBitmap(ccImage, DOWNSAMPLE_RATIO));
                    }
                } catch (Exception e) {

                }

                Message msg = new Message();
                msg.what = MSG_DECODE_FRAME;
                msg.arg1 = index;
                mImageUpdateHandler.sendMessage(msg);

            }
        };

        public VideoDecoder() {
        }

        public void closeDecoder() {
            mCodecHandler.post(new Runnable() {
                @Override
                public void run() {
                    CCLog.d(TAG, "closeDecoder");
                    mCCVideoReader.close();
                    mDecoderFinish = true;
                    CCLog.d(TAG, "closeDecoder end");
                }
            });
            if (mIsVideoEncoding) {
                mCodecHandler.postAtTime(new Runnable() {
                    @Override
                    public void run() {
                        CCLog.d(TAG, "closeEncoder");
                        closeEncoder();
                    }
                }, 100);
            }
        }

        public void run() {
            mDecoderFinish = false;
            mCCVideoReader = new CCVideoReader(mVideoPath, mReaderListener);
            mCCVideoReader.initQueue(mWidth, mHeight);
            mCCVideoReader.start();
        }
    }




}