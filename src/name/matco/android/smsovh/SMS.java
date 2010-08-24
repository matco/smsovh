package name.matco.android.smsovh;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import name.matco.android.smsovh.Resources.ACTION;
import android.app.ProgressDialog;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.BaseColumns;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.Contacts;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup.LayoutParams;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FilterQueryProvider;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.SimpleCursorAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.apps.analytics.GoogleAnalyticsTracker;

public class SMS extends NetworkObserver {

	private static final int SMS_LENGTH = 160;

	private GoogleAnalyticsTracker tracker;

	private String ticket;
	private Integer creditLeft;
	private String[] froms;

	private SimpleCursorAdapter adapter;
	private MatrixCursor cursor;

	private ProgressDialog dialog;

	private TextView creditLeftText;
	private TextView smsSizeText;
	private TextView errorView;

	private Spinner from;
	private LinearLayout recipientsContainer;
	private List<LinearLayout> recipients = new ArrayList<LinearLayout>();
	private AutoCompleteTextView to;
	private EditText message;

	private Button addTo;
	private Button send;

	private boolean getSaveSMS() {
		 SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
		 return preferences.getBoolean("save_sent_sms", false);
	}

	private String getDefaultCallingCode() {
		SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
		return preferences.getString("default_calling_code", "33");
	}

	public boolean hasValidSettings() {
		SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
		return preferences.contains("login") && preferences.getString("login", "").trim().length() > 0 &&
			preferences.contains("password") && preferences.getString("password", "").trim().length() > 0 &&
			preferences.contains("account") && preferences.getString("account", "").trim().length() > 0 &&
			preferences.contains("default_from_number") && preferences.getString("default_from_number", "").trim().length() > 0;
	}

	private String getPhoneType(int type) {
		switch (type) {
		case Phone.TYPE_HOME:
			return getString(R.string.phone_type_home);
		case Phone.TYPE_MOBILE:
			return getString(R.string.phone_type_mobile);
		case Phone.TYPE_WORK:
			return getString(R.string.phone_type_work);
		default:
			return "";
		}
	}

	private final String getTicket() throws Exception {
		if(ticket == null) {
			//try to recover ticket if user just configure its account
			if(getIntent().getExtras() != null && getIntent().getExtras().containsKey("ticket")) {
				ticket = getIntent().getExtras().getString("ticket");
			}
			//retrieve a new ticket
			else {
				SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
				ticket = Resources.getTicket(getApplicationContext(), preferences.getString("login", ""), preferences.getString("password", ""), Resources.LANGUAGE.getLanguage(getResources().getConfiguration().locale));
			}
		}
		return ticket;
	}

	private SimpleCursorAdapter getAdapter() {
		if(adapter == null) {
			adapter = new SimpleCursorAdapter(this,
					R.layout.contact_list, getCursor(null),
					new String[] {"name", "phone", "type"},
					new int[] {R.id.name_entry, R.id.phone_entry, R.id.phone_type_entry});
			adapter.setStringConversionColumn(1);
			adapter.setCursorToStringConverter(
				new SimpleCursorAdapter.CursorToStringConverter() {
					@Override
					public CharSequence convertToString(Cursor cursor) {
						return cursor.getString(2);
					}
				});
			adapter.setFilterQueryProvider(
				new FilterQueryProvider() {
					@Override
					public Cursor runQuery(CharSequence constraint) {
						return getCursor(constraint);
				}
			});
		}
		return adapter;
	}

