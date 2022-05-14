
#include <android/bitmap.h>
#include <jni.h>
#include <ImageUtils.h>
#include <LogUtil.h>
#include <CommonConstant.h>
#include <cstdlib>
#include <iostream>
#include <algorithm>

#define LOG_TAG "CloudCamera[ImageUtils]"

#define DIV(a,b) (a)/(b)

#define GET_POSITION(DST, X, RESIZE_FLOAT, RESIZE_FLOAT_HALF) DST = ((X * RESIZE_FLOAT) >> 10) + RESIZE_FLOAT_HALF;

#define Y_AVG_1STROW_1ST_PIX(inputY, inY, stride_width, oy_stride, ox) inY = inputY[oy_stride + ox] \
                                                                           + inputY[oy_stride + stride_width + ox] \
                                                                           + inputY[oy_stride + ox + 1] \
                                                                           + inputY[oy_stride + stride_width + ox + 1];

#define Y_AVG_1STROW_PIX(inputY, inY, stride_width, oy_stride, ox) inY = inputY[oy_stride + ox] \
                                                                       + inputY[oy_stride + ox - 1] \
                                                                       + inputY[oy_stride + ox + 1] \
                                                                       + inputY[oy_stride + stride_width + ox];

#define Y_AVG_1STROW_LAST_PIX(inputY, inY, stride_width, oy_stride, ox) inY = inputY[oy_stride + ox] \
                                                                            + inputY[oy_stride + ox -1] \
                                                                            + inputY[oy_stride + stride_width + ox] \
                                                                            + inputY[oy_stride + stride_width + ox - 1];

#define Y_AVG_1ST_COL_PIX(inputY, inY, stride_width, oy_stride, ox) inY = inputY[oy_stride + ox] \
                                                                        + inputY[oy_stride - stride_width + ox] \
                                                                        + inputY[oy_stride + stride_width + ox] \
                                                                        + inputY[oy_stride + ox + 1];

#define Y_AVG_LAST_COL_PIX(inputY, inY, stride_width, oy_stride, ox) inY = inputY[oy_stride + ox] \
                                                                         + inputY[oy_stride - stride_width + ox] \
                                                                         + inputY[oy_stride + stride_width + ox] \
                                                                         + inputY[oy_stride + ox - 1];

#define Y_AVG_LASTROW_1ST_PIX(inputY, inY, stride_width, oy_stride, ox) inY = inputY[oy_stride + ox] \
                                                                            + inputY[oy_stride - stride_width + ox] \
                                                                            + inputY[oy_stride + ox + 1] \
                                                                            + inputY[oy_stride - stride_width + ox + 1];

#define Y_AVG_LASTROW_PIX(inputY, inY, stride_width, oy_stride, ox) inY = inputY[oy_stride + ox] \
                                                                        + inputY[oy_stride + ox - 1] \
                                                                        + inputY[oy_stride + ox + 1] \
                                                                        + inputY[oy_stride - stride_width + ox];

#define Y_AVG_LASTROW_LAST_PIX(inputY, inY, stride_width, oy_stride, ox) inY = inputY[oy_stride + ox] \
                                                                             + inputY[oy_stride + ox -1] \
                                                                             + inputY[oy_stride - stride_width + ox] \
                                                                             + inputY[oy_stride - stride_width + ox - 1];

#define CLAMP(x, low, high)  (((x) > (high)) ? (high) : (((x) < (low)) ? (low) : (x)))

static void storePixel(uint8_t* &dst, int iR, int iG, int iB,
                        uint8_t alpha) {
    *dst++ = (iR > 0) ? (iR < 65535 ? (uint8_t) (iR >> 8) : 0xff) : 0;
    *dst++ = (iG > 0) ? (iG < 65535 ? (uint8_t) (iG >> 8) : 0xff) : 0;
    *dst++ = (iB > 0) ? (iB < 65535 ? (uint8_t) (iB >> 8) : 0xff) : 0;
    *dst++ = alpha;
}

bool convertRgbToYuv(uint8_t *out_y, uint8_t *out_uv, uint32_t* in, int width, int height, int stride) {
    int yIndex = 0, uvIndex = 0, index = 0;
    double R, G, B;
    int Y, U, V;
    int i, j;
    int padding = stride - width;

    for (j = 0; j < height; j++) {
        for (i = 0; i < width; i++) {

            R = (double)((in[index] & 0xff0000) >> 16) / 255.0;
            G = (double)((in[index] & 0xff00) >> 8) / 255.0;
            B = (double)((in[index] & 0xff)) / 255.0;

            Y =  (int)((0.299 * R + 0.587 * G + 0.114 * B) * 255.0);
            out_y[yIndex++] = (Y < 0) ? 0 : ((Y > 255) ? 255 : Y);

            if ((j % 2) == 0 && (i % 2) == 0) {
                U = (int)((-0.168736 * R - 0.331264 * G + 0.5 * B + 0.5) * 255.0);
                V = (int)((0.5 * R - 0.418688 * G - 0.081312 * B + 0.5) * 255.0);
                out_uv[uvIndex++] = (U < 0) ? 0 : ((U > 255) ? 255 : U);
                out_uv[uvIndex++] = (V < 0) ? 0 : ((V > 255) ? 255 : V);
            }

/*
            R = (in[index] & 0xff0000) >> 16;
            G = (in[index] & 0xff00) >> 8;
            B = (in[index] & 0xff);

            Y = ((66 * R + 129 * G + 25 * B + 128) >> 8) + 16;
            out_y[yIndex++] = (Y < 0) ? 0 : ((Y > 255) ? 255 : Y);

            if ((j % 2) == 0 && (i % 2) == 0) {
                U = ((-38 * R - 74 * G + 112 * B + 128) >> 8) + 128;
                V = ((112 * R - 94 * G - 18 * B + 128) >> 8) + 128;
                out_uv[uvIndex++] = (U < 0) ? 0 : ((U > 255) ? 255 : U);
                out_uv[uvIndex++] = (V < 0) ? 0 : ((V > 255) ? 255 : V);
            }
*/
            index++;
        }
        yIndex += padding;
        uvIndex += padding;
    }
    return true;
}

