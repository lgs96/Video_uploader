package kr.ac.snu.nxc.cloudcamera;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

import kr.ac.snu.nxc.cloudcamera.util.CCConstants;
import kr.ac.snu.nxc.cloudcamera.util.CCUtils;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";

    public static final int REQUEST_CAMERA = 1;
    public static final int REQUEST_STORAGE = 2;

    private Context mContext;

    private ListView mMenuListView = null;
    // List item
    private MenuAdapter mCCMenuArrayAdapter = null;
    private List<MenuItem> mCCMenuItemList = null;

    private void bindList() {
        mCCMenuItemList = new ArrayList<>();
        mCCMenuItemList.add(new MenuItem(R.drawable.menu_camera, CCConstants.ACTIVITY_CAMERA, CameraActivity.class));
        mCCMenuItemList.add(new MenuItem(R.drawable.menu_encoding, CCConstants.ACTIVITY_CODEC_GALLERY, GalleryActivity.class));
        mCCMenuItemList.add(new MenuItem(R.drawable.menu_setting, CCConstants.ACTIVITY_SETTING, SettingActivity.class));

        mCCMenuArrayAdapter = new MenuAdapter(this, mCCMenuItemList);

        mMenuListView.setAdapter(mCCMenuArrayAdapter);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mContext = getApplicationContext();
        setContentView(R.layout.activity_main);

        //ActionBar actionBar = getSupportActionBar();
        //if (actionBar != null) {
        //    actionBar.hide();
        //}
//        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        CCUtils.initPreference(mContext);
        mMenuListView = (ListView) findViewById(R.id.list_view_menu);

        int permissionCamera = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA);
        int permissionStorage = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE);
        if (permissionCamera == PackageManager.PERMISSION_DENIED || permissionStorage == PackageManager.PERMISSION_DENIED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE}, MainActivity.REQUEST_STORAGE);
        }

        bindList();

        mMenuListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                MenuItem item = mCCMenuItemList.get(position);
                Intent intent = new Intent(MainActivity.this, item.getCallClass());
                intent.putExtra(CCConstants.KEY_ACTIVITY_CODE, item.getActivityCode());
                startActivity(intent);
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        Log.d(TAG, "onRequestPermissionsResult()");
        switch (requestCode) {
            case REQUEST_CAMERA:
                for (int i = 0; i < permissions.length; i++) {
                    String permission = permissions[i];
                    int grantResult = grantResults[i];
                    if (permission.equals(Manifest.permission.CAMERA)) {
                        Log.d(TAG, "CAMERA");
                        if (grantResult != PackageManager.PERMISSION_GRANTED) {
                            Toast.makeText(this, "Should have camera permission to run", Toast.LENGTH_LONG).show();
                            finish();
                        }
                    }
                }
                break;
            case REQUEST_STORAGE:
                for (int i = 0; i < permissions.length; i++) {
                    String permission = permissions[i];
                    int grantResult = grantResults[i];
                    if (permission.equals(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                        if (grantResult != PackageManager.PERMISSION_GRANTED) {
                            Toast.makeText(this, "Should have storage permission to run", Toast.LENGTH_LONG).show();
                            finish();
                        }
                    }
                }
                break;
        }
    }

    public class MenuItem {
        private int mImageResId;
        private int mActivityCode;
        private String mName;
        private Class<?> mCallClass;

        public MenuItem(int imageResId, int activityCode, Class<?> callClass) {
            mImageResId = imageResId;
            mActivityCode = activityCode;
            mName = CCConstants.ACTIVITY_NAME[mActivityCode];
            mCallClass = callClass;
        }

        public int getImageResId() {
            return mImageResId;
        }

        public String getName() {
            return mName;
        }

        public int getActivityCode() {
            return mActivityCode;
        }

        public Class<?> getCallClass() { return mCallClass; }
    }

    class MenuAdapter extends ArrayAdapter<MenuItem> {
        private static final int LAYOUT_RESOURCE_ID = R.layout.activity_main_list_item;

        private Context mContext;
        private List<MenuItem> mMenuItemList;

        public MenuAdapter(Context context, List<MenuItem> itemList) {
            super(context, LAYOUT_RESOURCE_ID, itemList);

            mContext = context;
            mMenuItemList = itemList;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            CCMenuItemViewHolder viewHolder;
            if (convertView == null) {
                convertView = LayoutInflater.from(mContext).inflate(LAYOUT_RESOURCE_ID, parent, false);

                viewHolder = new CCMenuItemViewHolder(convertView);
                convertView.setTag(viewHolder);
            } else {
                viewHolder = (CCMenuItemViewHolder) convertView.getTag();
            }

            MenuItem menuItem = mMenuItemList.get(position);
            viewHolder.mMenuImageView.setImageResource(menuItem.getImageResId());
            viewHolder.mMenuName.setText(menuItem.getName());

            return convertView;
        }

        public class CCMenuItemViewHolder {
            public ImageView mMenuImageView;
            public TextView mMenuName;

            public CCMenuItemViewHolder(View view) {
                mMenuImageView = view.findViewById(R.id.image_view_icon);
                mMenuName = view.findViewById(R.id.text_view_name);
            }
        }
    }
}