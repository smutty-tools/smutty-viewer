package io.github.smutty_tools.smutty_viewer.Tools;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class FileUtils {

    public static final String TAG = "FileUtils";

    private static final char[] HEX_ARRAY = "0123456789ABCDEF".toCharArray();
    public static final String HASH_MD5 = "MD5";

    public static final int FILE_ACCESS_BUFFER_LENGTH = 4 * 1024;
    public static final int HASH_BUFFER_LENGTH = 4 * 1024;

    public static void Copy(InputStream inputStream, OutputStream outputStream) throws IOException {
        byte[] buffer = new byte[FILE_ACCESS_BUFFER_LENGTH];
        int bytesRead;
        while ((bytesRead = inputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, bytesRead);
        }
    }

    public static void InputToFile(InputStream inputStream, File outputFile) throws IOException {
        OutputStream outputStream = new FileOutputStream(outputFile);
        Copy(inputStream, outputStream);
        outputStream.close();
    }

    public static int DownloadUrlToFile(URL url, File outputFile) throws IOException {
        URLConnection urlConnection = url.openConnection();
        int contentLength = urlConnection.getContentLength();
        InputToFile(urlConnection.getInputStream(), outputFile);
        return contentLength;
    }

    public static InputStream DownloadUrlToInputStream(URL url) throws IOException {
        URLConnection urlConnection = url.openConnection();
        return urlConnection.getInputStream();
    }

    public boolean IsFileMd5Valid(File file, String md5) throws FileNotFoundException {
        return FileUtils.FileMd5(file).toLowerCase().equals(md5.toLowerCase());
    }

    public static String ToHexString(byte[] bytes) {
        // Source: https://stackoverflow.com/a/9855338/5973357
        char[] hexChars = new char[bytes.length << 1];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j << 1] = HEX_ARRAY[v >>> 4];
            hexChars[(j << 1) + 1] = HEX_ARRAY[v & 0x0F];
        }
        return new String(hexChars);
    }

    public static String FileMd5(File inputFile) throws FileNotFoundException{
        FileInputStream inputStream = new FileInputStream(inputFile);
        String result = HashInput(HASH_MD5, inputStream);
        return result;
    }

    public static String HashInput(String algorithm, InputStream inputStream) {
        try {
            MessageDigest md = MessageDigest.getInstance(algorithm);
            int bytesRead;
            byte[] buffer = new byte[HASH_BUFFER_LENGTH];
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                md.update(buffer, 0, bytesRead);
            }
            byte hash[] = md.digest();
            return FileUtils.ToHexString(hash);
        } catch (NoSuchAlgorithmException e) {
            return null;
        } catch (IOException e) {
            return null;
        }
        // TODO: use smutty exceptions only
    }
}
