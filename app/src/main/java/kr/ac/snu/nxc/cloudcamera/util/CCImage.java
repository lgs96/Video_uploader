package kr.ac.snu.nxc.cloudcamera.util;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.media.ExifInterface;
import android.media.Image;
import android.os.SystemClock;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Base64;
import java.util.zip.Deflater;

import kr.ac.snu.nxc.cloudcamera.library.ImageUtils;

public class CCImage {
    private static final String TAG = "CCImage";
    public int mWidth;
    public int mHeight;
    public int mStride;

    public long mTimestamp;
    public ByteBuffer mYuvBuffer = null;

    public int mBufferSize;

    public CCImage(int width, int height, int stride, long timestamp) {
        mWidth = width;
        mHeight = height;
        mStride = stride;

        mBufferSize = mHeight * mStride + (mHeight/2) * mStride;
        mTimestamp = timestamp;
        mYuvBuffer = ByteBuffer.allocateDirect(mBufferSize);
        CCLog.d(TAG, "Create new CCImage " + mWidth + " x " + mHeight + " st : " + mStride + " size : " + mBufferSize);
    }

    public CCImage(CCImage image) {
        mWidth = image.mWidth;
        mHeight = image.mHeight;
        mStride = image.mStride;

        mBufferSize = mHeight * mStride + (mHeight/2) * mStride;
        mTimestamp = image.mTimestamp;
//        mYuvBuffer = ByteBuffer.allocateDirect(mBufferSize);
        mYuvBuffer = image.copy(mBufferSize);

    }

