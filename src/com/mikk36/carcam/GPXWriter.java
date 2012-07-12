package com.mikk36.carcam;

import java.io.OutputStream;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public class GPXWriter {

	// writer.writeBeginTrack(location1);
	// writer.writeOpenSegment();
	// writer.writeLocation(location1);
	// writer.writeLocation(location2);
	// writer.writeCloseSegment();
	// writer.writeOpenSegment();
	// writer.writeLocation(location3);
	// writer.writeLocation(location4);
	// writer.writeCloseSegment();
	// writer.writeEndTrack(location4);
	// writer.writeWaypoint(wp1);
	// writer.writeWaypoint(wp2);
	// writer.writeFooter();
	// writer.close();

	private static final String TIMESTAMP_FORMAT = "yyyy-MM-dd'T'HH:mm:ss'Z'";

	private final NumberFormat elevationFormatter;
	private final NumberFormat coordinateFormatter;
	private final SimpleDateFormat timestampFormatter;
	private PrintWriter pw = null;

	private String trackName;
	private String trackDescription;

	private Boolean trackOpen = false;
	private Boolean segmentOpen = false;

	public GPXWriter(OutputStream out) {
		elevationFormatter = NumberFormat.getInstance(Locale.US);
		elevationFormatter.setMaximumFractionDigits(1);
		elevationFormatter.setGroupingUsed(false);

		coordinateFormatter = NumberFormat.getInstance(Locale.US);
		coordinateFormatter.setMaximumFractionDigits(5);
		coordinateFormatter.setMaximumIntegerDigits(3);
		coordinateFormatter.setGroupingUsed(false);

		timestampFormatter = new SimpleDateFormat(TIMESTAMP_FORMAT);
		timestampFormatter.setTimeZone(TimeZone.getTimeZone("UTC"));

		pw = new PrintWriter(out);
		trackName = new SimpleDateFormat("HH:mm:ss dd.MM.yyyy").format(new Date());
		trackDescription = "Carcam tracklog starting at "
				+ new SimpleDateFormat("HH:mm:ss dd.MM.yyyy").format(new Date());
	}

	private String formatLocation(LocationCombined location) {
		return "lat=\"" + coordinateFormatter.format(location.getLatitude()) + "\" lon=\""
				+ coordinateFormatter.format(location.getLongitude()) + "\"";
	}

	public void writeHeader() {
		if (pw != null) {
			pw.format("<?xml version=\"1.0\" encoding=\"%s\" standalone=\"yes\"?>\n", Charset.defaultCharset().name());
			pw.println("<?xml-stylesheet type=\"text/xsl\" href=\"details.xsl\"?>");
			pw.println("<gpx");
			pw.println(" version=\"1.1\"");
			pw.println(" creator=\"Carcam\"");
			pw.println(" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"");
			pw.println(" xmlns=\"http://www.topografix.com/GPX/1/1\"");
			pw.print(" xmlns:topografix=\"http://www.topografix.com/GPX/Private/" + "TopoGrafix/0/1\"");
			pw.print(" xsi:schemaLocation=\"http://www.topografix.com/GPX/1/1 ");
			pw.print("http://www.topografix.com/GPX/1/1/gpx.xsd ");
			pw.print("http://www.topografix.com/GPX/Private/TopoGrafix/0/1 ");
			pw.println("http://www.topografix.com/GPX/Private/TopoGrafix/0/1/" + "topografix.xsd\">");
		}
	}

	public void writeFooter() {
		if (pw != null) {
			pw.println("</gpx>");
		}
	}

	public void writeBeginTrack() {
		if (pw != null && !trackOpen) {
			pw.println("<trk>");
			pw.println("<name>" + stringAsCData(trackName) + "</name>");
			pw.println("<desc>" + stringAsCData(trackDescription) + "</desc>");
			pw.println("<number>1</number>");
			pw.println("<extensions><topografix:color>c0c0c0</topografix:color></extensions>");
			trackOpen = true;
		}
	}

	public void writeEndTrack() {
		if (pw != null && trackOpen) {
			pw.println("</trk>");
			trackOpen = false;
		}
	}

	public void writeOpenSegment() {
		if (trackOpen && !segmentOpen) {
			pw.println("<trkseg>");
			segmentOpen = true;
		}
	}

	public void writeCloseSegment() {
		if (trackOpen && segmentOpen) {
			pw.println("</trkseg>");
			segmentOpen = false;
		}
	}

	public void writeLocation(LocationCombined location) {
		if (pw != null && trackOpen && segmentOpen) {
			pw.println("<trkpt " + formatLocation(location) + ">");
			Date d = new Date(location.getTime());
			pw.println("<ele>" + elevationFormatter.format(location.getAltitude()) + "</ele>");
			pw.println("<time>" + timestampFormatter.format(d) + "</time>");
			pw.println("<sat>" + location.getSatelliteCount() + "</sat>");
			pw.println("</trkpt>");
		}
	}

	public void close() {
		if (pw != null) {
			pw.close();
			pw = null;
		}
	}

	public static String stringAsCData(String unescaped) {
		// "]]>" needs to be broken into multiple CDATA segments, like:
		// "Foo]]>Bar" becomes "<![CDATA[Foo]]]]><![CDATA[>Bar]]>"
		// (the end of the first CDATA has the "]]", the other has ">")
		String escaped = unescaped.replaceAll("]]>", "]]]]><![CDATA[>");
		return "<![CDATA[" + escaped + "]]>";
	}

}
