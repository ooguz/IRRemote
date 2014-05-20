/*
 * Copyright 2014 Luuk Willemsen (Twinone)
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * 
 */
package org.twinone.irremote.globalcache;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;

import com.google.gson.Gson;

public class DBConnector {

	private static final String TAG = "DBConnector";

	public void setOnDataReceivedListener(Listener listener) {
		mListener = listener;
	}

	/*
	 * Listener that will provide callbacks to the user
	 */
	private Listener mListener;

	// private UriData mUriData = new UriData();

	public void query(UriData data) {
		if (data == null)
			data = new UriData();
		queryServer(data);
	}

	private final Context mContext;

	public DBConnector(Context c) {
		this(c, null);
	}

	public DBConnector(Context c, Listener listener) {
		this.mListener = listener;
		this.mContext = c;
	}

	private void queryServer(UriData data) {
		cancelQuery();
		Log.d(TAG, "queryServer (type=" + data.target + ")");
		mDBTask = new DBTask(data);
		mDBTask.execute();
	}

	private AsyncTask<String, Void, String> mDBTask;

	private class DBTask extends AsyncTask<String, Void, String> {
		private String mUrl;
		private String mCacheName;
		private int mTarget;

		public DBTask(UriData data) {
			mUrl = data.getUrl();
			mCacheName = data.getCacheName();
			mTarget = data.target;
		}

		@Override
		protected String doInBackground(String... params) {
			String cached = SimpleCache.get(mContext, mCacheName);
			if (cached != null) {
				Log.i(TAG, "Returning cached version for " + mCacheName);
				return cached;
			}
			// If not cached, load from http
			try {
				Log.d(TAG, "Querying " + mUrl);
				URL url = new URL(mUrl);
				HttpURLConnection urlConnection = (HttpURLConnection) url
						.openConnection();

				InputStream in = new BufferedInputStream(
						urlConnection.getInputStream());
				BufferedReader br = new BufferedReader(
						new InputStreamReader(in));
				StringBuilder data = new StringBuilder();
				String tmp;
				while ((tmp = br.readLine()) != null) {
					data.append(tmp);
				}
				urlConnection.disconnect();
				// Save to cache for future access...
				SimpleCache.put(mContext, mCacheName, data.toString());

				return data.toString();
			} catch (Exception e) {
				Log.w(TAG, "Query to server failed ", e);
				return null;
			}
		}

		@Override
		protected void onPostExecute(String result) {
			Log.d(TAG, "onPostExecute");
			onQueryCompleted(result);
		}

		private void onQueryCompleted(String result) {
			triggerListenerOnReceived(mTarget, result);
		}

	}

	public void cancelQuery() {
		if (mDBTask != null && !mDBTask.isCancelled())
			mDBTask.cancel(true);
	}

	// Result may be null if connection failed!
	public void triggerListenerOnReceived(int target, String result) {
		Gson gson = new Gson();
		Object[] data = null;
		if (result != null) {
			switch (target) {
			case UriData.TYPE_MANUFACTURER:
				data = gson.fromJson(result, Manufacturer[].class);
				break;
			case UriData.TYPE_DEVICE_TYPE:
				data = gson.fromJson(result, DeviceType[].class);
				break;
			case UriData.TYPE_CODESET:
				data = gson.fromJson(result, Codeset[].class);
				break;
			case UriData.TYPE_IR_CODE:
				data = gson.fromJson(result, IrCode[].class);
				break;
			}
		}
		if (mListener != null)
			mListener.onReceiveData(target, data);
	}

	public interface Listener {
		/**
		 * Called when data was returned from the DB
		 * 
		 * @param data
		 *            may be null if the connection failed
		 */
		public void onReceiveData(int type, Object[] data);
	}

}
