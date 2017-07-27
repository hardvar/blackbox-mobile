package org.hardvar.blackbox;

import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private static final ComponentName LAUNCHER_COMPONENT_NAME = new ComponentName(
            "org.hardvar.blackbox", "org.hardvar.blackbox.Launcher");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (this.isLauncherIconVisible()) {
            this.hideLauncherIcon();
        }
    }

    private boolean isLauncherIconVisible() {
        int enabled = getPackageManager()
                .getComponentEnabledSetting(LAUNCHER_COMPONENT_NAME);
        return enabled != PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
    }

    private void hideLauncherIcon() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("App icon will be hidden");
        builder.setMessage("To launch the app again, dial phone number 2017.");
        builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                getPackageManager().setComponentEnabledSetting(LAUNCHER_COMPONENT_NAME,
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                        PackageManager.DONT_KILL_APP);
            }
        });
        builder.setIcon(android.R.drawable.ic_dialog_alert);
        builder.show();
    }
}
