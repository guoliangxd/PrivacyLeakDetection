/*
 * Main activity
 * Copyright (C) 2014  Yihang Song

 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.

 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.

 * You should have received a copy of the GNU General Public License along
 * with this program; if not, write to the Free Software Foundation, Inc.,
 * 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 */

package cn.edu.xidian.privacyleakdetection.Application.Activities;

import android.Manifest;
import android.annotation.TargetApi;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.net.VpnService;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.provider.Settings;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ListView;

import cn.edu.xidian.privacyleakdetection.Application.Database.AppSummary;
import cn.edu.xidian.privacyleakdetection.Application.Database.DatabaseHandler;
import cn.edu.xidian.privacyleakdetection.Application.Helpers.ActivityRequestCodes;
import cn.edu.xidian.privacyleakdetection.Application.Helpers.PermissionsHelper;
import cn.edu.xidian.privacyleakdetection.Application.Helpers.PreferenceHelper;
import cn.edu.xidian.privacyleakdetection.Application.Logger;
import cn.edu.xidian.privacyleakdetection.Application.Network.FakeVPN.FakeVpnService;
import cn.edu.xidian.privacyleakdetection.Application.Network.FakeVPN.FakeVpnService.MyVpnServiceBinder;
import cn.edu.xidian.privacyleakdetection.Application.PrivacyLeakDetection;
import cn.edu.xidian.privacyleakdetection.Utilities.CertificateManager;
import cn.edu.xidian.privacyleakdetection.R;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

@TargetApi(22)
public class MainActivity extends AppCompatActivity {

    private static String TAG = "MainActivity";

    private static float DISABLED_ALPHA = 0.3f;

    private ListView listLeak;
    private MainListViewAdapter adapter;

    private View permissionDisabledView;
    private View applicationPermissionDisabledView;
    private View usageStatsPermissionDisabledView;

    private View mainLayout;
    private View onIndicator;
    private View offIndicator;
    private View loadingIndicator;
    private FloatingActionButton vpnToggle;
    private FloatingActionButton statsButton;

    private boolean bounded = false;
    //private boolean keyChainInstalled = false;
    ServiceConnection mSc;
    FakeVpnService mVPN;

    // 当VPN开始运行时，删除加载视图，以便用户可以继续与应用程序交互。
    private class ReceiveMessages extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            long difference = System.currentTimeMillis() - loadingViewShownTime;

