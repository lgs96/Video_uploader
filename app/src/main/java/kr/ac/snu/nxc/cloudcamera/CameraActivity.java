package kr.ac.snu.nxc.cloudcamera;

import android.Manifest;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.animation.Animation;
import android.view.animation.LinearInterpolator;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.snackbar.Snackbar;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;

import kr.ac.snu.nxc.cloudcamera.customview.AutoFitTextureView;
import kr.ac.snu.nxc.cloudcamera.device.CameraOps;
import kr.ac.snu.nxc.cloudcamera.device.PictureCallbackInterface;
import kr.ac.snu.nxc.cloudcamera.library.ImageUtils;
import kr.ac.snu.nxc.cloudcamera.util.CCConstants;
import kr.ac.snu.nxc.cloudcamera.util.CCImage;
import kr.ac.snu.nxc.cloudcamera.util.CCLog;
import kr.ac.snu.nxc.cloudcamera.util.CCPreferences;
import kr.ac.snu.nxc.cloudcamera.util.CCUtils;

import static android.media.ExifInterface.ORIENTATION_ROTATE_270;
import static android.media.ExifInterface.ORIENTATION_ROTATE_90;
import static kr.ac.snu.nxc.cloudcamera.util.CCConstants.MAX_YUV_SHOT_FRAME_COUNT;
import static kr.ac.snu.nxc.cloudcamera.util.CCConstants.ShutterAction;
import static kr.ac.snu.nxc.cloudcamera.util.CCConstants.ShutterAction.FULL_FRAME_DUMP;
import static kr.ac.snu.nxc.cloudcamera.util.CCConstants.ShutterAction.JPG_SHOT;
import static kr.ac.snu.nxc.cloudcamera.util.CCConstants.ShutterAction.PREVIEW_DUMP;
import static kr.ac.snu.nxc.cloudcamera.util.CCConstants.ShutterAction.YUV_SHOT;

public class CameraActivity extends AppCompatActivity implements PictureCallbackInterface {
    private static final String TAG = "CameraActivity";

    private Object mLock = new Object();

    private Context mContext = null;
    private CameraOps mCameraOps = null;

    private ImageView mImageViewSetting = null;

    private AutoFitTextureView mCameraTextureView = null;

    private ImageView mImageViewCaptureCountPlus = null;
    private ImageView mImageViewCaptureCountMinus = null;
    private TextView mTextViewCaptureCount = null;

    private ImageView mImageViewThumbnail = null;
    private ImageView mImageViewShutter = null;

    private LinearLayout mLayoutBottomSheet = null;
    private LinearLayout mLayoutGesture = null;
    private BottomSheetBehavior<LinearLayout> mSheetBehavior = null;
    protected ImageView mImageViewBottomSheetArrow = null;

    private TextView mTextViewPreviewResolution = null;
    private TextView mTextViewShotResolution = null;

    private RadioGroup mRadioGroupShutterAction = null;
    private RadioButton mRadioButtonJpgShot = null;
    private RadioButton mRadioButtonYuvShot = null;
    private RadioButton mRadioButtonPreviewDump = null;
    private RadioButton mRadioButtonFullFrameDump = null;


    ShutterAction mShutterAction = JPG_SHOT;

    private static final int CS_JPG_QUALITY = 15;

    boolean mEnabledCloudShotDebug = false;
    boolean mEnabledCloudShot = false;
    boolean mEnabledHdr = false;
    boolean mRunningDump = false;
    boolean mShotFinish = true;

    private int mCaptureCount = 1;

    Bitmap mThumbnailBitmap = null;
    ObjectAnimator mShutterAnimator = null;

    private int mPreviewWidth = 1920;
    private int mPreviewHeight = 1080;

    Handler mThumbnailUpdateHandler = new Handler(new Handler.Callback() {
        @Override
        public boolean handleMessage(Message msg) {
            mImageViewThumbnail.setImageBitmap(mThumbnailBitmap);
            return true;
        }
    });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mContext = this;
        setContentView(R.layout.activity_camera);

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.hide();
        }
