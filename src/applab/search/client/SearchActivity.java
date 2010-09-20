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
import java.util.ArrayList;
import java.util.Date;

import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Responsible for constructing, displaying keyword option sequences, and submitting search queries.
 * 
 */
public class SearchActivity extends BaseSearchActivity {
    /** for debugging purposes in adb logcat */
    private static final String LOG_TAG = "Radio";

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

    /** holds selected keywords */
    private ArrayList<String> selectedKeywords;

    /** the active search keywords database table */
    private String activeDatabaseTable;

    /** holds the selected radio button ID */
    private int radioId;

    /** when set true search query can be submitted */
    private boolean canSubmitQuery;

    /** set true when there are no more keywords in a given sequence */
    private boolean endOfKeywordSequence;

    /** search sequence number */
    private int sequence;

    /** true if there has been a configuration change */
    private boolean configurationChanged;
    
    private String lastSelection = "";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.searchDatabase = new Storage(this);
        if (savedInstanceState != null) {
            configurationChanged = savedInstanceState.getBoolean("changed");
            if (configurationChanged) {
                Log.w(LOG_TAG, "Activity RESTART");
            }
        }
        setContentView(R.layout.main);
        this.activeDatabaseTable = StorageManager.getActiveTable();
        this.keywordChoices = (RadioGroup)findViewById(R.id.radio_group);
        this.keywordChoices.bringToFront();
        this.nextButtonSmall = (Button)findViewById(R.id.next_button);
        this.nextButtonLarge = (Button)findViewById(R.id.next);
        this.backButton = (Button)findViewById(R.id.back_button);
        this.searchPath = (TextView)findViewById(R.id.search);
        this.searchPath.setEllipsize(TextUtils.TruncateAt.START);
        this.searchPath.setSingleLine();
        this.searchPath.setHorizontallyScrolling(true);
        this.startLayout = (LinearLayout)findViewById(R.id.startLayout);
        this.nextButtonSmall.setText(getString(R.string.next_button));
        this.layout = (LinearLayout)findViewById(R.id.layout);
        this.backButton.setText(getString(R.string.back_button));

        // Initialize selectedKeywords to empty array list
        this.selectedKeywords = new ArrayList<String>();

        if (!SynchronizationManager.isSynchronizing()) {
            ActivityState instanceState = (ActivityState)getLastNonConfigurationInstance();

            if (instanceState != null) {
                this.sequence = instanceState.currentKeywordSegmentIndex;
                this.selectedKeywords = instanceState.keywords;
                this.canSubmitQuery = instanceState.canSubmitQuery;
                this.endOfKeywordSequence = instanceState.endOfKeywordSequence;
                String query = "";
                for (String keywordSegment : this.selectedKeywords) {
                    query = query.concat(" >" + keywordSegment);
                }
                this.searchPath.setText(query);
            }

            buildRadioList();
        }

        if (this.sequence > 0) {
            this.startLayout.setVisibility(View.GONE);
        }
        else {
            this.layout.setVisibility(View.GONE);
        }

        this.nextButtonLarge.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                radioId = keywordChoices.getCheckedRadioButtonId();

