package yo.applab.ckwinfo;

import java.io.IOException;
import java.net.URI;

import org.apache.http.client.HttpClient;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;

/**
 * Connects to network resource and processes HTTP response  
 */
public class DownloadManager {
	
	public DownloadManager() {

	}

	public static String retrieveData(URI uri) {
		HttpClient client = null;
		try {
			ResponseHandler<String> responseHandler = new BasicResponseHandler();
			HttpParams httpParameters = new BasicHttpParams();
			HttpConnectionParams.setSoTimeout(httpParameters, Global.TIMEOUT);
			HttpConnectionParams.setConnectionTimeout(httpParameters,
					Global.TIMEOUT);
			client = new DefaultHttpClient(httpParameters);
			HttpGet getMethod = new HttpGet(uri);
			return client.execute(getMethod, responseHandler);
		} catch (IOException e) {
			return null;
		} finally {
			//Dealocate all system resources
			client.getConnectionManager().shutdown();
		}
	}
}
