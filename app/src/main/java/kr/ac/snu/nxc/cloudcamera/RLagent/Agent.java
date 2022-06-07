package kr.ac.snu.nxc.cloudcamera.RLagent;


import static kr.ac.snu.nxc.cloudcamera.CloudInferenceManager.HOST;
import static kr.ac.snu.nxc.cloudcamera.CloudInferenceManager.PORT;

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
    public float resolution;
    public int little_clock;
    public int big_clock;
    public int bitrate;

    public Agent (){
        api = new ApiClient(HOST, PORT);
        api.setListner(mUploadListener);
    }

    ApiClient.UploadListener mUploadListener = new ApiClient.UploadListener() {
        @Override
        public void onResponse(String response) throws JSONException {
            JSONObject jObject = new JSONObject(response);
            receive_action (jObject);
        }
    };

    public void transmit_state (int little_cpu_temp, int big_cpu_temp, int modem_temp) throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put("little_temp", little_cpu_temp);
        obj.put("big_temp", big_cpu_temp);
        obj.put("modem_temp", modem_temp);
        api.state_tx(obj);
    }

    public void receive_action (JSONObject action) throws JSONException {
        resolution = Integer.parseInt(action.getString("res"));
        little_clock  = Integer.parseInt(action.getString("little_clock"));
        big_clock  = Integer.parseInt(action.getString("big_clock"));
        bitrate = Integer.parseInt(action.getString("bitrate"));

        set_clock ();
        set_resolution ();
        set_bitrate ();
    }

    public void set_clock (){
        // TODO

    }

    public void set_resolution () {
        // TODO

    }

    public void set_bitrate () {
        // TODO

    }
}
