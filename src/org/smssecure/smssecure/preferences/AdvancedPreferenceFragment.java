package org.smssecure.smssecure.preferences;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.preference.Preference;
import android.provider.ContactsContract;
import android.support.v4.preference.PreferenceFragment;
import android.util.Log;

import org.smssecure.smssecure.ApplicationPreferencesActivity;
import org.smssecure.smssecure.LogSubmitActivity;
import org.smssecure.smssecure.R;
import org.smssecure.smssecure.contacts.ContactAccessor;
import org.smssecure.smssecure.crypto.MasterSecret;
import org.smssecure.smssecure.util.SMSSecurePreferences;

public class AdvancedPreferenceFragment extends PreferenceFragment {
  private static final String TAG = AdvancedPreferenceFragment.class.getSimpleName();

  private static final String SUBMIT_DEBUG_LOG_PREF = "pref_submit_debug_logs";

  private static final int PICK_IDENTITY_CONTACT = 1;

  @Override
  public void onCreate(Bundle paramBundle) {
    super.onCreate(paramBundle);
    addPreferencesFromResource(R.xml.preferences_advanced);

    this.findPreference(SUBMIT_DEBUG_LOG_PREF)
      .setOnPreferenceClickListener(new SubmitDebugLogListener());
  }

  @Override
  public void onResume() {
    super.onResume();
    ((ApplicationPreferencesActivity) getActivity()).getSupportActionBar().setTitle(R.string.preferences__advanced);
  }

  private class SubmitDebugLogListener implements Preference.OnPreferenceClickListener {
    @Override
    public boolean onPreferenceClick(Preference preference) {
      final Intent intent = new Intent(getActivity(), LogSubmitActivity.class);
      startActivity(intent);
      return true;
    }
  }
}
