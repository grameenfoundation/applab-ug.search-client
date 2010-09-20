package applab.search.client;

import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import android.content.Context;
import android.content.DialogInterface;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.widget.Toast;
import applab.client.ApplabActivity;
import applab.client.HttpHelpers;

/**
 * The SynchronizationManager is responsible for all the complexity involved in scheduling timers, background threads,
 * coordinating UI, etc.
 * 
 * SynchronizationManager handles three main tasks: 1) Ensuring keywords and their associated content is up to date 2)
 * Sending any searches that failed to send in the mainline path 3) Transmitting logs of any searches that have happened
 * on our cached data so that we can track this activity for M&E purposes on our servers
 * 
 * TODO: move the general scheduling algorithm into shared code and leverage it
 */
public class SynchronizationManager {
    // by default we will synchronize once an hour
    private static final int SYNCHRONIZATION_INTERVAL = 60 * 60 * 1000;

    private static SynchronizationManager singleton = new SynchronizationManager();

    private Timer timer;
    private boolean isSynchronizing;
    private Handler completionCallback;
    private Context currentContext;
    private Handler progressMessageHandler;
    private Thread backgroundThread;

    // used for getting messages from the download and parsing threads
    private Handler internalMessageHandler;

    private SynchronizationManager() {
    }

    /**
     * performs an unsynchronized check that can be used for dirty read purposes such as UI enablement checks, etc.
     */
    public static boolean isSynchronizing() {
        return SynchronizationManager.singleton.isSynchronizing;
    }

    // TOOD: hook this up to isSynchronizing
    private boolean isRunning() {
        return this.backgroundThread != null && this.backgroundThread.isAlive();
    }

    /**
     * Kick off an immediate synchronization, usually from the refresh menu
     */
    public static void synchronize(Context context, Handler completionCallback) {
        synchronize(context, completionCallback, true);
    }

    /**
     * Called when resuming an activity. Will make sure that we have our timer running and that if we're in the middle
     * of a synchronization episode that we attach to any outstanding UI.
     */
    public static void onActivityResume(Context context, Handler completionCallback) {
        synchronize(context, completionCallback, false);
    }

    /**
     * Check if it's okay to proceed with a synchronization episode. isModal determines if this is a foreground or
     * background synchronization.
     */
    private static void synchronize(Context context, Handler completionCallback, boolean isModal) {
        boolean attachToUi = false;
        boolean synchronizeNow = isModal;

        synchronized (SynchronizationManager.singleton) {
            // if we don't have a timer, set that up
            SynchronizationManager.singleton.ensureTimerIsScheduled();

            // Now check if we are in the middle of a synchronization episode
            // and if so, we should attach to the UI
            // TODO: check isRunning instead?
            if (SynchronizationManager.singleton.isSynchronizing) {
                attachToUi = true;
            }
            else {
                // check if we have any keywords cached locally. If not, we have to become
                // modal and initialize our local store.
                if (!StorageManager.hasKeywords()) {
                    synchronizeNow = true;
                }

                // only set our boolean under the lock. The actual synchronization work
                // will occur outside of the lock
                if (synchronizeNow) {
                    SynchronizationManager.singleton.isSynchronizing = true;
                }
            }
        }

        if (attachToUi) {
            SynchronizationManager.singleton.attachActivity(context, completionCallback);
        }
        else if (synchronizeNow) {
            // start a modal synchronization episode
            ProgressDialogManager.silentMode = false;
            SynchronizationManager.singleton.startSynchronization(context, completionCallback);
        }
    }

    /**
     * completes a synchronization task by releasing associated locks
     */
    public static void completeSynchronization() {
        synchronized (SynchronizationManager.singleton) {
            SynchronizationManager.singleton.isSynchronizing = false;
        }
    }

    /**
     * Make sure our background timer is scheduled. Assumes that it's called under a lock.
     * 
     * Returns true if we allocated the timer
     */
    private boolean ensureTimerIsScheduled() {
        boolean scheduledTimer = false;
        if (this.timer == null) {
            this.timer = new Timer();
            // TODO: should we kick off a synchronization episode immediately? If so, change the first parameter here
            // and modify the caller appropriately
            this.timer.scheduleAtFixedRate(new BackgroundSynchronizationTask(this, false),
                    SYNCHRONIZATION_INTERVAL, SYNCHRONIZATION_INTERVAL);
            scheduledTimer = true;
        }

        return scheduledTimer;
    }

    /**
     * By the time this method is called, we know that no synchronization is in progress, so it's our job to kick it off
     */
    private void startSynchronization(Context context, Handler completionCallback) {
        this.completionCallback = completionCallback;
        this.currentContext = context;
        this.progressMessageHandler = createProgressMessageHandler();
        this.internalMessageHandler = new Handler() {
            @Override
            public void handleMessage(Message message) {
                handleBackgroundThreadMessage(message);
            }
        };
        this.backgroundThread = new Thread(new BackgroundSynchronizationTask(this, true));
        this.backgroundThread.start();
    }

