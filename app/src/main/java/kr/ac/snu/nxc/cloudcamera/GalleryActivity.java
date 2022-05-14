package kr.ac.snu.nxc.cloudcamera;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.media.ThumbnailUtils;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.provider.MediaStore;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

import kr.ac.snu.nxc.cloudcamera.util.CCConstants;
import kr.ac.snu.nxc.cloudcamera.util.CCLog;
import kr.ac.snu.nxc.cloudcamera.util.CCUtils;

import static kr.ac.snu.nxc.cloudcamera.util.CCConstants.CCVIDEO_PATH;

public class GalleryActivity extends AppCompatActivity {
    private static final String TAG = "GalleryActivity";
    private static final int MAX_VIDEO_THUMBNAIL_LOADER_THREAD = 4;
    private static final int MSG_UPDATE_DONE = -1;

    public static final int REQUEST_STORAGE = 1;

    private Context mContext = null;
    private Object mLock = new Object();

    ArrayList<ThumbnailItem> mThumbnailItemList;
    GridView mGridView;
    GalleryAdapter mAdapter = null;

    HandlerThread[] mThumbnailLoaderThreads = null;
    Handler[] mImageLoadHandlers = null;
    int mTotalUpdateImage = 0;
    int mUpdateImageCount = 0;

    Handler mImageUpdateHandler = new Handler(new Handler.Callback() {
        @Override
        public boolean handleMessage(Message msg) {
            if (MSG_UPDATE_DONE == msg.what) {
                finishLoadImage(true);
            } else {
                if (msg.what % 4 == 0) {
                    mAdapter.notifyDataSetChanged();
                }
            }
            return true;
        }
    });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mContext = getApplicationContext();
        setContentView(R.layout.activity_gallery);

//        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);

        Intent intent = getIntent();
        ActionBar actionBar = getSupportActionBar();
        actionBar.setTitle("Gallery");
        actionBar.setDisplayHomeAsUpEnabled(true);
//
//        int permissionStorage = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE);
//        if (permissionStorage == PackageManager.PERMISSION_DENIED) {
//            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, GalleryActivity.REQUEST_STORAGE);
//        }

        mThumbnailItemList = new ArrayList<ThumbnailItem>();

        mGridView = (GridView) findViewById(R.id.grid_view);
        mAdapter = new GalleryAdapter(mContext, mThumbnailItemList);
        mGridView.setAdapter(mAdapter);

        mGridView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
                ThumbnailItem imageItem = (ThumbnailItem)parent.getItemAtPosition(position);
                CCLog.d(TAG, "Dir Name : " + imageItem.mVideoPath);

                Intent intent = new Intent(GalleryActivity.this, CodecActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                intent.putExtra(CCConstants.KEY_VIDEO_PATH, imageItem.mVideoPath);
                startActivityIfNeeded(intent, 0);
            }
        });

        mGridView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, final int position, long id) {
                ThumbnailItem imageItem = mThumbnailItemList.get(position);
                CCLog.d(TAG, "Delete Dir Name : " + imageItem.mVideoPath);

                AlertDialog.Builder builder = new AlertDialog.Builder(GalleryActivity.this);

                builder.setTitle("Delete video").setMessage(imageItem.mVideoPath);

                builder.setPositiveButton("OK", new DialogInterface.OnClickListener(){
                    @Override
                    public void onClick(DialogInterface dialog, int id) {
                        File videoFile = new File(imageItem.mVideoPath);
                        if (videoFile.exists()) {
                            videoFile.delete();
                        }
                    }
                });

                builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener(){
                    @Override
                    public void onClick(DialogInterface dialog, int id) {
                    }
                });

                AlertDialog alertDialog = builder.create();
                alertDialog.show();

                return true;
            }
        });
    }