//yuv_601_full
bool convertYuvToRgb(uint8_t* out, uint8_t const* y0, uint8_t const* uv,  int width, int height, int stride, uint8_t alpha = 0xff) {
    // pre-condition : width and height must be even
    if (0 != (width & 1) || width < 2 || 0 != (height & 1) || height < 2 || !out
        || !y0 || !uv )
        return false;

    uint8_t* dst0 = out;
    int const halfHeight = height >> 1;
    int const halfWidth = width >> 1;
    int const padding = stride - width;

    int Y00;
    int Y01;
    int Y10;
    int Y11;
    int V;
    int U;
    int tR;
    int tG;
    int tB;
    for (int h = 0; h < halfHeight; ++h) {
        //__android_log_print(ANDROID_LOG_INFO, "Test", "decode_yuv for loop : % d", h);
        uint8_t const* y1 = y0 + stride;
        uint8_t* dst1 = dst0 + width * 4;
        for (int w = 0; w < halfWidth; ++w) {
            // shift
            Y00 = (*y0++);
            Y01 = (*y0++);
            Y10 = (*y1++);
            Y11 = (*y1++);

            // U,V or V,U? our trait will make the right call
            //loadvu(U, V, uv);
            V = (*uv++) - 128;
            U = (*uv++) - 128;

            // temps
            Y00 = (Y00 > 0) ? (256 * Y00) : 0;
            Y01 = (Y01 > 0) ? (256 * Y01) : 0;
            Y10 = (Y10 > 0) ? (256 * Y10) : 0;
            Y11 = (Y11 > 0) ? (256 * Y11) : 0;
            tR = 128 + 360 * V;
            tG = 128 - 88 * U - 184 * V;
            tB = 128 + 455 * U;

            // 2x2 pixels result
            storePixel(dst0, Y00 + tR, Y00 + tG, Y00 + tB, alpha);
            storePixel(dst0, Y01 + tR, Y01 + tG, Y01 + tB, alpha);
            storePixel(dst1, Y10 + tR, Y10 + tG, Y10 + tB, alpha);
            storePixel(dst1, Y11 + tR, Y11 + tG, Y11 + tB, alpha);

        }

        y0 = y1 + padding;
        uv += padding;
        dst0 = dst1;
    }
    return true;
}

void resizeAndCropForYuv420SP(uint8_t* dstYuv,
                              uint8_t* inputY, uint8_t* inputVU,
                              int stride,
                              int orgInputW, int orgInputH,
                              int resizedW, int resizedH,
                              int startX, int endX) {

    unsigned int resize_f_w = orgInputW * 1024 / resizedW;
    unsigned int resize_f_h = orgInputH * 1024 / resizedH;
    unsigned int resize_hf_w = (resize_f_w + (resize_f_w) % 2) >> 11;
    unsigned int resize_hf_h = (resize_f_h + (resize_f_h) % 2) >> 11;

    unsigned int ox = 0;
    unsigned int oy = 0;
    unsigned int vu_ox = 0;
    unsigned int vu_oy = 0;
    unsigned int blkCntY = 0;
    unsigned int pos = 0;
    unsigned int vu_pos = 0;
    unsigned int ox_twice = 0;
    unsigned int oy_width = 0;
    unsigned int place = 0;

    int sumY = 0;
    int avg = 0;
    int center_val = 0;
    int temp_val = 0;
    int stride_width = stride;

    if (startX < 0) {
        startX = 0;
    }
    if (endX > resizedW) {
        endX = resizedW;
    }
    unsigned int cropW = endX - startX;
    unsigned int vuPlace = resizedH * cropW;

    // Boundary processing
    unsigned int y = 0;
    unsigned int x = startX;

    // 1. The first row
    unsigned int inY = 0;
    oy = resize_hf_h;
    unsigned int oy_stride = oy * stride_width;
    vu_oy = oy >> 1;
    unsigned int vu_oy_stride = vu_oy * stride_width;
    // 1-1. The first pixel in first row
    place = 0;
    if (x == 0) {
        Y_AVG_1STROW_1ST_PIX(inputY, inY, stride_width, oy_stride, resize_hf_w);
        dstYuv[place++] = inY >> 2;
        x++;
    }
    for (; x < endX - 1; x++) {
        GET_POSITION(ox, x, resize_f_w, resize_hf_w);
        Y_AVG_1STROW_PIX(inputY, inY, stride_width, oy_stride, ox);
        dstYuv[place++] = inY >> 2;
    }
    // 1-2. The last pixel in first row
    GET_POSITION(ox, x, resize_f_w, resize_hf_w);
    Y_AVG_1STROW_LAST_PIX(inputY, inY, stride_width, oy_stride, ox);
    dstYuv[place++] = inY >> 2;

    // 2. The first and last column
    unsigned int start_ox = 0;
    unsigned int end_ox = 0;
    GET_POSITION(start_ox, startX, resize_f_w, resize_hf_w);
    GET_POSITION(end_ox, (endX - 1), resize_f_w, resize_hf_w);

    for (y = 1; y < resizedH - 1; y++) {
        // 2-1. the 1st column
        x = startX;
        ox = start_ox;
        GET_POSITION(oy, y, resize_f_h, resize_hf_h);
        oy_stride = oy * stride_width;
        Y_AVG_1ST_COL_PIX(inputY, inY, stride_width, oy_stride, ox);
        dstYuv[y * cropW] = inY >> 2;

        // 2.2. the last column
        x = endX - 1;
        ox = end_ox;
        Y_AVG_LAST_COL_PIX(inputY, inY, stride_width, oy_stride, ox);
        dstYuv[y * cropW + cropW - 1] = inY >> 2;
    }

    // 3. The last row
    x = startX;
    GET_POSITION(oy, (resizedH - 1), resize_f_h, resize_hf_h);
    oy_stride = oy * stride_width;
    vu_oy = oy >> 1;
    vu_oy_stride = vu_oy * stride_width;
    unsigned int last_row = (resizedH - 1) * cropW;
    unsigned int vu_last_row = (resizedH + (resizedH >> 1) - 1) * cropW;
    vuPlace = vu_last_row;
    // 3-1. The first pixel in last row
    if (x == 0) {
        Y_AVG_LASTROW_1ST_PIX(inputY, inY, stride_width, oy_stride, resize_hf_w);
        dstYuv[last_row++] = inY >> 2;
        x++;
    }
    for (; x < endX - 1; x++) {
        GET_POSITION(ox, x, resize_f_w, resize_hf_w);
        Y_AVG_LASTROW_PIX(inputY, inY, stride_width, oy_stride, ox);
        dstYuv[last_row++] = inY >> 2;
    }
    // 3-2. The last pixel in last row
    GET_POSITION(ox, x, resize_f_w, resize_hf_w);
    Y_AVG_LASTROW_LAST_PIX(inputY, inY, stride_width, oy_stride, ox);
    dstYuv[last_row++] = inY >> 2;

    // 4. Center area processing
    place = 0;
    vuPlace = resizedH * cropW;
    for (y = 0; y < resizedH; y++) {
        for (x = startX; x < endX; x++) {
            GET_POSITION(ox, x, resize_f_w, resize_hf_w);
            GET_POSITION(oy, y, resize_f_h, resize_hf_h);
            if (y != 0 && y != resizedH - 1 && x != startX && x != endX - 1) {
                oy_stride = oy * stride_width;
                inY = inputY[oy_stride + ox];
                inY += inputY[oy_stride - stride_width + ox];
                inY += inputY[oy_stride + stride_width + ox];
                inY += inputY[oy_stride + ox - 1];
                inY += inputY[oy_stride + ox + 1];
                inY += inputY[oy_stride - stride_width + ox - 1];
                inY += inputY[oy_stride - stride_width + ox + 1];
                inY += inputY[oy_stride + stride_width + ox - 1];

                dstYuv[place] = inY >> 3;
            }
            place++;

            // UV plane
            /*
             * NV21 (YUV420) doesn't need to calculate pixel value around point.
             * Because, basically, UV express less precisely than Y.
             * As a result, it is enough by point sampling in UV plane.
            */
            if ((x & 1) == 0 && (y & 1) == 0) {
                vu_ox = ox >> 1;
                vu_oy = oy >> 1;
                ox_twice = vu_ox << 1;
                vu_pos = vu_oy * stride_width + ox_twice;
                dstYuv[vuPlace++] = inputVU[vu_pos];
                dstYuv[vuPlace++] = inputVU[vu_pos + 1];
            }
        }
    }
}

