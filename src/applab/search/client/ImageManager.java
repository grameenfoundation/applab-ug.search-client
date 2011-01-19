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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map.Entry;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.apache.http.entity.StringEntity;
import org.xml.sax.SAXException;

import android.util.Log;
import applab.client.ApplabActivity;
import applab.client.HttpHelpers;
import applab.client.XmlEntityBuilder;
import applab.client.XmlHelpers;

public class ImageManager {

    private final static String XML_NAME_SPACE = "http://schemas.applab.org/2010/07/search";
    private final static String REQUEST_ELEMENT_NAME = "GetImagesRequest";
    private static final String IMAGE_PATH = "search/getImages";
    final static String IMAGE_ELEMENT_NAME = "image";

    /**
     * Submits an image update request and retrieves XML containing image data from remote server
     * 
     * @return
     */
    public static InputStream getImageXml() {

        String baseServerUrl = Settings.getNewServerUrl();
        XmlEntityBuilder xmlRequest = new XmlEntityBuilder();
        xmlRequest.writeStartElement(REQUEST_ELEMENT_NAME, XML_NAME_SPACE);
        xmlRequest.writeEndElement();
        InputStream response = null;
        try {
            response = HttpHelpers.postXmlRequestAndGetStream(baseServerUrl + IMAGE_PATH, (StringEntity)xmlRequest.getEntity());
            // Write to disk as temp file and use filestream to process
            String filePath = ApplabActivity.getGlobalContext().getCacheDir() + "/keywords.tmp";
            Boolean downloadSuccessful = XmlHelpers.writeXmlToTempFile(response, filePath, "</GetImagesResponse>");
            response.close();

            if (!downloadSuccessful) {
                return null;
            }

            File file = new File(filePath);
            FileInputStream inputStream = new FileInputStream(file);
            return inputStream;
        }
        catch (IOException e) {
            return null;
        }
    }

    public static void updateLocalImages() {
        // Get remote list
        InputStream xmlStream = getImageXml();

        // Get local list
        HashMap<String, File> localImageList = getLocalImageList();

        // Init Sax Parser & XML Handler
        SAXParser xmlParser;
        ImageXmlParseHandler xmlHandler = new ImageXmlParseHandler();
        xmlHandler.setLocalImageList(localImageList);

        if (xmlStream == null) {
            return;
        }
        try {
            if (xmlStream != null) {
                xmlParser = SAXParserFactory.newInstance().newSAXParser();
                
                // This line was causing problems on android 2.2 (IDEOS)
                // xmlParser.reset();
                
                xmlParser.parse(xmlStream, xmlHandler);

                // Delete local files not on remote list
                for (Entry<String, File> local : localImageList.entrySet()) {
                    File file = local.getValue();
                    String sha1Hash = local.getKey();
                    // Confirm this is the file we intend to delete
                    if (ImageFilesUtility.getSHA1Hash(file).equalsIgnoreCase(sha1Hash)) {
                        ImageFilesUtility.deleteFile(file);
                    }
                }
            }
        }
        catch (SAXException e) {
            Log.e("ImageManager", "Error while parsing XML: " + e);
        }
        catch (IOException e) {
            Log.e("ImageManager", "Error while parsing XML: " + e);
        }
        catch (ParserConfigurationException e) {
            Log.e("ImageManager", "Error while parsing XML: " + e);
        }
    }

    public static HashMap<String, File> getLocalImageList() {
        // Key: SHA1, Value: absolute file path
        HashMap<String, File> hashPathPairs = new HashMap<String, File>();
        ArrayList<String> files = ImageFilesUtility.getFilesAsArrayList();
        if (files != null) {
            for (String path : files) {
                File file = new File(path);
                String sha1Hash = ImageFilesUtility.getSHA1Hash(file);
                hashPathPairs.put(sha1Hash, file);
            }
        }
        
        return hashPathPairs;
    }
}
