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

import java.util.ArrayList;
import java.util.HashMap;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.text.InputFilter;
import android.view.Menu;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import applab.client.ApplabActivity;
import applab.client.search.R;

/**
 * Displays a list view of the contents of the inbox. This includes both recent searches, and unsent searches
 * 
 */
public class InboxListActivity extends BaseSearchActivity {
    private InboxAdapter inbox;

    /** Mapping from list view index to database row ID */
    private HashMap<Long, Long> listIndexToDatabaseRowMap = new HashMap<Long, Long>();

    private int inboxCount;

    @Override
    protected String getTitleName() {
        return getString(R.string.inbox_title) + "(" + this.inboxCount + ")";
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        boolean configurationChanged = false;
        if (savedInstanceState != null) {
            configurationChanged = savedInstanceState.getBoolean("changed");
        }

        boolean blockAccess = true;
        Bundle extras = this.getIntent().getExtras();
        if (extras != null) {
            blockAccess = extras.getBoolean("block", true);
        }

        // If we're coming from the home screen do not ask for id confirmation
        if (blockAccess && !configurationChanged) {
            showAccessDialog();
        }

        this.inbox = new InboxAdapter(this);
        this.inbox.open();

        Cursor cursor = this.inbox.fetchAllRecords();

        // store the count for use during the activity's lifetime
        this.inboxCount = cursor.getCount();

        ArrayList<String> inboxItems = new ArrayList<String>();
        long id = 0;
        while (cursor.moveToNext()) {
            int titleColumn = cursor.getColumnIndexOrThrow(InboxAdapter.KEY_TITLE);
            int idColumn = cursor.getColumnIndexOrThrow(InboxAdapter.KEY_ROWID);
            int statusColumn = cursor.getColumnIndexOrThrow(InboxAdapter.KEY_STATUS);

            String title = cursor.getString(titleColumn);
            String status = cursor.getString(statusColumn);
            long rowId = cursor.getLong(idColumn);

            String elipses = "";
            if (title.length() > 50) {
                elipses = "...";
                title = title.substring(0, 50);
            }

            if (status.contentEquals("Incomplete")) {
                inboxItems.add(title + elipses + "\n[Incomplete...]");
            }
            else {
                inboxItems.add(title + elipses);
            }

            this.listIndexToDatabaseRowMap.put(id, rowId);
            ++id;
        }

        // detect the empty inbox case
        if (inboxItems.size() == 0) {
            inboxItems.add(getString(R.string.inbox_empty));
        }
        cursor.close();
        this.inbox.close();

        ListView listView = new ListView(this);
        listView.setAdapter(new ArrayAdapter<String>(this, R.layout.list_inbox, inboxItems));
        listView.setOnItemClickListener(new OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (inboxCount > 0) {
                    Intent displayResult = new Intent(view.getContext(), DisplaySearchResultsActivity.class);
                    long rowId = listIndexToDatabaseRowMap.get(id);
                    displayResult.putExtra("rowId", rowId);
                    displayResult.putExtra("fromInbox", true);
                    startActivity(displayResult);
                    finish();
                }
            }
        });

        this.setContentView(listView);
    }

    /**
     * creates a dialog that confirms user credentials before accessing the inbox
     * 
     * @return a confirmation alert dialog
     */
    public void showAccessDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        final EditText farmerNameEditBox = new EditText(this);
        if (GlobalConstants.intervieweeName != null) {
            farmerNameEditBox.setText(GlobalConstants.intervieweeName);
        }
        farmerNameEditBox.setFilters(new InputFilter[] { BaseSearchActivity.getFarmerInputFilter() });
        builder.setMessage(getString(R.string.confirm_id)).setCancelable(false)
                .setPositiveButton(getString(R.string.confirm_button),
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                String input = farmerNameEditBox.getText().toString().trim();
                                if (input.length() > 0) {
                                    dialog.cancel();
                                    if (checkId(input)) {
                                        GlobalConstants.intervieweeName = input;
                                    }
                                    else {
                                        showTestSearchDialog(new DialogInterface.OnClickListener() {
                                            public void onClick(DialogInterface dialog, int id) {
                                                GlobalConstants.intervieweeName = "TEST";
                                                dialog.cancel();
                                            }
                                        }, new DialogInterface.OnClickListener() {
                                            public void onClick(DialogInterface dialog, int id) {
                                                dialog.cancel();
                                                showAccessDialog();
                                            }
                                        });
                                    }
                                }
                                else {
                                    ApplabActivity.showToast(R.string.empty_text);
                                    showAccessDialog();
                                }
                            }
                        }).setNegativeButton(getString(R.string.cancel_button),
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                dialog.cancel();
                                finish();
                            }
                        }).setView(farmerNameEditBox);
        AlertDialog alert = builder.create();
        alert.show();
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        boolean result = super.onPrepareOptionsMenu(menu);
        if (this.inboxCount == 0) {
            menu.findItem(GlobalConstants.DELETE_ID).setEnabled(false);
        }
        menu.removeItem(GlobalConstants.ABOUT_ID);
        menu.removeItem(GlobalConstants.SETTINGS_ID);
        menu.removeItem(GlobalConstants.INBOX_ID);
        return result;
    }
}
