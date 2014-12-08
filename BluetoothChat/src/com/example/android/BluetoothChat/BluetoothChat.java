/*
 * Copyright (C) 2009 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.BluetoothChat;

import java.io.IOException;
import java.util.concurrent.Semaphore;

import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.Window;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import android.media.MediaPlayer;

/**
 * This is the main Activity that displays the current chat session.
 */
public class BluetoothChat extends Activity implements SensorEventListener {
	// Debugging stuff
	private static final String TAG = "BluetoothChat";
	private static final boolean D = true;

	// Message types sent from the BluetoothChatService Handler //
	public static final int MESSAGE_STATE_CHANGE = 1;
	public static final int MESSAGE_READ = 2;
	public static final int MESSAGE_WRITE = 3;
	public static final int MESSAGE_DEVICE_NAME = 4;
	public static final int MESSAGE_TOAST = 5;

	// Key names received from the BluetoothChatService Handler
	public static final String DEVICE_NAME = "device_name";
	public static final String TOAST = "toast";

	// Intent request codes
	private static final int REQUEST_CONNECT_DEVICE_SECURE = 1;
	private static final int REQUEST_ENABLE_BT = 3;

	// Name of the connected device
	private String mConnectedDeviceName = null;
	// String buffer for outgoing messages
	private StringBuffer mOutStringBuffer;
	// Local Bluetooth adapter
	private BluetoothAdapter mBluetoothAdapter = null;
	// Member object for the chat services
	private BluetoothChatService mChatService = null;

	private AsyncTask<Void, Void, Void> asyncTask = null;

	// Accelerometer
	private final String START_RECORDING = "START_RECORDING";
	private final String STOP_RECORDING = "STOP_RECORDING";
	private String slaveString;
	private Button ultraButton2001;
	StringBuilder builder = new StringBuilder();
	float[] history = new float[2];
	String direction;
	boolean buttonDepressed = false;
	String masterString;

	// Direction data
	Direction record;

	// Sound
	MediaPlayer media_connected = null;
	MediaPlayer media_start = null;
	MediaPlayer media_detected = null;
	MediaPlayer media_error = null;

	@Override
	public void onCreate(Bundle savedInstanceState) {

		media_connected = MediaPlayer.create(this, R.raw.connected);
		media_start = MediaPlayer.create(this, R.raw.start);
		media_detected = MediaPlayer.create(this, R.raw.flagdetected);
		media_error = MediaPlayer.create(this, R.raw.error_sound);

		record = new Direction();

		super.onCreate(savedInstanceState);
		getWindow().requestFeature(Window.FEATURE_ACTION_BAR);
		if (D)
			Log.e(TAG, "ONCREATE");

		// Set up the window layout
		setContentView(R.layout.main);

		// Get local Bluetooth adapter
		mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

		// Set up SensorManager and accelerometer
		SensorManager manager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);

		Sensor accelerometer = manager.getSensorList(Sensor.TYPE_LINEAR_ACCELERATION)
				.get(0);
		manager.registerListener(this, accelerometer,
				SensorManager.SENSOR_DELAY_FASTEST);

		// button
		ultraButton2001 = (Button) findViewById(R.id.ultraButton2001);
		direction = "";

		ultraButton2001.setOnTouchListener(new OnTouchListener() {

			@Override
			public boolean onTouch(View v, MotionEvent event) {



				if (mChatService.getState() != BluetoothChatService.STATE_CONNECTED) {
					media_error.start();
					return false;
				}

				media_start.start();

				if (event.getAction() == MotionEvent.ACTION_DOWN) {
					masterString ="";
					record.clearRecords();
					buttonDepressed =true;
					sendMessage(START_RECORDING);
					startTask();

				}
				if (event.getAction() == MotionEvent.ACTION_UP) {
					buttonDepressed = false;	
					sendMessage(STOP_RECORDING);

				}

				return true;
			}

		});

