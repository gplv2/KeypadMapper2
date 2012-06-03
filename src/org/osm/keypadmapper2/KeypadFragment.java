package org.osm.keypadmapper2;

import java.util.HashMap;
import java.util.Map;

import android.app.Activity;
import android.app.Fragment;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;

public class KeypadFragment extends Fragment implements OnClickListener {
	private TextView textStatus;
	private TextView textSatCount;
	private TextView textStreet;
	private TextView textHousename;
	private TextView textHousenumber;
	private double distance;
	private AddressInterface addressCallback;

	@Override
	public void onAttach(Activity activity) {
		super.onAttach(activity);

		try {
			addressCallback = (AddressInterface) activity;
		} catch (ClassCastException e) {
			throw new ClassCastException(activity.toString() + "must implement AddressInterface");
		}
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		View view = inflater.inflate(R.layout.keypad_fragment, container, false);
		textStatus = (TextView) view.findViewById(R.id.text_status);
		textSatCount = (TextView) view.findViewById(R.id.text_satcount);
		textStreet = (TextView) view.findViewById(R.id.text_street);
		textHousename = (TextView) view.findViewById(R.id.text_housename);
		textHousenumber = (TextView) view.findViewById(R.id.text_housenumber);
		setupButtons((ViewGroup) view.findViewById(R.id.fragment_keypad));
		return view;
	}

	@Override
	public void onResume() {
		super.onResume();
		SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getActivity());
		distance = Double.valueOf(preferences.getString("housenumberDistance", "10"));
		Map<String, String> address = addressCallback.getAddress();
		textStreet.setText(address.get("addr:street"));
		textHousename.setText(address.get("addr:housename"));
		textHousenumber.setText(address.get("addr:housenumber"));
		textStatus.setText(addressCallback.getLocationStatus());
	}

	@Override
	public void onPause() {
		// TODO Auto-generated method stub
		super.onPause();
	}

	@Override
	public void onClick(View v) {
		switch (v.getId()) {
		case R.id.button_C:
			// clear
			textHousenumber.setText("");
			addressCallback.onHousenumberChanged("");
			break;
		case R.id.button_DEL: {
			// delete the last char
			String housenumber = (String) textHousenumber.getText();
			if (housenumber.length() > 0) {
				housenumber = housenumber.substring(0, housenumber.length() - 1);
				textHousenumber.setText(housenumber);
				addressCallback.onHousenumberChanged(housenumber);
			}
			break;
		}
		case R.id.button_L:
			// place address to the left
			addressCallback.onAddressNodePlacedRelative(0, distance);
			clearVolatileTags();
			break;
		case R.id.button_F:
			// place address forwards
			addressCallback.onAddressNodePlacedRelative(distance, 0);
			clearVolatileTags();
			break;
		case R.id.button_R:
			// place address to the right
			addressCallback.onAddressNodePlacedRelative(0, -distance);
			clearVolatileTags();
			break;
		default:
			// all other buttons are used to add characters
			String housenumber = (String) textHousenumber.getText();
			housenumber += v.getTag();
			textHousenumber.setText(housenumber);
			addressCallback.onHousenumberChanged(housenumber);
		}
	}

	/**
	 * Sets the distance of the address node from the current position. Call this when the corresponding Preference is changed.
	 * @param placementDistance
	 */
	public void setPlacementDistance(double placementDistance) {
		distance = placementDistance;
	}

	/**
	 * Sets the status line to display the specified message.
	 * @param StatusMessage Status message string
	 */
	public void setStatus(String statusMessage) {
		textStatus.setText(statusMessage);
	}

	/**
	 * Sets the status line to display the specified message.
	 * @param resid ID of a string resource to use as status message
	 */
	public void setLocationStatus(int resid) {
		textStatus.setText(resid);
	}

	/**
	 * Sets the GPS satellite display.
	 * @param usedSats Number of satellites currently used for calculating the position.
	 * @param maxSats Number of visible satellites.
	 */
	public void setSatCount(int usedSats, int maxSats) {
		textSatCount.setText(getString(R.string.satString, usedSats, maxSats));
	}

	/**
	 * Performs additional setup steps for the buttons. Used for properties which cannot be set as layout properties.
	 * @param viewGroup
	 */
	private void setupButtons(ViewGroup viewGroup) {
		// Set OnClickListener. The method specified via android:onClick must be implemented in the main activity, so this workaround is needed.
		for (int i = 0; i < viewGroup.getChildCount(); i++) {
			if (ViewGroup.class.isInstance(viewGroup.getChildAt(i))) {
				setupButtons((ViewGroup) viewGroup.getChildAt(i));
			} else if (Button.class.isInstance(viewGroup.getChildAt(i)) | ImageButton.class.isInstance(viewGroup.getChildAt(i))) {
				viewGroup.getChildAt(i).setOnClickListener(this);
			}
		}
	}

	/**
	 * Clears all volatile tags after placing an address node. At the moment housenumber and housename are cleared.
	 * May be changed at a later time.
	 */
	private void clearVolatileTags() {
		textHousenumber.setText("");
		textHousename.setText("");
		HashMap<String, String> newAddress = new HashMap<String, String>();
		newAddress.put("addr:housenumber", "");
		newAddress.put("addr:housename", "");
		addressCallback.onAddressChanged(newAddress);
	}
}
