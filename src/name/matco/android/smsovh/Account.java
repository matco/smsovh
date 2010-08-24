package name.matco.android.smsovh;

import java.util.List;
import java.util.Map;

import name.matco.android.smsovh.Resources.ACTION;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.animation.AnimationUtils;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.ViewFlipper;

import com.google.android.apps.analytics.GoogleAnalyticsTracker;

public class Account extends NetworkObserver {

	private GoogleAnalyticsTracker tracker;

	private String ticket;
	private ProgressDialog dialog;

	private EditText login;
	private EditText password;

	private Spinner account;
	private Spinner from;

	private ViewFlipper flipper;

	private Button validateLogin;
	private Button validateAccount;
	private Button validateFrom;

	private Button backToLogin;
	private Button backToAccount;

	private TextView errorView;

	public final String getTicket() throws Exception {
		if(ticket == null) {
			SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
			if(!preferences.contains("login") || !preferences.contains("password") && preferences.contains("account")) {
				throw new Exception("Login and password are required to get a ticket");
			}
			System.out.println(Resources.LANGUAGE.getLanguage(getResources().getConfiguration().locale));
			ticket = Resources.getTicket(getApplicationContext(), preferences.getString("login", ""), preferences.getString("password", ""), Resources.LANGUAGE.getLanguage(getResources().getConfiguration().locale));
		}
		return ticket;
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.account);

		login = (EditText) findViewById(R.id.login);
		password = (EditText) findViewById(R.id.password);

		account = (Spinner) findViewById(R.id.account);
		from = (Spinner) findViewById(R.id.from);

		errorView = (TextView) findViewById(R.id.account_error);

		flipper = (ViewFlipper) findViewById(R.id.process);

		//buttons
		validateLogin = (Button) findViewById(R.id.validate_login);
		validateAccount = (Button) findViewById(R.id.validate_account);
		validateFrom = (Button) findViewById(R.id.validate_from);

		backToLogin = (Button) findViewById(R.id.back_to_login);
		backToAccount = (Button) findViewById(R.id.back_to_account);

		validateLogin.setOnClickListener(getLoginListener());
		validateAccount.setOnClickListener(getAccountListener());
		validateFrom.setOnClickListener(getFromListener());

		backToLogin.setOnClickListener(getBackToLoginListener());
		backToAccount.setOnClickListener(getBackToAccountListener());

