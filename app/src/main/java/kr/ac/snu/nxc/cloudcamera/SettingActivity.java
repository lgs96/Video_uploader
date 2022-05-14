package kr.ac.snu.nxc.cloudcamera;

import android.content.Context;
import android.content.Intent;
import android.graphics.ImageFormat;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import java.util.ArrayList;

import kr.ac.snu.nxc.cloudcamera.util.CCConstants;
import kr.ac.snu.nxc.cloudcamera.util.CCUtils;

import static kr.ac.snu.nxc.cloudcamera.util.CCConstants.ACTIVITY_SETTING;

public class SettingActivity extends AppCompatActivity {
    private static final String TAG = "SettingActivity";

    ArrayList<String> mFullFrameSizeList = null;

    private Context mContext = null;
    ListView mListView = null;
    Toolbar mToolbar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mContext = getApplicationContext();
        setContentView(R.layout.activity_setting);

//        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);

        Intent intent = getIntent();
        ActionBar actionBar = getSupportActionBar();
        actionBar.setTitle(CCConstants.ACTIVITY_NAME[ACTIVITY_SETTING]);
        actionBar.setDisplayHomeAsUpEnabled(true);

        mListView = (ListView) findViewById(R.id.list_view_setting);
        mFullFrameSizeList = CCUtils.getStreamConfigurationList(mContext, ImageFormat.YUV_420_888);
        mListView.setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, mFullFrameSizeList));

        mListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                String clickText = mListView.getItemAtPosition(position).toString();
                CCUtils.setRequestFullFrame(clickText);
                finish();
            }
        });
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
}