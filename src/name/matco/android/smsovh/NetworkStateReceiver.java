package name.matco.android.smsovh;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class NetworkStateReceiver extends BroadcastReceiver {

	private NetworkObserver activity;

	public NetworkStateReceiver(NetworkObserver observer) {
		this.activity = observer;
	}

	@Override
	public void onReceive(Context context, Intent intent) {
		Log.i(getClass().getSimpleName(), "Network changed");
		activity.checkConnectivity();
	}
}
