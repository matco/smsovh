package name.matco.android.smsovh;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.ConnectTimeoutException;
import org.apache.http.conn.scheme.LayeredSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.scheme.SocketFactory;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.SingleClientConnManager;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;

import android.content.Context;

public class CustomHttpClient extends DefaultHttpClient {

	final Context context;

	public CustomHttpClient(Context context) {
		this.context = context;
	}

	@Override
	protected ClientConnectionManager createClientConnectionManager() {
		SchemeRegistry registry = new SchemeRegistry();
		//registry.register(new Scheme("http", PlainSocketFactory.getSocketFactory(), 80));
		//registry.register(new Scheme("https", getSslSocketFactory(), 443));
		registry.register(new Scheme("https", new FakeSSLSocketFactory(), 1664));
		return new SingleClientConnManager(getParams(), registry);
	}

	public SSLSocketFactory getGoodSocketFactory() {
		try {
			KeyStore trusted = KeyStore.getInstance("BKS");
			InputStream in = context.getResources().openRawResource(R.raw.keystore);
			try {
				trusted.load(in, "smsovh".toCharArray());
			} finally {
				in.close();
			}
			SSLSocketFactory sf = new SSLSocketFactory(trusted);
			sf.setHostnameVerifier(SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER);
			return sf;
		} catch (Exception e) {
			throw new AssertionError(e);
		}
	}

	public class FakeSSLSocketFactory implements SocketFactory, LayeredSocketFactory {

		private SSLContext _sslcontext = null;

		private SSLContext getSSLContext() throws IOException {
			if (_sslcontext == null) {
				try {
					SSLContext context = SSLContext.getInstance("TLS");
					context.init(null, new TrustManager[] {new TrivialTrustManager()}, null);
					return context;
				} catch (Exception e) {
					throw new IOException(e.getMessage());
				}
			}
			return _sslcontext;
		}

		@Override
		public Socket connectSocket(Socket sock, String host, int port, InetAddress localAddress, int localPort, HttpParams params) throws IOException, UnknownHostException, ConnectTimeoutException {
			InetSocketAddress remoteAddress = new InetSocketAddress(host, port);
			SSLSocket sslsock = (SSLSocket) ((sock != null) ? sock : createSocket());

			InetSocketAddress isa = new InetSocketAddress(localAddress, localPort);
			sslsock.bind(isa);
			sslsock.connect(remoteAddress, HttpConnectionParams.getConnectionTimeout(params));
			sslsock.setSoTimeout(HttpConnectionParams.getSoTimeout(params));
			return sslsock;
		}

		@Override
		public Socket createSocket() throws IOException {
			return getSSLContext().getSocketFactory().createSocket();
		}

		@Override
		public Socket createSocket(Socket socket, String host, int port, boolean autoClose) throws IOException, UnknownHostException {
			return getSSLContext().getSocketFactory().createSocket(socket, host, port, autoClose);
		}

		@Override
		public boolean isSecure(Socket socket) throws IllegalArgumentException {
			return true;
		}
	}

	public static class TrivialTrustManager implements X509TrustManager {
		@Override
		public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {}

		@Override
		public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {}

		@Override
		public X509Certificate[] getAcceptedIssuers() {
			return new X509Certificate[0];
		}
	}
}
