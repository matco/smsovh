package name.matco.android.smsovh;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;

public abstract class NetworkObserver extends Activity {

	private static final int DIALOG_NETWORK_ERROR = 0;

	private NetworkStateReceiver networkStateReceiver;
	private Dialog networkAlert;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		//register listener
		networkStateReceiver = new NetworkStateReceiver(this);
		registerReceiver(networkStateReceiver, new IntentFilter(android.net.ConnectivityManager.CONNECTIVITY_ACTION));

		//create alert
		networkAlert = new Dialog(this);
		networkAlert.setContentView(R.layout.network_alert);
		networkAlert.setTitle(R.string.network_error_title);
		networkAlert.setCancelable(false);

		Button retry = (Button) networkAlert.findViewById(R.id.network_retry);
		Button exit = (Button) networkAlert.findViewById(R.id.network_exit);

		retry.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				networkAlert.dismiss();
				checkConnectivity();
			}
		});
		exit.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				networkAlert.dismiss();
				finish();
			}
		});
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		if(networkStateReceiver != null) {
			unregisterReceiver(networkStateReceiver);
		}
	}

	@Override
	public void onResume() {
		super.onResume();
		checkConnectivity();
	}

	public abstract void onNetworkComeBack();
	public abstract void onNetworkAvailable();

	public boolean checkConnectivity() {
		Log.i(getClass().getSimpleName(), "Checking connectivity");
		ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
		//no connectivity
		if (connectivityManager.getActiveNetworkInfo() == null || !connectivityManager.getActiveNetworkInfo().isConnected()) {
			if (!networkAlert.isShowing()) {
				showDialog(DIALOG_NETWORK_ERROR);
			}
			return false;
		}
		//connectivity came back
		if (networkAlert.isShowing()) {
			networkAlert.dismiss();
			onNetworkComeBack();
			return true;
		}
		//connectivity available
		onNetworkAvailable();
		return true;
	}

	@Override
	protected final Dialog onCreateDialog(int id) {
		switch(id) {
		case DIALOG_NETWORK_ERROR:
			return networkAlert;
		default:
			return null;
		}
	}
}