            // 加载视图应该至少显示2秒，以防止加载视图过快出现和消失。
            final Handler handler = new Handler();
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    showIndicator(Status.VPN_ON);
                }
            }, Math.max(2000 - difference, 0));
        }
    }

    private ReceiveMessages myReceiver = null;
    private boolean myReceiverIsRegistered = false;
    private long loadingViewShownTime = 0;

    /**
     * 当活动第一次创建时调用。
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        myReceiver = new ReceiveMessages();

        permissionDisabledView = findViewById(R.id.permission_disabled_message);
        applicationPermissionDisabledView = findViewById(R.id.application_settings_view);
        usageStatsPermissionDisabledView = findViewById(R.id.usage_status_settings_view);

        mainLayout = findViewById(R.id.main_layout);
        onIndicator = findViewById(R.id.on_indicator);
        offIndicator = findViewById(R.id.off_indicator);
        loadingIndicator = findViewById(R.id.loading_indicator);
        listLeak = (ListView)findViewById(R.id.leaksList);
        vpnToggle = (FloatingActionButton)findViewById(R.id.on_off_button);

        CertificateManager.initiateFactory(FakeVpnService.CADir, FakeVpnService.CAName, FakeVpnService.CertName, FakeVpnService.KeyType, FakeVpnService.Password.toCharArray());

        vpnToggle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!FakeVpnService.isRunning()) {
                    Logger.d(TAG, "Connect toggled ON");
                    Intent intent = CertificateManager.trustfakeRootCA(FakeVpnService.CADir, FakeVpnService.CAName);
                    if (intent != null) {
                        startActivityForResult(intent, ActivityRequestCodes.REQUEST_CERT);
                    } else {
                        startVPN();
                    }
                } else {
                    Logger.d(TAG, "Connect toggled OFF");
                    showIndicator(Status.VPN_OFF);
                    stopVPN();
                }
            }
        });

        statsButton = (FloatingActionButton)findViewById(R.id.stats_button);
        statsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(getApplicationContext(), AllAppsDataActivity.class);
                startActivity(i);
            }
        });

        /** 在这里使用绑定服务，因为stopservice（）不会立即触发VPN服务的销毁 */
        mSc = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                Logger.d(TAG, "VPN Service connected");
                mVPN = ((MyVpnServiceBinder) service).getService();
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                Logger.d(TAG, "VPN Service disconnected");
            }
        };

        Button permissionButton = (Button)findViewById(R.id.turn_on_permission_button);
        permissionButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent();
                intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                Uri uri = Uri.fromParts("package", getPackageName(), null);
                intent.setData(uri);
                startActivityForResult(intent, ActivityRequestCodes.PERMISSIONS_SETTINGS);
            }
        });

        Button usageStatsButton = (Button)findViewById(R.id.turn_on_usage_stats_button);
        usageStatsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivityForResult(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS), ActivityRequestCodes.USAGE_STATS_PERMISSION_REQUEST);
            }
        });

        // 如果所有必需的权限都被授予，则应用程序，否则，提示用户授予权限。
        checkPermissionsAndRequestAndEnableViews();
    }

    /**
     * 在更新视图可见性的同时，检查并请求权限。
     */
    private void checkPermissionsAndRequestAndEnableViews() {
        if (checkPermissionsAndRequest()) {
            if ((!PermissionsHelper.validBuildVersionForAppUsageAccess() || PermissionsHelper.hasUsageAccessPermission(getApplicationContext()))) {
                mainLayout.setVisibility(View.VISIBLE);
                permissionDisabledView.setVisibility(View.GONE);
            }
            else {
                mainLayout.setVisibility(View.GONE);
                permissionDisabledView.setVisibility(View.VISIBLE);
                applicationPermissionDisabledView.setVisibility(View.GONE);
                usageStatsPermissionDisabledView.setVisibility(View.VISIBLE);
            }
        }
        else {
            mainLayout.setVisibility(View.GONE);
            permissionDisabledView.setVisibility(View.GONE);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();

        if (!bounded) {
            Intent service = new Intent(this, FakeVpnService.class);
            this.bindService(service, mSc, Context.BIND_AUTO_CREATE);
            bounded = true;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        populateLeakList();

        if (!myReceiverIsRegistered) {
            registerReceiver(myReceiver, new IntentFilter(getString(R.string.vpn_running_broadcast_intent)));
            myReceiverIsRegistered = true;
        }

        if (FakeVpnService.isStarted()) {
            // 如果VPN是在用户关闭应用程序之前启动的，并且仍然没有运行，那么再次显示加载指示器。
            showIndicator(Status.VPN_STARTING);
        } else if (FakeVpnService.isRunning()) {
            showIndicator(Status.VPN_ON);
        } else {
            showIndicator(Status.VPN_OFF);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (myReceiverIsRegistered) {
            unregisterReceiver(myReceiver);
            myReceiverIsRegistered = false;
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (bounded) {// 必须解绑服务，否则ServiceConnection将被泄漏。
            this.unbindService(mSc);
            bounded = false;
        }
    }

    private enum Status {
        VPN_ON,
        VPN_OFF,
        VPN_STARTING
    }

    private void showIndicator(Status status) {
        onIndicator.setVisibility(status == Status.VPN_ON ? View.VISIBLE : View.GONE);
        offIndicator.setVisibility(status == Status.VPN_OFF ? View.VISIBLE : View.GONE);
        loadingIndicator.setVisibility(status == Status.VPN_STARTING ? View.VISIBLE : View.GONE);

        vpnToggle.setEnabled(status != Status.VPN_STARTING);
        vpnToggle.setAlpha(status == Status.VPN_STARTING ? DISABLED_ALPHA : 1.0f);

        if (status == Status.VPN_STARTING) {
            loadingViewShownTime = System.currentTimeMillis();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_menu, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
            case R.id.settings:
                Intent i = new Intent(this, MyPreferencesActivity.class);
                startActivity(i);
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    public void populateLeakList() {
        DatabaseHandler db = DatabaseHandler.getInstance(this);
        List<AppSummary> apps = db.getAllApps();

        if (apps.isEmpty()) {
            statsButton.setEnabled(false);
            statsButton.setAlpha(DISABLED_ALPHA);
        }
        else {
            statsButton.setEnabled(true);
            statsButton.setAlpha(1.0f);
        }

        Comparator<AppSummary> comparator = PreferenceHelper.getAppLeakOrder(getApplicationContext());
        Collections.sort(apps, comparator);

        if (adapter == null) {
            adapter = new MainListViewAdapter(this, apps);
            listLeak.setAdapter(adapter);
            listLeak.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                    Intent intent = new Intent(MainActivity.this, AppSummaryActivity.class);

                    AppSummary app = (AppSummary)parent.getItemAtPosition(position);

                    intent.putExtra(PrivacyLeakDetection.EXTRA_PACKAGE_NAME, app.getPackageName());
                    intent.putExtra(PrivacyLeakDetection.EXTRA_APP_NAME, app.getAppName());
                    intent.putExtra(PrivacyLeakDetection.EXTRA_IGNORE, app.getIgnore());

                    startActivity(intent);
                }
            });

            listLeak.setLongClickable(true);
            listLeak.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
                @Override
                public boolean onItemLongClick(final AdapterView<?> parent, View view, final int position, long id) {
                    final AppSummary app = (AppSummary)parent.getItemAtPosition(position);
                    PackageManager pm = getPackageManager();
                    Drawable appIcon;
                    try {
                        appIcon = pm.getApplicationIcon(app.getPackageName());
                    } catch (PackageManager.NameNotFoundException e) {
                        appIcon = getResources().getDrawable(R.drawable.default_icon);
                    }

                    new AlertDialog.Builder(MainActivity.this)
                            .setTitle(R.string.delete_package_title)
                            .setMessage(String.format(getResources().getString(R.string.delete_package_message), app.getAppName()))
                            .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int which) {
                                    DatabaseHandler databaseHandler = DatabaseHandler.getInstance(MainActivity.this);
                                    databaseHandler.deletePackage(app.getPackageName());
                                    populateLeakList();
                                }
                            })
                            .setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int which) {
                                    // do nothing
                                }
                            })
                            .setIcon(appIcon)
                            .show();
                    return true;
                }
            });
        } else {
            adapter.updateData(apps);
        }
    }

    /**
     * 当活动重新启动时，立即调用onResume().
     */
    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        if (request == ActivityRequestCodes.REQUEST_CERT) {
            boolean keyChainInstalled = result == RESULT_OK;
            if (keyChainInstalled) {
                startVPN();
            }
            else
                new AlertDialog.Builder(this)
                        .setTitle(R.string.certificate_root_store)
                        .setMessage(R.string.certificate_root_store_msg)
                        .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                //
                            }
                        })
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .show();
        } else if (request == ActivityRequestCodes.REQUEST_VPN) {
            if (result == RESULT_OK) {
                Logger.d(TAG, "Starting VPN service");

                showIndicator(Status.VPN_STARTING);
                mVPN.startVPN(this);
            }
        } else if (request == ActivityRequestCodes.PERMISSIONS_SETTINGS) {
            // 在给用户提供了手动开启所需权限的机会之后，检查所需权限是否已经被授予。
            checkPermissionsAndRequestAndEnableViews();
        } else if (request == ActivityRequestCodes.USAGE_STATS_PERMISSION_REQUEST) {
            // 在给用户提供了手动开启使用统计权限的机会之后，检查是否已经授予了它。
            checkPermissionsAndRequestAndEnableViews();
        }
    }

    private void startVPN() {
        if (!bounded) {
            Intent service = new Intent(this, FakeVpnService.class);
            this.bindService(service, mSc, Context.BIND_AUTO_CREATE);
            bounded = true;
        }
        /**
         * prepare() 有时会出现异常:
         * https://code.google.com/p/android/issues/detail?id=80074
         *
         * 如果这影响到了应用，可以让vpnservice更新主活动的状态
         * http://stackoverflow.com/questions/4111398/notify-activity-from-service
         *
         */
        Intent intent = VpnService.prepare(this);
        Logger.d(TAG, "VPN prepare done");
        if (intent != null) {
            startActivityForResult(intent, ActivityRequestCodes.REQUEST_VPN);
        } else {
            onActivityResult(ActivityRequestCodes.REQUEST_VPN, RESULT_OK, null);
        }
    }

    private void stopVPN() {
        Logger.d(TAG, "Stopping VPN service");
        if (bounded) {
            this.unbindService(mSc);
            bounded = false;
        }
        mVPN.stopVPN();
    }

    private static final int MY_PERMISSIONS_REQUEST_READ_CONTACTS = 0;
    private static final int MY_PERMISSIONS_REQUEST_READ_PHONE_STATE = 1;
    private static final int MY_PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE = 2;
    private static final int MY_PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE = 3;
    private static final int MY_PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION = 4;
    private static final int MY_PERMISSIONS_REQUEST_ACCESS_COURSE_LOCATION = 5;

    /**
     * 如果所需权限未被授予，则继续请求权限。
     * @return 无论应用是否获得所有权限.
     */
    private boolean checkPermissionsAndRequest() {

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.READ_CONTACTS},
                    MY_PERMISSIONS_REQUEST_READ_CONTACTS);
            return false;
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.READ_PHONE_STATE},
                    MY_PERMISSIONS_REQUEST_READ_PHONE_STATE);
            return false;
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                    MY_PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE);
            return false;
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    MY_PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE);
            return false;
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    MY_PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION);
            return false;
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_COARSE_LOCATION},
                    MY_PERMISSIONS_REQUEST_ACCESS_COURSE_LOCATION);
            return false;
        }

        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        String permission = null;

        switch (requestCode) {
            case MY_PERMISSIONS_REQUEST_READ_CONTACTS: {
                permission = Manifest.permission.READ_CONTACTS;
                break;
            }
            case MY_PERMISSIONS_REQUEST_READ_PHONE_STATE: {
                permission = Manifest.permission.READ_PHONE_STATE;
                break;
            }
            case MY_PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE: {
                permission = Manifest.permission.READ_EXTERNAL_STORAGE;
                break;
            }
            case MY_PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE: {
                permission = Manifest.permission.WRITE_EXTERNAL_STORAGE;
                break;
            }
            case MY_PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION: {
                permission = Manifest.permission.ACCESS_FINE_LOCATION;
                break;
            }
            case MY_PERMISSIONS_REQUEST_ACCESS_COURSE_LOCATION: {
                permission = Manifest.permission.ACCESS_COARSE_LOCATION;
                break;
            }
        }

        if (permission == null) throw new RuntimeException("Should not be null.");

        // 如果请求被取消，结果数组是空的。
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            // 如果单独的权限被授予，则再次检查。
            checkPermissionsAndRequestAndEnableViews();
        } else {
            // 如果一项单独的权限未被授予。
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, permission)) {
                // 如果用户未选择“不再询问”，则再次检查。
                checkPermissionsAndRequest();
            } else {
                // 在这种情况下，用户选择“不再询问”，并拒绝了授予权限。由于所需的权限未被授予，并且不能再请求这个权限，所以只能让用户访问权限界面来手动打开所有权限。
                mainLayout.setVisibility(View.GONE);
                permissionDisabledView.setVisibility(View.VISIBLE);
                applicationPermissionDisabledView.setVisibility(View.VISIBLE);
                boolean usageStats = (!PermissionsHelper.validBuildVersionForAppUsageAccess() || PermissionsHelper.hasUsageAccessPermission(getApplicationContext()));
                usageStatsPermissionDisabledView.setVisibility(usageStats ? View.GONE : View.VISIBLE);
            }
        }
    }
}