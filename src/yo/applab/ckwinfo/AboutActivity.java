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

public class AboutActivity extends Activity {
	private final String LOG_TAG = "AboutActivity";
	private KeywordDownloader keywordDownloader;
	private KeywordParser keywordParser;
	public Storage searchDatabase;
	private Thread network;
	private Button closeButton;

	/** shown when initializing database */
	private static final int PARSE_DIALOG = 1;

	/** shown when accessing network resources */
	private static final int CONNECT_DIALOG = 2;

	private ProgressDialog progressDialog;
	private SynchronizeTask synchronizeTask;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		this.synchronizeTask = new SynchronizeTask(this.connectHandle, this
				.getApplicationContext());
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

		closeButton = (Button) findViewById(R.id.close);
		closeButton.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				finish();
			}
		});

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
				dismissDialog(CONNECT_DIALOG);
				if (Global.data.trim().endsWith("</Keywords>")) {
					showDialog(PARSE_DIALOG);
					Thread parser = new Thread(keywordParser);
					parser.start();
				} else {
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
				synchronizeTask.updateKeywords();
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
		case Global.RESET_ID:
			if (!KeywordSynchronizer.isSynchronizing()) {
				// Get synchronization lock
				if (KeywordSynchronizer.tryStartSynchronization()) {
					Intent i = new Intent(getApplicationContext(),
							SearchActivity.class);
					if (searchDatabase != null)
						// TODO OKP-1#CFR-29, create a
						// private
						// cleanup method
						searchDatabase.close();
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
			if (searchDatabase != null)
				searchDatabase.close();
			startActivity(j);
			finish();
			return true;
		case Global.REFRESH_ID:
			if (!KeywordSynchronizer.isSynchronizing()) {
				// Get synchronization lock
				if (KeywordSynchronizer.tryStartSynchronization()) {
					keywordParser = new KeywordParser(this
							.getApplicationContext(), progressHandler,
							connectHandle);
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
			if (searchDatabase != null)
				searchDatabase.close();
			startActivity(l);
			finish();
			return true;
		case Global.EXIT_ID:
			if (searchDatabase != null)
				searchDatabase.close();
			this.finish();
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	@Override
	public void onRestoreInstanceState(Bundle savedInstanceState) {
		super.onRestoreInstanceState(savedInstanceState);
		if (progressDialog != null) {
			progressDialog.dismiss();
		}
	}

}
