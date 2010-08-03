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

import java.util.ArrayList;
import java.util.Vector;

import android.app.AlertDialog;
import android.app.ListActivity;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.text.InputFilter;
import android.text.Spanned;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;
import android.widget.AdapterView.OnItemClickListener;

/**
 *Displays a list view of the contents of the inbox.
 * 
 */
public class InboxListActivity extends ListActivity {
	private final String LOG_TAG = "InboxListActivity";
	private InboxAdapter inbox;
	private static KeywordDownloader keywordDownloader;
	private static KeywordParser keywordParser;
	private static Thread networkThread;
	private static Thread parserThread;
	private AlertDialog alertDialog;
	private ListView listView;
	private String activityTitle;

	/** holds list view index - database row ID pairs */
	private Vector<Index> Indices = new Vector<Index>();

	private ProgressDialog progressDialog;

	/** true if there has been a configuration change */
	private boolean configurationChanged;

	/** set true when the inbox is empty */
	private boolean empty = false;

	SynchronizeTask synchronizeTask;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		Cursor cursor;
		super.onCreate(savedInstanceState);
		if (savedInstanceState != null) {
			configurationChanged = savedInstanceState.getBoolean("changed");
			if (configurationChanged) {
				Log.w(LOG_TAG, "Activity RESTART");
			}
		}
		Bundle extras = this.getIntent().getExtras();
		boolean block = true;
		if (extras != null) {
			block = extras.getBoolean("block", true);
		}
		this.synchronizeTask = new SynchronizeTask(this.connectHandle, this
				.getApplicationContext());
		this.inbox = new InboxAdapter(this);
		ArrayList<String> results = new ArrayList<String>();
		this.inbox.open();
		cursor = this.inbox.fetchAllRecords();
		startManagingCursor(cursor);
		// inbox.close();
		this.activityTitle = getString(R.string.inbox_title) + "("
				+ cursor.getCount() + ")";
		// If we're coming from the home screen do not ask for id confirmation
		if (block && !configurationChanged) {
			accessDialog().show();
		} else {
			showCurrentUser();
		}

