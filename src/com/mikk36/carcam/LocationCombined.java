package com.mikk36.carcam;

import java.io.FileOutputStream;

import android.location.Location;

public class LocationCombined {
	private Boolean haveFirstFix;

	private Boolean haveLocation;
	private Boolean haveStatus;
	private long locationTime;
	private long statusTime;

	private double latitude;
	private double longitude;
	private Object altitude;
	private long time;
	private Object speed;
	private int satelliteCount;

	private static final int TYPE_LOCATION = 0;
	private static final int TYPE_STATUS = 1;
	private static final long SEGMENT_PAUSE = 5000000000l;
	private static final long LOCATION_SEPARATION_TIME = 500000000l;

	private static final String TAG = "Location";

	private GPXWriter writer;

	public LocationCombined(FileOutputStream out) {
		Log.log(TAG, "LocationCombined created");
		haveFirstFix = false;
		haveLocation = false;
		haveStatus = false;

		writer = new GPXWriter(out);
	}

	public void updateLocation(Location loc) {
		// Log.log(TAG, "Updating location");
		long tempLocationTime = System.nanoTime();
		haveLocation = true;
		if (tempLocationTime - locationTime > SEGMENT_PAUSE) {
			writer.writeCloseSegment();
			writer.writeOpenSegment();
		}
		locationTime = tempLocationTime;
		latitude = loc.getLatitude();
		longitude = loc.getLongitude();
		if (loc.hasAltitude()) {
			altitude = loc.getAltitude();
		} else {
			altitude = false;
		}
		time = loc.getTime();
		if (loc.hasSpeed()) {
			speed = loc.getSpeed();
		} else {
			speed = false;
		}

		// TODO: Add bearing

		writePoint(TYPE_LOCATION);
	}

	public void updateSatellites(int count) {
		// TODO: rewrite to get from Location

		// Log.log(TAG, "Updating satellite count");
		statusTime = System.nanoTime();
		haveStatus = true;
		satelliteCount = count;

		writePoint(TYPE_STATUS);
	}

	public void updateFirstFix() {
		Log.log(TAG, "Updating first fix");
		haveFirstFix = true;
		writer.writeBeginTrack();
		writer.writeOpenSegment();
	}

	public void startGPX() {
		Log.log(TAG, "Starting GPX logging");
		writer.writeHeader();
	}

	public void stopGPX() {
		Log.log(TAG, "Stopping GPX logging");
		writer.writeCloseSegment();
		writer.writeEndTrack();
		writer.writeFooter();
		writer.close();
	}

	private void writePoint(int updateType) {
		if (haveFirstFix && haveLocation && haveStatus) {
			Log.log(TAG, "writePoint: have firstfix, location and status");
			if (Math.max(locationTime, statusTime) - Math.min(locationTime, statusTime) < LOCATION_SEPARATION_TIME) {
				Log.log(TAG, "writePoint: time between location and status less than 0.5 s");
				writer.writeLocation(this);
				haveLocation = haveStatus = false;
			} else {
				Log.log(TAG, "writePoint: time between location and status more than 0.5 s");
				switch (updateType) {
				case TYPE_LOCATION:
					haveStatus = false;
					break;
				case TYPE_STATUS:
					haveLocation = false;
					break;
				}
			}
		}
	}

	public double getLatitude() {
		return latitude;
	}

	public double getLongitude() {
		return longitude;
	}

	public Object getAltitude() {
		return altitude;
	}

	public long getTime() {
		return time;
	}

	public int getSatelliteCount() {
		return satelliteCount;
	}

	public Object getSpeed() {
		return speed;
	}
}