	private Cursor getCursor(CharSequence constraint) {
		String selection = null;
		String[] selectionArgs = null;
		if (constraint != null && constraint.length() > 0) {
			selection = String.format("UPPER(%s) GLOB ?", Contacts.DISPLAY_NAME);
			selectionArgs = new String[] {String.format("*%s*", constraint.toString().toUpperCase())};
		}

		Cursor contactCursor = managedQuery(Contacts.CONTENT_URI, new String[] {BaseColumns._ID, Contacts.DISPLAY_NAME}, selection, selectionArgs, BaseColumns._ID + " ASC");
		Cursor phoneCursor = managedQuery(Phone.CONTENT_URI, new String[] {Phone.CONTACT_ID, Phone.NUMBER, Phone.TYPE}, selection, selectionArgs, Phone.CONTACT_ID + " ASC");

		cursor = new MatrixCursor(new String[] {"_id", "name", "phone", "type"});

		if(contactCursor.moveToFirst() && phoneCursor.moveToFirst()) {
			do {
				do {
					//System.out.println(contactCursor.getInt(0) + " - " + phoneCursor.getInt(0) + " - " + contactCursor.getString(1) + " - " + phoneCursor.getString(1));
					if(phoneCursor.getInt(0) == contactCursor.getInt(0)) {
						//add row
						String id = contactCursor.getString(0);
						String name = contactCursor.getString(1);
						String phone = phoneCursor.getString(1);
						String type = getPhoneType(phoneCursor.getInt(2));
						cursor.addRow(new String[] { id, name, phone, type });
					}
				}
				while(!phoneCursor.isLast() && phoneCursor.getInt(0) <= contactCursor.getInt(0) && phoneCursor.moveToNext());
			}
			while(!contactCursor.isLast() && contactCursor.moveToNext());
		}

		contactCursor.close();
		phoneCursor.close();

		startManagingCursor(cursor);
		return cursor;
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		//load settings
		if(!hasValidSettings()) {
			Intent intent = new Intent(getBaseContext(), Account.class);
			startActivityForResult(intent, 0);
			finish();
			return;
		}

		//load sms
		setContentView(R.layout.sms);

		errorView = (TextView) findViewById(R.id.sms_error);
		creditLeftText = (TextView) findViewById(R.id.credit_left);
		smsSizeText = (TextView) findViewById(R.id.characters_sms_counter);

		recipientsContainer = (LinearLayout) findViewById(R.id.layout_recipients);

		send = (Button) findViewById(R.id.send);
		send.setOnClickListener(getSendListener());

		addTo = (Button) findViewById(R.id.add_to);
		addTo.setOnClickListener(getAddToListener());

		to = (AutoCompleteTextView) findViewById(R.id.to);
		to.setAdapter(getAdapter());

		message = (EditText) findViewById(R.id.message);
		message.addTextChangedListener(new TextWatcher() {
			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {
				showSMSCount();
			}
			@Override
			public void beforeTextChanged(CharSequence s, int start, int count, int after) {
			}
			@Override
			public void afterTextChanged(Editable s) {
			}
		});

		from = (Spinner) findViewById(R.id.from_number);
		SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
		if(preferences.getBoolean("ask_from_number", false)) {
			from.setVisibility(View.VISIBLE);
			new GetFroms().execute();
		}
		else {
			from.setVisibility(View.GONE);
		}

		//start tracker
		tracker = GoogleAnalyticsTracker.getInstance();
		tracker.start(Resources.UA_NUMBER, this);
		tracker.trackPageView("/send");
	}

	@Override
	public void onNetworkAvailable() {
		if(hasValidSettings() && creditLeft == null) {
			showCredit();
		}
	}

	@Override
	public void onNetworkComeBack() {
		onNetworkAvailable();
	}

	@Override
	public void onSaveInstanceState(Bundle savedInstanceState) {
		super.onSaveInstanceState(savedInstanceState);
		//ticket and credit
		if(ticket != null) {
			savedInstanceState.putString("ticket", ticket);
		}
		if(creditLeft != null) {
			savedInstanceState.putInt("credit", creditLeft);
		}
		//from
		if(from != null) {
			savedInstanceState.putInt("from", from.getSelectedItemPosition());
			savedInstanceState.putStringArray("froms", froms);
		}
		//main recipient and message
		if(message != null && to != null) {
			savedInstanceState.putString("message", message.getText().toString());
			savedInstanceState.putString("to", to.getText().toString());
		}
		//other recipients
		if(!recipients.isEmpty()) {
			savedInstanceState.putStringArrayList("recipients", new ArrayList<String>(getRecipients().subList(0, getRecipients().size() - 1)));
		}
		//clean handles
		adapter = null;
		cursor.close();
		cursor = null;
	}

	@Override
	public void onRestoreInstanceState(Bundle savedInstanceState) {
		super.onRestoreInstanceState(savedInstanceState);
		//ticket and credit
		if(savedInstanceState.containsKey("ticket")) {
			ticket = savedInstanceState.getString("ticket");
		}
		if(savedInstanceState.containsKey("credit")) {
			creditLeft = savedInstanceState.getInt("credit");
		}
		//from
		if(savedInstanceState.containsKey("from")) {
			from.setSelection(savedInstanceState.getInt("from"));
			froms = savedInstanceState.getStringArray("froms");
			updateFrom();
		}
		//main recipient and message
		if(message != null && to != null) {
			message.setText(savedInstanceState.getString("message"));
			to.setText(savedInstanceState.getString("to"));
		}
		//other recipients
		if(recipientsContainer != null && savedInstanceState.containsKey("recipients")) {
			for(String recipient : savedInstanceState.getStringArrayList("recipients")) {
				LinearLayout container = addRecipient();
				((AutoCompleteTextView) container.getChildAt(0)).setText(recipient);
			}
		}
	}

