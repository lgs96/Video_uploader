package kr.ac.snu.nxc.cloudcamera.util;

import android.content.Context;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class CCSave implements Runnable {

    private String TAG = "CCSave";
    private boolean is_save = false;
    private String my_content = "";
    private String my_fileName = "";
    private Context context;

    public CCSave (Context context, boolean is_save, String content, String fileName) {
        this.context = context;
        this.is_save = is_save;
        my_content = content;
        my_fileName = fileName;

        CCLog.d(TAG, "Constructor");
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

        String fullName = "video-" + my_fileName + ".csv";
        File file = new File(context.getFilesDir(), fullName);

        if (!file.exists()) {
            CCLog.d(TAG, "Not found");
            notFound = true;
        }
        try {
            fileWriter = new FileWriter(file, true);
            if (notFound) {
                CCLog.d(TAG, "Not found");
                fileWriter.append("time, dec fps,dec tp,enc fps,enc tp,net fps,net tp" + "\n");
            }
            fileWriter.append(content + "\n");
        } catch (Exception e) {
            CCLog.e(TAG, "Error during appending");
            e.printStackTrace();
        } finally {
            try {
                if (fileWriter != null) {
                    assert fileWriter != null;
                    fileWriter.flush();
                    fileWriter.close();
                    CCLog.d(TAG, "File writing done in " + context.getFilesDir() + " "+ fullName);
                }
            } catch (IOException e) {
                CCLog.e(TAG, "Error during flushing");
                e.printStackTrace();
            }
        }
    }
}