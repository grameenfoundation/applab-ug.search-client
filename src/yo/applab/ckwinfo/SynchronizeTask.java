package yo.applab.ckwinfo;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Timer;
import java.util.TimerTask;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.util.Log;

/**
 * synchronizes local data including: updating keywords, submitting usage logs,
 * submitting incomplete searches, and retrieving search results
 * 
 */
public class SynchronizeTask {
	/** for debugging purposes in adb logcat */
	private final String LOG_TAG = "SynchronizeTask";

	/** Handler to notify of new changes */
	private Handler handler;

	/** The application context */
	private Context applicationContext;
	
	/**schedules recurring tasks using a timer*/
	private Timer timer;

	public SynchronizeTask(Handler uiHandler, Context applicationContext) {
		this.handler = uiHandler;
		this.applicationContext = applicationContext;
	}

	/**
	 * A timer for sheduled tasks
	 */
	public void scheduleRecurringTimer () {		
		this.timer = new Timer();
		this.timer.scheduleAtFixedRate(new SynchronizeScheduler(this),
				Global.SYNCHRONIZATION_INTERVAL,
				Global.SYNCHRONIZATION_INTERVAL);
	}

	/**
	 * Updates local keywords cache
	 */
	public void updateKeywords() {
		getServerUrl(R.string.update_path);
		KeywordDownloader keywordDownloader = new KeywordDownloader(
				this.handler);
		Thread network = new Thread(keywordDownloader);
		network.start();
	}

	/**
	 * retrieves server URL
	 * 
	 * @param id
	 *            the server path
	 * @return the resource url
	 */
	private String getServerUrl(int id) {
		if (Global.URL != null) { 
			return Global.URL; 
			} 
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
					URI uri = new URI(base + message);
					DownloadManager dataDownloadManager = new DownloadManager();
					String results = dataDownloadManager.retrieveData(uri);
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
				} catch (URISyntaxException e) {
					Log.d(LOG_TAG, "URISyntaxException: " + e.toString());
				}
			}
		};
		connectThread.start();
	}

	/**
	 * attempts to submit and update incomplete searches
	 */
	private void submitIncompleteSearches () {
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
		Thread backgroundSubmissionThread = new Thread(submitLocalSearchUsage);
		backgroundSubmissionThread.start();
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
			Log.i(LOG_TAG, "Submitting usage logs...");
			submitPendingUsageLogs();
			Log.i(LOG_TAG, "Updating incomplete searches...");
			submitIncompleteSearches ();
		}
		

	}
}
