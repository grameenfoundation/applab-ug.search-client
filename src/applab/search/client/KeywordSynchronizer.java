package applab.search.client;

import android.util.Log;

/**
 * used to check if it is okay to proceed with a synchronization task
 */
public class KeywordSynchronizer {

	private boolean isSynchronizing;
	private boolean canChangeDatabase;
	private static KeywordSynchronizer singleton = new KeywordSynchronizer();

	private KeywordSynchronizer() {
		this.canChangeDatabase = true;
	}

	/**
	 * Check if it's okay to proceed with a background update or if it is okay
	 * to start a new search. This method in effect will be called when we want
	 * to acquire a synchronization lock.
	 * 
	 * @return true if no synchronization is in progress or database is in use,
	 *         false otherwise.
	 */
	public static boolean tryStartSynchronization() {
		synchronized (KeywordSynchronizer.singleton) {
			Log.e("Singleton", "isSynchronizing-> "
					+ KeywordSynchronizer.singleton.isSynchronizing);
			Log.e("Singleton", "canChangeDatabase-> "
					+ KeywordSynchronizer.singleton.canChangeDatabase);
			// Is there a running synchronization task?
			if (KeywordSynchronizer.singleton.isSynchronizing) {
				return false;
			}
			// Can the database be modified? Mainly for later when we want to
			// seperate downloading keywords and database processing stages
			if (!KeywordSynchronizer.singleton.canChangeDatabase) {
				return false;
			}
			KeywordSynchronizer.singleton.canChangeDatabase = false;
			KeywordSynchronizer.singleton.isSynchronizing = true;
			return true;
		}
	}

	/**
	 * performs an unsynchronized check that can be used for dirty read purposes
	 * such as UI enablement checks, etc.
	 */
	public static boolean isSynchronizing() {
		return KeywordSynchronizer.singleton.isSynchronizing;
	}

	/**
	 * completes a synchronization task by releasing associated locks
	 */
	public static void completeSynchronization() {
		synchronized (KeywordSynchronizer.singleton) {
			KeywordSynchronizer.singleton.isSynchronizing = false;
			KeywordSynchronizer.singleton.canChangeDatabase = true;
		}
	}

}
