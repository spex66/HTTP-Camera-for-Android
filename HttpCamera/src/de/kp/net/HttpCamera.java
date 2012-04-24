package de.kp.net;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import android.app.Activity;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import de.kp.net.http.HTTPServer;

public class HttpCamera extends Activity implements SurfaceHolder.Callback, Camera.PreviewCallback {

	private SurfaceView mCameraPreview;
	private SurfaceHolder mSurfaceHolder;

	private String TAG = "RTSPStreamer";
	private Camera mCamera;
	protected boolean videoQualityHigh = false;

	private YuvImage lastYuvImage;
	private boolean cameraConfigured = false;
	private boolean inPreview = false;

	private int mPreviewWidth = Integer.valueOf(MediaConstants.WIDTH);
	private int mPreviewHeight = Integer.valueOf(MediaConstants.HEIGHT);

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		Log.d(TAG, "onCreate");

		setContentView(R.layout.main);

		/*
		 * Camera preview initialization
		 */
		mCameraPreview = (SurfaceView) findViewById(R.id.smallcameraview);

		SurfaceHolder holder = mCameraPreview.getHolder();
		holder.addCallback(this);
		holder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);

	}

	public void onResume() {
		Log.d(TAG, "onResume");

		/*
		 * Starts the HTTP Server
		 */
		// default HTTP port is 80
		try {
			
			new Thread(new HTTPServer(this)).start();

		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		Log.d(TAG, "HttpServer started");

		super.onResume();

	}

	/*
	 * Created state: - Open camera - initial call to startPreview() - hook
	 * PreviewCallback() on it, which notifies waiting thread with new preview
	 * data - start thread
	 * 
	 * @see
	 * android.view.SurfaceHolder.Callback#surfaceCreated(android.view.SurfaceHolder
	 * )
	 */
	public void surfaceCreated(SurfaceHolder holder) {
		mSurfaceHolder = holder;

		mCamera = Camera.open();
		startPreview();

		mCamera.setPreviewCallback(this);
	}

	/*
	 * Changed state: - initiate camera preview size, set
	 * camera.setPreviewDisplay(holder) - subsequent call to startPreview()
	 * 
	 * @see
	 * android.view.SurfaceHolder.Callback#surfaceChanged(android.view.SurfaceHolder
	 * , int, int, int)
	 */
	public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
		Log.i(TAG, "surfaceChanged");
		initPreview(w, h);
		startPreview();
	}

	/*
	 * Destroy State: Take care on release of camera
	 * 
	 * @see android.view.SurfaceHolder.Callback#surfaceDestroyed(android.view.
	 * SurfaceHolder)
	 */
	public void surfaceDestroyed(SurfaceHolder holder) {
		Log.i(TAG, "surfaceDestroyed");
		if (mCamera != null) {
			synchronized (this) {

				if (inPreview) {
					mCamera.stopPreview();

					// signal to stop MJPEG stream
					lastYuvImage = null;
				}

				// mCamera.setPreviewCallback(null);
				mCamera.release();
				mCamera = null;
				inPreview = false;

			}
		}
	}

	private void startPreview() {
		if (cameraConfigured && mCamera != null) {
			mCamera.startPreview();
			inPreview = true;
		}
	}

	/*
	 * Check availability of camera and preview
	 */
	private void initPreview(int width, int height) {
		if (mCamera != null && mSurfaceHolder.getSurface() != null) {
			try {
				// provide distinct fake SurfaceView for camera preview
				mCamera.setPreviewDisplay(mSurfaceHolder);

			} catch (Throwable t) {
				Log.e(TAG, "Exception in setPreviewDisplay()", t);
			}

			if (!cameraConfigured) {
				Camera.Parameters parameters = mCamera.getParameters();

				parameters.setPreviewSize(MediaConstants.WIDTH, MediaConstants.HEIGHT);
				mCamera.setParameters(parameters);
				cameraConfigured = true;
			}
		}
	}

	@Override
	public void onPreviewFrame(byte[] data, Camera camera) {

		// Camera.Parameters parameters = camera.getParameters();
		// int format = parameters.getPreviewFormat();
		// if (format == ImageFormat.NV21 /*|| format == ImageFormat.YUY2 ||
		// format == ImageFormat.NV16*/)

		lastYuvImage = new YuvImage(data, ImageFormat.NV21, mPreviewWidth, mPreviewHeight, null);
	}

	public byte[] getByteArray() {

		if (lastYuvImage == null)
			return null;
		else {
			ByteArrayOutputStream stream = new ByteArrayOutputStream();
			// store last preview frame for Motion JPEG streaming
			Rect rect = new Rect(0, 0, mPreviewWidth, mPreviewHeight);
			lastYuvImage.compressToJpeg(rect, 60, stream);

			byte[] ba = null;
			try {
				ba = stream.toByteArray();
				stream.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			return ba;

		}
	}

}