float computePixelDiff(uint8_t* pixelX, uint8_t* pixelY, int width, int height, int stride, int method) {
    float resultDiff = 0.0f;
    uint8_t *x = pixelX;
    uint8_t *y = pixelY;
    int padding = stride - width;

    if (method == kr_ac_snu_nxc_cloudcamera_library_ImageUtils_DIFF_METHOD_SIMPLE) {
        int diffCount = 0;
        for (int h = 0; h < height; h++) {
            for (int w = 0; w < width; w++) {
                diffCount = (*x != *y) ? diffCount + 1 : diffCount;
                x++;
                y++;
            }
            x += padding;
            y += padding;
        }
        resultDiff = (float)diffCount / (height * width);
        LOGI(LOG_DEBUG, "[SIMPLE]%d x %d Diff = %d  Normalize Diff = %f", width, height, diffCount, resultDiff);
    } else if (method == kr_ac_snu_nxc_cloudcamera_library_ImageUtils_DIFF_METHOD_MSE) {
        float diff;
        float sdiff;
        float diffSum = 0.0f;

        for (int h = 0; h < height; h++) {
            for (int w = 0; w < width; w++) {
                diff = (float)(*x - *y);
                sdiff = sqrtf(diff * diff);
                diffSum += sdiff;
                x++;
                y++;
            }
            x += padding;
            y += padding;
        }

        resultDiff = diffSum / (height * width);
        LOGI(LOG_DEBUG, "[MSE] Diff = %f Normalize Diff = %f", diffSum, resultDiff);
    } else if (method == kr_ac_snu_nxc_cloudcamera_library_ImageUtils_DIFF_METHOD_PSNR) {
        float diff;
        float diffSum = 0.0f;

        for (int h = 0; h < height; h++) {
            for (int w = 0; w < width; w++) {
                diff = (float)(*x - *y);
                diffSum += diff * diff;
                x++;
                y++;
            }
            x += padding;
            y += padding;
        }
        float mse = diffSum / (float)(width * height);
        resultDiff = 10 * log10f((255.0f * 255.0f) / mse);
        LOGI(LOG_DEBUG, "[PSNR] MSE = %f PSNR = %f", mse, resultDiff);
    }

    return resultDiff;
}

JNIEXPORT void JNICALL Java_kr_ac_snu_nxc_cloudcamera_library_ImageUtils_convertBitmapNative
        (JNIEnv *pEnv, jclass pObj, jobject outBitmap, jobject inYuv420sp, jint stride, jint width, jint height)
{
    // 1. Parameter setting
    AndroidBitmapInfo lBitmapInfo;
    uint32_t * lBitmapContent;
    int lRet;
    const uint8_t alpha = 0xff;

    LOGI(LOG_DEBUG, "**yuvByteBufferToBitmap - start **");
    if ((lRet = AndroidBitmap_getInfo(pEnv, outBitmap, &lBitmapInfo)) < 0) {
        LOGI(LOG_DEBUG, "AndroidBitmap_getInfo failed! error = %d", lRet);
        return;
    }

    if (lBitmapInfo.format != ANDROID_BITMAP_FORMAT_RGBA_8888) {
        LOGI(LOG_DEBUG, "Bitmap format is not RGBA_8888!");
        return;
    }

    int32_t bitmapWidth = lBitmapInfo.width;
    int32_t bitmapHeight = lBitmapInfo.height;

    //check values.
    if (bitmapWidth != width || bitmapHeight != height) {
        LOGE(LOG_DEBUG, "**yuvByteBufferToBitmap** size mis match");
        return;
    }

    if ((lRet = AndroidBitmap_lockPixels(pEnv, outBitmap, (void**)&lBitmapContent)) < 0) {
        LOGI(LOG_DEBUG, "AndroidBitmap_lockPixels() failed! error = %d", lRet);
        return;
    }

    uint8_t* inputY = static_cast<uint8_t*>(pEnv->GetDirectBufferAddress(inYuv420sp));
    uint8_t* inputVU = inputY + stride * height;

    // 2. Convert format
    convertYuvToRgb((uint8_t*)lBitmapContent, inputY, inputVU, width, height, stride, alpha);

    // 3. Release
    AndroidBitmap_unlockPixels(pEnv, outBitmap);
    LOGI(3, "**yuvByteBufferToBitmap - end **");
}

void JNICALL Java_kr_ac_snu_nxc_cloudcamera_library_ImageUtils_convertYuvNative
        (JNIEnv *pEnv, jclass pObj, jobject outYuv420sp, jobject inBitmap, jint stride, jint width, jint height)
{
    // 1. Parameter setting
    AndroidBitmapInfo lBitmapInfo;
    uint32_t * lBitmapContent;
    int lRet;
    const uint8_t alpha = 0xff;

    LOGI(LOG_DEBUG, "**convertYuvNative - start **");
    if ((lRet = AndroidBitmap_getInfo(pEnv, inBitmap, &lBitmapInfo)) < 0) {
        LOGI(LOG_DEBUG, "AndroidBitmap_getInfo failed! error = %d", lRet);
        return;
    }

    if (lBitmapInfo.format != ANDROID_BITMAP_FORMAT_RGBA_8888) {
        LOGI(LOG_DEBUG, "Bitmap format is not RGBA_8888!");
        return;
    }

    int32_t bitmapWidth = lBitmapInfo.width;
    int32_t bitmapHeight = lBitmapInfo.height;

    //check values.
    if (bitmapWidth != width || bitmapHeight != height) {
        LOGE(LOG_DEBUG, "**convertYuvNative** size mis match %d x %d / %d x %d", bitmapWidth, bitmapHeight, width, height);
        return;
    }

    if ((lRet = AndroidBitmap_lockPixels(pEnv, inBitmap, (void**)&lBitmapContent)) < 0) {
        LOGI(LOG_DEBUG, "AndroidBitmap_lockPixels() failed! error = %d", lRet);
        return;
    }

    uint8_t* outputY = static_cast<uint8_t*>(pEnv->GetDirectBufferAddress(outYuv420sp));
    uint8_t* outputUV = outputY + stride * height;

    // 2. Convert format
    convertRgbToYuv(outputY, outputUV, (uint32_t*)lBitmapContent, width, height, stride);

    // 3. Release
    AndroidBitmap_unlockPixels(pEnv, inBitmap);
    LOGI(3, "**convertYuvNative - end **");
}

