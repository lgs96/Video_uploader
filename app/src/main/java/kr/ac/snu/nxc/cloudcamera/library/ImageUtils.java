package kr.ac.snu.nxc.cloudcamera.library;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import kr.ac.snu.nxc.cloudcamera.util.CCConstants;
import kr.ac.snu.nxc.cloudcamera.util.CCImage;
import kr.ac.snu.nxc.cloudcamera.util.CCLog;

import static kr.ac.snu.nxc.cloudcamera.util.CCConstants.MAX_YUV_SHOT_FRAME_COUNT;

public class ImageUtils {
    public static final String TAG = "ImageUtils";
    public static final int YUV_CHANNEL = 0;
    public static final int Y_CHANNEL = 1;
    public static final int UV_CHANNEL = 2;

    public static final int MAX_IMAGE_BUFFERS = MAX_YUV_SHOT_FRAME_COUNT;

    /*
        sum(x != y) / size
     */
    public static final int DIFF_METHOD_SIMPLE = 0;

    /*
        sum(root((x - y)^2)) / size
     */
    public static final int DIFF_METHOD_MSE = 1;


    public static final int DIFF_METHOD_PSNR = 2;

    static {
        System.loadLibrary("ImageUtils");
    }

    public static CCImage convertCCImage(byte[] jpegBytes, int width, int height, int stride) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.outWidth = width;
        options.outHeight = height;
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        Bitmap bitmap = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.length, options);
        CCLog.d(TAG, "bitmap info : " + bitmap.getWidth() + " x " + bitmap.getHeight());
        CCImage ccImage = new CCImage(width, height, stride, 0);
        convertYuvNative(ccImage.mYuvBuffer, bitmap, stride, width, height);
        return ccImage;
    }

    public static Bitmap convertBitmap(CCImage ccImage) {
        Bitmap bitmap = Bitmap.createBitmap(ccImage.mWidth, ccImage.mHeight, Bitmap.Config.ARGB_8888);
        convertBitmapNative(bitmap, ccImage.mYuvBuffer, ccImage.mStride, ccImage.mWidth, ccImage.mHeight);
        return bitmap;
    }

    public static Bitmap convertDownScaledBitmap(CCImage ccImage, int downSampleRatio) {
        int resizeW = ccImage.mWidth / downSampleRatio;
        int resizeH = ccImage.mHeight / downSampleRatio;
        Bitmap bitmap = Bitmap.createBitmap(resizeW, resizeH, Bitmap.Config.ARGB_8888);
        convertResizedBitmapNative(bitmap, ccImage.mYuvBuffer, ccImage.mStride, ccImage.mWidth, ccImage.mHeight, resizeW, resizeH);
        return bitmap;
    }

    public static CCImage convertCenterCropCCImage(CCImage ccImage, int downSampleRatio, boolean sameSize) {
        int cropW = ccImage.mWidth / downSampleRatio;
        int cropH = ccImage.mHeight / downSampleRatio;

        cropW += cropW % 2;
        cropH += cropH % 2;

        int startX = (ccImage.mWidth / 2) - (cropW / 2);
        int startY = (ccImage.mHeight / 2) - (cropH / 2);

        startX -= startX % 2;
        startY -= startY % 2;

        CCImage cropCCImage = new CCImage(cropW, cropH, cropW, ccImage.mTimestamp);;

        convertCenterCropCCImage(ccImage.mYuvBuffer, cropCCImage.mYuvBuffer,
                startX, startY, cropW, cropH, ccImage.mWidth, ccImage.mHeight, ccImage.mStride);

        if (sameSize) {
            CCLog.d(TAG, "CropAndResize");
            Bitmap cropBitmap = convertBitmap(cropCCImage);
            Bitmap resizeBitmap = Bitmap.createScaledBitmap(cropBitmap, ccImage.mWidth, ccImage.mHeight, false);
            CCImage resizeCCImage = new CCImage(ccImage.mWidth, ccImage.mHeight, ccImage.mWidth, ccImage.mTimestamp);
            convertYuvNative(resizeCCImage.mYuvBuffer, resizeBitmap, resizeCCImage.mWidth, resizeCCImage.mWidth, resizeCCImage.mHeight);
            return resizeCCImage;
        } else {
            return cropCCImage;
        }
    }

    public static CCImage downScaleCCImage(CCImage ccImage, float downSampleRatio) {
        int resizeW = (int)((float)ccImage.mWidth * downSampleRatio);
        int resizeH = (int)((float)ccImage.mHeight * downSampleRatio);
        CCImage downScaledImage = new CCImage(resizeW, resizeH, resizeW, ccImage.mTimestamp);
        downScaleCCImage(downScaledImage.mYuvBuffer, ccImage.mYuvBuffer,
                ccImage.mStride, ccImage.mWidth, ccImage.mHeight, resizeW, resizeH);
        return downScaledImage;
    }


    public static void swapUV(CCImage ccImage) {
        swapUV(ccImage.mYuvBuffer, ccImage.mStride, ccImage.mWidth, ccImage.mHeight);
    }

    public static float computePixelDiff(CCImage ccImageX, CCImage ccImageY, int diffChannel, int diffMethod) {
        if (ccImageX.mWidth != ccImageY.mWidth
                || ccImageX.mHeight != ccImageX.mHeight
                || ccImageX.mStride != ccImageY.mStride) {
            return -1;
        }
        return computePixelDiff(ccImageX.mYuvBuffer, ccImageY.mYuvBuffer,
                ccImageX.mStride, ccImageX.mWidth, ccImageX.mHeight, diffChannel, diffMethod);
    }

    public static ByteBuffer convertByteToFloatBuffer(CCImage image, int channel, boolean normalize) {
        ByteBuffer byteBuffer = image.getYuvBuffer();
        ByteBuffer floatByteBuffer = null;
        if (channel == Y_CHANNEL) {
            int imageSize = image.getWidth() * image.getHeight();
            floatByteBuffer = ByteBuffer.allocateDirect(imageSize * 4)
                                    .order(ByteOrder.nativeOrder());

            convertByteToFloatBuffer(byteBuffer, floatByteBuffer, image.getWidth(), image.getHeight(), image.getStride(), normalize);
        } else if (channel == YUV_CHANNEL) {
            int width = image.getWidth();
            int height = image.getHeight() + image.getHeight() / 2;
            int stride = image.getStride();
            int imageSize = width * height;
            floatByteBuffer = ByteBuffer.allocateDirect(imageSize * 4)
                                    .order(ByteOrder.nativeOrder());

            convertByteToFloatBuffer(byteBuffer, floatByteBuffer, width, height, stride, normalize);
        }
        return floatByteBuffer;
    }

    public static ByteBuffer mergeImages(CCImage[] images, int channel, boolean normalize) {
        int imageCount = images.length;
        ByteBuffer[] byteBuffers = new ByteBuffer[imageCount];
        ByteBuffer mergeBuffer = null;

        for (int i = 0; i < imageCount; i++) {
            byteBuffers[i] = images[i].getYuvBuffer();
        }

        if (channel == Y_CHANNEL) {
            int imageSize = images[0].getWidth() * images[0].getHeight();
            mergeBuffer = ByteBuffer.allocateDirect(imageCount * imageSize)
                    .order(ByteOrder.nativeOrder());
            CCLog.d(TAG, "merge buffer capacity : " + mergeBuffer.capacity());

            nativeMergeImages(byteBuffers, mergeBuffer, images[0].getWidth(), images[0].getHeight(), images[0].getStride(), normalize);
        } else if (channel == YUV_CHANNEL) {
            int width = images[0].getWidth();
            int height = images[0].getHeight() + images[0].getHeight() / 2;
            int stride = images[0].getStride();
            int imageSize = width * height;
            mergeBuffer = ByteBuffer.allocateDirect(imageCount * imageSize)
                    .order(ByteOrder.nativeOrder());

            nativeMergeImages(byteBuffers, mergeBuffer, width, height, stride, normalize);
        }
        return mergeBuffer;
    }

    public static FloatBuffer convertByteToFloatBufferArray(CCImage[] images, int channel, boolean normalize) {
        int imageCount = images.length;
        ByteBuffer[] byteBuffers = new ByteBuffer[imageCount];
        FloatBuffer floatBuffer = null;

        for (int i = 0; i < imageCount; i++) {
            byteBuffers[i] = images[i].getYuvBuffer();
        }

        if (channel == Y_CHANNEL) {
            int imageSize = images[0].getWidth() * images[0].getHeight();
            floatBuffer = ByteBuffer.allocateDirect(imageCount * imageSize * 4)
                                    .order(ByteOrder.nativeOrder())
                                    .asFloatBuffer();
            CCLog.d(TAG, "float buffer capacity : " + floatBuffer.capacity());

            convertByteToFloatBufferArray(byteBuffers, floatBuffer, images[0].getWidth(), images[0].getHeight(), images[0].getStride(), normalize);
        } else if (channel == YUV_CHANNEL) {
            int width = images[0].getWidth();
            int height = images[0].getHeight() + images[0].getHeight() / 2;
            int stride = images[0].getStride();
            int imageSize = width * height;
            floatBuffer = ByteBuffer.allocateDirect(imageCount * imageSize * 4)
                                    .order(ByteOrder.nativeOrder())
                                    .asFloatBuffer();

            convertByteToFloatBufferArray(byteBuffers, floatBuffer, width, height, stride, normalize);
        }
        return floatBuffer;
    }

    public static void convertFloatToByteBuffer(CCImage toCCImage, ByteBuffer fromFloatByteBuffer, int channel, boolean denormalize) {
        ByteBuffer toByteBuffer = toCCImage.getYuvBuffer();

        convertFloatToByteBuffer(toByteBuffer, fromFloatByteBuffer,
                toCCImage.getWidth(), toCCImage.getHeight(), toCCImage.getStride(), channel, denormalize);
    }

    public static void convertFloatBufferToBitmap(Bitmap toBitmap, ByteBuffer fromFloatByteBuffer, boolean denormalize) {
        convertFloatToBitmap(toBitmap, fromFloatByteBuffer,
                toBitmap.getWidth(), toBitmap.getHeight(), denormalize);
    }

    public static void updateCCImage(CCImage toCCImage, ByteBuffer fromBytebuffer, int channel, boolean denormalize) {
        updateCCImage(toCCImage.mYuvBuffer, fromBytebuffer,
                toCCImage.mWidth, toCCImage.mHeight, toCCImage.mStride, fromBytebuffer.capacity(), channel, denormalize);
    }

    public static ByteBuffer quantizeBuffer(ByteBuffer intputBuffer, int width, int height, float scale, int zero) {
        ByteBuffer outputBuffer = ByteBuffer.allocateDirect(intputBuffer.capacity());
        nativeQuantizeBuffer(intputBuffer, outputBuffer, width, height, scale, zero);
        return outputBuffer;
    }


    public static native void convertBitmapNative(Bitmap outBitmap,
                                                  ByteBuffer inYuv420sp, int rowStride,
                                                  int width, int height);

    public static native void convertYuvNative(ByteBuffer outYuv420sp,
                                                  Bitmap inBitmap, int rowStride,
                                                  int width, int height);

    public static native void convertResizedBitmapNative(Bitmap outBitmap,
                                                  ByteBuffer inYuv420sp, int rowStride,
                                                  int orgInputW, int orgInputH,
                                                  int resizedInputW, int resizedInputH);

    public static native void downScaleCCImage(ByteBuffer outYuv420sp,
                                                         ByteBuffer inYuv420sp, int rowStride,
                                                         int orgInputW, int orgInputH,
                                                         int resizedInputW, int resizedInputH);

    public static native void swapUV(ByteBuffer yuv420sp, int rowStride, int width, int height);

    public static native float computePixelDiff(ByteBuffer yuv420spX, ByteBuffer yuv420spY,
                                              int rowStride, int orgInputW, int orgInputH,
                                              int diffChannel, int diffMethod);

    public static native void convertCenterCropCCImage(ByteBuffer inImage, ByteBuffer outImage,
                                                         int startX, int startY,
                                                         int cropW, int cropH,
                                                         int inWidth, int inHeight, int inStride);

    public static native void convertByteToFloatBuffer(ByteBuffer byteBuffer, ByteBuffer floatBuffer, int width, int height, int stride, boolean normalize);
    public static native void convertFloatToByteBuffer(ByteBuffer toByteBuffer, ByteBuffer fromFloatByteBuffer, int width, int height, int stride, int channel, boolean denormalize);

    public static native void convertByteToFloatBufferArray(ByteBuffer[] byteBuffers, FloatBuffer floatBuffer, int width, int height, int stride, boolean normalize);

    public static native void nativeMergeImages(ByteBuffer[] byteBuffers, ByteBuffer mergeImage, int width, int height, int stride, boolean normalize);

    public static native void convertFloatToBitmap(Bitmap toBitmap, ByteBuffer fromFloatByteBuffer,
                         int width, int height, boolean denormalize);

    public static native void updateCCImage(ByteBuffer toByteBuffer, ByteBuffer fromByteBuffer,
                                                   int width, int height, int stride, int fromBufferSize, int channel, boolean denormalize);

    public static native void nativeQuantizeBuffer(ByteBuffer intputBuffer, ByteBuffer outputBuffer, int width, int height, float scale, int zero);

    public static native void nativeGetLatentBitmap(Bitmap bitmap, int index, int imageWidth, int imageHeight,
                                                    int width, int height, int channel,
                                                    int dType, ByteBuffer buffer, float scale, int zero);

    public static native void nativeSubtractBuffer(ByteBuffer outputBuffer, ByteBuffer baseBuffer, ByteBuffer subBuffer, int width, int height, int typeId);
    public static native void nativeMergeBuffer(ByteBuffer mergeBuffer, ByteBuffer[] buffers, int width, int height, int channel, int typeId);
    public static native void nativeMergeBufferChannel(ByteBuffer mergeBuffer, ByteBuffer buffer, int start, int end, int width, int height, int channel, int typeId);
}
