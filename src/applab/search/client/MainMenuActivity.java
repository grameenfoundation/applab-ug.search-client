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

import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.InputFilter;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import applab.client.ApplabActivity;
import applab.client.BrowserActivity;
import applab.client.BrowserResultDialog;
import applab.client.HttpHelpers;
import applab.client.autoupdate.ApplicationUpdateManager;
import applab.client.dataconnection.DataConnectionManager;
import applab.client.farmerregistration.FarmerRegistrationAdapter;
import applab.client.farmerregistration.FarmerRegistrationController;
import applab.client.location.GpsManager;
import applab.client.search.R;

/**
 * The Search application home screen
 * 
 */
public class MainMenuActivity extends BaseSearchActivity implements Runnable {

    private static final int REGISTRATION_CODE = 2;
    private static final int UPDATE_CODE = 4;
    private static final int FORGOT_ID_CODE = 3;
    private static final int AGINFO_CODE = 1;
    private static final int PROGRESS_DIALOG = 1;

    private Button inboxButton;
    private Button nextButton;
    private Button forgotButton;
    private Button registerButton;
    private Button updateFarmerButton;
    private Button aginfoButton;
    private EditText farmerNameEditBox;
    private FarmerRegistrationController farmerRegController;
    private int requestCode;
    private ProgressDialog progressDialog;
    private String errorMessage;

    /**
     * Used to participate in the synchronization lifecycle events
     */
    private Handler keywordSynchronizationCallback = new Handler() {
        @Override
        public void handleMessage(Message message) {
            switch (message.what) {
                case GlobalConstants.KEYWORD_PARSE_SUCCESS:
                    onKeywordUpdateComplete();
                    break;
            }
        }
    };

    public MainMenuActivity() {
        this.farmerRegController = new FarmerRegistrationController();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ApplabActivity.setAppVersion(getString(R.string.app_name),
                getString(R.string.app_version));

        // Request to display an icon in the title bar. Must be done in
        // onCreate()
        requestWindowFeature(Window.FEATURE_RIGHT_ICON);
    }