	 private class GetCreditLeft extends AsyncTask<Void, Void, Void> {
		private String _error;

		@Override
		protected void onPreExecute() {
			dialog = ProgressDialog.show(SMS.this, getString(R.string.loading), getString(R.string.loading_ticket), true);
		}
		@Override
		protected Void doInBackground(Void... param) {
			try {
				SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
				creditLeft = (Integer) Resources.getResponse(getApplicationContext(), ACTION.SMS_CREDIT_LEFT, getTicket(), preferences.getString("account", ""));
				return null;
			} catch (Exception e) {
				e.printStackTrace();
				Log.e(getClass().getSimpleName(), "Get credit left", e);
				_error = e.getLocalizedMessage();
				return null;
			}
		}

		@Override
		protected void onPostExecute(Void param) {
			//hack
			if(errorView != null) {
				errorView.setVisibility(View.GONE);
				dialog.dismiss();
				if(_error != null) {
					errorView.setText(_error);
					errorView.setVisibility(View.VISIBLE);
				}
				else {
					showCredit();
				}
			}
		}
	}

	public void showCredit() {
		//already showing credit
		if(dialog != null && dialog.isShowing()) {
			return;
		}
		if(creditLeft == null) {
			new GetCreditLeft().execute();
		}
		else {
			if(creditLeft <= 0) {
				errorView.setText(getString(R.string.no_credit_left));
				errorView.setVisibility(View.VISIBLE);
				send.setEnabled(false);
				addTo.setEnabled(false);
			}
			else {
				//show credit left
				creditLeftText.setText(String.format(getString(R.string.credit_left), creditLeft));
				send.setEnabled(true);
			}
		}
	}

	public void showSMSCount() {
		if(!TextUtils.isEmpty(message.getText())) {
			String counter;
			if(recipients.isEmpty()) {
				counter = String.format(getString(R.string.sms_count), message.getText().length(), getRequiredSMSNumber());
			}
			else {
				counter = String.format(getString(R.string.multiple_sms_count), message.getText().length(), recipients.size() + 1, getRequiredSMSNumber());
			}
			smsSizeText.setText(counter);
		}
		else {
			smsSizeText.setText("");
		}
	}

	private int getRequiredSMSNumber() {
		return message.getText().length() / SMS_LENGTH + 1;
	}

	private int getTotalRequiredSMSNumber() {
		return (message.getText().length() / SMS_LENGTH + 1) * (recipients.size() + 1);
	}

	private ArrayList<String> getRecipients() {
		ArrayList<String> numbers = new ArrayList<String>();
		if(to != null) {
			numbers.add(to.getText().toString());
			for(LinearLayout layout : recipients) {
				String to = ((AutoCompleteTextView) layout.getChildAt(0)).getText().toString();
				if(to.trim().length() > 0) {
					numbers.add(to);
				}
			}
		}
		return numbers;
	}

	private class GetFroms extends AsyncTask<Void, Void, String[]> {
		@Override
		protected void onPreExecute() {
			dialog = ProgressDialog.show(SMS.this, getString(R.string.loading), getString(R.string.wizard_loading_froms), true);
		}
		@SuppressWarnings("unchecked")
		@Override
		protected String[] doInBackground(Void... params) {
			try {
				SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
				List<Map<String, String>> results = (List<Map<String, String>>) Resources.getResponse(getApplicationContext(), ACTION.SMS_SENDER_LIST, getTicket(), preferences.getString("account", ""));
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
			SMS.this.froms = froms;
			updateFrom();
			dialog.dismiss();
		}
	}

	private void updateFrom() {
		if(froms.length > 1) {
			ArrayAdapter<String> adapter = new ArrayAdapter<String>(SMS.this,
					android.R.layout.simple_spinner_item, froms);
			adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
			from.setAdapter(adapter);
			SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
			from.setSelection(adapter.getPosition(preferences.getString("default_from_number", "")));
			from.setVisibility(View.VISIBLE);
		}
		else {
			from.setVisibility(View.GONE);
		}
	}

	private class SendSMS extends AsyncTask<Void, Void, Void> {
		private String _error;
		private boolean _smsSaved;

