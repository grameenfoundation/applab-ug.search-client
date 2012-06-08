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
import java.io.InputStreamReader;
import java.io.SequenceInputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;

import javax.xml.parsers.FactoryConfigurationError;
import org.json.simple.parser.ContentHandler;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
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

public class JsonSimpleParser {
    /** for debugging purposes in adb logcat */
    private static final String LOG_TAG = "JsonSimpleParser";
    private static int progressLevel = 0;
    private Storage storage;

    /** handler to which progress updates are sent */
    private static Handler progressHandler;

    private SequenceInputStream keywordStreams;

    /** handler to which responses are sent */
    private Handler responseHandler;

    private static Bundle bundle;
    private JSONParser jsonParser;
    private KeywordParseHandler keywordHandler;
    private static Integer nodeCount;
    private String keywordVersion;
    private String imagesVersion;
    private Integer addedNodes;
    private Integer deletedNodes;
    public ArrayList<String> menuIdsCollection;
    public ArrayList<String> updatedImages;
    public ArrayList<String> deletedImages;

    public JsonSimpleParser(Handler progressHandler,
            Handler responseHandler, SequenceInputStream newKeywordStreams) {
        this.keywordStreams = newKeywordStreams;
        this.responseHandler = responseHandler;
        JsonSimpleParser.progressHandler = progressHandler;
        this.keywordHandler = new KeywordParseHandler();
        menuIdsCollection = new ArrayList<String>();
        updatedImages = new ArrayList<String>();
        deletedImages = new ArrayList<String>();

        try {
            this.jsonParser = new JSONParser();
        }
        catch (FactoryConfigurationError e) {
            // Needed to avoid java warnings, newSAXParser will never throw on Android
        }
    }

