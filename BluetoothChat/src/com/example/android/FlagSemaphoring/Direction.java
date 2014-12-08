package com.example.android.FlagSemaphoring;

import java.util.ArrayList;

public class Direction {
	
	private volatile ArrayList<float[]> records;
	
	public Direction() {
		records = new ArrayList<float[]>();
	}
	
	public synchronized void addRecord(float[] rec) {
		
		records.add(rec);
		
	}
	
	public synchronized ArrayList<float[]> getRecords() {
		
		return records;
		
	}
	
	public synchronized void clearRecords() {
		
		records.clear();
		
	}
	
	public synchronized String getDirection() {
		if(records.size() == 0) {
			return "NONE";
		}
		else {
			String result = "";
			
			float avgX = 0, avgY = 0, maxX = 0, minX = 0, maxY = 0, minY = 0;
			for(float[] data: records) {
				
				avgX += data[0];
				avgY += data[1];
				
				if(maxX < data[0]) {
					maxX = data[0];
				}
				
				if (minX > data[0]) {
					minX = data[0];
				}
				
				if(maxY < data[1]) {
					maxY = data[1];
				}
				
				if (minY > data[1]) {
					minY = data[1];
				}
				
			}
			
			avgX /= records.size();
			avgY /= records.size();
			
			if (avgY >= .7 && maxY >= 3) {
				result += "S";
			}
			else if (avgY < -.7 && minY <= -3) {
				result += "N";
			}
			
			if (avgX >= .4 && maxX >= 2) {
				result += "W";
			}
			else if (avgX < -.4 && minX <= -2) {
				result += "E";
			}
			
			return result;
			
		}
		
	}
	
}
