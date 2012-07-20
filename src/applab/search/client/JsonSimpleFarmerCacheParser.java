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
import java.sql.DataTruncation;
import java.util.ArrayList;
import java.util.Date;
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

public class JsonSimpleFarmerCacheParser {
    /** for debugging purposes in adb logcat */
    private static final String LOG_TAG = "JsonSimpleFarmerCacheParser";
    private static final String VERSION_ATTRIBUTE_NAME = "Version";
    private Storage storage;

    private InputStream farmerCacheStream;

    /** handler to which responses are sent */
    private Handler responseHandler;

    private static Bundle bundle;
    private JSONParser jsonParser;
    private FarmerCacheParseHandler farmerCacheHandler;
    private static Integer nodeCount;
    private static Handler progressHandler;
    private Integer addedNodes;
    private String farmerCacheVersion;

    public JsonSimpleFarmerCacheParser(Handler progressHandler,
            Handler responseHandler, InputStream newFarmerCacheStream) {
        this.farmerCacheStream = newFarmerCacheStream;
        this.responseHandler = responseHandler;
        JsonSimpleFarmerCacheParser.progressHandler = progressHandler;
        this.farmerCacheHandler = new FarmerCacheParseHandler();

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
            
            while (!this.farmerCacheHandler.isEnd()) {
                jsonParser.parse(new InputStreamReader(this.farmerCacheStream),
                        (org.json.simple.parser.ContentHandler)this.farmerCacheHandler,
                        true);
                if (this.farmerCacheHandler.isVersionFound()) {
                    farmerCacheVersion = this.farmerCacheHandler.getVersion();
                }
            }

            if (addedNodes != 0) {
                // let UI handler know
                Log.d(LOG_TAG, "Finished Parsing Farmer Cache ... Added: " + addedNodes);
                if (farmerCacheVersion != null && !farmerCacheVersion.equals("")) {
                    JsonSimpleFarmerCacheParser.storeFarmerCacheVersion(farmerCacheVersion);
                    Log.d(LOG_TAG, "Stored the Farmer Cache Version: " + farmerCacheVersion);
                }
            }
        }
        catch (IOException e) {
            Log.d(LOG_TAG, "IOException: " + e);
        }
        catch (IllegalStateException e) {
            Log.d(LOG_TAG, "IllegalStateException: " + e);
        }
        catch (Exception e) {
            Log.d(LOG_TAG, "Stop gap for continuity, means there are no Farmers in that district");
            Log.d(LOG_TAG, "Exception: " +  e);
        }
        finally {
            if (this.storage != null) {
                this.storage.close();
            }
        }
    }

    /**
     * Save Farmers to the Local Database Cache
     * 
     * @param rowId
     * @param farmerId
     * @param status
     */
    public void addRecord(String rowId, String farmerId, String firstName, String middleName, String lastName, int age,
                          String parentName) {
        ContentValues addValues = new ContentValues();

        addValues.put("id", rowId);
        addValues.put("farmer_id", farmerId);
        addValues.put("first_name", firstName);
        addValues.put("middle_name", middleName);
        addValues.put("last_name", lastName);
        addValues.put("age", age);
        addValues.put("father_name", parentName);
        storage.insertContent(GlobalConstants.FARMER_LOCAL_CACHE_TABLE_NAME, addValues);
    }

    static void storeFarmerCacheVersion(Document document) {
        String version = getFarmerCacheVersion(document);
        storeFarmerCacheVersion(version);
    }

    static void storeFarmerCacheVersion(String version) {
        PropertyStorage.getLocal().setValue(GlobalConstants.FARMER_CACHE_VERSION_KEY, version);
    }

    static String getFarmerCacheVersion(Document document) {
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
     * SAX parser handler that processes the Farmer Ids json message
     * 
     */
    private class FarmerCacheParseHandler implements ContentHandler {

        // Data object
        private DataObject dataObject = null;
        private static final String ID = "sfId";
        private static final String FARMER_ID = "fId";
        private static final String FIRST_NAME = "fName";
        private static final String MIDDLE_NAME = "mName";
        private static final String LAST_NAME = "lName";
        private static final String AGE = "age";
        private static final String PARENT_NAME = "pName";

        // Version
        private String version;
        private boolean versionFound = false;
        private boolean end;
        private String key;

        public String getVersion() {
            return version;
        }

        public void setVersion(String version) {
            this.version = version;
        }

        public boolean isVersionFound() {
            return versionFound;
        }

        public void setVersionFound(boolean versionFound) {
            this.versionFound = versionFound;
        }

        public boolean isEnd() {
            return end;
        }

        @Override
        public boolean endArray() throws ParseException, IOException {
            dataObject = null;
            return false;
        }

        @Override
        public void endJSON() throws ParseException, IOException {
            end = true;
        }

        @Override
        public boolean endObject() throws ParseException, IOException {
            if (null != this.dataObject && null != this.dataObject.getId()) {
                saveObject(this.dataObject);
            }
            this.dataObject = null;
            return true;
        }

        private void saveObject(DataObject dataObjectToSave) {
            try {     
                
                addRecord(dataObjectToSave.getId(), dataObjectToSave.getFarmerId(), dataObjectToSave.getFirstName(),
                        dataObjectToSave.getMiddleName(), dataObjectToSave.getLastName(), dataObjectToSave.getAge(),
                        dataObjectToSave.getParentName());
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
            if (null != key) {
                Log.d("INFO", key);
                Log.d("INFO", String.valueOf(value));
                if (key.equalsIgnoreCase(VERSION_ATTRIBUTE_NAME)) {
                    versionFound = true;
                    this.version = value.toString();
                    return true;
                }
                else {
                    if (null != dataObject) {

                        if (key.equalsIgnoreCase(ID)) {
                            dataObject.setId(String.valueOf(value));
                        }
                        else if (key.equalsIgnoreCase(FARMER_ID)) {
                            dataObject.setFarmerId(String.valueOf(value));
                        }
                        else if (key.equalsIgnoreCase(FIRST_NAME)) {
                            dataObject.setFirstName(String.valueOf(value));
                        }
                        else if (key.equalsIgnoreCase(MIDDLE_NAME)) {
                            dataObject.setMiddleName(String.valueOf(value));
                        }
                        else if (key.equalsIgnoreCase(LAST_NAME)) {
                            dataObject.setLastName(String.valueOf(value));
                        }
                        else if (key.equalsIgnoreCase(AGE)) {
                            int theAge = 0;
                            try {
                                theAge = Integer.parseInt(String.valueOf(value));
                            }
                            catch (Exception ex) {
                                theAge = 0;
                            }
                                
                            dataObject.setAge(theAge);
                        }
                        else if (key.equalsIgnoreCase(PARENT_NAME)) {
                            dataObject.setParentName(String.valueOf(value));
                        }
                    }
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
            private String firstName;
            private String middleName;
            private String lastName;
            private int age;
            private String parentName;

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

            public String getFirstName() {
                return firstName;
            }

            public void setFirstName(String firstName) {
                this.firstName = firstName;
            }

            public String getMiddleName() {
                return middleName;
            }

            public void setMiddleName(String middleName) {
                this.middleName = middleName;
            }

            public String getLastName() {
                return lastName;
            }

            public void setLastName(String lastName) {
                this.lastName = lastName;
            }

            public String getParentName() {
                return parentName;
            }

            public void setParentName(String parentName) {
                this.parentName = parentName;
            }

            public int getAge() {
                return age;
            }

            public void setAge(int age) {
                this.age = age;
            }
        }
    }
}
