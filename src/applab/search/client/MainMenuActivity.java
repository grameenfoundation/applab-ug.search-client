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

import android.content.Intent;
import android.text.InputFilter;
import android.text.Spanned;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;

/**
 * The Search application home screen
 * 
 */
public class MainMenuActivity extends BaseSearchActivity {
    private Button inboxButton;
    private Button nextButton;
    private EditText farmerNameEditBox;

    @Override
    public void onResume() {
        // First run parent code
        super.onResume();

        setContentView(R.layout.main_menu);

        this.nextButton = (Button)findViewById(R.id.next_button);
        this.inboxButton = (Button)findViewById(R.id.inbox_button);
        this.inboxButton.setText(getString(R.string.inbox_button));
        this.farmerNameEditBox = (EditText)findViewById(R.id.EditText01);
        this.farmerNameEditBox.setFilters(new InputFilter[] { getFarmerInputFilter() });

        if (!StorageManager.hasKeywords()) {
            this.inboxButton.setEnabled(false);
            this.nextButton.setEnabled(false);
        }

        this.nextButton.setText("Start New Search");
        this.nextButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                onButtonClick(SearchActivity.class);
            }

        });

        this.inboxButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                onButtonClick(InboxListActivity.class);
            }
        });
    }

    /**
     * Common code for handling button clicks for "New Search" and "Recent Searches"
     * 
     * @param classId
     */
    private void onButtonClick(Class<?> classId) {
        String farmerName = farmerNameEditBox.getText().toString().trim();
        if (farmerName.length() > 0) {
            Global.intervieweeName = farmerName;
            Intent nextActivity = new Intent(getApplicationContext(), classId);
            nextActivity.putExtra("block", false);
            switchToActivity(nextActivity);
        }
        else {
            showToast(R.string.empty_text);
        }
    }

    @Override
    protected void onKeywordUpdateComplete() {
        super.onKeywordUpdateComplete();
        if (StorageManager.hasKeywords()) {
            this.inboxButton.setEnabled(true);
            this.nextButton.setEnabled(true);
        }
    }
}