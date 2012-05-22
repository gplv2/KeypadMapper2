package org.osm.keypadmapper2;

public class FileFormatException extends Exception {

	private static final long serialVersionUID = 1L;

	public FileFormatException() {
		super();
	}

	public FileFormatException(String detailMessage, Throwable throwable) {
		super(detailMessage, throwable);
	}

	public FileFormatException(String detailMessage) {
		super(detailMessage);
	}

	public FileFormatException(Throwable throwable) {
		super(throwable);
	}
}
