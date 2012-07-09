package com.mikk36.carcam;

public class Log {
	public static void log(String text) {
		android.util.Log.i("Carcam", text);
	}

	public static void log(String tag, String text) {
		android.util.Log.i(tag, text);
	}
}
