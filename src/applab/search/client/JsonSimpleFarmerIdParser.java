/**
 * Copyright (C) 2012 Grameen Foundation
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

public class JsonSimpleFarmerIdParser {
    /** for debugging purposes in adb logcat */
    private static final String LOG_TAG = "JsonSimpleFarmerIdParser";
    private static final String VERSION_ATTRIBUTE_NAME = "Version";
    private static int progressLevel = 0;
    private Storage storage;

    /** handler to which progress updates are sent */
    private static Handler progressHandler;

    private InputStream farmerIdStream;

    /** handler to which responses are sent */
    private Handler responseHandler;

    private static Bundle bundle;
    private JSONParser jsonParser;
    private FarmerIdParseHandler farmerIdHandler;
    private static Integer nodeCount;
    private Integer addedNodes;

    public JsonSimpleFarmerIdParser(Handler progressHandler,
            Handler responseHandler, InputStream newFarmerIdStream) {
        this.farmerIdStream = newFarmerIdStream;
        this.responseHandler = responseHandler;
        JsonSimpleFarmerIdParser.progressHandler = progressHandler;
        this.farmerIdHandler = new FarmerIdParseHandler();

        try {
            this.jsonParser = new JSONParser();
        }
        catch (FactoryConfigurationError e) {
            // Needed to avoid java warnings, newSAXParser will never throw on Android
        }
    }

    /**
     * @throws ParseException
     */
    public void run() throws ParseException {
        try {
            addedNodes = 0;

            this.storage = new Storage(ApplabActivity.getGlobalContext());
            this.storage.open();
           
            // delete used farmer Ids
            this.storage.deleteUsedFarmerIds();

            while (!this.farmerIdHandler.isEnd()) {
                jsonParser.parse(new InputStreamReader(this.farmerIdStream), (org.json.simple.parser.ContentHandler)this.farmerIdHandler,
                        true);
            }

            if (addedNodes != 0) {
                // let UI handler know
                Log.d(LOG_TAG, "Finished Parsing Farmer Ids ... Added: " + addedNodes);
            }           
        }
        catch (IOException e) {
            Log.d(LOG_TAG, "IOException: " + e);
        }
        catch (IllegalStateException e) {
            Log.d(LOG_TAG, "IllegalStateException: " + e);
        }
        finally {
            if (this.storage != null) {
                this.storage.close();
            }
        }
    }

    /**
     * Save Farmer ids to the database
     * 
     * @param rowId
     * @param farmerId
     * @param status
     */
    public void addRecord(String rowId, String farmerId, int status) {
        ContentValues addValues = new ContentValues();

        addValues.put("id", farmerId);
        addValues.put("farmer_id", farmerId);
        addValues.put("status", status);

        storage.insertContent(GlobalConstants.AVAILABLE_FARMER_ID_TABLE_NAME, addValues);
    }

  
    /**
     * SAX parser handler that processes the Farmer Ids json message
     * 
     */
    private class FarmerIdParseHandler implements ContentHandler {

        // Data object
        private DataObject dataObject = null;
        private static final String ID = "Id";
        private static final String FARMER_ID = "FId";
        private boolean end;
        private String key;

        public boolean isEnd() {
            return end;
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
            if (null != this.dataObject) {
                if (null == this.dataObject.getId() || this.dataObject.getId().length() <= 0) {
                    this.dataObject.setId(this.dataObject.getFarmerId());
                }
                this.dataObject.setStatus(0);
                saveObject(this.dataObject);
            }
            this.dataObject = null;
            return true;
        }

        private void saveObject(DataObject dataObjectToSave) {
            try {
                //String debugRecAdded = String.format("ID: %s | FID:%s | STATUS: %d",dataObjectToSave.getId(), dataObjectToSave.getFarmerId(), dataObjectToSave.getStatus());
                //Log.d(LOG_TAG, "Added Farmer ID: " + debugRecAdded);
                addRecord(dataObjectToSave.getId(), dataObjectToSave.getFarmerId(), dataObjectToSave.getStatus());
                addedNodes++;                
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
            if (null != key && null != dataObject) {
                if (key.equalsIgnoreCase(ID)) {
                    dataObject.setId(String.valueOf(value));
                }
                else if ( key.equalsIgnoreCase(FARMER_ID)) {
                    dataObject.setFarmerId(String.valueOf(value));
                }
            }
            return true;
        }

        @Override
        public boolean startArray() throws ParseException, IOException {
            return true;
        }

        @Override
        public void startJSON() throws ParseException, IOException {

        }

        @Override
        public boolean startObject() throws ParseException, IOException {
            this.dataObject = new DataObject();
            return true;
        }

        @Override
        public boolean startObjectEntry(String key) throws ParseException, IOException {
            this.key = key;
            return true;
        }

        class DataObject {

            private String id;
            private String farmerId;
            private int status;

            public int getStatus() {
                return status;
            }

            public void setStatus(int status) {
                this.status = status;
            }

            public String getFarmerId() {
                return farmerId;
            }

            public void setFarmerId(String farmerId) {
                this.farmerId = farmerId;
            }

            public String getId() {
                return id;
            }

            public void setId(String id) {
                this.id = id;
            }
        }
    }
}
