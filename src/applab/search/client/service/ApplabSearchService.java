package applab.search.client.service;

import java.util.Timer;

import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;
import applab.client.service.ApplabService;
import applab.search.client.SynchronizationManager;
import applab.search.client.TimedSynchronizationTask;

/**
 * Needed to create ApplabSearchService mainly to handle synchronization. TODO: Should consider moving
 * SynchronizationManager to CommonClient, so that the ApplabService can access it
 * 
 * @since 3.1
 */
public class ApplabSearchService extends ApplabService {
    private static final String TAG = "ApplabSearchService";

    public ApplabSearchService() {
        super();
    }

    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        
        synchronized(SynchronizationManager.singleton) {
            SynchronizationManager.singleton.ensureTimerIsScheduled();
        }

        Log.v(TAG, "ApplabSearchService Created");
    }

    @Override
    public void onStart(Intent intent, int startId) {
        super.onStart(intent, startId);

        Log.v(TAG, "ApplabSearchService -- onStart()");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        Log.v(TAG, "ApplabSearchService Destroyed");
    }
}
