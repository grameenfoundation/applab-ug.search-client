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
 */
public class DisplaySearchResultsActivity extends Activity {
	/** for debugging purposes in adb logcat */
	private final String LOG_TAG = "DisplaySearchResultActivity";

	private static KeywordParser keywordParser;

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

	/** whether we're coming from the search activity */
	private Boolean configurationChanged = false;

	/** whether we're coming from the inbox */
	private Boolean fromInbox = false;

	private Button backButton;
	private Button deleteButton;
	private Button sendButton;

	/** database row ID for this search */
	private static long lastRowId;

	/** set true to display "Back" button for inbox list view */
	private boolean showBackButton;

	/** set true for incomplete searches */
	private boolean isIncompleteSearch;

	/** set true when updating keywords */
	private static boolean isUpdatingKeywords;

	/** inbox database */
	public InboxAdapter inboxDatabase;

	/** view for inbox search results */
	private TextView searchResultsTextView;

	private ProgressDialog progressDialog;

	private static KeywordDownloader keywordDownloader;

	private static Thread parserThread;
	private static Thread networkThread;

	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		if (savedInstanceState != null) {
			this.configurationChanged = savedInstanceState
					.getBoolean("changed");
			if (this.configurationChanged) {
				Log.w(LOG_TAG, "Activity RESTART");
			}
		}
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
		Bundle extras = this.getIntent().getExtras();

		if (extras != null) {
			this.searchResult = extras.getString("content");
			this.search = extras.getString("search");
			this.name = extras.getString("name");

			// From SearchActivity
			this.isIncompleteSearch = extras.getBoolean("send", false);
			this.request = extras.getString("request");
			this.location = extras.getString("location");
			this.fromInbox = extras.getBoolean("fromInbox");

			if (!this.configurationChanged) {
				DisplaySearchResultsActivity.lastRowId = extras
						.getLong("rowId");
			}
		}
		this.searchResultsTextView = (TextView) findViewById(R.id.content_view);
		TextView searchResultTitle = (TextView) findViewById(R.id.search);
		TextView searchDateDisplay = (TextView) findViewById(R.id.Date_time);
		this.backButton = (Button) findViewById(R.id.back_button);
		this.deleteButton = (Button) findViewById(R.id.delete_button);
		this.sendButton = (Button) findViewById(R.id.send_button);

		try {
			this.inboxDatabase = new InboxAdapter(this);
			this.inboxDatabase.open();

			if ((!this.configurationChanged) && (searchResult != null)) {
				DisplaySearchResultsActivity.lastRowId = inboxDatabase
						.insertRecord(search, searchResult, name, "",
								"Complete", "");
			} else if ((!this.configurationChanged) && isIncompleteSearch) {
				// Save as incomplete search
				DisplaySearchResultsActivity.lastRowId = inboxDatabase
						.insertRecord(search, getString(
								R.string.search_failure, search), name,
								location, "Incomplete", this.request);
			}

			if (this.fromInbox) {
				// From the list view, so return to it when done
				this.showBackButton = true;
				this.backButton.setText(getString(R.string.back_button));
			}
			/**
			 * rowId is either supplied through a bundle or at database insert
			 */
			Cursor inboxCursor = this.inboxDatabase
					.readRecord(DisplaySearchResultsActivity.lastRowId);
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
			if (this.request == null || this.request.length() == 0) {
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
			searchDateDisplay.setText(submissionTime);

			searchResultTitle.setText(inboxCursor.getString(titleColumn));

			if (!this.showBackButton) {
				this.backButton.setText(getString(R.string.new_button));
				this.backButton.setTextSize(15);
			}
		} catch (SQLException e) {

			searchResultsTextView.setText(e.toString());
		} catch (Exception e) {

			searchResultsTextView.setText(e.toString());
		}

		if (isIncompleteSearch) {
			sendButton.setEnabled(true);
		} else {
			sendButton.setEnabled(false);

			if (this.fromInbox && (!this.configurationChanged)) {
				insertLogEntry();
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
				confirmDeleteDialog().show();
			}
		});

