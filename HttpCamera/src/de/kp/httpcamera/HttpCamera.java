package de.kp.httpcamera;

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
import android.view.Window;
import android.view.WindowManager;
import de.kp.net.http.HTTPServer;

public class HttpCamera extends Activity {

	private SurfaceView mCameraPreview;
	private SurfaceHolder previewHolder;

	private String TAG = "HTTPCamera";
	private Camera camera;
	protected boolean videoQualityHigh = false;

	private YuvImage lastYuvImage;

	private boolean inPreview = false;
	private boolean cameraConfigured = false;

	private int mPreviewWidth = Integer.valueOf(MediaConstants.WIDTH);
	private int mPreviewHeight = Integer.valueOf(MediaConstants.HEIGHT);
	private HTTPServer streamer = null;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		Log.d(TAG, "onCreate");
		
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        Window win = getWindow();
		win.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);		
        win.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
				WindowManager.LayoutParams.FLAG_FULLSCREEN); 
        

		setContentView(R.layout.main);

		/*
		 * Camera preview initialization
		 */
		mCameraPreview = (SurfaceView) findViewById(R.id.smallcameraview);
		previewHolder = mCameraPreview.getHolder();
		previewHolder.addCallback(surfaceCallback);
		previewHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);

	}

	@Override
	public void onResume() {
		Log.d(TAG, "onResume");

		/*
		 * Starts the HTTP Server
		 */
		// default HTTP port is 80
		try {
			streamer = new HTTPServer(this);
			new Thread(streamer).start();

		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		Log.d(TAG, "HttpServer started");

		camera = Camera.open();
		startPreview();

		super.onResume();

	}

	@Override
	public void onPause() {

		// stop HTTP server
		if (streamer != null)
			streamer.stop();
		streamer = null;



		super.onPause();
	}

	/*
	 * SurfaceHolder callback triple
	 */
	SurfaceHolder.Callback surfaceCallback = new SurfaceHolder.Callback() {
		/*
		 * Created state: - Open camera - initial call to startPreview() - hook
		 * PreviewCallback() on it, which notifies waiting thread with new
		 * preview data - start thread
		 * 
		 * @see android.view.SurfaceHolder.Callback#surfaceCreated(android.view.
		 * SurfaceHolder )
		 */
		public void surfaceCreated(SurfaceHolder holder) {
			Log.d(TAG, "surfaceCreated");

		}

		/*
		 * Changed state: - initiate camera preview size, set
		 * camera.setPreviewDisplay(holder) - subsequent call to startPreview()
		 * 
		 * @see android.view.SurfaceHolder.Callback#surfaceChanged(android.view.
		 * SurfaceHolder , int, int, int)
		 */
		public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
			Log.d(TAG, "surfaceChanged");
			initializePreview(w, h);
			startPreview();
		}

		/*
		 * Destroy State: Take care on release of camera
		 * 
		 * @see
		 * android.view.SurfaceHolder.Callback#surfaceDestroyed(android.view.
		 * SurfaceHolder)
		 */
		public void surfaceDestroyed(SurfaceHolder holder) {
			Log.d(TAG, "surfaceDestroyed");
			
			if (inPreview) {
				camera.stopPreview();
			}
	        camera.setPreviewCallback(null);
			camera.release();
			camera = null;
			inPreview = false;

			// signal to stop active MJPG streams
			lastYuvImage = null;
		}
	};

	private void startPreview() {
		if (cameraConfigured && camera != null) {
			
			// activate onPreviewFrame()
			camera.setPreviewCallback(cameraPreviewCallback);
			
			camera.startPreview();
			inPreview = true;
		}
	}

	/*
	 * Check availability of camera and preview
	 */
	private void initializePreview(int width, int height) {
		if (camera != null && previewHolder.getSurface() != null) {
			try {
				// provide SurfaceView for camera preview
				camera.setPreviewDisplay(previewHolder);

			} catch (Throwable t) {
				Log.e(TAG, "Exception in setPreviewDisplay()", t);
			}

			if (!cameraConfigured) {
				Camera.Parameters parameters = camera.getParameters();

				parameters.setPreviewSize(MediaConstants.WIDTH, MediaConstants.HEIGHT);
				camera.setParameters(parameters);
				cameraConfigured = true;
			}
		}
	}

	/*
	 * SurfaceHolder callback triple
	 */
	Camera.PreviewCallback cameraPreviewCallback = new Camera.PreviewCallback() {
		@Override
		public void onPreviewFrame(byte[] data, Camera camera) {

			// Camera.Parameters parameters = camera.getParameters();
			// int format = parameters.getPreviewFormat();
			// if (format == ImageFormat.NV21 /*|| format == ImageFormat.YUY2 ||
			// format == ImageFormat.NV16*/)

			lastYuvImage = new YuvImage(data, ImageFormat.NV21, mPreviewWidth, mPreviewHeight, null);
		}
	};

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
