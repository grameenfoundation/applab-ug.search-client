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
package applab.search.client;

import android.view.Menu;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;
import applab.client.Handset;

/**
 * Shows the about page for our application TODO: can we factor out the common parts into CommonClient
 * (ApplabAboutActivity?)
 * 
 */
public class AboutActivity extends BaseSearchActivity {
    private Button closeButton;

    @Override
    public void onResume() {
        super.onResume();
        setContentView(R.layout.text_view);
        setTitle(getString(R.string.about_activity));
        TextView phoneId = (TextView)findViewById(R.id.phone_id);
        phoneId.setText("Phone ID: " + Handset.getImei());
        TextView nameAndVersion = (TextView)findViewById(R.id.name_version);
        nameAndVersion.setText(getString(R.string.app_name) + "\nVersion: " + getString(R.string.app_version));

        TextView releaseDate = (TextView)findViewById(R.id.release);
        releaseDate.setText("Release Date: " + getString(R.string.release_date));

        TextView info = (TextView)findViewById(R.id.info);
        info.setText(getString(R.string.info));

        this.closeButton = (Button)findViewById(R.id.close);
        this.closeButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                finish();
            }
        });
    }

    // Remove unnecessary menu items for this activity
    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        boolean result = super.onPrepareOptionsMenu(menu);
        // Disable new search option if no interviewee name has been supplied
        if (Global.intervieweeName == null) {
            menu.findItem(Global.RESET_ID).setEnabled(false);
        }
        menu.removeItem(Global.ABOUT_ID);
        menu.removeItem(Global.SETTINGS_ID);
        menu.removeItem(Global.DELETE_ID);
        return result;
    }
}
