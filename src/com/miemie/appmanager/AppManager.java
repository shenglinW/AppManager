
package com.miemie.appmanager;

import java.util.ArrayList;
import java.util.Collections;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.BaseExpandableListAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ExpandableListAdapter;
import android.widget.ExpandableListView;
import android.widget.ImageView;
import android.widget.TextView;

import com.miemie.appmanager.R;
import com.miemie.appmanager.Api.DroidApp;

public class AppManager extends Activity {

    private final static String TAG = AppManager.class.getSimpleName();

    /**
     * Asynchronous task used to load icons in a background thread.
     */
    private static class LoadIconTask extends AsyncTask<Object, Void, View> {
        @Override
        protected View doInBackground(Object... params) {
            try {
                final DroidApp app = (DroidApp) params[0];
                final PackageManager pkgMgr = (PackageManager) params[1];
                final View viewToUpdate = (View) params[2];
                if (!app.icon_loaded) {
                    app.cached_icon = pkgMgr.getApplicationIcon(app.appinfo);
                    app.icon_loaded = true;
                }
                // Return the view to update at "onPostExecute"
                // Note that we cannot be sure that this view still references
                // "app"
                return viewToUpdate;
            } catch (Exception e) {
                Log.e(TAG, "Error loading icon", e);
                return null;
            }
        }

        protected void onPostExecute(View viewToUpdate) {
            try {
                // This is executed in the UI thread, so it is safe to use
                // viewToUpdate.getTag()
                // and modify the UI
                final ViewHolder viewHolder = (ViewHolder) viewToUpdate.getTag();
                viewHolder.icon.setImageDrawable(viewHolder.appinfo.cached_icon);
            } catch (Exception e) {
                Log.e(TAG, "Error showing icon", e);
            }
        };
    }

    class ViewHolder {
        private CheckBox onoff;
        private TextView name;
        private ImageView icon;
        private DroidApp appinfo;
    }

    private class ApplistAdapter extends BaseExpandableListAdapter {
        private final Context mContext;
        private final LayoutInflater mFactory;
        private final PackageManager mPM;

        final ArrayList<DroidApp> mGroup0;
        final ArrayList<DroidApp> mGroup1;

        public ApplistAdapter(Context context, final DroidApp[] group) {
            super();
            mContext = context;
            mFactory = LayoutInflater.from(context);
            mPM = mContext.getPackageManager();
            mGroup0 = new ArrayList<Api.DroidApp>();
            mGroup1 = new ArrayList<Api.DroidApp>();

            for (DroidApp app : group) {
                if (!app.enable) {
                    mGroup0.add(app);
                } else {
                    mGroup1.add(app);
                }
            }
            Collections.sort(mGroup0, new AppComparator());
            Collections.sort(mGroup1, new AppComparator());
        }

        @Override
        public Object getChild(int groupPosition, int childPosition) {
            if (groupPosition == 0) {
                return mGroup0.get(childPosition);
            } else if (groupPosition == 1) {
                return mGroup1.get(childPosition);
            }
            return null;
        }

        @Override
        public long getChildId(int groupPosition, int childPosition) {
            return childPosition;
        }

        private View.OnClickListener mListener = new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                final ViewHolder viewHolder = (ViewHolder) v.getTag();
                final DroidApp appinfo = viewHolder.appinfo;
                if (appinfo.used == false)
                    appinfo.used = true;
                if (!appinfo.enable) {
                    appinfo.enable = true;
                    Api.setEnabled(AppManager.this, appinfo.enable, appinfo.appinfo.packageName);
                    if (appinfo.enable) {
                        mGroup0.remove(appinfo);
                        mGroup1.add(0, appinfo);
                    } else {
                        mGroup1.remove(appinfo);
                        mGroup0.add(0, appinfo);
                    }
                    notifyDataSetChanged();
                }

