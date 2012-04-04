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
import java.util.List;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;
import applab.client.search.R;

/**
 * An adapter for the search keywords database
 */
public class Storage {
    /* Menu Table Columns */
    public static final String MENU_ROWID_COLUMN = "id";
    public static final String MENU_LABEL_COLUMN = "label";

    /* Menu Item Table Columns */
    public static final String MENU_ITEM_ROWID_COLUMN = "id";
    public static final String MENU_ITEM_LABEL_COLUMN = "label";
    public static final String MENU_ITEM_POSITION_COLUMN = "position";
    public static final String MENU_ITEM_CONTENT_COLUMN = "content";
    public static final String MENU_ITEM_MENUID_COLUMN = "menu_id";
    public static final String MENU_ITEM_PARENTID_COLUMN = "parent_id";
    public static final String MENU_ITEM_ATTACHMENTID_COLUMN = "attachment_id";

    /* Available Farmer Ids Table Columns */
    public static final String AVAILABLE_FARMER_ID_ROWID_COLUMN = "id";
    public static final String AVAILABLE_FARMER_ID_FARMER_ID = "farmer_id";
    public static final String AVAILABLE_FARMER_ID_STATUS = "status";

    /* Farmer Local Cache Table Columns */
    public static final String FARMER_LOCAL_CACHE_ROWID_COLUMN = "id";
    public static final String FARMER_LOCAL_CACHE_FARMER_ID = "farmer_id";
    public static final String FARMER_LOCAL_CACHE_FIRST_NAME = "first_name";
    public static final String FARMER_LOCAL_CACHE_MIDDLE_NAME = "middle_name";
    public static final String FARMER_LOCAL_CACHE_LAST_NAME = "last_name";
    public static final String FARMER_LOCAL_CACHE_DATE_OF_BIRTH = "date_of_birth";
    public static final String FARMER_LOCAL_CACHE_FATHER_NAME = "father_name";

    private static final String DATABASE_NAME = "search";
    private static final int DATABASE_VERSION = 7;
    private static final int SEQUENCES = 32;

