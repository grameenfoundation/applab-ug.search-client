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

import java.io.IOException;
import java.io.StringReader;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.CharacterData;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import android.content.ContentValues;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;

/**
 * A custom XML parser that also sorts and adds content to search sequence
 * database.
 */

public class KeywordParser implements Runnable {
	/** for debugging purposes in adb logcat */
	private static final String TAG = "Parse";
	private Context applicationContext;
	private Storage storage;
	private SearchActivity searchActivity;
	private MainMenuActivity mainMenuActivity;
	private DisplaySearchResultsActivity displayActivity;
	private InboxListActivity inboxListActivity;
	private AboutActivity aboutActivity;

	/** indicator for calling activity */
	private int parent;

	/** handler to which progress updates are sent */
	private Handler progressHandler;

	private ContentValues insertValues;

	/** table to update */
	private String dbTable;

	/** handler to which responses are sent */
	private Handler responseHandler;

	public KeywordParser(Context appCntxt, Handler progressHandler,
			Handler activityHandler) {
		this.applicationContext = appCntxt;
		this.progressHandler = progressHandler;
		this.responseHandler = responseHandler;
	}

	@Override
	public void run() {
		boolean error = false;
		double percent = 0.0;
		double total;
		try {
			storage = new Storage(applicationContext);
			storage.open();
			dbTable = selectTable();
			android.util.Log.d(TAG, "Using TABLE: " + dbTable);
			insertValues = new ContentValues();

			try {
				DocumentBuilderFactory dbf = DocumentBuilderFactory
						.newInstance();
				DocumentBuilder db = dbf.newDocumentBuilder();
				InputSource is = new InputSource();
				is.setCharacterStream(new StringReader(Global.data));
				Document doc = db.parse(is);
				NodeList nodes = doc.getElementsByTagName("Keyword");
				int nodeCount = nodes.getLength();
				android.util.Log.d(TAG, "Total nodes: " + nodeCount);
				total = (double) nodeCount;
				Bundle b = new Bundle();

				// iterate through nodes
				for (int i = 0; i < nodeCount; i++) {
					Element element = (Element) nodes.item(i);
					NodeList word = element.getElementsByTagName("Keyword");
					Element line = (Element) word.item(0);
					store(getCharacterDataFromElement(line).split(" "));

					if (i > 0) {
						percent = ((double) i / total) * 100.0;
					}
					Message msg1 = progressHandler.obtainMessage();
					b.putInt("node", (int) percent);
					msg1.setData(b);
					progressHandler.sendMessage(msg1);
				}

			} catch (IOException e) {
				responseHandler.sendEmptyMessage(Global.KEYWORD_PARSE_ERROR);
				error = true;
				android.util.Log.d(TAG, "IOException: " + e);
			} catch (IllegalStateException e) {
				responseHandler.sendEmptyMessage(Global.KEYWORD_PARSE_ERROR);
				error = true;
				android.util.Log.d(TAG, "IllegalStateException: " + e);
			} catch (SAXException e) {
				responseHandler.sendEmptyMessage(Global.KEYWORD_PARSE_ERROR);
				error = true;
				android.util.Log.d(TAG, "SAXException: " + e);
			} catch (ParserConfigurationException e) {
				responseHandler.sendEmptyMessage(Global.KEYWORD_PARSE_ERROR);
				error = true;
				android.util.Log.d(TAG, "ParserConfigurationException: " + e);
			}
			if (error) {
				android.util.Log.d(TAG, "ROLL BACK");
				storage.deleteAll(dbTable);
				;
			} else {
				// mark this table as valid
				if (storage.validateTable(dbTable)) {
					if (dbTable.contentEquals(Global.DATABASE_TABLE)) {
						// discard data in the other table
						storage.deleteAll(Global.DATABASE_TABLE2);
						// Notify handler: database initialization complete
						responseHandler.sendEmptyMessage(Global.KEYWORD_PARSE_SUCCESS);
						android.util.Log.d(TAG, "DELETED TABLE: "
								+ Global.DATABASE_TABLE2);
					} else {
						// discard data in the other table
						storage.deleteAll(Global.DATABASE_TABLE);
						responseHandler.sendEmptyMessage(Global.KEYWORD_PARSE_SUCCESS);
						android.util.Log.d(TAG, "DELETED TABLE: "
								+ Global.DATABASE_TABLE);
					}

				}// TODO else let the handler know

			}
		} finally {
			storage.close();
		}

	}

	/**
	 * select a non active table to update
	 * 
	 * @return the name of the table to update
	 */
	private String selectTable() {
		// choose an empty or invalidated table
		if (storage.isEmpty(Global.DATABASE_TABLE)) {
			return Global.DATABASE_TABLE;
		} else if (storage.checkTable(Global.DATABASE_TABLE) < 1) {
			return Global.DATABASE_TABLE;
		} else {
			return Global.DATABASE_TABLE2;
		}
	}

	/**
	 * insert data into table
	 * 
	 * @param values
	 *            a content value pair
	 */
	public void store(String[] values) {
		for (int i = 0; i < values.length; i++) {
			insertValues.put("col" + Integer.toString(i), values[i].replace(
					"_", " "));
		}
		storage.insertKeyword(dbTable, insertValues);
		insertValues.clear();
	}

	/**
	 * Send message to calling activity's message handler and close database.
	 */
	public void sendMessage(int m) {
		switch (parent) {
		case 0:

			mainMenuActivity.connectHandle.sendEmptyMessage(m);
			break;
		case 1:

			searchActivity.connectHandle.sendEmptyMessage(m);
			break;
		case 2:

			displayActivity.connectHandle.sendEmptyMessage(m);
			break;
		case 3:

			inboxListActivity.connectHandle.sendEmptyMessage(m);
			break;
		case 4:

			aboutActivity.connectHandle.sendEmptyMessage(m);
			break;
		}
	}

	/**
	 * obtains characters in a keyword tag
	 * 
	 * @param e
	 *            The document element interface.
	 * @return keywords string
	 */
	public static String getCharacterDataFromElement(Element e) {
		Node child = e.getFirstChild();
		if (child instanceof CharacterData) {
			CharacterData cd = (CharacterData) child;
			return cd.getData();
		}
		return null;// TODO Malformed XML error
	}

}
