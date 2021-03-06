package kr.ac.snu.nxc.cloudcamera;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Random;
import java.util.concurrent.LinkedBlockingQueue;

import kr.ac.snu.nxc.cloudcamera.RLagent.Agent;
import kr.ac.snu.nxc.cloudcamera.codec.CCVideoReader;
import kr.ac.snu.nxc.cloudcamera.codec.CCVideoStreamEncoder;
import kr.ac.snu.nxc.cloudcamera.codec.CCVideoStreamWriter;
import kr.ac.snu.nxc.cloudcamera.library.ImageUtils;
import kr.ac.snu.nxc.cloudcamera.thermalreader.ThermalReader;
import kr.ac.snu.nxc.cloudcamera.util.CCConstants;
import kr.ac.snu.nxc.cloudcamera.util.CCImage;
import kr.ac.snu.nxc.cloudcamera.util.CCLog;
import kr.ac.snu.nxc.cloudcamera.util.CCSave;
import kr.ac.snu.nxc.cloudcamera.util.CCUtils;

import static java.lang.Math.max;
import static java.lang.Math.min;
import static kr.ac.snu.nxc.cloudcamera.CloudInferenceManager.downScaleCCImage;
import static kr.ac.snu.nxc.cloudcamera.util.CCConstants.CCFHD;
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

    Button mButtonTRS = null;
    Button mButtonJpgInference = null;
    Button mButtonVideoInference = null;

    TextView mTextViewStatus = null;
    TextView mTextViewEncode = null;
    TextView mTextViewPerf = null;
    TextView mTextViewThermal = null;
    TextView mTextViewCpu = null;
    TextView mTextViewVideo = null;
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
    boolean mIsTRSEncoding = false;

    CCVideoReader mCCVideoReader;
    int mWidth;
    int mHeight;
    int mBitRate;

    boolean mDebugShowVideo = true;
    boolean mDebugShowInference = false;
    int mEncodingFrameIndex = 0;

    // To save fps/throughput in csv file
    boolean is_save = true;
    long encode_last_milli= System.currentTimeMillis();
    long decode_last_milli = System.currentTimeMillis();

    int encode_frame = 0;
    int decode_frame = 0;

    float encode_fps = 0;
    float decode_fps = 0;

    float encode_byte = 0f;
    float decode_byte = 0f;

    public float network_fps = 0f;
    public float network_th = 0f;

    LinkedList<CCImage> mTestList = new LinkedList<CCImage>();
    LinkedList<CCImage> m4kList = new LinkedList<CCImage>();
    LinkedList<CCImage> m3_5kList = new LinkedList<CCImage>();
    LinkedList<CCImage> m3kList = new LinkedList<CCImage>();
    LinkedList<CCImage> m2_5kList = new LinkedList<CCImage>();
    LinkedList<CCImage> m2kList = new LinkedList<CCImage>();
    ArrayList <LinkedList<CCImage>> resolution_set = new ArrayList<LinkedList<CCImage>>();

    LinkedList<CCVideoStreamEncoder> encoder_set = new LinkedList<CCVideoStreamEncoder>();

    public int mFrameIndex = 0;
    public int mResolutionIndex = 1;
    public int mNextResolution = 1;
    public int mBitRateIndex = 3;
    public int mMaxBitRateIndex = 5;
    public int mClockIndex = 0;

    public int mLittleCpuIndex = 0;
    public int mBigCpuIndex = 0;
    public int mTestIndex = 0;

    public static int [] resolution_states;

    Agent RLagent = null;

    // Thermal reader
    ThermalReader thermalReader;
    CCVideoStreamEncoder mCCVideoEncoder;
    CCVideoStreamEncoder.TRSEncoderListener mEncoderListener = new CCVideoStreamEncoder.TRSEncoderListener() {
        public void onEncodedBuffer(MediaCodec.BufferInfo bufferInfo, ByteBuffer byteBuffer) {

            //encoder_set.get(mResolutionIndex).encodeFromMain();

            CCLog.d(TAG, "Buffer offset : " + bufferInfo.offset + " buffer size : " + bufferInfo.size);
            //if (mMediaMuxer != null) {
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            info.set(bufferInfo.offset, bufferInfo.size, bufferInfo.presentationTimeUs, bufferInfo.flags);

            try {
                byteBuffer.clear();
                byteBuffer.position(bufferInfo.offset);
                byteBuffer.limit(bufferInfo.offset + bufferInfo.size);
                ByteBuffer streamBuffer = ByteBuffer.allocate(bufferInfo.size);
                streamBuffer.put(byteBuffer);
//                    byte[] socketWriteBuffer = mFrameBuffer.array();

                //socket write [0] ~ [bufferInfo.size]
                mInferenceManager.inferenceImage(mEncodingFrameIndex, streamBuffer.array(), bufferInfo.size);

                mEncodingFrameIndex++;
                encode_frame += 1;
                encode_byte += (float)bufferInfo.size*8/(1024*1024);
                CCLog.d(TAG, "TRS encoded Mbits: " + (float)bufferInfo.size*8/(1024*1024));
            }
            catch (Exception e){
                CCLog.d(TAG, "Error during onDrainOutputBuffer: " + e);
            }
        }

        public void startNextResolution (){
            CCLog.d(TAG, "TRS RL Encoder start next");
            encoder_set.get(mResolutionIndex).encode_start();
        }

        public void getPermission(){

        }

        public void onRestart(){
            CCLog.i(TAG, "Output buffer id, restart " + mResolutionIndex);
            encoder_set.get(mResolutionIndex).encodeFromMain();
        }
    };

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
                CCLog.d(TAG, "Inject msg enter..");
                String status = "Video Decode : " + (total_frameCount) + " frame/fps: " + decode_fps;
                mTextViewStatus.setText(status);

                String status2 = "Video Encode : " + (mEncodingFrameIndex) + " frame/fps: " + encode_fps;
                mTextViewEncode.setText(status2);
                mTextViewThermal.setText("Temp: " + thermalReader.temp_info);
                mTextViewCpu.setText("CPU: " +thermalReader.cpu_info);
                //mTextViewVideo.setText("Resolution/Bitrate: " + encoder_set.get(mResolutionIndex).mWidth + " " + encoder_set.get(mResolutionIndex).mBitRate/1e6);

                CCLog.d(TAG, "Handle decode msg");

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
                    CCLog.d(TAG, "Downscale prelim : " + decodeFrame.mHeight + " " + decodeFrame.mWidth);


                    if (mIsVideoEncoding) {
                        mVideoEncodingHandler.post(getEncodingFrameRunnable(decodeFrame));
                    }
                    else if (mIsTRSEncoding){
                        mVideoEncodingHandler.post(startTRSEncoding(decodeFrame));
                    }
                    else {
                        mInferenceManager.inferenceImage(frameIndex, decodeFrame);
                    }
                    total_frameCount += 1;
                    decode_frame += 1;
                    decode_byte += decodeFrame.getHeight()*decodeFrame.getWidth()*4/(1024*1024);

                    long current_milli = System.currentTimeMillis();
                    CCLog.d(TAG, "Decode frame byte: " + decode_frame + " " + decode_byte);
                    long time_gap = (current_milli - decode_last_milli);
                    if (time_gap > 1000) {
                        decode_last_milli = current_milli;

                        float fps_f = decode_frame/((float)time_gap/1000f);
                        float th_f = decode_byte/((float)time_gap/1000f);
                        decode_fps = (Math.round(fps_f*100f)/100.0f);
                        String fps = Float.toString(Math.round(fps_f*100f)/100.0f);
                        String throughput = Float.toString(Math.round(th_f*100f)/100.0f);
                        String content = fps + "," + throughput;

                        float fps_e = encode_frame/((float)time_gap/1000f);
                        float th_e = encode_byte/((float)time_gap/1000f);
                        encode_fps = (Math.round(fps_e*100f)/100.0f);
                        fps = Float.toString(Math.round(fps_e*100f)/100.0f);
                        throughput = Float.toString(Math.round(th_e*100f)/100.0f);

                        content += ","+ fps + "," + throughput +","+network_fps+","+network_th;

                        CCLog.d(TAG, "CCSave save " + content);
                        //mSaveHandler.post(new CCSave (is_save, content, "record")) ;
                        thermalReader.GetCodecString(content);
                        decode_frame = 0;
                        decode_byte = 0;
                        encode_frame = 0;
                        encode_byte = 0;
                        CCLog.d(TAG, "Decoding record is " + content);
                    }

                    // Resolution change test
                    //if (total_frameCount == 30){
                    //    closeToRestart();
                    //}

                } catch (Exception e) {
                    CCLog.d(TAG, "Exception during handling");
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

    HandlerThread mSaveThread = null;
    Handler mSaveHandler = null;

    HandlerThread mInputThread = null;
    Handler mInputHandler = null;

    HandlerThread mStateThread = null;
    Handler mStateHandler = null;

    HandlerThread mPreProcessThread = null;
    Handler mPreProcessHandler = null;

    HandlerThread mActionThread = null;
    Handler mActionHandler = null;

    CCVideoStreamWriter mCCVideoWriter;
    CCVideoStreamWriter.CCVideoWriterListener mWriterListener = new CCVideoStreamWriter.CCVideoWriterListener() {
        @Override
        public void onFinish(long encodingTime) {
            CCLog.d(TAG, "Finish");
            //closeEncoder();
            //runUpload();
//            mRunningDump = false;

//            mInferenceManager.setState(true);
        }

        public void onError(String errorMsg) {
            CCLog.d(TAG, "onError : " + errorMsg);
            //closeEncoder();
        }

        public void onEncodedBuffer(ByteBuffer buffer, int size) {
            CCImage decodeImage = mEncodingImageQueue.poll();
            if (decodeImage != null) {
                mCCVideoReader.returnQueueImage(decodeImage);
            }
            mInferenceManager.inferenceImage(mEncodingFrameIndex, buffer.array(), size);

            mEncodingFrameIndex++;
            encode_frame += 1;
            encode_byte += (float)size*8/(1024*1024);

            /*
            long current_milli = System.currentTimeMillis();
            CCLog.d(TAG, "Encode frame byte: " + encode_frame + " " + encode_byte + " " + size);
            long time_gap = (current_milli - encode_last_milli);
            if (time_gap > 1000) {
                encode_last_milli = current_milli;

                float fps_f = 0f;
                float th_f = 0f;

                try {
                    fps_f = encode_frame / ((float) time_gap / 1000f);
                    th_f = encode_byte / ((float) time_gap / 1000f);
                }
                catch (Exception e){

                }

                CCLog.d(TAG, "CCSave save " + fps_f + " "+th_f);

                encode_fps = (Math.round(fps_f*100f)/100.0f);
                String fps = Float.toString(encode_fps);
                String throughput = Float.toString(Math.round(th_f*100f)/100.0f);
                String content = fps + "," + throughput;
                mSaveHandler.post(new CCSave(is_save, content, "encode"));
                encode_frame = 0;
                encode_byte = 0;
            }
            */

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

    private Runnable startTRSEncoding (final CCImage ccImage){
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                synchronized (mLock) {
                    if (!mEncoderFinish) {
                        CCLog.d(TAG, "Codec put");
                        //CCImage cpyImage = new CCImage(ccImage);
                        //cpyImage =  downScaleCCImage (cpyImage, 0.2f);

                        // origin: ccImage
                        //CCImage downScaleFrame = downScaleCCImage(ccImage, 0.5f);
                        CCLog.d(TAG, "TRS: start encoding " + mResolutionIndex);
                        mEncodingImageQueue.add(ccImage);
//                        mCCVideoReader.returnQueueImage(ccImage);
                        //mCCVideoEncoder.putEncodingBuffer(ccImage);
                        encoder_set.get(mResolutionIndex).putEncodingBuffer(ccImage);
//                        mEncodingImageQueue.add(ccImage);
                    } else {
                        CCLog.d(TAG, "Codec finish");
                    }
                }
            }
        };
        return runnable;
    }


    private Runnable getEncodingFrameRunnable(final CCImage ccImage) {
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                synchronized (mLock) {
                    if (!mEncoderFinish) {
                        CCLog.d(TAG, "Codec put");
                        //CCImage cpyImage = new CCImage(ccImage);
                        //cpyImage =  downScaleCCImage (cpyImage, 0.2f);

                        // origin: ccImage
                        //CCImage downScaleFrame = downScaleCCImage(ccImage, 0.5f);
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
        CCLog.d(TAG, "Video path : " + mVideoPath);
        Toast.makeText(mContext, "Load " + mVideoPath, Toast.LENGTH_SHORT).show();

        HandlerThread ht = new HandlerThread("CameraPreview");
        ht.start();
        mVideoEncodingHandler = new Handler(ht.getLooper());

        mButtonTRS = (Button) findViewById(R.id.button_trs);
        mButtonJpgInference = (Button) findViewById(R.id.button_jpg_inference);
        mButtonVideoInference = (Button) findViewById(R.id.button_video_inference);
        mTextViewStatus = (TextView) findViewById(R.id.text_view_codec_status);
        mTextViewEncode = (TextView) findViewById(R.id.text_view_codec_status2);
        mTextViewPerf = (TextView) findViewById(R.id.text_view_perf_status);
        mTextViewThermal = (TextView) findViewById(R.id.text_view_temp_status);
        mTextViewCpu = (TextView) findViewById(R.id.text_view_cpu_status);
        mTextViewVideo = (TextView) findViewById(R.id.text_view_video_status);
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
        mInferenceManager.setPerfTextView(mTextViewPerf, this);

        mCodecThread = new HandlerThread(("Codec"));
        mCodecThread.start();
        mCodecHandler = new Handler(mCodecThread.getLooper());

        mSaveThread = new HandlerThread(("Save"));
        mSaveThread.start();
        mSaveHandler = new Handler(mSaveThread.getLooper());

        mInputThread = new HandlerThread(("Input"));
        mInputThread.start();
        mInputHandler = new Handler(mInputThread.getLooper());

        mStateThread = new HandlerThread(("State"));
        mStateThread.start();
        mStateHandler = new Handler(mStateThread.getLooper());

        mPreProcessThread = new HandlerThread(("PreProcess"));
        mPreProcessThread.start();
        mPreProcessHandler = new Handler(mPreProcessThread.getLooper());

        mActionThread = new HandlerThread("Action");
        mActionThread.start();
        mActionHandler = new Handler(mActionThread.getLooper());

        // Goodsol
        mButtonTRS.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mIsVideoEncoding = false;
                mIsTRSEncoding = true;
                mDecodeBitmapQueue.clear();
                mDecodeFrameQueue.clear();
                mInferenceManager.startInference(CCConstants.InferenceInputType.INFERENCE_VIDEO);
                mInferenceManager.setState(true);
                mEncodingFrameIndex = 0;

                // This line increases the load level
                mCodecHandler.post(new VideoDecoder());

                // Define RL agent
                RLagent = new Agent();
                RLagent.setListener(mAgentListener);

                // Preprocessing
                try {
                    resolution_set.add(m2kList);
                    resolution_set.add(m2_5kList);
                    resolution_set.add(m3kList);
                    resolution_set.add(m3_5kList);
                    resolution_set.add(m4kList);
                    preprocess_jpeg();
                } catch (IOException e) {
                    e.printStackTrace();
                }

                int set_size = resolution_set.size();
                resolution_states = new int [set_size];

                for (int i = 0; i < set_size; i++) {
                    //SET Video Config
                    float bpp = 0.18f;
                    if (mEditBitrate.getText().toString().trim().length() > 0){
                        bpp = Float.parseFloat(mEditBitrate.getText().toString())/100;
                    }
                    int width = resolution_set.get(i).get(0).mWidth;
                    int height = resolution_set.get(i).get(0).mHeight;
                    resolution_states[i] = width;

                    CCLog.d (TAG, "Encoder set width/height: " + width + " " + height);

                    mBitRate = (int)(bpp * 30 * width * height);
                    // Define initial CCVideoWriter
                    encoder_set.add(new CCVideoStreamEncoder());
                    encoder_set.get(i).setSize(width, height);
                    encoder_set.get(i).setKeyFramePerSec(30);
                    encoder_set.get(i).setFPS(30);
                    encoder_set.get(i).setTRSListener(mEncoderListener);
                    encoder_set.get(i).setColorFormat(MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar);
                    encoder_set.get(i).setBitrate(mBitRate);
                    encoder_set.get(i).setBitrateMode(MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR);
                    try {
                        encoder_set.get(i).start();
                        encoder_set.get(i).encode_start();
                        //encoder_set.get(i).encode_stop();
                    }
                    catch (Exception e){

                    }
                    /*
                    mCCVideoEncoder = new CCVideoStreamEncoder();
                    // mEncoder.setImageList(mCCImageList);
                    mCCVideoEncoder.setSize(mWidth, mHeight);
                    mCCVideoEncoder.setKeyFramePerSec(30);
                    mCCVideoEncoder.setFPS(30);
                    mCCVideoEncoder.setTRSListener(mEncoderListener);
                    mCCVideoEncoder.setColorFormat(MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar);
                    mCCVideoEncoder.setBitrate(mBitRate);
                    mCCVideoEncoder.setBitrateMode(MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR);
                    mCCVideoEncoder.start();
                     */
                }

                // Input thread
                injectInput ();
                getState ();
                //encoder_set.get(mResolutionIndex).start();
                //encoder_set.get(mResolutionIndex).encode_start();
                encoder_set.get(mResolutionIndex).encodeFromMain();

                // Read states
                thermalReader = new ThermalReader();
                thermalReader.readThermal();
            }
        });

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

                int numCodecs = MediaCodecList.getCodecCount();
                for (int i = 0; i < numCodecs; i++) {
                    MediaCodecInfo codecInfo = MediaCodecList.getCodecInfoAt(i);
                    String name = codecInfo.getName();
                    CCLog.d(TAG, "supports examining " + (codecInfo.isEncoder() ? "encoder" : "decoder") + ": " + name);
                    for(String type: codecInfo.getSupportedTypes()) {
                        boolean ap = codecInfo.getCapabilitiesForType(type).isFeatureSupported(MediaCodecInfo.CodecCapabilities.FEATURE_AdaptivePlayback);
                        CCLog.d(TAG, "supports adaptive playback: " + ap);
                    }
                }

                CCLog.d(TAG, "Initial operation start!");

                mIsVideoEncoding = true;
                mDecodeBitmapQueue.clear();
                mDecodeFrameQueue.clear();
                mInferenceManager.startInference(CCConstants.InferenceInputType.INFERENCE_VIDEO);
                mEncodingFrameIndex = 0;
                mCodecHandler.post(new VideoDecoder());
//                mCodecHandler.post(new VideoDecoder());

                String tempVideoPath = TEMP_VIDEO_PATH + System.currentTimeMillis() + ".mp4";
                //mCCVideoWriter = new CCVideoStreamWriter(mWidth, mHeight, tempVideoPath, mWriterListener);
                mCCVideoWriter = new CCVideoStreamWriter(mWidth, mHeight, tempVideoPath, mWriterListener);

                thermalReader = new ThermalReader();
                thermalReader.readThermal();

                //SET Video Config
                float bpp = 0.18f;
                if (mEditBitrate.getText().toString().trim().length() > 0){
                    bpp = Float.parseFloat(mEditBitrate.getText().toString())/100;
                    mCCVideoWriter.setBitPerPixel(bpp);
                    mCCVideoWriter.start();
                }
                else{
                    mCCVideoWriter.setBitPerPixel(bpp);
                    mEncoderFinish = false;
                    mCCVideoWriter.start();
                }
                thermalReader.save_string = "";
                CCLog.d(TAG, "Save string is " + thermalReader.save_string);

                CCLog.d(TAG, "Initial operation end!");
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

    // Functions of TRS
    public void preprocess_jpeg () throws IOException {
        CCLog.i(TAG, "Preprocessing start");
        int frame_num = 1;
        for (int i = 0; i < 10; i++) {
            frame_num += i;
            String path4k = "/sdcard/CCVideo/3840/frame_" + frame_num + ".jpg";
            String path3_5k = "/sdcard/CCVideo/3456/frame_" + frame_num + ".jpg";
            String path3k = "/sdcard/CCVideo/2880/frame_" + frame_num + ".jpg";
            String path2_5k = "/sdcard/CCVideo/2340/frame_" + frame_num + ".jpg";
            String path2k = "/sdcard/CCVideo/1920/frame_" + frame_num + ".jpg";
            try {
                File f = new File(path4k);
                FileInputStream fis = new FileInputStream(f);
                byte[] bytes = new byte[(int) f.length()];
                fis.read(bytes);
                CCImage cc4k = ImageUtils.convertCCImage(bytes, 3840, 2160, 3840);

                f = new File(path3_5k);
                fis = new FileInputStream(f);
                bytes = new byte[(int) f.length()];
                fis.read(bytes);
                CCImage cc3_5k = ImageUtils.convertCCImage(bytes, 3456, 1944, 3456);

                f = new File(path3k);
                fis = new FileInputStream(f);
                bytes = new byte[(int) f.length()];
                fis.read(bytes);
                CCImage cc3k = ImageUtils.convertCCImage(bytes, 2880, 1620, 2880);

                f = new File(path3k);
                fis = new FileInputStream(f);
                bytes = new byte[(int) f.length()];
                fis.read(bytes);
                CCImage cc2_5k = ImageUtils.convertCCImage(bytes, 2560, 1440, 2560);

                f = new File(path2k);
                fis = new FileInputStream(f);
                bytes = new byte[(int) f.length()];
                fis.read(bytes);
                CCImage cc2k = ImageUtils.convertCCImage(bytes, 1920, 1080, 1920);

                m4kList.add(i, cc4k);
                m3_5kList.add(i, cc3_5k);
                m3kList.add(i, cc3k);
                m2_5kList.add(i, cc2_5k);
                m2kList.add(i, cc2k);
            }
            catch (Exception e){
                return;
            }
        }
        CCLog.i(TAG, "Preprocessing done");
        //mPreProcessHandler.post(new background_preprocess());
    }

    public class background_preprocess implements Runnable {
        public void run (){
            CCLog.d(TAG, "TRS start background " + mIsTRSEncoding);
            while(mIsTRSEncoding){
                long curr_time = System.currentTimeMillis();
                Random rand = new Random(curr_time);
                int frame_num = rand.nextInt(500) + 1001;
                CCLog.d(TAG, "TRS background frame num: " + frame_num);

                String path4k = "/sdcard/CCVideo/3840/frame_" + frame_num + ".jpg";
                String path2k = "/sdcard/CCVideo/1920/frame_" + frame_num + ".jpg";

                try {
                    File f = new File(path4k);
                    FileInputStream fis = new FileInputStream(f);
                    byte[] bytes = new byte[(int) f.length()];
                    fis.read(bytes);
                    CCImage cc4k = ImageUtils.convertCCImage(bytes, mWidth, mHeight, mWidth);

                    f = new File(path2k);
                    fis = new FileInputStream(f);
                    bytes = new byte[(int) f.length()];
                    fis.read(bytes);
                    CCImage cc2k = ImageUtils.convertCCImage(bytes, mWidth, mHeight, mWidth);
                    m4kList.add(frame_num % 10, cc4k);
                    m2kList.add(frame_num % 10, cc2k);
                }
                catch (Exception e){

                }
            }
        }
    }

    public void injectInput () {
        mInputHandler.post(new Runnable() {
            long start = 0;
            long end = 0;
            @Override
            public void run() {
                //for (int i = 0; i < 30*60*30; i++){
                while(true){
                    try{
                        Thread.sleep(Math.max(33 - (end-start),0));
                        start = System.currentTimeMillis();
                        CCLog.d(TAG, "TRS: Inject start " + start);
                        //CCImage ccImage = resolution_set.get(mResolutionIndex).get(mFrameIndex);
                        mFrameIndex += 1;
                        mFrameIndex %= 10;
                        try {
                            //Thread.sleep(16);
                            // Set video resolution here (Goodsol-RLagent)
                            mDecodeFrameQueue.put(resolution_set.get(mResolutionIndex).get(mFrameIndex));

                            if (mDebugShowVideo) {
                                mDecodeBitmapQueue.put(ImageUtils.convertDownScaledBitmap(resolution_set.get(mResolutionIndex).get(mFrameIndex), DOWNSAMPLE_RATIO));
                            }
                        } catch (Exception e) {

                        }

                        /*
                        Message msg = new Message();
                        msg.what = MSG_DECODE_FRAME;
                        msg.arg1 = mTestIndex;
                        mImageUpdateHandler.sendMessage(msg);
                         */
                        handleTRS();

                        mTestIndex += 1;
                        end = System.currentTimeMillis();
                        CCLog.d(TAG, "TRS: Inject end " + end);
                    }
                    catch (Exception e){

                    }
                }
            }
        });
    }

    public void handleTRS (){
        CCLog.d(TAG, "Inject msg enter..");
        String status = "Video Decode : " + (total_frameCount) + " frame/fps: " + decode_fps;
        mTextViewStatus.setText(status);

        String status2 = "Video Encode : " + (mEncodingFrameIndex) + " frame/fps: " + encode_fps;
        mTextViewEncode.setText(status2);
        mTextViewThermal.setText("Temp: " + thermalReader.temp_info);
        mTextViewCpu.setText("CPU: " +thermalReader.cpu_info);
        mTextViewVideo.setText("Resolution/Bitrate: " + mResolutionIndex + " " + mBitRateIndex);

        CCLog.d(TAG, "Handle decode msg");

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
            CCLog.d(TAG, "Downscale prelim : " + decodeFrame.mHeight + " " + decodeFrame.mWidth);

            mVideoEncodingHandler.post(startTRSEncoding(decodeFrame));

            total_frameCount += 1;
            decode_frame += 1;
            decode_byte += decodeFrame.getHeight()*decodeFrame.getWidth()*4/(1024*1024);

            long current_milli = System.currentTimeMillis();
            CCLog.d(TAG, "Decode frame byte: " + decode_frame + " " + decode_byte);
            long time_gap = (current_milli - decode_last_milli);
            if (time_gap > 1000) {
                decode_last_milli = current_milli;

                float fps_f = decode_frame/((float)time_gap/1000f);
                float th_f = decode_byte/((float)time_gap/1000f);
                decode_fps = (Math.round(fps_f*100f)/100.0f);
                String fps = Float.toString(Math.round(fps_f*100f)/100.0f);
                String throughput = Float.toString(Math.round(th_f*100f)/100.0f);
                String content = fps + "," + throughput;

                float fps_e = encode_frame/((float)time_gap/1000f);
                float th_e = encode_byte/((float)time_gap/1000f);
                encode_fps = (Math.round(fps_e*100f)/100.0f);
                fps = Float.toString(Math.round(fps_e*100f)/100.0f);
                throughput = Float.toString(Math.round(th_e*100f)/100.0f);

                content += ","+ fps + "," + throughput +","+network_fps+","+network_th;

                CCLog.d(TAG, "CCSave save " + content);
                //mSaveHandler.post(new CCSave (is_save, content, "record")) ;
                thermalReader.GetCodecString(content);
                decode_frame = 0;
                decode_byte = 0;
                encode_frame = 0;
                encode_byte = 0;
                CCLog.d(TAG, "Decoding record is " + content);
            }

            // Resolution change test
            //if (total_frameCount == 30){
            //    closeToRestart();
            //}

        } catch (Exception e) {
            CCLog.d(TAG, "Exception during handling");
        }
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
        CCLog.d(TAG, "Run upload called");

        mVideoPath = CCFHD;
        readVideoInfo();

        CCLog.d(TAG, "Video path: " +  mVideoPath);

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

    public void closeEnDecoder() {
        mCodecHandler.post(new Runnable() {
            @Override
            public void run() {
                CCLog.d(TAG, "closeDecoder");
                mCCVideoReader.close();
                mDecoderFinish = true;
                CCLog.d(TAG, "closeDecoder end");
                //if (mIsVideoEncoding) {
                //closeEncoder();
                //}
            }
        });
    }

    public void closeToRestart () {
        mCodecHandler.post(new Runnable() {
            @Override
            public void run() {
                CCLog.d(TAG, "closeDecoder");
                mCCVideoReader.close();
                //mDecoderFinish = true;
                CCLog.d(TAG, "closeDecoder end");
            }
        });
    }


    public class VideoDecoder implements Runnable {
        CCVideoReader.CCVideoReaderListener mReaderListener = new CCVideoReader.CCVideoReaderListener() {
            @Override
            public void onFinish() {
                CCLog.d(TAG, "onFinish");
                mStatusText = "Decode Finish";
                mImageUpdateHandler.sendEmptyMessage(MSG_UPDATE_STATUS);
                closeDecoder();
            }

            public void onError(String errorMsg) {
                CCLog.d(TAG, "onError : " + errorMsg);
                mStatusText = "Video Decoding Fail : " + errorMsg;
                mImageUpdateHandler.sendEmptyMessage(MSG_UPDATE_STATUS);
                closeDecoder();

                //runUpload ();
            }

            public void closeWriter (){
                CCLog.d(TAG, "Resolution set: close writer from codec activity");
                closeEncoder();
            }

            public void onDecodedImage(int index, CCImage ccImage) {
                CCLog.d(TAG, "onDecodedImage : " + index);

                try {
                    // Thread.sleep(16);
                    // Set video resolution here (Goodsol-RLagent)
                    // mDecodeFrameQueue.put(ccImage);

                    if (mDebugShowVideo) {
                        //mDecodeBitmapQueue.put(ImageUtils.convertDownScaledBitmap(ccImage, DOWNSAMPLE_RATIO));
                    }
                } catch (Exception e) {

                }
                mCCVideoReader.returnQueueImage(ccImage);

                Message msg = new Message();
                msg.what = MSG_DECODE_FRAME;
                msg.arg1 = index;
                //mImageUpdateHandler.sendMessage(msg);

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

    // TRS RL
    public void getState () {
        mStateHandler.post(new Runnable() {
            @Override
            public void run() {
                while(mIsTRSEncoding){
                    try {
                        CCLog.d(TAG, "TRS: state transmission");
                        //thermalReader.setCPUclock(0);
                        RLagent.transmit_state((int)encode_fps, (int) network_fps, thermalReader.temp_state, thermalReader.cool_state, thermalReader.clock_state, mResolutionIndex, mBitRate);

                        // Episode end..
                        if (thermalReader.temp_state[2] >= 53000){
                            pauseInjectThread();
                        }
                        Thread.sleep(2000);
                    }
                    catch (Exception e){

                    }
                }
            }
        });
    }

    public void pauseInjectThread(){
        mInputHandler.post(new Runnable() {
            @Override
            public void run() {
              try{
                  closeEnDecoder();
                  while(true) {
                      Thread.sleep(30000);
                      // restart
                      if (thermalReader.cool_state[0] == 0) {
                          mInferenceManager.api.reset_episode();
                          break;
                      }
                  }
              }
              catch(Exception e){

              }
            }
        });
    }

    Agent.AgentListener mAgentListener = new Agent.AgentListener() {
        public void setAction (int res, int bitrate, int big_clock){
            mActionHandler.post(new Runnable() {
                @Override
                public void run() {
                    // TODO
                    //mResolutionIndex %= encoder_set.size();
                    mNextResolution = Math.max(mResolutionIndex + res, 0);
                    mNextResolution = Math.min(mNextResolution, encoder_set.size() - 1);

                    //mBitRateIndex += 1;

                    // Set bitrate (0.2~1)

                    mBitRateIndex = Math.max(mBitRateIndex + bitrate, 1);
                    mBitRateIndex = Math.min(mBitRateIndex, mMaxBitRateIndex);

                    // CPU index
                    mClockIndex = big_clock;

                    //set action now
                    mResolutionIndex = mNextResolution;
                    encoder_set.get(mResolutionIndex).changeBitRate(mBitRateIndex*2);
                    thermalReader.setCPUclock(-1);

                    CCLog.d (TAG, "TRS RL Encoder resolution: " + mResolutionIndex + " " + res + " bitrate: " + bitrate +  " big clock: " + big_clock);
                }
            });

        }
    };


}