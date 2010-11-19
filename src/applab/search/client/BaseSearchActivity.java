package applab.search.client;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.Menu;
import android.view.MenuItem;
import applab.client.AboutDialog;
import applab.client.ApplabActivity;

/**
 * Base class for all Search activities, consolidates code around synchronization, menus, progress dialogs, etc.
 * 
 */
public abstract class BaseSearchActivity extends ApplabActivity {
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

    protected BaseSearchActivity() {
        super();
    }

    /**
     * called when we are notified that keywords have been updated
     * 
     * Used by MainMenuActivity to enable its buttons
     */
    protected void onKeywordUpdateComplete() {
    }

    /**
     * Take care of some clean up when ending any activity
     */
    @Override
    protected void onPause() {

        // Check if we're in the middle of a keyword download and we need to destroy the dialogs that are displaying
        // (otherwise they will leak)
        ProgressDialogManager.tryDestroyProgressDialog();

        // Call parent
        super.onPause();
    }

    /**
     * Take care of some setup when resuming any activity. We use onResume() rather than onCreate() because from
     * Activity Life Cycle, onResume seems to be called just before the app loading completes, while onCreate may be
     * by-passed in some scenarios.
     */
    @Override
    protected void onResume() {
        // Check if we're in the middle of synchronizing keywords, and if we should be
        // displaying a progress meter or not (i.e. was this a background synchronization?)
        // TODO: how do we detect when we had a progress dialog up before and so should still be modal?
        SynchronizationManager.onActivityResume(this, keywordSynchronizationCallback);

        // TODO: Check if we need to reload activity content (e.g if it was saved on screen orientation change)

        // Other setup
        this.setActivityTitle();

        // Call parent
        super.onResume();
    }

    private void setActivityTitle() {
        String title = getTitleName();

        // TODO: move this out of a global and into an Intent extra
        int maxFarmerNameLength = 30;
        String farmerName = GlobalConstants.intervieweeName;

        if (farmerName != null && farmerName.length() > 0 && showFarmerId()) {
            title += " | ";
            if (farmerName.length() > maxFarmerNameLength) {
                farmerName = farmerName.substring(0, maxFarmerNameLength) + "...";
            }
            title += farmerName;
        }

        this.setTitle(title);
    }

    /**
     * return the base name to use in the title. Default is "CKW Search"
     * 
     * @return
     */
    protected String getTitleName() {
        return getString(R.string.app_name);
    }

    // TODO: do we want this? It was in SearchActivity.java...
    @Override
    protected void onDestroy() {
        super.onDestroy();
        // release synchronization lock
        SynchronizationManager.completeSynchronization();
    }

    /**
     * Let activity know if a configuration change has occurred Configuration change usually leads to reloading the
     * activity, which can cause some code to re-run, unless we check for this first
     */
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        // Set a variable on the bundle to let us know that state has changed
        outState.putBoolean("configurationChanged", true);

