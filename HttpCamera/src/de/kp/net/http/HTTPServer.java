package de.kp.net.http;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.Date;
import java.util.Scanner;
import java.util.Vector;

import android.content.Context;
import android.util.Log;
import de.kp.httpcamera.HttpCamera;

public class HTTPServer implements Runnable {
	
	private static int SERVER_PORT = 8080;

	// indicator to determine whether the
	// server has stopped or not
	private boolean stopped = false;

	private ServerSocket serverSocket;

	private Context context;

	private String TAG = "HttpServerThread";

	private Vector<Thread> serverThreads;

	private boolean terminated;
	
	public HTTPServer(Context context) throws IOException {		
		this(context, SERVER_PORT);
	}

	public HTTPServer(Context context, int port) throws IOException {		

		this.context = context;
		
		this.serverThreads = new Vector<Thread>();
		this.serverSocket = new ServerSocket(port);

	}

	public void run() {

		Log.d(TAG  , "server started");
		while (this.stopped == false) {

			try {
				Socket clientSocket = this.serverSocket.accept();
				serverThreads.add(new ServerThread(clientSocket, context));
				
			} catch (SocketException e) {
				if (this.stopped) 
					break;
				e.printStackTrace();
				
			} catch (IOException e) {
				e.printStackTrace();
			} 
		}
		
		Log.d(TAG, "server stopped");

	}
	
	private void terminate() {

		for (Thread serverThread : serverThreads) {
			if (serverThread.isAlive()) serverThread.interrupt();
		}
		
		this.terminated = true;

	}

	public boolean isTerminated() {
		return this.terminated;
	}
	
	/**
	 * This method is used to stop the HTTP server
	 */
	public void stop() {

		this.stopped = true;		
		terminate();

		try {
			serverSocket.close();
		
		} catch (IOException e) {	
			// nothing todo
		}
		
	}
		
	private class ServerThread extends Thread {
		
		private String TAG = "HttpServer";

	    private Socket clientSocket;
		private Context context;

		public ServerThread(Socket socket, Context context) {
	    	
	    	this.clientSocket = socket;
	    	this.context = context;
	    	
	    	start();
	    
	    }

