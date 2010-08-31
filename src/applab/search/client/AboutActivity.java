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
package applab.search.client;

import applab.search.client.R;
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
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

public class AboutActivity extends Activity {
	private final String LOG_TAG = "AboutActivity";
	private static KeywordDownloader keywordDownloader;
	private static KeywordParser keywordParser;
	private static Thread networkThread;
	private static Thread parserThread;
	private ProgressDialog progressDialog;
	private Button closeButton;

	/** true if there has been a configuration change */
	private boolean configurationChanged;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		if (savedInstanceState != null) {
			configurationChanged = savedInstanceState.getBoolean("changed");
			if (configurationChanged) {
				Log.w(LOG_TAG, "Activity RESTART");
			}
		}
		setContentView(R.layout.text_view);
		setTitle(getString(R.string.about_activity));
		TelephonyManager telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
		String imei = telephonyManager.getDeviceId();
		TextView phoneId = (TextView) findViewById(R.id.phone_id);
		phoneId.setText("Phone ID: " + imei);
		TextView nameAndVersion = (TextView) findViewById(R.id.name_version);
		nameAndVersion.setText(getString(R.string.app_name) + "\nVersion: "
				+ getString(R.string.app_version));

		TextView releaseDate = (TextView) findViewById(R.id.release);
		releaseDate
				.setText("Release Date: " + getString(R.string.release_date));

		TextView info = (TextView) findViewById(R.id.info);
		info.setText(getString(R.string.info));

		this.closeButton = (Button) findViewById(R.id.close);
		this.closeButton.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				finish();
			}
		});
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
				progressDialog.dismiss();
				errorDialog().show();
				break;
			case Global.CONNECTION_SUCCESS:
				if (Global.data.trim().endsWith("</Keywords>")) {
					showProgressDialog(Global.PARSE_DIALOG);
					AboutActivity.keywordParser = new KeywordParser(
							getApplicationContext(), progressHandler,
							connectHandle);
					AboutActivity.parserThread = new Thread(
							AboutActivity.keywordParser);
					AboutActivity.parserThread.start();
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
		builder.setMessage(R.string.connection_error).setCancelable(false);
		builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int id) {
				// Release lock
				KeywordSynchronizer.completeSynchronization();
				dialog.cancel();
			}
		}).setNegativeButton("Retry", new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int id) {
				dialog.cancel();
				showProgressDialog(Global.UPDATE_DIALOG);
				getServerUrl(R.string.update_path);
				AboutActivity.keywordDownloader = new KeywordDownloader(
						connectHandle, getServerUrl(R.string.update_path));
				AboutActivity.networkThread = new Thread(keywordDownloader);
				AboutActivity.networkThread.start();
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
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// TODO Auto-generated method stub
		boolean result = super.onCreateOptionsMenu(menu);
		menu.add(1, Global.RESET_ID, 0, getString(R.string.menu_reset))
				.setIcon(R.drawable.search);
		menu.add(0, Global.INBOX_ID, 0, getString(R.string.menu_inbox))
				.setIcon(R.drawable.folder);
		menu.add(1, Global.REFRESH_ID, 0, getString(R.string.menu_refresh))
				.setIcon(R.drawable.refresh);
		menu.add(0, Global.EXIT_ID, 0, getString(R.string.close_button))
				.setIcon(R.drawable.done);
		menu.add(0, Global.HOME_ID, 0, getString(R.string.menu_home)).setIcon(
				R.drawable.home);
		return result;
	}

	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		boolean result = super.onPrepareOptionsMenu(menu);
		/* Remove new search option if no interviewee name has been supplied */
		if (Global.intervieweeName == null) {
			menu.removeItem(Global.RESET_ID);
		}
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
				// Acquire synchronization lock
				if (KeywordSynchronizer.tryStartSynchronization()) {
					showProgressDialog(Global.UPDATE_DIALOG);
					AboutActivity.keywordDownloader = new KeywordDownloader(
							connectHandle, getServerUrl(R.string.update_path));
					AboutActivity.networkThread = new Thread(
							AboutActivity.keywordDownloader);
					AboutActivity.networkThread.start();
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
		case Global.EXIT_ID:
			this.finish();
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	@Override
	protected void onResume() {
		super.onResume();
		Log.i(LOG_TAG, "-> onResume()");
		restoreProgressDialogs();
	}

	private void restoreProgressDialogs() {
		if (AboutActivity.parserThread != null
				&& AboutActivity.parserThread.isAlive()) {
			AboutActivity.keywordParser.swap(this
					.getApplicationContext(), this.connectHandle,
					this.progressHandler);
			Log.i(LOG_TAG, "Parser thread is alive");
			Log.i(LOG_TAG, "Show parse dialog");
			showProgressDialog(Global.UPDATE_DIALOG);
			showProgressDialog(Global.PARSE_DIALOG);
			// Cross check that parser thread is still alive
			if (!(AboutActivity.parserThread != null && AboutActivity.parserThread
					.isAlive())) {
				progressDialog.dismiss();
			}
		} else if (AboutActivity.networkThread != null
				&& AboutActivity.networkThread.isAlive()) {
			AboutActivity.keywordDownloader.swap(this.connectHandle);
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
}
