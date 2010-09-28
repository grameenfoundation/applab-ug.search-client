package applab.search.client;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import applab.client.ApplabActivity;

// TODO: consolidate this and ProgressDialogManager into a single "DialogManager" class? Move 
// into common code?
public class ErrorDialogManager {
    /**
     * Show an error dialog with Yes and No as the options. If a listener is not provided, the default one will simply
     * dismiss the dialog.
     */
    public static void show(int errorMessage, Context context, DialogInterface.OnClickListener yesListener,
                            DialogInterface.OnClickListener noListener) {
        show(errorMessage, context, yesListener, "Yes", noListener, "No");
    }

    /**
     * Show an error dialog with yesLabel and noLabel as the options. If a listener is not provided, the default one
     * will simply dismiss the dialog.
     */
    public static void show(int errorMessage, Context context,
                            DialogInterface.OnClickListener yesListener, String yesLabel,
                            DialogInterface.OnClickListener noListener, String noLabel) {
        // if a context is not provided, get the currently executing activity. We can't use
        // ApplabActivity.getGlobalContext() because of a bug in Android 1.5 where the system
        // will crash if you try and display a window parented by the application context
        if (context == null) {
            context = ApplabActivity.getCurrent();
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setMessage(errorMessage);
        builder.setCancelable(false);

        // if not explicitly set, default the click listeners to simple ones that will just dismiss the alert
        if (yesListener == null) {
            yesListener = new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                    dialog.cancel();
                }
            };
        }
        if (noListener == null) {
            noListener = new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                    dialog.cancel();
                }
            };
        }
        builder.setPositiveButton(yesLabel, yesListener).setNegativeButton(noLabel, noListener);
        AlertDialog alert = builder.create();
        alert.show();
    }
}
