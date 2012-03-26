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

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.view.Menu;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import applab.client.search.R;

/**
 * Displays search results.
 */
public class DisplaySearchResultsActivity extends BaseSearchActivity {
    /** for debugging purposes in adb logcat */
    /** interviewee name or ID */
    private String farmerId = "";

    /** search result */
    private String searchResult = "";
    // TODO OKP-1#CFR-29, change distinction between search and request
    /** keywords displayed in inbox */
    private String searchTitle = "";

    /** keywords server request */
    private String request = "";

    /** stored category for this search */
    private String category = "";

    /** stored location for this search */
    private String location = "";

    /** handset submission time */
    private String submissionTime = "";

    /** whether we're coming from the inbox */
    private Boolean fromInbox;

    private Button backButton;
    private Button deleteButton;

    /** database row ID for this search */
    private static long lastRowId;

    /** inbox database */
    public InboxAdapter inboxDatabase;

    /** view for inbox search results */
    private TextView searchResultsTextView;

    @Override
    protected String getTitleName() {
        return getString(R.string.inbox_title);
    }

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        boolean configurationChanged = false;
        if (savedInstanceState != null) {
            configurationChanged = savedInstanceState.getBoolean("changed");
        }
        setContentView(R.layout.inbox);

        Bundle extras = this.getIntent().getExtras();
        if (extras != null) {
            this.searchResult = extras.getString("content");
            this.searchTitle = extras.getString("searchTitle");
            this.farmerId = extras.getString("name");

            // From SearchActivity
            this.request = extras.getString("request");
            this.location = extras.getString("location");
            this.fromInbox = extras.getBoolean("fromInbox", false);
            this.category = extras.getString("category");

            if (!configurationChanged) {
                DisplaySearchResultsActivity.lastRowId = extras.getLong("rowId");
            }
        }
        this.searchResultsTextView = (TextView)findViewById(R.id.content_view);
        TextView searchResultTitle = (TextView)findViewById(R.id.search);
        TextView searchDateDisplay = (TextView)findViewById(R.id.Date_time);
        this.backButton = (Button)findViewById(R.id.back_button);
        this.deleteButton = (Button)findViewById(R.id.delete_button);

        Cursor inboxCursor = null;
        try {
            this.inboxDatabase = new InboxAdapter(this);
            this.inboxDatabase.open();

            if (!configurationChanged && searchResult != null) {
                DisplaySearchResultsActivity.lastRowId =
                        inboxDatabase.insertRecord(searchTitle, searchResult, farmerId, location, "Complete", request);
            }

            /**
             * rowId is either supplied through a bundle or at database insert
             */
            inboxCursor = this.inboxDatabase.readRecord(DisplaySearchResultsActivity.lastRowId);
            int titleColumn = inboxCursor.getColumnIndexOrThrow(InboxAdapter.KEY_TITLE);
            int bodyColumn = inboxCursor.getColumnIndexOrThrow(InboxAdapter.KEY_BODY);
            int dateColumn = inboxCursor.getColumnIndexOrThrow(InboxAdapter.KEY_DATE);
            int nameColumn = inboxCursor.getColumnIndexOrThrow(InboxAdapter.KEY_NAME);
            int requestColumn = inboxCursor.getColumnIndexOrThrow(InboxAdapter.KEY_REQUEST);
            int locationColumn = inboxCursor.getColumnIndexOrThrow(InboxAdapter.KEY_LOCATION);

            if (this.request == null || this.request.length() == 0) {
                this.request = inboxCursor.getString(requestColumn);
            }
            if (this.location == null || this.location.length() == 0) {
                try {
                    this.location = inboxCursor.getString(locationColumn);
                }
                catch (Exception e) {
                    // No location was captured
                    this.location = "";
                }
            }

            if (this.farmerId == null || this.farmerId.length() == 0) {
                farmerId = inboxCursor.getString(nameColumn);
            }

            this.submissionTime = inboxCursor.getString(dateColumn);
            searchResultsTextView.setText(inboxCursor.getString(bodyColumn));
            searchDateDisplay.setText(submissionTime);

            searchResultTitle.setText(inboxCursor.getString(titleColumn));
        }
        catch (Exception e) {
            searchResultsTextView.setText(e.toString());
        }
        finally {
            if (inboxCursor != null) {
                inboxCursor.close();
            }
        }

        // Log access
        if (!configurationChanged) {
            insertLogEntry();
        }

        backButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                if (fromInbox) {
                    openRecentSearches();
                }
                else {
                    finish();
                }
            }
        });

        deleteButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                // show a dialog to confirm the delete
                DialogInterface.OnClickListener okListener = new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        inboxDatabase.deleteRecord(InboxAdapter.INBOX_DATABASE_TABLE, DisplaySearchResultsActivity.lastRowId);
                        showToast(R.string.record_deleted, Toast.LENGTH_LONG);

                        if (fromInbox) {
                            openRecentSearches();
                        }
                        else {
                            startNewSearch();
                        }
                    }
                };
                ErrorDialogManager.show(R.string.delete_alert1, null,
                        okListener, "Yes", null, "No");
            }
        });
    }

    private void startNewSearch() {
        Intent searchActivity = new Intent(getApplicationContext(), SearchActivity.class);
        searchActivity.putExtra("name", farmerId);
        searchActivity.putExtra("location", location);
        switchToActivity(searchActivity);
    }

    private void openRecentSearches() {
        Intent inboxListActivity = new Intent(getApplicationContext(), InboxListActivity.class);
        inboxListActivity.putExtra("name", this.farmerId);
        inboxListActivity.putExtra("location", this.location);
        switchToActivity(inboxListActivity);
    }

    /**
     * makes an inbox access log entry
     *
     * @return the last table insert ID
     */
    private long insertLogEntry() {
        DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        ContentValues log = new ContentValues();
        log.put(InboxAdapter.KEY_REQUEST, request.replace(">", ""));
        log.put(InboxAdapter.KEY_DATE, dateFormat.format(new Date()));
        log.put(InboxAdapter.KEY_NAME, this.farmerId);
        log.put(InboxAdapter.KEY_LOCATION, this.location);
        log.put(InboxAdapter.KEY_CATEGORY, this.category);
        return inboxDatabase.insertLog(InboxAdapter.ACCESS_LOG_DATABASE_TABLE, log);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        inboxDatabase.close();
    }

    // Remove unnecessary menu items for this activity
    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        boolean result = super.onPrepareOptionsMenu(menu);
        menu.removeItem(GlobalConstants.INBOX_ID);
        menu.removeItem(GlobalConstants.SETTINGS_ID);
        menu.removeItem(GlobalConstants.DELETE_ID);
        menu.removeItem(GlobalConstants.ABOUT_ID);
        menu.removeItem(GlobalConstants.REFRESH_ID); // do not show update keywords option
        return result;
    }
}