		// If the adapter is null, then Bluetooth is not supported
		if (mBluetoothAdapter == null) {
			Toast.makeText(this, "Bluetooth is not available",
					Toast.LENGTH_LONG).show();
			finish();
			return;
		}
		try {
			((ImageView) findViewById(R.id.flag_detected)).setImageBitmap(BitmapFactory.decodeStream(getAssets().open("base.png")));
		} catch (IOException e) {
			e.printStackTrace();
		}
		build_help_general_initial_dialog();
	}

	@Override
	public void onStart() {
		super.onStart();
		if (D)
			Log.e(TAG, "START");

		// If BT is not on, request that it be enabled.
		// setupChat() will then be called during onActivityResult
		if (!mBluetoothAdapter.isEnabled()) {
			Intent enableIntent = new Intent(
					BluetoothAdapter.ACTION_REQUEST_ENABLE);
			startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
			// Otherwise, setup the chat session
		} else {
			if (mChatService == null) {
				setupBT();
			}
		}
	}

	@Override
	public synchronized void onResume() {
		super.onResume();
		if (D)
			Log.e(TAG, "+ ON RESUME +");

		// Performing this check in onResume() covers the case in which BT was
		// not enabled during onStart(), so we were paused to enable it...
		// onResume() will be called when ACTION_REQUEST_ENABLE activity
		// returns.
		if (mChatService != null) {
			// Only if the state is STATE_NONE, do we know that we haven't
			// started already
			if (mChatService.getState() == BluetoothChatService.STATE_NONE) {
				// Start the Bluetooth chat services
				mChatService.start();
			}
		}
	}

	private void setupBT() {

		// Initialize the BluetoothChatService to perform bluetooth connections
		mChatService = new BluetoothChatService(this, mHandler);

		// Initialize the buffer for outgoing messages
		mOutStringBuffer = new StringBuffer("");
	}

	@Override
	public synchronized void onPause() {
		super.onPause();
		if (D)
			Log.e(TAG, "- ON PAUSE -");
	}

	@Override
	public void onStop() {
		super.onStop();
		if (D)
			Log.e(TAG, "-- ON STOP --");
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		// Stop the Bluetooth chat services
		if (mChatService != null)
			mChatService.stop();
		if (D)
			Log.e(TAG, "--- ON DESTROY ---");
	}

	private void ensureDiscoverable() {
		if (D)
			Log.d(TAG, "ensure discoverable");
		if (mBluetoothAdapter.getScanMode() != BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
			Intent discoverableIntent = new Intent(
					BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
			discoverableIntent.putExtra(
					BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300);
			startActivity(discoverableIntent);
		}
	}

	/**
	 * Sends a message.
	 * 
	 * @param message
	 *            A string of text to send.
	 */
	private void sendMessage(String message) {
		// Check that we're actually connected before trying anything
		if (mChatService.getState() != BluetoothChatService.STATE_CONNECTED) {
			return;
		}

		// Check that there's actually something to send
		if (message.length() > 0) {
			// Get the message bytes and tell the BluetoothChatService to write
			byte[] send = message.getBytes();
			mChatService.write(send);

			// Reset out string buffer to zero and clear the edit text field
			mOutStringBuffer.setLength(0);
			//			mOutEditText.setText(mOutStringBuffer);
		}
	}

	// The Handler that gets information back from the BluetoothChatService
	private final Handler mHandler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {
			case MESSAGE_STATE_CHANGE:
				if (D)
					Log.i(TAG, "MESSAGE_STATE_CHANGE: " + msg.arg1);
				switch (msg.arg1) {
				case BluetoothChatService.STATE_CONNECTED:
					media_connected.start();
					((TextView) findViewById(R.id.bt_status_info)).setText(R.string.bt_conn);
					((TextView) findViewById(R.id.bconnto)).setText(mConnectedDeviceName.toString());
					break;
				case BluetoothChatService.STATE_CONNECTING:
					((TextView) findViewById(R.id.bt_status_info)).setText(R.string.bt_conning);
					break;
				case BluetoothChatService.STATE_LISTEN:
				case BluetoothChatService.STATE_NONE:
					((TextView) findViewById(R.id.bt_status_info)).setText(R.string.bt_notconn);
					((TextView) findViewById(R.id.bconnto)).setText(null);
					break;
				}
				break;
			case MESSAGE_WRITE:
				break;
			case MESSAGE_READ:
				byte[] readBuf = (byte[]) msg.obj;
				// construct a string from the valid bytes in the buffer
				String readMessage = new String(readBuf, 0, msg.arg1);
				/*if this device receives the message START_RECORDING, then
				 * the record button the other device has been touched first,
				 * and we should disable the button on this device and begin
				 * recording until further notice from the other device*/
				if (readMessage.equals(START_RECORDING)){
					try {
						((ImageView) findViewById(R.id.flag_detected)).setImageBitmap(BitmapFactory.decodeStream(getAssets().open("base.png")));
					} catch (IOException e) {
						e.printStackTrace();
					}
					ultraButton2001.setEnabled(false);
					buttonDepressed = true;
					BluetoothChat.this.startTask();
				}else if(readMessage.equals(STOP_RECORDING)){
					ultraButton2001.setEnabled(true);
					buttonDepressed = false;
					/*must process the data before sending to avoid sending
					 * in multiple packets*/
					masterString = record.getDirection();
					BluetoothChat.this.sendMessage(masterString);
					record.clearRecords();
					/* the only other communication should be the slave phone sending it's data
					 * back to the master phone. The code below is run by the master.*/
				}else{

					//this should be a single direction
					slaveString = readMessage;
					BluetoothChat.this.displaySemaphore();
				}
				break;
			case MESSAGE_DEVICE_NAME:
				// save the connected device's name
				mConnectedDeviceName = msg.getData().getString(DEVICE_NAME);
				break;
			case MESSAGE_TOAST:
				break;
			}
		}
	};

	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (D)
			Log.d(TAG, "onActivityResult " + resultCode);
		switch (requestCode) {
		case REQUEST_CONNECT_DEVICE_SECURE:
			// When DeviceListActivity returns with a device to connect
			if (resultCode == Activity.RESULT_OK) {
				connectDevice(data, true);
			}
			break;
		case REQUEST_ENABLE_BT:
			// When the request to enable Bluetooth returns
			if (resultCode == Activity.RESULT_OK) {
				// Bluetooth is now enabled, so set up a chat session
				setupBT();
			} else {
				// User did not enable Bluetooth or an error occurred
				Log.d(TAG, "BT not enabled");
				Toast.makeText(this, R.string.bt_not_enabled_leaving,
						Toast.LENGTH_SHORT).show();
				finish();
			}
		}
	}

	protected void displaySemaphore() {

		masterString = record.getDirection();

		String switchVar = masterString+" "+slaveString;

		String result = "";
		//right hand master, left hand slave
		if (switchVar.equals("SE S")){
			//A
			result = "a";
		}else if(switchVar.equals("E S")){
			//B
			result = "b";
		}else if(switchVar.equals("NE S")){
			//C
			result = "c";
		}else if(switchVar.equals("N S")){
			//D
			result = "d";
		}else if(switchVar.equals("S NW")){
			//E
			result = "e";
		}else if(switchVar.equals("S W")){
			//F
			result = "f";
		}else if(switchVar.equals("S SW")){
			//G
			result = "g";
		}else if(switchVar.equals("E SE")){
			//H
			result = "h";
		}else if(switchVar.equals("SE NE")){
			//I
			result = "i";
		}else if(switchVar.equals("N W")){
			//J
			result = "j";
		}else if(switchVar.equals("SE N")){
			//K
			result = "k";
		}else if(switchVar.equals("SE NW")){
			//L
			result = "l";
		}else if(switchVar.equals("SE W")){
			//M
			result = "m";
		}else if(switchVar.equals("SE SW")){
			//N
			result = "n";
		}else if(switchVar.equals("E NE")){
			//O
			result = "o";
		}else if(switchVar.equals("E N")){
			//P
			result = "p";
		}else if(switchVar.equals("E NW")){
			//Q
			result = "q";
		}else if(switchVar.equals("E W")){
			//R
			result = "r";
		}else if(switchVar.equals("E SW")){
			//S
			result = "s";
		}else if(switchVar.equals("NE N")){
			//T
			result = "t";
		}else if(switchVar.equals("NE NW")){
			//U
			result = "u";
		}else if(switchVar.equals("N SW")){
			//V
			result = "v";
		}else if(switchVar.equals("NW W")){
			//W
			result = "w";
		}else if(switchVar.equals("NW SW")){
			//X
			result = "x";
		}else if(switchVar.equals("NE W")){
			//Y
			result = "y";
		}else if(switchVar.equals("SW W")){
			//Z
			result = "z";
		}else if(switchVar.equals("S S")){
			//Space
			result = "space";
		}else if(switchVar.equals("N NW")){
			//Space
			result = "numeral";
		}else if(switchVar.equals("NE SW")){
			//Space
			result = "cancel";
		}

		if(!result.equals("")) {
			media_detected.start();
			try {
				((ImageView) findViewById(R.id.flag_detected)).setImageBitmap(BitmapFactory.decodeStream(getAssets().open(result+".png")));
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		else {
			media_error.start();
			try {
				((ImageView) findViewById(R.id.flag_detected)).setImageBitmap(BitmapFactory.decodeStream(getAssets().open("base.png")));
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

	}

	private void connectDevice(Intent data, boolean secure) {
		// Get the device MAC address
		String address = data.getExtras().getString(
				DeviceListActivity.EXTRA_DEVICE_ADDRESS);
		// Get the BluetoothDevice object
		BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
		// Attempt to connect to the device
		mChatService.connect(device, secure);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.option_menu, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		Intent serverIntent = null;
		switch (item.getItemId()) {
		case R.id.secure_connect_scan:
			// Launch the DeviceListActivity to see devices and do scan
			serverIntent = new Intent(this, DeviceListActivity.class);
			startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE_SECURE);
			return true;
		case R.id.discoverable:
			// Ensure this device is discoverable by others
			ensureDiscoverable();
			return true;
		case R.id.help_button_general: 
			build_help_general_dialog();
			return true;
		case R.id.help_button_detection:
			build_help_detection_dialog();
			return true;
		case R.id.what_is_a_flag_semaphore_button:
			build_what_is_a_flag_semaphore_dialog();
			return true;
		//case R.id.semaphore_button:
			//build_semaphore_dialog();
			//return true;
		case R.id.semaphore_button:
			Intent sem = new Intent(BluetoothChat.this, Semaphores.class);
			this.startActivity(sem);
		}
		return false;
	}

	public void build_help_general_initial_dialog() {

		AlertDialog.Builder builder = new AlertDialog.Builder(BluetoothChat.this);

		builder.setNeutralButton(R.string.ok, new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int id) {
				dialog.cancel();
			}
		});

		TextView titleText = new TextView(getBaseContext());
		titleText.setText(R.string.how_to_use_title);
		titleText.setGravity(Gravity.CENTER);
		titleText.setTextSize(20);

		builder.setMessage(R.string.initial_info);
		builder.setCustomTitle(titleText);

		AlertDialog dialog = builder.create();
		dialog.show();

	}

	public void build_help_general_dialog() {

		AlertDialog.Builder builder = new AlertDialog.Builder(BluetoothChat.this);

		builder.setNeutralButton(R.string.ok, new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int id) {
				dialog.cancel();
			}
		});

		TextView titleText = new TextView(getBaseContext());
		titleText.setText(R.string.how_to_use_title);
		titleText.setGravity(Gravity.CENTER);
		titleText.setTextSize(20);

		builder.setMessage(R.string.how_to_use_info);
		builder.setCustomTitle(titleText);

		AlertDialog dialog = builder.create();
		dialog.show();

	}
	public void build_what_is_a_flag_semaphore_dialog() {

		AlertDialog.Builder builder = new AlertDialog.Builder(BluetoothChat.this);

		builder.setNeutralButton(R.string.ok, new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int id) {
				dialog.cancel();
			}
		});

		TextView titleText = new TextView(getBaseContext());
		titleText.setText(R.string.what_is_a_flag_semaphore_title);
		titleText.setGravity(Gravity.CENTER);
		titleText.setTextSize(20);

		builder.setMessage(R.string.what_is_a_flag_semaphore_info);
		builder.setCustomTitle(titleText);

		AlertDialog dialog = builder.create();
		dialog.show();

	}

	public void build_help_detection_dialog() {

		AlertDialog.Builder builder = new AlertDialog.Builder(BluetoothChat.this);

		builder.setNeutralButton(R.string.ok, new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int id) {
				dialog.cancel();
			}
		});

		TextView titleText = new TextView(getBaseContext());
		titleText.setText(R.string.detection_help_title);
		titleText.setGravity(Gravity.CENTER);
		titleText.setTextSize(20);

		builder.setMessage(R.string.detection_help_info);
		builder.setCustomTitle(titleText);

		AlertDialog dialog = builder.create();
		dialog.show();

	}
	
	public void build_semaphore_dialog() {

		AlertDialog.Builder builder = new AlertDialog.Builder(BluetoothChat.this);

		builder.setNeutralButton(R.string.ok, new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int id) {
				dialog.cancel();
			}
		});

		ImageView sema = new ImageView(getBaseContext());
		try {
			sema.setImageBitmap(BitmapFactory.decodeStream(getAssets().open("Semaphore_Signals_A-Z.png")));
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		builder.setCustomTitle(sema);

		AlertDialog dialog = builder.create();
		dialog.show();

	}

	@Override
	public void onSensorChanged(SensorEvent event) {
		float xChange = history[0] - event.values[0];
		float yChange = history[1] - event.values[1];

		double sensitivity = 0.3;

		if(buttonDepressed && xChange > sensitivity || yChange > sensitivity) {
			record.addRecord(event.values);
		}

		if(!record.getRecords().isEmpty()) {
			Log.d(TAG, record.getDirection());
		}

	}

	public void startTask() {

		asyncTask = new AsyncTask<Void, Void, Void>() {

			@Override
			protected Void doInBackground(Void... params) {
				masterString = "";

				if (buttonDepressed) {
					record.clearRecords();
					while (buttonDepressed) {
						masterString+=","+direction;
					}
				}
				return null;
			}

		}.execute();
	}

	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy) {
		// TODO Auto-generated method stub

	}

}