                if (radioId != -1) {
                    RadioButton rb = (RadioButton)findViewById(radioId);
                    String choice = rb.getText().toString();
                    selectedKeywords.add(new String(choice));
                    searchPath.setText(getKeywordDisplay());
                    ++sequence;
                    buildRadioList();
                    startLayout.setVisibility(View.GONE);
                    layout.setVisibility(View.VISIBLE);
                }
                else {
                    Toast toast = Toast.makeText(getApplicationContext(),
                            getString(R.string.empty_select),
                            Toast.LENGTH_SHORT);
                    toast.setGravity(Gravity.CENTER, 0, 0);
                    toast.show();
                }
            }
        });

        this.nextButtonSmall.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                radioId = keywordChoices.getCheckedRadioButtonId();

                if (radioId != -1 && !endOfKeywordSequence) {
                    RadioButton rb = (RadioButton)findViewById(radioId);
                    String choice = rb.getText().toString();
                    String query = "";
                    selectedKeywords.add(new String(choice));

                    for (int i = 0; i < selectedKeywords.size(); i++) {
                        query = query.concat(" >" + selectedKeywords.get(i));
                    }

                    searchPath.setText(query);
                    ++sequence;
                    buildRadioList();
                }
                else {
                    if (!endOfKeywordSequence) {
                        showToast(R.string.empty_select);
                    }
                }

                if (canSubmitQuery) {
                    submitSearch();
                    canSubmitQuery = false;
                }
                if (endOfKeywordSequence) {
                    canSubmitQuery = true;
                }
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
                    lastSelection = selectedKeywords.get(selectedKeywords.size() - 1);
                    selectedKeywords.remove(selectedKeywords.size() - 1);
                    keywordChoices.clearCheck();
                    keywordChoices.removeAllViews();
                    buildRadioList();
                    String query = "";
                    for (int i = 0; i < selectedKeywords.size(); i++) {
                        query = query.concat(" >" + selectedKeywords.get(i));
                    }
                    searchPath.setText(query);
                }
            }
        });

    }

    /**
     * returns the category + full set of keyword segments, each delimited by '> '
     */
    private String getKeywordDisplay() {
        StringBuilder keywordDisplay = new StringBuilder();
        boolean isFirstSegment = true;
        for (String keywordSegment : this.selectedKeywords) {
            if (isFirstSegment) {
                isFirstSegment = false;
            }
            else {
                keywordDisplay.append(" >");
            }
            keywordDisplay.append(keywordSegment);
        }

        return keywordDisplay.toString();
    }

    /**
     * Called when we wish to submit a search
     */
    private void submitSearch() {
        // TODO: check if we're synchronizing first?
        /*
         * if (SearchActivity.isUpdatingKeywords) { SearchActivity.networkThread = new Thread(
         * SearchActivity.keywordDownloader); SearchActivity.networkThread.start();
         * showProgressDialog(Global.UPDATE_DIALOG); }
         */

        StringBuilder keyword = new StringBuilder();
        // we need to start at index 1 since the category determines "selectedKeywords[0]"
        for (int i = 1; i < selectedKeywords.size(); i++) {
            keyword.append(selectedKeywords.get(i) + "%20");
        }

        DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        SearchRequest request = new SearchRequest(keyword.toString(),
                    Global.intervieweeName, dateFormat.format(new Date()));

        request.submitInBackground(new Handler() {
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
                this.nextButtonSmall.setEnabled(false);
                showSearchResults(searchRequest);
                break;
            case SearchRequest.SEARCH_SUBMISSION_FAILURE:
                // for the error-out case, store the result in our inbox to send later
                // TODO: we shouldn't ever need these to appear in the UI!
                SearchRequest incompleteSearchRequest = (SearchRequest)message.obj;
                showSearchResults(incompleteSearchRequest);
                break;
        }
    }

    private void showSearchResults(SearchRequest searchRequest) {
        Intent searchResultActivity = new Intent(getApplicationContext(), DisplaySearchResultsActivity.class);
        searchResultActivity.putExtra("search", getKeywordDisplay());
        searchResultActivity.putExtra("name", searchRequest.getFarmerId());
        searchResultActivity.putExtra("location", searchRequest.getLocation());

        String searchResult = searchRequest.getResult();
        // was this a successful search?
        if (searchResult != null && searchResult.length() > 0) {
            searchResultActivity.putExtra("content", searchResult);
            searchResultActivity.putExtra("fromSearchActivity", true);
        }
        else {
            // TODO: clean up the extras taxonomy
            searchResultActivity.putExtra("send", true);
            searchResultActivity.putExtra("request", searchRequest.getKeyword());
        }

        startActivity(searchResultActivity);
        finish();
    }

    /**
     * Builds search sequences.
     */
    private void buildRadioList() {
        this.searchDatabase.open();
        RadioButton radioButton;
        String radioButtonText;
        int radioButtonId = 1;
        boolean remove = false;
        if (sequence == 0) {
            Cursor searchCursor = searchDatabase.selectMenuOptions(
                    activeDatabaseTable, "col" + Integer.toString(sequence), null);
            startManagingCursor(searchCursor);

            if (searchCursor.moveToFirst()) {
                while (!searchCursor.isAfterLast()) {
                    int option = searchCursor.getColumnIndexOrThrow("col" + Integer.toString(sequence));
                    radioButton = new RadioButton(this);
                    radioButton.setId(radioButtonId++);
                    radioButtonText = searchCursor.getString(option);
                    radioButton.setText(radioButtonText);
                    if(radioButtonText.compareTo(lastSelection) == 0){
                    	radioButton.setChecked(true);
                    	radioButton.setSelected(true);
                    };
                    radioButton.setTextColor(-16777216);
                    radioButton.setTextSize(21);
                    radioButton.setPadding(40, 1, 1, 1);
                    this.keywordChoices.addView(radioButton);
                    searchCursor.moveToNext();
                }
            }
        }
        else {
            String condition = "col0='" + selectedKeywords.get(0) + "'";

            for (int keywordIndex = 1; keywordIndex < selectedKeywords.size(); keywordIndex++) {
                String keywordSegment = selectedKeywords.get(keywordIndex);
                condition = condition.concat(" AND col" + keywordIndex + "='" + keywordSegment + "'");
            }

            Cursor searchCursor = searchDatabase.selectMenuOptions(
                    activeDatabaseTable, "col" + Integer.toString(sequence), condition);
            startManagingCursor(searchCursor);
            if (searchCursor.moveToFirst()) {
                while (!searchCursor.isAfterLast()) {
                    int option = searchCursor.getColumnIndexOrThrow("col" + Integer.toString(sequence));
                    if (searchCursor.getString(option) == null) {
                        this.endOfKeywordSequence = true;
                        this.nextButtonSmall.setText(getString(R.string.send_button));
                        Toast toast = Toast.makeText(getApplicationContext(), getString(R.string.end_of_search), Toast.LENGTH_LONG);
                        toast.setGravity(Gravity.CENTER, 0, 0);
                        toast.show();
                        break;
                    }
                    if (!remove) {
                        this.keywordChoices.clearCheck();
                        this.keywordChoices.removeAllViews();
                        remove = true;
                    }
                    radioButton = new RadioButton(this);
                    radioButton.setId(radioButtonId++);
                    radioButtonText = searchCursor.getString(option);
                    radioButton.setText(radioButtonText);
                    if(radioButtonText.compareTo(lastSelection) == 0){
                    	radioButton.setChecked(true);
                    	radioButton.setSelected(true);
                    }
                    radioButton.setTextColor(-16777216);
                    radioButton.setTextSize(21);
                    radioButton.setPadding(40, 1, 1, 1);
                    this.keywordChoices.addView(radioButton);
                    searchCursor.moveToNext();
                }
            }
        }
        this.searchDatabase.close();
    }

    @Override
    protected boolean confirmRefresh() {
        return true;
    }

    /**
     * If keywords have been updated, our search is invalid and we need to reset the search
     * 
     * TODO: only do this if the current selection is still invalid
     */
    @Override
    protected void onKeywordUpdateComplete() {
        if (this.selectedKeywords != null) {
            this.selectedKeywords.clear();
        }
        searchPath.setText("Search: ");
        this.sequence = 0;
        this.activeDatabaseTable = StorageManager.getActiveTable();
        this.keywordChoices.clearCheck();
        this.keywordChoices.removeAllViews();
        buildRadioList();
        this.layout.setVisibility(View.GONE);
        this.startLayout.setVisibility(View.VISIBLE);
        showToast(R.string.refreshed, Toast.LENGTH_LONG);
    }

    @Override
    public Object onRetainNonConfigurationInstance() {
        ActivityState instanceState = new ActivityState();
        instanceState.currentKeywordSegmentIndex = sequence;
        instanceState.keywords = selectedKeywords;
        instanceState.canSubmitQuery = this.canSubmitQuery;
        instanceState.endOfKeywordSequence = this.endOfKeywordSequence;
        return instanceState;
    }

    /**
     * object for holding activity data to store for orientation changes
     */
    private class ActivityState {
        int currentKeywordSegmentIndex;
        ArrayList<String> keywords;
        boolean endOfKeywordSequence;
        boolean canSubmitQuery;
    }
}
