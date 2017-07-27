package org.hardvar.blackbox;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Created by Allan Leon on 7/27/2017.
 */
public class DialLauncherReceiver extends BroadcastReceiver {
    private static final String LAUNCHER_NUMBER = "2017";

    @Override
    public void onReceive(Context context, Intent intent) {
        String phoneNumber = intent.getStringExtra(Intent.EXTRA_PHONE_NUMBER);

        if (LAUNCHER_NUMBER.equals(phoneNumber)) {
            setResultData(null);
            Intent appIntent = new Intent(context, MainActivity.class);
            appIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(appIntent);
        }
    }
}
