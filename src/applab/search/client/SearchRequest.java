package applab.search.client;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

import android.content.Context;
import android.content.DialogInterface;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import applab.client.HttpHelpers;
import applab.client.search.R;

/**
 * Construct that represents a request for search data. Right now this always results in a remote PHP GET call
 * 
 * In the future we will first look at our local cache of results, and then if we don't have anything locally, we will
 * send a POST request to our servlet
 * 
 */
public class SearchRequest {
    private static final String TAG = "SearchRequest";
    // constants used for communicating with our background thread that performs search requests for submitInBackground
    public static final int SEARCH_SUBMISSION_SUCCESS = 1;
    public static final int SEARCH_SUBMISSION_FAILURE = 2;
    private Handler submissionHandler;

    private String keyword;
    private String farmerId;
    private String submissionTime;
    private String location;
    private boolean isLogRequest;
    private String result;
    private String category;

    // TODO: add category as an input to SearchRequest
    public SearchRequest(String keyword, String farmerId, String submissionTime) {
        this(keyword, farmerId, submissionTime, null);
    }

    public SearchRequest(String keyword, String farmerId, String submissionTime, String location) {
        this(keyword, farmerId, submissionTime, location, false);
    }

    public SearchRequest(String keyword, String farmerId, String submissionTime, String location,
                         boolean isLogRequest) {

        this.category = "";
        this.keyword = keyword;

        // TODO: Why do we get a null farmer Id sometimes?
        if (farmerId == null) {
            farmerId = "";
        }

        this.farmerId = farmerId;
        this.submissionTime = submissionTime;
        if (location == null || location.length() == 0) {
            location = GlobalConstants.location;
        }

        this.location = location;
        this.isLogRequest = isLogRequest;
    }

    public SearchRequest(String keyword, String farmerId, String submissionTime, String location,
                         boolean isLogRequest, String category) {

        this(keyword, farmerId, submissionTime, location, isLogRequest);
        this.category = category;
    }

    public String getKeyword() {
        return this.keyword;
    }

    public String getCategory() {
        return this.category;
    }

    public String getFarmerId() {
        return this.farmerId;
    }

    public String getLocation() {
        return this.location;
    }

    public String getResult() {
        return this.result;
    }

    /**
     * Get the search content associated with this request.
     * 
     * Called from our background thread for active searches, and from the SynchronizationManager for updating various
     * unsent-searches and logs
     */
    public boolean submit() {
        String searchUrl = Settings.getNewServerUrl() + "search/search";
        StringBuilder requestParameters = new StringBuilder();
        try {
            requestParameters.append("?submissionTime=" + URLEncoder.encode(this.submissionTime, "UTF-8"));
            requestParameters.append("&intervieweeId=" + URLEncoder.encode(this.farmerId, "UTF-8"));
            requestParameters.append("&keyword=" + URLEncoder.encode(this.keyword, "UTF-8"));
            requestParameters.append("&location=" + URLEncoder.encode(this.location, "UTF-8"));

            if (this.category != null) {
                requestParameters.append("&category=" + URLEncoder.encode(this.category, "UTF-8"));
            }
            
            if (this.isLogRequest) {
                requestParameters.append("&log=true");
            }
            this.result = HttpHelpers.fetchContent(searchUrl + requestParameters.toString());
        }
        catch (UnsupportedEncodingException e) {
            // We should never get here, but if so, report failure
            return false;
        } 
        catch(Exception exception)
        {
            exception.printStackTrace();
            return false;
        }
        return this.result != null;
    }

    /**
     * Method that can be called from the UI thread to submit this request in the background
     */
    public void submitInBackground(Context context, Handler submissionHandler) {

        // Store the caller's Handler in a member field since we can't pass state to all the UI paths
        this.submissionHandler = submissionHandler;

        // TODO: in cases where we have the result locally we won't need to do these steps

        // First bring up a progress dialog since we're going to hit the network
        ProgressDialogManager.silentMode = false;
        ProgressDialogManager.displayProgressDialog(GlobalConstants.CONNECT_DIALOG, context);

        // Pass the results to our UI thread, embedded in the request
        final Handler internalHandler = new Handler() {
            @Override
            public void handleMessage(Message message) {
                onSearchSubmission(message);
            }
        };

        // now make a request to our server in the background so that we don't block the UI thread
        Thread backgroundThread = new Thread() {
            public void run() {
                if (submit()) {
                    sendSuccessMessage(internalHandler);
                }
                else {
                    sendFailureMessage(internalHandler);
                }
            }
        };
        backgroundThread.start();
    }

    private void onSearchSubmission(Message message) {
        switch (message.what) {
            case SEARCH_SUBMISSION_SUCCESS:
                ProgressDialogManager.tryDestroyProgressDialog();
                break;
            case SEARCH_SUBMISSION_FAILURE:
                ProgressDialogManager.tryDestroyProgressDialog();

                // search failed, allow the user to attempt a retry
                DialogInterface.OnClickListener onClickRetry = new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.cancel();
                        submit();
                    }
                };

                DialogInterface.OnClickListener onClickCancel = new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.cancel();

                        // only chain to the original caller if the user cancels the request
                        sendFailureMessage(submissionHandler);
                    }
                };
                ErrorDialogManager.show(R.string.connection_error_message, null, onClickRetry, "Retry", onClickCancel, "Cancel");
                break;
        }

        if (this.submissionHandler != null) {
            this.submissionHandler.handleMessage(message);
        }
    }

    private void sendSuccessMessage(Handler handler) {
        Message submissionMessage = Message.obtain(handler, SEARCH_SUBMISSION_SUCCESS, this);
        submissionMessage.sendToTarget();
    }

    private void sendFailureMessage(Handler handler) {
        if (handler != null) {
            Message failureMessage = Message.obtain(handler, SEARCH_SUBMISSION_FAILURE, this);
            failureMessage.sendToTarget();
        }
    }
}
