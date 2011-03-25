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

import java.util.ArrayList;
import java.util.HashMap;

import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.Menu;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;
import applab.client.location.GpsManager;

/**
 * Responsible for constructing, displaying keyword option sequences, and submitting search queries.
 * 
 */
public class SearchActivity extends BaseSearchActivity {

    private static final String SEARCH_PATH_DELIMETER = " >";

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

    /** holds the selected radio button ID */
    private int radioId;

    /** search sequence number */
    private int sequence;

    private String lastSelection = "";

    private String currentCondition;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.searchDatabase = new Storage(this);

        setContentView(R.layout.main);
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
                String query = "";
                for (String keywordSegment : this.selectedKeywords) {
                    query = query.concat(" >" + keywordSegment);
                }
                this.searchPath.setText(query);
            }
            else {
                this.sequence = -1;
            }

            buildKeywordsMenu();
        }

        if (this.sequence > 1) {
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
                    searchPath.setText(getSearchPath());
                    ++sequence;
                    buildKeywordsMenu();
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

                if (radioId != -1) {
                    RadioButton rb = (RadioButton)findViewById(radioId);
                    String choice = rb.getText().toString();
                    String query = "";
                    selectedKeywords.add(new String(choice));

                    for (int i = 0; i < selectedKeywords.size(); i++) {
                        query = query.concat(" >" + selectedKeywords.get(i));
                    }

                    searchPath.setText(query);
                    ++sequence;
                    buildKeywordsMenu();
                }
                else {
                    showToast(R.string.empty_select);
                }
            }

        });

        backButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                resetSelectionInfo();
                goBack();
            }
        });

    }

    private void resetSelectionInfo() {
        if (selectedKeywords.size() > 0) {
            nextButtonSmall.setText(getString(R.string.next_button));
            if (selectedKeywords.size() == 1) {
                layout.setVisibility(View.GONE);
                startLayout.setVisibility(View.VISIBLE);
            }
            --sequence;
            lastSelection = selectedKeywords.get(selectedKeywords.size() - 1);
            selectedKeywords.remove(selectedKeywords.size() - 1);

        }
    }

    private void goBack() {
        keywordChoices.clearCheck();
        keywordChoices.removeAllViews();
        buildKeywordsMenu();
        String query = "";
        for (int i = 0; i < selectedKeywords.size(); i++) {
            query = query.concat(" >" + selectedKeywords.get(i));
        }
        searchPath.setText(query);
    }

    /**
     * returns the category + full set of keyword segments, optionally each delimited by '> '
     */
    private String getSearchPath(String delimeter) {
        StringBuilder keywordDisplay = new StringBuilder();
        boolean isFirstSegment = true;
        for (String keywordSegment : this.selectedKeywords) {
            if (isFirstSegment) {
                isFirstSegment = false;
            }
            else {
                keywordDisplay.append(delimeter);
            }
            keywordDisplay.append(keywordSegment);
        }

        return keywordDisplay.toString();
    }

    private String getSearchPath() {
        return getSearchPath(" >");
    }

    private String getCategoryFromSearchPath(String searchPath, String delimeter) {

        String temp = "";
        String[] searchPathArray = searchPath.split(delimeter);
        for (int i = 0; i < searchPathArray.length; i++) {
            temp = searchPathArray[i].trim();
            if (temp != null && !temp.equals("")) {
                 return temp;
            }
        }
        return temp;
    }

    private void showSearchResults(String farmerId, String location, String content) {
        Intent searchResultActivity = new Intent(getApplicationContext(), DisplaySearchResultsActivity.class);
        String searchPath = getSearchPath();
        searchResultActivity.putExtra("searchTitle", searchPath);
        searchResultActivity.putExtra("name", farmerId);
        searchResultActivity.putExtra("location", location);
        searchResultActivity.putExtra("request", getSearchPath(" "));
        searchResultActivity.putExtra("category", getCategoryFromSearchPath(searchPath, SEARCH_PATH_DELIMETER));

        // Was this a successful search?
        if (content != null && content.length() > 0) {
            searchResultActivity.putExtra("content", content);
        }

        switchToActivity(searchResultActivity);
    }

    private String getImagePath() {
        String path = "";
        for (String keywordSegment : this.selectedKeywords) {
            path = path.concat(keywordSegment + " ");
        }

        // Convert to lower case and replace spaces with under scores
        return path.toLowerCase().replace(" ", "_");
    }

    /**
     * Builds search menus.
     */
    private void buildKeywordsMenu() {
        this.searchDatabase.open();
        Cursor searchCursor = null;
        String imagePath = getImagePath();
        int radioButtonId = 1;

        try {
            if (sequence == -1) {
                searchCursor = searchDatabase.selectMenuOptions(GlobalConstants.DATABASE_TABLE, Storage.KEY_CATEGORY, null);
                while (searchCursor.moveToNext()) {
                    int option = searchCursor.getColumnIndexOrThrow(Storage.KEY_CATEGORY);
                    addRadioButton(imagePath, radioButtonId, searchCursor, option);
                    radioButtonId++;
                }

                searchCursor.close();
            }
            else {
                String condition = Storage.KEY_CATEGORY + "='" + selectedKeywords.get(0) + "'";

                for (int keywordIndex = 1; keywordIndex < selectedKeywords.size(); keywordIndex++) {
                    String keywordSegment = selectedKeywords.get(keywordIndex);
                    condition = condition.concat(" AND col" + (keywordIndex - 1) + "='" + keywordSegment + "'");
                }

                searchCursor = searchDatabase.selectMenuOptions(GlobalConstants.DATABASE_TABLE, "col" + Integer.toString(sequence),
                        condition);

                // Save the current Sequence and current Condition in case this is the last menu, in which case we'll
                // have to use the previous "state" to get content
                currentCondition = condition;

                Boolean isFirst = true;
                while (searchCursor.moveToNext()) {
                    int option = searchCursor.getColumnIndexOrThrow("col" + Integer.toString(sequence));
                    if (searchCursor.getString(option) == null) {
                        // We're at the end of the keyword sequence, so we just launch the results and exit
                        launchResultsDisplay(currentCondition);
                        resetSelectionInfo();
                    }
                    else {
                        if (isFirst) {
                            this.keywordChoices.clearCheck();
                            this.keywordChoices.removeAllViews();
                            isFirst = false;
                        }
                        radioButtonId = addRadioButton(imagePath, radioButtonId, searchCursor, option);
                    }
                }
            }
        }
        finally {
            if (searchCursor != null) {
                searchCursor.close();
            }
            this.searchDatabase.close();
        }
    }

    /**
     * @param imagePath
     * @param radioButtonId
     * @param searchCursor
     * @param option
     * @return
     */
    public int addRadioButton(String imagePath, int radioButtonId, Cursor searchCursor, int option) {
        RadioButton radioButton;
        String radioButtonText;
        radioButton = new RadioButton(this);
        radioButton.setId(radioButtonId++);
        radioButtonText = searchCursor.getString(option);
        radioButton.setText(radioButtonText);
        trySetImage(radioButton, imagePath + radioButtonText.trim().toLowerCase().replace(" ", "_"));
        if (radioButtonText.compareTo(lastSelection) == 0) {
            radioButton.setChecked(true);
            radioButton.setSelected(true);
        }
        radioButton.setTextColor(-16777216);
        radioButton.setTextSize(21);
        radioButton.setPadding(40, 1, 1, 1);
        this.keywordChoices.addView(radioButton);
        return radioButtonId;
    }

    private void launchResultsDisplay(String condition) {
        // Get the content of the prev. sequence.
        HashMap<String, String> results = searchDatabase.selectContent(GlobalConstants.DATABASE_TABLE, condition);

        String content = results.get("content");
        String attribution = results.get("attribution");
        String updated = results.get("updated");

        if (attribution != null && attribution.length() > 0) {
            content += "\n\nAttribution: " + attribution;
        }

        if (updated != null && updated.length() > 0) {
            content += "\n\nUpdated: " + updated;
        }

        showSearchResults(GlobalConstants.intervieweeName, GpsManager.getInstance().getLocationAsString(), content);
    }

    private void trySetImage(RadioButton radioButton, String imagePath) {
        if (ImageFilesUtility.imageExists(imagePath)) {
            radioButton.setCompoundDrawablesWithIntrinsicBounds(null, null, null, ImageFilesUtility.getImageAsDrawable(imagePath));
        }
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
        this.sequence = -1;
        this.keywordChoices.clearCheck();
        this.keywordChoices.removeAllViews();
        buildKeywordsMenu();
        this.layout.setVisibility(View.GONE);
        this.startLayout.setVisibility(View.VISIBLE);
        showToast(R.string.refreshed, Toast.LENGTH_LONG);
    }

    @Override
    public Object onRetainNonConfigurationInstance() {
        ActivityState instanceState = new ActivityState();
        instanceState.currentKeywordSegmentIndex = sequence;
        instanceState.keywords = selectedKeywords;
        return instanceState;
    }

    /**
     * object for holding activity data to store for orientation changes
     */
    private class ActivityState {
        int currentKeywordSegmentIndex;
        ArrayList<String> keywords;
    }

    // Remove unnecessary menu items for this activity
    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        boolean result = super.onPrepareOptionsMenu(menu);
        menu.removeItem(GlobalConstants.SETTINGS_ID);
        menu.removeItem(GlobalConstants.DELETE_ID);
        menu.removeItem(GlobalConstants.ABOUT_ID);
        menu.removeItem(GlobalConstants.REFRESH_ID); // Do not show the update keywords option on search activity
        return result;
    }
}
