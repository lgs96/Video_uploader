package kr.ac.snu.nxc.cloudcamera;

import android.app.Application;

import java.io.File;

import kr.ac.snu.nxc.cloudcamera.util.CCUtils;

import static kr.ac.snu.nxc.cloudcamera.util.CCConstants.CCVIDEO_PATH;
import static kr.ac.snu.nxc.cloudcamera.util.CCConstants.TEMP_VIDEO_PATH;

public class CloudCameraApplication extends Application {
    public CloudCameraApplication() {
        super();
    }

    @Override
    public void onCreate() {
        CCUtils.init(getApplicationContext());
        File videoDir = new File(CCVIDEO_PATH);
        if (!videoDir.exists()) {
            videoDir.mkdir();
        }

        videoDir = new File(TEMP_VIDEO_PATH);
        if (!videoDir.exists()) {
            videoDir.mkdir();
        }
    }
}
