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

import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputFilter;
import android.view.Menu;
import android.view.View;
import android.view.Window;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import applab.client.ApplabActivity;
import applab.client.BrowserActivity;
import applab.client.BrowserResultDialog;
import applab.client.controller.FarmerRegistrationController;

/**
 * The Search application home screen
 * 
 */
public class MainMenuActivity extends BaseSearchActivity {
    private static final int REGISTRATION_CODE = 2;
    private static final int FORGOT_ID_CODE = 3;
    private static final int AGINFO_CODE = 1;
    private Button inboxButton;
    private Button nextButton;
    private Button forgotButton;
    private Button registerButton;
    private Button aginfoButton;
    private EditText farmerNameEditBox;

    private FarmerRegistrationController farmerRegController;

    public MainMenuActivity() {
        this.farmerRegController = new FarmerRegistrationController();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Set the app version
        ApplabActivity.setAppVersion(this.getString(R.string.app_version));
        // Request to display an icon in the title bar. Must be done in onCreate()
        requestWindowFeature(Window.FEATURE_RIGHT_ICON);
        // Set application version information
        ApplabActivity.setAppVersion(getString(R.string.app_version));
    }

    @Override
    public void onResume() {
        // First run parent code
        super.onResume();
        setContentView(R.layout.launch_menu);
        setFeatureDrawableResource(Window.FEATURE_RIGHT_ICON, R.drawable.search_title);

        this.nextButton = (Button)findViewById(R.id.next_button);
        this.inboxButton = (Button)findViewById(R.id.inbox_button);
        this.inboxButton.setText(getString(R.string.inbox_button));
        this.farmerNameEditBox = (EditText)findViewById(R.id.id_field);
        this.forgotButton = (Button)findViewById(R.id.forgot_button);
        this.registerButton = (Button)findViewById(R.id.register_button);
        this.aginfoButton = (Button)findViewById(R.id.aginfo_button);
        this.farmerNameEditBox.setFilters(new InputFilter[] { getFarmerInputFilter() });

        if (!StorageManager.hasKeywords()) {
            this.inboxButton.setEnabled(false);
            this.nextButton.setEnabled(false);
        }

        this.forgotButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                onRequestBrowserIntentButtonClick("findFarmerId", FORGOT_ID_CODE);
            }
        });

        this.registerButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                onRequestBrowserIntentButtonClick("getFarmerRegistrationForm", REGISTRATION_CODE);
            }
        });

        this.aginfoButton.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                onRequestBrowserIntentButtonClick("getSubscriptionForm", AGINFO_CODE);
            }
        });

        this.nextButton.setText("Start New Search");
        this.nextButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                onButtonClick(SearchActivity.class);
            }

        });

        this.inboxButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                onButtonClick(InboxListActivity.class);
            }
        });

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {

        switch (requestCode) {
            case REGISTRATION_CODE:
                if (resultCode == RESULT_OK) {

                    String message = "Registration successful.";
                    long result = this.farmerRegController.saveNewFarmerRegistration(data.getBundleExtra(BrowserActivity.EXTRA_DATA_INTENT));
                    if (result < 0)
                        message = "Failed to save farmer registration record.";

                    BrowserResultDialog.show(this, message, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            switchToActivity(SearchActivity.class);
                            dialog.cancel();
                        }
                    });
                }
                else if (resultCode == RESULT_CANCELED) {
                    // reset the Farmer ID
                    GlobalConstants.intervieweeName = "";
                    // Show error dialog
                    BrowserResultDialog.show(this, "Unable to register farmer. \nCheck the ID or try again later.");
                }
                break;
            case AGINFO_CODE:
                if (resultCode == RESULT_OK) {
                    // reset the Farmer ID
                    GlobalConstants.intervieweeName = "";
                    BrowserResultDialog.show(this, "Subscriptions updated successfully");
                }
                else if (resultCode == RESULT_CANCELED) {
                    // reset the Farmer ID
                    GlobalConstants.intervieweeName = "";
                    BrowserResultDialog.show(this, "Subscription was unsuccessful.\nPlease try again later.");
                }
                break;
            case FORGOT_ID_CODE:
                if (resultCode == RESULT_OK) {
                    if (data != null) {
                        final String farmerId = data.getStringExtra("data");
                        BrowserResultDialog.show(this, "Selected ID: " + farmerId, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                GlobalConstants.intervieweeName = farmerId;
                                switchToActivity(SearchActivity.class);
                                dialog.cancel();
                            }
                        });
                    }
                }
                else if (resultCode == RESULT_CANCELED) {
                    // reset the Farmer ID
                    GlobalConstants.intervieweeName = "";
                    BrowserResultDialog.show(this, "Unable to find ID. Try again later.");
                }
                break;
            default:
                break;
        }
    }

    /**
     * Common code for handling button clicks that start a browser activity for a result.
     * 
     * @param urlPattern
     *            The @BrowserActivity.EXTRA_URL_INTENT related url pattern
     * @param requestCode
     *            Code identifying the button that invoked the browser call. This so that the result is handled
     *            accordingly in the parent activity.
     */
    private void onRequestBrowserIntentButtonClick(String urlPattern, int requestCode) {
        String farmerName = farmerNameEditBox.getText().toString().replace(" ", "");
        if (farmerName.length() > 0 || urlPattern.contentEquals("findFarmerId")) {
            if (urlPattern.contentEquals("findFarmerId") || checkId(farmerName)) {
                // Set the farmer ID
                GlobalConstants.intervieweeName = farmerName;
                Intent webActivity = new Intent(getApplicationContext(), BrowserActivity.class);

                String html = this.farmerRegController.getFormHtml(farmerName, Settings.getServerUrl());
                if (html != null) {
                    webActivity.putExtra(BrowserActivity.EXTRA_HTML_INTENT, html);
                    startActivityForResult(webActivity, requestCode);
                }
                else {
                    showToast("Failed to get the farmer registration form. Please try again.");
                }
            }
            else {
                showToast("Invalid Farmer ID.");
            }
        }
        else {
            showToast(R.string.empty_text);
        }
    }

    /**
     * Common code for handling button clicks for "New Search" and "Recent Searches"
     * 
     * @param classId
     */
    private void onButtonClick(Class<?> classId) {
        final Intent nextActivity = new Intent(getApplicationContext(), classId);
        nextActivity.putExtra("block", false);
        String farmerName = farmerNameEditBox.getText().toString().replace(" ", "");
        if (farmerName.length() > 0) {
            if (checkId(farmerName)) {
                GlobalConstants.intervieweeName = farmerName;
                switchToActivity(nextActivity);
            }
            else {
                showTestSearchDialog(new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        GlobalConstants.intervieweeName = "TEST";
                        switchToActivity(nextActivity);
                        dialog.cancel();
                    }
                }, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.cancel();
                    }
                });
            }
        }
        else {
            showToast(R.string.empty_text);
        }
    }

    @Override
    protected boolean showFarmerId() {
        return false;
    }

    @Override
    protected void onKeywordUpdateComplete() {
        super.onKeywordUpdateComplete();
        if (StorageManager.hasKeywords()) {
            this.inboxButton.setEnabled(true);
            this.nextButton.setEnabled(true);
        }
    }

    // Remove unnecessary menu items for this activity
    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        boolean result = super.onPrepareOptionsMenu(menu);
        menu.removeItem(GlobalConstants.HOME_ID);
        menu.removeItem(GlobalConstants.RESET_ID);
        menu.removeItem(GlobalConstants.DELETE_ID);
        menu.removeItem(GlobalConstants.INBOX_ID);
        return result;
    }
}