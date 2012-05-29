package org.osm.keypadmapper2;

import java.util.Map;

public interface AddressInterface {
	/**
	 * Called when the user changes any of the address details.
	 * This function will update the only tags existing in the address map. Existing tags which aren't contained by this map won't be changed or deleted.
	 * @param address Map containing the address tags and their values using the addr:* scheme
	 */
	public void onAddressChanged(Map<String, String> address);

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
	public void onAddressNodePlacedRelative(double forward, double left);

	/**
	 * Called to get the complete address details entered in another fragment.
	 * @return Map containing the address tags and their values using the addr:* scheme
	 */
	public Map<String, String> getAddress();
	
	/**
	 * Called to get the current location status.
	 * @return Location status string
	 */
	public String getLocationStatus();
}
