package de.kp.net.http;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Date;
import java.util.Scanner;

import de.kp.httpcamera.HttpCamera;

import android.content.Context;
import android.util.Log;

public class HTTPServer implements Runnable {

	private static String SERVER_PORT = "8080";

	// indicator to determine whether the
	// server has stopped or not
	private boolean stopped = false;

	private ServerSocket serverSocket;

	private Context context;

	public HTTPServer(Context context) throws IOException {
		
		this.context = context;

		int port = Integer.parseInt(SERVER_PORT);
		serverSocket = new ServerSocket(port);
	
	}

	public void run() {

		while (this.stopped == false) {

			try {

				Socket  clientSocket = this.serverSocket.accept();
		    	new ServerThread(clientSocket, context);
				
			} catch (IOException e) {
				e.printStackTrace();
			} 
		}

	}
	

	/**
	 * This method is used to stop the HTTP server
	 */
	public void stop() {
		this.stopped = true;
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
				BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
				DataOutputStream out = new DataOutputStream(clientSocket.getOutputStream());

				// Read the HTTP request from the client
				String requestLine = in.readLine();

				Scanner sc = new Scanner(requestLine);

				String method = sc.next();
				String requestFileName = sc.next();

				if (method.equals("GET")) {

					if (requestFileName.equals("/")) {

						// The default home page
						sendDefaultResponsePage(out);

						// to open any default file like index.html , call
						// sendResponse(out, 200, "index.html",this.ctMap);
						out.close();

					} else {

						// removes / from the file name
						sendResponse(out);

					}

				} else
					sendErrorResponse(out, 500, "<b>Method  not found</b>");

			} catch (Exception e) {
				e.printStackTrace();

			}
		}

		/**
		 * This method sends the content of a file to the requestor.
		 * 
		 * @param out
		 * @param statusCode
		 * @param fileName
		 * @param contentTypes
		 * @throws Exception
		 */
		public void sendResponse(DataOutputStream out) throws Exception {

			Log.d(TAG , "sendResponse started");
			
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

			out.write(sb.toString().getBytes());

			while (true) {
				byte[] image = ((HttpCamera) context).getByteArray();

				if (image == null)
					break;
				else {
					Log.d(TAG , "sendResponse streaming");
					
					out.writeBytes("--KruscheUndPartnerPartG\r\n");
					out.writeBytes("Content-Type: image/jpeg\r\n\r\n");
					out.write(image);
					out.writeBytes("\r\n\r\n");				
					out.flush();
				}
			}
			Log.d(TAG , "sendResponse stopped");
			out.close();

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
			sb.append("<h1><div align='center'><b>Java Http Server Version 1.0 </b></div></h1>");
			sb.append("<h2><div align='center'><b>Welcome to Own Webserver  built in java </b></div></h2>");

			sendResponseHeader(out, 200, "text/html", sb.length());

			out.write(sb.toString().getBytes());
			out.close();

		}

		private void sendResponseHeader(DataOutputStream out, int code, String contentType, long contentLength) throws IOException {

			StringBuffer sb = new StringBuffer();
			sb.append("HTTP/1.1 " + code + " OK\r\n" + "Date: " + new Date().toString() + "\r\n");

			sb.append("Content-Type: " + contentType + "\r\n");
			sb.append((contentLength != -1) ? "Content-Length: " + contentLength + "\r\n" : "\r\n");

			out.write(sb.toString().getBytes());

		}

	}

}