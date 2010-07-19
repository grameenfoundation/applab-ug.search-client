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
import java.util.ArrayList;
import java.util.Date;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.telephony.TelephonyManager;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Responsible for constructing, displaying keyword option sequences, and
 * submitting search queries.
 * 
 * @author Eric Lwanga (elwanga@yo.co.ug)
 * 
 */

public class SearchActivity extends Activity {
	/** for debugging purposes in adb logcat */
	private static final String DEBUG_TAG = "Radio";

	/** dialog shown between search sequences */
	private static final int LOAD_DIALOG = 1;

	/** dialog shown when accessing network resources */
	private static final int CONNECT_DIALOG = 2;

	/** dialog shown during database initialization */
	private static final int PARSE_DIALOG = 3;

	/** database where search keywords are stored */
	private Storage searchDatabase;

	/** smaller "Next" button */
	private Button nextButtonSmall;

	/** larger "Next" button */
	private Button nextButtonLarge;

	private Button backButton;

	/** Layout for first search sequence */
	private LinearLayout startLayout;

	/** Layout for all but first search sequence */
	private LinearLayout layout;

	private RadioGroup keywordChoices;

	/** view where search path is displayed */
	private TextView searchPath;

	private ProgressDialog progressDialog;

	/** holds selected keywords */
	private ArrayList<String> selectedKeywords;

	private Thread network;

	private String location;
	private String submissionTime;

	/** the active search keywords database table */
	private String activeDatabaseTable;

	/** holds the selected radio button ID */
	private int radioId;

	/** when set true search query can be submitted */
	private boolean canSubmitQuery = false;

	/** set true when there are no more keywords in a given sequence */
	private boolean endOfKeywordSequence = false;

	/** set true when updating keywords */
	private boolean isUpdatingKeywords = false;

	/** search sequence number */
	private int sequence = 0;

	private KeywordParser keywordParser;
	private KeywordDownloader keywordDownloader;

	/** holds search state */
	private ActivityState searchStateData;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		// setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
		setContentView(R.layout.main);
		String activityTitle = getString(R.string.app_name) + " | ";
		if (Global.intervieweeName.length() > 30) {
			activityTitle = activityTitle.concat(Global.intervieweeName
					.substring(0, 30));
			activityTitle = activityTitle.concat("...");
		} else {
			activityTitle = activityTitle.concat(Global.intervieweeName);
		}
		setTitle(activityTitle);
		activeDatabaseTable = getTable();
		keywordChoices = (RadioGroup) findViewById(R.id.radio_group);
		keywordChoices.bringToFront();
		nextButtonSmall = (Button) findViewById(R.id.next_button);
		nextButtonLarge = (Button) findViewById(R.id.next);
		backButton = (Button) findViewById(R.id.back_button);
		searchPath = (TextView) findViewById(R.id.search);
		startLayout = (LinearLayout) findViewById(R.id.startLayout);
		nextButtonSmall.setText(getString(R.string.next_button));
		layout = (LinearLayout) findViewById(R.id.layout);
		backButton.setText(getString(R.string.back_button));

		final ActivityState data = (ActivityState) getLastNonConfigurationInstance();
		if (data == null) {
			layout.setVisibility(View.GONE);
			searchPath.setTextColor(R.drawable.black);
			searchPath.bringToFront();

			keywordDownloader = new KeywordDownloader(this.connectHandle);
			keywordParser = new KeywordParser(this.getApplicationContext(),
					progressHandler, connectHandle);
			searchDatabase = new Storage(this);
			searchDatabase.open();
			selectedKeywords = new ArrayList<String>();
			buildRadioList();
		} else {
			searchStateData = data;
			selectedKeywords = new ArrayList<String>();
			searchDatabase = new Storage(this);
			searchDatabase.open();
			sequence = data.myField;
			selectedKeywords = data.mySelect;
			canSubmitQuery = data.canSubmitQuery;
			endOfKeywordSequence = data.endOfKeywordSequence;
			String query = "Search:";
			for (int i = 0; i < selectedKeywords.size(); i++) {
				query = query.concat(" >" + selectedKeywords.get(i));
			}
			searchPath.setText(query);
			if (sequence > 0)
				startLayout.setVisibility(View.GONE);
			else
				layout.setVisibility(View.GONE);
			buildRadioList();
		}
		nextButtonLarge.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				radioId = keywordChoices.getCheckedRadioButtonId();