		sendButton.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				DisplaySearchResultsActivity.isUpdatingKeywords = false;
				String requestString = getRequestString();
				showProgressDialog(Global.CONNECT_DIALOG);
				SynchronizeTask synchronizeTask = new SynchronizeTask(
						connectHandle, getApplicationContext());				
				synchronizeTask.getSearchResults(requestString);
				isIncompleteSearch = false;
			}
		});
	}

	/**
	 * retrieves base server url
	 * 
	 * @return url string
	 */
	private String getRequestString() {
		String requestString = "";
		requestString = requestString.concat("?keyword=");
		requestString = requestString.concat(this.request.replace(" ", "%20"));
		TelephonyManager mTelephonyMngr = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
		String imei = mTelephonyMngr.getDeviceId();
		try {
			submissionTime = URLEncoder.encode(submissionTime, "UTF-8");
		} catch (UnsupportedEncodingException e) {

		}
		requestString = requestString.concat("&interviewee_id="
				+ name.replace(" ", "%20") + "&handset_id=" + imei
				+ "&location=" + location + "&handset_submit_time="
				+ submissionTime);

		return requestString;
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
				progressDialog.dismiss();
				errorDialog().show();
				break;
			case Global.CONNECTION_SUCCESS:
				if (!DisplaySearchResultsActivity.isUpdatingKeywords) {
					progressDialog.dismiss();
					if (Global.data != null) {
						// Update content for this incomplete query
						inboxDatabase.updateRecord(
								DisplaySearchResultsActivity.lastRowId,
								Global.data);
						// Reload this view by restarting itself.
						Intent i = new Intent(getApplicationContext(),
								DisplaySearchResultsActivity.class);
						i.putExtra("rowId",
								DisplaySearchResultsActivity.lastRowId);
						i.putExtra("name", name);
						i.putExtra("location", location);
						startActivity(i);
						finish();
					} else {
						progressDialog.dismiss();
						errorDialog().show();
					}
				} else {
					if (Global.data.trim().endsWith("</Keywords>")) {
						showProgressDialog(Global.PARSE_DIALOG);
						DisplaySearchResultsActivity.keywordParser = new KeywordParser(
								getApplicationContext(), progressHandler,
								connectHandle);
						DisplaySearchResultsActivity.parserThread = new Thread(
								keywordParser);
						DisplaySearchResultsActivity.parserThread.start();
					} else {
						errorDialog().show();
					}
				}
				break;
			case Global.KEYWORD_PARSE_SUCCESS:
				// Release synchronization lock
				KeywordSynchronizer.completeSynchronization();
				progressDialog.dismiss();
				Toast.makeText(getApplicationContext(),
						getString(R.string.refreshed), Toast.LENGTH_LONG)
						.show();
				break;
			case Global.KEYWORD_PARSE_ERROR:
				progressDialog.dismiss();
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
						DisplaySearchResultsActivity.isUpdatingKeywords = false;
					}
				}).setNegativeButton("Retry",
						new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog, int id) {
								dialog.cancel();
								if (DisplaySearchResultsActivity.isUpdatingKeywords) {
									DisplaySearchResultsActivity.isUpdatingKeywords = true;
									getServerUrl(R.string.update_path);
									DisplaySearchResultsActivity.keywordDownloader = new KeywordDownloader(
											connectHandle,
											getServerUrl(R.string.update_path));
									DisplaySearchResultsActivity.networkThread = new Thread(
											keywordDownloader);
									DisplaySearchResultsActivity.networkThread
											.start();
									showProgressDialog(Global.UPDATE_DIALOG);
								} else {
									showProgressDialog(Global.CONNECT_DIALOG);
									SynchronizeTask synchronizeTask = new SynchronizeTask(
											connectHandle,
											getApplicationContext());
									synchronizeTask
											.getSearchResults(getRequestString());
								}
							}
						});
		AlertDialog alert = builder.create();
		return alert;
	}

	private AlertDialog confirmDeleteDialog() {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setMessage(getString(R.string.delete_alert1)).setCancelable(
				false).setPositiveButton("Yes",
				new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int id) {
						inboxDatabase.deleteRecord(
								InboxAdapter.INBOX_DATABASE_TABLE,
								DisplaySearchResultsActivity.lastRowId);
						Toast.makeText(getApplicationContext(),
								getString(R.string.record_deleted),
								Toast.LENGTH_LONG).show();

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
				}).setNegativeButton("No",
				new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int id) {
						dialog.cancel();
					}
				});
		return builder.create();
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

	/**
	 * retrieves server URL from preferences
	 * 
	 * @param id
	 *            string resource ID
	 * @return server URL string
	 */
	private String getServerUrl(int id) {
		SharedPreferences settings = PreferenceManager
				.getDefaultSharedPreferences(this);
		String url = settings.getString(Settings.KEY_SERVER, this
				.getString(R.string.server));
		url = url.concat("/" + this.getString(id));
		return url;
	}

	/**
	 * create and update progress dialogs
	 * 
	 * @param id
	 *            progress dialog type
	 */
	private void showProgressDialog(int id) {
		switch (id) {
		case Global.UPDATE_DIALOG:
			progressDialog = new ProgressDialog(this);
			progressDialog.setMessage(getString(R.string.progress_msg));
			progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
			progressDialog.setCancelable(false);
			progressDialog.setIndeterminate(true);
			progressDialog.show();
			break;
		case Global.PARSE_DIALOG:
			// Updates previously showing update dialog
			progressDialog.setIndeterminate(false);
			progressDialog.setMessage(getString(R.string.parse_msg));
			break;
		case Global.CONNECT_DIALOG:
			progressDialog = new ProgressDialog(this);
			progressDialog.setMessage(getString(R.string.progress_msg));
			progressDialog.setCancelable(false);
			progressDialog.show();
			break;
		}
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
				DisplaySearchResultsActivity.isUpdatingKeywords = true;
				// Acquire synchronization lock
				if (KeywordSynchronizer.tryStartSynchronization()) {
					showProgressDialog(Global.UPDATE_DIALOG);
					DisplaySearchResultsActivity.keywordDownloader = new KeywordDownloader(
							connectHandle, getServerUrl(R.string.update_path));
					DisplaySearchResultsActivity.networkThread = new Thread(
							keywordDownloader);
					DisplaySearchResultsActivity.networkThread.start();
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
		} else {
			menu.setGroupEnabled(1, true);
		}
		return result;
	}

	@Override
	protected void onResume() {
		super.onResume();
		Log.i(LOG_TAG, "-> onResume()");
		restoreProgressDialogs();
	}

	private void restoreProgressDialogs() {
		if (DisplaySearchResultsActivity.parserThread != null
				&& DisplaySearchResultsActivity.parserThread.isAlive()) {
			DisplaySearchResultsActivity.keywordParser.swap(this
					.getApplicationContext(), this.connectHandle,
					this.progressHandler);
			Log.i(LOG_TAG, "Parser thread is alive");
			Log.i(LOG_TAG, "Show parse dialog");
			showProgressDialog(Global.UPDATE_DIALOG);
			showProgressDialog(Global.PARSE_DIALOG);
			// Cross check that parser thread is still alive
			if (!(DisplaySearchResultsActivity.parserThread != null && DisplaySearchResultsActivity.parserThread
					.isAlive())) {
				progressDialog.dismiss();
			}
		} else if (DisplaySearchResultsActivity.networkThread != null
				&& DisplaySearchResultsActivity.networkThread.isAlive()) {
			DisplaySearchResultsActivity.keywordDownloader
					.swap(this.connectHandle);
			Log.i(LOG_TAG, "Is still downloading keywords...");
			Log.i(LOG_TAG, "Show connect dialog");
			showProgressDialog(Global.UPDATE_DIALOG);			
		}
	}

	@Override
	protected void onPause() {
		Log.i(LOG_TAG, "-> onPause()");
		if (progressDialog != null && progressDialog.isShowing()) {
			Log.i(LOG_TAG, "Remove progress dialog");
			progressDialog.dismiss();
		}
		super.onPause();
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		Log.i(LOG_TAG, "-> onSaveInstanceState()");
		// Flag for configuration changes
		outState.putBoolean("changed", true);
		// continue with the normal instance state save
		super.onSaveInstanceState(outState);
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		inboxDatabase.close();
	}
}
