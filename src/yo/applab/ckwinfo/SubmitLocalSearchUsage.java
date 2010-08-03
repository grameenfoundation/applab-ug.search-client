/**
 * Copyright (C) 2010 Grameen Foundation
Licensed under the Apache License, Version 2.0 (the "License"); you may not
use this file except in compliance with the License. You may obtain a copy of
the License at http://www.apache.org/licenses/LICENSE-2.0
Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
License for the specific language governing permissions and limitations under
the License.
 */
package yo.applab.ckwinfo;

import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

import android.content.Context;
import android.database.Cursor;
import android.util.Log;

/**
 * Looks for any usage logs pending for submission and attempts to submit them.
 */
public class SubmitLocalSearchUsage {
	/** an identifier for debugging purposes **/
	private final String LOG_TAG = "SubmitUsageLog";

	/** inbox content display activity **/

	/** database row ID for the current log **/
	private long logId;

	/** server base URL **/
	private String serverBaseUrl;

	private Context applicationContext;

	private InboxAdapter inboxAdapter;

	SubmitLocalSearchUsage(Context appContext, String url) {
		this.applicationContext = appContext;
		this.serverBaseUrl = url;
	}

	/**
	 * Submits any available inbox access logs. Deletes logs once successfully
	 * submitted.
	 */
	public void sendLogs() {
		inboxAdapter = new InboxAdapter(applicationContext);
		inboxAdapter.open();
		Thread submitThread = new Thread() {
			public void run() {
				Log.e(LOG_TAG, "Submitting usage logs...");
				URL url;
				HttpURLConnection connection = null;
				String urlParameters = createUrlParams();
				try {
					while (urlParameters.length() > 0) {
						serverBaseUrl = serverBaseUrl.concat(urlParameters);
						url = new URL(serverBaseUrl);
						connection = (HttpURLConnection) url.openConnection();
						connection.setConnectTimeout(Global.TIMEOUT);
						connection.setReadTimeout(Global.TIMEOUT);
						connection.setRequestMethod("GET");
						connection.connect();
						int response = connection.getResponseCode();
						if (response == HttpURLConnection.HTTP_OK) {
							if (inboxAdapter.deleteRecord(
									InboxAdapter.ACCESS_LOG_DATABASE_TABLE,
									logId)) {
								urlParameters = createUrlParams();
							} else {
								break;
							}
						} else {
							break;
						}
					}

				} catch (Exception e) {
					Log.e(LOG_TAG, "Exception: " + e.toString());
				} finally {
					if (connection != null) {
						connection.disconnect();
					}
					if (inboxAdapter != null) {
						inboxAdapter.close();
					}
				}
			}
		};
		submitThread.start();
	}

	/**
	 * Creates the URL parameters.
	 * 
	 * @return URL parameters or an empty string if no parameters are available.
	 */
	private String createUrlParams() {
		String urlParams = "";
		if (inboxAdapter == null) {
			inboxAdapter.open();
		}
		// get the oldest log entry from the database
		Cursor logTableCursor = inboxAdapter
				.readLog(InboxAdapter.ACCESS_LOG_DATABASE_TABLE);

		if (logTableCursor.getCount() > 0) {
			int dateColumn = logTableCursor
					.getColumnIndexOrThrow(InboxAdapter.KEY_DATE);
			int requestColumn = logTableCursor
					.getColumnIndexOrThrow(InboxAdapter.KEY_REQUEST);
			int nameColumn = logTableCursor
					.getColumnIndexOrThrow(InboxAdapter.KEY_NAME);
			int idColumn = logTableCursor
					.getColumnIndexOrThrow(InboxAdapter.KEY_ROWID);
			String submissionTime = logTableCursor.getString(dateColumn);
			String intervieweeId = logTableCursor.getString(nameColumn);
			String keyword = logTableCursor.getString(requestColumn);
			logId = logTableCursor.getLong(idColumn);
			try {
				urlParams = "?log=true" + "&handset_submit_time="
						+ URLEncoder.encode(submissionTime, "UTF-8")
						+ "&interviewee_id="
						+ URLEncoder.encode(intervieweeId, "UTF-8")
						+ "&keyword=" + URLEncoder.encode(keyword, "UTF-8")
						+ "&location="
						+ URLEncoder.encode(Global.location, "UTF-8")
						+ "&handset_id="
						+ URLEncoder.encode(Global.IMEI, "UTF-8");
			} catch (UnsupportedEncodingException e) {
				Log.e(LOG_TAG, "Bad URL: " + e);
			}
		}
		return urlParams;

	}
}