//        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);

        mImageViewSetting = (ImageView) findViewById(R.id.image_view_setting);

        mCameraTextureView = (AutoFitTextureView) findViewById(R.id.texture_view_camera);

        mImageViewThumbnail = (ImageView) findViewById(R.id.image_view_thumbnail);
        mImageViewShutter = (ImageView) findViewById(R.id.image_view_shutter);

        mShutterAnimator = ObjectAnimator.ofFloat(mImageViewShutter, View.ROTATION, 0f, 360f).setDuration(2000);
        mShutterAnimator.setRepeatCount(Animation.INFINITE);
        mShutterAnimator.setInterpolator(new LinearInterpolator());

        mLayoutBottomSheet = findViewById(R.id.bottom_sheet_layout);
        mLayoutGesture = findViewById(R.id.layout_gesture);
        mSheetBehavior = BottomSheetBehavior.from(mLayoutBottomSheet);
        mImageViewBottomSheetArrow = findViewById(R.id.image_view_bottom_sheet_arrow);

        mTextViewPreviewResolution = (TextView) findViewById(R.id.text_view_preview_resolution);
        mTextViewShotResolution = (TextView) findViewById(R.id.text_view_shot_resolution);

        mRadioGroupShutterAction = (RadioGroup) findViewById(R.id.radio_group_shutter_action);
        mRadioButtonPreviewDump = (RadioButton) findViewById(R.id.radio_button_preview);
        mRadioButtonFullFrameDump = (RadioButton) findViewById(R.id.radio_button_fullframe);
        mRadioButtonYuvShot = (RadioButton) findViewById(R.id.radio_button_yuv_shot);
        mRadioButtonJpgShot = (RadioButton) findViewById(R.id.radio_button_jpg_shot);

        mImageViewCaptureCountPlus = (ImageView) findViewById(R.id.image_view_capture_count_plus);
        mImageViewCaptureCountMinus = (ImageView) findViewById(R.id.image_view_capture_count_minus);
        mTextViewCaptureCount = (TextView) findViewById(R.id.text_view_capture_count);

        mShutterAction = ShutterAction.getValue(CCPreferences.getInt(CCConstants.KEY_SHUTTER_ACTION));
        if (mShutterAction == JPG_SHOT) {
            mRadioButtonJpgShot.setChecked(true);
        } else if (mShutterAction == YUV_SHOT) {
            mRadioButtonYuvShot.setChecked(true);
        } else if (mShutterAction == PREVIEW_DUMP) {
            mRadioButtonPreviewDump.setChecked(true);
        } else if (mShutterAction == FULL_FRAME_DUMP) {
            mRadioButtonFullFrameDump.setChecked(true);
        }

        
        mEnabledHdr = CCPreferences.getBoolean(CCConstants.KEY_SHOT_MODE_HDR);
        mEnabledCloudShot = CCPreferences.getBoolean(CCConstants.KEY_CLOUD_SHOT);

        mCameraOps = new CameraOps(this, mCameraTextureView, mShutterAction, mEnabledCloudShot, mEnabledHdr);
        mCameraOps.setOnPictureCallbackListener(this);

        mCaptureCount = CCPreferences.getInt(CCConstants.KEY_YUV_SHOT_CAPTURE_COUNT);
        setCaptureCount(mCaptureCount);

        int permissionCamera = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA);
        int permissionStorage = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE);
        if (permissionCamera == PackageManager.PERMISSION_DENIED || permissionStorage == PackageManager.PERMISSION_DENIED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE}, MainActivity.REQUEST_STORAGE);
        }

        Size previewSize = CCUtils.getPreviewOptimalSize(mContext, CCConstants.DEFAULT_PREVIEW_SIZE);
        mPreviewWidth = previewSize.getWidth();
        mPreviewHeight = previewSize.getHeight();
        mTextViewPreviewResolution.setText(mPreviewWidth + "x" + mPreviewHeight);

        mImageViewSetting.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(CameraActivity.this, SettingActivity.class);
                startActivity(intent);
            }
        });

        mRadioGroupShutterAction.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                if (mRadioButtonJpgShot.isChecked()) {
                    mShutterAction = JPG_SHOT;
                } else if (mRadioButtonYuvShot.isChecked()) {
                    mShutterAction = YUV_SHOT;
                } else if (mRadioButtonPreviewDump.isChecked()) {
                    mShutterAction = PREVIEW_DUMP;
                } else if (mRadioButtonFullFrameDump.isChecked()) {
                    mShutterAction = FULL_FRAME_DUMP;
                }
                CCLog.d(TAG, "Shutter Action = " + mShutterAction);
                CCPreferences.setInt(CCConstants.KEY_SHUTTER_ACTION, mShutterAction.getID());
                changeConfiguration();
            }
        });

        mImageViewCaptureCountPlus.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mShutterAction == YUV_SHOT) {
                    setCaptureCount(mCaptureCount + 1);
                }
            }
        });

        mImageViewCaptureCountMinus.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mShutterAction == YUV_SHOT) {
                    setCaptureCount(mCaptureCount - 1);
                }
            }
        });

        mImageViewShutter.setClickable(true);
        mImageViewShutter.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                synchronized (mLock) {
                    CCLog.d(TAG, "ShutterAction : " + mShutterAction);
                    if (!mShotFinish) {
                        CCLog.w(TAG, "Not ready for shot");
                        return;
                    }
                    if (mShutterAction == JPG_SHOT || mShutterAction == YUV_SHOT) {
                        if (mShutterAnimator.isPaused()) {
                            mShutterAnimator.resume();
                        } else {
                            mShutterAnimator.start();
                        }

                        mShotFinish = false;
                        mCameraOps.takePicture();
                    } else {
                        if (!mRunningDump) {
                            checkDump(true);
                        } else {
                            checkDump(false);
                        }
                    }
                }
            }
        });

        ViewTreeObserver vto = mLayoutGesture.getViewTreeObserver();
        vto.addOnGlobalLayoutListener(
            new ViewTreeObserver.OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout() {
                    mLayoutGesture.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                    mSheetBehavior.setPeekHeight(mLayoutGesture.getMeasuredHeight());
                }
            }
        );
        mSheetBehavior.setHideable(false);

        mSheetBehavior.addBottomSheetCallback(
            new BottomSheetBehavior.BottomSheetCallback() {
                @Override
                public void onStateChanged(@NonNull View bottomSheet, int newState) {
                    switch (newState) {
                        case BottomSheetBehavior.STATE_HIDDEN:
                            break;
                        case BottomSheetBehavior.STATE_EXPANDED: {
                            mImageViewBottomSheetArrow.setImageResource(R.drawable.bottom_sheet_down);
                        }
                        break;
                        case BottomSheetBehavior.STATE_COLLAPSED: {
                            mImageViewBottomSheetArrow.setImageResource(R.drawable.bottom_sheet_up);
                        }
                        break;
                        case BottomSheetBehavior.STATE_DRAGGING:
                            break;
                        case BottomSheetBehavior.STATE_SETTLING:
                            mImageViewBottomSheetArrow.setImageResource(R.drawable.bottom_sheet_up);
                            break;
                    }
                }

                @Override
                public void onSlide(@NonNull View bottomSheet, float slideOffset) {
                }
            }
        );
    }

    private TextureView.SurfaceTextureListener mSurfaceTextureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            CCLog.i(TAG, "onSurfaceTextureAvailable, width = " + width + ", height = " + height);
            openCamera(width, height);
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
            CCLog.i(TAG, "onSurfaceTextureSizeChanged width =" + width + ", height =" + height);
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            return false;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {
        }
    };

    private void configureTransform(final int viewWidth, final int viewHeight) {
        if (null == mCameraTextureView ) {
            return;
        }
        CCLog.d(TAG, "configureTransform");

        final int rotation = getWindowManager().getDefaultDisplay().getRotation();
        final Matrix matrix = new Matrix();
        final RectF viewRect = new RectF(0, 0, viewWidth, viewHeight);
        final RectF bufferRect = new RectF(0, 0, mPreviewHeight, mPreviewWidth);
        final float centerX = viewRect.centerX();
        final float centerY = viewRect.centerY();
        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
            final float scale = Math.max((float) viewHeight / mPreviewHeight, (float) viewWidth / mPreviewWidth);
            matrix.postScale(scale, scale, centerX, centerY);
            matrix.postRotate(90 * (rotation - 2), centerX, centerY);
        } else if (Surface.ROTATION_180 == rotation) {
            matrix.postRotate(180, centerX, centerY);
        }
        mCameraTextureView.setTransform(matrix);
    }

    private void openCamera(int textureWidth, int textureHeight) {
        if (mCameraTextureView.isAvailable()) {
            mCameraTextureView.setAspectRatio(mPreviewHeight, mPreviewWidth);
            configureTransform(textureWidth, textureHeight);
            mCameraOps.openCamera(mPreviewWidth, mPreviewHeight);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        CCLog.d(TAG, "onResume");

        if (mCameraTextureView.isAvailable()) {
            openCamera(mCameraTextureView.getWidth(), mCameraTextureView.getHeight());
        } else {
            mCameraTextureView.setSurfaceTextureListener(mSurfaceTextureListener);
        }
        mTextViewShotResolution.setText(CCUtils.getRequestFullFrame());
    }

    @Override
    protected void onPause() {
        if (mRunningDump) {
            checkDump(false);
        }

        mCameraOps.closeCamera(false);
        super.onPause();
    }

    private void checkDump(boolean runningDump) {
        CCLog.d(TAG, "mRunningDump : " + mRunningDump + " checkedDump : " + runningDump);
        if (mRunningDump && !runningDump) {
            CCLog.d(TAG, "Dump stop");
            mRunningDump = false;
            String dumpPath = mCameraOps.getDumpPath();
            mCameraOps.stopDump();

            mShutterAnimator.pause();
            Toast.makeText(mContext, "Dump stop " + dumpPath, Toast.LENGTH_SHORT).show();
        } else if (mShutterAction == PREVIEW_DUMP || mShutterAction == FULL_FRAME_DUMP) {
            if (!mRunningDump && runningDump) {
                CCLog.d(TAG, "Dump start");
                String dumpPath = null;
                if (mShutterAction == PREVIEW_DUMP) {
                    dumpPath = CCUtils.getDumpPath(CCConstants.PREVIEW_DIR);
                } else {
                    dumpPath = CCUtils.getDumpPath(CCConstants.PREVIEW_FULL_DIR);
                }
                mRunningDump = true;
                if (mShutterAnimator.isPaused()) {
                    mShutterAnimator.resume();
                } else {
                    mShutterAnimator.start();
                }

//                Toast.makeText(mContext, "Dump Start " + dumpPath, Toast.LENGTH_SHORT).show();
                Toast.makeText(mContext, "Touch shutter to stop encoding", Toast.LENGTH_SHORT).show();
                mCameraOps.startDump(dumpPath);
            }
        } else {
            if (runningDump) {
                CCLog.d(TAG, "Dump Invalid");
                mRunningDump = false;
                Toast.makeText(mContext, "Need Preview or PreviewFullFramae checked", Toast.LENGTH_SHORT).show();
            }
        }
    }


    private void setCaptureCount(int captureCount) {
        mCaptureCount = captureCount;
        if (mCaptureCount > MAX_YUV_SHOT_FRAME_COUNT) {
            mCaptureCount = MAX_YUV_SHOT_FRAME_COUNT;
        }
        if (mCaptureCount < 1) {
            mCaptureCount = 1;
        }
        mTextViewCaptureCount.setText(String.valueOf(mCaptureCount));
        CCPreferences.setInt(CCConstants.KEY_YUV_SHOT_CAPTURE_COUNT, mCaptureCount);
        mCameraOps.setCaptureCount(mCaptureCount);
    }

    public void changeConfiguration() {
        checkDump(false);

        mCameraOps.changeConfiguration(mShutterAction, mCaptureCount);
    }

    @Override
    public void onCaptureCallback(byte[] jpegBytes, int orientation, boolean needExifUpdate) {
        synchronized (mLock) {
            String fileName = CCUtils.getDateString();
            CCLog.d(TAG, "orientation : " + orientation + " NeedExifUpdate : " + needExifUpdate);
            Bitmap bitmap = CCUtils.readJpeg(jpegBytes, CCConstants.THUMBNAIL_RATIO, false);
            mThumbnailBitmap = CCUtils.rotateBitmap(bitmap, orientation);
            mThumbnailUpdateHandler.sendEmptyMessage(0);
            saveFile(jpegBytes, fileName, orientation, needExifUpdate);
            mShotFinish = true;
            mShutterAnimator.pause();
        }
    }

    @Override
    public void onCaptureFail() {
        CCLog.e(TAG, "onCaptureFail isShotFinish : " + mShotFinish);
        synchronized (mLock) {
            if (mShotFinish == false) {
                Toast.makeText(mContext, "Capture Fail, Retry", Toast.LENGTH_SHORT).show();
                mShotFinish = true;
                mShutterAnimator.pause();
            }
        }
    }

    @Override
    public void onPreviewCallback(CCImage ccImage) {

    }

    private void saveFile(byte[] jpegBytes, String fileName, int orientation, boolean needExifUpdate) {
        String dirPath = CCUtils.getPath(CCConstants.JPG_DIR);
        String fileNameExt =  fileName + ".jpg";

        final File file = new File(dirPath, fileNameExt);
        OutputStream output = null;
        try {
            output = new FileOutputStream(file);
            output.write(jpegBytes);
            output.close();

            if (needExifUpdate) {
                CCLog.d(TAG, "Update Orientation : " + orientation);
                CCUtils.writeExifInfo(CCUtils.openExif(file.getPath()), orientation);
            }

            registerToMediaScanner(file);
            if (mShutterAction == YUV_SHOT) {
                Toast.makeText(mContext, "Saved " + CCUtils.getPath(CCConstants.YUV_DIR), Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(mContext, "Saved " + dirPath, Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(mContext, "Failed", Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        }
    }

    private void registerToMediaScanner(File newFile) {
        MediaScannerConnection.scanFile(this, new String[] {newFile.getPath()},
            new String[] { "image/jpg" },
            new MediaScannerConnection.OnScanCompletedListener() {
                @Override
                public void onScanCompleted(String path, Uri uri) {
                }
            }
        );
    }
}