JNIEXPORT void JNICALL Java_kr_ac_snu_nxc_cloudcamera_library_ImageUtils_convertResizedBitmapNative
        (JNIEnv *pEnv, jclass pObj, jobject outBitmap, jobject inYuv420sp,
        jint stride, jint orgInputW, jint orgInputH,
        jint resizedW, jint resizedH)
{
    // 1. Parameter setting
    AndroidBitmapInfo lBitmapInfo;
    uint32_t * lBitmapContent;
    int lRet;
    const uint8_t alpha = 0xff;

    LOGI(LOG_DEBUG, "**yuvByteBufferToBitmapWithResize - start **");
    if ((lRet = AndroidBitmap_getInfo(pEnv, outBitmap, &lBitmapInfo)) < 0) {
        LOGI(LOG_DEBUG, "AndroidBitmap_getInfo failed! error = %d", lRet);
        return;
    }

    if (lBitmapInfo.format != ANDROID_BITMAP_FORMAT_RGBA_8888) {
        LOGI(LOG_DEBUG, "Bitmap format is not RGBA_8888!");
        return;
    }

    int32_t bitmapWidth = lBitmapInfo.width;
    int32_t bitmapHeight = lBitmapInfo.height;

    //check values.
    if (bitmapWidth != resizedW || bitmapHeight != resizedH) {
        LOGE(LOG_DEBUG, "**yuvByteBufferToBitmapWithResize** size mis match");
        return;
    }

    if ((lRet = AndroidBitmap_lockPixels(pEnv, outBitmap, (void**)&lBitmapContent)) < 0) {
        LOGI(LOG_DEBUG, "AndroidBitmap_lockPixels() failed! error = %d", lRet);
        return;
    }

    uint8_t* inputY = static_cast<uint8_t*>(pEnv->GetDirectBufferAddress(inYuv420sp));
    uint8_t* inputVU = inputY + stride * orgInputH;

    uint8_t* resizedYuv = reinterpret_cast<uint8_t*> (calloc(resizedW * resizedH * 3 / 2, sizeof(jbyte)));
    resizeAndCropForYuv420SP(resizedYuv, inputY, inputVU, stride, orgInputW, orgInputH, resizedW, resizedH, 0, resizedW);

    // 2. Convert format
    convertYuvToRgb((uint8_t*)lBitmapContent, resizedYuv, resizedYuv + (resizedW * resizedH), resizedW, resizedH, resizedW, alpha);

    // 3. Release
    AndroidBitmap_unlockPixels(pEnv, outBitmap);

    free(resizedYuv);
    LOGI(LOG_DEBUG, "**yuvByteBufferToBitmapWithResize - end **");
}

JNIEXPORT void JNICALL Java_kr_ac_snu_nxc_cloudcamera_library_ImageUtils_downScaleCCImage
        (JNIEnv *pEnv, jclass pObj, jobject outYuv420sp, jobject inYuv420sp,
         jint stride, jint orgInputW, jint orgInputH,
         jint resizedW, jint resizedH) {
    LOGI(LOG_DEBUG, "**downScaleCCImage - start **");
    uint8_t* inputY = static_cast<uint8_t*>(pEnv->GetDirectBufferAddress(inYuv420sp));
    uint8_t* inputVU = inputY + stride * orgInputH;

    uint8_t* resizedYuv = static_cast<uint8_t*>(pEnv->GetDirectBufferAddress(outYuv420sp));
    resizeAndCropForYuv420SP(resizedYuv, inputY, inputVU, stride, orgInputW, orgInputH, resizedW, resizedH, 0, resizedW);
    LOGI(LOG_DEBUG, "**downScaleCCImage - end **");
}

JNIEXPORT void JNICALL Java_kr_ac_snu_nxc_cloudcamera_library_ImageUtils_convertCenterCropCCImage
        (JNIEnv *pEnv, jclass pObj, jobject inYuv420sp, jobject outYuv420sp,
         jint startX, jint startY, jint cropW, jint cropH,
         jint inWidth, jint inHeight, jint inStride) {
    LOGI(LOG_DEBUG, "**convertCenterCropCCImage - start **");

    uint8_t* inYuv = static_cast<uint8_t*>(pEnv->GetDirectBufferAddress(inYuv420sp));

    uint8_t* cropYuv;

    cropYuv = static_cast<uint8_t *>(pEnv->GetDirectBufferAddress(outYuv420sp));

    int32_t vu_offset = inStride * inHeight;
    int32_t vu_idx = 0;

    int32_t x, y;
    int32_t outW = startX + cropW;
    int32_t outH = startY + cropH;
    int32_t startX_vu = ((startX >> 1) << 1);

    int outY, outX, outVU_idx;
    int outVU_offset = cropW * cropH;

    LOGI(LOG_DEBUG, "Crop x, y : %d, %d, crop W * H = %d * %d", startX, startY, cropW, cropH);

    for (y = startY; y < outH; y++) {
        outY = y - startY;

        vu_idx = vu_offset + (y >> 1) * inStride + startX_vu;
        outVU_idx = outVU_offset + (outY >> 1) * cropW;

        for (x = startX; x < outW; x++) {
            outX = x - startX;

            cropYuv[outX + outY * cropW] = inYuv[x + y * inStride];
            if ((x % 2 == 0) && (y % 2 == 0)) {
                cropYuv[outVU_idx++] = inYuv[vu_idx++];
                cropYuv[outVU_idx++] = inYuv[vu_idx++];
            }
        }
    }

    LOGI(LOG_DEBUG, "**convertCenterCropCCImage - end **");
}

JNIEXPORT void JNICALL Java_kr_ac_snu_nxc_cloudcamera_library_ImageUtils_swapUV
        (JNIEnv *pEnv, jclass pObj, jobject yuv420sp, jint stride, jint width, jint height) {
    LOGD(LOG_DEBUG, "SWAP UV");

    uint8_t* yuvImage = static_cast<uint8_t*>(pEnv->GetDirectBufferAddress(yuv420sp));
    uint8_t* uvImage = yuvImage + stride * height;

    for (int h = 0; h < height / 2; h++) {
        for (int w = 0; w < width; w += 2) {
            uint8_t swapData = *uvImage;
            *uvImage = *(uvImage+1);
            *(uvImage+1) = swapData;
            uvImage += 2;
        }
        uvImage += (stride - width);
    }
}

