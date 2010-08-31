package applab.search.client;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;

import android.util.Log;

/**
 * Connects to a network resource and processes HTTP response
 */
public class DownloadManager {
	/** for debugging purposes in adb logcat */
	private static final String LOG_TAG = "DownloadManager";

	public DownloadManager() {

	}

	/**
	 * obtains the response body string
	 * 
	 * @param url
	 *            the resource URL
	 * @return A string holding the response body or null on failure
	 */
	public static String retrieveData(URL url) {
		InputStream inputStream = null;
		HttpURLConnection httpURLConnection = null;
		StringBuffer stringBuffer = new StringBuffer();
		try {
			Log.i(LOG_TAG, "Connecting to: " + url.toString());

			httpURLConnection = openHttpConnection(url);
			if (httpURLConnection != null) {
				inputStream = httpURLConnection.getInputStream();
				int byteIntegerValue = 0;
				if (inputStream != null) {
					Log.i(LOG_TAG, "Reading socket...");
					while ((byteIntegerValue = inputStream.read()) != -1) {
						stringBuffer.append((char) byteIntegerValue);
					}
					inputStream.close();
					return stringBuffer.toString();
				}
			} else {
				throw new IOException("Null http connection object");
			}
		} catch (IOException e) {
			Log.e(LOG_TAG, "Exception: " + e.toString());
		} finally {
			if (httpURLConnection != null) {
				httpURLConnection.disconnect();
			}
		}
		return null;
	}

	/**
	 * 
	 * @param url
	 *            the resource URL
	 * @return a HTTP connection object
	 * @throws IOException
	 */
	private static HttpURLConnection openHttpConnection(URL url)
			throws IOException {
		HttpURLConnection httpURLConnection;
		int response = -1;
		URLConnection connection = url.openConnection();
		if (!(connection instanceof HttpURLConnection)) {
			throw new IOException("Not an HTTP connection");
		}

		try {
			httpURLConnection = (HttpURLConnection) connection;
			httpURLConnection.setAllowUserInteraction(false);
			httpURLConnection.setInstanceFollowRedirects(true);
			httpURLConnection.setConnectTimeout(Global.TIMEOUT);
			httpURLConnection.setReadTimeout(Global.TIMEOUT);
			httpURLConnection.setRequestMethod("GET");
			httpURLConnection.connect();
			response = httpURLConnection.getResponseCode();
			int responseContentLength = httpURLConnection.getContentLength();
			Log.i(LOG_TAG, "SIZE: " + responseContentLength);

			Log.i(LOG_TAG, "RESPONSE CODE: " + Integer.toString(response));
			if (response == HttpURLConnection.HTTP_OK) {
				return httpURLConnection;
			}

		} catch (Exception ex) {
			Log.e(LOG_TAG, "Exception: " + ex.toString());
			throw new IOException(ex.toString());
		}
		return null;
	}
}