		@Override
		protected void onPreExecute() {
			dialog = ProgressDialog.show(SMS.this, getString(R.string.loading), getString(R.string.loading_sms), true);
		}
		@Override
		protected Void doInBackground(Void... params) {
			if(getTotalRequiredSMSNumber() > creditLeft) {
				_error = getString(R.string.not_enough_credit_left);
				return null;
			}
			try {
				Map<String, String> recipients = new HashMap<String, String>();

				for(String recipient : getRecipients()) {
					//try to normalize phone number
					String number = recipient.replaceAll(" ", "");
					if(!number.startsWith("+")) {
						if(number.startsWith("0")) {
							number = number.replaceFirst("0", String.format("+%s", getDefaultCallingCode()));
						}
						else {
							throw new Exception(getString(R.string.international_phone_number_required));
						}
					}
					recipients.put(recipient, number);
				}

				SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getBaseContext());

				for(Entry<String, String> recipient : recipients.entrySet()) {
					String fromNumber;
					if(from.getVisibility() == View.VISIBLE) {
						fromNumber = (String) from.getSelectedItem();
					}
					else {
						fromNumber = preferences.getString("default_from_number", "");
					}
					if(!Resources.DEBUG_MODE) {
						Resources.getResponse(getApplicationContext(), ACTION.SMS_SEND, getTicket(), preferences.getString("account", ""), fromNumber, recipient.getValue(), message.getText().toString());
					}

					//log
					Log.i(getClass().getSimpleName(), String.format("SMS sent using number %s to %s", fromNumber, recipient.getValue()));

					if(getSaveSMS()) {
						try {
							ContentValues values = new ContentValues();
							values.put("address", recipient.getKey());
							values.put("body", message.getText().toString());
							getContentResolver().insert(Uri.parse("content://sms/sent"), values);

							//log
							Log.i(getClass().getSimpleName(), "SMS saved");
						}
						catch(Exception e) {
							e.printStackTrace();
							Log.e(getClass().getSimpleName(), "Saving SMS", e);
							_error = getString(R.string.bug_saving_sms);
						}
					}

					//decrease credit left if in cache
					creditLeft -= getRequiredSMSNumber();
				}

				//track
				tracker.trackEvent("SMS", "Click", "Send", 1);
				tracker.trackEvent("SMS", "Click", "SendMultiple", recipients.size());
				tracker.dispatch();

				return null;
			} catch (Exception e) {
				e.printStackTrace();
				Log.e(getClass().getSimpleName(), "Sending SMS", e);
				_error = e.getLocalizedMessage();
				return null;
			}
		}

		@Override
		protected void onPostExecute(Void param) {
			errorView.setVisibility(View.GONE);
			if(_error != null) {
				errorView.setText(_error);
				errorView.setVisibility(View.VISIBLE);
				dialog.dismiss();
			}
			else {
				StringBuffer m = new StringBuffer(getString(R.string.sms_successfully_sent));
				if(_smsSaved) {
					m.append(getString(R.string.sms_successfully_saved));
				}
				while(!recipients.isEmpty()) {
					recipientsContainer.removeView(recipients.remove(0));
				}
				to.setText("");
				message.setText("");
				showCredit();
				dialog.dismiss();
				Toast.makeText(SMS.this, m.toString(), Toast.LENGTH_LONG).show();
			}
		}
	}

	public OnClickListener getSendListener() {
		return new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				errorView.setVisibility(View.GONE);
				if(TextUtils.isEmpty(to.getText()) || TextUtils.isEmpty(message.getText().toString())) {
					errorView.setText(getString(R.string.sms_fields_required));
					errorView.setVisibility(View.VISIBLE);
				}
				else {
					new SendSMS().execute();
				}
			}
		 };
	}

	public OnClickListener getAddToListener() {
		return new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				addRecipient();

				//track
				tracker.trackEvent("SMS", "Click", "Add recipient", 1);
			}
		 };
	}

	private LinearLayout addRecipient() {
		LinearLayout container = new LinearLayout(getBaseContext());
		AutoCompleteTextView to = new AutoCompleteTextView(getBaseContext());
		LayoutParams params = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, 0.8f);
		to.setLayoutParams(params);
		to.setHint(R.string.to_hint);
		to.setAdapter(getAdapter());
		ImageButton removeTo = new ImageButton(getBaseContext());
		removeTo.setImageResource(R.drawable.remove);
		removeTo.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				recipients.remove(v.getParent());
				recipientsContainer.removeView((View) v.getParent());
				showSMSCount();
			}
		});
		container.addView(to);
		container.addView(removeTo);
		recipientsContainer.addView(container);
		recipients.add(container);
		showSMSCount();
		return container;
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.menu, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.menu_quit :
			finish();
			return true;
		case R.id.menu_account :
			Intent account = new Intent(getBaseContext(), Account.class);
			startActivityForResult(account, 0);
			finish();
			return true;
		case R.id.menu_preferences :
			Intent preferences = new Intent(getBaseContext(), Preferences.class);
			startActivityForResult(preferences, 0);
			return true;
		case R.id.menu_about :
			Intent about = new Intent(getBaseContext(), About.class);
			startActivityForResult(about, 0);
			return true;
		}
		return false;
	}
}
