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

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.location.GpsSatellite;
import android.location.GpsStatus;
import android.location.GpsStatus.Listener;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.ArrayAdapter;
import android.widget.SpinnerAdapter;
import android.widget.Toast;

public class KeypadMapper2Activity extends FragmentActivity implements OnSharedPreferenceChangeListener, AddressInterface, LocationListener, Listener {
	private static final int REQUEST_GPS_ENABLE = 1;
	// order of entries in R.array.fragmentSelectorSpinnerEntries
	private static final int NAVIGATION_ITEM_KEYPAD = 0;
	private static final int NAVIGATION_ITEM_EXTENDED = 1;
	private static final long locationUpdateMinTimeMs = 0; // minimum time for location updates in ms
	private static final float locationUpdateMinDistance = 0; // minimum distance for location updates in m
	private HashMap<String, String> address;
	private String basename;
	private LocationManager locationManager = null;
	private String locationStatus;
	private GpxWriter trackWriter = null;
	private OsmWriter osmWriter = null;
	private Location location;

	private enum State {
		keypad, settings, extended
	};

	private State state;

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
		preferences.registerOnSharedPreferenceChangeListener(this);
		address = new HashMap<String, String>();
		locationStatus = getString(R.string.waitForGps);

		setContentView(R.layout.main);

