package cn.edu.xidian.privacyleakdetection.adapter;

import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.List;

import cn.edu.xidian.privacyleakdetection.R;
import cn.edu.xidian.privacyleakdetection.model.AppPermission;

/**
 * Created by 99544 on 2018/3/23/023.
 */

public class AppPermissionAdapter extends RecyclerView.Adapter<AppPermissionAdapter.ViewHolder> {
    private List<AppPermission> appPermissionList;

    static class ViewHolder extends RecyclerView.ViewHolder{
        ImageView appIcon;
        TextView appName;
        TextView totalPermission;
        TextView privacyPermission;

        public ViewHolder(View view) {
            super(view);
            appIcon = (ImageView) view.findViewById(R.id.icon);
            appName = (TextView) view.findViewById(R.id.app_name);
            totalPermission = (TextView) view.findViewById(R.id.total_permission);
            privacyPermission = (TextView) view.findViewById(R.id.privacy_permission);
        }
    }

    public AppPermissionAdapter(List<AppPermission> appList) {
        this.appPermissionList = appList;
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_permission,parent,false);
        ViewHolder holder = new ViewHolder(view);
        return holder;
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        AppPermission appPermission = appPermissionList.get(position);
        holder.appName.setText(appPermission.getAppName());
        holder.appIcon.setImageDrawable(appPermission.getIcon());
        holder.totalPermission.setText(appPermission.getTotalPermission()+"");//直接给函数int值的话函数会认为这是资源id，导致报错，加""后会自动转成字符串类型
        holder.privacyPermission.setText(appPermission.getPrivacyPermission()+"");
    }

    @Override
    public int getItemCount() {
        return appPermissionList.size();
    }
}
