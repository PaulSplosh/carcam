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
import java.util.List;
import java.util.TimerTask;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.hardware.Camera;
import android.hardware.Camera.AutoFocusCallback;
import android.hardware.Camera.PictureCallback;
import android.location.GpsSatellite;
import android.location.GpsStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.StatFs;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

public class CarcamActivity extends Activity implements MediaRecorder.OnInfoListener, MediaRecorder.OnErrorListener {
	private Settings settings;
	private static final float brightnessDimmed = (float) 50 / 255;

	private static CarcamActivity instance;
	private CarcamService mBoundService;

	public static Camera myCamera;
	public static MyCameraSurfaceView myCameraSurfaceView;
	public static MyCameraSurfaceView myCameraSurfaceView2;
	private MediaRecorder mediaRecorder;
	private boolean skipSurface = false;
	private Window window;
	private PowerManager pm;
	private PowerManager.WakeLock wl;

	private Button myButton;
	boolean recording;
	public static boolean serviceRunning;
	public boolean restartingRecording;

	private boolean bluetoothOldStatus;

	// private Timer nmeaTimer;

	private static long maxDataStoreSize;
	private CleanData cleanData;
	private static int audioSource = MediaRecorder.AudioSource.CAMCORDER;
	private static int videoSource = MediaRecorder.VideoSource.CAMERA;

