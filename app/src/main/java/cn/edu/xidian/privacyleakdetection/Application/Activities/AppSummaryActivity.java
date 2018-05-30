package cn.edu.xidian.privacyleakdetection.Application.Activities;

import android.app.TaskStackBuilder;
import android.content.Intent;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AppCompatActivity;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.CompoundButton;
import android.widget.ListView;
import android.widget.Switch;
import android.widget.TextView;

import cn.edu.xidian.privacyleakdetection.Application.Database.CategorySummary;
import cn.edu.xidian.privacyleakdetection.Application.Database.DatabaseHandler;
import cn.edu.xidian.privacyleakdetection.Application.PrivacyLeakDetection;
import cn.edu.xidian.privacyleakdetection.R;

import java.util.List;

public class AppSummaryActivity extends AppCompatActivity {

    private String packageName;
    private String appName;
    private int ignore;
    private ListView list;
    private SummaryListViewAdapter adapter;
    private Switch notificationSwitch;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        setContentView(R.layout.activity_app_summary);

        // 从Intent中获取信息
        Intent intent = getIntent();
        packageName= intent.getStringExtra(PrivacyLeakDetection.EXTRA_PACKAGE_NAME);
        appName = intent.getStringExtra(PrivacyLeakDetection.EXTRA_APP_NAME);
        ignore = intent.getIntExtra(PrivacyLeakDetection.EXTRA_IGNORE,0);

        TextView title = (TextView) findViewById(R.id.summary_title);
        title.setText(appName);
        TextView subtitle = (TextView) findViewById(R.id.summary_subtitle);
        subtitle.setText("[" + packageName + "]");

        notificationSwitch = (Switch) findViewById(R.id.summary_switch);
        notificationSwitch.setChecked(ignore == 1);
        notificationSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                DatabaseHandler db = DatabaseHandler.getInstance(AppSummaryActivity.this);
                if (isChecked) {
                    // 启用了切换
                    db.setIgnoreApp(packageName, true);
                    ignore = 1;
                } else {
                    // 关闭了切换
                    db.setIgnoreApp(packageName, false);
                    ignore = 0;
                }
            }
        });

        list = (ListView) findViewById(R.id.summary_list);
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                CategorySummary category = (CategorySummary) parent.getItemAtPosition(position);
                Intent intent;
                if(category.category.equalsIgnoreCase("location")){
                    intent = new Intent(AppSummaryActivity.this, LocationDetailActivity.class);
                }else{
                    intent = new Intent(AppSummaryActivity.this, DetailActivity.class);
                }

                intent.putExtra(PrivacyLeakDetection.EXTRA_ID, category.notifyId);
                intent.putExtra(PrivacyLeakDetection.EXTRA_PACKAGE_NAME, packageName);
                intent.putExtra(PrivacyLeakDetection.EXTRA_APP_NAME, appName);
                intent.putExtra(PrivacyLeakDetection.EXTRA_CATEGORY, category.category);
                intent.putExtra(PrivacyLeakDetection.EXTRA_IGNORE, category.ignore);

                startActivity(intent);
            }
        });

        FloatingActionButton viewStats = (FloatingActionButton)findViewById(R.id.stats_button);
        viewStats.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(getApplicationContext(), AppDataActivity.class);
                i.putExtra(AppDataActivity.APP_NAME_INTENT, appName);
                i.putExtra(AppDataActivity.APP_PACKAGE_INTENT, packageName);
                startActivity(i);
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();

        updateList();
    }

    private void updateList(){
        DatabaseHandler db = DatabaseHandler.getInstance(this);
        List<CategorySummary> details = db.getAppDetail(packageName);

        if (details == null) {
            return;
        }
        if (adapter == null) {
            adapter = new SummaryListViewAdapter(this, details);
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
                            // 把所有该活动的父活动都添加到返回栈中
                            .addNextIntentWithParentStack(upIntent)
                                    // Navigate up to the closest parent
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