    public CCImage(Image image) {
        try {
            int strideY = image.getPlanes()[0].getRowStride();
            int strideUV = image.getPlanes()[2].getRowStride();

            mWidth = image.getWidth();
            mHeight = image.getHeight();
            mStride = strideY;

//            setFitSize();

            if (strideY != strideUV) {
                CCLog.e(TAG, "Stride Error");
            }

            long start = System.currentTimeMillis();

            mBufferSize = mHeight * mStride + (mHeight / 2) * mStride;
            mTimestamp = image.getTimestamp();
            mYuvBuffer = ByteBuffer.allocateDirect(mBufferSize);

//            copyImageToYuvFitBuffer(image, mYuvBuffer);
            copyImageToYuvBuffer(image, mYuvBuffer);

            long end = System.currentTimeMillis();
            CCLog.d(TAG, "Create new CCImage " + mWidth + " x " + mHeight + " st : " + mStride
                    + " timestamp : " + mTimestamp + " creation time : " + (end - start)
                    + " size : " + mBufferSize);
        } catch (Exception e) {
            CCLog.e(TAG, "Create CCImage Buffer Error : " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void setFitSize() {
        for (int height = mHeight; height > 0; height--) {
            if (height % 2 != 0) {
                continue;
            }

            int yuvHeight = height + (height / 2);

            if (height % CCConstants.DONW_RATIO == 0 && yuvHeight % CCConstants.DONW_RATIO == 0) {
                CCLog.e(TAG, "Height " + mHeight + " -> : " + height);
                mHeight = height;
                break;
            }
        }
    }

    public int getWidth() {
        return mWidth;
    }

    public int getHeight() {
        return mHeight;
    }

    public int getStride() {
        return mStride;
    }

    public long getTimestamp() {
        return mTimestamp;
    }

    public int getBufferSize() {
        return mBufferSize;
    }

    public ByteBuffer getYuvBuffer() {
        mYuvBuffer.clear();
        return mYuvBuffer;
    }

    public void close() {
        mYuvBuffer = null;
    }

    public boolean update(ByteBuffer byteBuffer) {
        mYuvBuffer.clear();
        byteBuffer.clear();
        if (byteBuffer.capacity() % mStride == 0 && byteBuffer.capacity() < mBufferSize) {
            CCLog.d(TAG, "Update Image");
            mYuvBuffer.put(byteBuffer);
            return true;
        }
        return false;
    }

    public void update(Image image) {
        mYuvBuffer.clear();
        mTimestamp = image.getTimestamp();
        copyImageToYuvBuffer(image, mYuvBuffer);
        mYuvBuffer.clear();
    }

    public boolean update(byte[] srcBuffer) {
        if (mBufferSize < srcBuffer.length) {
            CCLog.e(TAG, "update size mismatch");
            return false;
        } else {
            try {
                mYuvBuffer.put(srcBuffer, 0, srcBuffer.length);
                return true;
            } catch (Exception e) {
                e.printStackTrace();
                return false;
            }
        }
    }

    private void copyImageToYuvBuffer(Image srcImage, ByteBuffer dstBuffer) {
        ByteBuffer srcY = srcImage.getPlanes()[0].getBuffer();
        ByteBuffer srcU = srcImage.getPlanes()[1].getBuffer();
        ByteBuffer srcV = srcImage.getPlanes()[2].getBuffer();
        srcY.clear();
        srcU.clear();
        srcV.clear();

        //long uvSize = (mStride * mHeight / 2);
        //long srcUVSize = srcU.capacity();

        //CCLog.d(TAG, "Size Y : " + srcY.capacity() + " U : " + srcU.capacity() + " V : " + srcU.capacity()
        //    + " Alloc : "+ (mStride * mHeight / 2) + " Diff : " + (uvSize-srcUVSize));


        //Copy Y Plane size = (mStride * mHeight) - (mStride - mWidth)
        srcY.limit(mStride * mHeight);
        dstBuffer.put(srcY);

        //Move UV Plane
        dstBuffer.position(mStride * mHeight);

        //VU_array
        //srcV[0] = VU[0] size = (mStride * mHeight / 2) - (mStride - mWidth) - 1
        //srcU[0] = VU[1] size = (mStride * mHeight / 2) - (mStride - mWidth) - 1

        //Copy V firat Data
        dstBuffer.put(srcV.get());

        srcU.limit(mStride * mHeight / 2 - 1);
        //Copy VU
        dstBuffer.put(srcU);
    }

    private void copyYuvBufferToImage(ByteBuffer srcBuffer, Image dstImage) {
        ByteBuffer dstY = dstImage.getPlanes()[0].getBuffer();
        ByteBuffer dstU = dstImage.getPlanes()[1].getBuffer();
        ByteBuffer dstV = dstImage.getPlanes()[2].getBuffer();
        dstY.clear();
        dstU.clear();
        dstV.clear();

        int stride = dstImage.getPlanes()[0].getRowStride();

        //Y size = (mStride * mHeight) - (mStride - mWidth)
        srcBuffer.limit(dstY.capacity());
        dstY.put(srcBuffer);

        //Move UV Plane
        srcBuffer.limit(mStride * mHeight + dstU.capacity() + 1);
        srcBuffer.position(mStride * mHeight);

        //VU_array
        //dstV[0] = VU[0] size = (mStride * mHeight / 2) - (mStride - mWidth) - 1
        //dstU[0] = VU[1] size = (mStride * mHeight / 2) - (mStride - mWidth) - 1

        //V first data
        dstV.put(srcBuffer.get());

        //Copy VU
        dstU.put(srcBuffer);
    }

    public ByteBuffer getPlaneY() {
        ByteBuffer planeY = ByteBuffer.allocateDirect(mStride * mHeight);
        mYuvBuffer.clear();
        mYuvBuffer.limit(mStride * mHeight);
        planeY.put(mYuvBuffer);
        mYuvBuffer.clear();
        return planeY;
    }

    public ByteBuffer copy() {
        try {
            long start = SystemClock.uptimeMillis();

            ByteBuffer srcBuffer = mYuvBuffer;
            ByteBuffer dstBuffer = ByteBuffer.allocateDirect(srcBuffer.capacity());

            byte[] dstByte = dstBuffer.array();

            //It makes unexpected line at top of image
            //dstBuffer.put(srcBuffer);

            srcBuffer.get(dstByte, 0, srcBuffer.capacity());

            long end = SystemClock.uptimeMillis();

            CCLog.d(TAG, "Copy CCImage " + (end - start) + " ms");
            return dstBuffer;
        } catch (Exception e) {
            CCLog.e(TAG, "Copy Error");
        }
        return null;
    }

    public ByteBuffer copyByNative() {
        try {
            long start = SystemClock.uptimeMillis();

            ByteBuffer srcBuffer = mYuvBuffer;
            ByteBuffer dstBuffer = ByteBuffer.allocateDirect(srcBuffer.capacity());

            dstBuffer.put(srcBuffer);

            long end = SystemClock.uptimeMillis();

            CCLog.d(TAG, "CopyByNative CCImage " + (end - start) + " ms");
            return dstBuffer;
        } catch (Exception e) {
            CCLog.e(TAG, "CopyByNative Error");
        }
        return null;
    }

    public ByteBuffer copy(int dstLength) {
        try {
            if ((mStride * mHeight * 3 / 2) < dstLength) {
                CCLog.e(TAG, "Invalid size");
                return null;
            }

            long start = SystemClock.uptimeMillis();

            ByteBuffer srcBuffer = mYuvBuffer;
            ByteBuffer dstBuffer = ByteBuffer.allocateDirect(dstLength);

            byte[] dstByte = dstBuffer.array();

            //It makes unexpected line at top of image
            //dstBuffer.put(srcBuffer);

            srcBuffer.get(dstByte, 0, dstLength);

            long end = SystemClock.uptimeMillis();

            CCLog.d(TAG, "Copy CCImage " + (end - start) + " ms");
            return dstBuffer;
        } catch (Exception e) {
            CCLog.e(TAG, "Copy Error");
        }
        return null;
    }

    public byte[] copyByteArray() {
        try {
            long start = System.currentTimeMillis();

            ByteBuffer srcBuffer = mYuvBuffer;
            byte[] dstByteArray = new byte[srcBuffer.capacity()];

            srcBuffer.get(dstByteArray, 0, srcBuffer.capacity());

            long end = System.currentTimeMillis();

            CCLog.d(TAG, "Copy CCImage ByteArray " + (end - start) + " ms");
            return dstByteArray;
        } catch (Exception e) {
            CCLog.e(TAG, "Copy Error");
        }
        return null;
    }

    public byte[] compressPNG() {
        byte[] jpegByteArray = compressJpeg(CCConstants.JPEG_QUALITY);
        Bitmap bitmap = BitmapFactory.decodeByteArray(jpegByteArray, 0, jpegByteArray.length);

        long start = SystemClock.uptimeMillis();
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream);

        byte[] pngBytes = outputStream.toByteArray();
        long end = SystemClock.uptimeMillis();

        CCLog.d(TAG, "YUV " + mBufferSize + " bytes to PNG " + pngBytes.length + " bytes");
        CCLog.d(TAG, "compress time : " + (end - start) + " ms");

        return pngBytes;
    }


    public byte[] compressDeflater(int level) {
        long start = SystemClock.uptimeMillis();;

        byte[] result = null;

        try {
            Deflater deflater = new Deflater();
            deflater.setLevel(level);
            deflater.setInput(mYuvBuffer.array());
            deflater.finish();

            ByteArrayOutputStream bao = new ByteArrayOutputStream(mYuvBuffer.capacity());
            byte[] buf = new byte[1024];

            while (!deflater.finished()) {
                int compByte = deflater.deflate(buf);
                bao.write(buf, 0, compByte);
            }

            bao.close();
            deflater.end();

            result = bao.toByteArray();
            long end = SystemClock.uptimeMillis();
            CCLog.d(TAG, "YUV " + mBufferSize + " bytes to Deflater[" + level + "] " + result.length + " bytes");
            CCLog.d(TAG, "compress time : " + (end - start) + " ms");
            return result;
        } catch (Exception e) {
            return null;
        }
    }

    public byte[] compressJpeg(int quality) {
        long start = SystemClock.uptimeMillis();
        byte yuvBuffer[] = mYuvBuffer.array();

        int strides[] = new int[3];
        strides[0] = mStride;
        strides[1] = mStride;
        strides[2] = mStride;

        YuvImage yuvImage = new YuvImage(mYuvBuffer.array(),
                ImageFormat.NV21,
                mWidth,
                mHeight,
                strides);

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        yuvImage.compressToJpeg(
                new Rect(0, 0,
                        mWidth,
                        mHeight),
                quality,
                outputStream);
        byte[] jpegBytes = outputStream.toByteArray();
        long end = SystemClock.uptimeMillis();

        try {
            outputStream.close();
        } catch(Exception e) {

        }

        CCLog.d(TAG, "YUV " + mBufferSize + " bytes to JPEG " + jpegBytes.length + " bytes");
        CCLog.d(TAG, "compress time : " + (end - start) + " ms");
        return jpegBytes;
    }

    public void dump(String dirPath) {
        long start = System.currentTimeMillis();
        String fileName = "Yuv_" + mWidth + "x" + mHeight + "_" + mStride + "_" + mTimestamp + ".nv21";

        FileChannel fc = null;
        ByteBuffer buffer = null;

        try {
            fc = new FileOutputStream(dirPath + fileName).getChannel();

            buffer = mYuvBuffer;

            if (buffer != null) {
                buffer.rewind();

                fc.write(buffer);
                fc.close();
                CCLog.e(TAG, "DUMP"
                        + "_" + mTimestamp + "_" + mWidth + "x" + mHeight + "_" + mStride + ".dump " + (System.currentTimeMillis() - start) + "ms");
            } else {
                CCLog.e(TAG, "DUMP_FAIL "
                        + "_" + mTimestamp + "_" + mWidth + "x" + mHeight + "_" + mStride + ".dump");
            }
        } catch(IOException e) {
            e.printStackTrace();
        } finally {
            buffer = null;
            try {
                if (fc != null) {
                    fc.close();
                }
            } catch(IOException e) {
                e.printStackTrace();
            }
        }
    }


    public static void dump(Image image, String dirPath) {
        long start = System.currentTimeMillis();
        int width = image.getWidth();
        int height = image.getHeight();
        int stride = image.getPlanes()[0].getRowStride();
        long timestamp = image.getTimestamp();

        String fileName = "Yuv_" + width + "x" + height + "_" + stride + "_" + timestamp + ".nv21";

        FileChannel fc = null;
        ByteBuffer buffer = null;
        ByteBuffer diffBufferY = null;
        ByteBuffer diffBufferUV = null;

        try {
            fc = new FileOutputStream(dirPath + fileName).getChannel();

            ByteBuffer srcY = image.getPlanes()[0].getBuffer();
            ByteBuffer srcU = image.getPlanes()[1].getBuffer();
            ByteBuffer srcV = image.getPlanes()[2].getBuffer();
            srcY.clear();
            srcU.clear();
            srcV.clear();

            //ByteBuffer

            int diff = (stride * height) - srcY.capacity();
            if (diff != 0) {
                CCLog.d(TAG, "diff byte count : " + diff);
                diffBufferY = ByteBuffer.allocateDirect(diff);
                diffBufferY.clear();
            }
            diff = (stride * height/2) - (srcU.capacity() + 1);
            if (diff != 0) {
                CCLog.d(TAG, "uv diff byte count : " + diff);
                diffBufferUV = ByteBuffer.allocateDirect(diff);
                diffBufferUV.clear();
            }

            ByteBuffer vFirstBuffer = ByteBuffer.allocateDirect(1);
            vFirstBuffer.put(srcV.get());

            vFirstBuffer.clear();

            fc.write(srcY);
            if(diffBufferY != null) {
                fc.write(diffBufferY);
            }
            fc.write(vFirstBuffer);
            fc.write(srcU);

            if(diffBufferUV != null) {
                fc.write(diffBufferUV);
            }
            fc.close();
            CCLog.d(TAG, "DUMP"
                    + "_" + timestamp + "_" + width + "x" + height + "_" + stride + ".dump " + (System.currentTimeMillis() - start) + "ms");

        } catch(IOException e) {
            CCLog.e(TAG, "DUMP_FAIL");
            e.printStackTrace();
        } finally {
            buffer = null;
            try {
                if (fc != null) {
                    fc.close();
                }
            } catch(IOException e) {
                e.printStackTrace();
            }
        }
    }
}
