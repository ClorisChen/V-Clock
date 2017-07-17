package com.example.dell.v_clock.activity;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.Volley;
import com.example.dell.v_clock.R;
import com.example.dell.v_clock.ServerInfo;
import com.example.dell.v_clock.adapter.SearchAdapter;
import com.example.dell.v_clock.object.GuestInfo;
import com.example.dell.v_clock.util.GuestListUtil;
import com.example.dell.v_clock.util.ImageUtil;
import com.example.dell.v_clock.util.JSONObjectRequestMapParams;
import com.org.afinal.simplecache.ACache;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SearchActivity extends AppCompatActivity implements View.OnClickListener, TextView.OnEditorActionListener, AdapterView.OnItemClickListener {

    //“取消”字样
    TextView tv_cancel;
    //显示搜索结果的的列表
    ListView lv_search_result;
    //搜索框
    EditText et_search;
    //数据源
    List<Map<String, Object>> dataList_guest;
    //数据对应的标识
    String[] from;
    //将数据添加到的view组件
    int[] to;
    //ListView 适配器
    SearchAdapter searchAdapter;
    //search tip
    final String WHOLE_NAME_SEARCH_TYPE = "2";

    //搜索结果 guest对象
    GuestInfo guestInfo;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search);

        et_search = (EditText) findViewById(R.id.et_search);
        et_search.setOnEditorActionListener(this);

        tv_cancel = (TextView) findViewById(R.id.tv_cancel);
        tv_cancel.setOnClickListener(this);

        lv_search_result = (ListView) findViewById(R.id.lv_search_result);
        dataList_guest = new ArrayList<>();

        from = new String[]{"iv_search_guest_avatar", "tv_search_guest_name"};
        to = new int[]{R.id.iv_search_guest_avatar, R.id.tv_search_guest_name};


        searchAdapter = new SearchAdapter(this, dataList_guest, R.layout.item_search_guest, from, to);
        lv_search_result.setAdapter(searchAdapter);


        lv_search_result.setOnItemClickListener(this);

    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!GuestListUtil.isNetworkAvailable(this)) {
            Toast.makeText(this, "当前网络不可用,只能进行本地搜索！", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 捕捉消息 更新UI
     */
    Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            //更新UI  ListView信息
            refreshGuestList();
        }
    };

    /**
     * 显示搜索结果 刷新ListView
     */
    private void refreshGuestList() {


        Log.i("Search", "刷新界面");
        if (guestInfo != null) {
            //显示结果
            Map<String, Object> tempMap = new HashMap();
            tempMap.put(from[0], guestInfo.getGuestBitmapPhoto());
            tempMap.put(from[1], guestInfo.getGuestName());
            dataList_guest.clear();
            dataList_guest.add(tempMap);
            et_search.setText("");
            Log.i("Search", "数据更新");
        }
        searchAdapter.notifyDataSetChanged();


    }

    /**
     * “取消”的点击事件监听
     *
     * @param view
     */
    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.tv_cancel:
                this.finish();
                break;
        }
    }

    /**
     * 监听点击软键盘上“搜索”键事件
     *
     * @param textView
     * @param i        键值
     * @param keyEvent 键盘事件
     * @return
     */
    @Override
    public boolean onEditorAction(TextView textView, int i, KeyEvent keyEvent) {
        if (i == EditorInfo.IME_ACTION_SEARCH) {
            String name = et_search.getText().toString();
            if (name.equals("") || name.equals(" ")) {
                Toast.makeText(this, "请输入正确的姓名！", Toast.LENGTH_SHORT).show();
                return false;
            }
            //向后台发送请求
            transferRequest(name);
            return true;
        }
        return false;
    }

    /**
     * 向后台发送搜索请求
     */
    private void transferRequest(final String name) {
        //要传哪些参数
        Map<String, String> searchInfo = new HashMap<>();
        searchInfo.put("gname", name);
        searchInfo.put("tip", WHOLE_NAME_SEARCH_TYPE);
        SharedPreferences sp = getSharedPreferences("loginInfo", MODE_PRIVATE);
        String eid = sp.getString("eid", null);
        searchInfo.put("eid", eid);
        JSONObjectRequestMapParams searchRequest = new JSONObjectRequestMapParams(Request.Method.POST, ServerInfo.SEARCH_GUEST_URL, searchInfo,
                new SearchResponseListener(), new SearchResponseErrorListener());
        //访问服务器请求队列
        RequestQueue requestQueue = Volley.newRequestQueue(this);
        requestQueue.add(searchRequest);
    }

    /**
     *
     */
    private class SearchResponseListener implements Response.Listener<JSONObject> {
        @Override
        public void onResponse(JSONObject response) {
            //判断返回是否有效
            try {
                String tip = response.getString("tip");
                if (tip.equals("2")) {
                    //数据错误
                    Toast.makeText(SearchActivity.this, "数据错误", Toast.LENGTH_SHORT).show();
                    return;
                } else if (tip.equals("0")) {
                    //接收成功
                    Log.i("Search", "接收成功");
                    String name = response.getString("gname");
                    String sex = response.getString("gsex");
                    String phone = response.getString("gtel");
                    String company = response.getString("gcompany");
                    String basePhoto = response.getString("gphoto");
                    Bitmap photo = ImageUtil.convertImage(basePhoto);
                    guestInfo = new GuestInfo(name, sex, company, phone, photo);
                    //发送Message 更新ListView的显示结果
                    handler.sendEmptyMessage(0);
                }

            } catch (JSONException e) {
                Toast.makeText(SearchActivity.this, "该嘉宾未添加！", Toast.LENGTH_SHORT).show();
                et_search.setText("");
                handler.sendEmptyMessage(0);
                e.printStackTrace();
            }
        }
    }

    /**
     *
     */
    private class SearchResponseErrorListener implements Response.ErrorListener {
        @Override
        public void onErrorResponse(VolleyError error) {
            Log.i("Transfer", "收到服务器回复");
            //提示网络连接失败
            Toast.makeText(SearchActivity.this, "服务器连接失败", Toast.LENGTH_SHORT).show();
            //本地搜索
            ACache aCache = ACache.get(SearchActivity.this);
            JSONArray myGuest = aCache.getAsJSONArray(GuestListUtil.ALL_GUEST_JSON_ARRAY_CACHE);
            if (myGuest != null) {
                List<Map<String, Object>> allGuestList = GuestListUtil.jsonToList(myGuest);
                String name = et_search.getText().toString();
                Bitmap photo = null;
                for (Map<String, Object> guest : allGuestList) {
                    if (guest.get("name").equals(name)) {
                        photo = (Bitmap) guest.get("avatar");
                        break;
                    }
                }
                guestInfo = new GuestInfo(name, photo);
                handler.sendEmptyMessage(0);
            }
        }
    }


    /**
     * 点击条目，进入嘉宾详细信息
     *
     * @param adapterView
     * @param view
     * @param i
     * @param l
     */
    @Override
    public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
        if (guestInfo != null) {
            String guestName = guestInfo.getGuestName();
            Bitmap guestPhoto = guestInfo.getGuestBitmapPhoto();
            int guest_type = 1;
            ACache aCache = ACache.get(this);
            JSONArray myGuest = aCache.getAsJSONArray(GuestListUtil.MY_GUEST_JSON_ARRAY_CACHE);
            if (myGuest != null) {
                List<Map<String, Object>> myGuestList = GuestListUtil.jsonToList(myGuest);
                for (Map<String, Object> guest : myGuestList) {
                    if (guest.get("name").equals(guestName)) {
                        guest_type = 0;
                        break;
                    }
                }
            }
            Intent guestInfoIntent = new Intent(this, GuestInfoActivity.class);
            guestInfoIntent.putExtra("guest_type", guest_type);
            guestInfoIntent.putExtra("gname", guestName);
            //todo  传照片
//            Bundle bd_photo = new Bundle();
//            bd_photo.putParcelable("gphoto", guestPhoto);
//            guestInfoIntent.putExtra("photoBundle", bd_photo);
//            if (guestPhoto == null) {
//                Log.i("SearchActivity","guest_photo = null");
//            }else {
//                Log.i("GuestActivity","guest_photo != null");
//            }
            startActivity(guestInfoIntent);
        }
    }
}
