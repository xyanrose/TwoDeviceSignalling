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

import java.util.HashMap;

import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
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
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.Window;
import android.view.View.OnClickListener;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

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

	// Layout Views
	private ListView mConversationView;
	private Button mSendButton;

	// Name of the connected device
	private String mConnectedDeviceName = null;
	// Array adapter for the conversation thread
	private ArrayAdapter<String> mConversationArrayAdapter;
	// String buffer for outgoing messages
	private StringBuffer mOutStringBuffer;
	// Local Bluetooth adapter
	private BluetoothAdapter mBluetoothAdapter = null;
	// Member object for the chat services
	private BluetoothChatService mChatService = null;

	// Accelerometer
	private final String START_RECORDING = "START_RECORDING";
	private final String STOP_RECORDING = "STOP_RECORDING";
	private String slaveString;
	private Button ultraButton2001;
	private SensorManager manager;
	private Sensor accelerometer;
	StringBuilder builder = new StringBuilder();
	float[] history = new float[2];
	String direction;
	private AsyncTask asyncTask = null;
	boolean buttonDepressed = false;
	String masterString;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		getWindow().requestFeature(Window.FEATURE_ACTION_BAR);
		if (D)
			Log.e(TAG, "+++ ON CREATE +++");

		// Set up the window layout
		setContentView(R.layout.main);

		// Get local Bluetooth adapter
		mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

		// Set up SensorManager and accelerometer
		SensorManager manager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);

		Sensor accelerometer = manager.getSensorList(Sensor.TYPE_ACCELEROMETER)
				.get(0);
		manager.registerListener(this, accelerometer,
				SensorManager.SENSOR_DELAY_NORMAL);

		// button
		ultraButton2001 = (Button) findViewById(R.id.ultraButton2001);
		direction = "";

		ultraButton2001.setOnTouchListener(new OnTouchListener() {

			@Override
			public boolean onTouch(View v, MotionEvent event) {
				if (mChatService.getState() != BluetoothChatService.STATE_CONNECTED) {
					Toast.makeText(BluetoothChat.this, R.string.not_connected, Toast.LENGTH_SHORT).show();
					return false;
				}
				if (event.getAction() == MotionEvent.ACTION_DOWN) {
					masterString ="";
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
		
		build_help_dialog();
	}

	@Override
	public void onStart() {
		super.onStart();
		if (D)
			Log.e(TAG, "++ ON START ++");

		// If BT is not on, request that it be enabled.
		// setupChat() will then be called during onActivityResult
		if (!mBluetoothAdapter.isEnabled()) {
			Intent enableIntent = new Intent(
					BluetoothAdapter.ACTION_REQUEST_ENABLE);
			startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
			// Otherwise, setup the chat session
		} else {
			if (mChatService == null) {
				setupChat();
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

	private void setupChat() {
		Log.d(TAG, "setupChat()");

		// Initialize the array adapter for the conversation thread
		mConversationArrayAdapter = new ArrayAdapter<String>(this,
				R.layout.message);
		mConversationView = (ListView) findViewById(R.id.in);
		mConversationView.setAdapter(mConversationArrayAdapter);

//		// Initialize the compose field with a listener for the return key
//		mOutEditText = (EditText) findViewById(R.id.edit_text_out);
//		mOutEditText.setOnEditorActionListener(mWriteListener);
//
//		// Initialize the send button with a listener that for click events
//		mSendButton = (Button) findViewById(R.id.button_send);
//		mSendButton.setOnClickListener(new OnClickListener() {
//			public void onClick(View v) {
//				// Send a message using content of the edit text widget
//				TextView view = (TextView) findViewById(R.id.edit_text_out);
//				String message = view.getText().toString();
//				sendMessage(message);
//			}
//		});

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
			Toast.makeText(this, R.string.not_connected, Toast.LENGTH_SHORT)
					.show();
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

	// The action listener for the EditText widget, to listen for the return key
	private TextView.OnEditorActionListener mWriteListener = new TextView.OnEditorActionListener() {
		public boolean onEditorAction(TextView view, int actionId,
				KeyEvent event) {
			// If the action is a key-up event on the return key, send the
			// message
			if (actionId == EditorInfo.IME_NULL
					&& event.getAction() == KeyEvent.ACTION_UP) {
				String message = view.getText().toString();
				sendMessage(message);
			}
			if (D)
				Log.i(TAG, "END onEditorAction");
			return true;
		}
	};

	private final void setStatus(int resId) {
		final ActionBar actionBar = getActionBar();
		actionBar.setSubtitle(resId);
	}

	private final void setStatus(CharSequence subTitle) {
		final ActionBar actionBar = getActionBar();
		actionBar.setSubtitle(subTitle);
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
//					setStatus(getString(R.string.title_connected_to,
//							mConnectedDeviceName));
					((TextView) findViewById(R.id.bt_status_info)).setText(R.string.bt_conn);
					((TextView) findViewById(R.id.bconnto)).setText(mConnectedDeviceName.toString());
					mConversationArrayAdapter.clear();
					break;
				case BluetoothChatService.STATE_CONNECTING:
//					setStatus(R.string.title_connecting);
					((TextView) findViewById(R.id.bt_status_info)).setText(R.string.title_connecting);
					break;
				case BluetoothChatService.STATE_LISTEN:
				case BluetoothChatService.STATE_NONE:
//					setStatus(R.string.title_not_connected);
					((TextView) findViewById(R.id.bt_status_info)).setText(R.string.bt_notconn);
					((TextView) findViewById(R.id.bconnto)).setText(null);
					break;
				}
				break;
			case MESSAGE_WRITE:
				byte[] writeBuf = (byte[]) msg.obj;
				// construct a string from the buffer
				String writeMessage = new String(writeBuf);
				mConversationArrayAdapter.add("Me:  " + writeMessage);
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
					ultraButton2001.setEnabled(false);
					buttonDepressed = true;
					BluetoothChat.this.startTask();
				}else if(readMessage.equals(STOP_RECORDING)){
					ultraButton2001.setEnabled(true);
					buttonDepressed = false;
					/*must process the data before sending to avoid sending
					 * in multiple packets*/
					masterString = BluetoothChat.this.getMostFrequentDirection(masterString);
					BluetoothChat.this.sendMessage(masterString);
				/* the only other communication should be the slave phone sending it's data
				 * back to the master phone. The code below is run by the master.*/
				}else{
					
					//this should be a single direction
					slaveString = readMessage;
					BluetoothChat.this.displaySemaphore(masterString, slaveString);
				}
				//mConversationArrayAdapter.add(mConnectedDeviceName + ":  "
				//		+ readMessage);
				break;
			case MESSAGE_DEVICE_NAME:
				// save the connected device's name
				mConnectedDeviceName = msg.getData().getString(DEVICE_NAME);
//				Toast.makeText(getApplicationContext(),
//						"Connected to " + mConnectedDeviceName,
//						Toast.LENGTH_SHORT).show();
				break;
			case MESSAGE_TOAST:
//				Toast.makeText(getApplicationContext(),
//						msg.getData().getString(TOAST), Toast.LENGTH_SHORT)
//						.show();
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
				setupChat();
			} else {
				// User did not enable Bluetooth or an error occurred
				Log.d(TAG, "BT not enabled");
				Toast.makeText(this, R.string.bt_not_enabled_leaving,
						Toast.LENGTH_SHORT).show();
				finish();
			}
		}
	}
	
	private String getMostFrequentDirection(String directions){
		//split strings into array by comma separation
		String[] masterArr = directions.split(",");
		HashMap<String, Integer> h1 = new HashMap<String,Integer>();
		
		//get most common direction for master
		for (String s:masterArr){
			if (h1.containsKey(s)){
				h1.put(s, h1.get(s)+1);
			}else{
				h1.put(s, 1);
			}
		}
		int max = 0;
		String returnString = "";
		for (String s: h1.keySet()){
			if(h1.get(s)>max){
				max = h1.get(s);
				returnString = s;
			}
		}
		return returnString;
		
	}

	protected void displaySemaphore(String masterString2, String slaveString2) {
		/*masterString still has to be processed*/
		masterString = getMostFrequentDirection(masterString2);
		/*slaveString was processed before sending from slave phone*/
		slaveString = slaveString2;
		
		Toast.makeText(this, masterString,
				Toast.LENGTH_SHORT).show();
		Toast.makeText(this, slaveString,
				Toast.LENGTH_SHORT).show();
		String switchVar = masterString+" "+slaveString;
		//right hand master, left hand slave
		if (switchVar.equals("SE S")){
			//A
			Toast.makeText(this, "A",
					Toast.LENGTH_SHORT).show();
		}else if(switchVar.equals("E S")){
			//B
			Toast.makeText(this, "B",
					Toast.LENGTH_SHORT).show();
		}else if(switchVar.equals("NE S")){
			//C
			Toast.makeText(this, "C",
					Toast.LENGTH_SHORT).show();
		}else if(switchVar.equals("N S")){
			//D
			Toast.makeText(this, "D",
					Toast.LENGTH_SHORT).show();
		}else if(switchVar.equals("S NW")){
			//E
			Toast.makeText(this, "E",
					Toast.LENGTH_SHORT).show();
		}else if(switchVar.equals("S W")){
			//F
			Toast.makeText(this, "F",
					Toast.LENGTH_SHORT).show();
		}else if(switchVar.equals("S SW")){
			//G
			Toast.makeText(this, "G",
					Toast.LENGTH_SHORT).show();
		}else if(switchVar.equals("E SE")){
			//H
			Toast.makeText(this, "H",
					Toast.LENGTH_SHORT).show();
		}else if(switchVar.equals("SE NE")){
			//I
			Toast.makeText(this, "I",
					Toast.LENGTH_SHORT).show();
		}else if(switchVar.equals("N W")){
			//J
			Toast.makeText(this, "J",
					Toast.LENGTH_SHORT).show();
		}else if(switchVar.equals("SE N")){
			//K
			Toast.makeText(this, "K",
					Toast.LENGTH_SHORT).show();
		}else if(switchVar.equals("SE NW")){
			//L
			Toast.makeText(this, "L",
					Toast.LENGTH_SHORT).show();
		}else if(switchVar.equals("SE W")){
			//M
			Toast.makeText(this, "M",
					Toast.LENGTH_SHORT).show();
		}else if(switchVar.equals("SE SW")){
			//N
			Toast.makeText(this, "N",
					Toast.LENGTH_SHORT).show();
		}else if(switchVar.equals("E NE")){
			//O
			Toast.makeText(this, "O",
					Toast.LENGTH_SHORT).show();
		}else if(switchVar.equals("E N")){
			//P
			Toast.makeText(this, "P",
					Toast.LENGTH_SHORT).show();
		}else if(switchVar.equals("E NW")){
			//Q
			Toast.makeText(this, "Q",
					Toast.LENGTH_SHORT).show();
		}else if(switchVar.equals("E W")){
			//R
			Toast.makeText(this, "R",
					Toast.LENGTH_SHORT).show();
		}else if(switchVar.equals("E SW")){
			//S
			Toast.makeText(this, "S",
					Toast.LENGTH_SHORT).show();
		}else if(switchVar.equals("NE N")){
			//T
			Toast.makeText(this, "T",
					Toast.LENGTH_SHORT).show();
		}else if(switchVar.equals("NE NW")){
			//U
			Toast.makeText(this, "U",
					Toast.LENGTH_SHORT).show();
		}else if(switchVar.equals("N SW")){
			//V
			Toast.makeText(this, "V",
					Toast.LENGTH_SHORT).show();
		}else if(switchVar.equals("NW W")){
			//W
			Toast.makeText(this, "W",
					Toast.LENGTH_SHORT).show();
		}else if(switchVar.equals("NW SW")){
			//X
			Toast.makeText(this, "X",
					Toast.LENGTH_SHORT).show();
		}else if(switchVar.equals("NE W")){
			//Y
			Toast.makeText(this, "Y",
					Toast.LENGTH_SHORT).show();
		}else if(switchVar.equals("SW W")){
			//Z
			Toast.makeText(this, "Z",
					Toast.LENGTH_SHORT).show();
		}else if(switchVar.equals("S S")){
			//Space
			Toast.makeText(this, "Space",
					Toast.LENGTH_SHORT).show();
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
		case R.id.help_button: 
			build_help_dialog();
			return true;
		}
		return false;
	}
	
	public void build_help_dialog() {
			
			AlertDialog.Builder builder = new AlertDialog.Builder(BluetoothChat.this);
	
			builder.setNeutralButton(R.string.ok, new DialogInterface.OnClickListener() {
			           public void onClick(DialogInterface dialog, int id) {
			        	   dialog.cancel();
			           }
			       });
	
			TextView titleText = new TextView(getBaseContext());
			titleText.setText(R.string.info_title);
			titleText.setGravity(Gravity.CENTER);
			titleText.setTextSize(20);
			
			builder.setMessage(R.string.info);
			builder.setCustomTitle(titleText);
			
			AlertDialog dialog = builder.create();
			dialog.show();
			
	}

	@Override
	public void onSensorChanged(SensorEvent event) {
		// TODO Auto-generated method stub
		float xChange = history[0] - event.values[0];
		float yChange = history[1] - event.values[1];

		history[0] = event.values[0];
		history[1] = event.values[1];

		if (xChange > 1) {
			if (yChange > 1) {
				direction = "NE";
			} else if (yChange < -1) {
				direction = "SE";
			} else {
				direction = "E";
			}
		} else if (xChange < -1) {
			if (yChange > 1) {
				direction = "NW";
			} else if (yChange < -1) {
				direction = "SW";
			} else {
				direction = "W";
			}
		} else {
			if (yChange > 1) {
				direction = "N";
			} else if (yChange < -1) {
				direction = "S";
			} else {
				// direction = "NONE";
			}
		}
	}

	public void startTask() {
		
		asyncTask = new AsyncTask<Void, Void, Void>() {
			
			@Override
			protected Void doInBackground(Void... params) {
				
				if (buttonDepressed) {
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
