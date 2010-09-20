package applab.search.client;

import android.app.ProgressDialog;
import android.content.Context;
import android.util.Log;
import applab.client.ApplabActivity;

/**
 * Helper class that handles the ownership and management of the progress
 * dialog that we use when halting user interaction
 * 
 * TODO: consolidate with Pulse progress dialog into CommonClient?
 */
public class ProgressDialogManager {
    private final static String DEBUG_TAG = "ProgressDialogManager";
    
    // Progress Dialog
    private static ProgressDialog progressDialog;
    
    // Progress Dialog Status
    private static int currentType = Global.UPDATE_DIALOG;
    
    // Silent mode (used for background sync)
    // TODO: we need a more robust mechanism here so that we can share this across synchronization
    // and user-driven search queries
    public static boolean silentMode;
    
    // Progress Dialog Max value
    private static int currentMaxVal = 100;
    
    public static void tryDisplayProgressDialog(Context context) {
        if(SynchronizationManager.isSynchronizing() && !ProgressDialogManager.isVisible()) {
            displayProgressDialog(currentType, context);
        }
    }
    
    public static void displayProgressDialog(int type) {
        displayProgressDialog(type, ApplabActivity.getGlobalContext(), currentMaxVal);
    }
    
    public static void displayProgressDialog(int type, Context context) {
        displayProgressDialog(type, context, currentMaxVal);
    }
    
    public static void displayProgressDialog(int type, Context context, int max) {        
        if(!silentMode) {
            Log.i(DEBUG_TAG,"Trying to destroy previous dialogs ...");
            ProgressDialogManager.tryDestroyProgressDialog();
            ProgressDialog dialog = ProgressDialogManager.getLastOrNewDialog(context);
            ProgressDialogManager.currentType = type;
            ProgressDialogManager.currentMaxVal = max;
            
            dialog.setMax(currentMaxVal);
            
            switch (ProgressDialogManager.currentType) {
                case Global.UPDATE_DIALOG:
                    dialog.setTitle(context.getString(R.string.update_dialog_title));
                    // TODO: MainMenuActivity used to use R.string.progress_msg here
                    dialog.setMessage(context.getString(R.string.update_dialog_message));
                    dialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
                    dialog.setIndeterminate(true);
                    dialog.setCancelable(false);
                    break;
                case Global.PARSE_DIALOG:
                    dialog.setTitle(context.getString(R.string.parse_dialog_title));
                    dialog.setMessage(context.getString(R.string.parse_msg));
                    dialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
                    dialog.setIndeterminate(false);
                    dialog.setCancelable(false);
                    break;
                case Global.SETUP_DIALOG:
                    dialog.setTitle(context.getString(R.string.progress_header));
                    dialog.setMessage(context.getString(R.string.progress_initial));
                    dialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
                    dialog.setIndeterminate(true);
                    dialog.setCancelable(false);
                    break;
            }
            Log.i(DEBUG_TAG,"Showing new dialog ...");
            dialog.show();
        } else {
            Log.i(DEBUG_TAG, "Not showing dialog because we're in silent mode.");
        }
    }
    
    private static ProgressDialog getLastOrNewDialog(Context context) {
        if(ProgressDialogManager.progressDialog == null)
        {
            ProgressDialogManager.progressDialog = new ProgressDialog(context);
        }
        return ProgressDialogManager.progressDialog;
    }

    public static void tryDestroyProgressDialog()
    {
        if(ProgressDialogManager.progressDialog != null)
        {
            ProgressDialogManager.progressDialog.cancel();
            ProgressDialogManager.progressDialog = null;
        }
    }

    public static void setProgress(int level) {
        if(ProgressDialogManager.progressDialog != null)
        {
            ProgressDialogManager.progressDialog.setProgress(level);
        }
    }
    
    public static boolean isVisible() {
        return (ProgressDialogManager.progressDialog instanceof ProgressDialog) && ProgressDialogManager.progressDialog.isShowing();
    }
    
    public static void setMax(int max) {
        if(ProgressDialogManager.progressDialog != null) {
            ProgressDialogManager.progressDialog.setMax(max);
        }
    }
}
