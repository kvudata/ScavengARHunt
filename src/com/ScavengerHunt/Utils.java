package com.ScavengerHunt;

import java.io.File;
import java.io.FilenameFilter;
import java.util.Arrays;

import android.util.Log;

public class Utils {
	private static final String TAG = "ScavengerHunt.Utils";
	private static int huntNum = 1;
	
	private static String scavHuntDir = "/sdcard/scavengARHunt/";
	
	static {
		// init huntNum
		String[] dirs = listHuntDirs();
		Log.v(TAG, "Existing hunt directories: " + Arrays.toString(dirs));
		if (dirs.length == 0) {
			huntNum = 0;
		} else {
			String lastDir = dirs[dirs.length-1];
			huntNum = Integer.parseInt(lastDir.substring(4));
		}
	}
	
	// return hunt dirs in sorted order
	public static String[] listHuntDirs() {
		File dir = new File(scavHuntDir);
		String[] huntDirs = dir.list(new FilenameFilter() {
			public boolean accept(File dir, String name) {
				// hunt directories are of the form 'huntXX' where XX is a #
				return name.startsWith("hunt");
			}
		});
		Arrays.sort(huntDirs);
		return huntDirs;
	}
	
	public static String getHuntsBaseDir() {
		return scavHuntDir;
	}
	
	public static String getNewHuntDataDir() {
		huntNum++;
		return scavHuntDir + "hunt" + huntNum + "/";
	}
}
