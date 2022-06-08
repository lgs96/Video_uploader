package kr.ac.snu.nxc.cloudcamera.RLagent;


import static kr.ac.snu.nxc.cloudcamera.CloudInferenceManager.HOST;
import static kr.ac.snu.nxc.cloudcamera.CloudInferenceManager.PORT;

import android.media.Image;

import org.json.JSONException;
import org.json.JSONObject;

import kr.ac.snu.nxc.cloudcamera.ApiClient;

public class Agent {
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
        public void setAction (int res, int bitrate, int little_clock, int big_clock);
    }

    ApiClient.UploadListener mUploadListener = new ApiClient.UploadListener() {
        @Override
        public void onResponse(String response) throws JSONException {
            JSONObject jObject = new JSONObject(response);
            receive_action (jObject);
        }
    };

    public void transmit_state (int [] temp, int [] cool, int [] clock,
                                int resolution, int bitrate) throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put("little_temp", temp[0]);
        obj.put("big_temp", temp[1]);
        obj.put("modem_temp", temp[2]);
        obj.put("cooling", cool[0]);
        obj.put("little_clock", clock[0]);
        obj.put("big_clock1", clock[1]);
        obj.put("big_clock2", clock[2]);
        obj.put("res", resolution);
        obj.put("bitrate", bitrate);
        api.state_tx(obj);
    }

    public void receive_action (JSONObject action) throws JSONException {
        resolution = Integer.parseInt(action.getString("res"));
        bitrate = Integer.parseInt(action.getString("bitrate"));
        little_clock  = Integer.parseInt(action.getString("little_clock"));
        big_clock  = Integer.parseInt(action.getString("big_clock"));

        mListener.setAction(resolution, bitrate, little_clock, big_clock);
    }
}
