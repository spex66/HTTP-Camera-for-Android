package de.kp.net.http;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.util.Date;
import java.util.Scanner;

import android.content.Context;
import android.util.Log;
import de.kp.net.HttpCamera;

public class ClientRequestThread extends Thread {

	private Socket client;
	private Context context;
	private String TAG = "ClientRequestThread";

	ClientRequestThread(Socket client, Context context) {

		this.client = client;
		this.context = context;

	}

	public void run() {

		try {

			// Streams to communicate with the client
			BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream()));
			DataOutputStream out = new DataOutputStream(client.getOutputStream());

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

		Log.d(TAG , "sendResponse strated");

		out.writeBytes("HTTP/1.1 " + 200 + " OK\r\n" + "Date: " + new Date().toString() + "\r\n");

		out.writeBytes("HTTP/1.0 200 OK\r\n" +
	      "Server: Android\r\n" +
	      "Connection: close\r\n" +
	      "Max-Age: 0\r\n" +
	      "Expires: 0\r\n" +
	      "Cache-Control: no-cache, private\r\n" + 
	      "Pragma: no-cache\r\n" + 
	      "Content-Type: multipart/x-mixed-replace; " +
	      "boundary=--KruscheUndPartnerPartG\r\n\r\n");
		
		
		out.writeBytes("Content-Type: multipart/x-mixed-replace;boundary=--KruscheUndPartnerPartG\r\n");

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

	private static void sendResponseHeader(DataOutputStream out, int code, String contentType, long contentLength)
			throws IOException {

		StringBuffer sb = new StringBuffer();
		sb.append("HTTP/1.1 " + code + " OK\r\n" + "Date: " + new Date().toString() + "\r\n");

		sb.append("Content-Type: " + contentType + "\r\n");
		sb.append((contentLength != -1) ? "Content-Length: " + contentLength + "\r\n" : "\r\n");

		out.write(sb.toString().getBytes());

	}

	public void writeFile(FileInputStream fis, DataOutputStream out) throws Exception {

		byte[] buffer = new byte[1024];
		int bytesRead;

		while ((bytesRead = fis.read(buffer)) != -1) {
			out.write(buffer, 0, bytesRead);

		}

		fis.close();

	}

	public static String FileExtension(String fileName) {

		String extension = "";
		int pos = fileName.lastIndexOf(".");

		if (pos >= 0) {
			extension = fileName.substring(pos);

		}

		return extension.toLowerCase();

	}

}
