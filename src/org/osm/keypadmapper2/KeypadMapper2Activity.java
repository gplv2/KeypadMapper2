/*   Copyright (C) 2010 Nic Roets

     Licensed under the Apache License, Version 2.0 (the "License");
     you may not use this file except in compliance with the License.
     You may obtain a copy of the License at

          http://www.apache.org/licenses/LICENSE-2.0

     Unless required by applicable law or agreed to in writing, software
     distributed under the License is distributed on an "AS IS" BASIS,
     WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
     See the License for the specific language governing permissions and
     limitations under the License.
 */
package org.osm.keypadmapper2;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import android.app.ActionBar;
import android.app.ActionBar.OnNavigationListener;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.ArrayAdapter;
import android.widget.SpinnerAdapter;
import android.widget.Toast;

final class LocationLogger implements LocationListener {
	public PowerManager.WakeLock wakeLock;
	public double lon = -200, lat, bearing;
	public int record = 0;
	public LocationManager locationManager;
	public KeypadFragment keypadFragment;
	public GpxWriter trackWriter = null;
	public OsmWriter osmWriter = null;

	public void onProviderDisabled(String provider) {
	}

	public void onProviderEnabled(String provider) {
	}

	public void onStatusChanged(String provider, int status, Bundle extras) {
	}

	public void onLocationChanged(Location location) {
		if (lon < -180)
			if (keypadFragment != null) {
				keypadFragment.setLocationStatus(R.string.ready);
			}
		lon = location.getLongitude();
		lat = location.getLatitude();
		bearing = location.getBearing();
		try {
			if (location.hasAltitude()) {
				trackWriter.addTrackpoint(location.getLatitude(), location.getLongitude(), location.getAltitude());
			} else {
				trackWriter.addTrackpoint(location.getLatitude(), location.getLongitude());
			}
		} catch (IOException e) {
		}
	}
}

public class KeypadMapper2Activity extends Activity implements OnSharedPreferenceChangeListener, OnNavigationListener, AddressInterface {
	private static final int REQUEST_GPS_ENABLE = 1;
	// order of entries in R.array.fragmentSelectorSpinnerEntries
	private static final int NAVIGATION_ITEM_KEYPAD = 0;
	private static final int NAVIGATION_ITEM_EXTENDED = 1;
	private SharedPreferences preferences = null;
	private LocationLogger locationLogger = null;
	private HashMap<String, String> address;

	private enum State {
		keypad, settings, extended
	};

