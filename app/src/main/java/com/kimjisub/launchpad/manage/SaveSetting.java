package com.kimjisub.launchpad.manage;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;

import static android.content.Context.MODE_PRIVATE;
import static android.preference.PreferenceManager.getDefaultSharedPreferences;

/**
 * Created by kimjisub on 2017. 7. 14..
 */

public class SaveSetting {
	
	public static class LaunchpadConnectMethod {
		
		public static void save(Context context, int value) {
			SharedPreferences pref = context.getSharedPreferences("data", MODE_PRIVATE);
			SharedPreferences.Editor editor = pref.edit();
			editor.putInt("LaunchpadConnectMethod", value);
			editor.apply();
		}
		
		public static int load(Context context) {
			SharedPreferences pref = context.getSharedPreferences("data", MODE_PRIVATE);
			return pref.getInt("LaunchpadConnectMethod", 0);
		}
	}
	
	public static class PrevNotice {
		
		public static void save(Context context, String value) {
			SharedPreferences pref = context.getSharedPreferences("data", MODE_PRIVATE);
			SharedPreferences.Editor editor = pref.edit();
			editor.putString("PrevNotice", value);
			editor.apply();
		}
		
		public static String load(Context context) {
			SharedPreferences pref = context.getSharedPreferences("data", MODE_PRIVATE);
			return pref.getString("PrevNotice", "");
		}
	}
	
	public static class FileExplorerPath {
		
		public static void save(Context context, String value) {
			SharedPreferences pref = context.getSharedPreferences("data", MODE_PRIVATE);
			SharedPreferences.Editor editor = pref.edit();
			editor.putString("FileExplorerPath", value);
			editor.apply();
		}
		
		public static String load(Context context) {
			SharedPreferences pref = context.getSharedPreferences("data", MODE_PRIVATE);
			String url = pref.getString("FileExplorerPath", System.getenv("SECONDARY_STORAGE") + "/Download");
			if (!new java.io.File(url).isDirectory())
				url = Environment.getExternalStorageDirectory().getPath();
			if (!new java.io.File(url).isDirectory())
				url = "/";
			
			return url;
		}
	}
	
	public static class IsUsingSDCard {
		
		public static String URL;
		
		public static void save(Context context, boolean value) {
			SharedPreferences pref = getDefaultSharedPreferences(context);
			SharedPreferences.Editor editor = pref.edit();
			editor.putBoolean("IsUsingSDCard", value);
			editor.apply();
		}
		
		public static boolean load(Context context) {
			SharedPreferences pref = getDefaultSharedPreferences(context);
			Boolean isSDCard = pref.getBoolean("IsUsingSDCard", false);
			
			URL = Environment.getExternalStorageDirectory().getPath() + "/Unipad";
			if (isSDCard) {
				if (FileManager.isSDCardAvalable())
					URL = FileManager.getExternalMounts() + "/Unipad";
				else
					save(context, false);
			}
			return isSDCard;
		}
	}
	
	public static class PrevAdsShowTime {
		
		public static void save(Context context, long value) {
			SharedPreferences pref = context.getSharedPreferences("data", MODE_PRIVATE);
			SharedPreferences.Editor editor = pref.edit();
			editor.putLong("PrevAdsShowTime", value);
			editor.commit();
		}
		
		public static long load(Context context) {
			SharedPreferences pref = context.getSharedPreferences("data", MODE_PRIVATE);
			return pref.getLong("PrevAdsShowTime", 0);
		}
	}
	
	public static class SelectedTheme {
		
		public static void save(Context context, String value) {
			SharedPreferences pref = context.getSharedPreferences("data", MODE_PRIVATE);
			SharedPreferences.Editor editor = pref.edit();
			editor.putString("SelectedTheme", value);
			editor.commit();
		}
		
		public static String load(Context context) {
			SharedPreferences pref = context.getSharedPreferences("data", MODE_PRIVATE);
			return pref.getString("SelectedTheme", "");
		}
	}
	
	public static class DefaultFont {
		public static void save(Context context, boolean value) {
			SharedPreferences pref = getDefaultSharedPreferences(context);
			SharedPreferences.Editor editor = pref.edit();
			editor.putBoolean("DefaultFont", value);
			editor.commit();
		}
		
		public static boolean load(Context context) {
			SharedPreferences pref = getDefaultSharedPreferences(context);
			return pref.getBoolean("DefaultFont", true);
		}
	}
	
}
