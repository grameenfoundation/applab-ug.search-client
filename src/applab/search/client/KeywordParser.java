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
import java.io.InputStream;

import javax.xml.parsers.FactoryConfigurationError;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import android.content.ContentValues;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import applab.client.ApplabActivity;
import applab.client.PropertyStorage;

/**
 * A custom XML parser that also sorts and adds content to search sequence database.
 */

public class KeywordParser {
    /** for debugging purposes in adb logcat */
    private static final String LOG_TAG = "KeywordParser";
    private static final String ADD_TAG = "add";
    private static final String REMOVE_TAG = "remove";
    private static final String ITEM_ORDER = "order";
    private static final String ITEM_KEYWORD = "keyword";
    private static final String ITEM_UPDATED = "updated";
    private static final String ITEM_ATTRIBUTION = "attribution";
    private static final String ITEM_CATEGORY = "category";
    private static final String ITEM_ID = "id";
    private static final String VERSION_ATTRIBUTE_NAME = "version";
    private static final String TOTAL_ATTRIBUTE_NAME = "total";
    private final static String NAMESPACE = "http://schemas.applab.org/2010/07/search";
    private static int progressLevel = 0;
    private Storage storage;

    /** handler to which progress updates are sent */
    private Handler progressHandler;

    private InputStream keywordStream;

    /** handler to which responses are sent */
    private Handler responseHandler;

    private Bundle bundle;
    private SAXParser xmlParser;
    private KeywordParseHandler keywordHandler;
    private Integer nodeCount;
    private String keywordVersion;
    private Integer addedNodes;
    private Integer deletedNodes;

    public KeywordParser(Handler progressHandler,
                         Handler responseHandler, InputStream newKeywordStream) {
        this.keywordStream = newKeywordStream;
        this.responseHandler = responseHandler;
        this.progressHandler = progressHandler;
        this.keywordHandler = new KeywordParseHandler();

        try {
            this.xmlParser = SAXParserFactory.newInstance().newSAXParser();
        }
        catch (ParserConfigurationException e) {
            // Needed to avoid java warnings, newSAXParser will never throw on Android
        }
        catch (SAXException e) {
            // Needed to avoid java warnings, newSAXParser will never throw on Android
        }
        catch (FactoryConfigurationError e) {
            // Needed to avoid java warnings, newSAXParser will never throw on Android
        }
    }

