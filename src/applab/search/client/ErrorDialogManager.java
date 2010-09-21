package applab.search.client;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import applab.client.ApplabActivity;

// TODO: consolidate this and ProgressDialogManager into a single "SearchDialogManager" class? Move 
// into common code?
public class ErrorDialogManager {
    /**
     * Show an error dialog with OK and Retry as the options. If a listener is not provide, the default one will simply
     * dismiss the dialog.
     */
    public static void show(int errorMessage, Context context, DialogInterface.OnClickListener okListener,
                            DialogInterface.OnClickListener retryListener) {
        show(errorMessage, context, okListener, retryListener, "Retry");
    }

    /**
     * Show an error dialog with OK and negativeLabel as the options. If a listener is not provide, the default one will
     * simply dismiss the dialog.
     */
    public static void show(int errorMessage, Context context, DialogInterface.OnClickListener okListener,
                            DialogInterface.OnClickListener noListener, String negativeLabel) {
        show(errorMessage, context, okListener, "OK", noListener, negativeLabel);
    }

    /**
     * Show an error dialog with okLabel and negativeLabel as the options. If a listener is not provided, the default
     * one will simply dismiss the dialog.
     */
    public static void show(int errorMessage, Context context,
                            DialogInterface.OnClickListener okListener, String okLabel,
                            DialogInterface.OnClickListener noListener, String negativeLabel) {
        if (context == null) {
            context = ApplabActivity.getCurrent();
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setMessage(errorMessage);
        builder.setCancelable(false);

        // if not explicitly set, default the click listeners to simple ones that will just dismiss the alert
        if (okListener == null) {
            okListener = new DialogInterface.OnClickListener() {
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
        builder.setPositiveButton(okLabel, okListener).setNegativeButton(negativeLabel, noListener);
        AlertDialog alert = builder.create();
        alert.show();
    }
}
