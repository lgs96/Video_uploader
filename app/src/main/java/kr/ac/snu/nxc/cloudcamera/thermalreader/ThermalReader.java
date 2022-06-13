package kr.ac.snu.nxc.cloudcamera.thermalreader;

import static kr.ac.snu.nxc.cloudcamera.thermalreader.Constant.COOL_ID;
import static kr.ac.snu.nxc.cloudcamera.thermalreader.Constant.CPU_ID;
import static kr.ac.snu.nxc.cloudcamera.thermalreader.Constant.TEMP_ID;
import static kr.ac.snu.nxc.cloudcamera.thermalreader.Constant.cooling_path;
import static kr.ac.snu.nxc.cloudcamera.thermalreader.Constant.cpu_path;
import static kr.ac.snu.nxc.cloudcamera.thermalreader.Constant.set_cpu_path;
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
import kr.ac.snu.nxc.cloudcamera.util.CCImage;
import kr.ac.snu.nxc.cloudcamera.util.CCLog;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
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

    public String big1_path;
    public String big2_path;
    public int [] avail_cpu_freq1;
    public int [] avail_cpu_freq2;

    // action
    public int mActionindex1;
    public int mActionindex2;

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

        try {
            InitializeTrace();
        }
        catch (Exception e){

        }
        // Start tracing for every second
        CCLog.d(TAG, "Start thermal tracing");
        mTraceThread = new HandlerThread("Trace");
        mTraceThread.start();
        mTraceHandler = new Handler(mTraceThread.getLooper());
        mTraceHandler.post(new StartTrace());
    }

    public void InitializeTrace () throws IOException {
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

        // Get CPU info for action
        getCPUinfo ();

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

    public ArrayList<String> ReadFileSplit (String path){
        StringBuffer strBuffer = new StringBuffer();
        ArrayList<String> words = new ArrayList<>();
        String [] wordsArray;
        try{
            InputStream is = new FileInputStream(path);
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            String line="";
            while((line=reader.readLine())!=null){
                CCLog.d(TAG, "Split Path: " + path);
                CCLog.d(TAG, "Split Line: " + line);
                wordsArray = line.split(" ");
                for (String each : wordsArray){
                    words.add(each);
                }
            }
            reader.close();
            is.close();
        }catch (IOException e){
            e.printStackTrace();
        }
        return words;
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



    public int cpu_num = 8;
    LinkedList<int []> avail_cpu_freq_set = new LinkedList<int []>();
    int [] action_set = new int[cpu_num];

    public void getCPUinfo () {
        for (int i = 0; i < cpu_num; i++){
            String big_path = set_cpu_path + Integer.toString(i) + "/cpufreq";
            String avail = big_path + "/scaling_available_frequencies";
            ArrayList<String> avail_freq = ReadFileSplit(avail);

            int [] avail_cpu_freq = new int [avail_freq.size()];

            for (int j = 0; j < avail_freq.size(); j++){
                avail_cpu_freq[j] = Integer.parseInt(avail_freq.get(j));
                CCLog.d(TAG, "Exception5 " + i + " " +  avail_freq.get(j));
            }

            avail_cpu_freq_set.add(avail_cpu_freq);
            action_set[i] = (int)(avail_freq.size()/2);
        }
    }

    public void setCPUclock (int cpu_index){

        if (cpu_index ==0 ){
            return;
        }

        Process p;
        DataOutputStream os;
        try {
            p = Runtime.getRuntime().exec("su");
            os = new DataOutputStream(p.getOutputStream());
            String gov_path = "";
            String max_freq_path = "";
            String min_freq_path = "";
            int current_cpu_clock = 0;
            for (int i = 0; i < avail_cpu_freq_set.size(); i++) {
                gov_path = set_cpu_path + Integer.toString(i) + "/cpufreq/scaling_governor";
                max_freq_path = set_cpu_path + Integer.toString(i) + "/cpufreq/scaling_max_freq";
                min_freq_path = set_cpu_path + Integer.toString(i) + "/cpufreq/scaling_min_freq";

                action_set[i] += cpu_index;
                action_set[i] = Math.max(action_set[i] , 0);
                action_set[i] = Math.min(action_set[i] , avail_cpu_freq_set.get(i).length - 1);
                current_cpu_clock = avail_cpu_freq_set.get(i)[action_set[i]];

                os.writeBytes("echo userspace > " + gov_path + "\n");
                os.writeBytes("echo " + current_cpu_clock + " > " + max_freq_path + "\n");
                os.writeBytes("echo " + current_cpu_clock + " > " + min_freq_path + "\n");
                CCLog.d("TAG", "command " + "echo " + current_cpu_clock + " > " + min_freq_path + "\n");
            }
            os.writeBytes("exit\n");
            os.flush();
            os.close();

            CCLog.d(TAG, "Set CPU Path: " + gov_path + " " + max_freq_path + " " + min_freq_path);
            CCLog.d(TAG, "Set CPU clock done " + " specified CPU index: "+ current_cpu_clock);
        }
        catch (Exception e){
            CCLog.d(TAG, "Exception");
        }
    }

    /*
    public void setCPUclock (int cpu_index) {
        if (cpu_index ==0 ){
            return;
        }

        CCLog.d(TAG, "Start to setup CPU clock1 " + mActionindex1 + " " + mActionindex2);
        mActionindex1 += cpu_index;
        mActionindex1 = Math.max(mActionindex1, 0);
        mActionindex1 = Math.min(mActionindex1, avail_cpu_freq1.length - 1);
        mActionindex2 += cpu_index;
        mActionindex2 = Math.max(mActionindex2, 0);
        mActionindex2 = Math.min(mActionindex2, avail_cpu_freq2.length - 1);
        CCLog.d(TAG, "Start to setup CPU clock2 " + mActionindex1 + " " + mActionindex2);
        int current_cpu_clock1 = avail_cpu_freq1[mActionindex1];
        int current_cpu_clock2 = avail_cpu_freq2[mActionindex2];

        try {
            Process p = Runtime.getRuntime().exec("su");
            DataOutputStream os = new DataOutputStream(p.getOutputStream());
            os.writeBytes("echo userspace > " + big1_path + "/scaling_governor\n");
            os.writeBytes("echo userspace > " + big2_path + "/scaling_governor\n");
            os.writeBytes("echo " + current_cpu_clock1 + " > " +big1_path+ "/scaling_max_freq\n");
            os.writeBytes("echo " + current_cpu_clock1 + " > " +big1_path+ "/scaling_min_freq\n");
            os.writeBytes("echo " + current_cpu_clock2 + " > " +big2_path+ "/scaling_max_freq\n");
            os.writeBytes("echo " + current_cpu_clock2 + " > " +big2_path+ "/scaling_min_freq\n");
            os.writeBytes("exit\n");

            os.flush();
            os.close();

            String big = "";
            List<String> cpu_list = GetList(cpu_path, Config.cpu_index, "scaling_cur_freq");
            for (int i = 0; i < cpu_values.length; i++) {
                big = cpu_list.get(i);
            }
            CCLog.d(TAG, "Set CPU clock done " + " CPU index: " +cpu_index + " "+  current_cpu_clock1 + " " + current_cpu_clock2 + " " + avail_cpu_freq1.length + " Real: " + big);
        }
        catch (Exception e){
            CCLog.d(TAG, "Set CPU clock exception");
        }
    }
     */
}
