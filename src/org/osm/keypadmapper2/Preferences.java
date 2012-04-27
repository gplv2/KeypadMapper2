package org.osm.keypadmapper2;

import android.preference.PreferenceActivity;
import android.os.Bundle;

public class Preferences extends PreferenceActivity {

	@SuppressWarnings("deprecation") // using old mode because the new mode needs fragments (works only with API level >10)
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		addPreferencesFromResource(R.xml.preferences);
	}

}