		// check for GPS
		locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
		if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
			showDialogGpsDisabled();
		}
		location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);	

		// check for external storage
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

		File extStorage = Environment.getExternalStorageDirectory();
		File kpmFolder = new File(extStorage.getAbsolutePath() + "/keypadmapper");
		if (!kpmFolder.exists()) {
			if (!kpmFolder.mkdir()) {
				showDialogFatalError(R.string.FolderCreationFailed);
			}
		}

		if (savedInstanceState == null) {
			// first start
			state = State.keypad;

			try {
				Calendar cal = Calendar.getInstance();
				basename = String.format("%tF_%tH-%tM-%tS", cal, cal, cal, cal);

				Toast.makeText(getApplicationContext(), getText(R.string.writeToNewFile) + " " + basename, Toast.LENGTH_LONG).show();

				trackWriter = new GpxWriter(kpmFolder + "/" + basename + ".gpx", false);
				osmWriter = new OsmWriter(kpmFolder + "/" + basename + ".osm", false);
			} catch (FileNotFoundException e) {
				showDialogFatalError(R.string.errorFileOpen);
			} catch (IOException e) {
				showDialogFatalError(R.string.errorFileOpen);
			}
		} else {
			// restart
			state = State.values()[savedInstanceState.getInt("state", State.keypad.ordinal())];
			basename = savedInstanceState.getString("basename");

			address.put("addr:housenumber", savedInstanceState.getString("housenumber"));
			address.put("addr:housename", savedInstanceState.getString("housename"));
			address.put("addr:street", savedInstanceState.getString("street"));
			address.put("addr:postcode", savedInstanceState.getString("postcode"));
			address.put("addr:city", savedInstanceState.getString("city"));
			address.put("addr:country", savedInstanceState.getString("country"));

			try {
				trackWriter = new GpxWriter(kpmFolder + "/" + basename + ".gpx", true);
				osmWriter = new OsmWriter(kpmFolder + "/" + basename + ".osm", true);
			} catch (FileNotFoundException e) {
				if (trackWriter != null) {
					try {
						trackWriter.close();
					} catch (IOException e1) {
						showDialogFatalError(R.string.errorFileOpen);
					}
				}
				if (osmWriter != null) {
					try {
						osmWriter.close();
					} catch (IOException e1) {
						showDialogFatalError(R.string.errorFileOpen);
					}
				}
				Calendar cal = Calendar.getInstance();
				basename = String.format("%tF_%tH-%tM-%tS", cal, cal, cal, cal);

				Toast.makeText(getApplicationContext(), getText(R.string.writeToNewFile) + " " + basename, Toast.LENGTH_LONG).show();

				try {
					trackWriter = new GpxWriter(kpmFolder + "/" + basename + ".gpx", false);
					osmWriter = new OsmWriter(kpmFolder + "/" + basename + ".osm", false);
				} catch (IOException e1) {
					showDialogFatalError(R.string.errorFileOpen);
				}
			} catch (IOException e) {
				showDialogFatalError(R.string.errorFileOpen);
			}
		}

		FragmentManager fragmentManager = getSupportFragmentManager();
		FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
		Fragment mainFragment;
		switch (state) {
		case extended:
			mainFragment = new ExtendedAddressFragment();
			fragmentTransaction.add(R.id.fragment_container, mainFragment, "address_editor");
			break;
		default:
		case keypad:
			mainFragment = new KeypadFragment();
			fragmentTransaction.add(R.id.fragment_container, mainFragment, "keypad");
			break;
		}
		fragmentTransaction.commit();

		locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, locationUpdateMinTimeMs, locationUpdateMinDistance, this);
		locationManager.addGpsStatusListener(this);
	}

	@Override
	public void onResume() {
		super.onResume();
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		outState.putInt("state", state.ordinal());
		outState.putString("basename", basename);
		outState.putString("housenumber", address.get("addr:housenumber"));
		outState.putString("housename", address.get("addr:housename"));
		outState.putString("street", address.get("addr:street"));
		outState.putString("postcode", address.get("addr:postcode"));
		outState.putString("city", address.get("addr:city"));
		outState.putString("country", address.get("addr:country"));
		FragmentManager fragmentManager = getFragmentManager();
		Fragment fragment = null;
		switch (state) {
		case keypad:
			fragment = fragmentManager.findFragmentByTag("keypad");
			break;
		case extended:
			fragmentManager.findFragmentByTag("address_editor");
			break;
		}
		fragmentManager.beginTransaction().remove(fragment).commit();
		super.onSaveInstanceState(outState);
	}

	@Override
	protected void onPause() {
		try {
			if (trackWriter != null) {
				trackWriter.flush();
			}
			if (osmWriter != null) {
				osmWriter.flush();
			}
		} catch (IOException e) {
			// something is going horribly wrong. no way out.
		}
		super.onPause();
	}

	@Override
	public void onDestroy() {		
		locationManager.removeUpdates(this);
		try {
			if (trackWriter != null) {
				trackWriter.close();
			}
			if (osmWriter != null) {
				osmWriter.close();
			}
		} catch (IOException e) {
			// should not happen, the file may be damaged anyway.
		}
		super.onDestroy();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.actionmenu, menu);
		return true;
	}

	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		MenuItem modeSwitcher = menu.findItem(R.id.modeSwitcher);
		if (state == State.keypad) {
			modeSwitcher.setTitle(R.string.AddressEditor);
		} else {
			modeSwitcher.setTitle(R.string.Keypad);
		}
		return super.onPrepareOptionsMenu(menu);
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
		case R.id.modeSwitcher:
			FragmentManager fragmentManager = getSupportFragmentManager();
			FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
			if(state==State.keypad){
				// switch to address editor
				Fragment extendedAddressFragment = new ExtendedAddressFragment();
				fragmentTransaction.replace(R.id.fragment_container, extendedAddressFragment, "address_editor");
				state = State.extended;
				item.setTitle(R.string.Keypad);
			} else {
				//switch back to keypad
				Fragment keypadFragment = new KeypadFragment();
				fragmentTransaction.replace(R.id.fragment_container, keypadFragment, "keypad");
				state = State.keypad;
				item.setTitle(R.string.AddressEditor);
			}
			fragmentTransaction.commit();
			return true;
		default:
			return super.onOptionsItemSelected(item);
		}
	}

	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
		if (key.equals("housenumberDistance")) {
			// keypadfragment will fetch the distance itself when resuming
		}
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
	public void onGpsStatusChanged(int event) {
		switch (event) {
		case GpsStatus.GPS_EVENT_STARTED:
			break;
		case GpsStatus.GPS_EVENT_STOPPED:
			break;
		case GpsStatus.GPS_EVENT_FIRST_FIX:
			break;
		case GpsStatus.GPS_EVENT_SATELLITE_STATUS:
			GpsStatus gpsStatus = locationManager.getGpsStatus(null);
			int maxSats = 0;
			int usedSats = 0;
			Iterable<GpsSatellite> gpsSatellites = gpsStatus.getSatellites();
			for (GpsSatellite sat : gpsSatellites) {
				maxSats++;
				if (sat.usedInFix()) {
					usedSats++;
				}
			}
			KeypadFragment keypadFragment = (KeypadFragment) getSupportFragmentManager().findFragmentByTag("keypad");
			if (keypadFragment != null) {
				keypadFragment.setSatCount(usedSats, maxSats);
			}
			break;
		}
	}

	@Override
	public void onStatusChanged(String provider, int status, Bundle extras) {
		// useless, doesn't get called when a GPS fix is available
	}

	@Override
	public void onProviderDisabled(String provider) {
		if(provider.equals(LocationManager.GPS_PROVIDER)){
			showDialogGpsDisabled();
		}
	}

	@Override
	public void onProviderEnabled(String provider) {
		// ignored. GPS availability is checked on startup
	}

	@Override
	public void onLocationChanged(Location location) {
		this.location = location;
		// write tracklog
		try {
			if (location.hasAltitude()) {
				trackWriter.addTrackpoint(location.getLatitude(), location.getLongitude(), location.getTime(), location.getAltitude());
			} else {
				trackWriter.addTrackpoint(location.getLatitude(), location.getLongitude(), location.getTime());
			}
		} catch (IOException e) {
		}
		locationStatus = getString(R.string.statusReadyString, location.getAccuracy());
		if (state == State.keypad) {
			KeypadFragment keypadFragment = (KeypadFragment) getSupportFragmentManager().findFragmentByTag("keypad");
			if (keypadFragment != null) {
				keypadFragment.setStatus(locationStatus);
			}
		}
	}

	@Override
	public void onAddressChanged(Map<String, String> newAddress) {
		for (Entry<String, String> entry : newAddress.entrySet()) {
			if (entry.getValue().length() == 0) {
				address.remove(entry.getKey());
			} else {
				address.put(entry.getKey(), entry.getValue());
			}
		}
	}

	@Override
	public void onHousenumberChanged(String newHousenumber) {
		if (newHousenumber.length() == 0) {
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
			double lat = (location.getLatitude() + Math.sin(Math.PI / 180 * location.getBearing()) * left + Math.cos(Math.PI / 180 * location.getBearing())	* forward);
			double lon = (location.getLongitude() + (Math.sin(Math.PI / 180 * location.getBearing()) * forward - Math.cos(Math.PI / 180 * location.getBearing()) * left)
					/ Math.cos(Math.PI / 180 * location.getLatitude()));
			try {
				osmWriter.addNode(lat, lon, address);
			} catch (IOException e) {
				showDialogFatalError(R.string.errorFileOpen);
			}
		}
	}

	@Override
	public Map<String, String> getAddress() {
		return address;
	}

	@Override
	public String getLocationStatus() {
		return locationStatus;
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
