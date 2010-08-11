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

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

/**
 * An adapter for the inbox database
 */
public class InboxAdapter {
	public static final String KEY_TITLE = "title";
	public static final String KEY_BODY = "body";
	public static final String KEY_LOCATION = "gps";
	public static final String KEY_NAME = "name";
	public static final String KEY_DATE = "date";
	public static final String KEY_ROWID = "_id";
	public static final String KEY_STATUS = "status";
	public static final String KEY_REQUEST = "request";
	private static final String DATABASE_NAME = "resultstorage";
	public static final String INBOX_DATABASE_TABLE = "inbox";
	public static final String ACCESS_LOG_DATABASE_TABLE = "access_logs";
	private static final int DATABASE_VERSION = 3;// XXX release version 2.7
	private DatabaseHelper databaseHelper;
	private SQLiteDatabase database;
	private static final String CREATE_INBOX_DATABASE_TABLE = "create table "
			+ INBOX_DATABASE_TABLE
			+ " (_id integer primary key autoincrement, " + KEY_REQUEST
			+ " VARCHAR, " + KEY_TITLE + " VARCHAR, " + KEY_LOCATION
			+ " VARCHAR, " + KEY_NAME + " VARCHAR, " + KEY_DATE
			+ " DEFAULT CURRENT_TIMESTAMP, " + KEY_STATUS + " VARCHAR, "
			+ KEY_BODY + " text not null);";
	private static final String CREATE_ACCESS_LOG_DATABASE_TABLE = "create table "
			+ ACCESS_LOG_DATABASE_TABLE
			+ " (_id integer primary key autoincrement, "
			+ KEY_REQUEST
			+ " VARCHAR, "
			+ KEY_NAME
			+ " VARCHAR, "
			+ KEY_DATE
			+ " DEFAULT CURRENT_TIMESTAMP);";

	private final Context mContext;

	public InboxAdapter(Context cntxt) {
		this.mContext = cntxt;
	}

	private static class DatabaseHelper extends SQLiteOpenHelper {
		DatabaseHelper(Context context) {
			super(context, DATABASE_NAME, null, DATABASE_VERSION);
		}

		@Override
		public void onCreate(SQLiteDatabase db) {
			db.execSQL(CREATE_INBOX_DATABASE_TABLE);
			db.execSQL(CREATE_ACCESS_LOG_DATABASE_TABLE);
		}

		@Override
		public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
			Log.w("InboxAdapter", "Upgrading database from version "
					+ oldVersion + " to " + newVersion
					+ ", which will destroy all old data");
			db.execSQL("DROP TABLE IF EXISTS " + INBOX_DATABASE_TABLE);
			db.execSQL("DROP TABLE IF EXISTS " + ACCESS_LOG_DATABASE_TABLE);
			onCreate(db);
		}
	}

	/**
	 * Attempt to open @DATABASE_NAME database
	 * 
	 * @return
	 * @throws SQLException
	 */
	public InboxAdapter open() throws SQLException {
		databaseHelper = new DatabaseHelper(mContext);
		database = databaseHelper.getWritableDatabase();
		return this;
	}

	public void close() {
		databaseHelper.close();
	}

	/**
	 * Saves record in database.
	 * 
	 * @param title
	 *            Search keywords
	 * @param body
	 *            Search result
	 * @param name
	 *            Interviewee name
	 * @param gps
	 *            Location where the result was retrieved
	 * @param status
	 *            Whether the record is complete or incomplete the keyword file
	 *            content
	 * @return the table row ID where the item was stored or -1 on fail
	 */
	public long insertRecord(String title, String body, String name,
			String gps, String status, String request) {
		ContentValues initialValues = new ContentValues();
		DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		Date date = new Date();
		initialValues.put(KEY_TITLE, title);
		initialValues.put(KEY_BODY, body);
		initialValues.put(KEY_NAME, name);
		initialValues.put(KEY_LOCATION, gps);
		initialValues.put(KEY_STATUS, status);
		initialValues.put(KEY_REQUEST, request);
		initialValues.put(KEY_DATE, dateFormat.format(date));

		return database.insert(INBOX_DATABASE_TABLE, null, initialValues);
	}

	/**
	 * Stores an inbox access log
	 * 
	 * @param table
	 *            table where to store data
	 * @param content
	 *            field-value pair for storage
	 * @return the table row ID where the item was stored
	 */
	public long insertLog(String table, ContentValues content) {
		return database.insert(table, null, content);
	}

	/**
	 * Get the oldest inbox search log
	 * 
	 * @return A cursor pointing before the first entry
	 */
	public Cursor readLog(String table) {
		Cursor mCursor = database.query(table, new String[] { KEY_ROWID,
				KEY_REQUEST, KEY_DATE, KEY_NAME }, null, null, null, null,
				KEY_DATE + " ASC", "1");
		if (mCursor != null) {
			mCursor.moveToFirst();
		}
		return mCursor;
	}

	/**
	 * retrieves all incomplete searches
	 * 
	 * @return a cursor pointing to the first element in the result set
	 */
	public Cursor getPendingSearches() {
		Cursor mCursor = database.query(INBOX_DATABASE_TABLE, new String[] {
				KEY_ROWID, KEY_REQUEST, KEY_DATE, KEY_LOCATION, KEY_NAME },
				KEY_STATUS + "='Incomplete'", null, null, null, KEY_DATE
						+ " ASC", "1");
		if (mCursor != null) {
			mCursor.moveToFirst();
		}
		return mCursor;
	}

	/**
	 * Deletes a specific record
	 * 
	 * @param rowId
	 * @return true when deleted, false otherwise
	 */
	public boolean deleteRecord(String table, long rowId) {
		return database.delete(table, KEY_ROWID + "=" + rowId, null) > 0;
	}

	/**
	 * Deletes all records from a given table
	 * 
	 * @return true when deleted, false otherwise
	 */
	public boolean deleteAllRecords(String table) {
		return database.delete(table, "1", null) > 0;
	}

	/**
	 * Retrieves all stored records
	 * 
	 * @return a cursor pointing to the first element in the result set
	 */
	public Cursor fetchAllRecords() {
		return database.query(INBOX_DATABASE_TABLE, new String[] { KEY_ROWID,
				KEY_TITLE, KEY_BODY, KEY_DATE, KEY_STATUS }, null, null, null,
				null, KEY_DATE + " DESC");
	}

	/**
	 * Select keywords list
	 * 
	 * @rowId
	 * @return Cursor
	 * @throws SQLException
	 *             if file could not be found/retrieved
	 */
	public Cursor readRecord(long rowId) throws SQLException {
		Cursor mCursor = database.query(true, INBOX_DATABASE_TABLE,
				new String[] { KEY_ROWID, KEY_TITLE, KEY_BODY, KEY_DATE,
						KEY_NAME, KEY_LOCATION, KEY_REQUEST, KEY_STATUS },
				KEY_ROWID + "=" + rowId, null, null, null, null, null);
		if (mCursor != null) {
			mCursor.moveToFirst();
		}
		return mCursor;
	}

	/**
	 * Updates a previously incomplete search
	 * 
	 * @param rowId
	 * @param content
	 * @return
	 */
	public boolean updateRecord(long rowId, String content) {
		DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		Date date = new Date();
		ContentValues args = new ContentValues();
		args.put(KEY_BODY, content);
		args.put(KEY_STATUS, "Complete");
		args.put(KEY_DATE, dateFormat.format(date));
		return database.update(INBOX_DATABASE_TABLE, args, KEY_ROWID + "="
				+ rowId, null) > 0;
	}

}
