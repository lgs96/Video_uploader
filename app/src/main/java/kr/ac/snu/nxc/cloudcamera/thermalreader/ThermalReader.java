package kr.ac.snu.nxc.cloudcamera.thermalreader;

import static kr.ac.snu.nxc.cloudcamera.thermalreader.Constant.COOL_ID;
import static kr.ac.snu.nxc.cloudcamera.thermalreader.Constant.CPU_ID;
import static kr.ac.snu.nxc.cloudcamera.thermalreader.Constant.TEMP_ID;
import static kr.ac.snu.nxc.cloudcamera.thermalreader.Constant.cooling_path;
import static kr.ac.snu.nxc.cloudcamera.thermalreader.Constant.cpu_path;
import static kr.ac.snu.nxc.cloudcamera.thermalreader.Constant.temp_path;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import kr.ac.snu.nxc.cloudcamera.thermalreader.Config;
import kr.ac.snu.nxc.cloudcamera.util.CCLog;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class ThermalReader {
    private static final String TAG = "ThermalReader";

    private List<String> temp_types;
    private List<String> cooling_types;
    private List<String> cpu_types;

    private ArrayList<String> [] temp_values;
    private ArrayList<String> [] cooling_values;
    private ArrayList<String> [] cpu_values;

    TextView mThermalText;
    TextView mCPUText;
    Button mStartButton;
    Button mStopButton;

    private List<String> folders;
    private List<String> types;
    String types_csv = "";

    private String stat_time = "";

    HandlerThread mTraceThread = null;
    Handler mTraceHandler = null;

    // Parameters from the external class (CodecActivity)
    public String save_string;
    public boolean run_trace = false;
    public boolean run_record = false;
    private final int record_time = 5000;
    public String codec_content;
    public String temp_info = "";
    public String cpu_info = "";

    // State for RL
    public int [] temp_state;
    public int [] cool_state;
    public int [] clock_state;

    public void readThermal (){

        File dir = new File("/sdcard/zts/");
        if(!dir.exists()){
            try{
                dir.mkdirs();
            }
            catch(Exception e){
                e.printStackTrace();
            }
        }

        InitializeTrace();
        // Start tracing for every second
        CCLog.d(TAG, "Start thermal tracing");
        mTraceThread = new HandlerThread("Trace");
        mTraceThread.start();
        mTraceHandler = new Handler(mTraceThread.getLooper());
        mTraceHandler.post(new StartTrace());
    }

    public void InitializeTrace (){
        // Get types of target traces. It will be used as a legend of a graph.

        final SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss");
        stat_time = f.format(new Date());
        Config.getConfiguration();
        temp_types = GetList (temp_path, Config.temp_index, "type");
        cooling_types = GetList (cooling_path, Config.cooling_index, "type");
        cpu_types = new ArrayList<String>();
        cpu_types.add("Little");
        cpu_types.add("Big1");
        cpu_types.add("Big2");

        temp_values = new ArrayList[Config.temp_index.size()];
        cooling_values = new ArrayList[Config.cooling_index.size()];
        cpu_values = new ArrayList[Config.cpu_index.size()];

        temp_state = new int[Config.temp_index.size()];
        cool_state = new int[Config.cooling_index.size()];
        clock_state = new int[Config.cpu_index.size()];

        // Get 2-d list
        for (int i = 0; i < Config.temp_index.size(); i++){
            temp_values[i] = new ArrayList<String>();
            temp_values[i].add(temp_types.get(i));
            types_csv = types_csv + "," + temp_types.get(i);
        }
        for (int i = 0; i < Config.cooling_index.size(); i++){
            cooling_values[i] = new ArrayList<String>();
            cooling_values[i].add(cooling_types.get(i));
            types_csv = types_csv + "," + cooling_types.get(i);
        }
        for (int i = 0; i < Config.cpu_index.size(); i++){
            cpu_values[i] = new ArrayList<String>();
            cpu_values[i].add(cpu_types.get(i));
            types_csv = types_csv + "," + cpu_types.get(i);
        }

        types_csv += "fps (decode),throughput (decode),fps (encode),throughput (encode),fps (network),throughput (network)";
        types_csv += "\n";

        CCLog.d(TAG, "Types csv is " + types_csv);

    }

    public class StartTrace implements Runnable {
        public void run() {
            run_trace = true;
            run_record = true;
            int time_count = 0;

            while(true) {
                CCLog.d(TAG, "Try tracing");
                try {
                    CCLog.d(TAG, "Tracing is working");
                    // Temp
                    temp_info = "";
                    cpu_info = "";

                    String content = Integer.toString(time_count);
                    List<String> temp_list = GetList(temp_path, Config.temp_index, "temp");
                    for (int i = 0; i < temp_values.length; i++){
                        content = content + "," + temp_list.get(i);
                        temp_info = temp_info + " " + temp_list.get(i);
                        temp_state[i] = Integer.parseInt(temp_list.get(i));
                    }
                    // Cooling
                    List<String> cooling_list = GetList(cooling_path, Config.cooling_index, "cur_state");
                    for (int i = 0; i < cooling_values.length; i++) {
                        content = content + "," + cooling_list.get(i);
                        cool_state[i] = Integer.parseInt(cooling_list.get(i));
                    }
                    // CPU
                    List<String> cpu_list = GetList(cpu_path, Config.cpu_index, "scaling_cur_freq");
                    for (int i = 0; i < cpu_values.length; i++) {
                        content = content + "," + cpu_list.get(i);
                        cpu_info = cpu_info + " " + cpu_list.get(i);
                        clock_state[i] = Integer.parseInt(cpu_list.get(i));
                    }
                    content = content + "," + codec_content;
                    content += "\n";

                    if (run_record){
                        writeToFile(content, "zts", stat_time+"_"+save_string+".csv");
                    }

                    CCLog.d(TAG, content + " " + temp_values.length + " " + cpu_values.length);
                    CCLog.d(TAG, "================Traced================");
                    if (run_trace == false)
                        break;
                    else {
                        time_count += 1;
                        if (time_count >= record_time - 2){
                            run_record = false;
                        }
                        Thread.sleep(1000);
                    }
                }
                catch(Exception e){
                    CCLog.e(TAG, "Error during tracing");
                }
            }
            //SaveTrace();
        }
    }

    public void StopTrace(){
        run_trace = false;
    }

    public void SaveTrace() {
        int list_length = temp_values[0].size();
        CCLog.d(TAG, list_length + " "+ temp_values.length + " " +cooling_values.length + " " + cpu_values.length);
        int time_step = 0;
        String temp_info = "";
        for (int i = 0; i < list_length; i++) {
            temp_info = Integer.toString(time_step);
            for (int j = 0; j < temp_values.length; j++) {
                CCLog.d(TAG, temp_values[j].get(i));
                temp_info = temp_info + "," + temp_values[j].get(i);
            }
            for (int j = 0; j < cooling_values.length; j++) {
                CCLog.d(TAG, temp_info + " " + cooling_values.length);
                temp_info = temp_info + "," + cooling_values[j].get(i);
            }
            for (int j = 0; j < cpu_values.length; j++) {
                temp_info = temp_info + "," + cpu_values[j].get(i);
            }
            time_step += 1;
            temp_info += "\n";
            //RTLog.d(TAG, temp_info);
            writeToFile(temp_info, "zts", stat_time+"_"+save_string+".csv");
        }
    }

    public void GetCodecString (String content){
        codec_content = content;
    }

    public List<String> GetList(String folderPath, List<Integer> index, String info){

        List <String> types = new ArrayList<String>();

        for (int i = 0; i < index.size(); i++){
            String temp_path = folderPath + Integer.toString(index.get(i));
            String current_type = ReadFile(temp_path + "/" + info);

            types.add(current_type);
        }

        return types;
    }

    public String ReadFile (String path){
        StringBuffer strBuffer = new StringBuffer();
        try{
            InputStream is = new FileInputStream(path);
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            String line="";
            while((line=reader.readLine())!=null){
                CCLog.d(TAG, "Path: " + path);
                CCLog.d(TAG, "Line: " + line);
                strBuffer.append(line+"");
            }

            reader.close();
            is.close();
        }catch (IOException e){
            e.printStackTrace();
            return "";
        }
        return strBuffer.toString();
    }

    public void writeToFile(String content, String folder_name, String fileName) {

        FileWriter fileWriter = null;
        String path = Environment.getExternalStorageDirectory().getPath();
        boolean notFound = false;

        File file = new File("/sdcard/zts/" + fileName);

        Log.i("WriteToFile", path + "/" +folder_name +"/" + fileName);
        if (!file.exists()) {
            Log.i("StatusDataCollector", "Not found");
            notFound = true;
        }
        try {
            fileWriter = new FileWriter(file, true);
            if (notFound)
                fileWriter.append(types_csv);
            fileWriter.append(content);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                fileWriter.flush();
                fileWriter.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }


}