    @Override
    public void onResume() {
        // First run parent code
        super.onResume();

        // Check if we're in the middle of synchronizing keywords, and if we
        // should be
        // displaying a progress meter or not (i.e. was this a background
        // synchronization?)
        // TODO: how do we detect when we had a progress dialog up before and so
        // should still be modal?
        SynchronizationManager.onActivityResume(this,
                keywordSynchronizationCallback);

        setContentView(R.layout.launch_menu);
        setFeatureDrawableResource(Window.FEATURE_RIGHT_ICON,
                R.drawable.search_title);

        Intent intentReceiver = getIntent();
        String farmerIdFound = intentReceiver.getStringExtra("edit_text");

        this.nextButton = (Button)findViewById(R.id.next_button);
        this.inboxButton = (Button)findViewById(R.id.inbox_button);
        this.inboxButton.setText(getString(R.string.inbox_button));
        this.farmerNameEditBox = (EditText)findViewById(R.id.id_field);
        this.forgotButton = (Button)findViewById(R.id.forgot_button);
        this.registerButton = (Button)findViewById(R.id.register_button);
        this.updateFarmerButton = (Button)findViewById(R.id.update_farmer_button);
        this.aginfoButton = (Button)findViewById(R.id.aginfo_button);
        this.farmerNameEditBox
                .setFilters(new InputFilter[] { getFarmerInputFilter() });

        // set text for main menu buttons basing on Locale
        this.nextButton.setText(R.string.next_button);
        this.inboxButton.setText(R.string.inbox_button);
        this.forgotButton.setText(R.string.forgot_button);
        this.registerButton.setText(R.string.register_new_farmer);
        this.updateFarmerButton.setText(R.string.update_farmer_registration);
        this.aginfoButton.setText(R.string.ag_info_button);

        // toggle showing of register farmer button
        String showFarmerRegistrationButton = getResources().getString(R.string.show_farmer_registration);
        this.registerButton.setVisibility(showFarmerRegistrationButton.equalsIgnoreCase("yes") ? View.VISIBLE : View.GONE);

        // toggle showing of forgot farmer button
        String showForgotFarmerButton = getResources().getString(R.string.show_forgot_farmer);
        this.forgotButton.setVisibility(showForgotFarmerButton.equalsIgnoreCase("yes") ? View.VISIBLE : View.GONE);

        // toggle showing of update farmer button
        String showUpdateFarmerButton = getResources().getString(R.string.show_update_farmer);
        this.updateFarmerButton.setVisibility(showUpdateFarmerButton.equalsIgnoreCase("yes") ? View.VISIBLE : View.GONE);

        if (farmerIdFound != null) {
            this.farmerNameEditBox.setText(farmerIdFound);
        }

        if (!StorageManager.hasKeywords()) {
            this.inboxButton.setEnabled(false);
            this.nextButton.setEnabled(false);
        }

        this.forgotButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                // Start FindFarmerId Activity
                Intent nextActivity = new Intent(getApplicationContext(),
                        FindFarmerIdActivity.class);
                startActivity(nextActivity);
            }
        });

        this.registerButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                onRequestBrowserIntentButtonClick("getFarmerRegistrationForm",
                        REGISTRATION_CODE);
            }
        });

        this.updateFarmerButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                onRequestBrowserIntentButtonClick("getFarmerRegistrationForm",
                        UPDATE_CODE);
            }
        });

        this.aginfoButton.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                onRequestBrowserIntentButtonClick("getSubscriptionForm",
                        AGINFO_CODE);
            }
        });

        this.nextButton.setText(R.string.start_new_search);
        this.nextButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                GpsManager.getInstance().update();
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
    protected void refreshKeywords() {
        super.refreshKeywords();
        SynchronizationManager
                .synchronize(this, keywordSynchronizationCallback);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {

        switch (requestCode) {
            case REGISTRATION_CODE:
                if (resultCode == RESULT_OK) {

                    Bundle bundle = data
                            .getBundleExtra(BrowserActivity.EXTRA_DATA_INTENT);
                    bundle.putString(FarmerRegistrationAdapter.KEY_LOCATION,
                            GpsManager.getInstance().getLocationAsString());

                    String message = getResources().getString(R.string.registration_successful);
                    long result = this.farmerRegController
                            .saveNewFarmerRegistration(bundle);
                    if (result < 0) {
                        message = getResources().getString(R.string.registration_failed);
                    }
                    else { // Farmer registration successful, mark assigned farmer
                           // id as used
                        this.searchDatabase.open();
                        searchDatabase.toggleFarmerIdStatus(
                                GlobalConstants.intervieweeName,
                                GlobalConstants.AVAILABLE_FARMER_ID_USED_STATUS);
                        this.searchDatabase.close();
                    }

                    BrowserResultDialog.show(this, message,
                            new DialogInterface.OnClickListener() {
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
                    BrowserResultDialog
                            .show(this, getResources().getString(R.string.registration_unable));
                }

                break;
            case AGINFO_CODE:
                if (resultCode == RESULT_OK) {
                    // reset the Farmer ID
                    GlobalConstants.intervieweeName = "";
                    BrowserResultDialog.show(this, getResources().getString(R.string.subscriptions_succesful));
                }
                else if (resultCode == RESULT_CANCELED) {
                    // reset the Farmer ID
                    GlobalConstants.intervieweeName = "";
                    BrowserResultDialog
                            .show(this, getResources().getString(R.string.subscriptions_failed));
                }
                break;
            case FORGOT_ID_CODE:
                if (resultCode == RESULT_OK) {
                    if (data != null) {
                        final String farmerId = data
                                .getStringExtra(BrowserActivity.EXTRA_DATA_INTENT);
                        BrowserResultDialog.show(this, "Selected ID: " + farmerId,
                                new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog,
                                                        int id) {
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
                    BrowserResultDialog.show(this, getResources().getString(R.string.ID_not_found));
                }
                break;
            case ApplicationUpdateManager.INSTALL_APPLICATION:
                ApplicationUpdateManager.setFinishedInstall(true);
                break;
            default:
                break;
        }

        // restore the farmer id as previously typed in by the user.
        farmerNameEditBox.setText(GlobalConstants.intervieweeName);
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
    private void onRequestBrowserIntentButtonClick(String urlPattern,
                                                   int requestCode) {
        String farmerName = farmerNameEditBox.getText().toString()
                .replace(" ", "");
        // Check local db for available IDs if no farmerId is provided in the
        // app or an invalid id is provided
        if (urlPattern.contentEquals("getFarmerRegistrationForm")
                && (farmerName.length() == 0 || !checkId(farmerName))) {
            farmerName = getNextAvailableId();
        }
        if (null == farmerName) {
            showToast(getResources().getString(R.string.update_keywords));
        }
        else {
            if (farmerName.length() > 0) {
                if (checkId(farmerName)) {
                    // Set the farmer ID
                    GlobalConstants.intervieweeName = farmerName;

                    // Start GPS search for: Farmer Registration, Ag Info
                    // Subscription, Forgot Farmer ID Search
                    GpsManager.getInstance().update();

                    if (requestCode == REGISTRATION_CODE || requestCode == UPDATE_CODE) {
                        showDialog(PROGRESS_DIALOG);
                        this.requestCode = requestCode;
                        new Thread(this).start();
                    }
                    else {
                        Intent webActivity = new Intent(getApplicationContext(),
                                BrowserActivity.class);

                        String serverUrl = Settings.getServerUrl();
                        serverUrl = serverUrl.substring(0, serverUrl.length() - 1);

                        webActivity.putExtra(BrowserActivity.EXTRA_URL_INTENT,
                                serverUrl + ":8888/services/" + urlPattern
                                        + HttpHelpers.getCommonParameters()
                                        + "&farmerId=" + farmerName);
                        startActivityForResult(webActivity, requestCode);
                    }
                }
                else {
                    showToast(getResources().getString(R.string.invalid_Farmer_ID));
                }
            }
            else {
                showToast(R.string.empty_text);
            }
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
        String farmerName = farmerNameEditBox.getText().toString()
                .replace(" ", "");
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

    public void run() {

        if (this.requestCode == REGISTRATION_CODE || this.requestCode == UPDATE_CODE) {

            errorMessage = null;
            String html = null;

            if (this.requestCode == REGISTRATION_CODE) {
                html = this.farmerRegController.getFormHtml(
                        GlobalConstants.intervieweeName, Settings.getServerUrl());
            }
            else {
                String[] farmerDetails = this.getFarmerDetails(GlobalConstants.intervieweeName);

                if (farmerDetails != null) {
                    html = this.farmerRegController.getFormHtml(GlobalConstants.intervieweeName, farmerDetails[0], farmerDetails[1],
                            farmerDetails[2], Settings.getServerUrl());
                }
            }

            if (html != null) {
                Intent webActivity = new Intent(getApplicationContext(),
                        BrowserActivity.class);
                webActivity.putExtra(BrowserActivity.EXTRA_HTML_INTENT, html);
                startActivityForResult(webActivity, this.requestCode);
            }
            else {
                errorMessage = getResources().getString(R.string.form_registration_failed);
            }

            // Dismiss the progress window.
            handler.sendEmptyMessage(0);
        }
    }

    private Handler handler = new Handler() {

        @Override
        public void handleMessage(Message msg) {
            dismissDialog(PROGRESS_DIALOG);

            if (errorMessage != null) {
                showToast(errorMessage);
            }
        }
    };

    @Override
    protected Dialog onCreateDialog(int id) {
        progressDialog = new ProgressDialog(this);
        progressDialog.setTitle(getTitle());
        progressDialog.setMessage(getResources().getString(R.string.form_loading));
        progressDialog.setIndeterminate(true);
        progressDialog.setCancelable(false);

        return progressDialog;
    }

    @Override
    protected void onStart() {
        super.onStart();

        // We need to display only one settings screen at a time.
        // So if no settings screen shown for GPS, try show that of mobile data,
        // if disabled.
        // Every time a settings screen is closed, Activity:onStart() will be
        // called and hence
        // help us ensure that we display all the settings screen we need, but
        // one a time.
        if (!GpsManager.getInstance().onStart(this)) {
            DataConnectionManager.getInstance().onStart(this);
        }
    }

}