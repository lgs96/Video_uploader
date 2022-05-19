package kr.ac.snu.nxc.cloudcamera.util;

import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class CCSave implements Runnable {

    private String TAG = "CCSave";
    private static final SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private static final String stat_time = f.format(new Date());
    private boolean is_save = false;
    private String my_content = "";
    private String my_fileName = "";

    public CCSave (boolean is_save, String content, String fileName) {
        this.is_save = is_save;
        my_content = content;
        my_fileName = fileName;

        CCLog.d(TAG, "Constructor");
        File dir = new File("/sdcard/CCSave/");
        if(!dir.exists()){
            try{
                dir.mkdirs();
            }
            catch(Exception e){
                e.printStackTrace();
            }
        }
        dir = new File("/sdcard/CCSave/" + stat_time);
        if(!dir.exists()){
            try{
                dir.mkdirs();
            }
            catch(Exception e){
                e.printStackTrace();
            }
        }
    }

    public void run(){
        writeToFile (my_content, my_fileName);
    }


    public void writeToFile(String content, String fileName) {
        if (!is_save) {
            return;
        }
        FileWriter fileWriter = null;
        boolean notFound = false;

        File file = new File("/sdcard/CCSave/" + stat_time + "/" + fileName + ".csv");

        if (!file.exists()) {
            CCLog.d(TAG, "Not found");
            notFound = true;
        }
        try {
            fileWriter = new FileWriter(file, true);
            if (notFound) {
                CCLog.d(TAG, "Not found");
                fileWriter.append("fps,throughput,fps,throughput,fps,throughput" + "\n");
            }
            fileWriter.append(content + "\n");
        } catch (Exception e) {
            CCLog.e(TAG, "Error during appending");
            e.printStackTrace();
        } finally {
            try {
                fileWriter.flush();
                fileWriter.close();
                CCLog.d(TAG, "File writing done" + "/sdcard/CCSave/" + stat_time + "/" + fileName + ".csv");
            } catch (IOException e) {
                CCLog.e(TAG, "Error during flushing");
                e.printStackTrace();
            }
        }
    }
}