		public void run() {

			try {

				// Streams to communicate with the client
				BufferedReader requestStream = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
				DataOutputStream responseStream = new DataOutputStream(clientSocket.getOutputStream());

				// Read the HTTP request from the client
				String requestLine = requestStream.readLine();

				Scanner sc = new Scanner(requestLine);

				String method = sc.next();
				String requestFileName = sc.next();

				/*
				 * This HTTP server is dedicated to exclusively support Motion JPEG
				 * streaming. The user may request a generic answer from the server
				 * by NOT providing a request file name; otherwise always the streaming
				 * result of the camera is sent.
				 * 
				 */
				
				if (method.equals("GET")) {

					if (requestFileName.equals("/")) {

						Log.d(TAG , "sendDefaultResponsePage:" + requestLine);

						// The default home page
						sendDefaultResponsePage(responseStream);

					} else {

						Log.d(TAG , "sendResponse:" + requestLine);
						sendResponse(responseStream);

					}

				} else
					sendErrorResponse(responseStream, 500, "<b>Method  not found</b>");

			} catch (Exception e) {
				e.printStackTrace();

			} finally {

				if (this.clientSocket != null) {
					
					try {
						this.clientSocket.close();
				
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
					
				}
			}
			
			
		}

		public void sendResponse(DataOutputStream responseStream) throws Exception {
		
			if (((HttpCamera) context).isReady()) {
				sendMJPEGResponse(responseStream);
			
			} else {

				// the camera is actually not ready; in this case, we send
				// a standby picture
				sendStandByResponse(responseStream);
			
			}
		
		}
		
		/**
		 * This method sends the content of a file to the requestor.
		 * 
		 * @param responseStream
		 * @param statusCode
		 * @param fileName
		 * @param contentTypes
		 * @throws Exception 
		 */
		public void sendMJPEGResponse(DataOutputStream responseStream) throws Exception {
			
			Log.d(TAG , "sendMJPEGResponse started");
			
			// build header
			StringBuffer sb = new StringBuffer();

			sb.append("HTTP/1.0 200 OK\r\n");
			sb.append("Server: Android\r\n");
			
			sb.append("Connection: close\r\n");
			sb.append("Max-Age: 0\r\n");
			
			sb.append("Expires: 0\r\n");
			sb.append("Cache-Control: no-cache, private\r\n");
			
			sb.append("Pragma: no-cache\r\n");
			
			sb.append("Content-Type: multipart/x-mixed-replace; ");
			sb.append("boundary=--KruscheUndPartnerPartG\r\n\r\n");

			responseStream.write(sb.toString().getBytes());

			byte[] image;
			while (true) {
			
				image = ((HttpCamera) context).getByteArray();

				if (image == null) {
					Log.d(TAG , "sendResponse standby picture");
					image = ((HttpCamera) context).getStandByImage();
				}

				Log.d(TAG , "sendResponse streaming");
				
				imageToResponse(responseStream, image);
				
				try {
					sleep(20);
				
				} catch (InterruptedException e) {
					
					Log.d(TAG , "sendResponse last picture");
					image = ((HttpCamera) context).getFinalImage();
					
					// finally send ten last pictures
					for(int i=0; i < 10; i++)
						imageToResponse(responseStream, image);
					
					break;

				}
			}

		}

		private void imageToResponse(DataOutputStream responseStream, byte[] image) throws IOException {

			responseStream.writeBytes("--KruscheUndPartnerPartG\r\n");
			responseStream.writeBytes("Content-Type: image/jpeg\r\n\r\n");
			
			responseStream.write(image);
			
			responseStream.writeBytes("\r\n\r\n");				
			responseStream.flush();
		}

		/**
		 * This method returns a standby image in case
		 * of camera is not ready for streaming.
		 * 
		 * @param responseStream
		 * @throws Exception
		 */
		public void sendStandByResponse(DataOutputStream responseStream) throws Exception {
			
			Log.d(TAG , "sendStandByResponse started");

			byte[] image = ((HttpCamera) context).getStandByImage();
			int len = (image == null) ? 0 : image.length;
			
			// build header
			sendResponseHeader(responseStream, 200, "image/jpeg", len);

			responseStream.write(image);

			responseStream.flush();
			responseStream.close();

		}
		
		/**
		 * @param out
		 * @param statusCode
		 * @param message
		 * @throws IOException
		 */
		private void sendErrorResponse(DataOutputStream out, int statusCode, String message) throws IOException {
			out.write(("HTTP/1.1 " + statusCode + " " + message + "\r\n").getBytes());
			out.close();
		}

		private void sendDefaultResponsePage(DataOutputStream out) throws IOException {

			StringBuffer sb = new StringBuffer();
			sb.append("<html><body>");
			sb.append("<h1><div align='center'><b>Android Http Server Version 1.0 </b></div></h1>");
			sb.append("<h2><div align='center'><b>Welcome to Android Webserver built in java </b></div></h2>");
			sb.append("</body></html>");

			sendResponseHeader(out, 200, "text/html", sb.length());

			out.write(sb.toString().getBytes());
			out.close();

		}

		private void sendResponseHeader(DataOutputStream out, int code, String contentType, long contentLength) throws IOException {

			StringBuffer sb = new StringBuffer();
			sb.append("HTTP/1.1 " + code + " OK\r\n" + "Date: " + new Date().toString() + "\r\n");

			sb.append("Content-Type: " + contentType + "\r\n");
			sb.append((contentLength != -1) ? "Content-Length: " + contentLength + "\r\n\r\n" : "\r\n");

			out.write(sb.toString().getBytes());

		}

	}

}