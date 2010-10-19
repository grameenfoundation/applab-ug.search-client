package applab.search.client;

import applab.client.ApplabActivity;

/**
 * Singleton that is responsible for managing the local storage of our data.
 * 
 * This is primarily our local cache of keywords and results, but also includes local storage of our unsent queries and
 * local search usage
 * 
 * TODO: clean this up, unify with Storage class, and migrate the majority to common code
 */
public class StorageManager {
    private static StorageManager singleton = new StorageManager();
    private Storage legacyStorage;
    private boolean hasKeywords;

    private StorageManager() {
    }

    public static boolean hasKeywords() {
        return singleton.privateHasKeywords();
    }

    private Storage getLegacyStorage() {
        if (this.legacyStorage == null) {
            this.legacyStorage = new Storage(ApplabActivity.getGlobalContext());
            this.legacyStorage.open();
        }

        return this.legacyStorage;
    }

    private boolean privateHasKeywords() {
        // once we have valid data, that never changes, but we can
        // switch from invalid to valid at any time
        if (!this.hasKeywords) {
            this.hasKeywords = getLegacyStorage().tableExistsAndIsValid(GlobalConstants.DATABASE_TABLE);
        }

        return this.hasKeywords;
    }
}
