package kr.ac.snu.nxc.cloudcamera.device;

import java.nio.ByteBuffer;

import kr.ac.snu.nxc.cloudcamera.util.CCImage;

public interface PictureCallbackInterface {
    public void onCaptureCallback(byte[] jpegBytes, int orientation, boolean needExifUpdate);
    public void onPreviewCallback(CCImage image);
    public void onCaptureFail();
}
