package cn.edu.xidian.privacyleakdetection.activity;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

import cn.edu.xidian.privacyleakdetection.R;
import cn.edu.xidian.privacyleakdetection.adapter.AppPermissionAdapter;
import cn.edu.xidian.privacyleakdetection.model.AppPermission;

public class PermissonActivity extends AppCompatActivity {

    private static final String TAG = "PermissonActivity";

    private List<AppPermission> appList = new ArrayList<AppPermission>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_permisson);
        initAppList();
        RecyclerView recyclerView = (RecyclerView) findViewById(R.id.permisson_rlv);
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        recyclerView.setLayoutManager(layoutManager);
        AppPermissionAdapter appPermissionAdapter = new AppPermissionAdapter(appList);
        recyclerView.setAdapter(appPermissionAdapter);
    }

    /*
    初始化权限列表
     */
    private void initAppList(){
        PackageManager pm;
        pm = this.getPackageManager();
        //获取系统安装的所有应用程序的PackageInfo;
        List<PackageInfo> apps = pm.getInstalledPackages(PackageManager.GET_PERMISSIONS);
        //Toast.makeText(PermissonActivity.this,"得到数组的长度为"+apps.size(),Toast.LENGTH_LONG).show();
        for(int i = 0; i < apps.size();i++){
            PackageInfo pi = apps.get(i);
            AppPermission ap = new AppPermission();
            ap.setAppName(pi.applicationInfo.loadLabel(pm).toString());
            ap.setIcon(pi.applicationInfo.loadIcon(pm));
            if(null != pi.requestedPermissions){
                ap.setTotalPermission(pi.requestedPermissions.length);
                //ap.setPermission(pi.requestedPermissions);
           }
            else
            {
                //ap.setPermission(new String[]{"kong"});
                ap.setTotalPermission(0);
            }

            ap.setPrivacyPermission(ap.getTotalPermission());

           // Log.d(TAG, "获得到的权限列表为："+ap.getPermission()[0]);
            appList.add(ap);
        }
        //Toast.makeText(PermissonActivity.this,"初始化后的数组长度为："+appList.size(),Toast.LENGTH_SHORT).show();
    }
}
