package applab.search.client;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Timer;
import java.util.TimerTask;

import applab.search.client.R;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.Toast;

/**
 * synchronizes local data including: updating keywords, submitting usage logs,
 * submitting incomplete searches, and retrieving search results
 */
public class SynchronizeTask {
	/** for debugging purposes in adb logcat */
	private final String LOG_TAG = "SynchronizeTask";

	/** Handler to notify UI of new changes */
	private Handler handler;

	/** The application context */
	private Context applicationContext;

	/** schedules recurring tasks using a timer */
	private Timer timer;
	private KeywordDownloader keywordDownloader;

	public SynchronizeTask(Handler uiHandler, Context applicationContext) {
		this.handler = uiHandler;
		this.applicationContext = applicationContext;
	}

	/** thread for launch time synchronization */
	public void startLaunchThread() {
		// background thread for launch time synchronization
		Thread launchTimeSynchronizationThread = new Thread() {
			public void run() {
				Log.i(LOG_TAG, "Launch time synchronization started...");
				submitPendingUsageLogs();
				submitIncompleteSearches();

				// TODO: Removing this from 2.7 because the user experience
				// isn't yet satisfactory.
				// For 2.7 we only have Initial update and Manual update
				// For 2.8 we need to think about how the user expects the timer
				// to work
				// backgroundUpdateKeywords();

				// Schedule recurring tasks
				scheduleRecurringTimer();
			}
		};
		launchTimeSynchronizationThread.start();
	}

	/**
	 * A timer for scheduled tasks
	 */
	public void scheduleRecurringTimer() {
		Log.i(LOG_TAG, "Start timer.");
		this.timer = new Timer();
		this.timer.scheduleAtFixedRate(new SynchronizeScheduler(this),
				Global.SYNCHRONIZATION_INTERVAL,
				Global.SYNCHRONIZATION_INTERVAL);
	}

	/**
	 * updates keywords in background
	 */
	private void backgroundUpdateKeywords() {
		if (KeywordSynchronizer.tryStartSynchronization()) {
			String base = getServerUrl(R.string.update_path);
			try {
				Log.i(LOG_TAG, "Updating keywords...");
				URL url = new URL(base);
				String newKeywords = DownloadManager.retrieveData(url);
				if (newKeywords != null) {
					if (newKeywords.trim().endsWith("</Keywords>")) {
						KeywordParser keywordParser = new KeywordParser(
								applicationContext, backgroundUpdateHandler,
								newKeywords);
						Thread parser = new Thread(keywordParser);
						parser.start();
					} else {
						Log.e(LOG_TAG, "ERROR: Invalid keyword file!");
						KeywordSynchronizer.completeSynchronization();
						this.handler
								.sendEmptyMessage(Global.DISMISS_WAIT_DIALOG);
					}
				} else {
					Log.i(LOG_TAG, "Failed to connect.");
					KeywordSynchronizer.completeSynchronization();
					this.handler.sendEmptyMessage(Global.DISMISS_WAIT_DIALOG);
				}
			} catch (MalformedURLException e) {
				Log.e(LOG_TAG, "MalformedURLException: " + e);
			}
		} else {
			Log.i(LOG_TAG, "Cannot synchronize at this time.");
		}
	}

	/**
	 * handler for the background update process
	 */
	final Handler backgroundUpdateHandler = new Handler() {
		// A handler for parsing responses
		public void handleMessage(Message msg) {
			switch (msg.what) {
			case Global.KEYWORD_PARSE_SUCCESS:
				Toast.makeText(applicationContext,
						applicationContext.getString(R.string.refreshed),
						Toast.LENGTH_LONG).show();
				break;
			case Global.KEYWORD_PARSE_ERROR:

				break;
			}
			KeywordSynchronizer.completeSynchronization();
			handler.sendEmptyMessage(Global.DISMISS_WAIT_DIALOG);
		}
	};

	/**
	 * retrieves server URL
	 * 
	 * @param id
	 *            the server path
	 * @return the resource url
	 */
	private String getServerUrl(int id) {
		SharedPreferences settings = PreferenceManager
				.getDefaultSharedPreferences(applicationContext);
		String url = settings.getString(Settings.KEY_SERVER, applicationContext
				.getString(R.string.server));
		url = url.concat("/" + applicationContext.getString(id));
		Global.URL = url;
		return url;
	}

	/**
	 * retrieves search results
	 * 
	 * @param message
	 *            the search url encoded request string
	 */
	public void getSearchResults(final String message) {
		Thread connectThread = new Thread() {
			public void run() {
				String base = getServerUrl(R.string.search_path);
				try {
					URL url = new URL(base + message);
					String results = DownloadManager.retrieveData(url);
					if (results != null) {
						if (handler != null) {
							Global.data = results;
							handler.sendEmptyMessage(Global.CONNECTION_SUCCESS);
						}
					} else {
						if (handler != null) {
							handler.sendEmptyMessage(Global.CONNECTION_ERROR);
						}
					}
				} catch (MalformedURLException e) {
					Log.d(LOG_TAG, "MalformedURLException: " + e.toString());
				}
			}
		};
		connectThread.start();
	}

	/**
	 * attempts to submit and update incomplete searches
	 */
	private void submitIncompleteSearches() {
		String base = getServerUrl(R.string.search_path);
		SubmitIncompleteSearches submitIncompleteSearches = new SubmitIncompleteSearches(
				this.applicationContext, base);
		submitIncompleteSearches.updateIncompleteSearches();
	}

	/**
	 * attempts to submit pending usage logs
	 */
	private void submitPendingUsageLogs() {
		String base = getServerUrl(R.string.search_path);
		SubmitLocalSearchUsage submitLocalSearchUsage = new SubmitLocalSearchUsage(
				this.applicationContext, base);
		submitLocalSearchUsage.sendLogs();
	}

	/**
	 * A background timer that executes scheduled tasks
	 * 
	 */
	class SynchronizeScheduler extends TimerTask {
		SynchronizeTask synchronize;

		public SynchronizeScheduler(SynchronizeTask synchronizeTask) {
			this.synchronize = synchronizeTask;
		}

		@Override
		public void run() {
			Log.i(LOG_TAG, "Timer run()");
			Log.i(LOG_TAG, "Submitting usage logs...");
			submitPendingUsageLogs();
			Log.i(LOG_TAG, "Updating incomplete searches...");
			submitIncompleteSearches();

			// Log.i(LOG_TAG, "Updating keywords...");
			// backgroundUpdateKeywords();
			// XXX Luke asked to keep this update to launch time synchronization
		}
	}
}
