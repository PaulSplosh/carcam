package com.mikk36.carcam;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Iterator;
import java.util.Timer;
import java.util.TimerTask;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.hardware.Camera;
import android.location.GpsSatellite;
import android.location.GpsStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.MediaRecorder;
import android.os.Binder;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.os.StatFs;

public class CarcamService extends Service implements MediaRecorder.OnInfoListener, MediaRecorder.OnErrorListener {
	private static final String TAG = "CarcamService";

	private Settings settings;
	private CarcamService instance;

	private final IBinder mBinder = new LocalBinder();

	private Camera myCamera;
	private MediaRecorder mediaRecorder;
	private MyCameraSurfaceView myCameraSurfaceView;

	// private Timer recorderTimer;
	private Timer nmeaTimer;

	private static long maxDataStoreSize;
	private CleanData cleanData;
	private static int audioSource = MediaRecorder.AudioSource.DEFAULT;
	private static int videoSource = MediaRecorder.VideoSource.CAMERA;

	// GPS stuff
	private ArrayList<String> nmeaLog = new ArrayList<String>();
	private static final int nmeaLength = 5 * 1000;
	private LocationCombined location;
	private LocationManager locationManager;
	private LocationListener locationListener = new LocationListener() {
		public void onLocationChanged(Location loc) {
			location.updateLocation(loc);
		}

		public void onProviderDisabled(String provider) {
		}

		public void onProviderEnabled(String provider) {
		}

		public void onStatusChanged(String provider, int status, Bundle extras) {
		}
	};

	GpsStatus.Listener gpsStatusListener = new GpsStatus.Listener() {
		private GpsStatus status;

		public void onGpsStatusChanged(int event) {
			switch (event) {
			case GpsStatus.GPS_EVENT_FIRST_FIX:
				Log.log("onGpsStatusChanged: First Fix");
				location.updateFirstFix();
				break;
			case GpsStatus.GPS_EVENT_SATELLITE_STATUS:
				Log.log(TAG, "GPS status update");
				status = locationManager.getGpsStatus(status);
				Iterable<GpsSatellite> satellites = status.getSatellites();
				Iterator<GpsSatellite> satellitesIterator = satellites.iterator();

				int satellitesUsed = 0;
				while (satellitesIterator.hasNext()) {
					GpsSatellite gpsSatellite = (GpsSatellite) satellitesIterator.next();

					if (gpsSatellite.usedInFix()) {
						satellitesUsed++;
					}
				}
				location.updateSatellites(satellitesUsed);

				satellites = null;
				satellitesIterator = null;
				break;
			case GpsStatus.GPS_EVENT_STARTED:
				Log.log("onGpsStatusChanged: Started");
				location.startGPX();
				break;
			case GpsStatus.GPS_EVENT_STOPPED:
				Log.log("onGpsStatusChanged: Stopped");
				location.stopGPX();
				break;
			}
		}
	};

	public class LocalBinder extends Binder {
		CarcamService getService() {
			return CarcamService.this;
		}
	}

	public IBinder onBind(Intent intent) {
		Log.log(TAG, "onBind called");

		return mBinder;
	}

	public boolean onUnbind(Intent intent) {
		Log.log(TAG, "onUnbind called");
		return false;
	}

