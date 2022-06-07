package kr.ac.snu.nxc.cloudcamera;

import android.net.Uri;
import android.os.FileUtils;
import android.widget.TextView;

import com.google.firebase.firestore.auth.User;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import kr.ac.snu.nxc.cloudcamera.util.CCLog;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
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

public class ApiClient {

    private static String TAG = "ApiClient";
    private String host;
    private int port;
    public RetrofitClientInstance r1;
    public String BASE_URL_API;
    public UploadListener mListner;
    public int call_queue_size;
    public final int MAX_QUEUE_SIZE = 50;

    public ApiClient (String host, int port){
        this.host = host;
        this.port = port;
        r1 = new RetrofitClientInstance(this.host, this.port);
        call_queue_size = 0;
    }

    public class RetrofitClientInstance {

        private Retrofit retrofit;
        public RetrofitClientInstance (String host, int port) {
            BASE_URL_API  = "http://"+host + ":" + port;
        }

        public Retrofit getRetrofitInstance () {
            OkHttpClient client = new OkHttpClient.Builder()
                    .connectTimeout(60, TimeUnit.SECONDS)
                    .readTimeout(60, TimeUnit.SECONDS).build();
            CCLog.i(TAG, "Retrofit");
            if (retrofit == null) {
                Gson gson = new GsonBuilder()
                        .setLenient()
                        .create();
                retrofit = new retrofit2.Retrofit.Builder()
                        .baseUrl(BASE_URL_API).client(client)
                        .addConverterFactory(GsonConverterFactory.create(gson))
                        .build();
                CCLog.i(TAG, "Retrofit gen");
            }
            return retrofit;
        }
    }

    public interface UploadReceiptService {
        @Multipart
        @POST("video")
        Call <Object>uploadReceipt(@Part MultipartBody.Part file);
    }


    public interface RLservice {
        @GET("RL")
        Call <String> getName();
    }

    public void state_tx (JSONObject RLinfo){
        RLservice service = r1.getRetrofitInstance().create(RLservice.class);

        Call<String> call = service.getName();
        call.enqueue(new Callback<String>() {
            @Override
            public void onResponse(Call<String> call, Response<String> response) {
                if (response.isSuccessful()){
                    CCLog.d(TAG,"Get Success: " + response.message());
                } else {
                    CCLog.d(TAG,"Get Error: " + response.message());
                }
            }

            @Override
            public void onFailure(Call<String> call, Throwable t) {
                CCLog.d(TAG,"Get Failure " + BASE_URL_API +" "+ call);
            }
        });
    }

    public void uploadFile (byte[] byteFile){

        // do not send if queue is accumulated
        if (call_queue_size > MAX_QUEUE_SIZE){
            return;
        }

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

        RequestBody items = RequestBody.create(MediaType.parse("application/json"), item);
        RequestBody stringValue = RequestBody.create(MediaType.parse("text/plain"), stringValues);

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
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                    CCLog.d(TAG,"Upload Success: " + response_body);
                    //new Gson().toJson(response.body()));
                } else {
                    CCLog.d(TAG,"Upload Error: " + response.message());
                }

            }
            @Override
            public void onFailure(Call<Object> call, Throwable t) {
                call_queue_size -= 1;
                CCLog.d(TAG,"Upload Failure" );
                t.printStackTrace();
            }
        });
    }

    public void setListner(UploadListener Listner){ mListner = Listner; }

    public interface UploadListener{
        public void onResponse(String response) throws JSONException;
    }

}