package com.mikk36.carcam;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.Properties;

import android.os.Environment;

public class Settings {
	private Boolean recordingEnabled;
	private Boolean videoEnabled;
	private Boolean audioEnabled;
	private int maxLength;
	private int videoBitRate;
	private Boolean brightness;
	private File settingsFile = new File(
			Environment
					.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM),
			"Carcam/Xsettings.ini");

	public Settings() {
		Properties defProps = new Properties();
		defProps.put("recordingEnabled", "true");
		defProps.put("videoEnabled", "true");
		defProps.put("audioEnabled", "true");
		defProps.put("maxLength", "1200000");
		defProps.put("videoBitRate", "6000000");
		defProps.put("brightness", "true");

		Properties props = new Properties(defProps);
		try {
			FileReader file = new FileReader(settingsFile);
			props.load(file);
		} catch (FileNotFoundException e) {
			Log.log("Error loading settings file: " + e.getMessage());
		} catch (IOException e) {
			Log.log("Error loading settings file: " + e.getMessage());
			e.printStackTrace();
		}

		recordingEnabled = Boolean.parseBoolean(props
				.getProperty("recordingEnabled"));
		videoEnabled = Boolean.parseBoolean(props.getProperty("videoEnabled"));
		audioEnabled = Boolean.parseBoolean(props.getProperty("audioEnabled"));
		maxLength = Integer.parseInt(props.getProperty("maxLength"));
		videoBitRate = Integer.parseInt(props.getProperty("videoBitRate"));
		brightness = Boolean.parseBoolean(props.getProperty("brightness"));
	}

	public Boolean getRecordingEnabled() {
		return recordingEnabled;
	}

	public Boolean getVideoEnabled() {
		return videoEnabled;
	}

	public Boolean getAudioEnabled() {
		return audioEnabled;
	}

	public int getMaxLength() {
		return maxLength;
	}

	public int getVideoBitRate() {
		return videoBitRate;
	}

	public Boolean getBrightness() {
		return brightness;
	}
}
