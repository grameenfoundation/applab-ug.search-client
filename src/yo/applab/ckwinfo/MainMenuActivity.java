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

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.telephony.TelephonyManager;
import android.text.InputFilter;
import android.text.Spanned;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

/**
 * The Search application home with access to the inbox, and settings.
 * 
 */
public class MainMenuActivity extends Activity {
	/** for debugging purposes in adb logcat */
	private static final String LOG_TAG = "Home";

	private Button inboxButton;
	private Button nextButton;
	private EditText intervieweeNameEditBox;

	/** runnable class handling XML parsing and initializing keywords database */
	private static KeywordParser keywordParser;

	private static Thread parserThread;

	private static Thread networkThread;

	/** true if keyword cache exists, false otherwise */
	private static boolean cacheExists;

	/** true if there has been a configuration change */
	private boolean configurationChanged;

	private ProgressDialog progressDialog;
	private SynchronizeTask synchronizeTask;

	private static KeywordDownloader keywordDownloader;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		if (savedInstanceState != null) {
			configurationChanged = savedInstanceState.getBoolean("changed");
			if (configurationChanged) {
				Log.w(LOG_TAG, "Activity RESTART");
			}
		}
		this.synchronizeTask = new SynchronizeTask(this.connectHandle, this
				.getApplicationContext());
		if (!configurationChanged) {
			init();
		}
		setContentView(R.layout.main_menu);
		// Display the current user in the activity title if available otherwise
		// do launch synchronization
		if (Global.intervieweeName == null) {
			setTitle(getString(R.string.app_name));
			// Make the IMEI available to other threads
			if (Global.IMEI == null) {
				TelephonyManager telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
				String imei = telephonyManager.getDeviceId();
				Global.IMEI = imei;
			}
			// Do launch time synchronization unless this is an initial setup
			// (@cacheExits is false) and device configuration has not changed
			if (MainMenuActivity.cacheExists && !configurationChanged) {
				this.synchronizeTask.startLaunchThread();
			}
		} else {
			String activity_title = getString(R.string.app_name) + " | ";
			if (Global.intervieweeName.length() > 30) {
				activity_title = activity_title.concat(Global.intervieweeName
						.substring(0, 30));
				activity_title = activity_title.concat("...");
			} else {
				activity_title = activity_title.concat(Global.intervieweeName);
			}
			setTitle(activity_title);
		}

		nextButton = (Button) findViewById(R.id.next_button);
		inboxButton = (Button) findViewById(R.id.inbox_button);
		inboxButton.setText(getString(R.string.inbox_button));
		intervieweeNameEditBox = (EditText) findViewById(R.id.EditText01);

		// Filter out special characters from the input text
		InputFilter filter = new InputFilter() {
			public CharSequence filter(CharSequence source, int start, int end,
					Spanned dest, int dstart, int dend) {
				for (int i = start; i < end; i++) {
					if (!(((Character.isWhitespace(source.charAt(i)))) || Character
							.isLetterOrDigit(source.charAt(i)))) {
						Toast.makeText(getApplicationContext(),
								getString(R.string.invalid_text),
								Toast.LENGTH_SHORT).show();
						return "";
					}
				}
				return null;
			}
		};

