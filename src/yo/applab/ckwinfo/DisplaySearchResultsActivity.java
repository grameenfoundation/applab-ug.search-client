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
import android.database.Cursor;
import android.database.SQLException;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.telephony.TelephonyManager;
import android.util.Log;
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
	private final String LOG_TAG = "DisplaySearchResultActivity";

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
	private boolean isIncompleteSearch = false;

	/** set true when updating keywords */
	private boolean isUpdatingKeywords = false;

	/** set true if this inbox access should be logged */
	private boolean isCachedSearch = true;

	/** inbox database */
	public InboxAdapter inboxDatabase;

	/** view for inbox search results */
	private TextView searchResultsTextView;

	/** dialog shown during database initialization */
	private static final int PARSE_DIALOG = 1;

	/** dialog shown when accessing networkThread resources */
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
		this.searchDatabase = new Storage(this);

		this.keywordParser = new KeywordParser(this.getApplicationContext(),
				progressHandler, connectHandle);
		Bundle extras = this.getIntent().getExtras();
		if (extras != null) {
			this.searchResult = extras.getString("content");
			this.search = extras.getString("search");
			this.name = extras.getString("name");
			this.rowId = extras.getLong("rowId");
			// From SearchActivity
			this.isIncompleteSearch = extras.getBoolean("send", false);
			this.request = extras.getString("request");
			this.location = extras.getString("location");
		}
		this.searchResultsTextView = (TextView) findViewById(R.id.content_view);
		TextView mSearch = (TextView) findViewById(R.id.search);
		TextView mDate = (TextView) findViewById(R.id.Date_time);
		this.backButton = (Button) findViewById(R.id.back_button);
		this.deleteButton = (Button) findViewById(R.id.delete_button);
		this.sendButton = (Button) findViewById(R.id.send_button);

		try {
			this.inboxDatabase = new InboxAdapter(this);
			this.inboxDatabase.open();
			if (searchResult != null) {
				this.rowId = inboxDatabase.insertRecord(search, searchResult,
						name, "", "Complete", "");
				// Newly completed search. Do not log for submission.
				isCachedSearch = false;
			} else if (isIncompleteSearch) {
				// Save as incomplete search
				this.rowId = inboxDatabase.insertRecord(search, getString(
						R.string.search_failure, search), name, location,
						"Incomplete", request);
			} else {
				// From the list view, so return to it when done
				this.showBackButton = true;
				this.backButton.setText(getString(R.string.back_button));
			}
			if (!this.showBackButton) {
				this.backButton.setText(getString(R.string.new_button));
				this.backButton.setTextSize(15);
			}
			/**
			 * rowId is either supplied through a bundle or at database insert
			 */
			Cursor inboxCursor = this.inboxDatabase.readRecord(rowId);
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
				this.isIncompleteSearch = true;
			}
			if (this.request == null || (this.request.length() == 0)) {
				this.request = inboxCursor.getString(requestColumn);
			}
			if (this.location == null || (this.location.length() == 0)) {
				this.location = inboxCursor.getString(locationColumn);
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

		if (isIncompleteSearch) {
			sendButton.setEnabled(true);
		} else {
			sendButton.setEnabled(false);
			if (isCachedSearch) {
				insertLogEntry();
				// We will submit the log at the next scheduled synchronization
			}

		}
		if (isIncompleteSearch) {
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
				String requestString = getRequestString();
				showDialog(CONNECT_DIALOG);
				SynchronizeTask synchronizeTask = new SynchronizeTask(
						connectHandle, getApplicationContext());
				synchronizeTask.getSearchResults(requestString);
				isIncompleteSearch = false;
			}
		});
		inboxDatabase.close();
	}

	/**
	 * retrieves base server url
	 * 
	 * @return url string
	 */
	private String getRequestString() {
		String request = "";
		request = request.concat("?keyword=");
		request = request.concat(request.replace(" ", "%20"));
		TelephonyManager mTelephonyMngr = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
		String imei = mTelephonyMngr.getDeviceId();
		try {
			submissionTime = URLEncoder.encode(submissionTime, "UTF-8");
		} catch (UnsupportedEncodingException e) {

		}
		request = request.concat("&interviewee_id=" + name.replace(" ", "%20")
				+ "&handset_id=" + imei + "&location=" + location
				+ "&handset_submit_time=" + submissionTime);

		return request;
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
	 * handles responses from networkThread layer
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
				// Release synchronization lock
				KeywordSynchronizer.completeSynchronization();
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
						// Release synchronization lock
						KeywordSynchronizer.completeSynchronization();
						isUpdatingKeywords = false;
					}
				}).setNegativeButton("Retry",
						new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog, int id) {
								dialog.cancel();
								showDialog(CONNECT_DIALOG);
								SynchronizeTask synchronizeTask = new SynchronizeTask(
										connectHandle, getApplicationContext());
								if (isUpdatingKeywords) {
									synchronizeTask.updateKeywords();
								} else {
									synchronizeTask
											.getSearchResults(getRequestString());
								}
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
		menu.add(1, Global.REFRESH_ID, 0, getString(R.string.menu_refresh))
				.setIcon(R.drawable.refresh);
		menu.add(0, Global.ABOUT_ID, 0, getString(R.string.menu_about))
				.setIcon(R.drawable.about);
		menu.add(0, Global.EXIT_ID, 0, getString(R.string.menu_exit)).setIcon(
				R.drawable.exit);
		return result;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case Global.RESET_ID:
			if (!KeywordSynchronizer.isSynchronizing()) {
				// Get synchronization lock
				if (KeywordSynchronizer.tryStartSynchronization()) {
					Intent i = new Intent(getApplicationContext(),
							SearchActivity.class);
					startActivity(i);
					finish();
				} else {
					Log.i(LOG_TAG, "Failed to get synchronization lock");
				}
			}
			return true;
		case Global.INBOX_ID:
			Intent j = new Intent(getApplicationContext(),
					InboxListActivity.class);
			startActivity(j);
			finish();
			return true;
		case Global.REFRESH_ID:
			if (!KeywordSynchronizer.isSynchronizing()) {
				isUpdatingKeywords = true;
				// Acquire synchronization lock
				if (KeywordSynchronizer.tryStartSynchronization()) {
					SynchronizeTask synchronizeTask = new SynchronizeTask(
							this.connectHandle, this.getApplicationContext());
					showDialog(CONNECT_DIALOG);
					synchronizeTask.updateKeywords();
				} else {
					Log.i(LOG_TAG, "Failed to get synchronization lock");
				}
			}
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
	public boolean onPrepareOptionsMenu(Menu menu) {
		boolean result = super.onPrepareOptionsMenu(menu);
		// Disable keyword update if background update is running
		if (KeywordSynchronizer.isSynchronizing()) {
			// Disable keyword updates and new searches
			menu.setGroupEnabled(1, false);
		}
		return result;
	}

	@Override
	protected void onPause() {
		super.onPause();
	}

	@Override
	protected void onStop() {
		super.onStop();
	}

	@Override
	public void onRestoreInstanceState(Bundle savedInstanceState) {
		super.onRestoreInstanceState(savedInstanceState);
		android.util.Log.e(LOG_TAG, "-> onRestoreInstanceState()");
		if (progressDialog != null) {
			progressDialog.dismiss();
		}
	}

}
