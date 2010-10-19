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
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map.Entry;

import javax.xml.parsers.ParserConfigurationException;

import org.apache.http.entity.StringEntity;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import android.util.Log;
import applab.client.HttpHelpers;
import applab.client.XmlEntityBuilder;
import applab.client.XmlHelpers;

public class ImageManager {
   
    private final static String XML_NAME_SPACE = "http://schemas.applab.org/2010/07/search";
    private final static String REQUEST_ELEMENT_NAME = "GetImagesRequest";
    private static final String IMAGE_PATH = "search/getImages";
    private final static String IMAGE_ELEMENT_NAME = "image";

    static String getImageXml() {

        String baseServerUrl = Settings.getNewServerUrl();        
        XmlEntityBuilder xmlRequest = new XmlEntityBuilder();
        xmlRequest.writeStartElement(REQUEST_ELEMENT_NAME, XML_NAME_SPACE);        
        xmlRequest.writeEndElement();
        String response = null;
        try {
            response = HttpHelpers.postXmlRequest(baseServerUrl + IMAGE_PATH, (StringEntity)xmlRequest.getEntity());
            if (!response.trim().endsWith("</GetImagesResponse>")) {
                return null;
            }
        }
        catch (IOException e) {
            return null;
        }
        return response;
    }

    static void updateLocalImages() {
        // Get remote list
        String xml = getImageXml();
        // Get local list
        HashMap<String, String> localImageList = getLocalHashList();
        try {
            if (xml != null) {
                Document document = XmlHelpers.parseXml(xml);
                NodeList nodeList = document.getElementsByTagName(IMAGE_ELEMENT_NAME);
                for (int i = 0; i < nodeList.getLength(); i++) {
                    NamedNodeMap nodeMap = nodeList.item(i).getAttributes();
                    String sha1hash = nodeMap.getNamedItem("sha1hash").getNodeValue().toLowerCase();
                    String source = nodeMap.getNamedItem("src").getNodeValue();
                    String fileName = nodeMap.getNamedItem("name").getNodeValue();
                    if (!localImageList.containsKey(sha1hash)) {
                        // Retrieve remote file
                        try {
                            Log.i("ImageManager", "Fetching: " + source);
                            InputStream inputStream = HttpHelpers.getResource(source);
                            ImageFilesUtility.writeFile(fileName, inputStream);
                        }
                        catch (IOException e) {
                            Log.e("ImageManager", "Error while fetching resource: " + e);
                            continue;
                        }
                    }
                    else {
                        // Remove processed item
                        localImageList.remove(sha1hash);
                    }
                }
                // Delete local files not on remote list
                for (Entry<String, String> local : localImageList.entrySet()) {
                    String filePath = local.getValue();
                    String sha1Hash = local.getKey();
                    File file = new File(filePath);
                    //Confirm this is the file we intend to delete
                    if (ImageFilesUtility.getSHA1Hash(file).equalsIgnoreCase(sha1Hash)) {
                        android.util.Log.i("ImageManager", "Deleting file: " + filePath);
                        ImageFilesUtility.deleteFile(filePath);
                    }
                }
            }
        }
        catch (SAXException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        catch (ParserConfigurationException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    static HashMap<String, String> getLocalHashList() {
        // Key: SHA1, Value: absolute file path
        HashMap<String, String> hashPathPairs = new HashMap<String, String>();
        ArrayList<String> files = ImageFilesUtility.getFilesAsArrayList();
        for (String path : files) {
            File file = new File(path);
            String sha1Hash = ImageFilesUtility.getSHA1Hash(file);
            hashPathPairs.put(sha1Hash, path);
        }
        return hashPathPairs;
    }
}
