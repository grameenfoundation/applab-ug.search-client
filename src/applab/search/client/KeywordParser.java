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

import java.io.IOException;

import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.CharacterData;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import android.content.ContentValues;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import applab.client.XmlHelpers;

/**
 * A custom XML parser that also sorts and adds content to search sequence database.
 */

public class KeywordParser implements Runnable {
    /** for debugging purposes in adb logcat */
    private static final String LOG_TAG = "KeywordParser";
    private Context applicationContext;
    private Storage storage;

    /** handler to which progress updates are sent */
    private Handler progressHandler;

    private ContentValues insertValues;

    /** table to update */
    private String dbTable;

    private String keywords;

    /** handler to which responses are sent */
    private Handler responseHandler;

    public KeywordParser(Context applicationContext, Handler progressHandler,
                         Handler responseHandler, String newKeywords) {
        this.applicationContext = applicationContext;
        this.keywords = newKeywords;
        this.responseHandler = responseHandler;
        this.progressHandler = progressHandler;
    }

    @Override
    public void run() {
        boolean error = false;

        try {
            Document xmlDocument = XmlHelpers.parseXml(this.keywords);
            NodeList nodes = xmlDocument.getElementsByTagName("Keyword");
            int nodeCount = nodes.getLength();
            Log.d(LOG_TAG, "Total nodes: " + nodeCount);
            Bundle b = new Bundle();

            // Show parse dialog (send signal with total node count)
            Message msg = responseHandler.obtainMessage();
            b.putInt("nodeCount", nodeCount);
            msg.what = GlobalConstants.KEYWORD_PARSE_GOT_NODE_TOTAL;
            msg.setData(b);
            responseHandler.sendMessage(msg);

            storage = new Storage(applicationContext);
            storage.open();
            dbTable = getInactiveTable();
            Log.d(LOG_TAG, "Using TABLE: " + dbTable);
            insertValues = new ContentValues();
            android.util.Log.e("BG","Start parse");
            // iterate through nodes
            for (int i = 0; i < nodeCount; i++) {
                Element element = (Element)nodes.item(i);
                NodeList word = element.getElementsByTagName("Keyword");
                Element line = (Element)word.item(0);
                store(getCharacterDataFromElement(line).split(" "));
                // For now if keywords are not passed in we're not updating
                // on progress
                if (keywords != null) {

                    Message msg1 = progressHandler.obtainMessage();
                    b.putInt("node", i);
                    msg1.setData(b);
                    progressHandler.sendMessage(msg1);
                }
            }

            // mark this table as valid
            if (storage.validateTable(dbTable)) {
                if (dbTable.contentEquals(GlobalConstants.DATABASE_TABLE)) {
                    // discard data in the other table
                    storage.deleteAll(GlobalConstants.DATABASE_TABLE2);
                    // Notify handler: database initialization complete
                    responseHandler
                                .sendEmptyMessage(GlobalConstants.KEYWORD_PARSE_SUCCESS);
                    Log.d(LOG_TAG, "DELETED TABLE: "
                                + GlobalConstants.DATABASE_TABLE2);
                }
                else {
                    // discard data in the other table
                    storage.deleteAll(GlobalConstants.DATABASE_TABLE);
                    responseHandler
                                .sendEmptyMessage(GlobalConstants.KEYWORD_PARSE_SUCCESS);
                    Log.d(LOG_TAG, "DELETED TABLE: "
                                + GlobalConstants.DATABASE_TABLE);
                }

            }// TODO else let the handler know
            Log.d(LOG_TAG, "Finished Parsing Keywords ...");
        }
        catch (IOException e) {
            responseHandler.sendEmptyMessage(GlobalConstants.KEYWORD_PARSE_ERROR);
            error = true;
            Log.d(LOG_TAG, "IOException: " + e);
        }
        catch (IllegalStateException e) {
            responseHandler.sendEmptyMessage(GlobalConstants.KEYWORD_PARSE_ERROR);
            error = true;
            Log.d(LOG_TAG, "IllegalStateException: " + e);
        }
        catch (SAXException e) {
            responseHandler.sendEmptyMessage(GlobalConstants.KEYWORD_PARSE_ERROR);
            error = true;
            Log.d(LOG_TAG, "SAXException: " + e);
        }
        catch (ParserConfigurationException e) {
            responseHandler.sendEmptyMessage(GlobalConstants.KEYWORD_PARSE_ERROR);
            error = true;
            Log.d(LOG_TAG, "ParserConfigurationException: " + e);
        }
        finally {
            if (error) {
                Log.d(LOG_TAG, "ROLL BACK");
                storage.deleteAll(dbTable);
            }
            storage.close();
        }
    }

    /**
     * select a non active table to update
     * 
     * @return the name of the table to update
     */
    private String getInactiveTable() {
        // choose an empty or invalidated table
        if (this.storage.tableExistsAndIsValid(GlobalConstants.DATABASE_TABLE)) {
            return GlobalConstants.DATABASE_TABLE2;
        }
        else {
            return GlobalConstants.DATABASE_TABLE;
        }
    }

    /**
     * insert data into table
     * 
     * @param values
     *            a content value pair
     */
    private void store(String[] values) {
        for (int i = 0; i < values.length; i++) {
            insertValues.put("col" + Integer.toString(i), values[i].replace("_", " "));
        }
        storage.insertKeyword(dbTable, insertValues);
        insertValues.clear();
    }// TODO store() should indicate whether insert was successful. On failure

    // mark as dirty.

    /**
     * obtains characters in a keyword tag
     * 
     * @param e
     *            The document element interface.
     * @return keywords string
     */
    private String getCharacterDataFromElement(Element e) {
        Node child = e.getFirstChild();
        if (child instanceof CharacterData) {
            CharacterData cd = (CharacterData)child;
            return cd.getData();
        }
        return null;// TODO Malformed XML error
    }
}
