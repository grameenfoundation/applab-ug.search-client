package applab.search.client;

import java.util.TimerTask;

import android.os.Handler;
import android.os.Message;
import android.util.Log;
import applab.client.ApplabActivity;
import applab.client.search.R;

public class TimedSynchronizationTask extends TimerTask {
    private static final String LOG_TAG = "TimedSynchronizationTask";
    /**
     * Used to participate in the synchronization lifecycle events
     */
    private Handler keywordSynchronizationCallback = new Handler() {
        @Override
        public void handleMessage(Message message) {
            SynchronizationManager.singleton.handleBackgroundThreadMessage(message);
        }
    };

    @Override
    public void run() {
        Log.d(LOG_TAG, "Timer: launching background sync");

        try {
            SynchronizationManager.synchronizeFromTimer(ApplabActivity.getGlobalContext(), keywordSynchronizationCallback);
        }
        catch (Exception ex) {
            Log.e(LOG_TAG, "Unexpected failure during background syncronization", ex);
            //ProgressDialogManager.tryDestroyProgressDialog();
        }
    }
}