	private State state;
	public String LocationStatus;

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);
		preferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
		preferences.registerOnSharedPreferenceChangeListener(this);
		address = new HashMap<String, String>();

		FragmentManager fragmentManager = getFragmentManager();
		FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
		Fragment keypadFragment = new KeypadFragment();
		fragmentTransaction.replace(R.id.fragment_container, keypadFragment);
		fragmentTransaction.commit();

		state = State.keypad;

		SpinnerAdapter fragmentSpinnerAdapter = ArrayAdapter.createFromResource(this, R.array.fragmentSelectorSpinnerEntries,
				android.R.layout.simple_spinner_dropdown_item);
		ActionBar actionBar = getActionBar();
		actionBar.setDisplayOptions(0, ActionBar.DISPLAY_SHOW_TITLE);
		actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_LIST);
		actionBar.setListNavigationCallbacks(fragmentSpinnerAdapter, this);

		LocationManager locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
		if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
			showDialogGpsDisabled();
		}

		locationLogger = new LocationLogger();
		PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
		locationLogger.wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "KeypadMapper");
		locationLogger.wakeLock.acquire();
		locationLogger.locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
		locationLogger.keypadFragment = (KeypadFragment) getFragmentManager().findFragmentById(R.id.fragment_keypad);

		String extStorageState = Environment.getExternalStorageState();
		if (Environment.MEDIA_MOUNTED.equals(extStorageState)) {
			// We can read and write the media
		} else if (Environment.MEDIA_MOUNTED_READ_ONLY.equals(extStorageState)) {
			// We can only read the media
			showDialogFatalError(R.string.errorStorageRO);
		} else {
			// Something else is wrong. It may be one of many other states, but all we need to know is we can neither read nor write
			showDialogFatalError(R.string.errorStorageUnavailable);
		}

		try {
			File extStorage = Environment.getExternalStorageDirectory();
			File kpmFolder = new File(extStorage.getAbsolutePath() + "/keypadmapper");
			if (!kpmFolder.exists()) {
				kpmFolder.mkdir();
			}

			Calendar cal = Calendar.getInstance();
			String basename = String.format("%tF_%tH-%tM-%tS", cal, cal, cal, cal);

			Toast.makeText(getApplicationContext(), getText(R.string.writeToNewFile) + " " + basename, Toast.LENGTH_LONG).show();

			locationLogger.trackWriter = new GpxWriter(kpmFolder + "/" + basename + ".gpx", false);
			locationLogger.osmWriter = new OsmWriter(kpmFolder + "/" + basename + ".osm", false);
		} catch (FileNotFoundException e) {
			showDialogFatalError(R.string.errorFileOpen);
		} catch (IOException e) {
			showDialogFatalError(R.string.errorFileOpen);
		} catch (FileFormatException e) {
			// will not be thrown
		}
	}

	@Override
	public void onResume() {
		super.onResume();
		if (locationLogger.record == 0) {
			locationLogger.wakeLock.acquire();
			locationLogger.locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 6f, locationLogger);
			locationLogger.record = 1;
			locationLogger.lon = -200;
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.actionmenu, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle item selection
		switch (item.getItemId()) {
		case R.id.actionStop:
			finish();
			return true;
		case R.id.actionPreferences:
			final Intent intent = new Intent(this, Preferences.class);
			startActivity(intent);
			return true;
		default:
			return super.onOptionsItemSelected(item);
		}
	}

	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
		if (key.equals("housenumberDistance")) {
			KeypadFragment keypadFragment = (KeypadFragment) getFragmentManager().findFragmentById(R.id.fragment_keypad);
			if (keypadFragment != null) {
				double distance = Double.valueOf(preferences.getString("housenumberDistance", "10"));
				keypadFragment.setPlacementDistance(distance);
			}
		}
	}

	@Override
	public void onDestroy() {
		locationLogger.wakeLock.release();
		locationLogger.locationManager.removeUpdates(locationLogger);
		locationLogger.record = 0;
		try {
			if (locationLogger.trackWriter != null) {
				locationLogger.trackWriter.close();
			}
			if (locationLogger.osmWriter != null) {
				locationLogger.osmWriter.close();
			}
		} catch (IOException e) {
			// should not happen, the file may be damaged anyway.
		}
		super.onDestroy();
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		switch (requestCode) {
		case REQUEST_GPS_ENABLE:
			LocationManager locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
			if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
				showDialogGpsDisabled();
			}
			break;
		default:
			super.onActivityResult(requestCode, resultCode, data);
		}
	}

	@Override
	public boolean onNavigationItemSelected(int itemPosition, long itemId) {
		boolean ret;
		FragmentManager fragmentManager = getFragmentManager();
		FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
		switch (itemPosition) {
		case NAVIGATION_ITEM_KEYPAD: // keypad
			if (state != State.keypad) {
				Fragment keypadFragment = new KeypadFragment();
				fragmentTransaction.replace(R.id.fragment_container, keypadFragment);
				state = State.keypad;
			}
			ret = true;
			break;
		case NAVIGATION_ITEM_EXTENDED: // extended address editor
			if (state != State.extended) {
				Fragment extendedAddressFragment = new ExtendedAddressFragment();
				fragmentTransaction.replace(R.id.fragment_container, extendedAddressFragment);
				state = State.extended;
			}
			ret = true;
			break;
		default:
			ret = false;
		}
		fragmentTransaction.commit();

		return ret;
	}

	@Override
	public void onAddressChanged(Map<String, String> newAddress) {
		for (Entry<String, String> entry : newAddress.entrySet()) {
			if (entry.getValue().isEmpty()) {
				address.remove(entry.getKey());
			} else {
				address.put(entry.getKey(), entry.getValue());
			}
		}
	}

	@Override
	public void onHousenumberChanged(String newHousenumber) {
		if (newHousenumber.isEmpty()) {
			address.remove("addr:housenumber");
		} else {
			address.put("addr:housenumber", newHousenumber);
		}
	}

	@Override
	public void onAddressNodePlacedRelative(double forward, double left) { // fwd and left are distances in meter
		if (address.containsKey("addr:housenumber")) {
			forward /= 111111;
			left /= 111111;
			double lat = (locationLogger.lat + Math.sin(Math.PI / 180 * locationLogger.bearing) * left + Math.cos(Math.PI / 180 * locationLogger.bearing)
					* forward);
			double lon = (locationLogger.lon + (Math.sin(Math.PI / 180 * locationLogger.bearing) * forward - Math.cos(Math.PI / 180 * locationLogger.bearing)
					* left)
					/ Math.cos(Math.PI / 180 * locationLogger.lat));
			try {
				locationLogger.osmWriter.addNode(lat, lon, address);
			} catch (IOException e) {
				showDialogFatalError(R.string.errorFileOpen);
			}
		}
	}

	@Override
	public Map<String, String> getAddress() {
		return address;
	}

	private void showDialogGpsDisabled() {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setMessage(R.string.errorGpsDisabled)
			.setCancelable(false)
			.setNegativeButton(R.string.quit, new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int which) {
					finish();
				}
			})
			.setPositiveButton(R.string.systemSettings, new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int which) {
					startActivityForResult(new Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS), REQUEST_GPS_ENABLE);
				}
		});
		builder.create().show();
	}

	private void showDialogFatalError(int messageId) {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setMessage(messageId)
			.setCancelable(false)
			.setNegativeButton(R.string.quit, new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int id) {
					finish();
				}
		});
		builder.create().show();
	}
}
