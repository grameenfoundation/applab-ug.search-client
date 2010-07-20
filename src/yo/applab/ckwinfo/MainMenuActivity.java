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
	private static final String DEBUG_TAG = "Home";

	private Button inboxButton;
	private Button nextButton;
	private EditText intervieweeNameEditBox ;

	/** database holding search keywords */
	public Storage searchDatabase;

	/** runnable class for establishing network connection */
	private KeywordDownloader keywordDownloader;
	private Thread network;

	/** runnable class handling XML parsing and initializing keywords database */
	private KeywordParser keywordParser;

	/** shown at initial keyword setup */
	private static final int SETUP_DIALOG = 1;

	/** shown on network access */
	private static final int CONNECT_DIALOG = 2;

	/** shown when parsing and initializing database */
	private static final int PARSE_DIALOG = 3;

	private ProgressDialog progressDialog;

	/** true if keyword cache exists, false otherwise */
	private boolean savedList = false;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);		
		init();
		setContentView(R.layout.main_menu);
		// Display the current user in the activity title if available
		if (Global.intervieweeName == null) {
			setTitle(getString(R.string.app_name));
			//Make the IMEI available to other threads
			if(Global.IMEI == null){
				TelephonyManager telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
				String imei = telephonyManager.getDeviceId();
				Global.IMEI = imei;
			}
			SynchronizeTask synchronizeTask = new SynchronizeTask(this.connectHandle, this.getApplicationContext());
			//Schedule recurring background tasks
			synchronizeTask.scheduleRecurringTimer ();
			//TODO OKP-98, at launch synchronization
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
		intervieweeNameEditBox  = (EditText) findViewById(R.id.EditText01);

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

		intervieweeNameEditBox .setFilters(new InputFilter[] { filter });
		if (!savedList) {
			inboxButton.setEnabled(false);
			nextButton.setEnabled(false);
		}
		inboxButton.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				String myEdit = intervieweeNameEditBox .getText().toString().trim();
				if (myEdit.length() > 0) {
					Global.intervieweeName = intervieweeNameEditBox .getText().toString();
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
				String myEdit = intervieweeNameEditBox .getText().toString().trim();
				if (myEdit.length() > 0) {

					if (savedList) {
						Global.intervieweeName = intervieweeNameEditBox .getText().toString();
						Intent i = new Intent(getApplicationContext(),
								SearchActivity.class);
						startActivity(i);
						finish();
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
				if (savedList) {
					dismissDialog(CONNECT_DIALOG);
				} else {
					dismissDialog(SETUP_DIALOG);
				}
				errorDialog().show();
				break;
			case Global.CONNECTION_SUCCESS:
				if (Global.data.trim().endsWith("</Keywords>")) {
					if (savedList) {
						dismissDialog(CONNECT_DIALOG);
					} else {
						dismissDialog(SETUP_DIALOG);
					}
					showDialog(PARSE_DIALOG);
					if(keywordParser == null){
						keywordParser = new KeywordParser(getApplicationContext(), progressHandler,
								connectHandle);
					}
					Thread parser = new Thread(keywordParser);
					parser.start();
				} else {
					if (savedList) {
						dismissDialog(CONNECT_DIALOG);
					} else {
						dismissDialog(SETUP_DIALOG);
					}
					errorDialog().show();
				}
				break;
			case Global.KEYWORD_PARSE_SUCCESS:
				dismissDialog(PARSE_DIALOG);
				Toast.makeText(getApplicationContext(),
						getString(R.string.refreshed), Toast.LENGTH_LONG)
						.show();
				inboxButton.setEnabled(true);
				nextButton.setEnabled(true);
				savedList = true;
				break;
			case Global.KEYWORD_PARSE_ERROR:
				dismissDialog(PARSE_DIALOG);
				errorDialog().show();
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
				if (searchDatabase != null)
					searchDatabase.close();
			}
		}).setNegativeButton("Retry", new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int id) {
				dialog.cancel();
				network = new Thread(keywordDownloader);
				if (savedList) {
					showDialog(CONNECT_DIALOG);
				} else {
					showDialog(SETUP_DIALOG);
				}
				network.start();
			}
		});
		AlertDialog alert = builder.create();
		return alert;
	}

	/**
	 * keywordParser error alert dialog builder.
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
	 * A start up check to see if search keywords are in database and initialize
	 * setup if not.
	 */
	private void init() {
		if (checkKeywordsCache()) {
			keywordParser = new KeywordParser(this.getApplicationContext(), progressHandler,
					connectHandle);
			keywordDownloader = new KeywordDownloader(this.connectHandle);
			Global.URL = getURL();
			network = new Thread(keywordDownloader);
			showDialog(SETUP_DIALOG);
			network.start();
		} else {
			savedList = true;
		}
	}

	private boolean checkKeywordsCache() {
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

		menu.add(0, Global.REFRESH_ID, 0, getString(R.string.menu_refresh))
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
	public boolean onOptionsItemSelected(MenuItem item) {

		switch (item.getItemId()) {

		case Global.REFRESH_ID:
			SynchronizeTask synchronizeTask = new SynchronizeTask(this.connectHandle, this.getApplicationContext());
			synchronizeTask.updateKeywords();
			if (savedList) {
				showDialog(CONNECT_DIALOG);
			} else {
				showDialog(SETUP_DIALOG);
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
		android.util.Log.e(DEBUG_TAG, "-> onRestoreInstanceState()");
		progressDialog.dismiss();
	}

}