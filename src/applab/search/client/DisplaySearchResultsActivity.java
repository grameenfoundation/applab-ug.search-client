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
import android.os.Handler;
import android.os.Message;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Displays search results.
 */
public class DisplaySearchResultsActivity extends BaseSearchActivity {
    /** for debugging purposes in adb logcat */
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

    /** whether we're coming from the inbox */
    private Boolean fromInbox;

    private Button backButton;
    private Button deleteButton;
    private Button sendButton;

    /** database row ID for this search */
    private static long lastRowId;

    /** set true to display "Back" button for inbox list view */
    private boolean showBackButton;

    /** set true for incomplete searches */
    private boolean isIncompleteSearch;

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
            this.search = extras.getString("search");
            this.name = extras.getString("name");

            // From SearchActivity
            this.isIncompleteSearch = extras.getBoolean("send", false);
            this.request = extras.getString("request");
            this.location = extras.getString("location");
            this.fromInbox = extras.getBoolean("fromInbox", false);

            if (!configurationChanged) {
                DisplaySearchResultsActivity.lastRowId = extras.getLong("rowId");
            }
        }
        this.searchResultsTextView = (TextView)findViewById(R.id.content_view);
        TextView searchResultTitle = (TextView)findViewById(R.id.search);
        TextView searchDateDisplay = (TextView)findViewById(R.id.Date_time);
        this.backButton = (Button)findViewById(R.id.back_button);
        this.deleteButton = (Button)findViewById(R.id.delete_button);
        this.sendButton = (Button)findViewById(R.id.send_button);

        Cursor inboxCursor = null;
        try {
            this.inboxDatabase = new InboxAdapter(this);
            this.inboxDatabase.open();

            if (!configurationChanged) {
                if (searchResult != null) {
                    DisplaySearchResultsActivity.lastRowId =
                            inboxDatabase.insertRecord(search, searchResult, name, "", "Complete", "");
                }
                else if (isIncompleteSearch) {
                    // Save as incomplete search
                    DisplaySearchResultsActivity.lastRowId =
                            inboxDatabase.insertRecord(search, getString(R.string.search_failure, search), name,
                                    location, "Incomplete", this.request);
                }
            }

            if (this.fromInbox) {
                // From the list view, so return to it when done
                this.showBackButton = true;
                this.backButton.setText(getString(R.string.back_button));
            }
            /**
             * rowId is either supplied through a bundle or at database insert
             */
            inboxCursor = this.inboxDatabase.readRecord(DisplaySearchResultsActivity.lastRowId);
            int titleColumn = inboxCursor.getColumnIndexOrThrow(InboxAdapter.KEY_TITLE);
            int bodyColumn = inboxCursor.getColumnIndexOrThrow(InboxAdapter.KEY_BODY);
            int dateColumn = inboxCursor.getColumnIndexOrThrow(InboxAdapter.KEY_DATE);
            int statusColumn = inboxCursor.getColumnIndexOrThrow(InboxAdapter.KEY_STATUS);
            int nameColumn = inboxCursor.getColumnIndexOrThrow(InboxAdapter.KEY_NAME);
            int requestColumn = inboxCursor.getColumnIndexOrThrow(InboxAdapter.KEY_REQUEST);
            int locationColumn = inboxCursor.getColumnIndexOrThrow(InboxAdapter.KEY_LOCATION);
            if (inboxCursor.getString(statusColumn).contentEquals("Incomplete")) {
                this.isIncompleteSearch = true;
            }
            if (this.request == null || this.request.length() == 0) {
                this.request = inboxCursor.getString(requestColumn);
            }
            if (this.location == null || this.location.length() == 0) {
                this.location = inboxCursor.getString(locationColumn);
            }

            if (this.name == null || this.name.length() == 0) {
                name = inboxCursor.getString(nameColumn);
            }

            this.submissionTime = inboxCursor.getString(dateColumn);
            searchResultsTextView.setText(inboxCursor.getString(bodyColumn));
            searchDateDisplay.setText(submissionTime);

            searchResultTitle.setText(inboxCursor.getString(titleColumn));

            if (!this.showBackButton) {
                this.backButton.setText(getString(R.string.new_button));
                this.backButton.setTextSize(15);
            }
        }
        catch (Exception e) {
            searchResultsTextView.setText(e.toString());
        }
        finally {
            if (inboxCursor != null) {
                inboxCursor.close();
            }
        }

        if (isIncompleteSearch) {
            sendButton.setEnabled(true);
        }
        else {
            sendButton.setEnabled(false);

            if (this.fromInbox && !configurationChanged) {
                insertLogEntry();
            }
        }

        backButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                if (showBackButton) {
                    openRecentSearches();
                }
                else {
                    startNewSearch();
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

                        if (showBackButton) {
                            openRecentSearches();
                        }
                        else {
                            startNewSearch();
                        }
                    }
                };
                ErrorDialogManager.show(R.string.delete_alert1, getApplicationContext(), 
                        okListener, "Yes", null, "No");
            }
        });

        sendButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                submitSearch();
            }
        });
    }
    
    private void submitSearch() {
        SearchRequest incompleteSearchRequest = new SearchRequest(this.request, this.name, this.submissionTime, this.location);
        incompleteSearchRequest.submitInBackground(this, new Handler() {
            @Override
            public void handleMessage(Message message) {
                onSearchSubmission(message);
            }
        });     
    }

    private void onSearchSubmission(Message message) {
        switch (message.what) {
            case SearchRequest.SEARCH_SUBMISSION_SUCCESS:
                // the search results are stored in the message object
                SearchRequest searchRequest = (SearchRequest)message.obj;
                // Update content for this incomplete query
                this.inboxDatabase.updateRecord(DisplaySearchResultsActivity.lastRowId, searchRequest.getResult());
                
                // Reload this view by restarting itself.
                Intent displayResults = new Intent(getApplicationContext(), DisplaySearchResultsActivity.class);
                displayResults.putExtra("rowId", DisplaySearchResultsActivity.lastRowId);
                displayResults.putExtra("name", name);
                displayResults.putExtra("location", location);
                switchToActivity(displayResults);
                break;
        }
    }

    private void startNewSearch() {
        Intent searchActivity = new Intent(getApplicationContext(), SearchActivity.class);
        searchActivity.putExtra("name", name);
        searchActivity.putExtra("location", location);
        switchToActivity(searchActivity);
    }

    private void openRecentSearches() {
        Intent inboxListActivity = new Intent(getApplicationContext(), InboxListActivity.class);
        inboxListActivity.putExtra("name", this.name);
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
        log.put(InboxAdapter.KEY_NAME, Global.intervieweeName);
        return inboxDatabase.insertLog(InboxAdapter.ACCESS_LOG_DATABASE_TABLE, log);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        inboxDatabase.close();
    }
}
