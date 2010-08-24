package name.matco.android.smsovh;

import java.util.List;
import java.util.Map;

import name.matco.android.smsovh.Resources.ACTION;
import android.app.ProgressDialog;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.util.Log;

import com.google.android.apps.analytics.GoogleAnalyticsTracker;

public class Preferences extends PreferenceActivity {

	private ProgressDialog dialog;
	private ListPreference from;
	private Preference askFrom;

	private GoogleAnalyticsTracker tracker;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		addPreferencesFromResource(R.layout.preferences);

		from = (ListPreference) findPreference("default_from_number");
		askFrom = findPreference("ask_from_number");

		new GetFroms().execute();

		// start tracker
		tracker = GoogleAnalyticsTracker.getInstance();
		tracker.start(Resources.UA_NUMBER, this);
		tracker.trackPageView("/preferences");
	}

	private class GetFroms extends AsyncTask<Void, Void, String[]> {
		SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getBaseContext());

		@Override
		protected void onPreExecute() {
			dialog = ProgressDialog.show(Preferences.this, getString(R.string.loading), getString(R.string.wizard_loading_froms), true);
		}
		@SuppressWarnings("unchecked")
		@Override
		protected String[] doInBackground(Void... params) {
			try {
				String ticket = Resources.getTicket(getApplicationContext(), preferences.getString("login", ""), preferences.getString("password", ""), Resources.LANGUAGE.getLanguage(getResources().getConfiguration().locale));
				List<Map<String, String>> results = (List<Map<String, String>>) Resources.getResponse(getApplicationContext(), ACTION.SMS_SENDER_LIST, ticket, preferences.getString("account", ""));
				String[] froms = new String[results.size()];
				for(int i = 0; i < results.size(); i++) {
					froms[i] = results.get(i).get("number");
				}
				return froms;
			} catch (Exception e) {
				e.printStackTrace();
				Log.e(getClass().getSimpleName(), "Get froms", e);
				return null;
			}
		}

		@Override
		protected void onPostExecute(String[] froms) {
			if(froms.length > 1) {
				from.setEntries(froms);
				from.setEntryValues(froms);
				from.setDefaultValue(preferences.getString("default_from_number", ""));
			}
			else {
				askFrom.setEnabled(false);
				from.setEnabled(false);
			}
			dialog.dismiss();
		}
	}
}