JNIEXPORT jfloat JNICALL Java_kr_ac_snu_nxc_cloudcamera_library_ImageUtils_computePixelDiff
        (JNIEnv *pEnv, jclass pObj, jobject yuv420spX, jobject yuv420spY,
         jint stride, jint width, jint height, jint diffChannel, jint diffMethod)
{
    jfloat diff = 0;

    uint8_t* yChannelX = static_cast<uint8_t*>(pEnv->GetDirectBufferAddress(yuv420spX));
    uint8_t* uvChannelX = yChannelX + stride * height;

    uint8_t* yChannelY = static_cast<uint8_t*>(pEnv->GetDirectBufferAddress(yuv420spY));
    uint8_t* uvChannelY = yChannelY + stride * height;

    if (diffChannel == kr_ac_snu_nxc_cloudcamera_library_ImageUtils_YUV_CHANNEL) {
        diff = computePixelDiff(yChannelX, yChannelY, width, height + height / 2, stride, diffMethod);
        LOGD(LOG_DEBUG, "YVU diff = %f", diff);
    } else if (diffChannel == kr_ac_snu_nxc_cloudcamera_library_ImageUtils_Y_CHANNEL) {
        diff = computePixelDiff(yChannelX, yChannelY, width, height, stride, diffMethod);
        LOGD(LOG_DEBUG, "Y diff = %f", diff);
    } else if (diffChannel == kr_ac_snu_nxc_cloudcamera_library_ImageUtils_UV_CHANNEL) {
        diff = computePixelDiff(uvChannelX, uvChannelY, width, height / 2, stride, diffMethod);
        LOGD(LOG_DEBUG, "VU diff = %f", diff);
    }
    return diff;
}

JNIEXPORT void JNICALL Java_kr_ac_snu_nxc_cloudcamera_library_ImageUtils_convertByteToFloatBuffer
        (JNIEnv *pEnv, jclass pObj, jobject byteBuffer, jobject floatBuffer,
         jint width, jint height, jint stride, jboolean normalize) {
    LOGD(LOG_DEBUG, "convertByteToFloatBuffer START");

    uint8_t* byteImage = static_cast<uint8_t*>(pEnv->GetDirectBufferAddress(byteBuffer));
    float* floatImage = static_cast<float*>(pEnv->GetDirectBufferAddress(floatBuffer));

    uint8_t *readImage = byteImage;
    float* writeImage = floatImage;
    float normalizeValue = 1.0f;

    if (normalize == JNI_TRUE) {
        normalizeValue = 255.0f;
    }

    for (int h = 0; h < height; h++) {
        for (int w = 0; w < width; w++) {
            *writeImage = static_cast<float>(*readImage) / normalizeValue;
            writeImage++;
            readImage++;
        }
        readImage += (stride - width);
    }

    LOGD(LOG_DEBUG, "convertByteToFloatBuffer END");
}

JNIEXPORT void JNICALL Java_kr_ac_snu_nxc_cloudcamera_library_ImageUtils_convertFloatToByteBuffer
        (JNIEnv *pEnv, jclass pObj, jobject byteBuffer, jobject floatBuffer,
         jint width, jint height, jint stride, jint channel, jboolean denormalize) {
    LOGD(LOG_DEBUG, "convertFloatToByteBuffer START");

    uint8_t* byteImage = static_cast<uint8_t*>(pEnv->GetDirectBufferAddress(byteBuffer));
    float* floatImage = static_cast<float*>(pEnv->GetDirectBufferAddress(floatBuffer));

    uint8_t *writeImage = byteImage;
    float* readImage = floatImage;
    float denormalizeValue = 1.0f;

    if (denormalize == JNI_TRUE) {
        denormalizeValue = 255.0f;
    }

    if (channel == kr_ac_snu_nxc_cloudcamera_library_ImageUtils_YUV_CHANNEL) {
        height = height + height / 2;
    }

    LOGD(LOG_DEBUG, "Image %d x %d", width, height);

    for (int h = 0; h < height; h++) {
        for (int w = 0; w < width; w++) {
            *writeImage =  (uint8_t)(fmin(fmax((*readImage) * denormalizeValue, 0.0f), 255.0f));
            writeImage++;
            readImage++;
        }
        writeImage += (stride - width);
    }

    LOGD(LOG_DEBUG, "convertFloatToByteBuffer END");

}


JNIEXPORT void JNICALL Java_kr_ac_snu_nxc_cloudcamera_library_ImageUtils_nativeMergeImages
        (JNIEnv *pEnv, jclass pObj, jobjectArray byteBufferArray, jobject mergeBuffers,
         jint width, jint height, jint stride, jboolean normalize) {
    int imageCount = pEnv->GetArrayLength(byteBufferArray);

    LOGD(LOG_DEBUG, "nativeMergeImages count = %d START", imageCount);

    jobject byteBuffers[kr_ac_snu_nxc_cloudcamera_library_ImageUtils_MAX_IMAGE_BUFFERS];
    uint8_t* imageBuffers[kr_ac_snu_nxc_cloudcamera_library_ImageUtils_MAX_IMAGE_BUFFERS];
    uint8_t* readImages[kr_ac_snu_nxc_cloudcamera_library_ImageUtils_MAX_IMAGE_BUFFERS];

    for (int i = 0; i < imageCount; i++) {
        byteBuffers[i] = (jobject)pEnv->GetObjectArrayElement(byteBufferArray, i);

        if (byteBuffers[i] == NULL) {
            LOGE(LOG_ERROR,"%s: byteBuffers[%d] == NULL\n",__func__, i);
            pEnv->DeleteLocalRef(byteBuffers[i]);
            break;
        }

        LOGD(LOG_DEBUG, "Get Image %d", i);
        imageBuffers[i] = static_cast<uint8_t*>(pEnv->GetDirectBufferAddress(byteBuffers[i]));
        if (imageBuffers[i] == NULL) {
            LOGE(LOG_ERROR,"%s: imageBuffers[%d] == NULL\n",__func__, i);
            pEnv->DeleteLocalRef(byteBuffers[i]);
            break;
        }

        readImages[i] = imageBuffers[i];

        pEnv->DeleteLocalRef(byteBuffers[i]);
    }

    LOGD(LOG_DEBUG, "Get merge buffer");
    uint8_t* mergeImages = static_cast<uint8_t*>(pEnv->GetDirectBufferAddress(mergeBuffers));
    if (mergeImages == NULL) {
        LOGE(LOG_ERROR,"%s: mergeImages == NULL\n",__func__);
        return;
    }

    uint8_t* writeImage = mergeImages;

    for (int h = 0; h < height; h++) {
        for (int w = 0; w < width; w++) {
            for (int c = 0; c < imageCount; c++) {
                *writeImage = *readImages[c];
                writeImage++;
                (readImages[c])++;
            }
        }
        for (int c = 0; c < imageCount; c++) {
            readImages[c] += (stride - width);
        }
    }

    LOGD(LOG_DEBUG, "nativeMergeImages END");
}

