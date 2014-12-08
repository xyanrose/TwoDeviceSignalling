package com.example.android.BluetoothChat;

import java.util.ArrayList;

public class Direction {
	
	private ArrayList<float[]> records;
	
	public Direction() {
		records = new ArrayList<float[]>();
	}
	
	public void addRecord(float[] rec) {
		
		records.add(rec);
		
	}
	
	public ArrayList<float[]> getRecords() {
		
		return records;
		
	}
	
	public void clearRecords() {
		
		records.clear();
		
	}
	
	public String getDirection() {
		if(records.size() == 0) {
			return "NONE";
		}
		else {
			String result = "";
			
			float avgX = 0, avgY = 0;
			for(int i = 0; i < records.size(); i++) {
				
				avgX += records.get(i)[0];
				avgY += records.get(i)[1];
				
			}
			
			avgX /= records.size();
			avgY /= records.size();
			
			if (avgY >= .5) {
				result += "S";
			}
			else if (avgY < -.5) {
				result += "N";
			}
			
			if (avgX >= .5) {
				result += "W";
			}
			else if (avgX < -.5) {
				result += "E";
			}
			
			return result;
			
		}
		
	}
	
}
