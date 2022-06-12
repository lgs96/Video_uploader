package kr.ac.snu.nxc.cloudcamera.RLagent;


import static kr.ac.snu.nxc.cloudcamera.CloudInferenceManager.HOST;
import static kr.ac.snu.nxc.cloudcamera.CloudInferenceManager.PORT;
import static kr.ac.snu.nxc.cloudcamera.CodecActivity.resolution_states;

import android.media.Image;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.json.JSONException;
import org.json.JSONObject;

import kr.ac.snu.nxc.cloudcamera.ApiClient;
import kr.ac.snu.nxc.cloudcamera.util.CCLog;

public class Agent {
    private final String TAG = "Agent";

    // network agent
    ApiClient api;

    // state to tx
    public int little_cpu_temp;
    public int big_cpu_temp;
    public int modem_temp;

    // action
    public int resolution;
    public int little_clock;
    public int big_clock;
    public int bitrate;
    private AgentListener mListener;

    public Agent (){
        api = new ApiClient(HOST, PORT);
        api.setListner(mUploadListener);
    }

    public void setListener (AgentListener listener){mListener = listener;}

    public interface AgentListener {
        public void setAction (int res, int bitrate, int big_clock);
    }

    ApiClient.UploadListener mUploadListener = new ApiClient.UploadListener() {
        @Override
        public void onResponse(String response){
            receive_action (response);
        }
    };


    public void transmit_state (int encode_fps, int network_fps, int [] temp, int [] cool, int [] clock,
                                int resolution, int bitrate){

        resolution = resolution_states[resolution];

        JsonObject obj = new JsonObject();
        //Encoding rate
        //E2E frame rate
        obj.addProperty("encode_fps", encode_fps);
        obj.addProperty("network_fps", network_fps);
        obj.addProperty("big_temp", temp[1]);
        obj.addProperty("modem_temp", temp[2]);
        //obj.addProperty("cooling", cool[0]);
        obj.addProperty("big_clock2", clock[2]);
        obj.addProperty("res", resolution);
        //obj.addProperty("bitrate", bitrate);
        api.state_tx(obj);
        CCLog.d(TAG, "transmit state");
    }

    public void receive_action (String action) {
        CCLog.d(TAG, "Receive action " + action);
        try {
            JSONObject jObject = new JSONObject(action);
            resolution = Integer.parseInt(jObject.getString("res"));
            bitrate = Integer.parseInt(jObject.getString("bitrate"));
            big_clock  = Integer.parseInt(jObject.getString("clock"));

            mListener.setAction(resolution, bitrate, big_clock);
        }
        catch(Exception e){
        }
    }
}
