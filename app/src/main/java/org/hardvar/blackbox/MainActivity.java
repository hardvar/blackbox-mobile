package org.hardvar.blackbox;

import android.Manifest;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.MediaRecorder;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.FileProvider;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    private static final ComponentName LAUNCHER_COMPONENT_NAME = new ComponentName(
            "org.hardvar.blackbox", "org.hardvar.blackbox.Launcher");

    private Button pairButton;
    private ListView deviceList;
    private TextView receivedData;

    private BluetoothAdapter bluetoothAdapter = null;
    private Set<BluetoothDevice> pairedDevices;
    private BluetoothSocket btSocket = null;
    private MediaRecorder mRecorder = null;
    private Handler bluetoothIn;

    private ConnectedThread mConnectedThread;
    private boolean isRecording = false;

    private static String mFileName = null;

    final int handlerState = 0;                        //used to identify handler message

    // SPP UUID service - this should work for most devices
    private static final UUID BTMODULEUUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    // String for MAC address
    private static String arduinoMACAddress;

    private StringBuilder recDataString = new StringBuilder();

    private AdapterView.OnItemClickListener myListClickListener = new AdapterView.OnItemClickListener() {
        public void onItemClick(AdapterView av, View v, int arg2, long arg3) {
            // Get the device MAC address, the last 17 chars in the View
            String info = ((TextView) v).getText().toString();
            arduinoMACAddress = info.substring(info.length() - 17);

            listenBluetooth();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        pairButton = (Button) findViewById(R.id.pairButton);
        deviceList = (ListView) findViewById(R.id.deviceListView);
        receivedData = (TextView) findViewById(R.id.arduinoDataTextView);

        if (isLauncherIconVisible()) {
            hideLauncherIcon();
        }

        checkBluetoothStatus();
        pairButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showPairedDevices();
            }
        });

        bluetoothIn = new Handler() {
            public void handleMessage(Message msg) {
                if (msg.what == handlerState) {                                     //if message is what we want
                    String readMessage = (String) msg.obj;                                                                // msg.arg1 = bytes from connect thread
                    recDataString.append(readMessage);                                     //keep appending to string until ~
                    System.out.println(recDataString);
                    if (recDataString.charAt(0) == '#' && !isRecording) {
                        System.out.println("Recording");
                        isRecording = true;
                        mFileName = Environment.getExternalStorageDirectory().getAbsolutePath();
                        mFileName += "/blackbox-" + new Date().toString() + ".m4a";
                        startRecording(mFileName);
                        new android.os.Handler().postDelayed(
                                new Runnable() {
                                    public void run() {
                                        if (isRecording) {
                                            System.out.println("Saving");
                                            isRecording = false;
                                            mRecorder.stop();
                                            mRecorder.reset();
                                            mRecorder.release();
                                            mRecorder = null;
                                            if (isOnline()) {
                                                sendWhatsAppMessage(mFileName);
                                            } else {
                                                callReliableNumber();
                                            }
                                        }
                                    }
                                }, 3000);
                        //}
                    }
                    recDataString.delete(0, recDataString.length());                    //clear all string data
                }
            }
        };
    }

    private void checkBluetoothStatus() {
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            Toast.makeText(getApplicationContext(), "Bluetooth Device Not Available", Toast.LENGTH_LONG).show();
            finish();
        } else {
            if (bluetoothAdapter.isEnabled()) {
            } else {
                //Ask to the user turn the bluetooth on
                Intent turnBTon = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(turnBTon, 1);
            }
        }
    }

    private void showPairedDevices() {
        pairedDevices = bluetoothAdapter.getBondedDevices();
        ArrayList list = new ArrayList();

        if (pairedDevices.size() > 0) {
            for (BluetoothDevice bt : pairedDevices) {
                list.add(bt.getName() + "\n" + bt.getAddress()); //Get the device's name and the address
            }
        } else {
            Toast.makeText(getApplicationContext(), "No Paired Bluetooth Devices Found.", Toast.LENGTH_LONG).show();
        }

        final ArrayAdapter adapter = new ArrayAdapter(this, android.R.layout.simple_list_item_1, list);
        deviceList.setAdapter(adapter);
        deviceList.setOnItemClickListener(myListClickListener); //Method called when the device from the list is clicked
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

    private BluetoothSocket createBluetoothSocket(BluetoothDevice device) throws IOException {
        //creates a secure outgoing connection with BT device using UUID
        return device.createRfcommSocketToServiceRecord(BTMODULEUUID);
    }

    private void listenBluetooth() {
        if (bluetoothAdapter != null) {
            //create device and set the MAC address
            BluetoothDevice device = bluetoothAdapter.getRemoteDevice(arduinoMACAddress);

            try {
                btSocket = createBluetoothSocket(device);
            } catch (IOException e) {
                Toast.makeText(getBaseContext(), "Socket creation failed", Toast.LENGTH_LONG).show();
            }
            // Establish the Bluetooth socket connection.
            try {
                btSocket.connect();
            } catch (IOException e) {
                try {
                    btSocket.close();
                } catch (IOException e2) {
                    //insert code to deal with this
                }
            }
            mConnectedThread = new ConnectedThread(btSocket);
            mConnectedThread.start();

            //I send a character when resuming.beginning transmission to check device is connected
            //If it is not an exception will be thrown in the write method and finish() will be called
            //mConnectedThread.write("x");
        }
    }

    private void startRecording(String filename) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO},
                    0);
        } else {
            mRecorder = new MediaRecorder();
            mRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            mRecorder.setOutputFile(filename);
            mRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);

            try {
                mRecorder.prepare();
            } catch (IOException e) {
                //Log.e(LOG_TAG, "prepare() failed");
            }
            mRecorder.start();
        }
    }

    private void callReliableNumber() {
        Intent intent = new Intent(Intent.ACTION_CALL);

        intent.setData(Uri.parse("tel:70385155"));
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }
        getApplicationContext().startActivity(intent);
    }

    private boolean isOnline() {
        ConnectivityManager cm =
                (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo netInfo = cm.getActiveNetworkInfo();
        return netInfo != null && netInfo.isConnectedOrConnecting();
    }

    private void sendWhatsAppMessage(String filename) {
        Intent sharingIntent = new Intent(Intent.ACTION_SEND);
        sharingIntent.setType("audio/*");
        sharingIntent.putExtra(Intent.EXTRA_STREAM, FileProvider.getUriForFile(getApplicationContext(), getApplicationContext().getPackageName() + ".org.hardvar.provider", new File(filename)));
        sharingIntent.putExtra("jid", "59170385155" + "@s.whatsapp.net"); //phone number without "+" prefix
        sharingIntent.setPackage("com.whatsapp");
        startActivity(sharingIntent);
    }


    //create new class for connect thread
    private class ConnectedThread extends Thread {
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;

        //creation of the connect thread
        public ConnectedThread(BluetoothSocket socket) {
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            try {
                //Create I/O streams for connection
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) { }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }

        public void run() {
            byte[] buffer = new byte[256];
            int bytes;

            // Keep looping to listen for received messages
            while (true) {
                try {
                    bytes = mmInStream.read(buffer);            //read bytes from input buffer
                    String readMessage = new String(buffer, 0, bytes);
                    // Send the obtained bytes to the UI Activity via handler
                    bluetoothIn.obtainMessage(handlerState, bytes, -1, readMessage).sendToTarget();
                } catch (IOException e) {
                    break;
                }
            }
        }
    }
}