    /**
     * Parses JSON result with keywords including, menus, added, deleted and updated menu items
     * and added and deleted images
     * @throws ParseException
     */
    public void run() throws ParseException {
        try {
            addedNodes = 0;
            deletedNodes = 0;

            this.storage = new Storage(ApplabActivity.getGlobalContext());
            this.storage.open();

            while (!this.keywordHandler.isEnd()) {
                try {
                    // parse keywords streams built from multiple stream files starting with the first stream till the last
                    jsonParser.parse(new InputStreamReader(this.keywordStreams), (org.json.simple.parser.ContentHandler)this.keywordHandler,
                        true);
                }
                catch (ParseException e) {
                    Log.e(LOG_TAG, "Failed to finish Parsing Keywords, attempting to proceed with next steps");
                    
                    // Hack to bypass EOF ParseExceptions
                    break;
                }                
            }
            if (this.keywordHandler.keywordVersionFound()) {
                keywordVersion = this.keywordHandler.getKeywordVersion();
            }        
            
            if (this.keywordHandler.imagesVersionFound()) {
                imagesVersion = this.keywordHandler.getImagesVersion();
            }
                        
            // Delete menus that we do not need
            deleteOldMenus();          
                          
            if (nodeCount == null || keywordVersion == null || imagesVersion == null) {
                this.responseHandler.sendEmptyMessage(GlobalConstants.KEYWORD_PARSE_ERROR);
            }
            if (keywordVersion != "") {
                JsonSimpleParser.storeKeywordsVersion(keywordVersion);
                Log.d(LOG_TAG, "Stored version: " + keywordVersion);

                // let UI handler know
                Log.d(LOG_TAG, "Finished Parsing Keywords ... Added: " + addedNodes + ", Deleted: " + deletedNodes);
                this.responseHandler.sendEmptyMessage(GlobalConstants.KEYWORD_PARSE_SUCCESS);
            }

            // Update and delete images
            ImageManager.updatePhoneImages(updatedImages, deletedImages); 
            if (imagesVersion != "") {
                JsonSimpleParser.storeImagesVersion(imagesVersion);
                Log.d(LOG_TAG, "Stored Images version: " + imagesVersion);
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
        finally {
            if (this.storage != null) {
                this.storage.close();
            }
        }
    }

    private void deleteOldMenus() {
        ArrayList<String> localMenuIds = storage.getLocalMenuIds();

        // Check if local menu ids match the new ones; if local id doesn't exist
        // among new ids delete it and all its children
        if (!localMenuIds.isEmpty()) {
            for (int i = 0; i < menuIdsCollection.size(); i++) {
                if (!menuIdsCollection.contains(localMenuIds.get(i))) {
                    storage.deleteMenuEntry(localMenuIds.get(i));
                    storage.deleteMenuItemEntry(
                            localMenuIds.get(i));
                    deletedNodes++;
                }
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
        addValues.put("id", rowId);
        addValues.put("position", order);
        addValues.put("content", content);

        storage.insertContent(GlobalConstants.MENU_ITEM_TABLE_NAME, addValues);
    }

    /**
     * Call this each time to increment the progress bar by one level
     */
    static void incrementProgressLevel() {
        Message message = progressHandler.obtainMessage();
        bundle.putInt("node", ++progressLevel);
        Log.d(LOG_TAG, "Processed : " + progressLevel + " of " + nodeCount);
        message.setData(bundle);
        progressHandler.sendMessage(message);
    }

    static void storeKeywordsVersion(String version) {
        PropertyStorage.getLocal().setValue(GlobalConstants.KEYWORDS_VERSION_KEY, version);
    }
    
    static void storeImagesVersion(String version) {
        PropertyStorage.getLocal().setValue(GlobalConstants.IMAGES_VERSION_KEY, version);
    }

    /**
     * SAX parser handler that processes the Keyword json message
     *
     */
    private class KeywordParseHandler implements ContentHandler {
        // Keyword Version
        private String keywordVersion;
        private boolean keywordVersionFound = false;
        
        // Images Version
        private String imagesVersion;
        private boolean imagesVersionFound = false;

        // Data object
        private DataObject dataObject = null;

        private class DataObject {
            String type;
            HashMap<String, String> data;
        }

        private boolean end;
        private String key;

        public String getKeywordVersion() {
            return keywordVersion;
        }
        
        public String getImagesVersion() {
            return imagesVersion;
        }

        public boolean isEnd() {
            return end;
        }

        public boolean keywordVersionFound() {
            return keywordVersionFound;
        }
        
        public boolean imagesVersionFound() {
            return imagesVersionFound;
        }

        @Override
        public boolean endArray() throws ParseException, IOException {
            // TODO Auto-generated method stub
            return false;
        }

        @Override
        public void endJSON() throws ParseException, IOException {
            end = true;
        }

        @Override
        public boolean endObject() throws ParseException, IOException {
            if (key != null) {
                if(null != this.dataObject && null != this.dataObject.data) {
                    saveObject(this.dataObject);
                }
            }
            return true;
        }

        private void saveObject(DataObject dataObjectToSave) {
            try {
                if(dataObjectToSave.type.equals("Menus")) {
                    ContentValues addValues = new ContentValues();
                    Set<String> keys = dataObjectToSave.data.keySet();
                    for(String key: keys) {
                        if(key.equals("id")) {
                            addValues.put(Storage.MENU_ROWID_COLUMN, dataObjectToSave.data.get(key));

                            // Add to collection so we can delete the unwanted ones
                            menuIdsCollection.add(dataObjectToSave.data.get(key));
                        }
                        else {
                            addValues.put(key, dataObjectToSave.data.get(key));
                        }
                    }

                    storage.insertContent(GlobalConstants.MENU_TABLE_NAME, addValues);
                    addedNodes++;
                    incrementProgressLevel();

                } else if(dataObjectToSave.type.equals("MenuItems")) {
                    ContentValues addValues = new ContentValues();
                    Set<String> keys = dataObjectToSave.data.keySet();
                    for(String key: keys) {
                        addValues.put(key, dataObjectToSave.data.get(key));
                    }

                    storage.insertContent(GlobalConstants.MENU_ITEM_TABLE_NAME, addValues);
                    addedNodes++;
                    incrementProgressLevel();

                } else if(dataObjectToSave.type.equals("DeletedMenuItems")) {
                    storage.deleteMenuItemEntry(dataObjectToSave.data.get("id"));
                    deletedNodes++;

                } else if(dataObjectToSave.type.equals("Images")) {
                    updatedImages.add(dataObjectToSave.data.get("id"));
                } else if(dataObjectToSave.type.equals("DeletedImages")) {
                    deletedImages.add(dataObjectToSave.data.get("id"));
                }
            }
            catch (Exception e) {
               Log.e(LOG_TAG, e.getMessage());
            }
        }

        @Override
        public boolean endObjectEntry() throws ParseException, IOException {
            return true;
        }

        @Override
        public boolean primitive(Object value) throws ParseException, IOException {
            if (key != null) {              
                            
                if (key.equals("Version")) {
                    keywordVersionFound = true;
                    imagesVersionFound = true;
                    this.keywordVersion = value.toString();
                    this.imagesVersion = value.toString();
                    key = null;
                    Log.d(LOG_TAG, "Keyword version: " + keywordVersion);
                    return true;
                }              
                else if(key.equals("Total")) {
                    nodeCount = Integer.parseInt(value.toString());
                    //Log.d(LOG_TAG, "Total nodes: " + nodeCount);

                    bundle = new Bundle();

                    // Show parse dialog (send signal with total node count)
                    Message message = responseHandler.obtainMessage();
                    bundle.putInt("nodeCount", nodeCount);
                    message.what = GlobalConstants.KEYWORD_PARSE_GOT_NODE_TOTAL;
                    message.setData(bundle);
                    responseHandler.sendMessage(message);
                }
                else {
                    if(null != this.dataObject && null != this.dataObject.data) {
                        this.dataObject.data.put(key.toString(), (null != value)?value.toString():"");
                    }
                }
            }
            return true;
        }

        @Override
        public boolean startArray() throws ParseException, IOException {
            if (key != null) {
                // Start handling the key type
                this.dataObject = new DataObject();
                this.dataObject.type = key.toString();
            }
            return true;
        }

        @Override
        public void startJSON() throws ParseException, IOException {
            // TODO Auto-generated method stub

        }

        @Override
        public boolean startObject() throws ParseException, IOException {
            if (key != null) {
                if(null != this.dataObject) {
                    this.dataObject.data = new HashMap<String, String>();
                }
            }
            return true;
        }

        @Override
        public boolean startObjectEntry(String key) throws ParseException, IOException {
            this.key = key;
            return true;
        }
    }
}
