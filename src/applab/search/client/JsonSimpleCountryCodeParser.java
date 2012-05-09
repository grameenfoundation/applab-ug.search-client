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

public class JsonSimpleCountryCodeParser {

    /** for debugging purposes in adb logcat */
    private static final String LOG_TAG = "JsonSimpleCountryCodeParser";
    private InputStream countryCodeStream;
    private JSONParser jsonParser;
    private CountryCodeParseHandler countryCodeParseHandler;

    public JsonSimpleCountryCodeParser(Handler progressHandler, InputStream countryCodeStream) {
        this.countryCodeStream = countryCodeStream;
        this.countryCodeParseHandler = new CountryCodeParseHandler();

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

            while (!this.countryCodeParseHandler.isEnd()) {
                jsonParser.parse(new InputStreamReader(this.countryCodeStream),
                        (org.json.simple.parser.ContentHandler)this.countryCodeParseHandler,
                        true);
            }

            Log.d(LOG_TAG, "Finished getting country code.");

        }
        catch (IOException e) {
            Log.d(LOG_TAG, "IOException: " + e);
        }
        catch (IllegalStateException e) {
            Log.d(LOG_TAG, "IllegalStateException: " + e);
        }
    }

    /**
     * SAX parser handler that processes the Farmer Ids json message
     * 
     */
    private class CountryCodeParseHandler implements ContentHandler {

        private static final String COUNTRY_CODE = "CountryCode";
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
            return true;
        }

        @Override
        public boolean endObjectEntry() throws ParseException, IOException {
            return true;
        }

        @Override
        public boolean primitive(Object value) throws ParseException, IOException {
            if (null != key) {
                if (key.equals(COUNTRY_CODE)) {
                    if (value != null) {
                        Log.d(LOG_TAG, "The country code is: " + String.valueOf(value));
                        PropertyStorage.getLocal().setValue(GlobalConstants.COUNTRY_CODE, String.valueOf(value));
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
            return true;
        }

        @Override
        public boolean startObjectEntry(String key) throws ParseException, IOException {
            this.key = key;
            return true;
        }

    }
}