		intervieweeNameEditBox.setFilters(new InputFilter[] { filter });
		if (!MainMenuActivity.cacheExists) {
			inboxButton.setEnabled(false);
			nextButton.setEnabled(false);
		}
		inboxButton.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				String myEdit = intervieweeNameEditBox.getText().toString()
						.trim();
				if (myEdit.length() > 0) {
					Global.intervieweeName = intervieweeNameEditBox.getText()
							.toString();
					Intent i = new Intent(getApplicationContext(),
							InboxListActivity.class);
					i.putExtra("block", false);
					startActivity(i);
					finish();
				} else {
					Toast.makeText(getApplicationContext(),
							getString(R.string.empty_text), Toast.LENGTH_SHORT)
							.show();
				}

			}

		});

		nextButton.setText(getString(R.string.next_button));
		nextButton.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				String myEdit = intervieweeNameEditBox.getText().toString()
						.trim();
				if (myEdit.length() > 0) {
					// check if synchronization is on going if not get the lock
					if (!KeywordSynchronizer.tryStartSynchronization()) {
						showWaitDialog();
					} else {
						if (MainMenuActivity.cacheExists) {
							Global.intervieweeName = intervieweeNameEditBox
									.getText().toString();
							Intent i = new Intent(getApplicationContext(),
									SearchActivity.class);
							startActivity(i);
							finish();
						}
					}
				} else {
					Toast.makeText(getApplicationContext(),
							getString(R.string.empty_text), Toast.LENGTH_SHORT)
							.show();
				}
			}

		});

	}

	/**
	 * creates dialog shown when connecting to the network
	 */
	/*
	 * private void showConnectDialog() { progressDialog = new
	 * ProgressDialog(this); progressDialog
	 * .setMessage(getString(R.string.progress_msg)); progressDialog
	 * .setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
	 * progressDialog.setIndeterminate(true);
	 * progressDialog.setCancelable(false); progressDialog.show(); }
	 */

	/**
	 * creates dialog shown when waiting on background synchronization to
	 * complete before proceeding with a search
	 */
	private void showWaitDialog() {
		progressDialog = new ProgressDialog(this);
		progressDialog.setMessage(getString(R.string.wait_on_background));
		progressDialog.setIndeterminate(true);
		progressDialog.setCancelable(true);
		progressDialog.show();
	}

	/**
	 * updates database initialization progress
	 */
	public Handler progressHandler = new Handler() {
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
				progressDialog.dismiss();
				errorDialog().show();
				break;
			case Global.CONNECTION_SUCCESS:
				if (Global.data.trim().endsWith("</Keywords>")) {
					android.util.Log.i(LOG_TAG, "Show parse dialog");
					showProgressDialog(Global.PARSE_DIALOG);
					if (MainMenuActivity.keywordParser == null) {
						MainMenuActivity.keywordParser = new KeywordParser(
								getApplicationContext(), progressHandler,
								connectHandle);
					}
					MainMenuActivity.parserThread = new Thread(
							MainMenuActivity.keywordParser);
					MainMenuActivity.parserThread.start();
				} else {
					progressDialog.dismiss();
					errorDialog().show();
				}
				break;
			case Global.KEYWORD_PARSE_SUCCESS:
				// Release synchronization lock
				KeywordSynchronizer.completeSynchronization();
				progressDialog.dismiss();
				Toast.makeText(getApplicationContext(),
						getString(R.string.refreshed), Toast.LENGTH_LONG)
						.show();
				if (!MainMenuActivity.cacheExists) {
					synchronizeTask.scheduleRecurringTimer();
				}
				inboxButton.setEnabled(true);
				nextButton.setEnabled(true);
				MainMenuActivity.cacheExists = true;
				break;
			case Global.KEYWORD_PARSE_ERROR:
				progressDialog.dismiss();
				errorDialog().show();
				break;
			case Global.DISMISS_WAIT_DIALOG:
				if (progressDialog != null && progressDialog.isShowing()) {
					progressDialog.dismiss();
				}
				break;
			}
		}
	};

	/**
	 * Connection error alert dialog builder.
	 * 
	 * @return A dialog.
	 */
	public AlertDialog errorDialog() {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setMessage(R.string.connection_error);
		builder.setCancelable(false);
		builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int id) {
				dialog.cancel();
				KeywordSynchronizer.completeSynchronization();
				// Start synchronization timer
				if (!MainMenuActivity.cacheExists) {
					synchronizeTask.scheduleRecurringTimer();
				}
			}
		}).setNegativeButton("Retry", new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int id) {
				dialog.cancel();
				if (MainMenuActivity.cacheExists) {
					showProgressDialog(Global.UPDATE_DIALOG);
				} else {
					showProgressDialog(Global.SETUP_DIALOG);
				}
				getServerUrl(R.string.update_path);
				MainMenuActivity.keywordDownloader = new KeywordDownloader(
						connectHandle);
				MainMenuActivity.networkThread = new Thread(keywordDownloader);
				MainMenuActivity.networkThread.start();
			}
		});
		AlertDialog alert = builder.create();
		return alert;
	}

	/**
	 * keywordParser error alert dialog builder.
	 * 
	 * @return dialog with dismiss button.
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
			progressDialog.setIndeterminate(true);
			progressDialog.setCancelable(false);
			progressDialog.show();
			break;
		case Global.PARSE_DIALOG:
			// Updates previously showing update/setup dialog
			progressDialog.setIndeterminate(false);
			progressDialog.setMessage(getString(R.string.parse_msg));
			break;
		case Global.SETUP_DIALOG:
			progressDialog = new ProgressDialog(this);
			progressDialog.setTitle(getString(R.string.progress_header));
			progressDialog.setMessage(getString(R.string.progress_initial));
			progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
			progressDialog.setIndeterminate(true);
			progressDialog.setCancelable(false);
			progressDialog.show();
			break;
		}
	}

	/**
	 * A start up check to see if search keywords are in database and initialize
	 * setup if not.
	 */
	private void init() {
		if (isCacheEmpty()) {
			getServerUrl(R.string.update_path);
			MainMenuActivity.keywordDownloader = new KeywordDownloader(
					connectHandle);
			networkThread = new Thread(MainMenuActivity.keywordDownloader);
			networkThread.start();
			showProgressDialog(Global.SETUP_DIALOG);
		} else {
			MainMenuActivity.cacheExists = true;
		}
	}

	/**
	 * check if cache has keywords
	 * 
	 * @return true if no valid keywords exit, false otherwise
	 */
	private boolean isCacheEmpty() {
		Storage searchDatabase = new Storage(this);
		searchDatabase.open();
		// If we have content check if it is valid
		if (!searchDatabase.isEmpty(Global.DATABASE_TABLE)) {
			if (searchDatabase.checkTable(Global.DATABASE_TABLE) > 0) {
				searchDatabase.close();
				return false;
			}
		}
		if (!searchDatabase.isEmpty(Global.DATABASE_TABLE2)) {
			if (searchDatabase.checkTable(Global.DATABASE_TABLE2) > 0) {
				searchDatabase.close();
				return false;
			}
		}
		searchDatabase.close();
		return true;
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		boolean result = super.onCreateOptionsMenu(menu);

		menu.add(1, Global.REFRESH_ID, 0, getString(R.string.menu_refresh))
				.setIcon(R.drawable.refresh);
		menu.add(0, Global.ABOUT_ID, 0, getString(R.string.menu_about))
				.setIcon(R.drawable.about);
		menu.add(0, Global.EXIT_ID, 0, getString(R.string.menu_exit)).setIcon(
				R.drawable.exit);
		menu.add(0, Global.SETTINGS_ID, 0, getString(R.string.menu_settings))
				.setIcon(R.drawable.settings);

		return result;
	}

	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		boolean result = super.onPrepareOptionsMenu(menu);
		if (KeywordSynchronizer.isSynchronizing()) {
			// Disable keyword updates and new searches
			menu.setGroupEnabled(1, false);
		}else{
			menu.setGroupEnabled(1, true);
		}
		return result;
	}

	/**
	 * retrieves server URL from preferences
	 * 
	 * @param id
	 *            string resource ID
	 * @return server URL string
	 */
	private String getServerUrl(int id) {
		String url;
		if (Global.URL == null) {
			SharedPreferences settings = PreferenceManager
					.getDefaultSharedPreferences(this);
			url = settings.getString(Settings.KEY_SERVER, this
					.getString(R.string.server));
			url = url.concat("/" + this.getString(id));
			Global.URL = url;
		} else {
			url = Global.URL;
		}
		return url;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case Global.REFRESH_ID:
			if (!KeywordSynchronizer.isSynchronizing()) {
				// Acquire synchronization lock
				if (KeywordSynchronizer.tryStartSynchronization()) {
					getServerUrl(R.string.update_path);
					keywordDownloader = new KeywordDownloader(connectHandle);
					networkThread = new Thread(keywordDownloader);
					networkThread.start();
					if (MainMenuActivity.cacheExists) {
						showProgressDialog(Global.UPDATE_DIALOG);
					} else {
						showProgressDialog(Global.SETUP_DIALOG);
					}
				} else {
					Log.i(LOG_TAG, "Failed to get synchronization lock");
				}
			}
			return true;
		case Global.ABOUT_ID:
			Intent k = new Intent(getApplicationContext(), AboutActivity.class);
			k.putExtra("block", true);
			startActivity(k);
			return true;
		case Global.EXIT_ID:
			this.finish();
			return true;
		case Global.SETTINGS_ID:
			Intent l = new Intent(getApplicationContext(), Settings.class);
			startActivity(l);
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	@Override
	protected void onStart() {
		super.onStart();
		Log.i(LOG_TAG, "-> onStart()");
		if (MainMenuActivity.parserThread != null
				&& MainMenuActivity.parserThread.isAlive()) {
			MainMenuActivity.keywordParser.setHandlers(this
					.getApplicationContext(), this.connectHandle,
					this.progressHandler);
			Log.i(LOG_TAG, "Parser thread is alive");
			android.util.Log.e(LOG_TAG, "Show parse dialog");
			if (MainMenuActivity.cacheExists) {
				showProgressDialog(Global.UPDATE_DIALOG);
			} else {
				showProgressDialog(Global.SETUP_DIALOG);
			}
			showProgressDialog(Global.PARSE_DIALOG);
			// Cross check that parser thread is still alive
			if (!(MainMenuActivity.parserThread != null && MainMenuActivity.parserThread
					.isAlive())) {
				progressDialog.dismiss();
			}
		} else if (MainMenuActivity.networkThread != null
				&& MainMenuActivity.networkThread.isAlive()) {
			MainMenuActivity.keywordDownloader.swap(this.connectHandle);
			Log.i(LOG_TAG, "Is still downloading keywords...");
			android.util.Log.e(LOG_TAG, "Show connect dialog");
			if (MainMenuActivity.cacheExists) {
				showProgressDialog(Global.UPDATE_DIALOG);
			} else {
				showProgressDialog(Global.SETUP_DIALOG);
			}
		}
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		android.util.Log.i(LOG_TAG, "-> onSaveInstanceState()");
		// remove any showing dialog since activity is going to be recreated
		if (progressDialog != null && progressDialog.isShowing()) {
			android.util.Log.i(LOG_TAG, "Remove progress dialog");
			progressDialog.dismiss();
		}
		// Flag for configuration changes
		outState.putBoolean("changed", true);
		// continue with the normal instance state save
		super.onSaveInstanceState(outState);
	}
}