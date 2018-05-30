package cn.edu.xidian.privacyleakdetection.Application.Fragments;

import android.annotation.TargetApi;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import cn.edu.xidian.privacyleakdetection.R;
import cn.edu.xidian.privacyleakdetection.Application.Database.AppStatusEvent;
import cn.edu.xidian.privacyleakdetection.Application.Database.DataLeak;
import cn.edu.xidian.privacyleakdetection.Application.Database.DatabaseHandler;
import cn.edu.xidian.privacyleakdetection.Application.Helpers.PreferenceHelper;
import cn.edu.xidian.privacyleakdetection.Application.Interfaces.AppDataInterface;
import cn.edu.xidian.privacyleakdetection.Plugin.LeakReport;
import com.androidplot.xy.BoundaryMode;
import com.androidplot.xy.LineAndPointFormatter;
import com.androidplot.xy.SimpleXYSeries;
import com.androidplot.xy.StepFormatter;
import com.androidplot.xy.StepMode;
import com.androidplot.xy.XYGraphWidget;
import com.androidplot.xy.XYPlot;

import java.text.DateFormat;
import java.text.FieldPosition;
import java.text.Format;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;


@TargetApi(22)
public class LeakReportFragment extends Fragment {

    private boolean setUpGraph = false;

    private static final int DOMAIN_STEPS_PER_HALF_DOMAIN = 5;

    private ImageButton navigateLeft;
    private ImageButton navigateRight;
    private TextView graphTitleText;

    private UsageStatsManager usageStatsManager;

    private Map<Date, int[]> leakMap = new HashMap<>();
    private List<Date> leakMapKeys = new ArrayList<>();
    private int currentKeyIndex = -1;
    private XYPlot plot;

    private AppDataInterface activity;

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        activity = (AppDataInterface)context;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        View view = inflater.inflate(R.layout.leak_report_fragment, null);

        usageStatsManager = (UsageStatsManager)getContext().getSystemService(Context.USAGE_STATS_SERVICE);

        plot = (XYPlot)view.findViewById(R.id.plot);

        navigateLeft = (ImageButton)view.findViewById(R.id.navigate_left);
        navigateRight = (ImageButton)view.findViewById(R.id.navigate_right);

        navigateLeft.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Date centerDate = leakMapKeys.get(currentKeyIndex);
                long centerMillis = centerDate.getTime();
                int halfRange = PreferenceHelper.getLeakReportGraphDomainSize(getContext())/2;
                int domainStep = (halfRange/DOMAIN_STEPS_PER_HALF_DOMAIN) * 1000;

                long newMillis = centerMillis;
                while (centerMillis - newMillis < domainStep) {
                    if (currentKeyIndex == 0) break;
                    currentKeyIndex--;
                    newMillis = leakMapKeys.get(currentKeyIndex).getTime();
                }