    /** keep track of batch size to enable batch inserts **/
    private Integer currentBatchSize = 0;
    private static final Integer MAX_BATCH_SIZE = 200;

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
        if (database.inTransaction()) {
            database.setTransactionSuccessful();
            database.endTransaction();
        }
        databaseHelper.close();
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
        // database.rawQuery("SELECT DISTINCT(" + optionColumn + ") FROM " + table + " WHERE ", selectionArgs)
        return database.query(true, table, new String[] { optionColumn }, condition,
                null, null, null, " MAX(" + MENU_ITEM_POSITION_COLUMN + ") DESC, " + optionColumn + " ASC", null);
    }

    public String selectContent(String menuItemId) {
        Cursor cursor = null;
        try {
            cursor = database.query(true, GlobalConstants.MENU_ITEM_TABLE_NAME, new String[] { Storage.MENU_ITEM_CONTENT_COLUMN },
                    Storage.MENU_ITEM_ROWID_COLUMN + " = ?",
                    new String[] { menuItemId }, null, null, null, null);
            cursor.moveToFirst();
            String content = cursor.getString(0);
            return content;
        }
        finally {
            if (null != cursor) {
                cursor.close();
            }
        }
    }

    public boolean insertContent(String table, ContentValues values) {
        return database.replace(table, null, values) > 0;
    }

    public boolean insertContentInBatch(String table, ContentValues values) {
        // Begin a transaction if we're not yet in one
        if (!database.inTransaction()) {
            database.beginTransaction();
        }

        // Add the current values
        Boolean successful = insertContent(table, values);

        // Increment the currentBatchSize
        currentBatchSize++;

        // Write all the previous data
        if ((currentBatchSize > MAX_BATCH_SIZE) && database.inTransaction()) {
            database.setTransactionSuccessful();
            database.endTransaction();
            currentBatchSize = 0;
        }

        return successful;

        // Note: remember to call storage.close() - it will end any pending transactions, in case there are <
        // MAX_BATCH_SIZE values in the batch
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
    public boolean tableExistsAndIsValid(String table, String idColumn, String labelColumn) {
        Cursor cursor = database.query(table, new String[] { idColumn, labelColumn }, null,
                null, null, null, null, "1");
        boolean isValid = false;
        if (cursor.moveToFirst()) {
            // simple validation: check if the label column is not null
            int columnIndex = cursor.getColumnIndexOrThrow(labelColumn);
            if (cursor.getString(columnIndex) != null) {
                isValid = true;
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
            // Create Menu Table
            database.execSQL(getMenuTableInitializationSql());

            // Create Menu Item Table
            database.execSQL(getMenuItemTableInitializationSql());

            // Create Available Farmer Id Table
            database.execSQL(getAvailableFarmerIdTableInitializationSql());

            // Create Farmer Local Cache Table
            database.execSQL(getFarmerLocalCacheTableInitializationSql());
        }

        /**
         * Returns the SQL string for Menu Table creation
         * 
         * @return String
         */
        private String getMenuTableInitializationSql() {
            StringBuilder sqlCommand = new StringBuilder();
            sqlCommand.append("create table " + GlobalConstants.MENU_TABLE_NAME);
            sqlCommand
                    .append(" (" + Storage.MENU_ROWID_COLUMN + " CHAR(16) PRIMARY KEY, " + Storage.MENU_LABEL_COLUMN + " TEXT NOT NULL);");
            return sqlCommand.toString();
        }

        /**
         * Returns the SQL string for MenuItem Table creation
         * 
         * @return String
         */
        private String getMenuItemTableInitializationSql() {

            StringBuilder sqlCommand = new StringBuilder();
            sqlCommand.append("create table " + GlobalConstants.MENU_ITEM_TABLE_NAME);
            sqlCommand.append(" (" + Storage.MENU_ITEM_ROWID_COLUMN + " CHAR(16) PRIMARY KEY, " + Storage.MENU_ITEM_LABEL_COLUMN
                    + " TEXT NOT NULL, "
                    + Storage.MENU_ITEM_MENUID_COLUMN + " CHAR(16), " + Storage.MENU_ITEM_PARENTID_COLUMN + " CHAR(16), "
                    + Storage.MENU_ITEM_POSITION_COLUMN + " INTEGER, " + Storage.MENU_ITEM_CONTENT_COLUMN + " TEXT, "
                    + Storage.MENU_ITEM_ATTACHMENTID_COLUMN + " CHAR(16), ");
            sqlCommand.append(" FOREIGN KEY(menu_id) REFERENCES " + GlobalConstants.MENU_TABLE_NAME + "(id) ON DELETE CASCADE, ");
            sqlCommand.append(" FOREIGN KEY(parent_id) REFERENCES " + GlobalConstants.MENU_ITEM_TABLE_NAME + "(id) ON DELETE CASCADE ");
            sqlCommand.append(" );");
            return sqlCommand.toString();
        }

        /**
         * Returns the SQL string for AvailableFarmerId Table creation
         * 
         * @return String
         */
        private String getAvailableFarmerIdTableInitializationSql() {

            StringBuilder sqlCommand = new StringBuilder();
            sqlCommand.append("create table " + GlobalConstants.AVAILABLE_FARMER_ID_TABLE_NAME);
            sqlCommand.append(" (" + Storage.AVAILABLE_FARMER_ID_ROWID_COLUMN + " CHAR(16) PRIMARY KEY, "
                    + Storage.AVAILABLE_FARMER_ID_FARMER_ID
                    + " CHAR(16), " + Storage.AVAILABLE_FARMER_ID_STATUS + " INTEGER ");
            sqlCommand.append(" );");
            return sqlCommand.toString();
        }

        /**
         * Returns the SQL string for FarmerLocalCache Table creation
         * 
         * @return String
         */
        private String getFarmerLocalCacheTableInitializationSql() {

            StringBuilder sqlCommand = new StringBuilder();
            sqlCommand.append("create table " + GlobalConstants.FARMER_LOCAL_CACHE_TABLE_NAME);
            sqlCommand.append(" (" + Storage.FARMER_LOCAL_CACHE_ROWID_COLUMN + " CHAR(16) PRIMARY KEY, " + Storage.FARMER_LOCAL_CACHE_FARMER_ID
                    + " CHAR(16), "
                    + Storage.FARMER_LOCAL_CACHE_FIRST_NAME + " CHAR(16), " + Storage.FARMER_LOCAL_CACHE_MIDDLE_NAME + " CHAR(16), "
                    + Storage.FARMER_LOCAL_CACHE_LAST_NAME + " CHAR(16), " + Storage.FARMER_LOCAL_CACHE_DATE_OF_BIRTH + " CHAR(16), "
                    + Storage.FARMER_LOCAL_CACHE_FATHER_NAME + " CHAR(16) ");
            sqlCommand.append(" );");
            return sqlCommand.toString();
        }

        @Override
        public void onUpgrade(SQLiteDatabase database, int oldVersion, int newVersion) {
            Log.w("StorageAdapter", "***Upgrading database from version*** "
                    + oldVersion + " to " + newVersion
                    + ", which will destroy all old data");

            // Get rid of old tables
            database.execSQL("DROP TABLE IF EXISTS keywords");
            database.execSQL("DROP TABLE IF EXISTS keywords2");

            // Get rid of new tables if they exist
            database.execSQL("DROP TABLE IF EXISTS " + GlobalConstants.MENU_TABLE_NAME);
            database.execSQL("DROP TABLE IF EXISTS " + GlobalConstants.MENU_ITEM_TABLE_NAME);
            database.execSQL("DROP TABLE IF EXISTS " + GlobalConstants.FARMER_LOCAL_CACHE_TABLE_NAME);
            database.execSQL("DROP TABLE IF EXISTS " + GlobalConstants.AVAILABLE_FARMER_ID_TABLE_NAME);

            onCreate(database);
        }
    }

    /**
     * Returns a cursor containing top level items for a given menu
     * 
     * @param string
     * @return
     */
    public Cursor getTopLevelMenuItems(String menuId) {
        Cursor itemCursor = database.query(false, GlobalConstants.MENU_ITEM_TABLE_NAME, new String[] { Storage.MENU_ITEM_ROWID_COLUMN,
                Storage.MENU_ITEM_LABEL_COLUMN, Storage.MENU_ITEM_ATTACHMENTID_COLUMN },
                Storage.MENU_ITEM_MENUID_COLUMN + " = ? AND (" + Storage.MENU_ITEM_PARENTID_COLUMN + " IS NULL OR "
                        + Storage.MENU_ITEM_PARENTID_COLUMN + " = '' )", new String[] { menuId },
                null, null, Storage.MENU_ITEM_POSITION_COLUMN + " ASC, " + Storage.MENU_ITEM_LABEL_COLUMN + " ASC", null);
        return itemCursor;
    }

    public Cursor getMenuList() {
        try {
            Cursor cursor = database.query(false, GlobalConstants.MENU_TABLE_NAME, new String[] { Storage.MENU_ROWID_COLUMN,
                    Storage.MENU_LABEL_COLUMN }, null, null, null, null, " " +
                    Storage.MENU_LABEL_COLUMN + " ASC", null);
            return cursor;
        }
        catch (NullPointerException ex) {
            // throws null pointer exception if the application is run for the first time!
            return null;
        }
    }

    int getMenuCount() {
        Cursor cursor = database.rawQuery("SELECT COUNT(*) as total FROM " + GlobalConstants.MENU_TABLE_NAME, null);
        int count = 0;
        if (cursor.moveToFirst()) {
            count = Integer.parseInt(cursor.getString(0));
        }
        cursor.close();
        return count;
    }

    public Cursor getChildMenuItems(String parentMenuItemId) {
        Cursor itemCursor = database.query(false, GlobalConstants.MENU_ITEM_TABLE_NAME, new String[] { Storage.MENU_ITEM_ROWID_COLUMN,
                Storage.MENU_ITEM_LABEL_COLUMN, Storage.MENU_ITEM_ATTACHMENTID_COLUMN },
                Storage.MENU_ITEM_PARENTID_COLUMN + " = ?", new String[] { parentMenuItemId }, null, null,
                Storage.MENU_ITEM_POSITION_COLUMN + " ASC, " + Storage.MENU_ITEM_LABEL_COLUMN + " ASC", null);
        return itemCursor;
    }

    public String getFirstMenuId() {
        Cursor cursor = null;
        try {
            cursor = database.query(false, GlobalConstants.MENU_TABLE_NAME, new String[] { Storage.MENU_ROWID_COLUMN }, null, null,
                    null, null, null, null);
            if (cursor.moveToFirst()) {
                return cursor.getString(0);
            }
            return null;
        }
        finally {
            if (null != cursor) {
                cursor.close();
            }
        }
    }

    public String getNextFarmerId() {
        Cursor cursor = null;
        try {
            cursor = database.query(false, GlobalConstants.AVAILABLE_FARMER_ID_TABLE_NAME, new String[] { Storage.AVAILABLE_FARMER_ID_FARMER_ID }, 
                    Storage.AVAILABLE_FARMER_ID_STATUS + " = ?", new String[] { GlobalConstants.AVAILABLE_FARMER_ID_UNUSED_STATUS },
                    null, null, null, null);
            if (cursor.moveToFirst()) {
                return cursor.getString(0);
            }
            return null;
        }
        finally {
            if (null != cursor) {
                cursor.close();
            }
        }
    }
    
    /**
     * Delete all entries for this id and also where the parent id is this id (delete children too)
     * 
     * @param table
     * @param id
     * @return
     */
    public boolean deleteMenuItemEntry(String itemId) {
        return database.delete(GlobalConstants.MENU_ITEM_TABLE_NAME, Storage.MENU_ITEM_ROWID_COLUMN + "='" + itemId + "'", null) > 0;
    }

    /**
     * Delete all entries in menu table for this id and also where the parent id this id (delete children too)
     * 
     * @param table
     * @param id
     * @return
     */
    public boolean deleteMenuEntry(String menuId) {
        return database.delete(GlobalConstants.MENU_TABLE_NAME, Storage.MENU_ROWID_COLUMN + "= '" + menuId + "'", null) > 0;

    }

    public boolean insertMenu(String table, ContentValues values) {
        return database.replace(table, null, values) > 0;
    }
    
    public boolean deleteUsedFarmerIds() {
        return database.delete(GlobalConstants.AVAILABLE_FARMER_ID_TABLE_NAME, Storage.AVAILABLE_FARMER_ID_STATUS + "=" + 1, null) > 0;
    }
    
    public int getUnusedFarmerIdCount() {
        Cursor cursor = null;
        try {
            cursor = database.rawQuery("SELECT COUNT(*) FROM " + GlobalConstants.AVAILABLE_FARMER_ID_TABLE_NAME + " WHERE status = 0 ", null);
            if (cursor.moveToFirst()) {
                return cursor.getInt(0);
            }
            return 0;
        }
        catch (NullPointerException ex) {
            Log.w("No search database yet: ", "SEARCH");
            return 0;
        }
        finally {
            if (null != cursor) {
                cursor.close();
            }
        }
    }

    public ArrayList<String> getLocalMenuIds() {
        Cursor cursor = getMenuList();
        ArrayList<String> results = new ArrayList<String>();
        if (null == cursor) {
            return results;
        }
        try {
            while (cursor.moveToNext()) {
                results.add(cursor.getString(0));
            }
            return results;
        }
        finally {
            if (cursor != null) {
                cursor.close();
            }
        }

    }

	public String findFarmerIdFromFarmerLocalCacheTable(String farmerFirstName,
			String farmerLastName, String farmerFatherName) {
		Cursor cursor = null;
		try {
			cursor = database.query(true,
					GlobalConstants.FARMER_LOCAL_CACHE_TABLE_NAME,
					new String[] { Storage.FARMER_LOCAL_CACHE_FARMER_ID },
					Storage.FARMER_LOCAL_CACHE_FIRST_NAME + " = ? AND "
							+ Storage.FARMER_LOCAL_CACHE_LAST_NAME
							+ " = ? AND "
							+ Storage.FARMER_LOCAL_CACHE_FATHER_NAME + " = ?",
					new String[] { farmerFirstName, farmerLastName,
							farmerFatherName }, null, null, null, null);

			if (cursor.moveToFirst()) {
				return cursor.getString(0);
			}
			return null;
		} finally {
			if (null != cursor) {
				cursor.close();
			}
		}

	}

	public boolean isFarmerIdInFarmerLocalCacheTable(String farmerId) {
		Cursor cursor = null;
		final String countSql = "SELECT COUNT(*) FROM"
				+ GlobalConstants.FARMER_LOCAL_CACHE_TABLE_NAME + "WHERE"
				+ Storage.FARMER_LOCAL_CACHE_FARMER_ID + "= ?";
		cursor = database.rawQuery(countSql, new String[] { farmerId });

		int count = 0;
		if (cursor.moveToFirst()) {
			count = Integer.parseInt(cursor.getString(0));
		}
		cursor.close();

		if (count > 0) {
			return true;
		} else {
			return false;
		}
	}

	public boolean isFarmerIdSetToUsedInAvailableFarmerIdTable(String farmerId) {
		Cursor cursor = null;
		final String countSql = "SELECT COUNT(*) FROM"
				+ GlobalConstants.AVAILABLE_FARMER_ID_TABLE_NAME + "WHERE"
				+ Storage.FARMER_LOCAL_CACHE_FARMER_ID + "= ? AND "
				+ Storage.AVAILABLE_FARMER_ID_STATUS + "= ?";
		cursor = database.rawQuery(countSql, new String[] { farmerId,
				GlobalConstants.AVAILABLE_FARMER_ID_USED_STATUS });

		int count = 0;
		if (cursor.moveToFirst()) {
			count = Integer.parseInt(cursor.getString(0));
		}
		cursor.close();

		if (count > 0) {
			return true;
		} else {
			return false;
		}
	}
	
	/**
     * Toggle the status of farmer id in the local farmer id database
     *
     * @param farmerId
     *            the farmer id whose status needs to be toggled
     * @param newStatus
     *            the new value of the status field
     * @return 
     *            the number of rows affected by the call to update
     */
    public void toggleFarmerIdStatus(String farmerId, String newStatus) {
        ContentValues farmerIdStatus = new ContentValues();
        farmerIdStatus.put(AVAILABLE_FARMER_ID_STATUS, newStatus);
        database.update(GlobalConstants.AVAILABLE_FARMER_ID_TABLE_NAME, farmerIdStatus, 
                Storage.AVAILABLE_FARMER_ID_FARMER_ID + " = ?", new String[] { farmerId });         
    }
}
