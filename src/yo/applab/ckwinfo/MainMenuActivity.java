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
import android.app.Dialog;
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

	/** database holding search keywords */
	public Storage searchDatabase;	
	
	/** runnable class handling XML parsing and initializing keywords database */
	private KeywordParser keywordParser;

	/** shown at initial keyword setup */
	private static final int SETUP_DIALOG = 1;

	/** shown on network access */
	private static final int CONNECT_DIALOG = 2;

	/** shown when parsing and initializing database */
	private static final int PARSE_DIALOG = 3;

	/** dialog shown in case a background update is underway */
	private static final int WAIT_DIALOG = 4;

	private ProgressDialog progressDialog;

	/** true if keyword cache exists, false otherwise */
	private boolean cacheExists = false;

	SynchronizeTask synchronizeTask;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		this.synchronizeTask = new SynchronizeTask(this.connectHandle, this
				.getApplicationContext());
		init();
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
			// (@cacheExits is false)
			if (cacheExists) {
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
		if (!cacheExists) {
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
						showDialog(WAIT_DIALOG);
					} else {
						if (cacheExists) {
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

	@Override
	protected Dialog onCreateDialog(int id) {
		switch (id) {
		case SETUP_DIALOG:
			progressDialog = new ProgressDialog(this);
			progressDialog.setTitle(getString(R.string.progress_header));
			progressDialog.setMessage(getString(R.string.progress_retrieve));
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
		case WAIT_DIALOG:
			progressDialog = new ProgressDialog(this);
			progressDialog.setMessage(getString(R.string.wait_on_background));
			progressDialog.setIndeterminate(true);
			progressDialog.setCancelable(true);
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
				if (cacheExists) {
					dismissDialog(CONNECT_DIALOG);
				} else {
					dismissDialog(SETUP_DIALOG);
				}
				errorDialog().show();
				break;
			case Global.CONNECTION_SUCCESS:
				if (Global.data.trim().endsWith("</Keywords>")) {
					if (cacheExists) {
						dismissDialog(CONNECT_DIALOG);
					} else {
						dismissDialog(SETUP_DIALOG);
					}
					showDialog(PARSE_DIALOG);
					if (keywordParser == null) {
						keywordParser = new KeywordParser(
								getApplicationContext(), progressHandler,
								connectHandle);
					}
					Thread parser = new Thread(keywordParser);
					parser.start();
				} else {
					if (cacheExists) {
						dismissDialog(CONNECT_DIALOG);
					} else {
						dismissDialog(SETUP_DIALOG);
					}
					errorDialog().show();
				}
				break;
			case Global.KEYWORD_PARSE_SUCCESS:
				// Release synchronization lock
				KeywordSynchronizer.completeSynchronization();
				dismissDialog(PARSE_DIALOG);
				Toast.makeText(getApplicationContext(),
						getString(R.string.refreshed), Toast.LENGTH_LONG)
						.show();
				if (!cacheExists) {
					// Start synchronization timer
					synchronizeTask.scheduleRecurringTimer();
				}
				inboxButton.setEnabled(true);
				nextButton.setEnabled(true);
				cacheExists = true;
				break;
			case Global.KEYWORD_PARSE_ERROR:
				dismissDialog(PARSE_DIALOG);
				errorDialog().show();
				break;
			case Global.DISMISS_WAIT_DIALOG:
				try {
					dismissDialog(WAIT_DIALOG);
				} catch (IllegalArgumentException e) {
					// Nothing to do. The dialog may no longer be showing since
					// it is
					// cancelable.
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
				if (searchDatabase != null) {
					searchDatabase.close();
				}
				// Start synchronization timer
				if (!cacheExists) {
					synchronizeTask.scheduleRecurringTimer();
				}
			}
		}).setNegativeButton("Retry", new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int id) {
				dialog.cancel();				
					if (cacheExists) {
						showDialog(CONNECT_DIALOG);
					} else {
						showDialog(SETUP_DIALOG);
					}
					synchronizeTask.updateKeywords();				
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
	 * A start up check to see if search keywords are in database and initialize
	 * setup if not.
	 */
	// TODO Eventually move this logic to the activity onStart method
	private void init() {
		if (isCacheEmpty()) {
			keywordParser = new KeywordParser(this.getApplicationContext(),
					progressHandler, connectHandle);
			showDialog(SETUP_DIALOG);
			this.synchronizeTask.updateKeywords();
		} else {
			cacheExists = true;
		}
	}

	/**
	 * check if the cache has keywords
	 * 
	 * @return true if no valid keywords exit, false otherwise
	 */
	private boolean isCacheEmpty() {
		searchDatabase = new Storage(this);
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

	private String getURL() {
		SharedPreferences settings = PreferenceManager
				.getDefaultSharedPreferences(getBaseContext());
		String url = settings.getString(Settings.KEY_SERVER,
				getString(R.string.server));
		if (url.endsWith("/")) {
			url = url.concat(getString(R.string.update_path));
		} else {
			url = url.concat("/" + getString(R.string.update_path));
		}
		return url;
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
		// Disable keyword update if background update is running
		if (KeywordSynchronizer.isSynchronizing()) {
			// Disable keyword updates and new searches
			menu.setGroupEnabled(1, false);
		}
		return result;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {

		case Global.REFRESH_ID:
			if (!KeywordSynchronizer.isSynchronizing()) {
				// Acquire synchronization lock
				if (KeywordSynchronizer.tryStartSynchronization()) {
					SynchronizeTask synchronizeTask = new SynchronizeTask(
							this.connectHandle, this.getApplicationContext());
					synchronizeTask.updateKeywords();
					if (cacheExists) {
						showDialog(CONNECT_DIALOG);
					} else {
						showDialog(SETUP_DIALOG);
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
	protected void onPause() {
		super.onPause();
	}

	@Override
	protected void onStop() {
		super.onStop();
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
	}

	@Override
	protected void onResume() {
		super.onResume();
	}

	@Override
	protected void onStart() {
		super.onStart();
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