                setGraphBounds();
                setUpNavigationDisplay();
            }
        });

        navigateRight.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Date centerDate = leakMapKeys.get(currentKeyIndex);
                long centerMillis = centerDate.getTime();
                int halfRange = PreferenceHelper.getLeakReportGraphDomainSize(getContext())/2;
                int domainStep = (halfRange/ DOMAIN_STEPS_PER_HALF_DOMAIN) * 1000;

                long newMillis = centerMillis;
                while (newMillis - centerMillis < domainStep) {
                    if (currentKeyIndex == leakMapKeys.size() - 1) break;
                    currentKeyIndex++;
                    newMillis = leakMapKeys.get(currentKeyIndex).getTime();
                }

                setGraphBounds();
                setUpNavigationDisplay();
            }
        });

        graphTitleText = (TextView)view.findViewById(R.id.graph_title_text);

        PackageManager pm = getContext().getPackageManager();
        ImageView appIcon = (ImageView)view.findViewById(R.id.app_icon);
        try {
            appIcon.setImageDrawable(pm.getApplicationIcon(activity.getAppPackageName()));
        } catch (PackageManager.NameNotFoundException e) {
            appIcon.setImageResource(R.drawable.default_icon);
        }

        TextView appNameText = (TextView)view.findViewById(R.id.app_name);
        appNameText.setText(activity.getAppName());

        //在应用程序包为空的情况下，将显示图表上的所有应用程序泄漏。
        if (activity.getAppPackageName() == null) {
            View legend = view.findViewById(R.id.foreground_background_legend);
            legend.setVisibility(View.GONE);
        }

        setUpGraph();
        setGraphBounds();
        setUpNavigationDisplay();

        return view;
    }

    private void setUpNavigationDisplay() {
        int halfRange = PreferenceHelper.getLeakReportGraphDomainSize(getContext())/2;
        int domainStep = (halfRange/ DOMAIN_STEPS_PER_HALF_DOMAIN) * 1000;

        // 只允许用户在一个方向上导航，如果在这个方向上有一个域的泄漏（或更多）。
        boolean navigateRightEnabled = leakMapKeys.get(leakMapKeys.size() - 1).getTime() - leakMapKeys.get(currentKeyIndex).getTime() >= domainStep;
        boolean navigateLeftEnabled = leakMapKeys.get(currentKeyIndex).getTime() - leakMapKeys.get(0).getTime() >= domainStep;

        navigateRight.setEnabled(navigateRightEnabled);
        navigateRight.setAlpha(navigateRightEnabled ? 1.0f : 0.3f);
        navigateLeft.setEnabled(navigateLeftEnabled);
        navigateLeft.setAlpha(navigateLeftEnabled ? 1.0f : 0.3f);
    }

    private void setGraphBounds() {
        Date centerDate = leakMapKeys.get(currentKeyIndex);
        long centerMillis = centerDate.getTime();
        int halfRange = PreferenceHelper.getLeakReportGraphDomainSize(getContext())/2;
        long range = 1000 * halfRange;

        long domainLowerBound = centerMillis - range;
        long domainUpperBound = centerMillis + range;
        plot.setDomainBoundaries(domainLowerBound, domainUpperBound, BoundaryMode.FIXED);
        plot.setDomainStep(StepMode.INCREMENT_BY_VAL, (halfRange/ DOMAIN_STEPS_PER_HALF_DOMAIN) * 1000);

        DateFormat dateFormat = new SimpleDateFormat("MMMM d, yyyy", Locale.CANADA);
        String lowerBoundDate = dateFormat.format(new Date(domainLowerBound));
        String upperBoundDate = dateFormat.format(new Date(domainUpperBound));
        if (lowerBoundDate.equals(upperBoundDate)) {
            graphTitleText.setText(lowerBoundDate);
        } else {
            graphTitleText.setText(lowerBoundDate + " - " + upperBoundDate);
        }

        int rangeUpperBound = 0;

        int searchIndex = currentKeyIndex;
        while(searchIndex >=0 && leakMapKeys.get(searchIndex).getTime() >= domainLowerBound) {
            int[] summary = leakMap.get(leakMapKeys.get(searchIndex));
            for (int i : summary) {
                if (i > rangeUpperBound) {
                    rangeUpperBound = i;
                }
            }
            searchIndex--;
        }

        searchIndex = currentKeyIndex;
        while(searchIndex < leakMapKeys.size() && leakMapKeys.get(searchIndex).getTime() <= domainUpperBound) {
            int[] summary = leakMap.get(leakMapKeys.get(searchIndex));
            for (int i : summary) {
                if (i > rangeUpperBound) {
                    rangeUpperBound = i;
                }
            }
            searchIndex++;
        }

        // 在最高的数据点和图的顶部之间添加一些空间。
        rangeUpperBound++;
        rangeUpperBound = rangeUpperBound + rangeUpperBound % 2;

        plot.setRangeBoundaries(0, rangeUpperBound, BoundaryMode.FIXED);
        plot.redraw();
    }

    // 绘制图表上的所有数据。应该只调用一次。
    private void setUpGraph() {
        if (setUpGraph) {
            throw new RuntimeException("This method should only be called once!");
        }
        setUpGraph = true;

        long currentTime = System.currentTimeMillis();
        int maxNumberOfLeaks = 0;

        // 接下来，按日期和类别对应用程序的泄漏进行聚合。
        for (LeakReport.LeakCategory category : LeakReport.LeakCategory.values()) {
            List<DataLeak> leaks = activity.getLeaks(category);
            for (DataLeak leak : leaks) {
                int[] summary = leakMap.get(leak.getTimestampDate());
                if (summary == null) {
                    summary = new int[LeakReport.LeakCategory.values().length];
                    leakMap.put(leak.getTimestampDate(), summary);
                }
                summary[category.ordinal()]++;
                int value = summary[category.ordinal()];
                if (value > maxNumberOfLeaks) {
                    maxNumberOfLeaks = value;
                }
            }
        }

        maxNumberOfLeaks++;
        maxNumberOfLeaks = maxNumberOfLeaks + maxNumberOfLeaks % 2;
        leakMapKeys.addAll(leakMap.keySet());
        Collections.sort(leakMapKeys);

        currentKeyIndex = leakMapKeys.size() - 1;

        int[] lineFormats = {R.xml.point_formatter_location, R.xml.point_formatter_contact, R.xml.point_formatter_device, R.xml.point_formatter_keyword};

        plot.setRangeStep(StepMode.INCREMENT_BY_VAL, 1);
        plot.getGraph().getLineLabelStyle(XYGraphWidget.Edge.BOTTOM).setFormat(new GraphDomainFormat());
        plot.getGraph().getLineLabelStyle(XYGraphWidget.Edge.LEFT).setFormat(new GraphRangeFormat());
        plot.getGraph().getDomainCursorPaint().setTextSize(10);
        plot.getGraph().setPaddingLeft(35);
        plot.getGraph().setPaddingRight(30);
        plot.getGraph().setPaddingBottom(100);
        plot.getLayoutManager().remove(plot.getLegend());
        plot.getLayoutManager().remove(plot.getDomainTitle());
        plot.getLayoutManager().remove(plot.getTitle());

        List<SimpleXYSeries> leakSeries = new ArrayList<>();
        for (int i = 0; i < LeakReport.LeakCategory.values().length; i++) {
            leakSeries.add(new SimpleXYSeries(null));
        }

        for (Date date : leakMapKeys) {
            int[] summary = leakMap.get(date);
            for (LeakReport.LeakCategory category : LeakReport.LeakCategory.values()) {
                int index = category.ordinal();
                if (summary[index] > 0) {
                    leakSeries.get(index).addLast(date.getTime(), summary[index]);
                }
            }
        }

        for (int i = 0; i < leakSeries.size(); i++) {
            plot.addSeries(leakSeries.get(i), new LineAndPointFormatter(getContext(), lineFormats[i]));
        }

        if (activity.getAppPackageName() != null) {
            List<AppStatusEvent> appStatusEventList = new ArrayList<>();

            UsageEvents usageEvents = usageStatsManager.queryEvents(currentTime - TimeUnit.DAYS.toMillis(30), currentTime);

            HashSet<AppStatusEvent> appStatusEvents = new HashSet<>();
            while (usageEvents.hasNextEvent()) {
                UsageEvents.Event event = new UsageEvents.Event();
                usageEvents.getNextEvent(event);
                if (event.getPackageName().equals(activity.getAppPackageName()) &&
                        (event.getEventType() == UsageEvents.Event.MOVE_TO_BACKGROUND ||
                                event.getEventType() == UsageEvents.Event.MOVE_TO_FOREGROUND)) {

                    int foreground = event.getEventType() ==
                            UsageEvents.Event.MOVE_TO_FOREGROUND ? DatabaseHandler.FOREGROUND_STATUS : DatabaseHandler.BACKGROUND_STATUS;
                    AppStatusEvent temp = new AppStatusEvent(event.getPackageName(), event.getTimeStamp(), foreground);
                    appStatusEvents.add(temp);
                }
            }

            DatabaseHandler databaseHandler = DatabaseHandler.getInstance(getContext());
            List<AppStatusEvent> storedEvents = databaseHandler.getAppStatusEvents(activity.getAppPackageName());
            appStatusEvents.addAll(storedEvents);

            appStatusEventList.addAll(appStatusEvents);

            Collections.sort(appStatusEventList);

            Paint lineFillForeground = new Paint();
            lineFillForeground.setColor(getResources().getColor(R.color.app_status_green));
            lineFillForeground.setAlpha(70);

            Paint lineFillBackground = new Paint();
            lineFillBackground.setColor(getResources().getColor(R.color.app_status_red));
            lineFillBackground.setAlpha(70);

            StepFormatter stepFormatterForeground  = new StepFormatter(Color.WHITE, Color.WHITE);
            stepFormatterForeground.setVertexPaint(null);
            stepFormatterForeground.getLinePaint().setStrokeWidth(0);
            stepFormatterForeground.setFillPaint(lineFillForeground);

            StepFormatter stepFormatterBackground  = new StepFormatter(Color.WHITE, Color.WHITE);
            stepFormatterBackground.setVertexPaint(null);
            stepFormatterBackground.getLinePaint().setStrokeWidth(0);
            stepFormatterBackground.setFillPaint(lineFillBackground);

            SimpleXYSeries seriesForeground = new SimpleXYSeries(null);
            SimpleXYSeries seriesBackground = new SimpleXYSeries(null);

            // 在一开始的时候，这款应用就在后台。
            seriesBackground.addLast(0, maxNumberOfLeaks);

            for (AppStatusEvent event : appStatusEventList) {
                if (event.getForeground()) {
                    seriesForeground.addLast(event.getTimeStamp(), maxNumberOfLeaks);
                    seriesBackground.addLast(event.getTimeStamp(), 0);
                } else {
                    seriesForeground.addLast(event.getTimeStamp(), 0);
                    seriesBackground.addLast(event.getTimeStamp(), maxNumberOfLeaks);
                }
            }

            // 这确保了图表上的最后一个事件持续到当前时间。
            //在图上的所有未来时间都没有背景色。
            seriesForeground.addLast(currentTime, 0);
            seriesBackground.addLast(currentTime, 0);

            plot.addSeries(seriesForeground, stepFormatterForeground);
            plot.addSeries(seriesBackground, stepFormatterBackground);
        }

        plot.redraw();
    }

    private static class GraphDomainFormat extends Format {
        private static DateFormat dateFormat = new SimpleDateFormat("h:mm:ss aa", Locale.CANADA);

        @Override
        public StringBuffer format(Object obj, StringBuffer toAppendTo, FieldPosition pos) {
            long millis = ((Number) obj).longValue();
            return toAppendTo.append(dateFormat.format(new Date(millis)));
        }
        @Override
        public Object parseObject(String source, ParsePosition pos) {
            return null;
        }
    }

    private static class GraphRangeFormat extends Format {

        @Override
        public StringBuffer format(Object obj, StringBuffer toAppendTo, FieldPosition pos) {
            int quantity = ((Number) obj).intValue();
            return toAppendTo.append(quantity % 2 == 0 ? quantity : "");
        }
        @Override
        public Object parseObject(String source, ParsePosition pos) {
            return null;
        }
    }
}
