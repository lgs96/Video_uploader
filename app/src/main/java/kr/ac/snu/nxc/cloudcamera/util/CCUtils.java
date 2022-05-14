package kr.ac.snu.nxc.cloudcamera.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.graphics.YuvImage;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.ExifInterface;
import android.media.ThumbnailUtils;
import android.os.Environment;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.Size;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.zip.Deflater;
import java.util.zip.GZIPOutputStream;

public class CCUtils {
    private static final String TAG = "CCUtils";


    //Normal = 0, front = 1, wide = 2
    private static final String CAMERA_ID = "0";

    public static void init(Context context) {
        initPreference(context);
    }

    //File Path
    public static String getDateString() {
        Date date = new Date();
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy_MM_dd_HH_mm_ss");
        return dateFormat.format(date);
    }

    public static String getDumpPath(String subFolder1, String subFolder2) {
        String dirPath = CCConstants.CCIMAGE_PATH + subFolder1 + "/" + subFolder2 + "/";

        File directory = new File(dirPath);
        if (!directory.exists()) {
            directory.mkdirs();
        }
        return dirPath;
    }

    public static String getDumpPath(String subFolder) {
        String dirPath = CCConstants.CCIMAGE_PATH + subFolder + "/" + getDateString() + "/";

        File directory = new File(dirPath);
        if (!directory.exists()) {
            directory.mkdirs();
        }
        return dirPath;
    }

    public static String getPath(String subFolder) {
        String dirPath = CCConstants.CCIMAGE_PATH + subFolder + "/";

        File directory = new File(dirPath);
        if (!directory.exists()) {
            directory.mkdirs();
        }
        return dirPath;
    }

    public static String getNameWithoutExtension(String path) {
        File viewFile = new File(path);
        String fileName = viewFile.getName();
        int pos = fileName.lastIndexOf(".");
        return fileName.substring(0, pos);
    }

    public static String getBytesToMB(long size) {
        float fSize = (float)size / 1024.0f / 1024.0f;
        return String.format("%.2f MB", fSize);
    }

    public static int getDpToPx(Context context, int dp) {
        float density = context.getResources().getDisplayMetrics().density;
        return Math.round((float) dp * density);
    }


    public static String getStr(float result) {
        return String.format("%.4f", result);
    }

    //Bitmap
    public static Bitmap readJpeg(String path, int sampleSize, boolean enableRotate) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sampleSize;
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        options.inJustDecodeBounds = false;

