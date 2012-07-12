package com.mikk36.carcam;

import android.location.Location;

public class LocationCombined {
	private Boolean haveFirstFix;

	private Boolean haveLocation;
	private Boolean haveStatus;
	private long dataArrivalTime;

	private double latitude;
	private double longitude;
	private double altitude;
	private long time;

	public void updateLocation(Location loc) {

	}

	public void updateSatellites() {

	}

	public double getLatitude() {
		return latitude;
	}

	public double getLongitude() {
		return longitude;
	}

	public double getAltitude() {
		return altitude;
	}

	public long getTime() {
		return time;
	}
}
