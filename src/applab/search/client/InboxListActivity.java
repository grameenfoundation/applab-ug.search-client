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
import java.util.Vector;

import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.Spanned;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;

/**
 * Displays a list view of the contents of the inbox.
 * 
 */
public class InboxListActivity extends ListActivity {
    private final String LOG_TAG = "InboxListActivity";
    private InboxAdapter inbox;
    private AlertDialog alertDialog;
    private ListView listView;
    private String activityTitle;

    /** holds list view index - database row ID pairs */
    private Vector<Index> indices = new Vector<Index>();

    /** set true when the inbox is empty */
    private boolean inboxEmpty;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        boolean configurationChanged = false;
        if (savedInstanceState != null) {
            configurationChanged = savedInstanceState.getBoolean("changed");
            if (configurationChanged) {
                Log.w(LOG_TAG, "Activity RESTART");
            }
        }
        Bundle extras = this.getIntent().getExtras();
        boolean block = true;
        if (extras != null) {
            block = extras.getBoolean("block", true);
        }
        this.inbox = new InboxAdapter(this);
        this.inbox.open();
        Cursor cursor = this.inbox.fetchAllRecords();
        startManagingCursor(cursor);
        // inbox.close();
        this.activityTitle = getString(R.string.inbox_title) + "("
                + cursor.getCount() + ")";
        // If we're coming from the home screen do not ask for id confirmation
        if (block && !configurationChanged) {
            accessDialog().show();
        }
        else {
            showCurrentUser();
        }

        ArrayList<String> results = new ArrayList<String>();
        if (cursor.moveToFirst()) {
            String elipses = "";
            long rowId;
            long id = 0;
            while (!cursor.isAfterLast()) {
                int titleColumn = cursor.getColumnIndexOrThrow(InboxAdapter.KEY_TITLE);
                int idColumn = cursor.getColumnIndexOrThrow(InboxAdapter.KEY_ROWID);
                int statusColumn = cursor.getColumnIndexOrThrow(InboxAdapter.KEY_STATUS);

                String title = cursor.getString(titleColumn);
                String status = cursor.getString(statusColumn);
                rowId = cursor.getLong(idColumn);

                if (title.length() > 50) {
                    elipses = "...";
                    title = title.substring(0, 50);
                }
                if (status.contentEquals("Incomplete")) {
                    results.add(title + elipses + "\n[Incomplete...]");
                }
                else {
                    results.add(title + elipses);
                }
                cursor.moveToNext();
                Index myIndex = new Index();
                myIndex.rowId = rowId;
                myIndex.id = id;
                indices.addElement(myIndex);
                ++id;
            }

            cursor.close();
            setListAdapter(new ArrayAdapter<String>(this, R.layout.list_inbox,
                    results));
        }
        else {
            results.add(getString(R.string.inbox_empty));
            setListAdapter(new ArrayAdapter<String>(this, R.layout.list_inbox,
                    results));
            inboxEmpty = true;
        }
        this.listView = getListView();
        this.inbox.close();
        this.listView.setOnItemClickListener(new OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View view,
                                    int position, long id) {
                if (!inboxEmpty) {

                    Intent i = new Intent(view.getContext(),
                            DisplaySearchResultsActivity.class);

                    for (int j = 0; j < indices.size(); j++) {
                        Index myIndex = indices.elementAt(j);
                        if (myIndex.id == id) {
                            i.putExtra("rowId", myIndex.rowId);
                            i.putExtra("fromInbox", true);
                        }

                    }
                    startActivity(i);
                    finish();
                }

            }
        });

    }

    /**
     * creates a dialog that confirms user credentials before accessing the inbox
     * 
     * @return a confirmation alert dialog
     */
    public AlertDialog accessDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        final EditText mEditText = new EditText(this);
        if (Global.intervieweeName != null) {
            mEditText.setText(Global.intervieweeName);
        }
        InputFilter filter = new InputFilter() {
            public CharSequence filter(CharSequence source, int start, int end,
                                       Spanned dest, int dstart, int dend) {
                for (int i = start; i < end; i++) {
                    if (!(((Character.isWhitespace(source.charAt(i)))) || Character
                            .isLetterOrDigit(source.charAt(i)))) {
                        Toast.makeText(getApplicationContext(),
                                getString(R.string.invalid_text),
                                Toast.LENGTH_SHORT).show();
                        return "";
                    }
                }
                return null;
            }
        };
        mEditText.setFilters(new InputFilter[] { filter });
        builder.setMessage(getString(R.string.confirm_id)).setCancelable(false)
                .setPositiveButton(getString(R.string.confirm_button),
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                String input = mEditText.getText().toString()
                                        .trim();
                                if (input.length() > 0) {
                                    dialog.cancel();
                                    Global.intervieweeName = mEditText
                                            .getText().toString().trim();
                                    showCurrentUser();
                                }
                                else {
                                    Toast.makeText(getApplicationContext(),
                                            getString(R.string.empty_text),
                                            Toast.LENGTH_SHORT).show();
                                    accessDialog().show();
                                }
                            }
                        }).setNegativeButton(getString(R.string.cancel_button),
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                dialog.cancel();
                                Intent l = new Intent(getApplicationContext(),
                                        MainMenuActivity.class);
                                startActivity(l);
                                finish();
                            }
                        }).setView(mEditText);
        AlertDialog alert = builder.create();
        return alert;
    }

    /**
     * obtains and displays the currently set user ID in title bar
     * TODO: move this into BaseSearchActivity
     */
    private void showCurrentUser() {
        this.activityTitle = this.activityTitle.concat(" | ");
        if (Global.intervieweeName.length() > 30) {
            this.activityTitle = this.activityTitle.concat(Global.intervieweeName.substring(0, 30));
            this.activityTitle = this.activityTitle.concat("...");
        }
        else {
            this.activityTitle = this.activityTitle.concat(Global.intervieweeName);
        }
        setTitle(this.activityTitle);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {

        boolean result = super.onCreateOptionsMenu(menu);
        menu.add(2, Global.DELETE_ID, 0, getString(R.string.menu_delete))
                .setIcon(R.drawable.delete);

        return result;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        boolean result = super.onPrepareOptionsMenu(menu);
        if (this.inboxEmpty) {
            menu.removeItem(Global.DELETE_ID);
        }
        return result;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case Global.DELETE_ID:
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setMessage(getString(R.string.delete_alert)).setCancelable(
                        false).setPositiveButton("Yes",
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                inbox.open();
                                inbox
                                        .deleteAllRecords(InboxAdapter.INBOX_DATABASE_TABLE);
                                inbox.close();
                                dialog.cancel();
                                Intent j = new Intent(getApplicationContext(),
                                        InboxListActivity.class);
                                j.putExtra("block", false);
                                startActivity(j);
                                finish();

                            }
                        }).setNegativeButton("No",
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                dialog.cancel();
                            }
                        });
                alertDialog = builder.create();
                alertDialog.show();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /** Object for list view index - database row ID pairs */
    private class Index {
        public long id;
        public long rowId;
    }
}
