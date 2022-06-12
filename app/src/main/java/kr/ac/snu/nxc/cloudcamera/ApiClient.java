package kr.ac.snu.nxc.cloudcamera;

import android.net.Uri;
import android.os.FileUtils;
import android.os.Handler;
import android.os.HandlerThread;
import android.widget.TextView;

import com.google.firebase.firestore.auth.User;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;


import org.json.JSONException;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import kr.ac.snu.nxc.cloudcamera.util.CCImage;
import kr.ac.snu.nxc.cloudcamera.util.CCLog;
import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import okhttp3.WebSocket;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.Part;
import retrofit2.http.Query;

public class ApiClient {

    private static String TAG = "ApiClient";
    private String host;
    private int port;
    public RetrofitClientInstance r1;
    public String BASE_URL_API;
    public UploadListener mListner;
    public int call_queue_size;
    public final int MAX_QUEUE_SIZE = 50;

    HandlerThread mApiThread = null;
    Handler mApiHandler = null;

    public ApiClient (String host, int port){
        this.host = host;
        this.port = port;
        r1 = new RetrofitClientInstance(this.host, this.port);

        mApiThread = new HandlerThread("ApiThread");
        mApiThread.start();
        mApiHandler = new Handler(mApiThread.getLooper());

        call_queue_size = 0;
    }



    public class RetrofitClientInstance {

        private Retrofit retrofit;
        public RetrofitClientInstance (String host, int port) {
            BASE_URL_API  = "http://"+host + ":" + port;
        }

        public Retrofit getRetrofitInstance () {
            OkHttpClient client = new OkHttpClient.Builder()
                    //.addNetworkInterceptor(commonNetworkInterceptor)
                    .writeTimeout(5, TimeUnit.SECONDS)
                    .connectTimeout(5, TimeUnit.SECONDS)
                    .readTimeout(5, TimeUnit.SECONDS).build();
            CCLog.i(TAG, "Retrofit");
            if (retrofit == null) {
                Gson gson = new GsonBuilder()
                        .setLenient()
                        .create();
                retrofit = new retrofit2.Retrofit.Builder()
                        .baseUrl(BASE_URL_API).client(client)
                        .callbackExecutor(Executors.newSingleThreadExecutor())
                        .addConverterFactory(GsonConverterFactory.create(gson))
                        .build();
                CCLog.i(TAG, "Retrofit gen");
            }
            return retrofit;
        }
    }

    public interface ResetService {
        @GET("reset")
        Call <ResponseBody> getName(@Query("name") int name);
    }

    public void reset_episode (){
                ResetService service = r1.getRetrofitInstance().create(ResetService.class);

                Call<ResponseBody> call = service.getName(1);
                call.enqueue(new Callback<ResponseBody>() {
                    @Override
                    public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                        if (response.isSuccessful()) {
                            CCLog.d(TAG, "Get Success: " + response.message());
                        } else {
                            CCLog.d(TAG, "Get Error: " + response.message());
                        }
                    }

                    @Override
                    public void onFailure(Call<ResponseBody> call, Throwable t) {
                        CCLog.d(TAG, "Get Failure " + BASE_URL_API + " " + call);
                    }
                });
        }

    public interface UploadReceiptService {
        @Multipart
        @POST("video")
        Call <Object>uploadReceipt(@Part MultipartBody.Part file);
    }


    public interface RLservice {
        @POST("rl")
        Call <JsonObject> getName(@Body JsonObject file);
    }


    public void state_tx (JsonObject RLinfo){
        mApiHandler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    CCLog.d(TAG, "TRS transmit state w/ http");


                    RLservice service = r1.getRetrofitInstance().create(RLservice.class);

                    Call<JsonObject> call = service.getName(RLinfo);
                    CCLog.d(TAG, "TRS transmit state w/ http2 " + call);
                    call.enqueue(new Callback<JsonObject>() {
                        @Override
                        public void onResponse(Call<JsonObject> call, Response<JsonObject> response) {
                            if (response.isSuccessful()){
                                String response_body = new Gson().toJson(response.body());
                                CCLog.d(TAG,"Get Success: " + response.message() + " body: " +response_body);
                                try {
                                    mListner.onResponse(response_body);
                                } catch (Exception e) {
                                    CCLog.d(TAG,"TRS transmit state w/ http2 exception");
                                    e.printStackTrace();
                                }
                            } else {
                                CCLog.d(TAG,"Get Error: " + response.message());
                            }
                        }
                        @Override
                        public void onFailure(Call<JsonObject> call, Throwable t) {
                            CCLog.d(TAG,"Get Failure " + BASE_URL_API +" "+ call);
                        }
                    });
                    //Thread.sleep (10);
                } catch (Exception e) {

                    e.printStackTrace();
                    CCLog.e(TAG, e.getMessage());
                }
            }
        });
    }

    private LinkedList<Call<Object>> callList = new LinkedList<Call<Object>>();

    public void uploadFile (byte[] byteFile){
        mApiHandler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    CCLog.d(TAG, "TRS transmit image w/ http2 " + call_queue_size + " " + MAX_QUEUE_SIZE);

                    //mApiThread.setPriority(Thread.NORM_PRIORITY);

                    // do not send if queue is accumulated
                    UploadReceiptService service = r1.getRetrofitInstance().create(UploadReceiptService.class);

                    String stringValues = "stringValue";

                    RequestBody requestFile =
                            RequestBody.create(
                                    MediaType.parse("video/mp4"),
                                    byteFile
                            );

                    String item = "[1,2,4]";

                    MultipartBody.Part body =
                            MultipartBody.Part.createFormData("body", "frame", requestFile);

                    Call<Object> call = service.uploadReceipt(body);
                    call_queue_size += 1;

                    call.enqueue(new Callback<Object>() {
                        @Override
                        public void onResponse(Call<Object> call, Response<Object> response) {
                            call_queue_size -= 1;
                            if (response.isSuccessful()){
                                String response_body = new Gson().toJson(response.body());
                                try {
                                    mListner.onResponse(response_body);
                                } catch (Exception e) {
                                    CCLog.d(TAG,"TRS transmit image w/ http2 exception");
                                    e.printStackTrace();
                                }
                                CCLog.d(TAG,"TRS transmit image w/ http2 success: " + response_body);
                                //new Gson().toJson(response.body()));
                            } else {
                                CCLog.d(TAG,"TRS transmit image w/ http2 error: " + response.message());
                            }
                        }
                        @Override
                        public void onFailure(Call<Object> call, Throwable t) {
                            call_queue_size -= 1;
                            CCLog.d(TAG,"TRS transmit image w/ http2 failure" );
                            call.cancel();
                            t.printStackTrace();
                        }

                    });
                    //Thread.sleep (10);
                } catch (Exception e) {
                    e.printStackTrace();
                    CCLog.e(TAG, e.getMessage());
                }
            }
        });
    }

    public void setListner(UploadListener Listner){ mListner = Listner; }

    public interface UploadListener{
        public void onResponse (String response);
    }



}