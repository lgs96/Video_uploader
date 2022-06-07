package kr.ac.snu.nxc.cloudcamera.util;

import android.os.Environment;
import android.util.Size;

public class CCConstants {
    public static final int DONW_RATIO = 16;

    public static final int MAX_CODEC_QUEUE_SIZE = 5;

    //Activity
    public static final int ACTIVITY_MAIN = 0;
    public static final int ACTIVITY_CAMERA = ACTIVITY_MAIN + 1;
    public static final int ACTIVITY_CODEC_GALLERY = ACTIVITY_MAIN + 2;
    public static final int ACTIVITY_SETTING = ACTIVITY_MAIN + 3;
    public static final int ACTIVITY_DONE = ACTIVITY_MAIN + 4;

    public static final String[] ACTIVITY_NAME;
    static {
        ACTIVITY_NAME = new String[ACTIVITY_DONE];
        ACTIVITY_NAME[ACTIVITY_MAIN] = "MAIN";
        ACTIVITY_NAME[ACTIVITY_CAMERA] = "Camera";
        ACTIVITY_NAME[ACTIVITY_CODEC_GALLERY] = "Gallery";
        ACTIVITY_NAME[ACTIVITY_SETTING] = "Setting frame size";
    }

    //Perferences
    public static final String KEY_FULL_FRAME_RESOLUTION = "res";
    public static final String KEY_YUV_SHOT_CAPTURE_COUNT = "shotcount";
    public static final String KEY_SHUTTER_ACTION = "shutteraction";
    public static final String KEY_CLOUD_SHOT_TYPE = "cs_type";
    public static final String KEY_CLOUD_SHOT_PROCESSOR = "cs_processor";
    public static final String KEY_SHOT_MODE_HDR = "hdr";
    public static final String KEY_CLOUD_SHOT = "cs";
    public static final String KEY_USE_HARDWARE_BUFFER = "cs_hw_buffer";


    //Intent
    public static final String KEY_ACTIVITY_CODE = "ac";
    public static final String KEY_YUV_DIR_NAME = "yuv_dir";

    //Intent
    public static final String KEY_VIDEO_PATH = "v_path";

    //Path
    public static final String CCVIDEO_PATH = Environment.getExternalStorageDirectory() + "/CCVideo/";
    public static final String CC4k = Environment.getExternalStorageDirectory() + "/CCVideo/4k.mp4";
    public static final String CC3264 = Environment.getExternalStorageDirectory() + "/CCVideo/4k_3264x1836.mp4";
    public static final String CC2880 = Environment.getExternalStorageDirectory() + "/CCVideo/4k_2880x1620.mp4";
    public static final String CC2496 = Environment.getExternalStorageDirectory() + "/CCVideo/4k_2496x1404.mp4";
    public static final String CCFHD = Environment.getExternalStorageDirectory() + "/CCVideo/Peru 8K HDR 60FPS (FUHD).mp4";
    public static final String TEMP_VIDEO_PATH = Environment.getExternalStorageDirectory() + "/CCVideo/temp/";

    public static final String CCIMAGE_PATH = Environment.getExternalStorageDirectory() + "/CCImage/";
    public static final String JPG_DIR = "Jpg";
    public static final String YUV_DIR = "Yuv";
    public static final String CS_DEBUG_DIR = "cs_debug";
    public static final String LOG_DIR = "Log";
    public static final String PREVIEW_DIR = "Preview";
    public static final String PREVIEW_FULL_DIR = "FullFrame";

    public static final String JPG_PATH = CCUtils.getPath(JPG_DIR);
    public static final String YUV_PATH = CCUtils.getPath(YUV_DIR);
    public static final String LOG_PATH = CCUtils.getPath(LOG_DIR);
    public static final String VIDEO_PATH = CCUtils.getPath(LOG_DIR);
    public static final String CS_DEBUG_PATH = CCUtils.getPath(CS_DEBUG_DIR);

    //Image
    public static final int THUMBNAIL_RATIO = 8;
    public static final int JPEG_QUALITY = 95;

    //Camera
    public static final int MAX_YUV_SHOT_FRAME_COUNT = 10;
    public static final Size DEFAULT_PREVIEW_SIZE = new Size(1920, 1080);
    public static final Size OBJECT_DETECTION_PREVIEW_SIZE = new Size(640, 480);

    public static enum InferenceInputType {
        INFERENCE_JPG(1) {
            @Override
            public String toString() {
                return "INFERENCE_JPG";
            }
        },
        INFERENCE_VIDEO(2) {
            @Override
            public String toString() {
                return "INFERENCE_VIDEO";
            }
        };

        int mId;
        private InferenceInputType(int id) { mId = id; }
        public int getID(){return mId;}
        public boolean compare(int id){return mId == id;}
        public static InferenceInputType getValue(int id)
        {
            InferenceInputType[] inputTypes = InferenceInputType.values();
            for(int i = 0; i < inputTypes.length; i++) {
                if(inputTypes[i].compare(id))
                    return inputTypes[i];
            }
            return INFERENCE_JPG;
        }
    }



    public static enum ShutterAction {
        JPG_SHOT(1) {
            @Override
            public String toString() {
                return "JPG_SHOT";
            }
        },
        YUV_SHOT(2) {
            @Override
            public String toString() {
                return "YUV_SHOT";
            }
        },
        PREVIEW_DUMP(3) {
            @Override
            public String toString() {
                return "PREVIEW_DUMP";
            }
        },
        FULL_FRAME_DUMP(4) {
            @Override
            public String toString() {
                return "FULL_FRAME_DUMP";
            }
        };

        int mId;
        private ShutterAction(int id) { mId = id; }
        public int getID(){return mId;}
        public boolean compare(int id){return mId == id;}
        public static ShutterAction getValue(int id)
        {
            ShutterAction[] shutterActions = ShutterAction.values();
            for(int i = 0; i < shutterActions.length; i++) {
                if(shutterActions[i].compare(id))
                    return shutterActions[i];
            }
            return JPG_SHOT;
        }
    }

}
