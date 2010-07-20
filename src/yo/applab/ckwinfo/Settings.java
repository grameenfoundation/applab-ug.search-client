/**
 * Copyright (C) 2010 Grameen Foundation
Licensed under the Apache License, Version 2.0 (the "License"); you may not
use this file except in compliance with the License. You may obtain a copy of
the License at http://www.apache.org/licenses/LICENSE-2.0
Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
License for the specific language governing permissions and limitations under
the License.
 */

package yo.applab.ckwinfo;

import java.net.MalformedURLException;
import java.net.URL;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.PreferenceActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

public class Settings extends PreferenceActivity implements
OnSharedPreferenceChangeListener{
	public static String KEY_SERVER = "server";
        
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preferences);
        setTitle("Settings");
        updateServer();
    }
    
    private void updateServer() {
		EditTextPreference editTextPreferences = (EditTextPreference) this
				.getPreferenceScreen().findPreference(KEY_SERVER);
		String urlString = editTextPreferences.getText();
		urlString = urlString.trim();
		if (!urlString.endsWith("/")) {
			urlString = urlString.concat("/");
		}
		if (isValidUrl(urlString)) {
			editTextPreferences.setText(urlString);
			editTextPreferences.setSummary(urlString);
		} else {
			editTextPreferences.setText((String) editTextPreferences.getSummary());
			Toast.makeText(getApplicationContext(), "Sorry, invalid URL!",
					Toast.LENGTH_SHORT).show();
		}
	}
      
	 public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
	     updateServer();
	    }
	 
	 private static boolean isValidUrl(String url) {

	        try {
	            new URL(url);
	            return true;
	        } catch (MalformedURLException e) {
	            return false;
	        }

	    }
	 
	 @Override
		public boolean onCreateOptionsMenu(Menu menu) {
			// TODO Auto-generated method stub
			boolean result = super.onCreateOptionsMenu(menu);
			
			menu.add(0, Global.BACK_ID, 0,
							getString(R.string.menu_back)).setIcon(R.drawable.done);			
			return result;
		}

		@Override
		public boolean onOptionsItemSelected(MenuItem item) {
			// TODO Auto-generated method stub
			switch (item.getItemId()) {
			
			case Global.BACK_ID:				
				finish();
				return true;
			}
			return super.onOptionsItemSelected(item);
		}
	 
	  @Override
	    protected void onPause() {
	        super.onPause();
	        getPreferenceScreen().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(
	                this);
	    }


	    @Override
	    protected void onResume() {
	        super.onResume();
	        getPreferenceScreen().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
	    }
}

