package org.osm.keypadmapper2;

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
	private KeypadInterface keypadCallback;

	public interface KeypadInterface {
		/**
		 * Called when the user changes the housenumber.
		 * @param newHousenumber Currently entered housenumber.
		 */
		public void onHousenumberChanged(String newHousenumber);

		/**
		 * Called then the user indicates where the address node should be placed.
		 * @param forward Forward distance in meters
		 * @param left Distance to the left in meters, negative if the node is to be placed to the right
		 */
		public void onAddressNodePlaced(double forward, double left);

		/**
		 * Called to get the complete address details entered in another fragment.
		 * @return Map containing the address tags and their values using the addr:* scheme
		 */
		public Map<String, String> getAddress();
	}

	@Override
	public void onAttach(Activity activity) {
		super.onAttach(activity);

		try {
			keypadCallback = (KeypadInterface) activity;
		} catch (ClassCastException e) {
			throw new ClassCastException(activity.toString() + "must implement KeypadInterface");
		}
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getActivity());
		distance = Double.valueOf(preferences.getString("housenumberDistance", "10"));
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
		setupButtons((ViewGroup) view.findViewById(R.id.buttonGroup));
		return view;
	}

	@Override
	public void onResume() {
		super.onResume();
		Map<String, String> address = keypadCallback.getAddress();
		textStreet.setText((String) address.get("addr:street"));
		textHousename.setText((String) address.get("addr:housename"));
		textHousenumber.setText((String) address.get("addr:housenumber"));
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
			keypadCallback.onHousenumberChanged("");
			break;
		case R.id.button_DEL: {
			// delete the last char
			String housenumber = (String) textHousenumber.getText();
			if (housenumber.length() > 0) {
				housenumber = housenumber.substring(0, housenumber.length() - 1);
				textHousenumber.setText(housenumber);
				keypadCallback.onHousenumberChanged(housenumber);
			}
			break;
		}
		case R.id.button_L:
			// place address to the left
			keypadCallback.onAddressNodePlaced(0, distance);
			textHousenumber.setText("");
			keypadCallback.onHousenumberChanged("");
			break;
		case R.id.button_F:
			// place address forwards
			keypadCallback.onAddressNodePlaced(distance, 0);
			textHousenumber.setText("");
			keypadCallback.onHousenumberChanged("");
			break;
		case R.id.button_R:
			// place address to the right
			keypadCallback.onAddressNodePlaced(0, -distance);
			textHousenumber.setText("");
			keypadCallback.onHousenumberChanged("");
			break;
		default:
			// all other buttons are used to add characters
			String housenumber = (String) textHousenumber.getText();
			housenumber += v.getTag();
			textHousenumber.setText(housenumber);
			keypadCallback.onHousenumberChanged(housenumber);
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
	public void setStatus(int resid) {
		textStatus.setText(resid);
	}

	/**
	 * Sets the GPS satellite display.
	 * @param usedSats Number of satellites currently used for calculating the position.
	 * @param maxSats Number of visible satellites.
	 */
	public void setSatCount(int usedSats, int maxSats) {
		textSatCount.setText(String.format("%d/%d", usedSats, maxSats));
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
}
