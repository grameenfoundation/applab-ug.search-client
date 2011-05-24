package applab.search.client.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import applab.client.ApplabActivity;
import applab.search.client.BaseSearchActivity;


/**
 * Gets notified when the OS starts up. Then we start our global applab service.
 * 
 * @since 3.1
 */
public class BootCompletedReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) 
    {
        if (intent.getAction().equals(Intent.ACTION_BOOT_COMPLETED)) {
             BaseSearchActivity.tryStartService(context);
        }
    }
}
