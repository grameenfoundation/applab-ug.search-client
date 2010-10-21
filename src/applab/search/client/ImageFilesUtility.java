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
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Environment;
import android.util.Log;

/**
 * Contains methods for managing image files on the file system. TODO: Most of the methods come from Collect code and
 * could be moved to common client code
 */
public class ImageFilesUtility {
    private static final String ROOT = "/sdcard/ckwsearch/";
    private static final String FORMAT = ".jpg";
    private static final String LOG_TAG = "ImageFilesUtility";

    private static boolean storageReady() {
        String cardstatus = Environment.getExternalStorageState();
        if (cardstatus.equals(Environment.MEDIA_REMOVED)
                || cardstatus.equals(Environment.MEDIA_UNMOUNTABLE)
                || cardstatus.equals(Environment.MEDIA_UNMOUNTED)
                || cardstatus.equals(Environment.MEDIA_MOUNTED_READ_ONLY)) {
            return false;
        }
        else {
            return true;
        }
    }

    public static boolean createRootFolder() {
        if (storageReady()) {
            File dir = new File(ROOT);
            if (!dir.exists()) {
                return dir.mkdirs();
            }
            return true;
        }
        else {
            return false;
        }
    }

    public static boolean deleteFile(String path) {
        if (storageReady()) {
            File file = new File(path);
            return file.delete();
        }
        else {
            return false;
        }
    }

    public static void writeFile(String fileName, InputStream inputStream) throws IOException {
        if (storageReady() && createRootFolder()) {
            // replace spaces with underscores
            fileName = fileName.replace(" ", "_");
            // change to lowercase
            fileName = fileName.toLowerCase();
            FileOutputStream fileOutputStream = new FileOutputStream(new File(ROOT, fileName));
            byte[] buffer = new byte[1024];
            int length = 0;
            while ((length = inputStream.read(buffer)) > 0) {
                fileOutputStream.write(buffer, 0, length);
            }
            fileOutputStream.close();
        }
    }

    public static Drawable getImageAsDrawable(String fileName) {
        if (!storageReady()) {
            return null;
        }
        fileName = ROOT + fileName + FORMAT;
        Bitmap bitmap = BitmapFactory.decodeFile(fileName);
        return new BitmapDrawable(bitmap);
    }

    public static ArrayList<String> getFilesAsArrayList() {
        ArrayList<String> fileList = new ArrayList<String>();
        File rootDirectory = new File(ROOT);
        if (!storageReady()) {
            return null;
        }
        // If directory does not exist, create it.
        if (!rootDirectory.exists()) {
            if (!createRootFolder()) {
                return null;
            }
        }
        File[] children = rootDirectory.listFiles();
        for (File child : children) {
            fileList.add(child.getAbsolutePath());
        }

        return fileList;
    }

    public static boolean imageExists(String fileName) {
        fileName = ROOT + fileName + FORMAT;
        File file = new File(fileName);
        if (!storageReady()) {
            return false;
        }
        if (file.exists()) {
            return true;
        }
        return false;
    }

    public static String getSHA1Hash(File file) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA1");
            byte[] messageDigest = md.digest(getFileAsBytes(file));
            BigInteger number = new BigInteger(1, messageDigest);
            String sha1 = number.toString(16);
            while (sha1.length() < 32)
                sha1 = "0" + sha1;
            return sha1;
        }
        catch (NoSuchAlgorithmException e) {
            Log.e("SHA1", e.getMessage());
            return null;
        }
    }

    public static byte[] getFileAsBytes(File file) {
        byte[] bytes = null;
        InputStream is = null;
        try {
            is = new FileInputStream(file);
            // Get the size of the file
            long length = file.length();
            if (length > Integer.MAX_VALUE) {
                Log.e("", "File " + file.getName() + "is too large");
                return null;
            }
            // Create the byte array to hold the data
            bytes = new byte[(int)length];

            // Read in the bytes
            int offset = 0;
            int read = 0;
            try {
                while (offset < bytes.length && read >= 0) {
                    read = is.read(bytes, offset, bytes.length - offset);
                    offset += read;
                }
            }
            catch (IOException e) {
                Log.e(LOG_TAG, "Cannot read " + file.getName());
                e.printStackTrace();
                return null;
            }
            // Ensure all the bytes have been read in
            if (offset < bytes.length) {
                try {
                    throw new IOException("Could not completely read file " + file.getName());
                }
                catch (IOException e) {
                    e.printStackTrace();
                    return null;
                }
            }
            return bytes;
        }
        catch (FileNotFoundException e) {
            Log.e(LOG_TAG, "Cannot find " + file.getName());
            e.printStackTrace();
            return null;
        }
        finally {
            // Close the input stream
            try {
                is.close();
            }
            catch (IOException e) {
                Log.e(LOG_TAG, "Cannot close input stream for " + file.getName());
                e.printStackTrace();
                return null;
            }
        }
    }
}