	public void onCreate() {
		Log.log(TAG, "Service created");
		super.onCreate();
		instance = this;

		FileOutputStream gpxOutput = null;
		try {
			gpxOutput = new FileOutputStream(getGPXOutputFile());
		} catch (FileNotFoundException e) {
			Log.log("Could not get GPX file: " + e.getMessage());
		}

		location = new LocationCombined(gpxOutput);
		// nmeaHandler = new NmeaHandler();
		locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
		Log.log("Registering GpsStatus Listener");
		locationManager.addGpsStatusListener(gpsStatusListener);
		// Log.log("Registering NMEA Listener");
		// locationManager.addNmeaListener(nmeaListener);
		Log.log("Registering Location Updates");
		locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, locationListener);
	}

	public int onStartCommand(Intent intent, int flags, int startId) {
		super.onStartCommand(intent, flags, startId);

		return START_NOT_STICKY;
	}

	public void startRecording() {

		settings = new Settings();

		StatFs stat = new StatFs(Environment.getExternalStorageDirectory().getPath());
		long bytesAvailable = (long) stat.getBlockSize() * (long) stat.getBlockCount();
		Log.log(TAG, "Total space: " + bytesAvailable);
		maxDataStoreSize = (long) (bytesAvailable * 0.75);
		Log.log(TAG, "Usable space: " + maxDataStoreSize);

		cleanData = new CleanData();
		if (settings.getVideoEnabled()) {
			myCamera = CarcamActivity.myCamera;
			myCameraSurfaceView = CarcamActivity.myCameraSurfaceView2;

			myCamera = getCameraInstance();
			cameraFeatureCheck(myCamera);
		}

		locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
		locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, locationListener);
		// locationManager.addNmeaListener(nmeaListener);
		nmeaTimer = new Timer();
		nmeaTimer.scheduleAtFixedRate(new NmeaWriter(), nmeaLength, nmeaLength);

		if (settings.getVideoEnabled()) {
			releaseCamera();
		}

		prepareMediaRecorder();

		try {
			Log.log(TAG, "Starting mediaRecorder");
			mediaRecorder.start();
			Log.log(TAG, "Mediarecorder started");
		} catch (IllegalStateException e) {
			Log.log(TAG, "Mediarecorder start IllegalStateException: " + e.getMessage());
		}
		// recorderTimer = new Timer();
		// recorderTimer.scheduleAtFixedRate(new restartRecorder(), settings.getMaxLength(),
		// settings.getMaxLength());
		Log.log(TAG, "Started recording");
	}

	public void stopRecording() {
		Log.log(TAG, "Stopping recording");

		// recorderTimer.cancel();
		if (mediaRecorder != null) {
			mediaRecorder.stop();
			releaseMediaRecorder(); // if you are using MediaRecorder, release it first
		}
		if (settings.getVideoEnabled()) {
			releaseCamera(); // release the camera immediately on pause event
		}

		locationManager.removeUpdates(locationListener);
		writeNmeaLog();
		if (nmeaTimer != null)
			nmeaTimer.cancel();
		Log.log(TAG, "Stopped recording");
	}

	public void onDestroy() {
		super.onDestroy();

		location.stopGPX();
		locationManager.removeGpsStatusListener(gpsStatusListener);
		locationManager.removeUpdates(locationListener);
		Log.log(TAG, "Service destroyed");
		CarcamActivity.serviceRunning = false;
	}

	private Camera getCameraInstance() {
		Camera c = null;
		try {
			c = Camera.open(); // attempt to get a Camera instance
		} catch (Exception e) {
			Log.log(TAG, "getCameraInstance(): Camera is not available (in use or does not exist)");
			// Camera is not available (in use or does not exist)
		}
		return c; // returns null if camera is unavailable
	}

	private void cameraFeatureCheck(Camera myCam) {
		Camera.Parameters params = myCam.getParameters();

		params.setFocusMode(Camera.Parameters.FOCUS_MODE_FIXED);
		params.setAntibanding(Camera.Parameters.ANTIBANDING_50HZ);

		myCam.setParameters(params);
	}

	private void releaseCamera() {
		if (myCamera != null) {
			myCamera.release(); // release the camera for other applications
			myCamera = null;
		}
	}

	private boolean prepareMediaRecorder() {
		cleanData.cleanup(
				new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "Carcam"),
				maxDataStoreSize);
		mediaRecorder = new MediaRecorder();

		mediaRecorder.setOnErrorListener(instance);
		mediaRecorder.setOnInfoListener(instance);

		if (settings.getVideoEnabled()) {
			myCamera = getCameraInstance();
			myCamera.unlock();

			mediaRecorder.setCamera(myCamera);

			mediaRecorder.setVideoSource(videoSource);
		}
		if (settings.getAudioEnabled() == true) {
			// .setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION);
			mediaRecorder.setAudioSource(audioSource);
		}

		// mediaRecorder.setProfile(CamcorderProfile
		// .get(CamcorderProfile.QUALITY_HIGH));

		if (settings.getVideoEnabled()) {
			mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
		} else {
			mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
		}

		if (settings.getVideoEnabled()) {
			mediaRecorder.setVideoEncodingBitRate(settings.getVideoBitRate());
			mediaRecorder.setVideoSize(1280, 720);
			mediaRecorder.setVideoFrameRate(30);
			mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
		}
		if (settings.getAudioEnabled()) {
			mediaRecorder.setAudioChannels(1);
			mediaRecorder.setAudioEncodingBitRate(320000);
			mediaRecorder.setAudioSamplingRate(48000);
			mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
		}

		String outputFile = getOutputMediaFile().toString();
		Log.log(TAG, "Output file: '" + outputFile + "'");
		mediaRecorder.setOutputFile(outputFile);
		mediaRecorder.setMaxDuration(settings.getMaxLength()); // Set max duration 60 sec.
		mediaRecorder.setMaxFileSize(3758096384l); // Set max file size

		mediaRecorder.setPreviewDisplay(myCameraSurfaceView.getHolder().getSurface());

		try {
			Log.log(TAG, "Preparing mediaRecorder");
			mediaRecorder.prepare();
			Log.log(TAG, "MediaRecorder prepared");
		} catch (IllegalStateException e) {
			Log.log(TAG, "Mediarecorder prepare failed with IllegalStateException: " + e.getMessage());
			releaseMediaRecorder();
			return false;
		} catch (IOException e) {
			Log.log(TAG, "Mediarecorder prepare failed with IOException: " + e.getMessage());
			releaseMediaRecorder();
			return false;
		}
		return true;
	}

	private void releaseMediaRecorder() {
		if (mediaRecorder != null) {
			mediaRecorder.reset(); // clear recorder configuration
			mediaRecorder.release(); // release the recorder object
			mediaRecorder = null;
			myCamera.lock(); // lock camera for later use
		}
	}

	// class restartRecorder extends TimerTask {
	//
	// @Override
	// public void run() {
	// restartRecording();
	// }
	// }

	// private void restartRecording() {
	// Log.log(TAG, "I should restart recording now.");
	// mediaRecorder.stop();
	// releaseMediaRecorder();
	//
	// releaseCamera();
	//
	// // prepareMediaRecorder();
	// // mediaRecorder.start();
	// startRecording();
	// }

	class NmeaWriter extends TimerTask {
		public void run() {
			writeNmeaLog();
		}
	}

	private void writeNmeaLog() {
		try {
			File mediaStorageDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM),
					"Carcam");
			if (mediaStorageDir.canWrite()) {
				String timeStamp = new SimpleDateFormat("yyyyMMdd").format(new Date());
				File nmeaFile = new File(mediaStorageDir.getPath() + File.separator + "xNMEA_" + timeStamp + ".log");
				FileWriter nmeaWriter = new FileWriter(nmeaFile, true);
				BufferedWriter out = new BufferedWriter(nmeaWriter);
				while (nmeaLog.isEmpty() == false) {
					out.append(nmeaLog.get(0));
					nmeaLog.remove(0);
				}
				out.close();

			}

		} catch (IOException e) {
			Log.log(TAG, "Could not write file " + e.getMessage());
		}
	}

	private static File getOutputMediaFile() {
		// To be safe, you should check that the SDCard is mounted
		// using Environment.getExternalStorageState() before doing this.

		File mediaStorageDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM),
				"Carcam");
		// This location works best if you want the created images to be shared
		// between applications and persist after your app has been uninstalled.

		// Create the storage directory if it does not exist
		if (!mediaStorageDir.exists()) {
			if (!mediaStorageDir.mkdirs()) {
				return null;
			}
		}

		// Create a media file name
		String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
		File mediaFile = new File(mediaStorageDir.getPath() + File.separator + "VID_" + timeStamp + ".mp4");

		return mediaFile;
	}

	class CleanData {
		private long totalSizeBytes;
		private long maxDataSize;

		public void cleanup(File dir, long maxSize) {
			totalSizeBytes = 0L;
			maxDataSize = maxSize;
			Log.log(TAG, "maxSize: " + maxDataSize);
			visitAllFiles(dir);
			Log.log(TAG, "totalSize: " + totalSizeBytes);
		}

		private void visitAllFiles(File dir) {
			if (dir.isDirectory()) {
				String[] children = dir.list();
				Arrays.sort(children);
				for (int i = children.length - 1; i >= 0; i--) {
					visitAllFiles(new File(dir, children[i]));
				}
			} else {
				// log("Checking: " + dir.getPath());
				totalSizeBytes += dir.length();
				if (totalSizeBytes > maxDataSize) {
					Log.log(TAG, "Deleting " + dir.getPath());
					dir.delete();
				}
			}
		}
	}

	public void onError(MediaRecorder mediaRecorder, int what, int extra) {
		Log.log("Video Error: " + what + ", " + extra);
	}

	public void onInfo(MediaRecorder mediaRecorder, int what, int extra) {
		String whatText = null;
		switch (what) {
		case 1:
			whatText = "Unknown error";
			break;
		case 800:
			whatText = "Max duration reached";
			break;
		case 801:
			whatText = "Max file size reached";
			break;
		}
		Log.log(TAG, "Video Info: " + whatText + ", " + extra);

		releaseMediaRecorder();
	}

	private File getGPXOutputFile() {
		if (Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())) {

			File mediaStorageDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM),
					"Carcam");
			if (!mediaStorageDir.exists()) {
				if (!mediaStorageDir.mkdirs()) {
					return null;
				}
			}

			// Create a media file name
			String extension = "gpx";
			File file = new File(mediaStorageDir.getPath() + File.separator + "xGPX_" + baseFileName() + "."
					+ extension);

			return file;
		}
		return null;
	}

	private String baseFileName() {
		return new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
	}
}