JNIEXPORT void JNICALL Java_kr_ac_snu_nxc_cloudcamera_library_ImageUtils_convertByteToFloatBufferArray
        (JNIEnv *pEnv, jclass pObj, jobjectArray byteBufferArray, jobject floatBuffer,
         jint width, jint height, jint stride, jboolean normalize) {
    int imageCount = pEnv->GetArrayLength(byteBufferArray);

    LOGD(LOG_DEBUG, "convertByteToFloatBufferArray count = %d START", imageCount);

    jobject byteBuffers[kr_ac_snu_nxc_cloudcamera_library_ImageUtils_MAX_IMAGE_BUFFERS];
    uint8_t* imageBuffers[kr_ac_snu_nxc_cloudcamera_library_ImageUtils_MAX_IMAGE_BUFFERS];
    uint8_t* readImages[kr_ac_snu_nxc_cloudcamera_library_ImageUtils_MAX_IMAGE_BUFFERS];

    for (int i = 0; i < imageCount; i++) {
        byteBuffers[i] = (jobject)pEnv->GetObjectArrayElement(byteBufferArray, i);

        if (byteBuffers[i] == NULL) {
            LOGE(LOG_ERROR,"%s: byteBuffers[%d] == NULL\n",__func__, i);
            pEnv->DeleteLocalRef(byteBuffers[i]);
            break;
        }

        LOGD(LOG_DEBUG, "Get Image %d", i);
        imageBuffers[i] = static_cast<uint8_t*>(pEnv->GetDirectBufferAddress(byteBuffers[i]));
        if (imageBuffers[i] == NULL) {
            LOGE(LOG_ERROR,"%s: imageBuffers[%d] == NULL\n",__func__, i);
            pEnv->DeleteLocalRef(byteBuffers[i]);
            break;
        }

        readImages[i] = imageBuffers[i];

        pEnv->DeleteLocalRef(byteBuffers[i]);
    }

    LOGD(LOG_DEBUG, "Get float buffer");
    float* floatImage = static_cast<float*>(pEnv->GetDirectBufferAddress(floatBuffer));
    if (floatImage == NULL) {
        LOGE(LOG_ERROR,"%s: floatImage == NULL\n",__func__);
        return;
    }

    float* writeImage = floatImage;
    float normalizeValue = 1.0f;

    if (normalize == JNI_TRUE) {
        normalizeValue = 255.0f;
    }

    for (int h = 0; h < height; h++) {
        for (int w = 0; w < width; w++) {
            for (int c = 0; c < imageCount; c++) {
                *writeImage = ((float) (*(readImages[c]))) / normalizeValue;
                writeImage++;
                (readImages[c])++;
            }
        }
        for (int c = 0; c < imageCount; c++) {
            readImages[c] += (stride - width);
        }
    }

    LOGD(LOG_DEBUG, "convertByteToFloatBufferArray END");
}

JNIEXPORT void JNICALL Java_kr_ac_snu_nxc_cloudcamera_library_ImageUtils_convertFloatToBitmap
        (JNIEnv *pEnv, jclass pObj, jobject bitmap, jobject floatBuffer,
         jint width, jint height, jboolean denormalize) {
    LOGI(LOG_DEBUG, "convertFloatToBitmap START");

    // 1. Parameter setting
    AndroidBitmapInfo lBitmapInfo;
    uint8_t* lBitmapContent;
    int lRet;
    const uint8_t alpha = 0xff;

    LOGI(LOG_DEBUG, "**yuvByteBufferToBitmapWithResize - start **");
    if ((lRet = AndroidBitmap_getInfo(pEnv, bitmap, &lBitmapInfo)) < 0) {
        LOGE(LOG_DEBUG, "AndroidBitmap_getInfo failed! error = %d", lRet);
        return;
    }

    if (lBitmapInfo.format != ANDROID_BITMAP_FORMAT_RGBA_8888) {
        LOGE(LOG_DEBUG, "Bitmap format is not RGBA_8888!");
        return;
    }

    int32_t bitmapWidth = lBitmapInfo.width;
    int32_t bitmapHeight = lBitmapInfo.height;


    float denormalizeValue = 1.0f;

    if (denormalize == JNI_TRUE) {
        denormalizeValue = 255.0f;
    }

    LOGD(LOG_DEBUG, "Bitmap image %d x %d denomalize %f", width, height, denormalizeValue);


    if ((lRet = AndroidBitmap_lockPixels(pEnv, bitmap, (void**)&lBitmapContent)) < 0) {
        LOGE(LOG_DEBUG, "AndroidBitmap_lockPixels() failed! error = %d", lRet);
        return;
    }

    float* readBuffer = static_cast<float*>(pEnv->GetDirectBufferAddress(floatBuffer));
    uint8_t* writeBitmap = lBitmapContent;

    for (int h = 0; h < height; h++) {
        for (int w = 0; w < width; w++) {
            uint8_t r = (uint8_t)(fmin(fmax((*readBuffer) * denormalizeValue, 0.0f), 255.0f));
            readBuffer++;
            uint8_t g = (uint8_t)(fmin(fmax((*readBuffer) * denormalizeValue, 0.0f), 255.0f));
            readBuffer++;
            uint8_t b = (uint8_t)(fmin(fmax((*readBuffer) * denormalizeValue, 0.0f), 255.0f));
            readBuffer++;

            *writeBitmap++ = r;
            *writeBitmap++ = g;
            *writeBitmap++ = b;
            *writeBitmap++ = alpha;
        }
    }

    // 3. Release
    AndroidBitmap_unlockPixels(pEnv, bitmap);

    LOGI(LOG_DEBUG, "convertFloatToBitmap END");
}

