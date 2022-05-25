package kr.ac.snu.nxc.cloudcamera.thermalreader;

public class Constant {
    public final static String temp_path = "/sys/class/thermal/thermal_zone";
    public final static String cooling_path = "/sys/class/thermal/cooling_device";
    public final static String cpu_path = "/sys/devices/system/cpu/cpufreq/policy";

    public final static int TEMP_ID = 0;
    public final static int COOL_ID = 1;
    public final static int CPU_ID = 2;
}