        // Call parent
        super.onSaveInstanceState(outState);
    }

    /**
     * override if you want to display a prompt to confirm refreshing
     */
    protected boolean confirmRefresh() {
        return false;
    }

    /**
     * override if you do not want to show the farmer ID in the activity title bar
     * @return
     */
        protected boolean showFarmerId() {
            return true;
       }
    // helper methods for Android menu management
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        boolean result = super.onCreateOptionsMenu(menu);
        // groupId, itemId, order, title
        menu.add(1, GlobalConstants.REFRESH_ID, 0, R.string.menu_refresh).setIcon(R.drawable.refresh);
        menu.add(0, GlobalConstants.INBOX_ID, 0, R.string.menu_inbox).setIcon(R.drawable.folder);
        menu.add(0, GlobalConstants.ABOUT_ID, 2, R.string.menu_about).setIcon(R.drawable.about);
        menu.add(0, GlobalConstants.EXIT_ID, 3, R.string.menu_exit).setIcon(R.drawable.exit);
        menu.add(0, GlobalConstants.SETTINGS_ID, 1, R.string.menu_settings).setIcon(R.drawable.settings);
        menu.add(0, GlobalConstants.HOME_ID, 0, R.string.menu_home).setIcon(R.drawable.home);
        menu.add(1, GlobalConstants.RESET_ID, 0, R.string.menu_reset).setIcon(R.drawable.search);
        menu.add(0, GlobalConstants.DELETE_ID, 0, R.string.menu_delete).setIcon(R.drawable.delete);

        return result;
    }

    private void refreshKeywords() {
        SynchronizationManager.synchronize(this, keywordSynchronizationCallback);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        super.onOptionsItemSelected(item);

        // for a number of our menu items, the correct action is
        switch (item.getItemId()) {
            case GlobalConstants.REFRESH_ID:
                if (confirmRefresh()) {
                    DialogInterface.OnClickListener onClickYes = new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            dialog.cancel();
                            refreshKeywords();
                        }
                    };

                    ErrorDialogManager.show(R.string.refresh_confirm, this, onClickYes, null);
                }
                else {
                    refreshKeywords();
                }
                return true;
            case GlobalConstants.ABOUT_ID:
                AboutDialog.show(this, getString(R.string.app_version), getString(R.string.app_name),
                        getString(R.string.release_date), getString(R.string.info), R.drawable.icon);

                return true;
            case GlobalConstants.EXIT_ID:
                ApplabActivity.exit();
                return true;
            case GlobalConstants.SETTINGS_ID:
                // At the moment switchToActivity will make it impossible to leave the settings activity
                startActivity(Settings.class);
                return true;
            case GlobalConstants.INBOX_ID:
                switchToActivity(InboxListActivity.class);
                finish();
                return true;
            case GlobalConstants.HOME_ID:
                switchToActivity(MainMenuActivity.class);
                return true;
            case GlobalConstants.RESET_ID:
                // TODO: should we check if the system is in the middle of synchronizing?
                switchToActivity(SearchActivity.class);
                return true;
            case GlobalConstants.DELETE_ID:
                DialogInterface.OnClickListener okListener = new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        InboxAdapter inbox = new InboxAdapter(getApplicationContext());
                        inbox.open();
                        inbox.deleteAllRecords(InboxAdapter.INBOX_DATABASE_TABLE);
                        inbox.close();
                        dialog.cancel();

                        // replace ourself with a new instance that doesn't block
                        Intent inboxList = new Intent(getApplicationContext(), InboxListActivity.class);
                        inboxList.putExtra("block", false);
                        switchToActivity(inboxList);
                        finish();
                    }
                };

                ErrorDialogManager.show(R.string.delete_alert, this, okListener, null);
                return true;
        }
        return false;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        boolean result = super.onPrepareOptionsMenu(menu);

        // Do any "on-the-fly" changes to the menu;

        // TODO: do we want to disable any functionality while we're synchronizing?
        // how about other removals/disables based on current display activity?
        /*
         * if (SynchronizationManager.isSynchronizing()) { // Disable keyword updates and new searches
         * menu.setGroupEnabled(1, false); } else { menu.setGroupEnabled(1, true); }
         */

        return result;
    }

    /**
     * Regular expression check to match 2 letters followed by at least 4 but at most 5 digits for a Farmer ID.
     * 
     * @param text
     *            the matcher
     * @return true if the matcher is an exact match of the input text
     */
    boolean checkId(String text) {
        Pattern pattern = Pattern.compile("[a-zA-Z]{2}[0-9]{4,5}+");
        Matcher matcher = pattern.matcher(text);
        return matcher.matches();
    }

    /**
     * Dialog confirming a test search
     * 
     * @param yesListener
     *            the listener to call on clicking the positive button
     * @param noListener
     *            the listener to call on clicking the negative button
     */
    void showTestSearchDialog(DialogInterface.OnClickListener yesListener, DialogInterface.OnClickListener noListener) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setIcon(R.drawable.help);
        builder.setTitle("Perform Test Search?");
        builder.setMessage("The ID you entered is not valid, "
                + "it should be 2 letters followed by at least 4 numbers."
                + "\nWould you like to do a test search instead?"
                + " NOTE: You will NOT be compensated for doing a test search.")
                .setCancelable(false)
                .setPositiveButton("Yes", yesListener)
                .setNegativeButton("No", noListener);
        AlertDialog alert = builder.create();
        alert.show();
    }
}
