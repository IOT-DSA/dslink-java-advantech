package advantech;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.codec.binary.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Utils {
	private final static Logger LOGGER;
	static {
        LOGGER = LoggerFactory.getLogger(Utils.class);
    }

	public Utils() {
		// TODO Auto-generated constructor stub
	}
	
	public static final String LOGON = "http://{HostIp}/WaWebService/Json/Logon";
	public static final String PROJ_DETAIL = "http://{HostIp}/WaWebService/JSON/ProjectDetail/{ProjectName}";
	public static final String NODE_LIST = "http://{HostIp}/WaWebService/JSON/NodeList/{ProjectName}";
	public static final String NODE_DETAIL = "http://{HostIp}/WaWebService/Json/NodeDetail/{ProjectName}/{NodeName}";
	public static final String PORT_LIST = "http://{HostIp}/WaWebService/JSON/PortList/{ProjectName}/{NodeName}";
	public static final String PORT_DETAIL = "http://{HostIp}/WaWebService/Json/PortDetail/{ProjectName}/{NodeName}/{Comport}";
	public static final String DEVICE_LIST = "http://{HostIp}/WaWebService/JSON/DeviceList/{ProjectName}/{NodeName}/{Comport}";
	public static final String DEVICE_DETAIL = "http://{HostIp}/WaWebService/JSON/DeviceDetail/{ProjectName}/{NodeName}/{Comport}/{DeviceName}";
	public static final String PROJ_TAG_LIST = "http://{HostIp}/WaWebService/Json/TagList/{ProjectName}";
	public static final String NODE_TAG_LIST = "http://{HostIp}/WaWebService/Json/TagList/{ProjectName}/{NodeName}";
	public static final String PORT_TAG_LIST = "http://{HostIp}/WaWebService/Json/TagList/{ProjectName}/{NodeName}/{Comport}";
	public static final String DEVICE_TAG_LIST = "http://{HostIp}/WaWebService/Json/TagList/{ProjectName}/{NodeName}/{Comport}/{DeviceName}";
	public static final String PROJ_TAG_DETAIL = "http://{HostIp}/WaWebService/Json/TagDetail/{ProjectName}";
	public static final String NODE_TAG_DETAIL = "http://{HostIp}/WaWebService/Json/TagDetail/{ProjectName}/{NodeName}";
	public static final String TAG_VALUE = "http://{HostIp}/WaWebService/Json/GetTagValue/{ProjectName}";
	public static final String SET_TAG_VALUE = "http://{HostIp}/WaWebService/Json/SetTagValue/{ProjectName}";
	public static final String SET_TAG_VAUE_TEXT = "http://{HostIp}/WaWebService/Json/SetTagValueText/{ProjectName}";
	
	public static String sendGet(String urlString, Map<String, String> urlParams, String authString) throws ApiException {
		urlString = getUrlString(urlString, urlParams);
		HttpURLConnection connection = null;
		try {
			URL url = new URL(urlString);
			connection = (HttpURLConnection)url.openConnection();
			connection.setRequestMethod("GET");
			connection.setRequestProperty("Authorization", authString);
//			connection.setRequestProperty("Content-Type", "application/json");
			connection.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
			connection.setRequestProperty("Accept-Language", "en-US,en;q=0.5");
			connection.setRequestProperty("Accept-Encoding", "gzip, deflate");
			int code = connection.getResponseCode();
			if (code != 200) {
				throw new ApiException(code, connection.getResponseMessage());
			}
			
			
			BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream(), "UTF-8"));
			String inputLine;
			StringBuffer response = new StringBuffer();

			while ((inputLine = in.readLine()) != null) {
				response.append(inputLine);
			}
			in.close();
			
			return response.toString();
			
		} catch (MalformedURLException e) {
			// TODO Auto-generated catch block
			LOGGER.debug("", e);
			return null;
		} catch (IOException e) {
			// TODO Auto-generated catch block
			LOGGER.debug("", e);
			return null;
		} finally {
			if(connection != null) connection.disconnect(); 
		}
	   
	}
	
	public static String sendPost(String urlString, Map<String, String> urlParams, String authString, String jsonString) throws ApiException {
		urlString = getUrlString(urlString, urlParams);
		HttpURLConnection connection = null;
		try {
			URL url = new URL(urlString);
			connection = (HttpURLConnection)url.openConnection();
			connection.setRequestMethod("POST");
			connection.setRequestProperty("Authorization", authString);
			connection.setRequestProperty("Content-Type", "application/json");
			connection.setRequestProperty("Accept", "application/json");
			connection.setDoInput(true);
			connection.setDoOutput(true);
			
			OutputStreamWriter wr = new OutputStreamWriter(connection.getOutputStream(), "UTF-8");
			wr.write(jsonString);
			wr.flush();
			wr.close();
			
			int code = connection.getResponseCode();
			if (code != 200) {
				throw new ApiException(code, connection.getResponseMessage());
			}
			
			BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream(), "UTF-8"));
			String inputLine;
			StringBuffer response = new StringBuffer();

			while ((inputLine = in.readLine()) != null) {
				response.append(inputLine);
			}
			in.close();
			
			return response.toString();
			
		} catch (MalformedURLException e) {
			// TODO Auto-generated catch block
			LOGGER.debug("", e);
			return null;
		} catch (IOException e) {
			// TODO Auto-generated catch block
			LOGGER.debug("", e);
			return null;
		}
		
	}
	
	public static String encodeAuth(String user, String pass) {
		String toEncode = user + ":" + pass;
		byte[] binaryData;
		try {
			binaryData = toEncode.getBytes("UTF-8");
		} catch (UnsupportedEncodingException e1) {
			binaryData = toEncode.getBytes(Charset.defaultCharset());
		}
		String encoded = Base64.encodeBase64URLSafeString(binaryData);
		//			return URLEncoder.encode("Basic " + encoded, "UTF-8");
		return "Basic "+encoded;
	}
	
	public static class ApiException extends Exception {
		int code = 0;
		public ApiException(String message) {
		    super(message);
		}
		public ApiException(int code, String message) {
			super("Response code "+ code+ " :" + message);
			this.code = code;
		}
	}
	
	private static String getUrlString(String urlTemplate, Map<String, String> urlParams) {
		String urlString = urlTemplate;
		if (urlParams != null) {
			for (Entry<String, String> entry: urlParams.entrySet()) {
				urlString = urlString.replaceAll("\\{"+entry.getKey()+"\\}", entry.getValue());
			}
		}
		return urlString;
	}

}
