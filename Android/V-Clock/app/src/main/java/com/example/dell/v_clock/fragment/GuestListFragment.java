package com.example.dell.v_clock.fragment;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.Fragment;
import android.support.v4.widget.SwipeRefreshLayout;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ExpandableListView;
import android.widget.ImageButton;

import com.android.volley.RequestQueue;
import com.android.volley.toolbox.Volley;
import com.example.dell.v_clock.R;
import com.example.dell.v_clock.activity.AddGuestActivity;
import com.example.dell.v_clock.activity.GuestInfoActivity;
import com.example.dell.v_clock.activity.MainActivity;
import com.example.dell.v_clock.activity.SearchActivity;
import com.example.dell.v_clock.adapter.GuestListAdapter;
import com.example.dell.v_clock.util.GuestListUtil;
import com.org.afinal.simplecache.ACache;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static android.content.Context.MODE_PRIVATE;

/**
 * This fragment shows the list of my guest and all guest.
 * 这个碎片展示我的嘉宾和所有嘉宾
 */
public class GuestListFragment extends Fragment implements View.OnClickListener,
        ExpandableListView.OnChildClickListener, SwipeRefreshLayout.OnRefreshListener {

    //嘉宾列表
    private ExpandableListView guestList;
    //添加嘉宾 按钮
    private ImageButton ibt_addGuest;
    //搜索按钮
    private Button bt_search;
    //嘉宾列表的适配器
    private GuestListAdapter guestListAdapter;
    //外侧列表的数据源
    private List<String> guestGroupList;
    //内层列表的数据源
    private List<List<Map<String, Object>>> guestChildList;

    //请求队列
    private RequestQueue requestQueue;
    String eid;

    //缓存对象
    private ACache mACache;
    //myGuestJson对象
    private JSONArray myGuestJsonArray = null;
    //allGuestJson对象
    private JSONArray allGuestJsonArray = null;

    //是否可加载  请求服务器时 不可加载
//    private boolean isLoadable = false;

    private final int MY_GUEST_IDENTITOR = 0;
    private final int ALL_GUEST_IDENTITOR = 1;
    private final int FRESH_UI = 4;

    SwipeRefreshLayout swipeRefreshLayout;

    String TAG = "StartGuestList";


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        Log.i("guestList", "onCreate");
        View view = inflater.inflate(R.layout.fragment_guest_list, container, false);

        //初始化控件
        guestList = view.findViewById(R.id.explv_my_guest_list);
        ibt_addGuest = view.findViewById(R.id.img_bt_add);
        bt_search = view.findViewById(R.id.bt_search);
        swipeRefreshLayout = view.findViewById(R.id.swipe_refresh_guest_list);

        ibt_addGuest.setOnClickListener(this);
        bt_search.setOnClickListener(this);

        guestList.setOnChildClickListener(this);

        swipeRefreshLayout.setOnRefreshListener(this);

        //初始化嘉宾列表
        initGuestList();

        return view;
    }

    /**
     * 初始化嘉宾列表
     */
    private void initGuestList() {
        //GroupList只包含两项
        guestGroupList = new ArrayList<>();
        guestGroupList.add(0, "我的嘉宾");
        guestGroupList.add(1, "全部嘉宾");
        //childList的信息来源于后台服务器
        guestChildList = new ArrayList<>();
        guestChildList.add(MY_GUEST_IDENTITOR, new ArrayList<Map<String, Object>>());
        guestChildList.add(ALL_GUEST_IDENTITOR, new ArrayList<Map<String, Object>>());
        //设置适配器
        guestListAdapter = new GuestListAdapter(this.getContext(), guestGroupList, guestChildList);
        guestList.setAdapter(guestListAdapter);
        //缓存对象
        mACache = ACache.get(getContext());
        //
        requestQueue = Volley.newRequestQueue(getContext());
        SharedPreferences sp = getContext().getSharedPreferences("loginInfo", MODE_PRIVATE);
        eid = sp.getString("eid", null);
        //启动线程读取数据
        readCache();
    }

    /**
     * 启动线程读取缓存数据
     */
    private void readCache() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                //加载ChildList数据
                if (GuestListUtil.getMyGuestList().size() > 0) {//内存中已有我的嘉宾数据
                    Log.i(TAG, "内存中已有我的嘉宾数据,从内存加载");
                    GuestListUtil.setValueToList(GuestListUtil.getMyGuestList(), guestChildList.get(MY_GUEST_IDENTITOR));
                    //数据更新 刷新UI
                    guestListAdapter.notifyDataSetChanged();
                } else {
                    Log.i(TAG, "内存中没有我的嘉宾数据,加载缓存");
                    //判断是否有缓存数据  没有请求后台
                    myGuestJsonArray = mACache.getAsJSONArray(GuestListUtil.MY_GUEST_JSON_ARRAY_CACHE);
                    //读取完我的嘉宾缓存后 判读是否 加载数据
                    cacheIsAvailable(myGuestJsonArray, MY_GUEST_IDENTITOR);
                }
                if (GuestListUtil.getAllGuestList().size() > 0) {
                    Log.i(TAG, "内存中已有全部嘉宾数据,从内存加载");
                    GuestListUtil.setValueToList(GuestListUtil.getAllGuestList(), guestChildList.get(ALL_GUEST_IDENTITOR));
                    //数据更新 刷新UI
                    guestListAdapter.notifyDataSetChanged();
                } else {
                    Log.i(TAG, "内存中没有全部嘉宾数据,加载缓存");
                    //判断是否有缓存数据
                    allGuestJsonArray = mACache.getAsJSONArray(GuestListUtil.ALL_GUEST_JSON_ARRAY_CACHE);
                    //读取完全部嘉宾缓存后 判读是否 加载数据
                    cacheIsAvailable(allGuestJsonArray, ALL_GUEST_IDENTITOR);
                }
            }
        }).start();
    }

    @Override
    public void onResume() {
        super.onResume();
        //启动线程 若向数据库请求数据 检测是否完成
        refreshChildList();
    }

    /**
     * 接收Message 更改UI
     */
    public Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MY_GUEST_IDENTITOR:
                    //我的嘉宾 数据加载完成
                    Log.i(TAG, "我的嘉宾 数据加载完成");
                    if (GuestListUtil.getMyGuestList().size() == 0) {
                        Log.i(TAG, "GuestUtil 我的嘉宾 无数据 给其赋值 ");
                        GuestListUtil.setMyGuestList(guestChildList.get(MY_GUEST_IDENTITOR));
                    }
                    //刷新UI
                    guestListAdapter.notifyDataSetChanged();
                    break;
                case ALL_GUEST_IDENTITOR:
                    //全部嘉宾 数据加载完成
                    Log.i(TAG, "全部嘉宾 数据加载完成");
                    if (GuestListUtil.getAllGuestList().size() == 0) {
                        Log.i(TAG, "GuestUtil 全部嘉宾 无数据 给其赋值 ");
                        GuestListUtil.setAllGuestList(guestChildList.get(ALL_GUEST_IDENTITOR));
                    }
                    //刷新UI
                    guestListAdapter.notifyDataSetChanged();
                    break;
                case FRESH_UI:
                    //数据更新 刷新UI
                    guestListAdapter.notifyDataSetChanged();
                    swipeRefreshLayout.setRefreshing(false);
            }
        }
    };

    /**
     * 读取完缓存后 判读是否 加载数据
     */
    private void cacheIsAvailable(final JSONArray guestJsonArray, final int identitor) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                if (guestJsonArray == null || guestJsonArray.length() == 0) {
                    //请求服务器
                    if (identitor == 0) {
                        Log.i(TAG, "我的嘉宾缓存为空，请求服务器");
                        GuestListUtil.requestMyGuestList(getContext(), requestQueue, eid);
                    } else if (identitor == 1) {
                        Log.i(TAG, "全部嘉宾缓存为空，请求服务器");
                        GuestListUtil.requestAllGuestList(getContext(), requestQueue, eid);
                    }
                } else {
                    Log.i(TAG, "加载嘉宾缓存——" + identitor);
                    loadChildListData(guestJsonArray, identitor);
                }
            }
        }).start();

        //启动线程  检测是否需要写缓存
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    while (true) {
                        Thread.sleep(500);
                        JSONArray temp;
                        if (identitor == MY_GUEST_IDENTITOR) {
                            temp = GuestListUtil.getMyGuestJsonArray();
                            if (temp.length() > 0 && myGuestJsonArray == null) {
                                //写缓存
                                Log.i(TAG, "我的嘉宾 写缓存");
                                mACache.put(GuestListUtil.MY_GUEST_JSON_ARRAY_CACHE, temp, GuestListUtil.MY_SAVE_TIME);
                                break;
                            }
                        } else if (identitor == ALL_GUEST_IDENTITOR) {
                            temp = GuestListUtil.getAllGuestJsonArray();
                            if (temp.length() > 0 && allGuestJsonArray == null) {
                                //写缓存
                                Log.i(TAG, "全部嘉宾 写缓存");
                                mACache.put(GuestListUtil.ALL_GUEST_JSON_ARRAY_CACHE, temp, GuestListUtil.ALL_SAVE_TIME);
                                break;
                            }
                        }
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

            }
        }).start();
    }

    /**
     * 加载ChildList信息
     *
     * @param guestJsonArray 嘉宾信息
     * @param i              我的嘉宾：0 ； 全部嘉宾：1
     */
    private void loadChildListData(JSONArray guestJsonArray, int i) {
        if (guestJsonArray != null) {
            synchronized (this) {
                if (guestChildList.size() > i) {
                    Log.i(TAG, "嘉宾列表——" + i + " 有数据，清空");
                    guestChildList.get(i).clear();
                }
                Log.i(TAG, "将从缓存中读取的JSon对象转为List——" + i);
                List<Map<String, Object>> tempList = GuestListUtil.jsonToList(guestJsonArray);
                guestChildList.add(i, tempList);
                handler.sendEmptyMessage(i);
            }
        }
    }

    //下拉刷新 重新请求数据库
    @Override
    public void onRefresh() {
        GuestListUtil.requestMyGuestList(getContext(), requestQueue, eid);
        GuestListUtil.requestAllGuestList(getContext(), requestQueue, eid);
    }

    /**
     * 刷新ChildList的数据
     */
    private void refreshChildList() {
        //启动一个线程 检查数据是否更新完成
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    while (true) {
                        Thread.sleep(1000);
                        if (GuestListUtil.isMyFreshed()) {
                            Log.i(TAG, "isMyFreshed = true,工具类给Fragment赋值");
                            GuestListUtil.setValueToList(GuestListUtil.getMyGuestList(), guestChildList.get(MY_GUEST_IDENTITOR));
                            handler.sendEmptyMessage(FRESH_UI);
                            GuestListUtil.setIsMyFreshed(false);
                        }
                        if (GuestListUtil.isAllFreshed()) {
                            Log.i(TAG, "isAllFreshed = true,工具类给Fragment赋值");
                            GuestListUtil.setValueToList(GuestListUtil.getAllGuestList(), guestChildList.get(ALL_GUEST_IDENTITOR));
                            handler.sendEmptyMessage(FRESH_UI);
                            GuestListUtil.setIsAllFreshed(false);
                        }
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }).start();
        guestList.expandGroup(MY_GUEST_IDENTITOR);
    }

    /**
     * 搜索按钮点击事件的监听
     *
     * @param view 点击的控件
     */
    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.bt_search:
                Log.i("Guest", "点击了搜索按钮");
                Intent search_intent = new Intent(getContext(), SearchActivity.class);
                startActivity(search_intent);
                break;
            case R.id.img_bt_add:
                Log.i("Guest", "点击了添加按钮");
                Intent add_intent = new Intent(getContext(), AddGuestActivity.class);
                startActivity(add_intent);
                break;
        }
    }

    /**
     * 列表项 点击事件的监听
     *
     * @param expandableListView The ExpandableListView where the click happened
     * @param view               The view within the expandable list/ListView that was clicked
     * @param groupPosition      The group position that contains the child thatwas clicked
     * @param childPosition      The child position within the group
     * @param id                 The row id of the child that was clicked
     * @return true if the click was handled
     */
    @Override
    public boolean onChildClick(ExpandableListView expandableListView, View view,
                                int groupPosition, int childPosition, long id) {
        //T判断点击的是哪一项
        String name = (String) guestChildList.get(groupPosition).get(childPosition).get("name");
        Bitmap photo = (Bitmap) guestChildList.get(groupPosition).get(childPosition).get("avatar");
        Intent guestInfoIntent = new Intent(getContext(), GuestInfoActivity.class);
        int guest_type = groupPosition;
        if (groupPosition == ALL_GUEST_IDENTITOR) {
            //判断是不是我的嘉宾
            for (int i = 0; i < guestChildList.get(0).size(); i++) {
                if (guestChildList.get(0).get(i).get("name").equals(name)) {
                    guest_type = MY_GUEST_IDENTITOR;
                    break;
                }
            }
        }
        guestInfoIntent.putExtra("guest_type", guest_type);
        guestInfoIntent.putExtra("gname", name);
//        Bundle bd_photo = new Bundle();
//        bd_photo.putParcelable("gphoto",photo);
        //todo  传照片
//        guestInfoIntent.putExtra("photoBundle", bd_photo);
//        if (photo == null) {
//            Log.i("GuestActivity","guest_photo = null");
//        }else {
//            Log.i("GuestActivity","guest_photo != null");
//        }
        startActivity(guestInfoIntent);
        return false;
    }

}