//    @Override
//    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
//        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
//        Log.d(TAG, "onRequestPermissionsResult()");
//        switch (requestCode) {
//            case REQUEST_STORAGE:
//                for (int i = 0; i < permissions.length; i++) {
//                    String permission = permissions[i];
//                    int grantResult = grantResults[i];
//                    if (permission.equals(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
//                        if (grantResult != PackageManager.PERMISSION_GRANTED) {
//                            Toast.makeText(this, "Should have storage permission to run", Toast.LENGTH_LONG).show();
//                            finish();
//                        }
//                    }
//                }
//                break;
//        }
//    }

    @Override
    public void onResume() {
        super.onResume();
        CCLog.d(TAG,"onResume");
        readVideoFiles();
    }

    @Override
    public void onPause() {
        finishLoadImage(false);
        super.onPause();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()){
            case android.R.id.home:
                onBackPressed();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void readVideoFiles() {
        CCLog.i(TAG, "readVideoFiles()");
        try {
            ArrayList<String> videoList = new ArrayList<String>();

            File videoDir = new File(CCVIDEO_PATH);
            File[] videoFiles = videoDir.listFiles();
            CCLog.i(TAG, "Load file : " + CCVIDEO_PATH + " " + videoFiles.length + " files");

            for (int i = 0; videoFiles != null && i < videoFiles.length; i++) {
                CCLog.d(TAG, "File : " + videoFiles[i].getName());
                if (videoFiles[i].getName().contains(".mp4") || videoFiles[i].getName().contains(".MP4")) {
                    videoList.add(videoFiles[i].getPath());
                    CCLog.d(TAG, "Add File : " + videoFiles[i].getName());
                }
            }

            
            if (!videoDir.exists() || videoFiles == null || videoFiles.length == 0 || videoList.size() == 0) {
                Toast.makeText(mContext, "Push mp4 video files. /sdcard/CCVideo/", Toast.LENGTH_SHORT).show();
                return;
            }

            Collections.sort(videoList, new Comparator<String>() {
                @Override
                public int compare(String lhs, String rhs) {
                    return rhs.compareTo(lhs);
                }
            });
            loadImage(videoList);
        } catch ( Exception e ) {
            e.printStackTrace();
        }
    }

    private void loadImage(ArrayList<String> videoList) {
        if (videoList.size() != mThumbnailItemList.size()) {
//            CCLog.d(TAG, "updateImage : " + fileList.size());
            int i;
            mThumbnailItemList.clear();

            mThumbnailLoaderThreads = new HandlerThread[MAX_VIDEO_THUMBNAIL_LOADER_THREAD];
            mImageLoadHandlers = new Handler[MAX_VIDEO_THUMBNAIL_LOADER_THREAD];
            for (i = 0; i < MAX_VIDEO_THUMBNAIL_LOADER_THREAD; i++) {
                mThumbnailLoaderThreads[i] = new HandlerThread(("ThumbnailLoader" + i));
                mThumbnailLoaderThreads[i].start();
                mImageLoadHandlers[i] = new Handler(mThumbnailLoaderThreads[i].getLooper());
            }

            mTotalUpdateImage = videoList.size();
            mUpdateImageCount = 0;

            for (i = 0; i < videoList.size(); i++) {
                int targetHandler = i % 4;
                String videoPath = videoList.get(i);
                ThumbnailItem imageItem = new ThumbnailItem(videoPath);

//                CCLog.d(TAG, "Add Image : " + (fileName));
                mThumbnailItemList.add(imageItem);
                mImageLoadHandlers[targetHandler].post(new ImageLoader(i, imageItem));
            }
            mAdapter.notifyDataSetChanged();
        }
    }

    private void finishLoadImage(boolean isLoadFinish) {
        if (isLoadFinish) {
            CCLog.d(TAG, "notifyDataSetChanged");
            mAdapter.notifyDataSetChanged();
        }

        synchronized (mLock) {
            if (mImageLoadHandlers != null && mThumbnailLoaderThreads != null) {
                CCLog.d(TAG, "finishReadImage");
                for (int i = 0; i < MAX_VIDEO_THUMBNAIL_LOADER_THREAD; i++) {
                    mImageLoadHandlers[i].removeCallbacksAndMessages(null);
                    mImageLoadHandlers[i] = null;
                    mThumbnailLoaderThreads[i].quitSafely();
                    mThumbnailLoaderThreads[i] = null;
                }
                mImageLoadHandlers = null;
                mThumbnailLoaderThreads = null;
            }
        }
    }

    public class ImageLoader implements Runnable {
        int mIndex = 0;
        ThumbnailItem mThumbnailItem = null;

        public ImageLoader(int index, ThumbnailItem imageItem) {
            mIndex = index;
            mThumbnailItem = imageItem;
        }

        @Override
        public void run() {
            CCLog.d(TAG, mThumbnailItem.mVideoPath);
            mThumbnailItem.mBitmap = CCUtils.readThumbnail(mThumbnailItem.mVideoPath, 180, 240);

            mImageUpdateHandler.sendEmptyMessage(mIndex);
            synchronized (mLock) {
                mUpdateImageCount++;
                if (mUpdateImageCount == mTotalUpdateImage) {
                    mImageUpdateHandler.sendEmptyMessage(MSG_UPDATE_DONE);
                }
            }
        }
    }

    class ThumbnailItem {
        public String mVideoPath = null;
        public Bitmap mBitmap = null;

        public ThumbnailItem(String videoPath) {
            mVideoPath = videoPath;
            mBitmap = null;
        }
    }

    class GalleryAdapter extends ArrayAdapter<ThumbnailItem> {
        LayoutInflater mInflater;

        public GalleryAdapter(Context context, ArrayList<ThumbnailItem> items) {
            super(context, R.layout.activity_gallery_grid_item, items);
            mInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        }

        @Override
        public int getCount() {
            return mThumbnailItemList.size();
        }

        @Override
        public ThumbnailItem getItem(int position) {
            return mThumbnailItemList.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(final int position, View convertView, ViewGroup parent) {
            ViewHolder viewHolder;

            if (convertView == null) {
                convertView = mInflater.inflate(R.layout.activity_gallery_grid_item, parent, false);

                viewHolder = new ViewHolder();
                viewHolder.mImageView = (ImageView) convertView.findViewById(R.id.image_grid_item);
                convertView.setTag(viewHolder);
            } else {
                viewHolder = (ViewHolder) convertView.getTag();
            }
            viewHolder.mImageView.setImageBitmap(mThumbnailItemList.get(position).mBitmap);

            return convertView;
        }

        class ViewHolder {
            public ImageView mImageView;
        }
    }
}