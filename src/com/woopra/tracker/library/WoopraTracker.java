package com.woopra.tracker.library;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpResponseException;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

public class WoopraTracker {
	private Context mContext;
	private String mCookie;
	private String mUserAgent;
	private String mHostName;
	private String responseString;
	private Handler mHandler;
	
	private static String META_USERDEFAULT_KEY	=	"com_woopra_tracker_meta_shared_resources_key";
	private static String UNIQUE_IDENTIFIER_KEY	=	"com_woopra_tracker_unique_identifier_key";
	private static String UNIQUE_PREFERENCE_KEY	=	"com_woopra_tracker_unique_preference_key";;
	
	public static String PARAMETER_NAME			=	"com_woopra_tracker_parameter_name";
	public static String PARAMETER_EMAIL		=	"com_woopra_tracker_parameter_email";
	
	public WoopraTracker(Context context, String hostname){
		try {
			mHostName	=	URLEncoder.encode(hostname, "UTF-8");
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		}
		mContext		=	context;
		mHandler		=	new Handler();
		
		setupUserAgent();
		setupCookie();
	}
	
	
	private void setupUserAgent(){
		PackageManager	man		=	mContext.getPackageManager();
		String packageName 		= 	mContext.getApplicationContext().getPackageName();
		ApplicationInfo ai;
		try {
		    ai					= 	man.getApplicationInfo(packageName, 0);
		} catch (final NameNotFoundException e) {
		    ai 					= 	null;
		}
		PackageInfo pInfo;
		try {
			pInfo = man.getPackageInfo(packageName, 0);
		} catch (NameNotFoundException e) {
			pInfo				=	null;
		}
		String version 			= 	(String) (pInfo != null ? pInfo.versionName : "(unknown)");
		String appName			=	(String) (ai != null ? man.getApplicationLabel(ai) : "(unknown)");
		String model			=	Build.MODEL;
		String osVersion		=	Build.VERSION.RELEASE;
		
		mUserAgent           	=   "woopra/os="+osVersion+"&browser="+appName+" "+version+"&device="+model;
		try {
			mUserAgent				=	URLEncoder.encode(mUserAgent, "UTF-8");
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		}
	}
	
	private void setupCookie(){
		SharedPreferences settings 	= 	mContext.getSharedPreferences(UNIQUE_PREFERENCE_KEY, 0);
		String uniqueId				= 	settings.getString(UNIQUE_IDENTIFIER_KEY, "");
		if(uniqueId.length() == 0){
			uniqueId				= 	UUID.randomUUID().toString();
			SharedPreferences.Editor editor 	= 	settings.edit();
			editor.putString(UNIQUE_IDENTIFIER_KEY, uniqueId);
			editor.commit();
		}
		mCookie						=	uniqueId;
		try {
			mCookie					=	URLEncoder.encode(mCookie,"UTF-8");
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		}
	}
	

	public void trackPageView(String title, String url, HashMap<String,String> customDict){
	    String extraParams 		=   "";
	    String urlString		=   null;
	    
	    if (title != null && title.length() > 0 ) {
	        extraParams      	+=	"&ce_name="+title;
	    }
	    if (url != null && url.length() > 0) {
	        extraParams			+=	"&ce_url=" + url;
	    }
	    if (customDict != null) {
	        @SuppressWarnings("rawtypes")
			Iterator it 		= 	customDict.entrySet().iterator();
	        while (it.hasNext()) {
	            @SuppressWarnings("rawtypes")
				Map.Entry pairs = 	(Map.Entry)it.next();
	            String key		=	pairs.getKey().toString();
	            String value	=	pairs.getValue().toString();
	            if(key.equals(PARAMETER_NAME)){
	            	extraParams	+=	"&cv_name=" + value;
	            }else if(key.equals(PARAMETER_EMAIL)){
	            	extraParams	+=	"&cv_email=" + value;
	            }
	            it.remove(); // avoids a ConcurrentModificationException
	        }
	    }
	    
		SharedPreferences settings 	= 	mContext.getSharedPreferences(UNIQUE_PREFERENCE_KEY, 0);
		String objMetaString		= 	settings.getString(META_USERDEFAULT_KEY, "");
		try {
			objMetaString			=	URLEncoder.encode(objMetaString, "UTF-8");
			extraParams				=	URLEncoder.encode(extraParams,"UTF-8");
		} catch (UnsupportedEncodingException e1) {
			e1.printStackTrace();
		}
		if(objMetaString.length() > 0){
			urlString			=	"http://"+mHostName+".woopra-ns.com/visit/&response=json&cookie="+mCookie+"&meta="+objMetaString;
		}else{
			urlString			=	"http://"+mHostName+".woopra-ns.com/visit/&response=json&cookie="+mCookie;
		}
	    
	    //Extra parameters from views
	    if(extraParams.length() > 0){
	        urlString      		+=	extraParams;
	    }
	    final String uri		=	urlString;
	    
		Thread thread = new Thread(){
			@Override 
			public void run() {
				responseString	=  	webGet(uri);
				if(responseString != null)
					responseHandler.sendEmptyMessage(0);
			}
		};
		thread.start();
		
		pingToServer();
	}
	
