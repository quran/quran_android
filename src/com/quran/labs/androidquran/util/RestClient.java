/*
 * Based on 
 * http://senior.ceng.metu.edu.tr/2009/praeda/2009/01/11/a-simple-restful-client-at-android/
 */

package com.quran.labs.androidquran.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URLEncoder;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.JSONException;
import org.json.JSONObject;

import android.util.Log;


public class RestClient {

	// connects to a given url in the context of the give activity	 
	public static JSONObject connect(String url, String[] params, String[] values) {
		HttpClient httpclient = new DefaultHttpClient();

		try {
			url = prepareRequestUrl(url, params, values);
			Log.d("URL", url);
			
			HttpGet httpget = new HttpGet(url);

			// Execute the request
			HttpResponse response;

			response = httpclient.execute(httpget);
			HttpEntity entity = response.getEntity();
			
			if (entity != null) {
                // A Simple JSON Response Read
                InputStream instream = entity.getContent();
                String result= convertStreamToString(instream);
                instream.close();
                JSONObject jsonObj = new JSONObject(result);
                Log.i("Praeda",result);
                return jsonObj;                
            }				
			// Get hold of the response entity			

		} catch (ClientProtocolException e) {
			Log.d(e.toString(), e.getMessage());			
		} catch (IOException e) {
			Log.d(e.toString(), e.getMessage());			
		} catch (JSONException e) {
			Log.d(e.toString(), e.getMessage());
		}
		return null;
	}
	
	private static String prepareRequestUrl(String url, String[] params, String[] values) {
		// Prepare a request object	
		StringBuilder urlQueryString = new StringBuilder();
		if (params != null && values != null && params.length == values.length) {
			for (int i = 0; i < params.length; i ++) {
				if (i != 0) { 
					urlQueryString.append("&");
				}
				urlQueryString.append(params[i]).append("=").append(values[i]);				
			}
		}
		if (urlQueryString.length() > 0) {
			url +=  "?" + URLEncoder.encode(urlQueryString.toString());
		}
		return url;
	}
	
	private static String convertStreamToString(InputStream is) {
		/*
		 * To convert the InputStream to String we use the BufferedReader.readLine()
		 * method. We iterate until the BufferedReader return null which means
		 * there's no more data to read. Each line will appended to a StringBuilder
		 * and returned as String.
		 */
		BufferedReader reader = new BufferedReader(new InputStreamReader(is));
		StringBuilder sb = new StringBuilder();

		String line = null;
		try {
			while ((line = reader.readLine()) != null) {
				sb.append(line + "\n");
			}
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			try {
				is.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		return sb.toString();
	}

}
