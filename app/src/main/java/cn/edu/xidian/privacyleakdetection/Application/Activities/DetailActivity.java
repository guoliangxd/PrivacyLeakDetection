package cn.edu.xidian.privacyleakdetection.Application.Activities;

import android.app.TaskStackBuilder;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.MenuItem;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.ListView;
import android.widget.Switch;
import android.widget.TextView;

import cn.edu.xidian.privacyleakdetection.Application.Database.DataLeak;
import cn.edu.xidian.privacyleakdetection.Application.Database.DatabaseHandler;
import cn.edu.xidian.privacyleakdetection.Application.PrivacyLeakDetection;
import cn.edu.xidian.privacyleakdetection.R;

import java.util.List;

public class DetailActivity extends AppCompatActivity {

    private int notifyId;
    private String packageName;
    private String appName;
    private String category;
    private int ignore;

    private ListView list;
    private DetailListViewAdapter adapter;
    private Switch notificationSwitch;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        setContentView(R.layout.activity_detail);

        // 从Intent中获取信息
        Intent intent = getIntent();
        notifyId = intent.getIntExtra(PrivacyLeakDetection.EXTRA_ID, -1);
        packageName = intent.getStringExtra(PrivacyLeakDetection.EXTRA_PACKAGE_NAME);
        appName = intent.getStringExtra(PrivacyLeakDetection.EXTRA_APP_NAME);
        category = intent.getStringExtra(PrivacyLeakDetection.EXTRA_CATEGORY);
        ignore = intent.getIntExtra(PrivacyLeakDetection.EXTRA_IGNORE, 0);

        TextView title = (TextView) findViewById(R.id.detail_title);
        title.setText(category);
        TextView subtitle = (TextView) findViewById(R.id.detail_subtitle);
        subtitle.setText("[" + appName + "]");


        notificationSwitch = (Switch) findViewById(R.id.detail_switch);
        notificationSwitch.setChecked(ignore == 1);
        notificationSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                DatabaseHandler db = DatabaseHandler.getInstance(DetailActivity.this);
                if (isChecked) {
                    // 启用了切换
                    db.setIgnoreAppCategory(notifyId, true);
                    ignore = 1;
                } else {
                    // 关闭了切换
                    db.setIgnoreAppCategory(notifyId, false);
                    ignore = 0;
                }
            }
        });

        list = (ListView) findViewById(R.id.detail_list);
    }

    @Override
    protected void onResume() {
        super.onResume();

        updateList();
    }

    private void updateList() {
        DatabaseHandler db = DatabaseHandler.getInstance(this);
        List<DataLeak> details = db.getAppLeaks(packageName, category);

        if (details == null) {
            return;
        }

        if (adapter == null) {
            adapter = new DetailListViewAdapter(this, details);

            View header = getLayoutInflater().inflate(R.layout.listview_detail, null);
            ((TextView) header.findViewById(R.id.detail_type)).setText(R.string.type_label);
            ((TextView) header.findViewById(R.id.detail_time)).setText(R.string.time_label);
            //((TextView) header.findViewById(R.id.detail_content)).setText(R.string.content_label);
            ((TextView) header.findViewById(R.id.detail_destination)).setText(R.string.destination_label);

            list.addHeaderView(header);
            list.setAdapter(adapter);
        } else {
            adapter.updateData(details);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            // 响应action bar的返回按钮/Home键
            case android.R.id.home:
                Intent upIntent = getParentActivityIntent();
                if (shouldUpRecreateTask(upIntent)) {
                    // 这个活动不是这个应用程序任务的一部分，所以在导航时创建一个新任务，使用一个合成的返回栈。
                    TaskStackBuilder.create(this)
                            // 把所有该活动的父活动都添加到返回栈中。
                            .addNextIntentWithParentStack(upIntent)
                                    // 导航到最近的父活动。
                            .startActivities();
                } else {
                    // 这个活动是这个应用程序任务的一部分，所以简单地导航到逻辑父活动。
                    navigateUpTo(upIntent);
                }
                return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
