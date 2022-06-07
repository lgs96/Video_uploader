package kr.ac.snu.nxc.cloudcamera;


import android.content.Context;
import android.net.TrafficStats;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.util.Pair;
import android.widget.Button;
import android.widget.TextView;

import org.apache.commons.net.ntp.NTPUDPClient;
import org.apache.commons.net.ntp.TimeInfo;
import org.json.JSONException;
import org.json.JSONObject;


import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;
import java.nio.ByteBuffer;

import kr.ac.snu.nxc.cloudcamera.library.ImageUtils;
import kr.ac.snu.nxc.cloudcamera.util.CCConstants;
import kr.ac.snu.nxc.cloudcamera.util.CCImage;
import kr.ac.snu.nxc.cloudcamera.util.CCLog;
import kr.ac.snu.nxc.cloudcamera.util.CCUtils;

import static kr.ac.snu.nxc.cloudcamera.util.CCConstants.InferenceInputType.INFERENCE_JPG;
import static kr.ac.snu.nxc.cloudcamera.util.CCConstants.InferenceInputType.INFERENCE_VIDEO;


public class CloudInferenceManager {
    public static final String TIME_SERVER = "time.bora.net";

    private final static String TAG = "CloudShotManager";
    private static final String AROHA_TAG = "AROHA";

    public static final String HOST = "seoul.overlinkapp.org";
//    private static final String HOST = "192.168.1.5";
    public static final int PORT = 3030;
    private static final int PORT2 = 8486;

    //Socket MSG
    private static final int CONN_OK = 1000;
    private static final int KEEP_CONN = 200507;
    private static final int START_CAPTURE = 870604;
    private static final int NEW_FRAME = 777777;
    private static final int MERGE_FRAMES = 777777;
    private static final int VALIDATE_FRAME = 9878987;
    private static final int END_CAPTURE = 890420;
    private static final int RESULT_IMAGE = 9999999;
    private static final int CLOSE_CAMERA = 88888;

    private static final int INIT_SOCKET_INTERVAL = 2000;
    private static final int KEEP_SOCKET_INIT_INTERVAL = 1000;
    private static final int KEEP_SOCKET_INTERVAL = 500;

    static final float UPLINK_MPBS = 15.0f;
    static final float DOWNLINK_MPBS = 97.0f;

    private Object mSocketLock = new Object();

    private Context mContext = null;
    private Handler mEncodingHandler = null;
    private Handler mSocketHandler = null;

    private byte[] mRecvMsg = null;
    private byte[] mRecvAlphaLength = null;
    private byte[] mRecvAlpha = null;
    private boolean mSocketConn = false;
    private boolean mSocketResultConn = false;
    private Socket mSocket = null;
    private DataInputStream mSocketIn = null;
    private DataOutputStream mSocketOut = null;

    private Socket mSocketResult = null;
    private DataInputStream mSocketResultIn = null;
    private DataOutputStream mSocketResultOut = null;

    CCConstants.InferenceInputType mInputType = INFERENCE_JPG;

    private ByteBuffer mKeepConnBuffer = null;

    private boolean mRunning = false;

    private int quality = 50;
    private int sf = 50;
    private float downScale = 0.5f;
    private long NTPTime;

    InferenceCallback mCallback;

    public ApiClient api;
    public String response_body;
    TextView mPerftv;
    private CodecActivity codec_activity;

    long network_last_milli = System.currentTimeMillis();

    ApiClient.UploadListener mUploadListener = new ApiClient.UploadListener() {
        @Override
        public void onResponse(String response) throws JSONException {
            mPerftv.setText("Network performance: " + response);
                JSONObject jObject = new JSONObject(response);
                String fps = jObject.getString("fps");
                String throughput = jObject.getString("th");

                codec_activity.network_fps = Float.parseFloat(fps);
                codec_activity.network_th = Float.parseFloat(throughput);
        }
    };

