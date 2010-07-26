package yo.applab.ckwinfo;

import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.util.Log;

/**
 * This code runs on a background timer thread during synchronization
 */
public class SubmitIncompleteSearches {
	/** an identifier for debugging purposes **/
	private final String LOG_TAG = "SubmitIncomplete";
	private String serverUrl;
	private Context applicationContext;

	public SubmitIncompleteSearches(Context applicationContext, String uri) {
		this.applicationContext = applicationContext;
		this.serverUrl = uri;
	}

	/**
	 * attempts to submit incomplete searches
	 */
	public void updateIncompleteSearches() {
		final ContentValues contentValues = new ContentValues();
		final InboxAdapter inboxAdapter = new InboxAdapter(
				this.applicationContext);
		inboxAdapter.open();
		Thread connectThread = new Thread() {
			public void run() {
				Log.i(LOG_TAG, "Updating incomplete searches...");
				getIncompleteSearch(inboxAdapter, contentValues);
				while (contentValues.size() > 0) {
					Log.i(LOG_TAG, "Contents size: " + contentValues.size());
					try {
						URL url = new URL(serverUrl
								+ contentValues.getAsString("request"));
						String results = DownloadManager.retrieveData(url);
						if (results != null) {
							inboxAdapter.updateRecord(contentValues
									.getAsLong("id"), results);
							getIncompleteSearch(inboxAdapter, contentValues);
						}
					} catch (MalformedURLException e) {
						Log
								.d(LOG_TAG, "MalformedURLException: "
										+ e.toString());
					}
				}
				inboxAdapter.close();
			}
		};
		connectThread.start();
	}

	/**
	 * populates request URI and ID content values
	 */
	private void getIncompleteSearch(InboxAdapter inboxAdapter,
			ContentValues contentValues) {
		contentValues.clear();
		Cursor searchTableCursor = inboxAdapter.getPendingSearches();
		if (searchTableCursor.getCount() > 0) {
			int dateColumn = searchTableCursor
					.getColumnIndexOrThrow(InboxAdapter.KEY_DATE);
			int requestColumn = searchTableCursor
					.getColumnIndexOrThrow(InboxAdapter.KEY_REQUEST);
			int nameColumn = searchTableCursor
					.getColumnIndexOrThrow(InboxAdapter.KEY_NAME);
			int locationColumn = searchTableCursor
					.getColumnIndexOrThrow(InboxAdapter.KEY_LOCATION);
			int idColumn = searchTableCursor
					.getColumnIndexOrThrow(InboxAdapter.KEY_ROWID);
			String submissionTime = searchTableCursor.getString(dateColumn);
			String intervieweeId = searchTableCursor.getString(nameColumn);
			String keyword = searchTableCursor.getString(requestColumn);
			String location = searchTableCursor.getString(locationColumn);
			long searchTableId = searchTableCursor.getLong(idColumn);
			try {
				String uri = "?&handset_submit_time="
						+ URLEncoder.encode(submissionTime, "UTF-8")
						+ "&interviewee_id="
						+ URLEncoder.encode(intervieweeId, "UTF-8")
						+ "&keyword=" + URLEncoder.encode(keyword, "UTF-8")
						+ "&location=" + URLEncoder.encode(location, "latin1")
						+ "&handset_id="
						+ URLEncoder.encode(Global.IMEI, "UTF-8");
				contentValues.put("request", uri);
				contentValues.put("id", searchTableId);

			} catch (UnsupportedEncodingException e) {
				Log.e(LOG_TAG, "Bad URL: " + e);
			}
		}
	}
}