JNIEXPORT void JNICALL Java_kr_ac_snu_nxc_cloudcamera_library_ImageUtils_updateCCImage
        (JNIEnv *pEnv, jclass pObj, jobject toByteBuffer, jobject fromByteBuffer,
         jint width, jint height, jint stride, jint fromBufferSize, jint channel, jboolean denormalize) {
    LOGI(LOG_DEBUG, "updateCCImage START");

    uint8_t* toByteImage = static_cast<uint8_t*>(pEnv->GetDirectBufferAddress(toByteBuffer));
    uint8_t* fromByteImage = static_cast<uint8_t*>(pEnv->GetDirectBufferAddress(fromByteBuffer));

    if (channel == kr_ac_snu_nxc_cloudcamera_library_ImageUtils_Y_CHANNEL) {
        if (fromBufferSize == stride * height) {
            for (int h = 0; h < height; h++) {
                LOGE(LOG_DEBUG, "value1 : h[%d] %f", h, (float)fromByteImage[h * width]);
                for (int w = 0; w < width; w++) {
                    toByteImage[h * stride + w] = fromByteImage[h * width + w];
                }
            }

//            memcpy(toByteImage, fromByteImage, fromBufferSize);
        } else {
            for (int h = 0; h < height; h++) {
                LOGE(LOG_DEBUG, "value2 : h[%d] %f", h, (float)fromByteImage[h * width]);
                for (int w = 0; w < width; w++) {
                    toByteImage[h * stride + w] = fromByteImage[h * width + w];
                }
            }
        }
    } else if (channel == kr_ac_snu_nxc_cloudcamera_library_ImageUtils_YUV_CHANNEL) {
        int yuvHeight = height + (height / 2);
        if (fromBufferSize == stride * yuvHeight) {
            memcpy(toByteImage, fromByteImage, fromBufferSize);
        } else {
            for (int h = 0; h < yuvHeight; h++) {
                for (int w = 0; w < width; w++) {
                    toByteImage[h * stride + w] = fromByteImage[h * width + w];
                }
            }
        }
    } else {
        LOGE(LOG_ERROR, "Not support channel");
    }

    LOGI(LOG_DEBUG, "updateCCImage END");
}


JNIEXPORT void JNICALL Java_kr_ac_snu_nxc_cloudcamera_library_ImageUtils_nativeQuantizeBuffer
        (JNIEnv *pEnv, jclass pObj,
         jobject inputByteBuffer, jobject outputByteBuffer,
         jint width, jint height, jfloat scale, jint zero) {
    LOGI(LOG_DEBUG, "quantizeBuffer START");


    uint8_t* inputBuffer = static_cast<uint8_t*>(pEnv->GetDirectBufferAddress(inputByteBuffer));
    int8_t* outputBuffer = static_cast<int8_t*>(pEnv->GetDirectBufferAddress(outputByteBuffer));

    uint8_t* read_pos = inputBuffer;
    int8_t* write_pos = outputBuffer;

    for (int h = 0; h < height; h++) {
        read_pos = inputBuffer + (h * width);

        for (int w = 0; w < width; w++) {
            uint8_t data = *(read_pos);
            float quantData = (((float)(data) / 255.0f) / scale) + zero;
            int8_t writeData = static_cast<int8_t>(CLAMP(quantData, -128.0f, 127.0f));

            if (h % 1500 == 0 && w % 200 == 0) {
                LOGE(LOG_ERROR, "quantizeBuffer %d -> %f -> %d", data, quantData, writeData);
            }

            *write_pos = writeData;
            write_pos++;
            read_pos++;
        }
    }
    LOGI(LOG_DEBUG, "quantizeBuffer END");
}

template<typename T>
void updateLatentBitmap(JNIEnv *pEnv, uint8_t* bitmap, jint index, jint imageWidth, jint imageHeight,
                     jint width, jint height, jint channel,
                     jobject byteBuffer, jfloat scale, jint zero) {

    const uint8_t alpha = 0xff;

    T* buffers = static_cast<T*>(pEnv->GetDirectBufferAddress(byteBuffer));

//    LOGI(LOG_DEBUG, "updateLatentBitmap allocate %d x %d", width, height);

    uint8_t *scaledBuffer = new uint8_t[width * height];

    float* dequantBuffer = new float[width * height];

    T* readPos = buffers;
    float* dequantPos = dequantBuffer;

    float fmin = 1000;
    float fmax = -1000;

    for (int h = 0; h < height; h++) {
        readPos = buffers + (h * (width * channel));

        for (int w = 0; w < width; w++) {
            T data = *(readPos + index);
            float dequantData = ((float)data - zero) * scale;

//            if (h % 1000 == 0 && w % 200 == 0) {
//                LOGE(LOG_ERROR, "updateLatentBitmap %d -> %f", data, dequantData);
//            }
            *dequantPos = dequantData;
            dequantPos++;
            readPos += channel;

            if (fmin > dequantData) {
                fmin = dequantData;
            }
            if (fmax < dequantData) {
                fmax = dequantData;
            }
        }
    }

    uint8_t* scaledPos = scaledBuffer;
    dequantPos = dequantBuffer;
    float minMaxScale = fmax - fmin;

//    LOGE(LOG_ERROR, "scale fmin %f - fmax %f", fmin, fmax);

    for (int h = 0; h < height; h++) {
        dequantPos = dequantBuffer + (h * (width));

        for (int w = 0; w < width; w++) {
            float data = *(dequantPos);
            data = (data - fmin) / minMaxScale;
            uint8_t writeData = static_cast<uint8_t>(CLAMP(data * 255.0f, 0.0f, 255.f));
            *scaledPos = writeData;

//            if (h % 1000 == 0 && w % 200 == 0) {
//                LOGE(LOG_ERROR, "scale %f -> %d", data, writeData);
//            }
            scaledPos++;
            dequantPos++;
        }
    }

    uint8_t* inputY = scaledBuffer;
    uint8_t* inputVU = inputY + (imageHeight * imageWidth);
//    LOGI(LOG_DEBUG, "dequantizeBuffer yuv to rgb %d x %d", imageWidth, imageHeight);

    convertYuvToRgb(bitmap, inputY, inputVU, imageWidth, imageHeight, imageWidth, alpha);

    delete[] scaledBuffer;
    delete[] dequantBuffer;
}

JNIEXPORT void JNICALL Java_kr_ac_snu_nxc_cloudcamera_library_ImageUtils_nativeGetLatentBitmap
        (JNIEnv *pEnv, jclass pObj, jobject outBitmap, jint index, jint imageWidth, jint imageHeight,
         jint width, jint height, jint channel,
         jint dType, jobject byteBuffer, jfloat scale, jint zero) {
//    LOGI(LOG_DEBUG, "nativeGetLatentBitmap START");
    // 1. Parameter setting
    AndroidBitmapInfo lBitmapInfo;
    uint32_t * lBitmapContent;
    int lRet;

    if ((lRet = AndroidBitmap_getInfo(pEnv, outBitmap, &lBitmapInfo)) < 0) {
        LOGI(LOG_DEBUG, "AndroidBitmap_getInfo failed! error = %d", lRet);
        return;
    }

    if (lBitmapInfo.format != ANDROID_BITMAP_FORMAT_RGBA_8888) {
        LOGI(LOG_DEBUG, "Bitmap format is not RGBA_8888!");
        return;
    }

    int32_t bitmapWidth = lBitmapInfo.width;
    int32_t bitmapHeight = lBitmapInfo.height;

    if ((lRet = AndroidBitmap_lockPixels(pEnv, outBitmap, (void**)&lBitmapContent)) < 0) {
        LOGI(LOG_DEBUG, "AndroidBitmap_lockPixels() failed! error = %d", lRet);
        return;
    }

    if (dType == DTYPE_UINT8) {
        updateLatentBitmap<uint8_t>(pEnv, (uint8_t*)lBitmapContent, index, imageWidth, imageHeight,
                                    width, height, channel, byteBuffer, scale, zero);
    } else if (dType == DTYPE_INT8) {
        updateLatentBitmap<int8_t>(pEnv, (uint8_t*)lBitmapContent, index, imageWidth, imageHeight,
                                    width, height, channel, byteBuffer, scale, zero);
    }

    // 3. Release
    AndroidBitmap_unlockPixels(pEnv, outBitmap);

//    LOGI(LOG_DEBUG, "nativeGetLatentBitmap END");
}


