package com.example.xialong.funplacesforkids.util;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.util.Log;

import com.android.volley.Cache;
import com.android.volley.Network;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.BasicNetwork;
import com.android.volley.toolbox.DiskBasedCache;
import com.android.volley.toolbox.HurlStack;
import com.android.volley.toolbox.JsonObjectRequest;
import com.example.xialong.funplacesforkids.data.Place;
import com.example.xialong.funplacesforkids.data.PlaceContract;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

import static android.R.attr.radius;

public class PlaceUtil {

    private static PlaceUtil mInstance;
    private static Context mContext;
    private RequestQueue mRequestQueue;
    private static final String KEY = "&key=AIzaSyAj4OYy0O9hkAPpgc7jzpc5LpwgpGGJkb8";
    private static final String TAG = PlaceUtil.class.getSimpleName();

    private PlaceUtil(Context context) {
        mContext = context;
        mRequestQueue = getRequestQueue();
    }

    public static synchronized PlaceUtil getInstance(Context context) {
        if (mInstance == null) {
            mInstance = new PlaceUtil(context);
        }
        return mInstance;
    }

    public RequestQueue getRequestQueue() {
        if (mRequestQueue == null) {
            Cache cache = new DiskBasedCache(mContext.getCacheDir(), 10 * 1024 * 1024);
            Network network = new BasicNetwork(new HurlStack());
            mRequestQueue = new RequestQueue(cache, network);
            mRequestQueue.start();
        }
        return mRequestQueue;
    }

    public <T> void addToRequestQueue(Request<T> req) {
        getRequestQueue().add(req);
    }

    public static void fetchPlaces(final Context context){
        Thread placeThread = new Thread(new Runnable() {
            @Override
            public void run() {
                Cursor cursor = context.getContentResolver().query(
                        PlaceContract.PlaceEntry.CONTENT_URI,
                        null,
                        PlaceContract.PlaceEntry.COLUMN_ISFAV + " =?",
                        new String[]{"0"},
                        null
                );
                if(cursor == null || cursor.getCount()==0){
                    for(int i=0; i< Util.getPlaceTypes().size(); i++){
                        startVolleyRequest(context, Util.getPlaceTypes().get(i));
                    }
                }else{
                    context.getContentResolver().delete(PlaceContract.PlaceEntry.CONTENT_URI, null, null);
                    for(int i=0; i< Util.getPlaceTypes().size(); i++){
                        startVolleyRequest(context, Util.getPlaceTypes().get(i));
                    }
                }
                cursor.close();
            }
        });
        placeThread.start();
    }

    public static void startVolleyRequest(final Context context, final String placeType) {
        String location = "location="+Util.getCurrentLat()+","+Util.getCurrentLon();
        String url = "https://maps.googleapis.com/maps/api/place/nearbysearch/json?"+location+"&radius=45000&types=" + placeType + KEY;
        Log.d("URL=", url);
        JsonObjectRequest jsObjectRequest = new JsonObjectRequest(Request.Method.GET, url, null, new Response.Listener<JSONObject>() {
            @Override
            public void onResponse(JSONObject result) {
                try{
                    ContentResolver resolver = context.getContentResolver();
                    ContentValues[] contentValues = getPlaces(result.toString(), placeType);
                    Log.d(TAG, "contentvalues # = "+contentValues.length);
                    resolver.bulkInsert(PlaceContract.PlaceEntry.CONTENT_URI, contentValues);
                }catch (Exception e){
                    e.printStackTrace();
                }
            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                Log.d(TAG, "Something wrong!");
            }
        });
        PlaceUtil.getInstance(context).addToRequestQueue(jsObjectRequest);
    }

    public static ContentValues[] getPlaces(String result, String placeType) throws JSONException {
        JSONObject object = new JSONObject(result);
        JSONArray jArray = object.getJSONArray("results");
        ContentValues[] placeContentValues = new ContentValues[jArray.length()];
        String rating;
        String imageUrl;
        String photoReference;
        int isFave = 0; // 0 = not favorited, 1 = favorite
        for (int i = 0; i < jArray.length(); i++) {
            JSONObject placeDetail = jArray.getJSONObject(i);
            String name = placeDetail.getString("name");
            String id = placeDetail.getString("place_id");
            JSONObject loc = placeDetail.getJSONObject("geometry").getJSONObject("location");
            double lat = loc.getDouble("lat");
            double lon = loc.getDouble("lng");
            try {
                photoReference = placeDetail.getJSONArray("photos").getJSONObject(0).getString("photo_reference");
                imageUrl = "https://maps.googleapis.com/maps/api/place/photo?maxwidth=1600&photoreference=" + photoReference + KEY;
            } catch (JSONException e) {
                imageUrl = "na";
            }

            try {
                rating = placeDetail.getDouble("rating") + "";
            } catch (Exception e) {
                rating = "0";
            }

            String address = placeDetail.getString("vicinity");

            ContentValues row = new ContentValues();
            row.put(PlaceContract.PlaceEntry.COLUMN_PLACE_ID, id);
            row.put(PlaceContract.PlaceEntry.COLUMN_PLACE_NAME, name);
            row.put(PlaceContract.PlaceEntry.COLUMN_IMAGE_URL, imageUrl);
            row.put(PlaceContract.PlaceEntry.COLUMN_RATING, rating);
            row.put(PlaceContract.PlaceEntry.COLUMN_ADDRESS, address);
            row.put(PlaceContract.PlaceEntry.COLUMN_LATITUDE, lat);
            row.put(PlaceContract.PlaceEntry.COLUMN_LONGITUDE, lon);
            row.put(PlaceContract.PlaceEntry.COLUMN_PLACE_TYPE, placeType);
            row.put(PlaceContract.PlaceEntry.COLUMN_ISFAV, isFave);
            placeContentValues[i] = row;
        }
        return placeContentValues;
    }
}
