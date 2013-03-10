package com.ScavengerHunt.game;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;

import android.util.JsonReader;
import android.util.JsonWriter;

import com.google.android.maps.GeoPoint;

/**
 * @author kvudata
 * 
 *         Class representing a Scavenger Hunt clue. A clue generally consists
 *         of a: - location - graphic to display to the user - object to detect
 */
public class Clue implements JsonWritable {
	// longitude/latitude of clue
	private Location location;
	// TODO: add bearing/pose information?
	// file path to graphic to display to user
	private String graphicFilepath;
	// file path to image of object to detect
	private String trackableFilepath;
	//TODO: store descriptors file instead of image so don't need to compute when loading clue

	public Clue() {
		location = new Location();
	}

	public Clue(Location loc, String graphic, String trackable) {
		location = loc;
		graphicFilepath = graphic;
		trackableFilepath = trackable;
	}

	public Location getLocation() {
		return location;
	}

	public void setLocation(Location location) {
		this.location = location;
	}

	public String getGraphicFilepath() {
		return graphicFilepath;
	}

	public void setGraphicFilepath(String graphic_filepath) {
		this.graphicFilepath = graphic_filepath;
	}

	public String getTrackableFilepath() {
		return trackableFilepath;
	}

	public void setTrackableFilepath(String trackableFilepath) {
		this.trackableFilepath = trackableFilepath;
	}

	public void writeJson(JsonWriter out) throws IOException {
		out.beginObject();

		out.name("location");
		location.writeJson(out);
		out.name("graphicFilepath").value(graphicFilepath);
		out.name("trackableFilepath").value(trackableFilepath);

		out.endObject();
		out.flush();
	}

	public void readJson(JsonReader in) throws IOException {
		in.beginObject();
		while (in.hasNext()) {
			String name = in.nextName();
			if (name.equals("location")) {
				location.readJson(in);
			} else if (name.equals("graphicFilepath")) {
				graphicFilepath = in.nextString();
			} else if (name.equals("trackableFilepath")) {
				trackableFilepath = in.nextString();
			}
		}
		in.endObject();
	}

	@Override
	public boolean equals(Object o) {
		if (o instanceof Clue) {
			Clue c = (Clue) o;
			return c.location.equals(location)
					&& c.graphicFilepath.equals(graphicFilepath)
					&& c.trackableFilepath.equals(trackableFilepath);
		}
		return false;
	}

	public static class Location implements JsonWritable {
		// latitude in microdegrees
		private int latitude;
		// longitude in microdegrees
		private int longitude;

		// TODO: add bearing/pose

		public Location() {
			latitude = 0;
			longitude = 0;
		}

		public Location(int _lat, int _long) {
			latitude = _lat;
			longitude = _long;
		}

		public void writeJson(JsonWriter writer) throws IOException {
			writer.beginObject();

			writer.name("lat").value(latitude);
			writer.name("long").value(longitude);

			writer.endObject();
		}

		public void readJson(JsonReader reader) throws IOException {
			reader.beginObject();
			while (reader.hasNext()) {
				String name = reader.nextName();
				if (name.equals("lat")) {
					latitude = reader.nextInt();
				} else if (name.equals("long")) {
					longitude = reader.nextInt();
				}
			}
			reader.endObject();
		}

		@Override
		public boolean equals(Object o) {
			if (o instanceof Location) {
				Location loc = (Location) o;
				return latitude == loc.latitude && longitude == loc.longitude;
			}
			return false;
		}

		@Override
		public String toString() {
			return "(" + latitude + "," + longitude + ")";
		}

		@Override
		public int hashCode() {
			return latitude ^ longitude;
		}
	}
}
