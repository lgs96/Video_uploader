package kr.ac.snu.nxc.cloudcamera;

import static kr.ac.snu.nxc.cloudcamera.CloudInferenceManager.HOST;
import static kr.ac.snu.nxc.cloudcamera.CloudInferenceManager.PORT;

import android.app.Application;
import android.content.Context;
import android.util.Log;

import org.chromium.net.CronetEngine;
import org.chromium.net.CronetException;
import org.chromium.net.UploadDataProvider;
import org.chromium.net.UploadDataProviders;
import org.chromium.net.UrlRequest;
import org.chromium.net.UrlResponseInfo;
import org.json.JSONObject;

import java.nio.ByteBuffer;
import java.util.Date;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import kr.ac.snu.nxc.cloudcamera.util.CCLog;

public class CronetClient extends Application {
    private static final String TAG = "CronetClient";
    public CronetEngine cronetEngine;
    public ExecutorService cronetCallbackExecutorService;

    public CronetClient (){
        getCronetEngine();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        cronetEngine = getCronetEngine();
        cronetCallbackExecutorService = Executors.newFixedThreadPool(4);
    }

    private synchronized CronetEngine getCronetEngine() {
        // Lazily create the Cronet engine.
        if (cronetEngine == null) {
            CronetEngine.Builder myBuilder = new CronetEngine.Builder(this);
            // Enable caching of HTTP data and
            // other information like QUIC server information, HTTP/2 protocol and QUIC protocol.
            String hint_host = HOST;
            cronetEngine = myBuilder
                    .enableHttpCache(CronetEngine.Builder.HTTP_CACHE_IN_MEMORY, 100 * 1024)
                    .enableHttp2(true)
                    .build();
            CCLog.d(TAG, "CronetEngine is created.");
        }
        return cronetEngine;
    }

    public void startSending (){

        String urlString = HOST + ":" + String.valueOf(PORT) + "/video";

        Executor executor = Executors.newSingleThreadExecutor();
        CronetClient.MyUrlRequestCallback cronet_cb = new CronetClient.MyUrlRequestCallback();

        CCLog.d(TAG, urlString + " is the destination url");
        UrlRequest.Builder requestBuilder = cronetEngine.newUrlRequestBuilder(
                urlString, cronet_cb, executor);
        requestBuilder.setPriority(4);
        requestBuilder.addHeader("Content-Type", "application/json; charset=UTF-8");
        requestBuilder.setHttpMethod("POST");


        byte [] object = new byte[10*1024];

        try {
            Log.d(TAG, "Tx object");
            UploadDataProvider myUploadDataProvider = UploadDataProviders.create(object);
            requestBuilder.setUploadDataProvider(myUploadDataProvider, executor);
            UrlRequest request = requestBuilder.build();

            request.start();

        }
        catch(Exception e){
            e.printStackTrace();
        }
    }

    class MyUrlRequestCallback extends UrlRequest.Callback{

        @Override
        public void onRedirectReceived(UrlRequest request, UrlResponseInfo info, String newLocationUrl) {
            //Log.i(TAG, "onRedirectReceived method called.");
            // You should call the request.followRedirect() method to continue
            // processing the request.
            request.followRedirect();
        }

        @Override
        public void onResponseStarted(UrlRequest request, UrlResponseInfo info) {
            //Log.i(TAG, "onResponseStarted method called.");
            // You should call the request.read() method before the request can be
            // further processed. The following instruction provides a ByteBuffer object
            // with a capacity of 102400 bytes to the read() method.

            request.read(ByteBuffer.allocateDirect(102400));
        }

        @Override
        public void onReadCompleted(UrlRequest urlRequest, UrlResponseInfo urlResponseInfo, ByteBuffer byteBuffer) throws Exception {

            // You should keep reading the request until there's no more data.
            CCLog.d(TAG, "onRead method called.");
        }

        @Override
        public void onSucceeded(UrlRequest request, UrlResponseInfo info) {

            CCLog.d(TAG, "onSucceeded method called.");
        }

        @Override
        public void onFailed(UrlRequest urlRequest, UrlResponseInfo urlResponseInfo, CronetException e) {
            CCLog.d(TAG, "onFailed method called.");
        }
    }
}
