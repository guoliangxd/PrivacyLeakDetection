package cn.edu.xidian.privacyleakdetection.Application.Activities;


import android.content.Context;
import android.os.Bundle;
import android.support.design.widget.TabLayout;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuInflater;

import cn.edu.xidian.privacyleakdetection.Application.Database.DataLeak;
import cn.edu.xidian.privacyleakdetection.Application.Database.Traffic;
import cn.edu.xidian.privacyleakdetection.Application.Fragments.LeakQueryFragment;
import cn.edu.xidian.privacyleakdetection.Application.Fragments.LeakReportFragment;
import cn.edu.xidian.privacyleakdetection.Application.Fragments.LeakSummaryFragment;
import cn.edu.xidian.privacyleakdetection.Application.Fragments.TrafficFragment;
import cn.edu.xidian.privacyleakdetection.Application.Interfaces.AppDataInterface;
import cn.edu.xidian.privacyleakdetection.Plugin.LeakReport;
import cn.edu.xidian.privacyleakdetection.R;

import java.util.List;

public abstract class DataActivity extends AppCompatActivity implements AppDataInterface {

    private static final int TAB_COUNT = 4;

    private LeakReportFragment leakReportFragment;
    private LeakSummaryFragment leakSummaryFragment;
    private LeakQueryFragment leakQueryFragment;

    protected List<DataLeak> locationLeaks;
    protected List<DataLeak> contactLeaks;
    protected List<DataLeak> deviceLeaks;
    protected List<DataLeak> keywordLeaks;

    private TrafficFragment trafficFragment;
    protected List<Traffic> trafficsInE;
    protected List<Traffic> trafficsOutE;
    protected List<Traffic> trafficsInNe;
    protected List<Traffic> trafficsOutNe;

    protected TabLayout tabLayout;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        setContentView(R.layout.activity_app_data);

        leakReportFragment = new LeakReportFragment();
        leakSummaryFragment = new LeakSummaryFragment();
        leakQueryFragment = new LeakQueryFragment();

        trafficFragment = new TrafficFragment();

        ViewPager viewPager = (ViewPager) findViewById(R.id.viewpager);
        viewPager.setAdapter(new CustomFragmentPagerAdapter(getSupportFragmentManager(), this));
        viewPager.setOffscreenPageLimit(TAB_COUNT - 1);

        tabLayout = (TabLayout) findViewById(R.id.sliding_tabs);
        tabLayout.setupWithViewPager(viewPager);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.app_data_menu, menu);
        return super.onCreateOptionsMenu(menu);
    }

    public class CustomFragmentPagerAdapter extends FragmentPagerAdapter {
        private String tabTitles[] = new String[] { "报告", "总结", "查询", "流量"};

        public CustomFragmentPagerAdapter(FragmentManager fm, Context context) {
            super(fm);
        }

        @Override
        public int getCount() {
            return TAB_COUNT;
        }

        @Override
        public Fragment getItem(int position) {
            switch (position) {
                case 0:
                    return leakReportFragment;
                case 1:
                    return leakSummaryFragment;
                case 2:
                    return leakQueryFragment;
                case 3:
                    return trafficFragment;
                default:
                    return null;
            }
        }

        @Override
        public CharSequence getPageTitle(int position) {
            return tabTitles[position];
        }
    }

    @Override
    public String getAppName() {
        return null;
    }

    @Override
    public String getAppPackageName() {
        return null;
    }

    @Override
    public List<DataLeak> getLeaks(LeakReport.LeakCategory category) {
        switch (category) {
            case LOCATION:
                return locationLeaks;
            case CONTACT:
                return contactLeaks;
            case DEVICE:
                return deviceLeaks;
            case KEYWORD:
                return keywordLeaks;
        }
        return null;
    }

    @Override
    public List<Traffic> getTraffics(boolean encrypted, boolean outgoing){
        if(encrypted && outgoing){
            return trafficsOutE;
        }
        if(!encrypted && outgoing){
            return trafficsOutNe;
        }
        if(!encrypted && !outgoing){
            return trafficsInNe;
        }
        return trafficsInE;
    }
}

