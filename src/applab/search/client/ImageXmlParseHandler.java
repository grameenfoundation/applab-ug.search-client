package applab.search.client;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import android.util.Log;
import applab.client.HttpHelpers;
import applab.client.search.R;

public class ImageXmlParseHandler extends DefaultHandler {
    private static final String NAMESPACE = "http://schemas.applab.org/2010/07/search";
    private HashMap<String, File> localImageList;

    // <?xml version="1.0"?>
    // <GetImageResponse xmlns="http://schemas.applab.org/2010/07/search" version="2010-07-20 18:34:36"
    // total="25">
    // <image src="" sha1hash="" name="" />
    // </GetImageResponse>
    @Override
    public void startElement(String namespaceUri, String localName, String qName, Attributes attributes) throws SAXException {
        if (NAMESPACE.equals(namespaceUri)) {
            if (ImageManager.IMAGE_ELEMENT_NAME.equals(localName)) {
                String sha1hash = attributes.getValue("sha1hash");
                String imageListKey = sha1hash.toLowerCase();
                String source = attributes.getValue("src");
                String fileName = attributes.getValue("name");
                if (!this.localImageList.containsKey(imageListKey)) {
                    // Retrieve remote file
                    try {
                        Log.i("ImageManager", "Fetching: " + source);
                        InputStream inputStream = HttpHelpers.getResource(source);
                        ImageFilesUtility.writeFile(fileName, inputStream);
                    }
                    catch (IOException e) {
                        Log.e("ImageManager", "Error while fetching resource: " + e);
                    }
                }
                else {
                    // Remove processed item
                    this.localImageList.remove(imageListKey);
                }
            }
        }
    }

    public void setLocalImageList(HashMap<String, File> localImageList) {
        this.localImageList = localImageList;
    }
}