    /**
     * Obsoleted: loading the xml file into DOM takes a lot of memory. Now using walk() instead, which uses
     * XMLPullParser
     */
    public void run() {
        try {
            addedNodes = 0;
            deletedNodes = 0;
            this.xmlParser.reset();
            xmlParser.parse(this.keywordStream, this.keywordHandler);

            if (nodeCount == null || keywordVersion == null) {
                this.responseHandler.sendEmptyMessage(GlobalConstants.KEYWORD_PARSE_ERROR);
            }
            else if (keywordVersion != "") {
                KeywordParser.storeKeywordsVersion(keywordVersion);

                // let UI handler know
                Log.d(LOG_TAG, "Finished Parsing Keywords ... Added: " + addedNodes + ", Deleted: " + deletedNodes);
                this.responseHandler.sendEmptyMessage(GlobalConstants.KEYWORD_PARSE_SUCCESS);
            }
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
        finally {
            if (this.storage != null) {
                this.storage.close();
            }
        }
    }

    /**
     * @param rowId
     * @param order
     * @param category
     * @param attribution
     * @param updated
     * @param keyword
     * @param content
     */
    public void addRecord(String rowId, String order, String category, String attribution, String updated, String keyword, String content) {
        ContentValues addValues = new ContentValues();

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
        String version = getKeywordsVersion(document);
        storeKeywordsVersion(version);
    }

    static void storeKeywordsVersion(String version) {
        PropertyStorage.getLocal().setValue(GlobalConstants.KEYWORDS_VERSION_KEY, version);
    }

    static String getKeywordsVersion(Document document) {
        Element rootNode = document.getDocumentElement();
        if (rootNode != null) {
            String version = rootNode.getAttribute(VERSION_ATTRIBUTE_NAME);
            if (version.length() > 0) {
                return version;
            }
        }
        return "";
    }

    /**
     * SAX parser handler that processes the Keyword xml message
     * 
     */
    private class KeywordParseHandler extends DefaultHandler {
        private static final String RESPONSE_ELEMENT = "GetKeywordsResponse";
        private ContentRow contentRow;

        // <?xml version="1.0"?>
        // <GetKeywordsResponse xmlns="http://schemas.applab.org/2010/07/search" version="2010-07-20 18:34:36"
        // total="25">
        // <add id="23219" category="Farm_Inputs">Sironko Sisiyi
        // Seeds</add> <add id="39243" category="Animals">Bees Pests Wax_moths<add/>
        // <remove id="45" />
        // </GetKeywordsResponse>
        @Override
        public void startElement(String namespaceUri, String localName, String qName, Attributes attributes) throws SAXException {
            if (NAMESPACE.equals(namespaceUri)) {
                if (RESPONSE_ELEMENT.equals(localName)) {
                    // get version
                    keywordVersion = attributes.getValue(VERSION_ATTRIBUTE_NAME);

                    // get total
                    String total = attributes.getValue(TOTAL_ATTRIBUTE_NAME);
                    if (total != null && total.length() > 0) {
                        nodeCount = Integer.parseInt(total);
                    }

                    if (keywordVersion != "" && nodeCount > 0) {
                        nodeCount += 1; // Add one for the start document node
                        Log.d(LOG_TAG, "Total nodes: " + nodeCount);
                        bundle = new Bundle();

                        // Show parse dialog (send signal with total node count)
                        Message message = responseHandler.obtainMessage();
                        bundle.putInt("nodeCount", nodeCount);
                        message.what = GlobalConstants.KEYWORD_PARSE_GOT_NODE_TOTAL;
                        message.setData(bundle);
                        responseHandler.sendMessage(message);
                        storage = new Storage(ApplabActivity.getGlobalContext());
                        storage.open();
                    }
                }
                else if (ADD_TAG.equals(localName)) {
                    contentRow = new ContentRow();
                    contentRow.setRowId(attributes.getValue(ITEM_ID));
                    contentRow.setOrder(attributes.getValue(ITEM_ORDER));
                    contentRow.setCategory(attributes.getValue(ITEM_CATEGORY));
                    contentRow.setAttribution(attributes.getValue(ITEM_ATTRIBUTION));
                    contentRow.setUpdated(attributes.getValue(ITEM_UPDATED));
                    contentRow.setKeyword(attributes.getValue(ITEM_KEYWORD));
                }
                else if (REMOVE_TAG.equals(localName)) {
                    String rowId = attributes.getValue(ITEM_ID);
                    if (storage != null) {
                        storage.deleteEntry(GlobalConstants.DATABASE_TABLE, rowId);
                        deletedNodes++;
                    }
                }
            }
        }

        @Override
        public void endElement(String namespaceUri, String localName, String qName) throws SAXException {
            if (NAMESPACE.equals(namespaceUri)) {
                if (ADD_TAG.equals(localName) || REMOVE_TAG.equals(localName)) {
                    // Use this to increment progress
                    incrementProgressLevel();
                }
            }
        }

        @Override
        public void characters(char[] data, int start, int length) throws SAXException {
            if (this.contentRow != null) {
                contentRow.setContent(String.copyValueOf(data, start, length));
                contentRow.save();
                contentRow = null; // Set to null for next row
                addedNodes++;
            }
        }
    }

    private class ContentRow {
        // Row Fields
        String rowId = null;
        String order = null;
        String attribution = null;
        String updated = null;
        String keyword = null;
        String content = null;
        String category = null;

        public void save() {
            addRecord(rowId, order, category, attribution, updated, keyword, content);
        }

        public void setCategory(String category) {
            this.category = category;
        }

        public void setAttribution(String attribution) {
            this.attribution = attribution;
        }

        public void setUpdated(String updated) {
            this.updated = updated;
        }

        public void setKeyword(String keyword) {
            this.keyword = keyword;
        }

        public void setContent(String content) {
            this.content = content;
        }

        public void setRowId(String id) {
            this.rowId = id;
        }

        public void setOrder(String order) {
            this.order = order;
        }
    }
}
