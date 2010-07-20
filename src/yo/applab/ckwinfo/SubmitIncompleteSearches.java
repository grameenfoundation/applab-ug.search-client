package yo.applab.ckwinfo;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
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
	private final String LOG_TAG = "Submit_Log";
	private String serverUri;
	private Context applicationContext;	

	public SubmitIncompleteSearches(Context applicationContext, String uri) {
		this.applicationContext = applicationContext;
		this.serverUri = uri;
	}

	/**
	 * attempts to submit incomplete searches
	 */
	public void updateIncompleteSearches() {
		final ContentValues contentValues = new ContentValues();
		final InboxAdapter inboxAdapter = new InboxAdapter(this.applicationContext);
		inboxAdapter.open();

		Thread connectThread = new Thread() {
			public void run() {
				URI uri;
				try {
					uri = new URI(serverUri
							+ contentValues.getAsString("request"));					
					String results = DownloadManager.retrieveData(uri);
					if (results != null) {
						inboxAdapter.updateRecord(
								contentValues.getAsLong("ID"), results);
						getIncompleteSearch(inboxAdapter, contentValues);
					}
				} catch (URISyntaxException e) {
					Log.d(LOG_TAG, "URISyntaxException: " + e.toString());
				}

			}
		};
		Log.i(LOG_TAG, "Content value size: "
				+ Integer.toString(contentValues.size()));
		if (contentValues.size() > 0) {
			connectThread.start();
		} else {
			inboxAdapter.close();
		}

	}

	/**
	 * populates request URI and ID content values
	 */
	private void getIncompleteSearch(InboxAdapter inboxAdapter, ContentValues contentValues) {
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
				contentValues.put("ID", searchTableId);

			} catch (UnsupportedEncodingException e) {
				Log.e(LOG_TAG, "Bad URL: " + e);
			}
		}

	}
}
