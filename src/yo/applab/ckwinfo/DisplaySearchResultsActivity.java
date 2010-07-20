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
import java.net.URLEncoder;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.SQLException;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.telephony.TelephonyManager;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Displays search results.
 * 
 */
public class DisplaySearchResultsActivity extends Activity {
	/** for debugging purposes in adb logcat */
	private final String DEBUG_TAG = "Display";

	private SubmitLocalSearchUsage submitInboxLog;
	private KeywordDownloader keywordDownloader;
	private KeywordParser keywordParser;

	/** keywords database */
	public Storage searchDatabase;

	/** interviewee name or ID */
	private String name = "";

	/** search result */
	private String searchResult = "";
	// TODO OKP-1#CFR-29, change distinction between search and request
	/** keywords displayed in inbox */
	private String search = "";

	/** keywords server request */
	private String request = "";

	/** stored location for this search */
	private String location = "";

	/** handset submission time */
	private String submissionTime = "";

	private Button backButton;
	private Button deleteButton;
	private Button sendButton;

	/** database row ID for this search */
	private long rowId;

	/** set true to display "Back" button for inbox list view */
	private boolean showBackButton = false;

	/** set true for incomplete searches */
	private boolean incomplete = false;

	/** set true when updating keywords */
	private boolean isUpdatingKeywords = false;

	/** set true if this inbox access should be logged */
	private boolean isCachedSearch = true;

	/** inbox database */
	public InboxAdapter inboxDatabase;

	private Thread network;

	/** view for inbox search results */
	private TextView searchResultsTextView;

	/** dialog shown during database initialization */
	private static final int PARSE_DIALOG = 1;

	/** dialog shown when accessing network resources */
	private static final int CONNECT_DIALOG = 2;