				if (radioId != -1) {
					RadioButton rb = (RadioButton) findViewById(radioId);
					String choice = rb.getText().toString();
					String query = "Search: ";
					selectedKeywords.add(new String(choice));
					query = query.concat(">" + choice);
					searchPath.setText(query);
					++sequence;
					buildRadioList();
					startLayout.setVisibility(View.GONE);
					layout.setVisibility(View.VISIBLE);
				} else {
					Toast toast = Toast.makeText(getApplicationContext(),
							getString(R.string.empty_select),
							Toast.LENGTH_SHORT);
					toast.setGravity(Gravity.CENTER, 0, 0);
					toast.show();
				}
				collectMyLoadedData(selectedKeywords, sequence, canSubmitQuery,
						endOfKeywordSequence);
			}
		});

		nextButtonSmall.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				radioId = keywordChoices.getCheckedRadioButtonId();

				if (radioId != -1 && !endOfKeywordSequence) {
					RadioButton rb = (RadioButton) findViewById(radioId);
					String choice = rb.getText().toString();
					String query = "Search:";
					selectedKeywords.add(new String(choice));

					for (int i = 0; i < selectedKeywords.size(); i++) {
						query = query.concat(" >" + selectedKeywords.get(i));
					}

					searchPath.setText(query);
					++sequence;
					buildRadioList();
				} else {
					if (!endOfKeywordSequence)
						Toast.makeText(getApplicationContext(),
								getString(R.string.empty_select),
								Toast.LENGTH_SHORT).show();
				}

				if (canSubmitQuery) {
					try {
						Global.URL = getURL();
					} catch (UnsupportedEncodingException e) {

					}
					if (searchDatabase != null)
						searchDatabase.close();
					network = new Thread(keywordDownloader);
					showDialog(CONNECT_DIALOG);
					network.start();
					canSubmitQuery = false;
				}
				if (endOfKeywordSequence)
					canSubmitQuery = true;
				collectMyLoadedData(selectedKeywords, sequence, canSubmitQuery,
						endOfKeywordSequence);
			}

		});

		backButton.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				endOfKeywordSequence = false;
				canSubmitQuery = false;
				if (selectedKeywords.size() > 0) {
					nextButtonSmall.setText(getString(R.string.next_button));
					if (selectedKeywords.size() == 1) {
						layout.setVisibility(View.GONE);
						startLayout.setVisibility(View.VISIBLE);
					}
					--sequence;
					selectedKeywords.remove(selectedKeywords.size() - 1);
					keywordChoices.clearCheck();
					keywordChoices.removeAllViews();
					buildRadioList();
					String query = "Search:";
					for (int i = 0; i < selectedKeywords.size(); i++) {
						query = query.concat(" >" + selectedKeywords.get(i));
					}
					searchPath.setText(query);
				}
				collectMyLoadedData(selectedKeywords, sequence, canSubmitQuery,
						endOfKeywordSequence);
			}
		});

	}

	/**
	 * Generates the request URL for search queries.
	 * 
	 * @return The request URL.
	 * @throws UnsupportedEncodingException
	 */
	private String getURL() throws UnsupportedEncodingException {
		String url, query = "", name = Global.intervieweeName;
		location = Global.location;
		SharedPreferences settings = PreferenceManager
				.getDefaultSharedPreferences(getBaseContext());
		url = settings.getString(Settings.KEY_SERVER,
				getString(R.string.server));
		if (url.endsWith("/")) {
			url = url.concat(getString(R.string.search_path));
		} else {
			url = url.concat("/" + getString(R.string.search_path));
		}
		url = url.concat("?keyword=");

		for (int i = 1; i < selectedKeywords.size(); i++) {
			query = query.concat(selectedKeywords.get(i) + " ");
		}
		url = url.concat(query.replace(" ", "%20"));

		TelephonyManager telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
		String imei = telephonyManager.getDeviceId();

		DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		Date date = new Date();
		submissionTime = dateFormat.format(date);
		submissionTime = URLEncoder.encode(submissionTime, "UTF-8");
		name = name.replace(" ", "%20");
		location = URLEncoder.encode(location, "latin1");
		url = url.concat("&interviewee_id=" + name + "&handset_id=" + imei
				+ "&location=" + location + "&handset_submit_time="
				+ submissionTime);
		return url;
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
						nextButtonSmall.setEnabled(false);
						Intent i = new Intent(getApplicationContext(),
								DisplaySearchResultsActivity.class);

						String query = "";
						for (int j = 0; j < selectedKeywords.size(); j++) {
							query = query
									.concat(selectedKeywords.get(j) + "> ");
						}

						i.putExtra("search", query);
						i.putExtra("content", Global.data);
						i.putExtra("name", Global.intervieweeName);
						i.putExtra("location", Global.location);
						if (searchDatabase != null)
							searchDatabase.close();
						startActivity(i);
						finish();
					}

				} else {
					dismissDialog(CONNECT_DIALOG);
					if (Global.data != null
							&& Global.data.trim().endsWith("</Keywords>")) {
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
				selectedKeywords.clear();
				searchPath.setText("Search: ");
				sequence = 0;
				activeDatabaseTable = getTable();
				keywordChoices.clearCheck();
				keywordChoices.removeAllViews();
				searchDatabase.open();
				buildRadioList();
				layout.setVisibility(View.GONE);
				startLayout.setVisibility(View.VISIBLE);
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

	@Override
	public Object onRetainNonConfigurationInstance() {
		final ActivityState data = searchStateData;
		return data;
	}

	/**
	 * Method for collecting search activity data on orientation change.
	 * 
	 * @param aList
	 *            The current searched list.
	 * @param pos
	 *            The current search sequence position
	 * @param done
	 *            True if the search has reached its end
	 * @param send
	 *            Set to true to send data
	 */
	void collectMyLoadedData(ArrayList<String> aList, int pos, boolean done,
			boolean send) {
		searchStateData = new ActivityState();
		searchStateData.myField = sequence;
		searchStateData.mySelect = aList;
	}

	/**
	 * object for holding activity data on orientation change
	 */
	private class ActivityState {
		int myField;
		ArrayList<String> mySelect;
		boolean endOfKeywordSequence;
		boolean canSubmitQuery;
	}

	/**
	 * Builds search sequences.
	 */
	public void buildRadioList() {
		RadioButton radio;
		boolean remove = false;
		showDialog(LOAD_DIALOG);
		if (sequence == 0) {
			Cursor searchCursor = searchDatabase.selectMenuOptions(
					activeDatabaseTable, "col" + Integer.toString(sequence),
					null);
			startManagingCursor(searchCursor);

			if (searchCursor.moveToFirst()) {
				while (!searchCursor.isAfterLast()) {
					int option = searchCursor.getColumnIndexOrThrow("col"
							+ Integer.toString(sequence));
					radio = new RadioButton(this);
					radio.setText(searchCursor.getString(option));
					radio.setTextColor(-16777216);
					radio.setTextSize(21);
					radio.setPadding(40, 1, 1, 1);
					keywordChoices.addView(radio);
					searchCursor.moveToNext();
				}
			}
		} else {
			String condition = "col0='" + selectedKeywords.get(0) + "'";

			for (int s = 1; s < selectedKeywords.size(); s++) {
				condition = condition.concat(" AND col" + s + "='"
						+ selectedKeywords.get(s) + "'");
			}

			Cursor searchCursor = searchDatabase.selectMenuOptions(
					activeDatabaseTable, "col" + Integer.toString(sequence),
					condition);
			startManagingCursor(searchCursor);
			if (searchCursor.moveToFirst()) {

				while (!searchCursor.isAfterLast()) {
					int option = searchCursor.getColumnIndexOrThrow("col"
							+ Integer.toString(sequence));
					if (searchCursor.getString(option) == null) {
						endOfKeywordSequence = true;
						nextButtonSmall
								.setText(getString(R.string.send_button));
						Toast toast = Toast.makeText(getApplicationContext(),
								getString(R.string.end_of_search),
								Toast.LENGTH_LONG);
						toast.setGravity(Gravity.CENTER, 0, 0);
						toast.show();
						break;
					}
					if (!remove) {
						keywordChoices.clearCheck();
						keywordChoices.removeAllViews();
						remove = true;
					}
					radio = new RadioButton(this);
					radio.setText(searchCursor.getString(option));
					radio.setTextColor(-16777216);
					radio.setTextSize(21);
					radio.setPadding(40, 1, 1, 1);
					keywordChoices.addView(radio);
					searchCursor.moveToNext();
				}
			}
		}
		dismissDialog(LOAD_DIALOG);
	}

	@Override
	protected Dialog onCreateDialog(int id) {
		switch (id) {
		case LOAD_DIALOG:
			progressDialog = new ProgressDialog(this);
			progressDialog.setMessage(getString(R.string.loading));
			progressDialog.setIndeterminate(true);
			progressDialog.setCancelable(false);
			return progressDialog;

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
	 * Error alert dialog builder.
	 * 
	 * @return A dialog.
	 */
	public AlertDialog errorDialog() {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setMessage(R.string.connection_error).setCancelable(false)
				.setPositiveButton("OK", new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int id) {
						if (!isUpdatingKeywords) {
							Intent i = new Intent(getApplicationContext(),
									DisplaySearchResultsActivity.class);
							String query = "", search = "";
							for (int j = 0; j < selectedKeywords.size(); j++) {
								if (j > 0) {
									query = query.concat(selectedKeywords
											.get(j)
											+ " ");
									search = search.concat("> "
											+ selectedKeywords.get(j));
								} else {
									search = search.concat(selectedKeywords
											.get(j));
								}
							}

							i.putExtra("search", search);
							// We have failed to send this query
							i.putExtra("send", true);
							i.putExtra("request", query);
							i.putExtra("name", Global.intervieweeName);
							i.putExtra("location", location);
							if (searchDatabase != null)
								searchDatabase.close();
							startActivity(i);
							finish();
						} else {
							isUpdatingKeywords = false;
						}
						dialog.cancel();
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
		AlertDialog message = builder.create();
		return message;
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
	 * determines the active keywords search database table to use
	 */
	private String getTable() {
		Storage db = new Storage(this);
		String table = Global.DATABASE_TABLE;
		db.open();
		// Check if other table qualifies otherwise return above table
		if (!db.isEmpty(Global.DATABASE_TABLE2)) {
			if (db.checkTable(Global.DATABASE_TABLE2) > 0) {
				table = Global.DATABASE_TABLE2;
			}
		}
		db.close();
		return table;
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {

		boolean result = super.onCreateOptionsMenu(menu);
		menu.add(0, Global.HOME_ID, 0, getString(R.string.menu_home)).setIcon(
				R.drawable.home);
		menu.add(0, Global.RESET_ID, 0, getString(R.string.menu_reset))
				.setIcon(R.drawable.search);
		menu.add(0, Global.INBOX_ID, 0, getString(R.string.menu_inbox))
				.setIcon(R.drawable.folder);
		menu.add(0, Global.REFRESH_ID, 0, getString(R.string.menu_refresh))
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
			Intent i = new Intent(getApplicationContext(), SearchActivity.class);
			startActivity(i);
			finish();
			return true;
		case Global.INBOX_ID:
			Intent j = new Intent(getApplicationContext(),
					InboxListActivity.class);
			j.putExtra("name", Global.intervieweeName);
			j.putExtra("Global.location", Global.location);
			if (searchDatabase != null)
				searchDatabase.close();
			startActivity(j);
			finish();
			return true;
		case Global.REFRESH_ID:
			AlertDialog.Builder builder = new AlertDialog.Builder(this);
			builder.setMessage(getString(R.string.refresh_confirm))
					.setCancelable(false).setPositiveButton("Yes",
							new DialogInterface.OnClickListener() {
								public void onClick(DialogInterface dialog,
										int id) {
									isUpdatingKeywords = true;
									SharedPreferences settings = PreferenceManager
											.getDefaultSharedPreferences(getBaseContext());
									String url = settings.getString(
											Settings.KEY_SERVER,
											getString(R.string.server));
									if (url.endsWith("/")) {
										url = url
												.concat(getString(R.string.update_path));
									} else {
										url = url
												.concat("/"
														+ getString(R.string.update_path));
									}
									Global.URL = url;

									network = new Thread(keywordDownloader);
									showDialog(CONNECT_DIALOG);
									network.start();
									dialog.cancel();
								}
							}).setNegativeButton("No",
							new DialogInterface.OnClickListener() {
								public void onClick(DialogInterface dialog,
										int id) {
									dialog.cancel();
								}
							});
			AlertDialog alert = builder.create();
			alert.show();

			return true;
		case Global.ABOUT_ID:
			Intent k = new Intent(getApplicationContext(), AboutActivity.class);
			k.putExtra("name", Global.intervieweeName);
			k.putExtra("Global.location", Global.location);
			if (searchDatabase != null)
				searchDatabase.close();
			startActivity(k);
			return true;
		case Global.EXIT_ID:
			this.finish();
			return true;
		case Global.HOME_ID:
			Intent l = new Intent(getApplicationContext(),
					MainMenuActivity.class);
			l.putExtra("name", Global.intervieweeName);
			l.putExtra("Global.location", Global.location);
			if (searchDatabase != null)
				searchDatabase.close();
			startActivity(l);
			finish();
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

	@Override
	protected void onStart() {
		super.onStart();
		searchDatabase.open();
	}

}