        Bitmap bitmap = BitmapFactory.decodeFile(path, options);
        if (enableRotate) {
            return rotateBitmap(bitmap, readExifOrientation(openExif(path)));
        } else {
            return bitmap;
        }
    }

    public static Bitmap readJpeg(byte[] jpegBytes, int sampleSize, boolean enableRotate) {
        return readJpeg(jpegBytes, jpegBytes.length, sampleSize, enableRotate);
    }

    public static Bitmap readJpeg(byte[] jpegBytes, int jpgSize, int sampleSize, boolean enableRotate) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sampleSize;
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        options.inJustDecodeBounds = false;

        Bitmap bitmap = BitmapFactory.decodeByteArray(jpegBytes, 0, jpgSize, options);
        if (enableRotate) {
            int orientation = readExifOrientation(openExif(jpegBytes));
//            CCLog.d(TAG, "orientation : " + orientation);
            return rotateBitmap(bitmap, readExifOrientation(openExif(jpegBytes)));
        } else {
            return bitmap;
        }
    }

    public static byte[] compressJpg(byte[] rawData, int width, int height, int quality) {
        long start = SystemClock.uptimeMillis();

        int strides[] = new int[3];
        strides[0] = width;
        strides[1] = width;
        strides[2] = width;

        YuvImage yuvImage = new YuvImage(rawData,
                ImageFormat.NV21,
                width,
                height,
                strides);

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        yuvImage.compressToJpeg(
                new Rect(0, 0,
                        width,
                        height),
                quality,
                outputStream);
        byte[] jpegBytes = outputStream.toByteArray();
        long time = SystemClock.uptimeMillis() - start;

        CCLog.d(TAG, "Jpg compress time : " + time + " ms size " + jpegBytes.length);
        return jpegBytes;
    }


    public static Bitmap readThumbnail(String path, int width, int height) {
        Bitmap bitmap = ThumbnailUtils.createVideoThumbnail(path, MediaStore.Video.Thumbnails.FULL_SCREEN_KIND);
        return ThumbnailUtils.extractThumbnail(bitmap, width, height);

    }

    public static byte[] compressZlib(byte[] rawData, int size, int options) {
        long start = SystemClock.uptimeMillis();
        Deflater deflater = new Deflater(options);
        deflater.setInput(rawData, 0, size);
        deflater.finish();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buf = ByteBuffer.allocate(4096).array();
        while (!deflater.finished()) {
            int byteCount = deflater.deflate(buf);
            baos.write(buf, 0, byteCount);
        }
        deflater.end();

        byte[] compressedBytes = baos.toByteArray();

        float ratio = ((float)compressedBytes.length /  (float)rawData.length) * 100.0f;
        String percent = String.format("%.2f", ratio);

        long time = SystemClock.uptimeMillis() - start;
        CCLog.d(TAG, "Zlib compress time : " + time + " ms size : " + compressedBytes.length + " - " + percent + " %");
        return compressedBytes;
    }

    public static byte[] compressGzip(byte[] rawData) {
        long start = SystemClock.uptimeMillis();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] compressedBytes = null;
        try {
            GZIPOutputStream gzip = new GZIPOutputStream(out);
            gzip.write(rawData);
            compressedBytes = out.toByteArray();
            gzip.close();
            out.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        long time = SystemClock.uptimeMillis() - start;
        CCLog.d(TAG, "GZip compress time : " + time + " ms size : " + compressedBytes.length);
        return compressedBytes;
    }

    public static byte[] compressJpeg(ByteBuffer buffer, int width, int height, int quality) {
        long start = SystemClock.uptimeMillis();

        int[] strides = {width, width, width};

        YuvImage yuvImage = new YuvImage(buffer.array(),
                ImageFormat.NV21,
                width,
                height,
                strides);

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        yuvImage.compressToJpeg(
                new Rect(0, 0,
                        width,
                        height),
                quality,
                outputStream);
        byte[] jpegBytes = outputStream.toByteArray();

        long time = SystemClock.uptimeMillis() - start;

        CCLog.d(TAG, "YUV " + buffer.capacity() + " bytes to JPEG " + jpegBytes.length + " bytes");
        CCLog.d(TAG, "Jpg compress time : " + time + " ms size : " + jpegBytes.length);
        return jpegBytes;
    }

    public static Bitmap rotateBitmap(Bitmap bitmap, int orientation) {
//        CCLog.d(TAG, "Rotate Bitmap : " + orientation);
        Matrix matrix = new Matrix();
        switch (orientation) {
            case ExifInterface.ORIENTATION_NORMAL:
                return bitmap;
            case ExifInterface.ORIENTATION_FLIP_HORIZONTAL:
                matrix.setScale(-1, 1);
                break;
            case ExifInterface.ORIENTATION_ROTATE_180:
                matrix.setRotate(180);
                break;
            case ExifInterface.ORIENTATION_FLIP_VERTICAL:
                matrix.setRotate(180);
                matrix.postScale(-1, 1);
                break;
            case ExifInterface.ORIENTATION_TRANSPOSE:
                matrix.setRotate(90);
                matrix.postScale(-1, 1);
                break;
            case ExifInterface.ORIENTATION_ROTATE_90:
                matrix.setRotate(90);
                break;
            case ExifInterface.ORIENTATION_TRANSVERSE:
                matrix.setRotate(-90);
                matrix.postScale(-1, 1);
                break;
            case ExifInterface.ORIENTATION_ROTATE_270:
                matrix.setRotate(-90);
                break;
            default:
                return bitmap;
        }
        try {
            Bitmap bmRotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
            bitmap.recycle();
            return bmRotated;
        }
        catch (OutOfMemoryError e) {
            e.printStackTrace();
            return null;
        }
    }

    public static CCImage readYuvImage(String path, String fileName) {
        //File Name : Format Yuv_2880x2160_2880_97105789397739
        int width = 0;
        int height = 0;
        int stride = 0;
        long timestamp = 0;

        String[] nameSplit = fileName.split("_");
        if (path.contains("payload")) {
            String[] sizeStr = nameSplit[2].split("x");
            width = Integer.parseInt(sizeStr[0]);
            height = Integer.parseInt(sizeStr[1]);
            stride = width;
            timestamp = Integer.valueOf(nameSplit[1].substring(1)) * 330000;
        } else {
            String[] sizeStr = nameSplit[1].split("x");
            width = Integer.parseInt(sizeStr[0]);
            height = Integer.parseInt(sizeStr[1]);
            stride = Integer.parseInt(nameSplit[2]);
            timestamp = Long.parseLong(nameSplit[3]);
        }

        CCImage readImage = new CCImage(width, height, stride, timestamp);
        File file = new File(path);
        try {
            FileChannel fc = new FileInputStream(file).getChannel();
            fc.read(readImage.mYuvBuffer);
            readImage.mYuvBuffer.clear();
            fc.close();
        } catch (Exception e) {
            e.printStackTrace();
        }

        return readImage;
    }

    //Exif
    public static short getOrientationValueForRotation(int degrees) {
        degrees %= 360;
        if (degrees < 0) {
            degrees += 360;
        }
        if (degrees < 90) {
            return ExifInterface.ORIENTATION_NORMAL; // 0 degrees
        } else if (degrees < 180) {
            return ExifInterface.ORIENTATION_ROTATE_90; // 90 degrees cw
        } else if (degrees < 270) {
            return ExifInterface.ORIENTATION_ROTATE_180; // 180 degrees
        } else {
            return ExifInterface.ORIENTATION_ROTATE_270; // 270 degrees cw
        }
    }

    public static ExifInterface openExif(String path) {
        ExifInterface exif = null;
        try {
            exif = new ExifInterface(path);
        } catch (Exception e) {
            CCLog.e(TAG, "Exif open fail");
            e.printStackTrace();
        }
        return exif;
    }

    public static ExifInterface openExif(byte[] jpegBytes) {
        ExifInterface exif = null;
        try {
            InputStream inputStream = new ByteArrayInputStream(jpegBytes);
            exif = new ExifInterface(inputStream);
        } catch (Exception e) {
            CCLog.e(TAG, "Exif open fail");
            e.printStackTrace();
        }
        return exif;
    }

    public static int readExifOrientation(ExifInterface exif) {
        if (exif != null) {
            return exif.getAttributeInt(ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_UNDEFINED);
        } else {
            return -1;
        }
    }

    public static void writeExifInfo(ExifInterface exif, int orientation) {
        if (exif != null) {
            exif.setAttribute(ExifInterface.TAG_ORIENTATION, String.valueOf(orientation));
            try {
                exif.saveAttributes();
            } catch (IOException e) {
                CCLog.e(TAG, "Exif save fail");
                e.printStackTrace();
            }
        }
    }

    //Camera
    public static Size getRequestFullFrameSize() {
        String sizeString = getRequestFullFrame();
        String[] values = sizeString.split("x");
        Size size = new Size(Integer.parseInt(values[0]), Integer.parseInt(values[1]));
        return size;
    }

    public static String getRequestFullFrame() {
        return CCPreferences.getString(CCConstants.KEY_FULL_FRAME_RESOLUTION);
    }

    public static void setRequestFullFrame(String requestSize) {
        CCPreferences.setString(CCConstants.KEY_FULL_FRAME_RESOLUTION, requestSize);
    }

    public static String getCameraId() {
        return CAMERA_ID;
    }

    public static void initPreference(Context context) {
        CCPreferences.initPreferences(context);
        if (getRequestFullFrame() == null) {
            try {
                CameraManager cameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
                CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(CAMERA_ID);

                StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

                Size[] fullFrameSizes = map.getOutputSizes(ImageFormat.YUV_420_888);
                setRequestFullFrame(fullFrameSizes[0].toString());
            } catch (CameraAccessException cae) {
                cae.printStackTrace();
            }
        }
    }

    public static ArrayList<String> getStreamConfigurationList(Context context, int imageFormat) {
        ArrayList<String> frameSizeList = new ArrayList<String>();

        try {
            CameraManager cameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
            CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(CAMERA_ID);

            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

            Size[] frameSize = map.getOutputSizes(imageFormat);
            for (Size size : frameSize) {
                frameSizeList.add(size.toString());
                CCLog.d(TAG, "FullFrame : " + size.getWidth() + "x" + size.getHeight());
            }
        } catch (CameraAccessException cae) {

        }
        return frameSizeList;
    }

    public static Size[] getStreamConfigurationArray(Context context, Class<SurfaceTexture> klass) {
        try {
            CameraManager cameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
            CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(CAMERA_ID);

            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

            return map.getOutputSizes(klass);
        } catch (CameraAccessException cae) {

        }
        return null;
    }

    private static final int MINIMUM_PREVIEW_SIZE = 320;
    public static Size getPreviewOptimalSize(Context context, final Size desiredSize) {
        final int minSize = Math.max(Math.min(desiredSize.getWidth(), desiredSize.getHeight()), MINIMUM_PREVIEW_SIZE);

        Size[] sizeArray = getStreamConfigurationArray(context, SurfaceTexture.class);

        // Collect the supported resolutions that are at least as big as the preview Surface
        boolean exactSizeFound = false;
        final List<Size> bigEnough = new ArrayList<Size>();
        final List<Size> tooSmall = new ArrayList<Size>();
        for (final Size size : sizeArray) {
            if (desiredSize.equals(size)) {
                // Set the size but don't return yet so that remaining sizes will still be logged.
                exactSizeFound = true;
            }

            if (size.getHeight() >= minSize && size.getWidth() >= minSize) {
                bigEnough.add(size);
            } else {
                tooSmall.add(size);
            }
        }

        CCLog.i(TAG, "Desired size: " + desiredSize + ", min size: " + minSize + "x" + minSize);
        CCLog.i(TAG, "Valid preview sizes: [" + TextUtils.join(", ", bigEnough) + "]");
        CCLog.i(TAG, "Rejected preview sizes: [" + TextUtils.join(", ", tooSmall) + "]");

        if (exactSizeFound) {
            CCLog.i(TAG, "Exact size match found.");
            return desiredSize;
        }

        // Pick the smallest of those, assuming we found any
        if (bigEnough.size() > 0) {
            final Size chosenSize = Collections.min(bigEnough, new CompareSizesByArea());
            CCLog.i(TAG, "Chosen size: " + chosenSize.getWidth() + "x" + chosenSize.getHeight());
            return chosenSize;
        } else {
            CCLog.e(TAG, "Couldn't find any suitable preview size");
            return sizeArray[0];
        }
    }

    static class CompareSizesByArea implements Comparator<Size> {
        @Override
        public int compare(final Size lhs, final Size rhs) {
            // We cast here to ensure the multiplications won't overflow
            return Long.signum(
                    (long) lhs.getWidth() * lhs.getHeight() - (long) rhs.getWidth() * rhs.getHeight());
        }
    }

    public static Matrix getTransformationMatrix(
            final int srcWidth,
            final int srcHeight,
            final int dstWidth,
            final int dstHeight,
            final int applyRotation,
            final boolean maintainAspectRatio) {
        final Matrix matrix = new Matrix();

        if (applyRotation != 0) {
            if (applyRotation % 90 != 0) {
                CCLog.w(TAG, String.format("Rotation of %d % 90 != 0", applyRotation));
            }

            // Translate so center of image is at origin.
            matrix.postTranslate(-srcWidth / 2.0f, -srcHeight / 2.0f);

            // Rotate around origin.
            matrix.postRotate(applyRotation);
        }

        // Account for the already applied rotation, if any, and then determine how
        // much scaling is needed for each axis.
        final boolean transpose = (Math.abs(applyRotation) + 90) % 180 == 0;

        final int inWidth = transpose ? srcHeight : srcWidth;
        final int inHeight = transpose ? srcWidth : srcHeight;

        // Apply scaling if necessary.
        if (inWidth != dstWidth || inHeight != dstHeight) {
            final float scaleFactorX = dstWidth / (float) inWidth;
            final float scaleFactorY = dstHeight / (float) inHeight;

            if (maintainAspectRatio) {
                // Scale by minimum factor so that dst is filled completely while
                // maintaining the aspect ratio. Some image may fall off the edge.
                final float scaleFactor = Math.max(scaleFactorX, scaleFactorY);
                matrix.postScale(scaleFactor, scaleFactor);
            } else {
                // Scale exactly to fill dst from src.
                matrix.postScale(scaleFactorX, scaleFactorY);
            }
        }

        if (applyRotation != 0) {
            // Translate back from origin centered reference to destination frame.
            matrix.postTranslate(dstWidth / 2.0f, dstHeight / 2.0f);
        }

        return matrix;
    }


}