    public CloudInferenceManager(Context context, InferenceCallback callback) {
        mContext = context;

        api = new ApiClient(HOST, PORT);
        api.setListner(mUploadListener);

        HandlerThread ht = new HandlerThread("CloudShotSocket");
        ht.start();
        mSocketHandler = new Handler(ht.getLooper());

        ht = new HandlerThread("CloudShotEncoding");
        ht.start();
        mEncodingHandler = new Handler(ht.getLooper());

        mRecvMsg = new byte[4];
        mRecvAlphaLength = new byte[12];

        mCallback = callback;

//        NTPTime = getCurrentNetworkTime();
//        CCLog.d(AROHA_TAG, "Server time: " + NTPTime);

        for (int i = 0; i < 5; i++) {
            try {
                CodecActivity.SFQueue.put(50);
                CodecActivity.QPQueue.put(50);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

//        new Thread(new Runnable() {
//            @Override
//            public void run() {
//                while(true) {
//                    if(!CodecActivity.EncodingQueue.isEmpty()) {
//                        try {
//                            Pair<Integer, CCImage> encoded_frame = CodecActivity.EncodingQueue.take();
//                            CCLog.d(AROHA_TAG, "["+encoded_frame.first+"] Encoding Queue");
//                            encodingFrame(encoded_frame.first, encoded_frame.second);
//                        } catch (InterruptedException e) {
//                            e.printStackTrace();
//                        }
//                    }
//                }
//            }
//        }).start();

    }

    public void setPerfTextView(TextView tv, CodecActivity c){
        mPerftv = tv;
        codec_activity = c;
    }

    public void setState(boolean enabled) {
        if (enabled) {
            //mSocketHandler.postDelayed(new InitSocketRunnable(), INIT_SOCKET_INTERVAL);
            mSocketConn = true;
        } else {
            closeSocket();
        }
    }

    public void release() {
        CCLog.d(TAG, "release");
        closeSocket();
    }

    public void startInference(CCConstants.InferenceInputType inputType) {
        CCLog.d(TAG, "startInference");
        mInputType = inputType;
        mRunning = true;
    }

    public void stopInference() {
        CCLog.d(TAG, "stopInference");
        mRunning = false;

//        runKeepSocket(KEEP_SOCKET_INIT_INTERVAL);
    }


    public boolean isReady() {
        return true;
    }

    public static long getCurrentNetworkTime() {
        NTPUDPClient lNTPUDPClient = new NTPUDPClient();
        lNTPUDPClient.setDefaultTimeout(3000);
        long returnTime = 0;
        try {
            lNTPUDPClient.open();
            InetAddress lInetAddress = InetAddress.getByName(TIME_SERVER);
            TimeInfo lTimeInfo = lNTPUDPClient.getTime(lInetAddress);
            // returnTime =  lTimeInfo.getReturnTime(); // local time
            returnTime = lTimeInfo.getMessage().getTransmitTimeStamp().getTime();   //server time
            CCLog.d(AROHA_TAG, " "+ System.currentTimeMillis() + " " + returnTime);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            lNTPUDPClient.close();
        }
        return returnTime;
    }


    public static CCImage downScaleCCImage(CCImage ccImage, float downSampleRatio) {
        int resizeW = (int)((float)ccImage.mWidth * downSampleRatio);
        int resizeH = (int)((float)ccImage.mHeight * downSampleRatio);
        if (resizeH % 2 != 0) {
            resizeH += 1;
        }

        if (resizeW % 2 != 0) {
            resizeW += 1;
        }

        CCImage downScaledImage = new CCImage(resizeW, resizeH, resizeW, ccImage.mTimestamp);
        ImageUtils.downScaleCCImage(downScaledImage.mYuvBuffer, ccImage.mYuvBuffer,
                ccImage.mStride, ccImage.mWidth, ccImage.mHeight, resizeW, resizeH);
        return downScaledImage;
    }

    public void inferenceImage(int frameIndex, CCImage ccImage) {
        CCLog.d(TAG, "inferenceImage : " + frameIndex);
//        mEncodingHandler.post(new EncodingFrameRunnable(frameIndex, ccImage));
        encodingFrame(frameIndex, ccImage);
    }

    public void inferenceImage(int frameIndex, byte[] buffer, int encodedSize) {
        CCLog.d(TAG, "inferenceImage : " + frameIndex);
//        mEncodingHandler.post(new EncodingFrameRunnable(frameIndex, ccImage));
        api.uploadFile(buffer);
       // mSocketHandler.post(new TransmitFrameRunnable(buffer, encodedSize, frameIndex));
    }

    public void encodingFrame(int frameIndex, CCImage ccImage) {
        int encodedSize = 0;
        byte[] encodedData = null;

        try {

            if(!CodecActivity.QPQueue.isEmpty()) {
                quality = CodecActivity.QPQueue.take();
            }
            if(!CodecActivity.SFQueue.isEmpty()) {
                sf = CodecActivity.SFQueue.take();
            }

            downScale = sf / 100.0f;
//            long traffic = TrafficStats.getUidTxBytes(mContext.getApplicationInfo().uid);
//            CCLog.d(AROHA_TAG, "txbytes " + traffic);

            double encodeTime = SystemClock.uptimeMillis();
            if (mInputType == INFERENCE_JPG) {
                CCImage inferenceImage = ccImage;
                if (downScale != 1.0f) {
                    inferenceImage = downScaleCCImage(ccImage, downScale);
                }
                encodedData = inferenceImage.compressJpeg(quality);
                encodedSize = encodedData.length;
                encodeTime = SystemClock.uptimeMillis() - encodeTime;
                mCallback.onEncodingFinish(ccImage, inferenceImage, encodeTime);
                CCLog.d(TAG, "Encoding Time : " + encodeTime);

                CodecActivity.SFHistory.add(sf);
                CodecActivity.QPHistory.add(quality);

            }

            if (!mSocketConn) {
                CCLog.w(TAG, "Server is not connected!");
                return;
            }

            if (mSocketConn) {
                mSocketHandler.post(new TransmitFrameRunnable(encodedData, encodedSize, frameIndex));
            }


        } catch (Exception e) {
            CCLog.e(TAG, "Encoding ERROR : " + e.getMessage());
            stopInference();
            e.printStackTrace();
        }
    }

    class EncodingFrameRunnable implements Runnable {
        int mFrameIndex;
        private CCImage mCCImage;
        int mQuality;
        float mDownScale;

        EncodingFrameRunnable(int frameIndex, CCImage ccImage) {
            mFrameIndex = frameIndex;
            mCCImage = ccImage;
//            mQuality = quality;
//            mDownScale = downScale;
        }

        public void run() {
            encodingFrame(mFrameIndex, mCCImage);
        }
    }

    class InitSocketRunnable implements Runnable {
        public void run() {
            synchronized (mSocketLock) {
                if (mSocketConn) {
                    CCLog.d(TAG, "Already Connected");
                    return;
                }

                try {
                    NTPTime = getCurrentNetworkTime();
                    long current_system_time = System.currentTimeMillis();
                    NTPTime = NTPTime - current_system_time;
                    CCLog.d(AROHA_TAG, "Server time: " + NTPTime);
                    int retry = 5;
                    CCLog.d(TAG, "Socket Init");
                    for (int i = 0; i < retry; i++) {
                        try {
                            mSocketHandler.removeCallbacksAndMessages(null);

                            mSocket = new Socket(HOST, PORT);

                            mSocketOut = new DataOutputStream(mSocket.getOutputStream());
                            mSocketIn = new DataInputStream(mSocket.getInputStream());
                            long t0 = System.currentTimeMillis();
                            CCLog.d(AROHA_TAG, "[Time Sync] t0: " + t0);
                            mSocketOut.write(ByteBuffer.allocate(8).putLong(t0).array());
                            mSocketOut.flush();

                            byte[] temp = new byte[8];

                            mSocketIn.read(temp, 0, 4);
                            float t1 = byteToFloat(temp);
                            mSocketIn.read(temp, 0, 4);
                            float t2 = byteToFloat(temp);
                            CCLog.d(AROHA_TAG, "[Time Sync] t1: " + (long)t1);
                            CCLog.d(AROHA_TAG, "[Time Sync] t2: " + (long)t2);
                            long t3 = System.currentTimeMillis();
                            mSocketOut.write(ByteBuffer.allocate(8).putLong(t3).array());
                            CCLog.d(AROHA_TAG, "[Time Sync] t3: " + t3);
                            float offset = ((t1 - t0) + (t2 - t3))/2;
                            CCLog.d(AROHA_TAG, "[Time Sync] offset: " + offset);

                            int len = mSocketIn.read(mRecvMsg, 0, 4);
                            if (byteToInt(mRecvMsg) == CONN_OK) {
                                mSocketConn = true;
                                CCLog.d(TAG, "Socket Connected");
                                Thread.sleep(3000);
                                mSocketResult = new Socket(HOST, PORT2);
                                mSocketResultOut = new DataOutputStream(mSocketResult.getOutputStream());
                                mSocketResultIn = new DataInputStream(mSocketResult.getInputStream());
                                CCLog.d(TAG, "Socket2 Connected");
                                mSocketResultConn = true;
                                break;
                            }
                        } catch (SocketException se) {
                            CCLog.d(TAG, se.getMessage());
                            closeSocket();
                            try {
                                Thread.sleep(100);
                            } catch (Exception e) {

                            }
                            CCLog.e(TAG, "Socket Retry : " + i);
                        }
                    }

//                    runKeepSocket(KEEP_SOCKET_INIT_INTERVAL);
                    new Thread(new Runnable() {
                        @Override
                        public void run() {

                            while(true) {
                                if (mSocketResultConn) {
                                    try {
                                        mRecvAlphaLength = new byte[4];
                                        byte[] mRecvTransTime = new byte[4];
                                        mSocketResultIn.read(mRecvAlphaLength, 0, 4);
                                        mSocketResultIn.read(mRecvTransTime, 0, 4);
                                        float alpha = byteToFloat(mRecvAlphaLength);
                                        float trans_time = byteToFloat(mRecvTransTime);
                                        double recv_time = SystemClock.uptimeMillis();

                                        mCallback.onReceiveFinish(alpha, recv_time, trans_time);

                                    } catch (IOException e) {
//                                        e.printStackTrace();
                                    }
                                }
                            }
                        }
                    }).start();
                } catch (Exception e) {
                    e.printStackTrace();
                    mSocketConn = false;
                }
            }
        }
    }

    private boolean checkSendResult() {
        try {
            if (mSocketConn) {
                mSocketIn.read(mRecvMsg, 0, 4);
                if (byteToInt(mRecvMsg) == CONN_OK) {
                    return true;
                }
            }
        } catch (Exception e) {
            CCLog.e(TAG, "checkSendResult " + e.getMessage());
        }
        return false;
    }

    public void closeSocket() {
        synchronized (mSocketLock) {
            if (mSocket != null) {
                mEncodingHandler.removeCallbacksAndMessages(null);
                mSocketHandler.removeCallbacksAndMessages(null);

                CCLog.d(TAG, "Socket Close");
                try {
//                    mSocketOut.write(ByteBuffer.allocate(4).putInt(CLOSE_CAMERA).array());
//                    mSocketOut.flush();

                    mSocketOut.close();
                    mSocketIn.close();
                    mSocket.close();


                } catch (IOException e) {
                    CCLog.e(TAG, "Socket Close ERROR " + e.getMessage());
                    e.printStackTrace();
                }
                mSocketConn = false;
                mSocket = null;
            }
        }
    }

    class TransmitFrameRunnable implements Runnable {
        private int mEncodedSize;
        private byte[] mCompressedBytes;
        int mFrameIndex;

        TransmitFrameRunnable(byte[] compressedBytes, int encodedSize, int frameIndex) {
            mEncodedSize = encodedSize;
            mCompressedBytes = compressedBytes;
            mFrameIndex = frameIndex;
        }

        public void run() {
            try {
                if (!mSocketConn) {
                    CCLog.e(TAG, "Socket is not connected");
                    return;
                }

//                if(!CodecActivity.SendQueue.isEmpty()) {
//                    mCompressedBytes = CodecActivity.SendQueue.take();

                    long send_start = System.currentTimeMillis();
                    long start = TrafficStats.getUidTxBytes(mContext.getApplicationInfo().uid);
//                long send_start = getCurrentNetworkTime();
//                double send_start = SystemClock.currentThreadTimeMillis()-NTPTime;
                    mSocketOut.write(ByteBuffer.allocate(4).putInt(mEncodedSize).array());
                    mSocketOut.write(ByteBuffer.allocate(8).putLong(send_start).array());
                    mSocketOut.write(mCompressedBytes, 0, mEncodedSize);
                    mSocketOut.flush();
//                    long packet = 0;
//                    while(true) {
//                        long end = TrafficStats.getUidTxBytes(mContext.getApplicationInfo().uid);
//                        packet = end - start;
//                        if (packet >= (mEncodedSize + 16)) {
//                            break;
//                        }
//                        try {
//                            Thread.sleep(5);
//                        } catch (Exception e) {
//
//                        }
//                    }
                    double sendTime = System.currentTimeMillis()- send_start;
//                    double bw = (((float)packet*8)/(1024*1024))/(sendTime/1000);
//                    CCLog.d(AROHA_TAG, "Send packet: " + packet + " Send time: " + sendTime);

//                long sendTime = getCurrentNetworkTime()- send_start;
//                double sendTime = SystemClock.currentThreadTimeMillis()-NTPTime- send_start;

                    mCallback.onSendFinish(send_start, sendTime, mEncodedSize);

                    CCLog.d(AROHA_TAG, "[" + mFrameIndex + "] Socket Send Finish " +  sendTime + " ms  Data size : " + mEncodedSize + " bytes " + CCUtils.getBytesToMB(mEncodedSize));
//                }

            } catch (Exception e) {
                CCLog.e(TAG, "Socket ERROR : " + e.getMessage());
                e.printStackTrace();
                stopInference();
            }
        }
    }

    public int byteToInt(byte[] arr){
        return (arr[0] & 0xff)<<24 | (arr[1] & 0xff)<<16 | (arr[2] & 0xff)<<8 | (arr[3] & 0xff);
    }

    public static double[] byteToDoubleArray(byte[] byteArray){
        int times = Double.SIZE / Byte.SIZE;
        double[] doubles = new double[byteArray.length / times];
        for(int i=0;i<doubles.length;i++){
            doubles[i] = ByteBuffer.wrap(byteArray, i*times, times).getDouble();
        }
        return doubles;
    }

    public static float byteToFloat(byte[] bytes) {
        int intBits = bytes[0] << 24
                | (bytes[1] & 0xFF) << 16
                | (bytes[2] & 0xFF) << 8
                | (bytes[3] & 0xFF);
        return Float.intBitsToFloat(intBits);
    }

    public long bytesToLong(byte[] bytes) {
        ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
        buffer.put(bytes);
        buffer.flip();//need flip
        return buffer.getLong();
    }
}