		//start tracker
		tracker = GoogleAnalyticsTracker.getInstance();
		tracker.start(Resources.UA_NUMBER, this);
		tracker.trackPageView("/account");
	}

	private void setFlipperAnimationForPrevious() {
		flipper.setInAnimation(AnimationUtils.loadAnimation(this, android.R.anim.slide_in_left));
		flipper.setOutAnimation(AnimationUtils.loadAnimation(this, android.R.anim.slide_out_right));
	}

	private void setFlipperAnimationForNext() {
		flipper.setInAnimation(AnimationUtils.loadAnimation(this, R.anim.slide_out_left));
		flipper.setOutAnimation(AnimationUtils.loadAnimation(this, R.anim.slide_in_right));
	}

	private class GetAccounts extends AsyncTask<Void, Void, String[]> {
		private String _error;

		@Override
		protected void onPreExecute() {
			dialog = ProgressDialog.show(Account.this, getString(R.string.loading), getString(R.string.wizard_loading_accounts), true);
		}
		@Override
		protected String[] doInBackground(Void... param) {
			try {
				return (String[]) Resources.getResponse(getApplicationContext(), ACTION.SMS_ACCOUNT_LIST, getTicket());
			} catch (Exception e) {
				e.printStackTrace();
				Log.e(getClass().getSimpleName(), "Get accounts", e);
				_error = e.getLocalizedMessage();
				return null;
			}
		}

		@Override
		protected void onPostExecute(String[] accounts) {
			errorView.setVisibility(View.GONE);
			if(_error != null) {
				errorView.setText(_error);
				errorView.setVisibility(View.VISIBLE);
			}
			else if(accounts.length == 0) {
				errorView.setText(getString(R.string.wizard_accounts_error));
				errorView.setVisibility(View.VISIBLE);
			}
			else {
				ArrayAdapter<String> adapter = new ArrayAdapter<String>(Account.this,
						android.R.layout.simple_spinner_item, accounts);
				adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
				account.setAdapter(adapter);

				setFlipperAnimationForNext();
				flipper.showNext();
			}
			dialog.dismiss();
		}
	}

	private class GetFroms extends AsyncTask<String, Void, String[]> {
		private String _error;

		@Override
		protected void onPreExecute() {
			dialog = ProgressDialog.show(Account.this, getString(R.string.loading), getString(R.string.wizard_loading_froms), true);
		}
		@SuppressWarnings("unchecked")
		@Override
		protected String[] doInBackground(String... params) {
			try {
				List<Map<String, String>> results = (List<Map<String, String>>) Resources.getResponse(getApplicationContext(), ACTION.SMS_SENDER_LIST, getTicket(), params[0]);
				String[] froms = new String[results.size()];
				for(int i = 0; i < results.size(); i++) {
					froms[i] = results.get(i).get("number");
				}
				return froms;
			} catch (Exception e) {
				e.printStackTrace();
				Log.e(getClass().getSimpleName(), "Get froms", e);
				_error = e.getLocalizedMessage();
				return null;
			}
		}

		@Override
		protected void onPostExecute(String[] froms) {
			errorView.setVisibility(View.GONE);
			if(_error != null) {
				errorView.setText(_error);
				errorView.setVisibility(View.VISIBLE);
			}
			else if(froms.length == 0) {
				errorView.setText(getString(R.string.wizard_froms_error));
				errorView.setVisibility(View.VISIBLE);
			}
			else {
				ArrayAdapter<String> adapter = new ArrayAdapter<String>(Account.this,
						android.R.layout.simple_spinner_item, froms);
				adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
				from.setAdapter(adapter);

				setFlipperAnimationForNext();
				flipper.showNext();
			}
			dialog.dismiss();
		}
	}

	public final void setCredentials(String login, String password) {
		SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
		Editor e = preferences.edit();
		e.putString("login", login);
		e.putString("password", password);
		e.commit();
	}

	public final void setAccount(String account) {
		SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
		Editor e = preferences.edit();
		e.putString("account", account);
		e.commit();
	}

	public final void setFrom(String from) {
		SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
		Editor e = preferences.edit();
		e.putString("default_from_number", from);
		e.putBoolean("ask_from_number", false);
		e.commit();
	}

	public OnClickListener getLoginListener() {
		return new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				//check fields
				if(login.getText().length() == 0) {
					login.setError(getString(R.string.wizard_login_error));
					return;
				}
				if(password.getText().length() == 0) {
					password.setError(getString(R.string.wizard_password_error));
					return;
				}
				setCredentials(login.getText().toString(), password.getText().toString());

				//retrieve ticket and show accounts;
				new GetAccounts().execute();
			}
		 };
	}

	public OnClickListener getBackToLoginListener() {
		return new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				setFlipperAnimationForPrevious();
				flipper.showPrevious();
			}
		};
	}

	public OnClickListener getAccountListener() {
		return new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				setAccount((String) account.getSelectedItem());

				//retrieve ticket and show froms;
				new GetFroms().execute((String) account.getSelectedItem());
			}
		 };
	}

	public OnClickListener getBackToAccountListener() {
		return new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				setFlipperAnimationForPrevious();
				flipper.showPrevious();
			}
		};
	}

	public OnClickListener getFromListener() {
		return new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				setFrom((String) from.getSelectedItem());
				Intent intent = new Intent(v.getContext(), SMS.class);
				try {
					intent.putExtra("ticket", getTicket());
				} catch (Exception e) {
					e.printStackTrace();
					Log.e(getClass().getSimpleName(), "Retrieve ticket for main activity", e);
				}
				startActivityForResult(intent, 0);
				finish();
			}
		 };
	}

	@Override
	public void onNetworkComeBack() {
		//nothing to do
	}

	@Override
	public void onNetworkAvailable() {
		//nothing to do
	}
}