    /**
     * We have a new activity to attach to our progress UI and/or we have to bring up a progress dialog to link
     * into an existing synchronization
     */
    private void attachActivity(Context context, Handler completionCallback) {
        this.completionCallback = completionCallback;
        this.currentContext = context;
        ProgressDialogManager.tryDisplayProgressDialog(context);
    }

    private void sendInternalMessage(int what) {
        if (this.internalMessageHandler != null) {
            this.internalMessageHandler.sendEmptyMessage(what);
        }
    }

    private static Handler createProgressMessageHandler() {
        return new Handler() {
            public void handleMessage(Message message) {
                // Level
                int level = message.getData().getInt("node");
                if (level > 0) {
                    ProgressDialogManager.setProgress(level);
                }

                // Max
                int max = message.getData().getInt("max");
                if (max > 0) {
                    ProgressDialogManager.setMax(max);
                }
            }
        };
    }

    private void showErrorDialog(int errorText) {
        // if the progress dialog is still showing, remove it since
        // we're replacing the UI with an error dialog
        ProgressDialogManager.tryDestroyProgressDialog();

        // For retries we don't need to claim the lock, since we already have it
        DialogInterface.OnClickListener onClickRetry = new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                dialog.cancel();
                startSynchronization(currentContext, completionCallback);
            }
        };

        // for the error-out case, release the lock and we'll try again later
        DialogInterface.OnClickListener onClickOk = new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                dialog.cancel();
                SynchronizationManager.completeSynchronization();
            }
        };
        ErrorDialogManager.show(errorText, this.currentContext, onClickOk, onClickRetry);
    }

    /**
     * Our two background processes (downloading and parsing) communicate with us through Android messaging on the UI
     * thread (so we can react by manipulating UI)
     */
    private void handleBackgroundThreadMessage(Message message) {
        switch (message.what) {
            case Global.KEYWORD_DOWNLOAD_STARTING:
                // TODO: Can we do this on the UI thread before we offload the process into the background?
                // it would cleanup the code, allow us to easily thread in Global.SETUP_DIALOG when Storage is empty,
                // and avoid a few extra thread switches
                ProgressDialogManager.displayProgressDialog(Global.UPDATE_DIALOG, this.currentContext);
                break;
            case Global.CONNECTION_ERROR:
                showErrorDialog(R.string.connection_error);
                break;
            case Global.KEYWORD_DOWNLOAD_SUCCESS:
                // TODO: do we want to update the progress dialog here?
                // download complete, start parsing
                // NOTE: we do not dismiss the dialog, so that it shows until we receive
                // the Global.KEYWORD_PARSE_GOT_NODE_TOTAL signal
                break;
            case Global.KEYWORD_DOWNLOAD_FAILURE:
                showErrorDialog(R.string.incomplete_keyword_response_error);
                break;
            case Global.KEYWORD_PARSE_GOT_NODE_TOTAL:
                int nodeCount = message.getData().getInt("nodeCount");
                ProgressDialogManager.displayProgressDialog(Global.PARSE_DIALOG, this.currentContext, nodeCount);
                break;
            case Global.KEYWORD_PARSE_SUCCESS:
                ProgressDialogManager.tryDestroyProgressDialog();
                Toast updateToast = Toast.makeText(this.currentContext, this.currentContext.getString(R.string.refreshed),
                        Toast.LENGTH_LONG);
                updateToast.show();

                // in the error case, this is updated on click of the error dialog "OK" button. On success
                // it should hit this path
                SynchronizationManager.completeSynchronization();
                break;
            case Global.KEYWORD_PARSE_ERROR:
                showErrorDialog(R.string.keyword_parse_error);
                break;
            case Global.DISMISS_WAIT_DIALOG:
                ProgressDialogManager.tryDestroyProgressDialog();
                break;
        }

        if (this.completionCallback != null) {
            this.completionCallback.sendEmptyMessage(message.what);
        }
    }

    /**
     * Called by our background or timer thread to perform the actual synchronization tasks from a separate thread.
     */
    private void performBackgroundSynchronization() {
        InboxAdapter inboxAdapter = new InboxAdapter(ApplabActivity.getGlobalContext());
        inboxAdapter.open();

        submitPendingUsageLogs(inboxAdapter);
        submitIncompleteSearches(inboxAdapter);
        
        inboxAdapter.close();

        // we may want to associate UI with this task, so create
        // a looper to setup the message pump (by default, background threads
        // don't have a message pump)
        Looper.prepare();
        String newKeywords = downloadKeywords(Settings.getServerUrl());
        if (newKeywords != null) {
            parseKeywords(newKeywords);
        }

        // TODO: should Looper.loop be called before downloadKeywords?
        Looper.loop();
        Looper looper = Looper.getMainLooper();
        looper.quit();
    }
    
    /**
     * Upload the data about searches that have been performed off-line so that the CKW
     * and farmer statistics are updated correctly
     */
    private void submitPendingUsageLogs(InboxAdapter inboxAdapter) {
        List<InboxAdapter.SearchUsage> pendingSearches = inboxAdapter.getLocalSearches();
        for (InboxAdapter.SearchUsage pendingSearch : pendingSearches) {
            String searchResult = pendingSearch.submitSearch();
            if (searchResult != null) {
                inboxAdapter.deleteRecord(InboxAdapter.ACCESS_LOG_DATABASE_TABLE, pendingSearch.getSearchTableId());
            }            
        }
    }
    
    /**
     * Perform searches that were unable to be completed earlier due to lack of connectivity
     */
    private void submitIncompleteSearches(InboxAdapter inboxAdapter) {
        List<InboxAdapter.SearchUsage> pendingSearches = inboxAdapter.getPendingSearches();
        for (InboxAdapter.SearchUsage pendingSearch : pendingSearches) {
            String searchResult = pendingSearch.submitSearch();
            if (searchResult != null) {
                inboxAdapter.updateRecord(pendingSearch.getSearchTableId(), searchResult);
            }            
        }
    }

    /**
     * Get any updated keywords from the server. Our protocol allows for incremental updates, to the server will only
     * return items that are "new" from the perspective of the client.
     * 
     * NOTE: for now we are using the legacy API where we get _all_ keywords every time
     */
    private String downloadKeywords(String baseServerUrl) {
        // notify the UI that we're starting a download
        sendInternalMessage(Global.KEYWORD_DOWNLOAD_STARTING);

        // TODO: replace this get with a post call, factor out HttpPost code from
        // PulseDataCollector.downloadTabUpdates into our CommonClient library
        // HttpPost httpPost = HttpHelpers.createPost(baseServerUrl + "search/getKeywords");

        String newKeywords = HttpHelpers.fetchContent(baseServerUrl + ApplabActivity.getGlobalContext().getString(R.string.update_path));

        // Check if we downloaded successfully
        int connectionResult = Global.KEYWORD_DOWNLOAD_FAILURE;
        if (newKeywords != null && newKeywords.trim().endsWith("</Keywords>")) {
            connectionResult = Global.KEYWORD_DOWNLOAD_SUCCESS;
        }

        // and notify again that we've completed (either with success or failure
        sendInternalMessage(connectionResult);
        return newKeywords;
    }

    private void parseKeywords(String newKeywordsXml) {
        // Call KeywordParser to parse the keywords result and store the contents in our
        // local database
        // TODO: integrate this code into our synchronization manager?
        KeywordParser keywordParser = new KeywordParser(this.currentContext,
                this.progressMessageHandler, this.internalMessageHandler, newKeywordsXml);
        keywordParser.run();
    }

    /**
     * Called by our background thread to try and claim the lock. This may fail if for example, a Refresh happens
     * concurrently with our timer firing. In that case we will let the timer fail gracefully
     */
    private boolean tryStartSynchronization() {
        if (this.isSynchronizing) {
            return false;
        }

        synchronized (this) {
            if (this.isSynchronizing) {
                return false;
            }

            this.isSynchronizing = true;
            return true;
        }
    }

    /**
     * handler that we use to schedule synchronization tasks on a separate thread
     * 
     * Used both on-demand and timer-based synchronization. In the on-demand case, we may interact with UI through the
     * message pump
     * 
     */
    private class BackgroundSynchronizationTask extends TimerTask {
        private SynchronizationManager synchronizationManager;

        // true if we already have the synchronization lock
        private boolean hasLock;

        public BackgroundSynchronizationTask(SynchronizationManager synchronizationManager,
                                             boolean hasLock) {
            this.synchronizationManager = synchronizationManager;
            this.hasLock = hasLock;

        }

        @Override
        public void run() {
            boolean doSynchronization;

            // first determine if there is already a synchronization episode in-progress
            if (this.hasLock) {
                assert (this.synchronizationManager.isSynchronizing) : "if we have the lock, isSynchronizing must be true";
                doSynchronization = true;
            }
            else {
                doSynchronization = this.synchronizationManager.tryStartSynchronization();
            }

            // and if not, start the heavy lifting from our background thread
            if (doSynchronization) {
                this.synchronizationManager.performBackgroundSynchronization();
            }
        }
    }
}