package com.ScavengerHunt.game;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

import android.util.JsonReader;

/**
 * @author kvudata
 * 
 * This class stores the state of a hunt in progress. This consists of
 * the associated HuntData as well as the current clue that the user is
 * interpreting/has last seen
 */
public class Hunt {
	private HuntData huntData;
	private Clue currClue;   // current clue being searched for/solved
	private int currClueIdx; // 0-based index of currClue
	
	public Hunt(HuntData data) {
		huntData = data;
		if (huntData.getNumClues() > 0) {
			currClue = huntData.getClue(0);
			currClueIdx = 0;
		} else {
			currClue = null;
			currClueIdx = -1;
		}
	}
	
	public Hunt(String huntDataFile) throws IOException {
		JsonReader in = new JsonReader(new BufferedReader(new FileReader(huntDataFile)));
		huntData = new HuntData();
		huntData.readJson(in);
		if (huntData.getNumClues() > 0) {
			currClue = huntData.getClue(0);
			currClueIdx = 0;
		} else {
			currClue = null;
			currClueIdx = -1;
		}
	}
	
	public Clue getCurrClue() {
		return currClue;
	}
	
	public int getNumClues() {
		return huntData.getNumClues();
	}
	
	public boolean onLastClue() {
		return currClueIdx == huntData.getNumClues()-1;
	}
	
	/**
	 * Doesn't move past last clue i.e. returns null when no more clues
	 * @return next clue
	 */
	public Clue progressToNextClue() {
		if (currClueIdx == huntData.getNumClues()-1) {
			return null;
		} else {
			currClueIdx++;
			currClue = huntData.getClue(currClueIdx);
			return currClue;
		}
	}
}
