package applab.search.client;

import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.InputFilter;
import android.text.Spanned;
import android.view.Menu;
import android.view.MenuItem;
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
                case Global.KEYWORD_PARSE_SUCCESS:
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
    
    /**
     * Return an InputFilter that can validate farmer id characters entered in the UI
     */
    public static InputFilter getFarmerInputFilter() {
        return new InputFilter() {
            public CharSequence filter(CharSequence source, int start, int end,
                                       Spanned destination, int destinationStart, int destinationEnd) {
                for (int characterIndex = start; characterIndex < end; characterIndex++) {
                    char currentCharacter = source.charAt(characterIndex);
                    if (Character.isLetterOrDigit(currentCharacter)) {
                        continue;
                    }

                    if (Character.isWhitespace(currentCharacter)) {
                        continue;
                    }

                    showToast(R.string.invalid_text);
                    return "";
                }
                return null;
            }
        };        
    }
    
    private void setActivityTitle() {
        String title = getTitleName();
        
        // TODO: move this out of a global and into an Intent extra
        int maxFarmerNameLength = 30;
        String farmerName = Global.intervieweeName; 
        
        if (farmerName != null && farmerName.length() > 0) {
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

    // helper methods for Android menu management
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        boolean result = super.onCreateOptionsMenu(menu);
        // groupId, itemId, order, title
        menu.add(1, Global.REFRESH_ID, 0, R.string.menu_refresh).setIcon(R.drawable.refresh);
        menu.add(0, Global.INBOX_ID, 0, R.string.menu_inbox).setIcon(R.drawable.folder);
        menu.add(0, Global.ABOUT_ID, 0, R.string.menu_about).setIcon(R.drawable.about);
        menu.add(0, Global.EXIT_ID, 0, R.string.menu_exit).setIcon(R.drawable.exit);
        menu.add(0, Global.SETTINGS_ID, 0, R.string.menu_settings).setIcon(R.drawable.settings);
        menu.add(0, Global.HOME_ID, 0, R.string.menu_home).setIcon(R.drawable.home);
        menu.add(1, Global.RESET_ID, 0, R.string.menu_reset);

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
            case Global.REFRESH_ID:
                if (confirmRefresh()) {
                    DialogInterface.OnClickListener onClickOk = new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            dialog.cancel();
                            refreshKeywords();
                        }
                    };

                    ErrorDialogManager.show(R.string.refresh_confirm, this, onClickOk, null, "No");
                }
                else {
                    refreshKeywords();
                }
                return true;
            case Global.ABOUT_ID:
                startActivity(AboutActivity.class);
                return true;
            case Global.EXIT_ID:
                finish();
                return true;
            case Global.SETTINGS_ID:
                startActivity(Settings.class);
                return true;
            case Global.INBOX_ID:
                switchToActivity(InboxListActivity.class);
                finish();
                return true;
            case Global.HOME_ID:
                switchToActivity(MainMenuActivity.class);
                return true;
            case Global.RESET_ID:
                // TODO: should we check if the system is in the middle of synchronizing?
                switchToActivity(SearchActivity.class);
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

        // Disable new search option if no interviewee name has been supplied
        if (Global.intervieweeName == null) {
            menu.findItem(Global.RESET_ID).setEnabled(false);
        }

        return result;
    }
}
