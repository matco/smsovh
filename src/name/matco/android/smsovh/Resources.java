package name.matco.android.smsovh;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.protocol.HTTP;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

public class Resources {

	public static final boolean DEBUG_MODE = false;
	public static final String UA_NUMBER = "UA-20292205-1";

	private static String OVH_URL = "https://www.ovh.com:1664/";

	public enum LANGUAGE {
		FR {
			@Override
			public String toString() {
				return "fr";
			}
		},
		EN {
			@Override
			public String toString() {
				return "en";
			}
		},
		PL {
			@Override
			public String toString() {
				return "pl";
			}
		},
		DE {
			@Override
			public String toString() {
				return "de";
			}
		};

		public static LANGUAGE getLanguage(Locale locale) {
			for(LANGUAGE language : values()) {
				if(locale.getISO3Language().contains(language.toString())) {
					return language;
				}
			}
			return EN;
		}
	}

	public enum ACTION {
		LOGIN {
			@Override
			public String getActionName() {
				return "login";
			}

			@Override
			public Class<?> getReturnClass() {
				return String.class;
			}

		},
		SMS_ACCOUNT_LIST {
			@Override
			public String getActionName() {
				return "telephonySmsAccountList";
			}

			@Override
			public Class<?> getReturnClass() {
				return String[].class;
			}
		},
		SMS_SENDER_LIST {
			@Override
			public String getActionName() {
				return "telephonySmsSenderList";
			}

			@Override
			public Class<?> getReturnClass() {
				return Object.class;
			}
		},
		SMS_CREDIT_LEFT {
			@Override
			public String getActionName() {
				return "telephonySmsCreditLeft";
			}

			@Override
			public Class<?> getReturnClass() {
				return Integer.class;
			}
		},
		SMS_SEND {
			@Override
			public String getActionName() {
				return "telephonySmsSend";
			}

			@Override
			public Class<?> getReturnClass() {
				return String.class;
			}
		};

		public abstract String getActionName();
		public abstract Class<?> getReturnClass();
	}

	public static String WSDL_HEADER;
	static {
		StringBuffer header = new StringBuffer("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
		header.append("<soap:Envelope soap:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\" xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\" xmlns:soapenc=\"http://schemas.xmlsoap.org/soap/encoding/\" xmlns:xsd=\"http://www.w3.org/1999/XMLSchema\" xmlns:xsi=\"http://www.w3.org/1999/XMLSchema-instance\">");
		header.append("<soap:Body>");
		WSDL_HEADER = header.toString();
	}

	public static String WSDL_FOOTER;
	static {
		StringBuffer footer = new StringBuffer();
		footer.append("</soap:Body>");
		footer.append("</soap:Envelope>");
		WSDL_FOOTER = footer.toString();
	}

	private static String getParameterType(Object parameter) {
		String name = parameter.getClass().getName();
		if("java.lang.String".equals(name)) {
			return "string";
		}
		if("java.lang.Integer".equals(name)) {
			return "int";
		}
		throw new UnsupportedOperationException(String.format("%s is not supported as a parameter", name));
	}

	private static String getRequest(ACTION action, Object... parameters) {
		StringBuffer request = new StringBuffer(WSDL_HEADER);
		request.append("<ns1:");
		request.append(action.getActionName());
		request.append(" soapenc:root=\"1\" xmlns:ns1=\"http://soapi.ovh.com/manager\">");

		int i = 0;
		for(Object parameter : parameters) {
			i++;
			request.append("<v");
			request.append(i);
			request.append(" xsi:type=\"xsd:");
			request.append(getParameterType(parameter));
			request.append("\">");
			request.append(parameter);
			request.append("</v");
			request.append(i);
			request.append(">");
		}

		request.append("</ns1:");
		request.append(action.getActionName());
		request.append(">");
		request.append(WSDL_FOOTER);
		return request.toString();
	}

	public static Object getResponse(Context context, ACTION action, Object... parameters) throws Exception {
		StringEntity stringEntity = new StringEntity(getRequest(action, parameters), HTTP.UTF_8);
		stringEntity.setContentType("text/xml");
		stringEntity.setChunked(false);

		HttpPost httpPost = new HttpPost(OVH_URL);
		httpPost.setEntity(stringEntity);
		httpPost.setHeader("Content-type", "text/xml; charset=\"UTF-8\"");
		httpPost.setHeader("Accept", "text/xml");
		httpPost.setHeader("SOAPAction", String.format("http://soapi.ovh.com/manager#%s", action.getActionName()));

		HttpClient httpClient;
		SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
		httpClient = preferences.getBoolean("do_not_check_certificate", true) ? new CustomHttpClient(context) : new DefaultHttpClient();
		HttpResponse httpResponse = httpClient.execute(httpPost);

		DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
		DocumentBuilder documentBuilder;
		documentBuilder = documentBuilderFactory.newDocumentBuilder();

		InputStream stream = httpResponse.getEntity().getContent();

		/*
		if(action.equals(ACTION.SMS_SENDER_LIST)) {
			System.out.println(getStringFromStream(stream));
		}
		*/

		Document response = null;
		try {
			documentBuilder = documentBuilderFactory.newDocumentBuilder();
			response = documentBuilder.parse(stream);
		} catch (Exception e) {
			throw new Exception(String.format("Unable to parse response : %s", e.getLocalizedMessage()), e);
		}

		Node body = response.getElementsByTagName("soap:Body").item(0);

		//search for error
		if("soap:Fault".equals(body.getFirstChild().getNodeName())) {
			throw new Exception(body.getFirstChild().getChildNodes().item(1).getFirstChild().getNodeValue());
		}

		//get result
		if(action.getReturnClass().equals(String.class)) {
			return body.getFirstChild().getFirstChild().getFirstChild().getNodeValue();
		}
		if(action.getReturnClass().equals(Integer.class)) {
			return Integer.parseInt(body.getFirstChild().getFirstChild().getFirstChild().getNodeValue());
		}
		if(action.getReturnClass().equals(String[].class)) {
			Node node = body.getFirstChild().getFirstChild();
			String[] results = new String[node.getChildNodes().getLength()];
			for(int i = 0; i < node.getChildNodes().getLength(); i++) {
				results[i] = node.getChildNodes().item(i).getFirstChild().getNodeValue();
			}
			return results;
		}
		if(action.getReturnClass().equals(Object.class)) {
			Node node = body.getFirstChild().getFirstChild();
			List<Map<String, String>> results = new ArrayList<Map<String, String>>();
			for(int i = 0; i < node.getChildNodes().getLength(); i++) {
				Map<String, String> result = new LinkedHashMap<String, String>();
				Node item = node.getChildNodes().item(i);
				for(int j = 0; j < item.getChildNodes().getLength(); j++) {
					Node element = item.getChildNodes().item(j);
					String value = element.getChildNodes().getLength() == 0 ? null : element.getFirstChild().getNodeValue();
					result.put(element.getNodeName(), value);
				}
				results.add(result);
			}
			return results;
		}

		throw new Exception(String.format("%s is not supported as a result", action.getReturnClass().getName()));
	}

	public static String getStringFromStream(InputStream stream) throws IOException {
		BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
		StringBuilder sb = new StringBuilder();
		String line = null;
		while ((line = reader.readLine()) != null) {
		  sb.append(line + "\n");
		}
		return sb.toString();
	}

	public static String getTicket(Context context, String login, String password, LANGUAGE language) throws Exception {
		return (String) Resources.getResponse(context, ACTION.LOGIN, login, password, language.toString(), 0);
	}
}
