package kr.ac.snu.nxc.cloudcamera;


import static kr.ac.snu.nxc.cloudcamera.util.CCConstants.InferenceInputType.INFERENCE_JPG;

import android.content.Context;
import android.net.TrafficStats;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.widget.Toast;

import org.apache.commons.net.ntp.NTPUDPClient;
import org.apache.commons.net.ntp.TimeInfo;

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


public class StreamInferenceManager {
    public static final String TIME_SERVER = "time.bora.net";

    private final static String TAG = "StreamInferenceManager";
    private static final String AROHA_TAG = "AROHA";

    private static final String HOST = "147.46.130.213";
//    private static final String HOST = "192.168.1.5";
    private static final int PORT = 8485;
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

    public StreamInferenceManager(Context context) {
        mContext = context;

        HandlerThread ht = new HandlerThread("CloudShotSocket");
        ht.start();
        mSocketHandler = new Handler(ht.getLooper());

        mRecvMsg = new byte[4];
    }

    public void setState(boolean enabled) {
        if (enabled) {
            mSocketHandler.postDelayed(new InitSocketRunnable(), INIT_SOCKET_INTERVAL);

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

    public void inferenceImage(int frameIndex, byte[] buffer, int encodedSize) {
        CCLog.d(TAG, "inferenceImage : " + frameIndex);
//        mEncodingHandler.post(new EncodingFrameRunnable(frameIndex, ccImage));
        mSocketHandler.post(new TransmitFrameRunnable(buffer, encodedSize, frameIndex));
    }


    class InitSocketRunnable implements Runnable {
        public void run() {
            synchronized (mSocketLock) {
                if (mSocketConn) {
                    CCLog.d(TAG, "Already Connected");
                    return;
                }

                try {
                    int retry = 5;
                    CCLog.d(TAG, "Socket Init");
                    for (int i = 0; i < retry; i++) {
                        try {
                            mSocketHandler.removeCallbacksAndMessages(null);

                            mSocket = new Socket(HOST, PORT);

                            mSocketOut = new DataOutputStream(mSocket.getOutputStream());
                            mSocketIn = new DataInputStream(mSocket.getInputStream());

                            int len = mSocketIn.read(mRecvMsg, 0, 4);
                            if (byteToInt(mRecvMsg) == CONN_OK) {
                                mSocketConn = true;
                                CCLog.d(TAG, "Socket Connected");
                                mSocketResultConn = true;
                                Toast.makeText(mContext, "Connected", Toast.LENGTH_SHORT).show();
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

                } catch (Exception e) {
                    e.printStackTrace();
                    mSocketConn = false;
                }
            }
        }
    }

    public void closeSocket() {
        synchronized (mSocketLock) {
            if (mSocket != null) {
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

                    mSocketOut.write(ByteBuffer.allocate(4).putInt(mEncodedSize).array());
                    mSocketOut.write(mCompressedBytes, 0, mEncodedSize);
                    mSocketOut.flush();
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

