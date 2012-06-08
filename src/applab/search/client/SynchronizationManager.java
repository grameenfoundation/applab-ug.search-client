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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.SequenceInputStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Vector;

import org.apache.http.entity.AbstractHttpEntity;
import org.apache.http.entity.StringEntity;
import org.json.simple.parser.ParseException;
import org.xmlpull.v1.XmlPullParserException;

import android.content.Context;
import android.content.DialogInterface;
import android.content.res.AssetManager;
import android.database.SQLException;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.widget.Toast;
import applab.client.ApplabActivity;
import applab.client.HttpHelpers;
import applab.client.PropertyStorage;
import applab.client.StringHelpers;
import applab.client.XmlEntityBuilder;
import applab.client.XmlHelpers;
import applab.client.farmerregistration.FarmerRegistrationController;
import applab.client.search.R;

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
    // by default we will synchronize once every 12 hours
    public static final int SYNCHRONIZATION_INTERVAL = 12 * 60 * 60 * 1000;
    public static final int SYNCHRONIZATION_START_INTERVAL = 5 * 60 * 1000; // We run 5 minutes after the app is started

    public static SynchronizationManager singleton = new SynchronizationManager();
    private final static String XML_NAME_SPACE = "http://schemas.applab.org/2010/07/search";
    private final static String REQUEST_ELEMENT_NAME = "GetKeywordsRequest";
    private final static String VERSION_ELEMENT_NAME = "localKeywordsVersion";
    private final static String CURRENT_FARMER_ID_COUNT = "currentFarmerIdCount";
    private final static String FARMER_CACHE_LAST_UPDATE_DATE = "localCacheVersion";
    private final static String IMAGES_VERSION_ELEMENT_NAME = "localImagesVersion";
    private final static String CURRENT_MENU_IDS = "menuIds";
    private final static String DEFAULT_KEYWORDS_VERSION = "2010-04-04 00:00:00";
    private final static String DEFAULT_IMAGES_VERSION = "2010-04-04 00:00:00";
    public Timer timer;
    private boolean isSynchronizing;
    private static Boolean synchronizeNow; // This tells us to start sync
    private Handler completionCallback;
    private Context currentContext;
    private Handler progressMessageHandler;
    private Thread backgroundThread;

    /** Search database */
    private static Storage searchDatabase;

    // used for getting messages from the download and parsing threads
    private Handler internalMessageHandler;

    private Boolean launchedFromTimer = false;

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
     * Kick off timed synchronization
     */
    public static void synchronizeFromTimer(Context context, Handler completionCallback) {
        synchronize(context, completionCallback, false, true);
    }

    /**
     * Kick off an immediate synchronization, usually from the refresh menu
     */
    public static void synchronize(Context context, Handler completionCallback) {
        synchronize(context, completionCallback, true, false);
    }

    /**
     * Called when resuming an activity. Will make sure that we have our timer running and that if we're in the middle
     * of a synchronization episode that we attach to any outstanding UI.
     */
    public static void onActivityResume(Context context, Handler completionCallback) {
        synchronize(context, completionCallback, false, false);
    }

    /**
     * Check if it's okay to proceed with a synchronization episode. isModal determines if this is a foreground or
     * background synchronization.
     */
    private static void synchronize(Context context, Handler completionCallback, boolean isModal, boolean launchedFromTimer) {
        boolean processAlreadyRunning = false;
        synchronizeNow = isModal || launchedFromTimer;

        synchronized (SynchronizationManager.singleton) {
            // if we don't have a timer, set that up
            // Moving this to a service
            // SynchronizationManager.singleton.ensureTimerIsScheduled();

            // Now check if we are in the middle of a synchronization episode
            // and if so, we should attach to the UI
            // TODO: check isRunning instead?
            if (SynchronizationManager.isSynchronizing()) {
                processAlreadyRunning = true;
            }
            else {
                // check if we have any keywords cached locally. If not, we have to become
                // modal and initialize our local store.
                if (!StorageManager.hasKeywords()) {
                    synchronizeNow = true;
                }
            }

            if (synchronizeNow && (!processAlreadyRunning)) {
                SynchronizationManager.singleton.launchedFromTimer = launchedFromTimer;
                if (isModal) {
                    // start a modal synchronization episode
                    ProgressDialogManager.silentMode = false;
                }
            }
        }

        if (processAlreadyRunning) {
            // SynchronizationManager.singleton.attachActivity(context, completionCallback);
            Toast notification = Toast.makeText(context,
                    context.getResources().getString(R.string.keywords_updating),
                    Toast.LENGTH_LONG);
            notification.show();
        }
        else if (synchronizeNow) {
            SynchronizationManager.singleton.startSynchronization(context, completionCallback);
        }
    }

    /**
     * completes a synchronization task by releasing associated locks
     */
    public static void completeSynchronization() {
        synchronized (SynchronizationManager.singleton) {
            SynchronizationManager.singleton.isSynchronizing = false;
            SynchronizationManager.singleton.launchedFromTimer = false; // Reset this
        }
    }

    /**
     * Make sure our background timer is scheduled. Assumes that it's called under a lock.
     * 
     * Returns true if we allocated the timer
     */
    public boolean ensureTimerIsScheduled() {
        boolean scheduledTimer = false;
        if (this.timer == null) {
            this.timer = new Timer();
            // TODO: should we kick off a synchronization episode immediately? If so, change the first parameter here
            // and modify the caller appropriately
            this.timer.scheduleAtFixedRate(new TimedSynchronizationTask(), SYNCHRONIZATION_START_INTERVAL,
                    SYNCHRONIZATION_INTERVAL);
            scheduledTimer = true;
        }

        return scheduledTimer;
    }

    /**
     * By the time this method is called, we know that no synchronization is in progress, so it's our job to kick it off
     */
    private void startSynchronization(Context context, Handler completionCallback) {
        this.currentContext = context;
        if (!this.launchedFromTimer) {
            this.completionCallback = completionCallback;
            this.progressMessageHandler = createProgressMessageHandler();

            this.internalMessageHandler = new Handler() {
                @Override
                public void handleMessage(Message message) {
                    handleBackgroundThreadMessage(message);
                }
            };
        }
        else {
            this.completionCallback = null;
            this.progressMessageHandler = completionCallback;
            this.internalMessageHandler = completionCallback;
        }

        BackgroundSynchronizationTask task = new BackgroundSynchronizationTask(this, true);
        if (this.launchedFromTimer) {
            // Timer already has it's own thread, so just run the task
            task.run();
        }
        else {
            // Start a new thread
            this.backgroundThread = new Thread(task);
            this.backgroundThread.start();
        }
    }

    /**
     * We have a new activity to attach to our progress UI and/or we have to bring up a progress dialog to link into an
     * existing synchronization
     * 
     * @deprecated This function is being deprecated in 3.1 due to conflicts with the background process. We now show a
     *             toast instead
     */
    private void attachActivity(Context context, Handler completionCallback) {
        this.completionCallback = completionCallback;
        this.currentContext = context;

        this.progressMessageHandler = createProgressMessageHandler();
        this.internalMessageHandler = new Handler() {

            @Override
            public void handleMessage(Message message) {
                handleBackgroundThreadMessage(message);
            }
        };

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

    /**
     * Show an error dialog that allows the user to either choose "Retry" to attempt synchronization again, or "Cancel"
     * to abandon the process
     */
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
        DialogInterface.OnClickListener onClickCancel = new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                dialog.cancel();
                SynchronizationManager.completeSynchronization();
            }
        };

        ErrorDialogManager.show(errorText, this.currentContext, onClickRetry, "Retry", onClickCancel, "Cancel");
    }

    /**
     * Our two background processes (downloading and parsing) communicate with us through Android messaging on the UI
     * thread (so we can react by manipulating UI)
     */
    void handleBackgroundThreadMessage(Message message) {
        switch (message.what) {
            case GlobalConstants.KEYWORD_DOWNLOAD_STARTING:
                // TODO: Can we do this on the UI thread before we offload the process into the background?
                // it would cleanup the code, allow us to easily thread in Global.SETUP_DIALOG when Storage is empty,
                // and avoid a few extra thread switches
                if (!this.launchedFromTimer) {
                    ProgressDialogManager.displayProgressDialog(GlobalConstants.UPDATE_DIALOG, this.currentContext);
                }
                break;
            case GlobalConstants.CONNECTION_ERROR:
                ProgressDialogManager.tryDestroyProgressDialog();
                if (!this.launchedFromTimer) {
                    showErrorDialog(R.string.connection_error_message);
                }
                break;
            case GlobalConstants.KEYWORD_DOWNLOAD_SUCCESS:
                // TODO: do we want to update the progress dialog here?
                // download complete, start parsing
                // NOTE: we do not dismiss the dialog, so that it shows until we receive
                // the Global.KEYWORD_PARSE_GOT_NODE_TOTAL signal
                break;
            case GlobalConstants.KEYWORD_DOWNLOAD_FAILURE:
                ProgressDialogManager.tryDestroyProgressDialog(); // Dialog may still be launched in background process
                                                                  // as a side-effect
                if (!this.launchedFromTimer) {
                    showErrorDialog(R.string.incomplete_keyword_response_error);
                }
                break;
            case GlobalConstants.KEYWORD_PARSE_GOT_NODE_TOTAL:
                int nodeCount = message.getData().getInt("nodeCount");
                if (!this.launchedFromTimer) {
                    ProgressDialogManager.displayProgressDialog(GlobalConstants.PARSE_DIALOG, this.currentContext, nodeCount);
                }
                break;
            case GlobalConstants.KEYWORD_PARSE_SUCCESS:
                ProgressDialogManager.tryDestroyProgressDialog();
                Toast updateToast = Toast.makeText(this.currentContext, this.currentContext.getString(R.string.refreshed),
                        Toast.LENGTH_LONG);
                updateToast.show();
                // TODO: Decide whether to check for image updates here right after the keyword update and before
                // releasing the synchronization lock.
                // in the error case, this is updated on click of the error dialog "OK" button. On success
                // it should hit this path
                SynchronizationManager.completeSynchronization();
                break;
            case GlobalConstants.KEYWORD_PARSE_ERROR:
                ProgressDialogManager.tryDestroyProgressDialog();
                if (!this.launchedFromTimer) {
                    showErrorDialog(R.string.keyword_parse_error);
                }
                break;
            case GlobalConstants.DISMISS_WAIT_DIALOG:
                ProgressDialogManager.tryDestroyProgressDialog();
                break;
        }

        if (this.completionCallback != null) {
            this.completionCallback.sendEmptyMessage(message.what);
        }
    }

    /**
     * Called by our background or timer thread to perform the actual synchronization tasks from a separate thread.
     * 
     * @throws XmlPullParserException
     */
    private void performBackgroundSynchronization(Context context) throws XmlPullParserException {
        Boolean setupLooper = true;
        if (this.launchedFromTimer) {
            setupLooper = false;
        }

        if (setupLooper) {
            // we may want to associate UI with this task, so create
            // a looper to setup the message pump (by default, background threads
            // don't have a message pump)

            Looper.prepare();
        }

        try {
            sendInternalMessage(GlobalConstants.KEYWORD_DOWNLOAD_STARTING); // We send this so that the dialog shows up
                                                                            // immediately
            SynchronizationManager.singleton.isSynchronizing = true;

            // First submit pending farmer registrations and get latest registration form

            String serverUrl = Settings.getServerUrl();
            FarmerRegistrationController farmerRegController = new
                    FarmerRegistrationController();
            farmerRegController.postFarmerRegistrationData(serverUrl);
            farmerRegController.fetchAndStoreRegistrationForm(serverUrl);

            // Then submit pending usage logs and incomplete searches
            InboxAdapter inboxAdapter = new InboxAdapter(ApplabActivity.getGlobalContext());
            inboxAdapter.open();
            submitPendingUsageLogs(inboxAdapter);

            inboxAdapter.close();

            // Get Person country code
            getCountryCode();

            // Get New Farmer Ids
            getNewFarmerIds();

            // Get Local Farmer Cache
            getFamerLocalCache();

            // Finally update keywords
            updateKeywords(context);
        }
        catch (Exception e) {
            e.printStackTrace();
            completeSynchronization();
        }

        if (setupLooper) {
            // TODO: Looper.loop is problematic here. This should be restructured
            Looper.loop();
            Looper looper = Looper.getMainLooper();
            looper.quit();
        }
    }

    /**
     * Sets the version in the update request entity
     * Passes the keywords version, images version and current MenuIds
     * @return XML request entity
     * @throws UnsupportedEncodingException
     */
    static AbstractHttpEntity getRequestEntity(Context context) throws UnsupportedEncodingException {
        String keywordsVersion = PropertyStorage.getLocal().getValue(GlobalConstants.KEYWORDS_VERSION_KEY, DEFAULT_KEYWORDS_VERSION);
        String imagesVersion = PropertyStorage.getLocal().getValue(GlobalConstants.IMAGES_VERSION_KEY, DEFAULT_IMAGES_VERSION);
        XmlEntityBuilder xmlRequest = new XmlEntityBuilder();
        xmlRequest.writeStartElement(REQUEST_ELEMENT_NAME, XML_NAME_SPACE);
        xmlRequest.writeStartElement(VERSION_ELEMENT_NAME);
        xmlRequest.writeText(keywordsVersion);
        xmlRequest.writeEndElement();
        xmlRequest.writeStartElement(IMAGES_VERSION_ELEMENT_NAME);
        xmlRequest.writeText(imagesVersion);
        xmlRequest.writeEndElement();
        xmlRequest.writeStartElement(CURRENT_MENU_IDS);
        xmlRequest.writeText(getMenuIds(context));
        xmlRequest.writeEndElement();
        xmlRequest.writeEndElement();
        return xmlRequest.getEntity();
    }

    static AbstractHttpEntity getFarmerIdsRequestEntity(int currentFarmerIdCount) throws UnsupportedEncodingException {
        XmlEntityBuilder xmlRequest = new XmlEntityBuilder();
        xmlRequest.writeStartElement(REQUEST_ELEMENT_NAME, XML_NAME_SPACE);
        xmlRequest.writeStartElement(CURRENT_FARMER_ID_COUNT);
        xmlRequest.writeText(Integer.toString(currentFarmerIdCount));
        xmlRequest.writeEndElement();
        xmlRequest.writeEndElement();
        return xmlRequest.getEntity();
    }

    static AbstractHttpEntity getFarmerCacheRequestEntity() throws UnsupportedEncodingException {
        String farmerCacheVersion = PropertyStorage.getLocal().getValue(GlobalConstants.FARMER_CACHE_VERSION_KEY, "2012-04-03 00:00:00");
        XmlEntityBuilder xmlRequest = new XmlEntityBuilder();
        xmlRequest.writeStartElement(REQUEST_ELEMENT_NAME, XML_NAME_SPACE);
        xmlRequest.writeStartElement(FARMER_CACHE_LAST_UPDATE_DATE);
        xmlRequest.writeText(farmerCacheVersion);
        xmlRequest.writeEndElement();
        xmlRequest.writeEndElement();
        return xmlRequest.getEntity();
    }

    static AbstractHttpEntity getCountryCodeRequestEntity() throws UnsupportedEncodingException {

        XmlEntityBuilder xmlRequest = new XmlEntityBuilder();
        xmlRequest.writeStartElement(REQUEST_ELEMENT_NAME, XML_NAME_SPACE);
        xmlRequest.writeEndElement();
        return xmlRequest.getEntity();
    }

    /**
     * Upload the data about searches that have been performed off-line so that the CKW and farmer statistics are
     * updated correctly
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

    private void parseKeywords(Vector<InputStream> keywordStreams) throws XmlPullParserException, ParseException {
        // Call KeywordParser to parse the keywords result and store the contents in our
        // local database
        // TODO: integrate this code into our synchronization manager?
        SequenceInputStream sequenceInputStream = new SequenceInputStream(keywordStreams.elements());
        JsonSimpleParser keywordParser = new JsonSimpleParser(this.progressMessageHandler, this.internalMessageHandler, sequenceInputStream);
        keywordParser.run();
    }

    private void parseFarmerIds(InputStream farmerIdStream) throws XmlPullParserException, ParseException {
        // Call FarmerIdParser to parse the farmerIds result and store the contents in our
        // local database
        // TODO: integrate this code into our synchronization manager?
        JsonSimpleFarmerIdParser farmerIdParser = new JsonSimpleFarmerIdParser(this.progressMessageHandler, this.internalMessageHandler,
                farmerIdStream);
        farmerIdParser.run();
    }

    private void parseFarmerCache(InputStream farmerCacheStream) throws XmlPullParserException, ParseException {
        // Call FarmerCacheParser to parse the farmerCache result and store the contents in our
        // local database
        JsonSimpleFarmerCacheParser farmerCacheParser = new JsonSimpleFarmerCacheParser(this.progressMessageHandler,
                this.internalMessageHandler, farmerCacheStream);
        farmerCacheParser.run();
    }

    private void parseCountryCode(InputStream countryCodeStream) throws XmlPullParserException, ParseException {
        // Call FarmerCacheParser to parse the farmerCache result and store the contents in our
        // local database
        JsonSimpleCountryCodeParser countryCodeParser = new JsonSimpleCountryCodeParser(this.progressMessageHandler, countryCodeStream);
        countryCodeParser.run();
    }

    /**
     * Called by our background thread to try and claim the lock. This may fail if for example, a Refresh happens
     * concurrently with our timer firing. In that case we will let the timer fail gracefully
     */
    private boolean tryStartSynchronization() {
        if (isSynchronizing()) {
            return false;
        }

        synchronized (this) {
            if (isSynchronizing()) {
                return false;
            }

            synchronizeNow = true;
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
    private class BackgroundSynchronizationTask implements Runnable {
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
                assert (SynchronizationManager.isSynchronizing()) : "if we have the lock, isSynchronizing must be true";
                doSynchronization = true;
            }
            else {
                doSynchronization = this.synchronizationManager.tryStartSynchronization();
            }

            // and if not, start the heavy lifting from our background thread
            if (doSynchronization) {
                try {
                    this.synchronizationManager.performBackgroundSynchronization(this.synchronizationManager.currentContext);
                }
                catch (Exception e) {
                    e.printStackTrace();
                    ProgressDialogManager.tryDestroyProgressDialog(); // Just in case it was showing
                }
            }
        }
    }

    /**
     * @throws XmlPullParserException
     * @throws ParseException
     */
    public void updateKeywords(Context context) throws XmlPullParserException, ParseException {

        try {
            // get URL to check if the connection should be made to test.applab.org,
            // if yes, always download from remote source
            String url = Settings.getNewServerUrl()
                    + ApplabActivity.getGlobalContext().getString(
                            R.string.update_path);
            String keywordsVersion = PropertyStorage.getLocal().getValue(GlobalConstants.KEYWORDS_VERSION_KEY, DEFAULT_KEYWORDS_VERSION);

            // check if its the first time for keywords to run 
            // and connection is not set to point to test.applab.org, if yes, load keywords from bundled file
            if (DEFAULT_KEYWORDS_VERSION == keywordsVersion  && !url.contains("test") ) {
                AssetManager assetManager = context.getAssets();

                // Add split keywords file into vector
                Vector<InputStream> inputStreams = new Vector<InputStream>();
                inputStreams.add(assetManager.open("keywords-1.txt"));
                inputStreams.add(assetManager.open("keywords-2.txt"));
                inputStreams.add(assetManager.open("keywords-3.txt"));

                if (inputStreams.isEmpty() || inputStreams.firstElement() == null) {
                    updateKeywordsFromRemoteSource(context, url);
                }
                else {
                    sendInternalMessage(GlobalConstants.KEYWORD_DOWNLOAD_SUCCESS);
                    parseKeywords(inputStreams);

                    for (InputStream stream : inputStreams) {
                        if (stream != null) {
                            stream.close();
                        }
                    }
                }
            }
            else {
                updateKeywordsFromRemoteSource(context, url);
            }

        }
        catch (IOException e) {
            sendInternalMessage(GlobalConstants.CONNECTION_ERROR);
        }
    }

    public void getNewFarmerIds() throws XmlPullParserException, ParseException {

        String url = Settings.getNewServerUrl()
                + ApplabActivity.getGlobalContext().getString(
                        R.string.get_farmer_ids_path);

        int networkTimeout = 3 * 60 * 1000;

        InputStream farmerIdsStream;
        try {

            if (searchDatabase == null) {
                searchDatabase = new Storage(ApplabActivity.getGlobalContext());
            }
            int unsedfarmerIdCount = searchDatabase.getUnusedFarmerIdCount();
            farmerIdsStream = HttpHelpers.postJsonRequestAndGetStream(url,
                    (StringEntity)getFarmerIdsRequestEntity(unsedfarmerIdCount), networkTimeout);

            // Write the farmer Ids to disk, and then open a FileStream
            String filePath = ApplabActivity.getGlobalContext().getCacheDir()
                    + "/farmerIds.tmp";
            Boolean downloadSuccessful = HttpHelpers.writeStreamToTempFile(farmerIdsStream, filePath);
            farmerIdsStream.close();
            File file = new File(filePath);
            FileInputStream inputStream = new FileInputStream(file);

            if (downloadSuccessful && inputStream != null) {
                parseFarmerIds(inputStream);
            }

            if (inputStream != null) {
                inputStream.close();
                file.delete();
            }
        }
        catch (IOException e) {
            sendInternalMessage(GlobalConstants.CONNECTION_ERROR);
        }
    }

    private void getFamerLocalCache() throws XmlPullParserException, ParseException {
        String url = Settings.getNewServerUrl()
                + ApplabActivity.getGlobalContext().getString(
                        R.string.get_farmer_cache_path);

        int networkTimeout = 5 * 60 * 1000;

        InputStream farmerCacheStream;
        try {

            if (searchDatabase == null) {
                searchDatabase = new Storage(ApplabActivity.getGlobalContext());
            }
            farmerCacheStream = HttpHelpers.postJsonRequestAndGetStream(url,
                    (StringEntity)getFarmerCacheRequestEntity(), networkTimeout);

            // Write the farmer Ids to disk, and then open a FileStream
            String filePath = ApplabActivity.getGlobalContext().getCacheDir()
                    + "/farmerCache.tmp";
            Boolean downloadSuccessful = HttpHelpers.writeStreamToTempFile(farmerCacheStream, filePath);
            farmerCacheStream.close();
            File file = new File(filePath);
            FileInputStream inputStream = new FileInputStream(file);

            if (downloadSuccessful && inputStream != null) {
                parseFarmerCache(inputStream);
            }

            if (inputStream != null) {
                inputStream.close();
                file.delete();
            }
        }
        catch (IOException e) {
            sendInternalMessage(GlobalConstants.CONNECTION_ERROR);
        }
    }

    private void getCountryCode() throws XmlPullParserException, ParseException {

        // Get country code of CKW only if its has never been set
        String countryCode = PropertyStorage.getLocal().getValue(GlobalConstants.COUNTRY_CODE, "NONE");
        if ("NONE".equalsIgnoreCase(countryCode)) {
            String url = Settings.getNewServerUrl()
                    + ApplabActivity.getGlobalContext().getString(
                            R.string.get_country_code_path);

            int networkTimeout = 3 * 60 * 1000;

            InputStream countryCodeStream;
            try {

                countryCodeStream = HttpHelpers.postJsonRequestAndGetStream(url,
                        (StringEntity)getCountryCodeRequestEntity(), networkTimeout);

                if (countryCodeStream != null) {
                    parseCountryCode(countryCodeStream);
                }

                if (countryCodeStream != null) {
                    countryCodeStream.close();
                }
            }
            catch (IOException e) {
                sendInternalMessage(GlobalConstants.CONNECTION_ERROR);
            }
        }
    }

    /**
     * Update keywords from remote source, after the initial download is done
     * 
     * @throws UnsupportedEncodingException
     * @throws IOException
     * @throws XmlPullParserException
     * @throws ParseException
     */
    private void updateKeywordsFromRemoteSource(Context context, String url) throws UnsupportedEncodingException, IOException, XmlPullParserException, ParseException {
        
        int networkTimeout = 5 * 60 * 1000;
        InputStream keywordStream;
        keywordStream = HttpHelpers.postJsonRequestAndGetStream(url,
                (StringEntity)getRequestEntity(context), networkTimeout);

        // Write the keywords to disk, and then open a FileStream
        String filePath = ApplabActivity.getGlobalContext().getCacheDir()
                + "/keywords.tmp";
        Boolean downloadSuccessful = HttpHelpers.writeStreamToTempFile(keywordStream, filePath);
        keywordStream.close();
        File file = new File(filePath);
        FileInputStream inputStream = new FileInputStream(file);

        if (downloadSuccessful && inputStream != null) {
            sendInternalMessage(GlobalConstants.KEYWORD_DOWNLOAD_SUCCESS);

            // Create Vector of input streams with one element
            Vector<InputStream> inputStreams = new Vector<InputStream>();
            inputStreams.add(inputStream);
            parseKeywords(inputStreams);
        }
        else {
            sendInternalMessage(GlobalConstants.KEYWORD_DOWNLOAD_FAILURE);
        }

        if (inputStream != null) {
            inputStream.close();
            file.delete();
        }

    }

    private static String getMenuIds(Context context) {
        ArrayList<String> menuIds = new ArrayList<String>();
        if (searchDatabase == null) {
            searchDatabase = new Storage(context);
            searchDatabase.open();
        }
        menuIds = searchDatabase.getLocalMenuIds();
        return StringHelpers.commaSeparateValues(menuIds);
    }
}