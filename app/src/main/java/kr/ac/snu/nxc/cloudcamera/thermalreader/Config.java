package kr.ac.snu.nxc.cloudcamera.thermalreader;

import android.os.Build;

import java.util.ArrayList;
import java.util.List;

import kr.ac.snu.nxc.cloudcamera.util.CCLog;

public class Config {

    private static String TAG = "Config";
    public static String model_name;
    public static List<Integer> temp_index = new ArrayList<>();
    public static List <Integer> cooling_index = new ArrayList<>();
    public static List <Integer> cpu_index = new ArrayList<>();

    public static void getConfiguration () {
        String manufacturer = Build.MANUFACTURER;
        String model = Build.MODEL;
        if (model.toLowerCase().startsWith(manufacturer.toLowerCase())) {
            model_name = capitalize(model);
        } else {
            model_name = capitalize(manufacturer) + " " + model;
        }
        CCLog.d(TAG, "Model name: " + model_name);
        configureTargets();
    }


    private static String capitalize(String s) {
        if (s == null || s.length() == 0) {
            return "";
        }
        char first = s.charAt(0);
        if (Character.isUpperCase(first)) {
            return s;
        } else {
            return Character.toUpperCase(first) + s.substring(1);
        }
    }

    public static void configureTargets () {
        // Samsung S20
        if (model_name.contains("SM-G988N")){
            // temp
            temp_index.add(5);
            temp_index.add(6);
            temp_index.add(50);
            // cooling
            cooling_index.add(0);
            cooling_index.add(1);
            cooling_index.add(2);
            cooling_index.add(22);
            //cpu freq
            cpu_index.add(0);
            cpu_index.add(4);
            cpu_index.add(7);
        }
    }

}

