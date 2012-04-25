package de.kp.httpcamera;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import android.app.Activity;
import android.content.res.Resources;
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

	private YuvImage lastYuvImage;

	private boolean inPreview = false;
	private boolean cameraConfigured = false;

	private int cameraQuality = 90;
	
	private int mPreviewWidth  = Integer.valueOf(MediaConstants.WIDTH);
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
		try {
			streamer = new HTTPServer(this);
			new Thread(streamer).start();

		} catch (IOException e) {
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

	/**
	 * This method checks availability of camera and preview
	 * 
	 * @param width
	 * @param height
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

	Camera.PreviewCallback cameraPreviewCallback = new Camera.PreviewCallback() {
		
		/* 
		 * This method registers the last preview frame for Motion JPEG streaming
		 * 
		 * (non-Javadoc)
		 * @see android.hardware.Camera.PreviewCallback#onPreviewFrame(byte[], android.hardware.Camera)
		 */
		public void onPreviewFrame(byte[] data, Camera camera) {
			lastYuvImage = new YuvImage(data, ImageFormat.NV21, mPreviewWidth, mPreviewHeight, null);
		
		}

	};

	
	/**
	 * This method converts the last preview frame from Motion JPEG
	 * streaming into a byte array; these bytes are used by the web
	 * server and added to the http response
	 *  
	 * @return
	 */
	public byte[] getByteArray() {

		if (lastYuvImage == null)
			return null;

		else {
		
			ByteArrayOutputStream stream = new ByteArrayOutputStream();

			Rect rect = new Rect(0, 0, mPreviewWidth, mPreviewHeight);
			lastYuvImage.compressToJpeg(rect, cameraQuality, stream);

			byte[] bytes = null;

			try {
				bytes = stream.toByteArray();
				stream.close();

			} catch (IOException e) {
				e.printStackTrace();
				
			}

			return bytes;

		}
	}
	
	public byte[] getStandByImage() {
		
		// TODO
		int standByID = 0;
		
		Resources resources = this.getResources();
		InputStream image = resources.openRawResource(standByID);
  
        // create a buffer that has the same size as the inputstream  
        byte[] buffer = null;
        
		try {
			
			buffer = new byte[image.available()];

			// read the text file as a stream, into the buffer  
	        image.read(buffer);  
	        
	        // create a output stream to write the buffer into  
	        ByteArrayOutputStream stream = new ByteArrayOutputStream();  

	        // write this buffer to the output stream  
	        stream.write(buffer);  

			byte[] bytes = stream.toByteArray();

	        // close the Input and Output streams  
	        stream.close();  
	        image.close();  
		
	        return bytes;
	        
		} catch (IOException e) {
			e.printStackTrace();

		}  
		
		return null;

	}
	
	public boolean isReady() {
		return this.inPreview;
	}

}
