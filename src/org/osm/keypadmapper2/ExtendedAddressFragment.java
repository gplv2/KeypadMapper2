package org.osm.keypadmapper2;

import java.util.HashMap;
import java.util.Map;

import android.app.Activity;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

public class ExtendedAddressFragment extends Fragment {
	private TextView textInputHousenumber;
	private TextView textInputHousename;
	private TextView textInputStreet;
	private TextView textInputPostcode;
	private TextView textInputCity;
	private TextView textInputCountry;
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
		View view = inflater.inflate(R.layout.extended_address_fragment, container, false);
		textInputHousenumber = (TextView) view.findViewById(R.id.input_housenumber);
		textInputHousename = (TextView) view.findViewById(R.id.input_housename);
		textInputStreet = (TextView) view.findViewById(R.id.input_street);
		textInputPostcode = (TextView) view.findViewById(R.id.input_postcode);
		textInputCity = (TextView) view.findViewById(R.id.input_city);
		textInputCountry = (TextView) view.findViewById(R.id.input_country);
		return view;
	}

	@Override
	public void onResume() {
		super.onResume();
		Map<String, String> address = addressCallback.getAddress();
		textInputHousenumber.setText(address.get("addr:housenumber"));
		textInputHousename.setText(address.get("addr:housename"));
		textInputStreet.setText(address.get("addr:street"));
		textInputPostcode.setText(address.get("addr:postcode"));
		textInputCity.setText(address.get("addr:city"));
		textInputCountry.setText(address.get("addr:country"));
	}

	@Override
	public void onPause() {
		HashMap<String, String> newAddress = new HashMap<String, String>();
		newAddress.put("addr:housenumber", textInputHousenumber.getText().toString());
		newAddress.put("addr:housename", textInputHousename.getText().toString());
		newAddress.put("addr:street", textInputStreet.getText().toString());
		newAddress.put("addr:postcode", textInputPostcode.getText().toString());
		newAddress.put("addr:city", textInputCity.getText().toString());
		newAddress.put("addr:country", textInputCountry.getText().toString());
		addressCallback.onAddressChanged(newAddress);
		super.onPause();
	}

}