	// GPS stuff
	private ArrayList<String> nmeaLog = new ArrayList<String>();
	// private static final int nmeaLength = 5 * 1000;
	// private NmeaHandler nmeaHandler;
	private LocationManager locationManager;
	private LocationListener locationListener = new LocationListener() {
		public void onLocationChanged(Location location) {
			TextView speedText = (TextView) findViewById(R.id.speedText);
			String logText = "";
			if (location.hasSpeed()) {
				logText += "Speed: " + location.getSpeed() + "m/s";
				speedText.setText("" + Math.round(location.getSpeed() * 3.6f));
			} else {
				logText += "Speed: n/a";
				speedText.setText("n/a");
			}
			logText += ", ";
			TextView accuracyText = (TextView) findViewById(R.id.accuracyText);
			if (location.hasAccuracy()) {
				logText += "Accuracy: " + location.getAccuracy();
				accuracyText.setText("" + location.getAccuracy() + " m");
			} else {
				logText += "Accuracy: n/a";
				accuracyText.setText("n/a");
			}
			Log.log(logText);
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
				break;
			case GpsStatus.GPS_EVENT_SATELLITE_STATUS:
				status = locationManager.getGpsStatus(status);
				Iterable<GpsSatellite> satellites = status.getSatellites();
				Iterator<GpsSatellite> satellitesIterator = satellites.iterator();

				int satellitesUsed = 0;
				int satellitesUsedGLONASS = 0;
				String satelliteNumbers = "";
				while (satellitesIterator.hasNext()) {
					GpsSatellite gpsSatellite = (GpsSatellite) satellitesIterator.next();

					satelliteNumbers = satelliteNumbers + gpsSatellite.getPrn() + ":"
							+ Math.round(gpsSatellite.getSnr()) + ", ";
					if (gpsSatellite.usedInFix()) {
						satellitesUsed++;
						if (gpsSatellite.getPrn() >= 65 && gpsSatellite.getPrn() <= 96) {
							Log.log("Found GLONASS satellite: " + gpsSatellite.getPrn());
							satellitesUsedGLONASS++;
						}
					}
				}
				Log.log("Satellites: " + satelliteNumbers);
				satellites = null;
				TextView satelliteText = (TextView) findViewById(R.id.satelliteText);
				String satelliteTextNew = "" + satellitesUsed;
				if (satellitesUsedGLONASS > 0) {
					satelliteTextNew = satelliteTextNew + " (" + satellitesUsedGLONASS + ")";
				}
				satelliteText.setText(satelliteTextNew);
				break;
			case GpsStatus.GPS_EVENT_STARTED:
				Log.log("onGpsStatusChanged: Started");
				break;
			case GpsStatus.GPS_EVENT_STOPPED:
				Log.log("onGpsStatusChanged: Stopped");
				break;
			}
		}
	};

	// GpsStatus.NmeaListener nmeaListener = new GpsStatus.NmeaListener() {
	//
	// public void onNmeaReceived(long timestamp, String nmea) {
	// // log("NMEA received at " + timestamp + ": " + nmea);
	// nmeaHandler.update(nmea);
	// }
	// };

	class NmeaHandler {
		public void update(String nmea) {
			nmeaLog.add(nmea);
		}
	}

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		Log.log("Called onCreate()");
		super.onCreate(savedInstanceState);

		instance = this;

		settings = new Settings();

		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		// WindowManager.LayoutParams lp = getWindow().getAttributes();
		// lp.buttonBrightness = (float) 1;
		// getWindow().setAttributes(lp);

		pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
		wl = pm.newWakeLock(PowerManager.FULL_WAKE_LOCK, "Carcam");
		wl.acquire();

		StatFs stat = new StatFs(Environment.getExternalStorageDirectory().getPath());
		long bytesAvailable = (long) stat.getBlockSize() * (long) stat.getBlockCount();
		Log.log("Total space: " + bytesAvailable);
		maxDataStoreSize = (long) (bytesAvailable * 0.75);
		Log.log("Usable space: " + maxDataStoreSize);

		recording = false;
		cleanData = new CleanData();

		startService(new Intent(CarcamActivity.this, CarcamService.class));
		bindService(new Intent(CarcamActivity.this, CarcamService.class), mConnection, Context.BIND_AUTO_CREATE);

		bluetoothEnable();
	}

	public void onRestart() {
		Log.log("Called onRestart()");
		super.onRestart();

		if (serviceRunning) {
			Log.log("Stopping service");
			mBoundService.stopRecording();
			Log.log("Service stopped");
		}

		skipSurface = true;
	}

	public void onStart() {
		Log.log("Called onStart()");
		super.onStart();

		window = this.getWindow();
		window.addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);
		window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
		window.addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);

		setContentView(R.layout.main);

		if (settings.getRecordingEnabled()) {
			if (settings.getVideoEnabled()) {
				// Get Camera for preview
				myCamera = getCameraInstance();
				if (myCamera == null) {
					Toast.makeText(CarcamActivity.this, "Fail to get Camera", Toast.LENGTH_LONG).show();
				}
				cameraFeatureCheck(myCamera);

				FrameLayout myCameraPreview = (FrameLayout) findViewById(R.id.videoview);

				myCameraSurfaceView = new MyCameraSurfaceView(this, myCamera, instance);
				myCameraPreview.addView(myCameraSurfaceView);

				FrameLayout myCameraPreview2 = (FrameLayout) findViewById(R.id.videoview);
				if (skipSurface == true) {
					skipSurface = false;
					myCameraPreview2.removeView(myCameraSurfaceView2);
				}
				myCameraSurfaceView2 = new MyCameraSurfaceView(this, null, null);
				myCameraPreview.addView(myCameraSurfaceView2);
			}

			myButton = (Button) findViewById(R.id.mybutton);
			myButton.setOnClickListener(myButtonOnClickListener);
		}

		// nmeaHandler = new NmeaHandler();
		locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
		Log.log("Registering GpsStatus Listener");
		locationManager.addGpsStatusListener(gpsStatusListener);
		// Log.log("Registering NMEA Listener");
		// locationManager.addNmeaListener(nmeaListener);
		Log.log("Registering Location Updates");
		locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, locationListener);
		// nmeaTimer = new Timer();
		// nmeaTimer.scheduleAtFixedRate(new NmeaWriter(), nmeaLength, nmeaLength);
	}

	public void onResume() {
		Log.log("Called onResume()");
		super.onResume();

		if (settings.getRecordingEnabled() && recording == true) {
			recording = false;
			Handler handler = new Handler();
			handler.post(new Runnable() {
				public void run() {
					myButton.performClick();
				}
			});
		}
	}

	@Override
	protected void onPause() {
		Log.log("Called onPause()");
		super.onPause();

		if (settings.getRecordingEnabled()) {
			releaseMediaRecorder(); // if you are using MediaRecorder, release it first
			if (settings.getVideoEnabled()) {
				releaseCamera(); // release the camera immediately on pause event
			}
		}

		// locationManager.removeNmeaListener(nmeaListener);
		locationManager.removeGpsStatusListener(gpsStatusListener);
		locationManager.removeUpdates(locationListener);
		// writeNmeaLog();
		// if (nmeaTimer != null)
		// nmeaTimer.cancel();

		if (settings.getRecordingEnabled() && recording && isFinishing() == false) {
			serviceRunning = true;

			mBoundService.startRecording();
		}
	}

	public void onStop() {
		Log.log("Called onStop()");
		super.onStop();
	}

	public void onDestroy() {
		Log.log("Called onDestroy()");
		super.onDestroy();

		bluetoothDisable();

		unbindService(mConnection);
		stopService(new Intent(CarcamActivity.this, CarcamService.class));
		wl.release();

		recording = false;
	}

	private void bluetoothEnable() {
		BluetoothAdapter btAdapter = BluetoothAdapter.getDefaultAdapter();
		if (btAdapter != null) {
			bluetoothOldStatus = btAdapter.isEnabled();

			if (bluetoothOldStatus == false) {
				Log.log("Bluetooth was disabled");
				btAdapter.enable();
			} else {
				Log.log("Bluetooth was enabled");
			}
		}
	}

	private void bluetoothDisable() {
		Log.log("Checking Bluetooth");
		if (bluetoothOldStatus == false) {
			Log.log("Should disable Bluetooth");
			BluetoothAdapter btAdapter = BluetoothAdapter.getDefaultAdapter();
			btAdapter.disable();
		}
	}

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
			Log.log("Could not write file " + e.getMessage());
		}
	}

	private void stopRecording() {
		reInstatePreview();

		// stop recording and release camera
		mediaRecorder.stop(); // stop the recording
		releaseMediaRecorder(); // release the MediaRecorder object
	}

	private void startRecording() {
		releaseCamera();

		if (!prepareMediaRecorder()) {
			Toast.makeText(CarcamActivity.this, "Fail in prepareMediaRecorder()!\n - Ended -", Toast.LENGTH_LONG)
					.show();
		}

		mediaRecorder.start();
		myCameraSurfaceView.setVisibility(MyCameraSurfaceView.GONE);
	}

	private long lastButtonClick = 0;

	Button.OnClickListener myButtonOnClickListener = new Button.OnClickListener() {

		public void onClick(View v) {
			long elapsedTime = System.nanoTime() - lastButtonClick;
			if (elapsedTime < 2000000000l) {
				Log.log("Touched too fast");
				return;
			}
			lastButtonClick = System.nanoTime();
			WindowManager.LayoutParams lp = null;
			if (settings.getBrightness()) {
				lp = getWindow().getAttributes();
			}

			if (recording) {
				if (settings.getBrightness()) {
					lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE;
				}

				stopRecording();

				recording = false;

				myButton.setText(getString(R.string.rec));
			} else {
				if (settings.getBrightness()) {
					lp.screenBrightness = brightnessDimmed;
				}
				// Release Camera before MediaRecorder start

				startRecording();

				recording = true;
				myButton.setText(getString(R.string.stop));
			}
			if (settings.getBrightness()) {
				getWindow().setAttributes(lp);
			}
			// pictureSeriesNumber = 0;
			// takePictureSeries();
		}
	};

	private void reInstatePreview() {
		FrameLayout myCameraPreview = (FrameLayout) findViewById(R.id.videoview);
		myCameraPreview.removeView(myCameraSurfaceView);
		myCameraSurfaceView = new MyCameraSurfaceView(instance, myCamera, instance);
		myCameraPreview.addView(myCameraSurfaceView);
	}

	private Camera getCameraInstance() {
		Camera c = null;
		try {
			c = Camera.open(); // attempt to get a Camera instance
		} catch (Exception e) {
			Log.log("getCameraInstance(): Camera is not available (in use or does not exist), " + e.getMessage());
			// Camera is not available (in use or does not exist)
		}
		return c; // returns null if camera is unavailable
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

			Camera.Parameters params = myCamera.getParameters();
			params.setFocusMode(Camera.Parameters.FOCUS_MODE_FIXED);
			params.setAntibanding(Camera.Parameters.ANTIBANDING_50HZ);
			myCamera.setParameters(params);

			myCamera.unlock();

			mediaRecorder.setCamera(myCamera);

			mediaRecorder.setVideoSource(videoSource);
		}
		if (settings.getAudioEnabled()) {
			mediaRecorder.setAudioSource(audioSource);
		}

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
		Log.log("Output file: '" + outputFile + "'");
		mediaRecorder.setOutputFile(outputFile);
		mediaRecorder.setMaxDuration(settings.getMaxLength());
		mediaRecorder.setMaxFileSize(3758096384l);

		mediaRecorder.setPreviewDisplay(myCameraSurfaceView.getHolder().getSurface());

		try {
			mediaRecorder.prepare();
		} catch (IllegalStateException e) {
			releaseMediaRecorder();
			return false;
		} catch (IOException e) {
			releaseMediaRecorder();
			return false;
		}
		return true;
	}

	private void cameraFeatureCheck(Camera myCam) {
		Camera.Parameters params = myCam.getParameters();
		String logLine = null;

		params.setFocusMode(Camera.Parameters.FOCUS_MODE_FIXED);
		List<Camera.Size> sizeList = params.getSupportedPreviewSizes();
		logLine = "";
		if (sizeList == null) {
			logLine = "no supported sizes";
		} else {
			for (int i = 0; i < sizeList.size(); i++) {
				logLine += sizeList.get(i).width + "x" + sizeList.get(i).height + ", ";
			}
		}
		Log.log("Supported PreviewSizes: " + logLine);

		params.setPreviewSize(1280, 720);
		params.setAntibanding(Camera.Parameters.ANTIBANDING_50HZ);

		myCam.setParameters(params);
	}

	private void releaseMediaRecorder() {
		if (mediaRecorder != null) {
			mediaRecorder.reset(); // clear recorder configuration
			mediaRecorder.release(); // release the recorder object
			mediaRecorder = null;
			myCamera.lock(); // lock camera for later use
		}
	}

	private void releaseCamera() {
		if (myCamera != null) {
			myCamera.release(); // release the camera for other applications
			myCamera = null;
		}
	}

	private File getOutputMediaFile() {
		// To be safe, you should check that the SDCard is mounted using Environment.getExternalStorageState() before
		// doing this.
		if (Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())) {

			File mediaStorageDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM),
					"Carcam");
			// This location works best if you want the created images to be shared between applications and persist
			// after your app has been uninstalled.

			// Create the storage directory if it does not exist
			if (!mediaStorageDir.exists()) {
				if (!mediaStorageDir.mkdirs()) {
					return null;
				}
			}

			// Create a media file name
			String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
			String extension = "3gp";
			if (settings.getVideoEnabled()) {
				extension = "mp4";
			}
			File mediaFile = new File(mediaStorageDir.getPath() + File.separator + "VID_" + timeStamp + "." + extension);

			return mediaFile;
		}
		return null;
	}

	class CleanData {
		private long totalSizeBytes;
		private long maxDataSize;

		public void cleanup(File dir, long maxSize) {
			totalSizeBytes = 0L;
			maxDataSize = maxSize;
			Log.log("maxSize: " + maxDataSize);
			visitAllFiles(dir);
			Log.log("totalSize: " + totalSizeBytes);
		}

		private void visitAllFiles(File dir) {
			if (dir.isDirectory()) {
				String[] children = dir.list();
				Arrays.sort(children);
				for (int i = children.length - 1; i >= 0; i--) {
					visitAllFiles(new File(dir, children[i]));
				}
			} else {
				totalSizeBytes += dir.length();
				if (totalSizeBytes > maxDataSize) {
					Log.log("Deleting " + dir.getPath());
					dir.delete();
				}
			}
		}
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
		Log.log("Video Info: " + whatText + ", " + extra);
		Log.log("I should restart recording now.");

		restartingRecording = true;
		reInstatePreview();
		releaseMediaRecorder();
	}

	// SERVICE

	private ServiceConnection mConnection = new ServiceConnection() {
		public void onServiceConnected(ComponentName className, IBinder service) {
			mBoundService = ((CarcamService.LocalBinder) service).getService();
		}

		public void onServiceDisconnected(ComponentName className) {
			mBoundService = null;
		}
	};

	public void surfaceChanged() {
		if (restartingRecording) {
			startRecording();
			restartingRecording = false;
		}
	}

	public void onError(MediaRecorder mr, int what, int extra) {
		Log.log("Video Error: " + what + ", " + extra);
	}

	// Picture capture
	private int pictureSeriesNumber;

	private void takePictureSeries() {
		switch (pictureSeriesNumber) {
		case 0:
			takePicture(Camera.Parameters.FOCUS_MODE_INFINITY);
			pictureSeriesNumber = 1;
			break;
		case 1:
			takePicture(Camera.Parameters.FOCUS_MODE_AUTO);
			pictureSeriesNumber = 2;
			break;
		case 2:
			takePicture(Camera.Parameters.FOCUS_MODE_FIXED);
			pictureSeriesNumber = 3;
			break;
		}
	}

	private void takePicture(String focusMode) {

		Camera.Parameters params = myCamera.getParameters();

		params.setFocusMode(focusMode);
		params.setPictureSize(3264, 2448);

		float[] distances = new float[3];
		params.getFocusDistances(distances);
		Log.log("Focus distances: " + distances[Camera.Parameters.FOCUS_DISTANCE_NEAR_INDEX] + ", "
				+ distances[Camera.Parameters.FOCUS_DISTANCE_OPTIMAL_INDEX] + ", "
				+ distances[Camera.Parameters.FOCUS_DISTANCE_FAR_INDEX]);

		myCamera.setParameters(params);

		params = myCamera.getParameters();
		distances = new float[3];
		params.getFocusDistances(distances);
		Log.log("Focus distances: " + distances[Camera.Parameters.FOCUS_DISTANCE_NEAR_INDEX] + ", "
				+ distances[Camera.Parameters.FOCUS_DISTANCE_OPTIMAL_INDEX] + ", "
				+ distances[Camera.Parameters.FOCUS_DISTANCE_FAR_INDEX]);

		if (focusMode == Camera.Parameters.FOCUS_MODE_INFINITY || focusMode == Camera.Parameters.FOCUS_MODE_FIXED) {
			myCamera.takePicture(null, null, mPicture);
		} else {
			myCamera.autoFocus(focusCallback);
		}
	}

	private AutoFocusCallback focusCallback = new AutoFocusCallback() {

		public void onAutoFocus(boolean success, Camera camera) {
			Camera.Parameters params = myCamera.getParameters();
			float[] distances = new float[3];
			params.getFocusDistances(distances);
			Log.log("Focus distances: " + distances[Camera.Parameters.FOCUS_DISTANCE_NEAR_INDEX] + ", "
					+ distances[Camera.Parameters.FOCUS_DISTANCE_OPTIMAL_INDEX] + ", "
					+ distances[Camera.Parameters.FOCUS_DISTANCE_FAR_INDEX]);
			myCamera.takePicture(null, null, mPicture);
		}
	};

	private PictureCallback mPicture = new PictureCallback() {

		public void onPictureTaken(byte[] data, Camera camera) {
			Log.log("Got image!");

			File mediaStorageDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM),
					"Carcam");

			// Create the storage directory if it does not exist
			if (!mediaStorageDir.exists()) {
				if (!mediaStorageDir.mkdirs()) {
					return;
				}
			}

			// Create a media file name
			String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
			File imageFile = new File(mediaStorageDir.getPath() + File.separator + "IMG_" + timeStamp + "_"
					+ pictureSeriesNumber + ".jpg");

			try {
				FileOutputStream fos = new FileOutputStream(imageFile);
				fos.write(data);
				fos.close();
			} catch (FileNotFoundException e) {
				Log.log("File not found: " + e.getMessage());
			} catch (IOException e) {
				Log.log("Error accessing file: " + e.getMessage());
			}
			takePictureSeries();
		}
	};
}