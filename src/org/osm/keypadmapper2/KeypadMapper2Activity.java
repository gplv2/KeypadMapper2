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
import java.io.RandomAccessFile;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TimeZone;

import org.osm.keypadmapper2.KeypadFragment.KeypadInterface;

import android.app.Activity;
import android.app.AlertDialog;
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
import android.widget.Toast;

final class LocationLogger implements LocationListener {
	public PowerManager.WakeLock wakeLock;
	public double lon = -200, lat, bearing;
	public int record = 0, osmid = 0;
	public RandomAccessFile track, houseNumbers;
	public java.text.SimpleDateFormat dateFormat = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
	public LocationManager locationManager;
	public KeypadFragment keypadFragment;

	public void onProviderDisabled(String provider) {
	}

	public void onProviderEnabled(String provider) {
	}

	public void onStatusChanged(String provider, int status, Bundle extras) {
	}

	public void onLocationChanged(Location location) {
		if (lon < -180)
			if (keypadFragment != null) {
				keypadFragment.setStatus(R.string.ready);
			}
		lon = location.getLongitude();
		lat = location.getLatitude();
		bearing = location.getBearing();
		try {
			track.seek(track.getFilePointer() - 24); // Overwrite </trkseg>\n</trk>\n</gpx>\n
			track.writeBytes("<trkpt lat=\"" + lat + "\" lon=\"" + lon + "\">\n"
					+ (location.hasAltitude() ? "<ele>" + (int) location.getAltitude() + "</ele>\n" : "") + "<time>"
					+ (dateFormat.format(new java.util.Date(location.getTime()))) + "Z</time>\n</trkpt>\n</trkseg>\n</trk>\n</gpx>\n");
		} catch (IOException e) {
		}
	}
}

public class KeypadMapper2Activity extends Activity implements OnSharedPreferenceChangeListener, KeypadInterface {
	private static final int REQUEST_GPS_ENABLE = 1;
	private SharedPreferences preferences = null;
	private LocationLogger locationLogger = null;
	private HashMap<String, String> address;
	
	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);
		preferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
		preferences.registerOnSharedPreferenceChangeListener(this);
		address = new HashMap<String, String>();

		LocationManager locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
		if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
			showDialogGpsDisabled();
		}

		locationLogger = new LocationLogger();
		PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
		locationLogger.wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Key");
		locationLogger.wakeLock.acquire();
		locationLogger.track = null;
		locationLogger.dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
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

			locationLogger.houseNumbers = new RandomAccessFile(new File(kpmFolder, "/" + basename + ".osm"), "rw");
			locationLogger.houseNumbers.writeBytes("<?xml version='1.0' encoding='UTF-8'?>\n" + "<osm version='0.6' generator='KeypadMapper'>\n</osm>\n");
			locationLogger.track = new RandomAccessFile(new File(kpmFolder, "/" + basename + ".gpx"), "rw");
			locationLogger.track.writeBytes(
					"<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" + 
					"<gpx\n" + 
					"version=\"1.0\"\n" + 
					"creator=\"KeypadMapper\"\n" + 
					"xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n" + 
					"xmlns=\"http://www.topografix.com/GPX/1/0\"\n" +
					"xsi:schemaLocation=\"http://www.topografix.com/GPX/1/0 http://www.topografix.com/GPX/1/0/gpx.xsd\">\n" + 
					"<trk>\n" + 
					"<trkseg>\n" + 
					"</trkseg>\n" + 
					"</trk>\n" + 
					"</gpx>\n");

		} catch (FileNotFoundException e) {
			showDialogFatalError(R.string.errorFileOpen);
		} catch (IOException e) {
			showDialogFatalError(R.string.errorFileOpen);
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
			exit();
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
		super.onDestroy();
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		switch (requestCode) {
		case REQUEST_GPS_ENABLE:
			LocationManager locationManager = (LocationManager)getSystemService(LOCATION_SERVICE);
			if(!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)){
				showDialogGpsDisabled();
			}
			break;
		default:
			super.onActivityResult(requestCode, resultCode, data);
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
	public void onAddressNodePlaced(double forward, double left) { // fwd and left are distances in meter
		if(address.containsKey("addr:housenumber")){
			forward /= 111111;
			left /= 111111;
			try {
				locationLogger.houseNumbers.seek(locationLogger.houseNumbers.getFilePointer() - 7); // Overwrite </osm>\n
				locationLogger.houseNumbers.writeBytes("<node id='"
						+ --locationLogger.osmid
						+ "' visible='true' lat='"
						+ (locationLogger.lat + Math.sin(Math.PI / 180 * locationLogger.bearing) * left + Math.cos(Math.PI / 180 * locationLogger.bearing) * forward)
						+ "' lon='"
						+ (locationLogger.lon + (Math.sin(Math.PI / 180 * locationLogger.bearing) * forward - Math.cos(Math.PI / 180 * locationLogger.bearing) * left)
								/ Math.cos(Math.PI / 180 * locationLogger.lat)) + "'>\n");
				for (Entry<String, String> entry : address.entrySet()) {
					locationLogger.houseNumbers.writeBytes(" <tag k='" + entry.getKey() + "' v='" + entry.getValue() + "'/>\n");
				}
				locationLogger.houseNumbers.writeBytes( "</node>\n</osm>\n");
			} catch (IOException e) {
				showDialogFatalError(R.string.errorFileOpen);
			}
		}
	}
	
	@Override
	public Map<String, String> getAddress() {
		return address;
	}

	private void exit() {
		locationLogger.wakeLock.release();
		locationLogger.locationManager.removeUpdates(locationLogger);
		locationLogger.record = 0;
		finish();
	}

	private void showDialogGpsDisabled() {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setMessage(R.string.errorGpsDisabled)
			.setCancelable(false)
			.setNegativeButton(R.string.quit, new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int which) {
					KeypadMapper2Activity.this.exit();
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
					KeypadMapper2Activity.this.exit();
				}
		});
		builder.create().show();
	}
}