	public void trackPageView(String title, String url){
		trackPageView(title, url, null);
	}
	
    //Use this method to do a HttpGet/WebGet on the web service
	protected  String webGet(String url) {
		DefaultHttpClient httpClient = new DefaultHttpClient();
	    HttpResponse response 	=	null;
	    HttpGet httpGet 		=	null;
	    String ret				=	null;
	    
        httpGet 				=	new HttpGet(url);
        httpGet.setHeader("User-Agent",mUserAgent);
        try {
            response 			=	httpClient.execute(httpGet);
        }catch (HttpResponseException e) {
        	e.printStackTrace();
        	return null;
        }catch (ClientProtocolException e) {
        	e.printStackTrace();
        	return null;
        }catch (SocketTimeoutException e){
        	e.printStackTrace();
        	return null;
        }catch (MalformedURLException e) {
        	e.printStackTrace();
        	return null;
        } catch (IOException e) {
        	e.printStackTrace();
        	return null;
        }catch (Exception e) {
        	e.printStackTrace();
        	return null;
        }
        
        // we assume that the response body contains the error message
        try {
            ret 				=	EntityUtils.toString(response.getEntity());
        } catch (IOException e) {
            e.printStackTrace();
        }
        return ret;
    }

	
    private Handler responseHandler 	= 	new Handler() {
        @Override
        public void handleMessage(Message msg) {
        	Log.e("DEBUG",responseString);
        	JSONObject jsonObject		=	null;
        	try {
				jsonObject				=	new JSONObject(responseString);
			} catch (JSONException e) {
				e.printStackTrace();
				return;
			}
			Boolean success				=	false;
			try {
				success					=	jsonObject.getBoolean("success");
			} catch (JSONException e) {
				e.printStackTrace();
				return;
			}
			if(success){
				String meta				=	null;
				try {
					meta				=	jsonObject.getString("meta");
				} catch (JSONException e) {
					return;
				}
				SharedPreferences settings 	= 	mContext.getSharedPreferences(UNIQUE_PREFERENCE_KEY, 0);
				SharedPreferences.Editor editor 	= 	settings.edit();
				editor.putString(META_USERDEFAULT_KEY, meta);
				editor.commit();
			}
        }
	};
	
	Runnable pingRunnable	=	new Runnable() {
		@Override
		public void run() {
			sendPingRequest();
			mHandler.postDelayed(pingRunnable, 12000);
		}
	};

	public void pingToServer(){
		if(mHandler != null ){
			mHandler.removeCallbacks(pingRunnable);
		}
		mHandler.postDelayed(pingRunnable, 12000);
	}
	
	public void sendPingRequest(){
		SharedPreferences settings 	= 	mContext.getSharedPreferences(UNIQUE_PREFERENCE_KEY, 0);
		String objMetaString		= 	settings.getString(META_USERDEFAULT_KEY, "");
		String urlString			=	null;
		try {
			objMetaString				=	URLEncoder.encode(objMetaString,"UTF-8");
		} catch (UnsupportedEncodingException e1) {
			e1.printStackTrace();
		}
		if(objMetaString.length() > 0){
			urlString			=	"http://"+mHostName+".woopra-ns.com/ping/?&response=json&cookie="+mCookie+"&meta="+objMetaString;
		}else{
			urlString			=	"http://"+mHostName+".woopra-ns.com/ping/?&response=json&cookie="+mCookie;
		}
	    final String uri		=	urlString;
	    
		Thread thread = new Thread(){
			@Override 
			public void run() {
				responseString	=  	webGet(uri);
				responseHandler.sendEmptyMessage(0);
			}
		};
		thread.start();
	}
}
	
