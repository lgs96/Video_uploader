package kr.ac.snu.nxc.cloudcamera;

import kr.ac.snu.nxc.cloudcamera.util.CCImage;

public interface InferenceCallback {
    public void onEncodingFinish(CCImage decodeImage, CCImage inferenceImage, double encodeTime);
    public void onSendFinish(double send_start, double sendTime, int encodedSize);
    public void onReceiveFinish(double alpha, double RecvTime, float TransTime);

}
