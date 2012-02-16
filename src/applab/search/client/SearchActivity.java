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
import applab.client.search.R;

/**
 * Responsible for constructing, displaying option pages, and submitting search queries.
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

    /** Layout for first search page */
    private LinearLayout startLayout;

    /** Layout for all subsequent pages */
    private LinearLayout layout;

    /** The radio group to show on this page */
    private RadioGroup pageRadioGroup;

    /**
     * A hashmap to hold the ids of the currently displayed items. It is used to get the id to use in the next selection
     **/
    private HashMap<Integer, String> pageItemIds;

    /** View where breadcrumb is displayed */
    private TextView breadcrumbView;

    /** Holds all the previously selected items in this path/breadcrumb */
    private ArrayList<BreadcrumbItem> breadcrumbItems;

    private class BreadcrumbItem {
        String label;
        String id;
    }

    /** Holds the selected radio button ID */
    private int selectedRadioButtonId;

    /** Page index. Tracking this makes some operations easier */
    private int pageIndex = 0;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.searchDatabase = new Storage(this);

        setContentView(R.layout.main);
        this.pageRadioGroup = (RadioGroup)findViewById(R.id.radio_group);
        this.pageRadioGroup.bringToFront();
        this.nextButtonSmall = (Button)findViewById(R.id.next_button);
        this.nextButtonLarge = (Button)findViewById(R.id.next);
        this.backButton = (Button)findViewById(R.id.back_button);
        this.breadcrumbView = (TextView)findViewById(R.id.search);
        this.breadcrumbView.setEllipsize(TextUtils.TruncateAt.START);
        this.breadcrumbView.setSingleLine();
        this.breadcrumbView.setHorizontallyScrolling(true);
        this.startLayout = (LinearLayout)findViewById(R.id.startLayout);
        this.nextButtonSmall.setText(getString(R.string.next_button));
        this.layout = (LinearLayout)findViewById(R.id.layout);
        this.backButton.setText(getString(R.string.back_button));

        // Initialize selectedKeywords to empty array list, and also pageItemIds
        this.breadcrumbItems = new ArrayList<BreadcrumbItem>();
        this.pageItemIds = new HashMap<Integer, String>();

        // Build the menu
        buildMenu();

        this.nextButtonLarge.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                // Get the selected radio button, if any
                selectedRadioButtonId = pageRadioGroup.getCheckedRadioButtonId();

                if (selectedRadioButtonId != -1) {
                    // Use it to find the selected label, and add it to the breadcrumb
                    updateBreadcrumbFromRadioButtonSelection();

                    // Also update the pageNumber
                    ++pageIndex;

                    // Build the menu
                    buildMenu();
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
                selectedRadioButtonId = pageRadioGroup.getCheckedRadioButtonId();

                if (selectedRadioButtonId != -1) {
                    updateBreadcrumbFromRadioButtonSelection();

                    // Also update the pageNumber
                    ++pageIndex;

                    // Build the menu
                    buildMenu();
                }
                else {
                    showToast(R.string.empty_select);
                }
            }

        });

        backButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                goBack();
            }
        });

    }

    private void goBack() {
        // First get the previously selected item
        String selectedItemId = this.getPreviousSelectedItemId();

        // Decrement the page number, but make sure it's atleast 1
        pageIndex--;
        pageIndex = Math.max(pageIndex, 0);

        if (breadcrumbItems.size() > 0) {
            breadcrumbItems.remove(breadcrumbItems.size() - 1);
        }

        pageRadioGroup.clearCheck();
        pageRadioGroup.removeAllViews();

        buildMenu(selectedItemId);
        breadcrumbView.setText(getBreadcrumb());
    }

    /**
     * returns the category + full set of keyword segments, optionally each delimited by '> '
     */
    private String getBreadcrumb(String delimeter) {
        StringBuilder breadcrumb = new StringBuilder();
        boolean isFirstSegment = true;
        for (BreadcrumbItem breadcrumbSegment : this.breadcrumbItems) {
            if (isFirstSegment) {
                isFirstSegment = false;
            }
            else {
                breadcrumb.append(delimeter);
            }
            breadcrumb.append(breadcrumbSegment.label);
        }

        return breadcrumb.toString();
    }

    private String getBreadcrumb() {
        return getBreadcrumb(" >");
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
        String searchPath = getBreadcrumb();
        searchResultActivity.putExtra("searchTitle", searchPath);
        searchResultActivity.putExtra("name", farmerId);
        searchResultActivity.putExtra("location", location);
        searchResultActivity.putExtra("request", getBreadcrumb(" "));
        searchResultActivity.putExtra("category", getCategoryFromSearchPath(searchPath, SEARCH_PATH_DELIMETER));

        // Was this a successful search?
        if (content != null && content.length() > 0) {
            searchResultActivity.putExtra("content", content);
        }

        switchToActivity(searchResultActivity);
    }


    /**
     * Builds current menu
     *
     * @param string
     */
    private void buildMenu() {
        buildMenu(null);
    }

    private void buildMenu(String previousSelectedItemId) {
        this.searchDatabase.open();
        Cursor searchCursor = null;
        int radioButtonId = 1;

        try {
            // Get the number of menus this user is allowed to see
            Integer menuCount = searchDatabase.getMenuCount();

            if (pageIndex == 0) {
                // We're on the first page
                if (menuCount <= 0) {
                    // No menu items
                    return;
                }
                else if (menuCount == 1) {
                    searchCursor = searchDatabase.getTopLevelMenuItems(searchDatabase.getFirstMenuId());
                }
                else {
                    searchCursor = searchDatabase.getMenuList();
                }
            }
            else {
                // Get the last selected Item from the breadcrumb
                String selectedItemOrMenuId = getPreviousSelectedItemId();

                // Decide whether selectedItemOrMenuId is a menuId or an itemId
                if (menuCount == 1) {
                    // selectedItemOrMenuId is an ItemId, since we can only see one menu
                    searchCursor = searchDatabase.getChildMenuItems(selectedItemOrMenuId);
                }
                else if (menuCount > 1 && pageIndex == 0) {
                    // selectedItemOrMenuId is a menuId, sine we're on the first page and there was/is more than one
                    // menu (so the first page displays the menus)
                    searchCursor = searchDatabase.getTopLevelMenuItems(selectedItemOrMenuId);

                }
                else if (menuCount > 1 && pageIndex > 0) {
                    // selectedItemOrMenuId is an itemId
                    searchCursor = searchDatabase.getChildMenuItems(selectedItemOrMenuId);
                }
                else {
                    // not sure what's going on
                    return;
                }
            }

            // If the cursor has no content, we might be able
            if (!searchCursor.moveToFirst()) {
                // Close the cursor
                searchCursor.close();

                // Get the item for which we want to show content
                String contentItemId = getPreviousSelectedItemId();

                // Decrement page and breadcrumb (so that back button works well)
                pageIndex--;
                if (breadcrumbItems.size() > 0) {
                    breadcrumbItems.remove(breadcrumbItems.size() - 1);
                }

                // Try to load content
                launchResultsDisplay(contentItemId);
            }
            else {

                Boolean isFirstLoop = true;
                do {
                    String menuOrItemId = searchCursor.getString(0); // Id is the first index
                    String label = searchCursor.getString(1); // label is at the 2nd index
                    String attachmentId = null;

                    // Clear the previous radio button selection
                    if (isFirstLoop) {
                        this.pageRadioGroup.clearCheck();
                        this.pageRadioGroup.removeAllViews();
                        this.pageItemIds.clear();
                        isFirstLoop = false;
                    }

                    if ((menuCount == 1 && pageIndex == 0) || pageIndex > 0) {
                        // We got items, so they may have an attachment
                        attachmentId = searchCursor.getString(2); // attachment id is the 3rd item
                    }

                    radioButtonId = addRadioButton(menuOrItemId, label, radioButtonId, attachmentId, previousSelectedItemId);
                } while (searchCursor.moveToNext());

                searchCursor.close();

                // The last thing we do is set the layouts
                if (pageIndex < 1) {
                    startLayout.setVisibility(View.VISIBLE);
                    layout.setVisibility(View.GONE);
                }
                else {
                    startLayout.setVisibility(View.GONE);
                    layout.setVisibility(View.VISIBLE);
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
     * Gets the itemId that was selected on the previous page
     *
     * @return
     */
    private String getPreviousSelectedItemId() {
        if (breadcrumbItems.size() > 0) {
            return breadcrumbItems.get(breadcrumbItems.size() - 1).id;
        }
        return "";
    }

    /**
     * Adds a radio button to the radio button group
     *
     * @param label
     * @param radioButtonId
     * @param imagePath
     * @param attachmentId
     * @param selectedId
     * @return
     */
    private int addRadioButton(String menuOrItemId, String label, int radioButtonId, String attachmentId, String selectedId) {
        RadioButton radioButton = new RadioButton(this);
        radioButton.setId(radioButtonId);

        String radioButtonText;
        radioButtonText = label;
        radioButton.setText(radioButtonText);
        trySetImage(radioButton, attachmentId + ".jpg");

        if (null != selectedId && selectedId.equals(menuOrItemId)) {
            radioButton.setChecked(true);
            radioButton.setSelected(true);
        }

        radioButton.setTextColor(-16777216);
        radioButton.setTextSize(21);
        radioButton.setPadding(40, 1, 1, 1);
        this.pageRadioGroup.addView(radioButton);

        // Save the current itemId
        pageItemIds.put(radioButtonId, menuOrItemId);
        return ++radioButtonId;
    }

    private void launchResultsDisplay(String menuItemId) {
        String content = searchDatabase.selectContent(menuItemId);
        showSearchResults(GlobalConstants.intervieweeName, GpsManager.getInstance().getLocationAsString(), content);
    }

    private void trySetImage(RadioButton radioButton, String image) {
        if (ImageFilesUtility.imageExists(image)) {
            radioButton.setCompoundDrawablesWithIntrinsicBounds(null, null, null, ImageFilesUtility.getImageAsDrawable(image));
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
        if (this.breadcrumbItems != null) {
            this.breadcrumbItems.clear();
        }
        breadcrumbView.setText("Search: ");
        this.pageIndex = 0;
        this.pageRadioGroup.clearCheck();
        this.pageRadioGroup.removeAllViews();
        buildMenu();
        this.layout.setVisibility(View.GONE);
        this.startLayout.setVisibility(View.VISIBLE);
        showToast(R.string.refreshed, Toast.LENGTH_LONG);
    }

    @Override
    public Object onRetainNonConfigurationInstance() {
        ActivityState instanceState = new ActivityState();
        instanceState.currentPage = pageIndex;
        instanceState.currentBreadcrumbItems = breadcrumbItems;
        return instanceState;
    }

    /**
     * object for holding activity data to store for orientation changes
     */
    private class ActivityState {
        int currentPage;
        ArrayList<BreadcrumbItem> currentBreadcrumbItems;
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

    /**
     *
     */
    public void updateBreadcrumbFromRadioButtonSelection() {
        RadioButton radioButton = (RadioButton)findViewById(selectedRadioButtonId);

        String choice = radioButton.getText().toString();
        String selectedItemOrMenuId = pageItemIds.get(selectedRadioButtonId);

        BreadcrumbItem breadcrumbItem = new BreadcrumbItem();
        breadcrumbItem.label = choice;
        breadcrumbItem.id = selectedItemOrMenuId;

        breadcrumbItems.add(breadcrumbItem);
        breadcrumbView.setText(getBreadcrumb());
    }
}
