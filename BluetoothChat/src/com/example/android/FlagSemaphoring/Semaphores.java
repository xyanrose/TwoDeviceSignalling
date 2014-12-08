package com.example.android.FlagSemaphoring;

import java.io.IOException;

import com.example.android.BluetoothChat.R;

import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.DialogInterface;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

public class Semaphores extends ListActivity{

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		// storing string resources into Array
		String[] semaphores = getResources().getStringArray(R.array.Semaphores);

		// Binding resources Array to ListAdapter
		this.setListAdapter(new ArrayAdapter<String>(this, R.layout.semaphore_layout, R.id.label, semaphores));

		ListView lv = getListView();

		// listening to single list item on click
		lv.setOnItemClickListener(new OnItemClickListener() {

			@Override
			public void onItemClick(AdapterView<?> parent, View view,
					int position, long id) {

				// selected item 
				String product = ((TextView) view).getText().toString();

				loadPhoto(product);

			}

		});
	}

	private void loadPhoto(String nameOfSemaphore) {

		AlertDialog.Builder builder = new AlertDialog.Builder(Semaphores.this);

		builder.setNeutralButton(R.string.ok, new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int id) {
				dialog.cancel();
			}
		});
		
		ImageView sema = new ImageView(getBaseContext());
		
		try {
			sema.setImageBitmap(BitmapFactory.decodeStream(getAssets().open("Semaphore_" + nameOfSemaphore + ".png")));
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		builder.setCustomTitle(sema);

		AlertDialog dialog = builder.create();
		dialog.show();
	}
}
