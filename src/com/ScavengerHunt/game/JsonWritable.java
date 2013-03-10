package com.ScavengerHunt.game;
import java.io.IOException;

import android.util.JsonReader;
import android.util.JsonWriter;

/**
 * @author kvudata
 * A JsonWritable can output its data in JSON and construct itself from JSON
 * TODO: Json objects for app data should probably be saved in internal storage,
 * but for now use external storage for debugging purposes.
 * See http://developer.android.com/guide/topics/data/data-storage.html#filesExternal
 */
public interface JsonWritable {
	/**
	 * Given an output stream, serialize data to stream.
	 * @param out
	 * @throws IOException
	 */
	public void writeJson(JsonWriter out) throws IOException;
	/**
	 * Given an input stream, return the T object read from the stream.
	 * @param in
	 * @return
	 * @throws IOException
	 */
	public void readJson(JsonReader in) throws IOException;
}
