package com.ScavengerHunt.game;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import android.util.JsonReader;
import android.util.JsonWriter;

/**
 * @author kvudata
 * 
 * Class which represents the data describing a Scavenger Hunt.
 * Generally, this consists of the set of clues (in order),
 * where each clue may consist of a picture/graphic to display to the user,
 * and associated location of the clue.
 */
public class HuntData implements JsonWritable {
	private List<Clue> clues;
//TODO: change writeJson to not flush here and make sure when write is called, writer is closed afterwards
	public HuntData() {
		super();
		clues = new ArrayList<Clue>();
	}
	
	public Clue getClue(int i) {
		return clues.get(i);
	}
	
	public int getNumClues() {
		return clues.size();
	}
	
	public void addClue(Clue c) {
		clues.add(c);
	}

	public void writeJson(JsonWriter out) throws IOException {
		out.beginObject();
		
		out.name("clues").beginArray();
		for (Clue c : clues) {
			c.writeJson(out);
		}
		out.endArray();
		
		out.endObject();
		out.flush();
	}

	public void readJson(JsonReader in) throws IOException {
		in.beginObject();
		
		in.nextName();
		in.beginArray();
		while (in.hasNext()) {
			Clue c = new Clue();
			c.readJson(in);
			clues.add(c);
		}
		in.endArray();
		
		in.endObject();
	}
	
	@Override
	public boolean equals(Object o) {
		if (o instanceof HuntData) {
			HuntData data = (HuntData)o;
			if (clues.size() == data.clues.size()) {
				for (int i = 0; i < clues.size(); i++) {
					if (!clues.get(i).equals(data.clues.get(i))) {
						return false;
					}
				}
				return true;
			}
		}
		return false;
	}
}