JNIEXPORT void JNICALL Java_kr_ac_snu_nxc_cloudcamera_library_ImageUtils_nativeSubtractBuffer
        (JNIEnv *pEnv, jclass, jobject resultBuffer,
         jobject baseBuffer, jobject subBuffer,
         jint width, jint height, jint dType) {
    uint8_t* base = static_cast<uint8_t*>(pEnv->GetDirectBufferAddress(baseBuffer));
    uint8_t* sub = static_cast<uint8_t*>(pEnv->GetDirectBufferAddress(subBuffer));

    LOGI(LOG_DEBUG, "nativeSubtractBuffer [%d][%d]", width, height);
    uint8_t* basePos = base;
    uint8_t* subPos = sub;

    if (dType == DTYPE_FLOAT) {
        float* result = static_cast<float*>(pEnv->GetDirectBufferAddress(resultBuffer));
        float* resultPos = result;

        for (int h = 0; h < height; h++) {
            for (int w = 0; w < width; w++) {
                float data = static_cast<float>(*basePos) - static_cast<float>(*subPos);
                float writeData = data / 255.0f;

//                if (h % 1500 == 0 && w % 200 == 0) {
//                    LOGE(LOG_ERROR, "quantizeBuffer %d -> %f -> %d", data, quantData, writeData);
//                }

                *resultPos = writeData;
                resultPos++;
                basePos++;
                subPos++;
            }
        }
    } else {
        int8_t* result = static_cast<int8_t*>(pEnv->GetDirectBufferAddress(resultBuffer));
        int8_t* resultPos = result;

        for (int h = 0; h < height; h++) {
            for (int w = 0; w < width; w++) {
                int32_t data = static_cast<int32_t>(*basePos) - static_cast<int32_t>(*subPos);
                int8_t writeData = static_cast<int8_t>(CLAMP(data, -128, 127));
//                if (h % 1500 == 0 && w % 200 == 0) {
//                    LOGE(LOG_ERROR, "quantizeBuffer %d -> %f -> %d", data, quantData, writeData);
//                }
                *resultPos = writeData;
                resultPos++;
                basePos++;
                subPos++;
            }
        }
    }
    LOGI(LOG_DEBUG, "nativeSubtractBuffer end");
}


template<typename T>
void mergeBuffers(JNIEnv *pEnv, jobject mergeByteBuffer,
                  jobjectArray bufferArray, jint width, jint height, jint channel) {

    T* readBuffer[kr_ac_snu_nxc_cloudcamera_library_ImageUtils_MAX_IMAGE_BUFFERS];
    T* readPos[kr_ac_snu_nxc_cloudcamera_library_ImageUtils_MAX_IMAGE_BUFFERS];

    for (int i = 0; i < channel; i++) {
        jobject byteBuffer = (jobject)pEnv->GetObjectArrayElement(bufferArray, i);

        if (byteBuffer == NULL) {
            LOGE(LOG_ERROR,"%s: byteBuffers[%d] == NULL\n",__func__, i);
            pEnv->DeleteLocalRef(byteBuffer);
            break;
        }

        LOGD(LOG_DEBUG, "Get Buffer %d", i);
        readBuffer[i] = static_cast<T*>(pEnv->GetDirectBufferAddress(byteBuffer));
        if (readBuffer[i] == NULL) {
            LOGE(LOG_ERROR,"%s: imageBuffers[%d] == NULL\n",__func__, i);
            pEnv->DeleteLocalRef(byteBuffer);
            break;
        }
        readPos[i] = readBuffer[i];
        pEnv->DeleteLocalRef(byteBuffer);
    }
    T* mergeBuffer = static_cast<T*>(pEnv->GetDirectBufferAddress(mergeByteBuffer));
    T* mergePos = mergeBuffer;

    for (int h = 0; h < height; h++) {
        for (int w = 0; w < width; w++) {
            for (int c = 0; c < channel; c++) {
                *mergePos = *readPos[c];
                mergePos++;
                readPos[c]++;
            }
        }
    }
}

JNIEXPORT void JNICALL Java_kr_ac_snu_nxc_cloudcamera_library_ImageUtils_nativeMergeBuffer
        (JNIEnv *pEnv, jclass, jobject mergeByteBuffer,
         jobjectArray bufferArray, jint width, jint height, jint channel, jint dType) {

    if (dType == DTYPE_FLOAT) {
        mergeBuffers<float>(pEnv, mergeByteBuffer, bufferArray, width, height, channel);
    } else {
        mergeBuffers<uint8_t>(pEnv, mergeByteBuffer, bufferArray, width, height, channel);
    }
}


template<typename T>
void mergeBuffersChannel(JNIEnv *pEnv, jobject mergeByteBuffer,
                  jobject byteBuffer, jint start, jint end, jint width, jint height, jint channel) {

    T* readBuffer = static_cast<T*>(pEnv->GetDirectBufferAddress(byteBuffer));
    T* readPos = readBuffer;
    T* mergeBuffer = static_cast<T*>(pEnv->GetDirectBufferAddress(mergeByteBuffer));
    T* mergePos = mergeBuffer;

    for (int h = 0; h < height; h++) {
        for (int w = 0; w < width; w++) {
            for (int c = 0; c < (end - start); c++) {
                *(mergePos + start + c) = *readPos;
                readPos++;
            }
            mergePos += channel;
        }
    }
}

JNIEXPORT void JNICALL Java_kr_ac_snu_nxc_cloudcamera_library_ImageUtils_nativeMergeBufferChannel
        (JNIEnv *pEnv, jclass, jobject mergeByteBuffer,
         jobject byteBuffer, jint start, jint end, jint width, jint height, jint channel, jint dType) {
    LOGI(LOG_DEBUG, "nativeMergeBufferChannel %d ~ %d : %d [%d][%d][%d]", start, end, width, height, channel);
    if (dType == DTYPE_FLOAT) {
        mergeBuffersChannel<float>(pEnv, mergeByteBuffer, byteBuffer, start, end, width, height, channel);
    } else {
        mergeBuffersChannel<uint8_t>(pEnv, mergeByteBuffer, byteBuffer, start, end, width, height, channel);
    }
    LOGI(LOG_DEBUG, "nativeMergeBufferChannel end");
}