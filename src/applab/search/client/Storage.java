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

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

/**
 * An adapter for the search keywords database
 */
public class Storage {
    public static final String KEY_ROWID = "_id";
    public static final String KEY_VALIDITY = "validity";
    public static final String KEY_ORDER = "ordering";
    public static final String KEY_CATEGORY = "category";
    public static final String KEY_CONTENT = "content";
    public static final String KEY_UPDATED = "updated";
    public static final String KEY_ATTRIBUTION = "attribution";
    private static final String DATABASE_NAME = "search";
    private static final int DATABASE_VERSION = 4;
    private static final int SEQUENCES = 32;

    private DatabaseHelper databaseHelper;
    private SQLiteDatabase database;

    /** the application context in which we are working */
    private final Context context;

    public Storage(Context context) {
        this.context = context;
    }

    /**
     * Attempt to open @DATABASE_NAME database
     * 
     * @return Database object
     * @throws SQLException
     */
    public Storage open() throws SQLException {
        this.databaseHelper = new DatabaseHelper(context);
        this.database = databaseHelper.getWritableDatabase();
        return this;
    }

    /**
     * Disconnect database
     */
    public void close() {
        databaseHelper.close();
    }

    /**
     * Returns SQL string for table creation
     * 
     * @return the SQL statement.
     */
    private static String generateCreateTableSqlCommand(String table) {
        StringBuilder sqlCommand = new StringBuilder();
        sqlCommand.append("create table " + table);
        sqlCommand.append(" (_id INTEGER PRIMARY KEY, " + KEY_VALIDITY + " SMALLINT DEFAULT 0, " + KEY_ORDER + " SMALLINT DEFAULT 0, "
                + KEY_CONTENT + " TEXT DEFAULT 'Content Unavailable', " + KEY_CATEGORY + " VARCHAR DEFAULT NULL, " + KEY_ATTRIBUTION
                + " VARCHAR DEFAULT NULL, " + KEY_UPDATED + " VARCHAR DEFAULT NULL");
        for (int i = 0; i < SEQUENCES; i++) {
            sqlCommand.append(", col" + i + " VARCHAR DEFAULT NULL");
        }
        sqlCommand.append(" );");
        return sqlCommand.toString();
    }

    /**
     * Select search menu options. Options are the search menu items that the user can select from during a search
     * activity. e.g. Animals, Crops, Farm Inputs, Regional Weather Info are menu options.
     * 
     * @param table
     *            the currently active table to query
     * @param optionColumn
     *            the search keywords table field
     * @param condition
     *            the conditional SQL string
     * @return A cursor pointing before the first element of the result set.
     */
    public Cursor selectMenuOptions(String table, String optionColumn, String condition) {
        return database.query(true, table, new String[] { optionColumn }, condition,
                null, null, null, KEY_ORDER + " DESC", null);
    }

    public boolean deleteEntry(String table, String id) {
        return database.delete(table, KEY_ROWID + "=" + id, null) > 0;
    }

    public boolean insertContent(String table, ContentValues values) {
        return database.replace(table, null, values) > 0;
    }

    /**
     * Remove all table rows
     * 
     * @return the number of rows affected
     */
    public int deleteAll(String table) {
        return database.delete(table, null, null);
    }

    /**
     * checks if the given table exists and has valid data.
     */
    public boolean tableExistsAndIsValid(String table) {
        Cursor cursor = database.query(table, new String[] { KEY_ROWID, KEY_CATEGORY }, null,
                null, null, null, null, "1");
        boolean isValid = false;
        if (cursor.moveToFirst()) {
            // simple validation: check if a category is not null
            int columnIndex = cursor.getColumnIndexOrThrow(KEY_CATEGORY);
            if (cursor.getString(columnIndex) != null) {
                return true;
            }
        }
        cursor.close();
        return isValid;
    }

    private static class DatabaseHelper extends SQLiteOpenHelper {
        private DatabaseHelper(Context context) {
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
        }

        @Override
        public void onCreate(SQLiteDatabase database) {
            // Create keywords table
            database.execSQL(generateCreateTableSqlCommand(GlobalConstants.DATABASE_TABLE));
        }

        @Override
        public void onUpgrade(SQLiteDatabase database, int oldVersion, int newVersion) {
            Log.w("StorageAdapter", "***Upgrading database from version*** "
                    + oldVersion + " to " + newVersion
                    + ", which will destroy all old data");
            database.execSQL("DROP TABLE IF EXISTS " + GlobalConstants.DATABASE_TABLE);
            database.execSQL("DROP TABLE IF EXISTS " + GlobalConstants.DATABASE_TABLE2);
            onCreate(database);
        }
    }
}
