package com.mikk36.carcam;

import android.content.Context;
import android.hardware.Camera;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

public class MyCameraSurfaceView extends SurfaceView implements SurfaceHolder.Callback {

	private SurfaceHolder mHolder;
	private Camera mCamera;
	private CarcamActivity activity;

	public MyCameraSurfaceView(Context context, Camera camera, CarcamActivity instance) {
		super(context);
		Log.log("SurfaceView: instanciated");
		mCamera = camera;
		activity = instance;

		// Install a SurfaceHolder.Callback so we get notified when the
		// underlying surface is created and destroyed.
		mHolder = getHolder();
		mHolder.addCallback(this);
		// deprecated setting, but required on Android versions prior to 3.0
		// mHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
	}

	public void surfaceChanged(SurfaceHolder holder, int format, int weight, int height) {
		Log.log("SurfaceView: changed");
		// If your preview can change or rotate, take care of those events
		// here.
		// Make sure to stop the preview before resizing or reformatting it.

		if (mHolder.getSurface() == null) {
			// preview surface does not exist
			return;
		}

		if (activity != null && !activity.restartingRecording) {

			// stop preview before making changes
			try {
				mCamera.stopPreview();
			} catch (Exception e) {
				// ignore: tried to stop a non-existent preview
			}

			// make any resize, rotate or reformatting changes here

			// start preview with new settings
			try {
				mCamera.setPreviewDisplay(mHolder);
				mCamera.startPreview();

			} catch (Exception e) {
			}
		}
		if (activity != null) {
			activity.surfaceChanged();
		}
	}

	public void surfaceCreated(SurfaceHolder holder) {
		Log.log("SurfaceView: created");
		// The Surface has been created, now tell the camera where to draw
		// the preview.
		// try {
		// mCamera.setPreviewDisplay(holder);
		// mCamera.startPreview();
		// } catch (IOException e) {
		// }
	}

	public void surfaceDestroyed(SurfaceHolder holder) {
		Log.log("SurfaceView: destroyed");
	}
}