		if (cursor.moveToFirst()) {
			String elipses = "";
			long rowId, Id = 0;
			while (!cursor.isAfterLast()) {
				int titleColumn = cursor
						.getColumnIndexOrThrow(InboxAdapter.KEY_TITLE);
				int idColumn = cursor
						.getColumnIndexOrThrow(InboxAdapter.KEY_ROWID);
				int statusColumn = cursor
						.getColumnIndexOrThrow(InboxAdapter.KEY_STATUS);

				String title = cursor.getString(titleColumn);
				String status = cursor.getString(statusColumn);
				rowId = cursor.getLong(idColumn);

				if (title.length() > 50) {
					elipses = "...";
					title = title.substring(0, 50);
				}
				if (status.contentEquals("Incomplete")) {
					results.add(title + elipses + "\n[Incomplete...]");
				} else {
					results.add(title + elipses);
				}
				cursor.moveToNext();
				Index myIndex = new Index();
				myIndex.rowId = rowId;
				myIndex.id = Id;
				Indices.addElement(myIndex);
				++Id;
			}

			cursor.close();
			setListAdapter(new ArrayAdapter<String>(this, R.layout.list_inbox,
					results));
		} else {
			results.add(getString(R.string.inbox_empty));
			setListAdapter(new ArrayAdapter<String>(this, R.layout.list_inbox,
					results));
			empty = true;
		}
		this.listView = getListView();
		this.inbox.close();
		this.listView.setOnItemClickListener(new OnItemClickListener() {
			public void onItemClick(AdapterView<?> parent, View view,
					int position, long id) {
				if (!empty) {

					Intent i = new Intent(view.getContext(),
							DisplaySearchResultsActivity.class);

					for (int j = 0; j < Indices.size(); j++) {
						Index myIndex = Indices.elementAt(j);
						if (myIndex.id == id) {
							i.putExtra("rowId", myIndex.rowId);
							i.putExtra("fromInbox", true);
						}

					}
					startActivity(i);
					finish();
				}

			}
		});

	}

	/**
	 * updates database initialization progress dialog
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
					InboxListActivity.keywordParser = new KeywordParser(
							getApplicationContext(), progressHandler,
							connectHandle);
					InboxListActivity.parserThread = new Thread(
							InboxListActivity.keywordParser);
					InboxListActivity.parserThread.start();
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
	 * Creates a dialog for connection error notifications with buttons to opt
	 * out or retry a failed connection
	 * 
	 * @return an error notification alert dialog
	 */
	public AlertDialog errorDialog() {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setMessage(R.string.connection_error).setCancelable(false)
				.setPositiveButton("OK", new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int id) {
						// Release synchronization lock
						KeywordSynchronizer.completeSynchronization();
						dialog.cancel();
					}
				}).setNegativeButton("Retry",
						new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog, int id) {
								dialog.cancel();
								showProgressDialog(Global.UPDATE_DIALOG);
								getServerUrl(R.string.update_path);
								InboxListActivity.keywordDownloader = new KeywordDownloader(
										connectHandle);
								InboxListActivity.networkThread = new Thread(
										keywordDownloader);
								InboxListActivity.networkThread.start();
							}
						});
		AlertDialog alert = builder.create();
		return alert;
	}

	/**
	 * creates a dialog that confirms user credentials before accessing the
	 * inbox
	 * 
	 * @return a confirmation alert dialog
	 */
	public AlertDialog accessDialog() {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		final EditText mEditText = new EditText(this);
		if (Global.intervieweeName != null)
			mEditText.setText(Global.intervieweeName);
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
		mEditText.setFilters(new InputFilter[] { filter });
		builder.setMessage(getString(R.string.confirm_id)).setCancelable(false)
				.setPositiveButton(getString(R.string.confirm_button),
						new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog, int id) {
								dialog.cancel();
								Global.intervieweeName = mEditText.getText()
										.toString().trim();
								showCurrentUser();
							}
						}).setNegativeButton(getString(R.string.cancel_button),
						new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog, int id) {
								dialog.cancel();
								Intent l = new Intent(getApplicationContext(),
										MainMenuActivity.class);
								startActivity(l);
								finish();
							}
						}).setView(mEditText);
		AlertDialog alert = builder.create();
		return alert;
	}

	/**
	 * obtains and displays the currently set user ID in title bar
	 */
	private void showCurrentUser() {
		this.activityTitle = this.activityTitle.concat(" | ");
		if (Global.intervieweeName.length() > 30) {
			this.activityTitle = this.activityTitle
					.concat(Global.intervieweeName.substring(0, 30));
			this.activityTitle = this.activityTitle.concat("...");
		} else {
			this.activityTitle = this.activityTitle
					.concat(Global.intervieweeName);
		}
		setTitle(this.activityTitle);
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

		boolean result = super.onCreateOptionsMenu(menu);
		menu.add(1, Global.RESET_ID, 1, getString(R.string.menu_reset))
				.setIcon(R.drawable.search);
		menu.add(1, Global.REFRESH_ID, 2, getString(R.string.menu_refresh))
				.setIcon(R.drawable.refresh);
		menu.add(0, Global.ABOUT_ID, 4, getString(R.string.menu_about))
				.setIcon(R.drawable.about);
		menu.add(0, Global.EXIT_ID, 5, getString(R.string.menu_exit)).setIcon(
				R.drawable.exit);
		menu.add(0, Global.DELETE_ID, 0, getString(R.string.menu_delete))
				.setIcon(R.drawable.delete);
		menu.add(0, Global.HOME_ID, 0, getString(R.string.menu_home)).setIcon(
				R.drawable.home);

		return result;
	}

	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		boolean result = super.onPrepareOptionsMenu(menu);
		// Disable keyword updates and new searches
		if (KeywordSynchronizer.isSynchronizing()) {
			menu.setGroupEnabled(1, false);
		}else{
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
		case Global.HOME_ID:
			Intent l = new Intent(getApplicationContext(),
					MainMenuActivity.class);
			startActivity(l);
			finish();
			return true;
		case Global.REFRESH_ID:
			if (!KeywordSynchronizer.isSynchronizing()) {
				// Acquire synchronization lock
				if (KeywordSynchronizer.tryStartSynchronization()) {
					showProgressDialog(Global.UPDATE_DIALOG);
					getServerUrl(R.string.update_path);
					InboxListActivity.keywordDownloader = new KeywordDownloader(
							connectHandle);
					InboxListActivity.networkThread = new Thread(
							InboxListActivity.keywordDownloader);
					InboxListActivity.networkThread.start();
				} else {
					Log.i(LOG_TAG, "Failed to get synchronization lock");
				}
			}
			return true;
		case Global.ABOUT_ID:
			Intent k = new Intent(getApplicationContext(), AboutActivity.class);
			startActivity(k);
			return true;
		case Global.EXIT_ID:
			this.finish();
			return true;
		case Global.DELETE_ID:
			AlertDialog.Builder builder = new AlertDialog.Builder(this);
			builder.setMessage(getString(R.string.delete_alert)).setCancelable(
					false).setPositiveButton("Yes",
					new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int id) {
							inbox.open();
							inbox
									.deleteAllRecords(InboxAdapter.INBOX_DATABASE_TABLE);
							inbox.close();
							dialog.cancel();
							Intent j = new Intent(getApplicationContext(),
									InboxListActivity.class);
							j.putExtra("block", false);
							startActivity(j);
							finish();

						}
					}).setNegativeButton("No",
					new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int id) {
							dialog.cancel();
						}
					});
			alertDialog = builder.create();
			alertDialog.show();
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	/** Object for list view index - database row ID pairs */
	private class Index {
		public long id;
		public long rowId;
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
		Global.URL = url;
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
			progressDialog.show();
			break;
		case Global.PARSE_DIALOG:
			// Updates previously showing update dialog
			progressDialog.setMessage(getString(R.string.parse_msg));
			break;
		}
	}

	@Override
	protected void onStart() {
		super.onStart();
		if (InboxListActivity.parserThread != null
				&& InboxListActivity.parserThread.isAlive()) {
			Log.i(LOG_TAG, "Parse thread alive.");
			InboxListActivity.keywordParser.setHandlers(this
					.getApplicationContext(), this.connectHandle,
					this.progressHandler);
			showProgressDialog(Global.UPDATE_DIALOG);
			showProgressDialog(Global.PARSE_DIALOG);
			// Cross check that parser thread is still alive
			if (!(InboxListActivity.parserThread != null && InboxListActivity.parserThread
					.isAlive())) {
				progressDialog.dismiss();
			}
		} else if (InboxListActivity.networkThread != null
				&& InboxListActivity.networkThread.isAlive()) {
			Log.i(LOG_TAG, "Network thread is alive.");
			InboxListActivity.keywordDownloader.swap(this.connectHandle);
			showProgressDialog(Global.UPDATE_DIALOG);
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