                Intent it = mPM.getLaunchIntentForPackage(appinfo.appinfo.packageName);
                it.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                // it.setFlags(Intent.FLAG_ACTIVITY_TASK_ON_HOME);
                it.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                AppManager.this.startActivity(it);
                AppManager.this.finish();
            }
        };

        @Override
        public View getChildView(int groupPosition, int childPosition, boolean isLastChild,
                View convertView,
                ViewGroup parent) {
            View v;
            ViewHolder viewHolder;
            if (convertView == null) {
                final View view = mFactory.inflate(R.layout.applist_item, parent, false);

                viewHolder = new ViewHolder();
                viewHolder.onoff = (CheckBox) view.findViewById(R.id.itemenable);
                viewHolder.name = (TextView) view.findViewById(R.id.itemtext);
                viewHolder.icon = (ImageView) view.findViewById(R.id.itemicon);

                view.setTag(viewHolder);
                v = view;
            } else {
                v = convertView;
                viewHolder = (ViewHolder) v.getTag();
            }

            final DroidApp appinfo = (DroidApp) getChild(groupPosition, childPosition);
            viewHolder.appinfo = appinfo;
            viewHolder.onoff.setOnCheckedChangeListener(null);
            final CompoundButton.OnCheckedChangeListener onOffListener = new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                    if (appinfo != null) {
                        switch (compoundButton.getId()) {
                            case R.id.itemenable:
                                if (appinfo.enable != checked) {
                                    appinfo.enable = checked;

                                    Api.setEnabled(AppManager.this, appinfo.enable,
                                            appinfo.appinfo.packageName);
                                    if (appinfo.enable) {
                                        mGroup0.remove(appinfo);
                                        mGroup1.add(0, appinfo);
                                    } else {
                                        mGroup1.remove(appinfo);
                                        mGroup0.add(0, appinfo);
                                    }
                                    if (appinfo.used == false)
                                        appinfo.used = true;
                                    notifyDataSetChanged();
                                }
                                break;
                        }
                    }
                }
            };
            viewHolder.onoff.setChecked(appinfo.enable);
            viewHolder.onoff.setOnCheckedChangeListener(onOffListener);

            viewHolder.name.setText(appinfo.toString());
            viewHolder.icon.setImageDrawable(appinfo.cached_icon);

            if (!appinfo.icon_loaded && appinfo.appinfo != null) {
                // this icon has not been loaded yet - load it on a separated
                // thread
                new LoadIconTask().execute(appinfo, getPackageManager(), v);
            }
            v.setOnClickListener(mListener);
            return v;
        }

        @Override
        public int getChildrenCount(int groupPosition) {
            if (groupPosition == 0) {
                return mGroup0.size();
            } else if (groupPosition == 1) {
                return mGroup1.size();
            }
            return 0;
        }

        @Override
        public Object getGroup(int groupPosition) {
            if (groupPosition == 0) {
                return mGroup0;
            } else if (groupPosition == 1) {
                return mGroup1;
            }
            return null;
        }

        @Override
        public int getGroupCount() {
            return 2;
        }

        @Override
        public long getGroupId(int groupPosition) {
            return groupPosition;
        }

        @Override
        public View getGroupView(int groupPosition, boolean isExpanded, View convertView,
                ViewGroup parent) {
            View v;
            if (convertView == null) {
                final View view = mFactory.inflate(R.layout.appmanager_group_item, parent, false);
                v = view;
            } else {
                v = convertView;
            }

            TextView groupName = (TextView) v.findViewById(R.id.app_group_name);
            ImageView groupIndicator = (ImageView) v.findViewById(R.id.app_group_indicator);

            TextView groupinfo = (TextView) v.findViewById(R.id.app_group_info);
            CheckBox groupselall = (CheckBox) v.findViewById(R.id.app_group_sel_all);
            // temp disable, it will be enable in future
            groupselall.setVisibility(View.GONE);

            if (isExpanded) {
                groupIndicator.setImageResource(R.drawable.listview_groupindicator_down);
            } else {
                groupIndicator.setImageResource(R.drawable.listview_groupindicator_up);
            }

            StringBuilder sb = new StringBuilder();
            if (groupPosition == 0) {
                groupName.setText("Disable");
                sb.append(mGroup0.size());
            } else if (groupPosition == 1) {
                groupName.setText("Enable");
                sb.append(mGroup1.size());
            }
            groupinfo.setText(sb.toString());

            return v;
        }

        @Override
        public boolean hasStableIds() {
            return false;
        }

        @Override
        public boolean isChildSelectable(int groupPosition, int childPosition) {
            return false;
        }

    }

    private ApplistAdapter mAdapter;
    private ExpandableListView mList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Api.removeApps();
        try {
            /* enable hardware acceleration on Android >= 3.0 */
            final int FLAG_HARDWARE_ACCELERATED = WindowManager.LayoutParams.class
                    .getDeclaredField("FLAG_HARDWARE_ACCELERATED").getInt(null);
            getWindow().setFlags(FLAG_HARDWARE_ACCELERATED, FLAG_HARDWARE_ACCELERATED);
        } catch (Exception e) {
        }

        setContentView(R.layout.appmanager_main);

        mList = (ExpandableListView) findViewById(R.id.applist);

    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        showApplications();
    }

    /**
     * If the applications are cached, just show them, otherwise load and show
     */
    private void showOrLoadApplications() {
        final Resources res = getResources();
        if (Api.applications == null) {
            // The applications are not cached.. so lets display the progress
            // dialog
            final ProgressDialog progress = ProgressDialog.show(this,
                    res.getString(R.string.working), res.getString(R.string.reading_apps), true);
            new AsyncTask<Void, Void, Void>() {
                @Override
                protected Void doInBackground(Void... params) {
                    Api.getApps(AppManager.this);
                    return null;
                }

                @Override
                protected void onPostExecute(Void result) {
                    try {
                        progress.dismiss();
                    } catch (Exception ex) {
                    }
                    showApplications();
                }
            }.execute();
        } else {
            // the applications are cached, just show the list
            showApplications();
        }
    }

    /**
     * Show the list of applications
     */
    private void showApplications() {
        final DroidApp[] apps = Api.getApps(this);
        mAdapter = new ApplistAdapter(this, apps);
        mList.setAdapter((ExpandableListAdapter) mAdapter);
    }

}
