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

import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import android.content.ContentValues;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import applab.client.ApplabActivity;
import applab.client.PropertyStorage;
import applab.client.XmlHelpers;

/**
 * A custom XML parser that also sorts and adds content to search sequence database.
 */

public class KeywordParser implements Runnable {
    /** for debugging purposes in adb logcat */
    private static final String LOG_TAG = "KeywordParser";
    private static final String ADD_TAG = "add";
    private static final String REMOVE_TAG = "remove";
    private static final String VERSION_TAG = "version";
    private static final String ITEM_ORDER = "order";
    private static final String ITEM_KEYWORD = "keyword";
    private static final String ITEM_UPDATED = "updated";
    private static final String ITEM_ATTRIBUTION = "attribution";
    private static final String ITEM_CATEGORY = "category";
    private static final String ITEM_ID = "id";
    private static int progressLevel = 0;
    private Storage storage;

    /** handler to which progress updates are sent */
    private Handler progressHandler;

    private String keywords;

    /** handler to which responses are sent */
    private Handler responseHandler;

    private Bundle bundle;
    private NodeList nodesToAdd;
    private NodeList nodesToRemove;

    public KeywordParser(Handler progressHandler,
                         Handler responseHandler, String newKeywords) {
        this.keywords = newKeywords;
        this.responseHandler = responseHandler;
        this.progressHandler = progressHandler;
    }

    @Override
    public void run() {
        try {
            Document xmlDocument = XmlHelpers.parseXml(this.keywords);
            this.nodesToAdd = xmlDocument.getElementsByTagName(ADD_TAG);
            this.nodesToRemove = xmlDocument.getElementsByTagName(REMOVE_TAG);
            int nodeCount = nodesToAdd.getLength() + nodesToRemove.getLength();
            Log.d(LOG_TAG, "Total nodes: " + nodeCount);
            this.bundle = new Bundle();
            // Show parse dialog (send signal with total node count)
            Message message = responseHandler.obtainMessage();
            this.bundle.putInt("nodeCount", nodeCount);
            message.what = GlobalConstants.KEYWORD_PARSE_GOT_NODE_TOTAL;
            message.setData(bundle);
            this.responseHandler.sendMessage(message);
            this.storage = new Storage(ApplabActivity.getGlobalContext());
            this.storage.open();
            processRemovals(this.nodesToRemove);
            processAdditions(this.nodesToAdd);
            // Save keywords version
            KeywordParser.storeKeywordsVersion(xmlDocument);
            // let UI handler know
            Log.d(LOG_TAG, "Finished Parsing Keywords ...");
            this.responseHandler.sendEmptyMessage(GlobalConstants.KEYWORD_PARSE_SUCCESS);
        }
        catch (IOException e) {
            this.responseHandler.sendEmptyMessage(GlobalConstants.KEYWORD_PARSE_ERROR);
            Log.d(LOG_TAG, "IOException: " + e);
        }
        catch (IllegalStateException e) {
            this.responseHandler.sendEmptyMessage(GlobalConstants.KEYWORD_PARSE_ERROR);
            Log.d(LOG_TAG, "IllegalStateException: " + e);
        }
        catch (SAXException e) {
            this.responseHandler.sendEmptyMessage(GlobalConstants.KEYWORD_PARSE_ERROR);
            Log.d(LOG_TAG, "SAXException: " + e);
        }
        catch (ParserConfigurationException e) {
            this.responseHandler.sendEmptyMessage(GlobalConstants.KEYWORD_PARSE_ERROR);
            Log.d(LOG_TAG, "ParserConfigurationException: " + e);
        }
        finally {
            this.storage.close();
        }
    }

    public void processRemovals(NodeList nodeList) {

        for (int i = 0; i < nodeList.getLength(); i++) {
            NamedNodeMap attributes = nodeList.item(i).getAttributes();

            String rowId = attributes.getNamedItem(ITEM_ID).getNodeValue();
            if (rowId != null && !storage.deleteEntry(GlobalConstants.DATABASE_TABLE, rowId)) {
                Log.e(LOG_TAG, "Failed to remove item. ID= " + rowId);
            }
            incrementProgressLevel();
        }
    }

    public void processAdditions(NodeList nodeList) {
        ContentValues addValues = new ContentValues();

        for (int i = 0; i < nodeList.getLength(); i++) {
            String rowId = null;
            String order = null;
            String category = null;
            String attribution = null;
            String updated = null;
            String keyword = null;
            String content = nodeList.item(i).getFirstChild().getNodeValue();
            NamedNodeMap attributes = nodeList.item(i).getAttributes();

            Node node = attributes.getNamedItem(ITEM_ID);
            if (node != null) {
                rowId = node.getNodeValue();
            }
            node = attributes.getNamedItem(ITEM_ORDER);
            if (node != null) {
                order = node.getNodeValue();
            }
            node = attributes.getNamedItem(ITEM_KEYWORD);
            if (node != null) {
                keyword = node.getNodeValue();
            }
            node = attributes.getNamedItem(ITEM_ATTRIBUTION);
            if (node != null) {
                attribution = node.getNodeValue();
            }
            node = attributes.getNamedItem(ITEM_UPDATED);
            if (node != null) {
                updated = node.getNodeValue();
            }
            node = attributes.getNamedItem(ITEM_CATEGORY);
            if (node != null) {
                category = node.getNodeValue();
            }

            // Split keyword
            String[] keywords = keyword.split(" ");
            for (int j = 0; j < keywords.length; j++) {
                addValues.put("col" + Integer.toString(j), keywords[j].replace("_", " "));
            }
            addValues.put(ITEM_ATTRIBUTION, attribution);
            addValues.put(ITEM_UPDATED, updated);
            addValues.put(Storage.KEY_ROWID, rowId);
            addValues.put(Storage.KEY_ORDER, order);
            addValues.put(Storage.KEY_CATEGORY, category.replace("_", " "));
            addValues.put(Storage.KEY_CONTENT, content);

            storage.insertContent(GlobalConstants.DATABASE_TABLE, addValues);

            incrementProgressLevel();
        }
    }

    /**
     * Call this each time to increment the progress bar by one level
     */
    private void incrementProgressLevel() {
        Message message = progressHandler.obtainMessage();
        bundle.putInt("node", ++progressLevel);
        message.setData(bundle);
        progressHandler.sendMessage(message);
    }

    /**
     * Record last update version in preferences
     * 
     * @param document
     */
    static void storeKeywordsVersion(Document document) {
        NodeList nodeList = document.getElementsByTagName(VERSION_TAG);
        if (nodeList.getLength() > 0) {
            Node node = nodeList.item(0).getFirstChild();
            String version = node.getNodeValue();
            PropertyStorage.getLocal().setValue(GlobalConstants.KEYWORDS_VERSION_KEY, version);
        }
    }

}