	private ProgressDialog progressDialog;

	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.inbox);
		String activityTitle = getString(R.string.inbox_title) + " | ";
		if (Global.intervieweeName.length() > 30) {
			activityTitle = activityTitle.concat(Global.intervieweeName
					.substring(0, 30));
			activityTitle = activityTitle.concat("...");
		} else {
			activityTitle = activityTitle.concat(Global.intervieweeName);
		}
		setTitle(activityTitle);
		searchDatabase = new Storage(this);
		keywordDownloader = new KeywordDownloader(this.connectHandle);
		keywordParser = new KeywordParser(this.getApplicationContext(),
				progressHandler, connectHandle);
		Bundle extras = this.getIntent().getExtras();
		if (extras != null) {
			searchResult = extras.getString("content");
			search = extras.getString("search");
			name = extras.getString("name");
			rowId = extras.getLong("rowId");
			// From SearchActivity
			incomplete = extras.getBoolean("send", false);
			request = extras.getString("request");
			location = extras.getString("location");
		}
		searchResultsTextView = (TextView) findViewById(R.id.content_view);
		TextView mSearch = (TextView) findViewById(R.id.search);
		TextView mDate = (TextView) findViewById(R.id.Date_time);
		backButton = (Button) findViewById(R.id.back_button);
		deleteButton = (Button) findViewById(R.id.delete_button);
		sendButton = (Button) findViewById(R.id.send_button);

		try {
			inboxDatabase = new InboxAdapter(this);
			inboxDatabase.open();
			if (searchResult != null) {
				rowId = inboxDatabase.insertRecord(search, searchResult, name,
						"", "Complete", "");
				// Newly completed search. Do not log for submission.
				isCachedSearch = false;
			} else if (incomplete) {
				// Save as incomplete search
				rowId = inboxDatabase.insertRecord(search, getString(
						R.string.search_failure, search), name, location,
						"Incomplete", request);
			} else {
				// From the list view, so return to it when done
				showBackButton = true;
				backButton.setText(getString(R.string.back_button));
			}
			if (!showBackButton) {
				backButton.setText(getString(R.string.new_button));
				backButton.setTextSize(15);
			}
			/**
			 * rowId is either supplied through a bundle or at database insert
			 */
			Cursor inboxCursor = inboxDatabase.readRecord(rowId);
			int titleColumn = inboxCursor
					.getColumnIndexOrThrow(InboxAdapter.KEY_TITLE);
			int bodyColumn = inboxCursor
					.getColumnIndexOrThrow(InboxAdapter.KEY_BODY);
			int dateColumn = inboxCursor
					.getColumnIndexOrThrow(InboxAdapter.KEY_DATE);
			int statusColumn = inboxCursor
					.getColumnIndexOrThrow(InboxAdapter.KEY_STATUS);
			int nameColumn = inboxCursor
					.getColumnIndexOrThrow(InboxAdapter.KEY_NAME);
			int requestColumn = inboxCursor
					.getColumnIndexOrThrow(InboxAdapter.KEY_REQUEST);
			int locationColumn = inboxCursor
					.getColumnIndexOrThrow(InboxAdapter.KEY_LOCATION);
			if ((inboxCursor.getString(statusColumn))
					.contentEquals("Incomplete")) {
				incomplete = true;
			}
			if (request == null || (request.length() == 0)) {
				request = inboxCursor.getString(requestColumn);
			}
			if (location == null || (location.length() == 0)) {
				location = inboxCursor.getString(locationColumn);
			}
			if (name == null || (name.length() == 0)) {
				name = inboxCursor.getString(nameColumn);
			}

			submissionTime = inboxCursor.getString(dateColumn);
			searchResultsTextView.setText(inboxCursor.getString(bodyColumn));
			mDate.setText(submissionTime);
			request = inboxCursor.getString(titleColumn);
			mSearch.setText(request);
		} catch (SQLException e) {

			searchResultsTextView.setText(e.toString());
		} catch (Exception e) {

			searchResultsTextView.setText(e.toString());
		}

		if (incomplete) {
			sendButton.setEnabled(true);
		} else {
			sendButton.setEnabled(false);
			if (isCachedSearch) {
				insertLogEntry();
				//We will submit the log at the next scheduled synchronization
			}

		}
		if (incomplete) {
			backButton.setOnClickListener(new OnClickListener() {
				public void onClick(View v) {
					if (showBackButton) {
						Intent i = new Intent(getApplicationContext(),
								InboxListActivity.class);
						i.putExtra("name", name);
						i.putExtra("location", location);
						startActivity(i);
						finish();
					} else {
						Intent i = new Intent(getApplicationContext(),
								SearchActivity.class);
						i.putExtra("name", name);
						i.putExtra("location", location);
						startActivity(i);
						finish();
					}
				}

			});
		} else {
			backButton.setOnClickListener(new OnClickListener() {
				public void onClick(View v) {
					if (showBackButton) {
						Intent i = new Intent(getApplicationContext(),
								InboxListActivity.class);
						i.putExtra("name", name);
						i.putExtra("location", location);
						startActivity(i);
						finish();
					} else {
						Intent i = new Intent(getApplicationContext(),
								SearchActivity.class);
						i.putExtra("name", name);
						i.putExtra("location", location);
						startActivity(i);
						finish();
					}
				}

			});
		}
		deleteButton.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {

				if (inboxDatabase.deleteRecord(
						InboxAdapter.INBOX_DATABASE_TABLE, rowId)) {
					Toast.makeText(getApplicationContext(),
							getString(R.string.record_deleted),
							Toast.LENGTH_LONG).show();
				} else {
					Toast.makeText(getApplicationContext(),
							getString(R.string.record_notdeleted),
							Toast.LENGTH_LONG).show();
				}

				if (showBackButton) {
					Intent i = new Intent(getApplicationContext(),
							InboxListActivity.class);
					i.putExtra("name", name);
					i.putExtra("location", location);
					startActivity(i);
					finish();
				} else {
					Intent i = new Intent(getApplicationContext(),
							SearchActivity.class);
					i.putExtra("name", name);
					i.putExtra("location", location);
					startActivity(i);
					finish();
				}
			}

		});

		sendButton.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				isUpdatingKeywords = false;
				String url = getURL();
				url = url.concat("?keyword=");
				url = url.concat(request.replace(" ", "%20"));
				TelephonyManager mTelephonyMngr = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
				String imei = mTelephonyMngr.getDeviceId();
				try {
					submissionTime = URLEncoder.encode(submissionTime, "UTF-8");
				} catch (UnsupportedEncodingException e) {

				}
				url = url.concat("&interviewee_id=" + name.replace(" ", "%20")
						+ "&handset_id=" + imei + "&location=" + location
						+ "&handset_submit_time=" + submissionTime);
				Global.URL = url;
				network = new Thread(keywordDownloader);
				showDialog(CONNECT_DIALOG);
				network.start();
				incomplete = false;
			}
		});
		inboxDatabase.close();
	}

	/**
	 * retrieves base server url
	 * 
	 * @return url string
	 */
	private String getURL() {
		SharedPreferences settings = PreferenceManager
				.getDefaultSharedPreferences(getBaseContext());
		String url = settings.getString(Settings.KEY_SERVER,
				getString(R.string.server));
		if (url.endsWith("/")) {
			url = url.concat(getString(R.string.search_path));
		} else {
			url = url.concat("/" + getString(R.string.search_path));
		}

		return url;
	}

	@Override
	protected Dialog onCreateDialog(int id) {
		switch (id) {
		case CONNECT_DIALOG:
			progressDialog = new ProgressDialog(this);
			progressDialog.setMessage(getString(R.string.progress_msg));
			progressDialog.setIndeterminate(true);
			progressDialog.setCancelable(false);
			return progressDialog;
		case PARSE_DIALOG:
			progressDialog = new ProgressDialog(this);
			progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
			progressDialog.setMessage(getString(R.string.parse_msg));
			progressDialog.setCancelable(false);
			return progressDialog;
		}
		return null;
	}

	/**
	 * updates database initialization progress
	 */
	final Handler progressHandler = new Handler() {
		public void handleMessage(Message msg) {
			int level = msg.getData().getInt("node");
			progressDialog.setProgress(level);
		}
	};

	/**
	 * handles responses from network layer
	 */
	public Handler connectHandle = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {
			case Global.CONNECTION_ERROR:
				dismissDialog(CONNECT_DIALOG);
				errorDialog().show();
				break;
			case Global.CONNECTION_SUCCESS:
				if (!isUpdatingKeywords) {
					dismissDialog(CONNECT_DIALOG);
					if (Global.data != null) {
						inboxDatabase.open();
						// Update content for this incomplete query
						inboxDatabase.updateRecord(rowId, Global.data);
						inboxDatabase.close();
						// Reload this view by restarting itself.
						Intent i = new Intent(getApplicationContext(),
								DisplaySearchResultsActivity.class);
						i.putExtra("rowId", rowId);
						i.putExtra("name", name);
						i.putExtra("location", location);
						startActivity(i);
						finish();
					} else {
						dismissDialog(CONNECT_DIALOG);
						errorDialog().show();
					}
				} else {
					dismissDialog(CONNECT_DIALOG);
					if (Global.data.trim().endsWith("</Keywords>")) {
						showDialog(PARSE_DIALOG);
						Thread parse = new Thread(keywordParser);
						parse.start();
						isUpdatingKeywords = false;
					} else {
						errorDialog().show();
					}
				}
				break;
			case Global.KEYWORD_PARSE_SUCCESS:
				dismissDialog(PARSE_DIALOG);
				Toast.makeText(getApplicationContext(),
						getString(R.string.refreshed), Toast.LENGTH_LONG)
						.show();
				break;
			case Global.KEYWORD_PARSE_ERROR:
				dismissDialog(PARSE_DIALOG);
				errorDialog().show();
				break;
			}
		}
	};

	/**
	 * Error alert dialog builder.
	 * 
	 * @return A dialog.
	 */
	public AlertDialog errorDialog() {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setMessage(R.string.connection_error).setCancelable(false)
				.setPositiveButton("OK", new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int id) {
						dialog.cancel();
						isUpdatingKeywords = false;
					}
				}).setNegativeButton("Retry",
						new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog, int id) {
								dialog.cancel();
								network = new Thread(keywordDownloader);
								showDialog(CONNECT_DIALOG);
								network.start();
							}
						});
		AlertDialog alert = builder.create();
		return alert;
	}

	/**
	 * Parse error alert dialog builder.
	 * 
	 * @return A dialog.
	 */
	public AlertDialog xmlDialog() {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setMessage("Error: Malformed XML").setCancelable(false)
				.setPositiveButton("OK", new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int id) {
						dialog.cancel();
					}
				});
		AlertDialog alert = builder.create();
		return alert;
	}

	/**
	 * makes an inbox access log entry
	 * 
	 * @return the last table insert ID
	 */
	private long insertLogEntry() {
		ContentValues log = new ContentValues();
		DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		Date date = new Date();
		log.put(InboxAdapter.KEY_REQUEST, request.replace(">", ""));
		log.put(InboxAdapter.KEY_DATE, dateFormat.format(date));
		log.put(InboxAdapter.KEY_NAME, Global.intervieweeName);
		return inboxDatabase.insertLog(InboxAdapter.ACCESS_LOG_DATABASE_TABLE,
				log);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {

		boolean result = super.onCreateOptionsMenu(menu);
		menu.add(0, Global.HOME_ID, 0, getString(R.string.menu_home)).setIcon(
				R.drawable.home);
		menu.add(1, Global.RESET_ID, 0, getString(R.string.menu_reset))
				.setIcon(R.drawable.search);
		menu.add(0, Global.INBOX_ID, 0, getString(R.string.menu_inbox))
				.setIcon(R.drawable.folder);
		menu.add(0, Global.REFRESH_ID, 0, getString(R.string.menu_refresh))
				.setIcon(R.drawable.refresh);
		menu.add(0, Global.ABOUT_ID, 0, getString(R.string.menu_about))
				.setIcon(R.drawable.about);
		menu.add(0, Global.EXIT_ID, 0, getString(R.string.menu_exit)).setIcon(
				R.drawable.exit);

		if (Global.intervieweeName == null) {
			menu.setGroupEnabled(1, false);
		}
		return result;

	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {

		switch (item.getItemId()) {
		case Global.RESET_ID:
			Intent i = new Intent(getApplicationContext(), SearchActivity.class);
			startActivity(i);
			finish();
			return true;
		case Global.INBOX_ID:
			Intent j = new Intent(getApplicationContext(),
					InboxListActivity.class);
			startActivity(j);
			finish();
			return true;
		case Global.REFRESH_ID:
			isUpdatingKeywords = true;
			searchDatabase.open();
			SharedPreferences settings = PreferenceManager
					.getDefaultSharedPreferences(getBaseContext());
			String url = settings.getString(Settings.KEY_SERVER,
					getString(R.string.server));
			if (url.endsWith("/")) {
				url = url.concat(getString(R.string.update_path));
			} else {
				url = url.concat("/" + getString(R.string.update_path));
			}
			Global.URL = url;
			network = new Thread(keywordDownloader);
			showDialog(CONNECT_DIALOG);
			network.start();
			return true;
		case Global.HOME_ID:
			Intent l = new Intent(getApplicationContext(),
					MainMenuActivity.class);
			startActivity(l);
			finish();
			return true;
		case Global.ABOUT_ID:
			Intent k = new Intent(getApplicationContext(), AboutActivity.class);
			startActivity(k);
			return true;
		case Global.EXIT_ID:

			this.finish();
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	@Override
	protected void onPause() {
		super.onPause();
	}

	@Override
	protected void onStop() {
		super.onStop();
	}

}
