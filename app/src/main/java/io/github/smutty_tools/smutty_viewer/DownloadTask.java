package io.github.smutty_tools.smutty_viewer;

import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.security.InvalidParameterException;

import javax.net.ssl.HttpsURLConnection;


public class DownloadTask extends AsyncTask<String, Integer, DownloadTask.Result> {

    public static final String TAG = "DownloadTask";

    private DownloadCallbackInterface<String> mCallback;

    DownloadTask(DownloadCallbackInterface<String> callback) {
        setCallback(callback);
    }

    void setCallback(DownloadCallbackInterface<String> callback) {
        mCallback = callback;
    }

    /**
     * This is the type that is provided to postExecute()
     * Wrapper class that serves as a union of a result value and an exception.
     * When the download task has completed, either the result value or exception
     * can be a non-null value.
     * This allows you to pass exceptions to the UI thread that were thrown during
     * doInBackground().
     */
    static class Result {
        public String mResultValue;
        public Exception mException;
        public Result(String resultValue) {
            mResultValue = resultValue;
        }
        public Result(Exception exception) {
            mException = exception;
        }
    }

    /**
     * Cancel background network operation if we do not have network connectivity.
     */
    @Override
    protected void onPreExecute() {
        Log.d(TAG, "onPreExecute");
        if (mCallback == null) {
            Log.d(TAG, "no callback");
            return;
        }
        NetworkInfo networkInfo = mCallback.getActiveNetworkInfo();
        if (networkInfo == null || !networkInfo.isConnected() ||
            (networkInfo.getType() != ConnectivityManager.TYPE_WIFI &&
                networkInfo.getType() != ConnectivityManager.TYPE_MOBILE)) {
            // if no connectivity, cancel task and update callback with null data
            mCallback.updateFromDownload(null);
            cancel(true);
        }
    }

    /**
     * Defines work to perform on the background thread.
     */
    @Override
    protected Result doInBackground(String... urls) {
        Log.d(TAG, "doInBackground");
        Result result = null;
        // return immediately if there is nothing to do
        if (isCancelled() || urls == null || urls.length == 0) {
            Log.d(TAG, "cancelled or no urls");
            return null;
        }
        // TODO: handle multiple URL
        String urlString = urls[0];
        try {
            URL url = new URL(urlString);
            String resultString = downloadUrl(url);
            if (resultString != null) {
                Log.d(TAG, "download success");
                result = new Result(resultString);
            } else {
                Log.d(TAG, "download error");
                throw new IOException("No response received");
            }
        } catch (Exception e) {
            result = new Result(e);
        }
        return result;
    }

    /**
     * Updates the DownloadCallback with the result.
     */
    @Override
    protected void onPostExecute(Result result) {
        Log.d(TAG, "onPostExecute");
        // return immediately if there is nothing to do
        if (result == null || mCallback == null) {
            Log.d(TAG, "no result or no callback");
            return;
        }
        // return the exception text if something wrong happened
        // or return the actual response text if it succeeded
        if (result.mException != null) {
            Log.d(TAG, "updating with exception message");
            mCallback.updateFromDownload(result.mException.getMessage());
        } else {
            Log.d(TAG, "updating with response body");
            mCallback.updateFromDownload(result.mResultValue);
        }
        mCallback.finishDownloading();
    }

    /**
     * Override to add special behavior for cancelled AsyncTask.
     */
    @Override
    protected void onCancelled() {
        Log.d(TAG, "onCancelled");
        // Do nothing for now
    }

    /**
     * Given a URL, sets up a connection and gets the HTTP response body from the server.
     * If the network request is successful, it returns the response body in String form.
     * Otherwise, it will throw an IOException.
     */
    private String downloadUrl(URL url) throws IOException {
        Log.d(TAG, "downloadUrl");
        InputStream stream = null;
        HttpsURLConnection connection = null;
        String result = null;
        try {
            connection = (HttpsURLConnection) url.openConnection();
            // timeout for reading Inputstring arbitrarily set to 3 seconds
            connection.setConnectTimeout(3000); // ms
            connection.setReadTimeout(3000); // ms
            // setup request
            connection.setRequestMethod("GET");
            connection.setDoInput(true); // will have a response body
            // open communication (network errors happen here)
            connection.connect();
            publishProgress(DownloadCallbackInterface.Progress.CONNECT_SUCCESS); // AsyncTask
            // request sent, handle reply
            int responseCode = connection.getResponseCode();
            // an http error happened
            if (responseCode != HttpsURLConnection.HTTP_OK) {
                Log.d(TAG, "no http 200");
                throw new IOException("HTTP error code: " + responseCode);
            }
            // retrieve the response body as an inputstream
            stream = connection.getInputStream();
            publishProgress(DownloadCallbackInterface.Progress.GET_INPUT_STREAM_SUCCESS,
                    0 /* progress indicator value */);
            if (stream != null) {
                Log.d(TAG, "reading response body");
                // convert Stream to String with max length of 500
                result = readStream(stream, 500);
            }
        }
        // caller does exception catching, we only clean up
        finally {
            if (stream != null) {
                stream.close();
            }
            if (connection != null) {
                connection.disconnect();
            }
        }
        return result;
    }

    /**
     * read input stream, block by block
     * @param stream input stream
     * @param maxReadSize size of reading block
     * @return UTF-8 string from the input stream
     * @throws IOException
     * @throws UnsupportedEncodingException
     */
    public String readStream(InputStream stream, int maxReadSize)
            throws IOException, UnsupportedEncodingException {
        Log.d(TAG, "readStream" + maxReadSize);
        // test for validity
        if (maxReadSize < 0) {
            throw new InvalidParameterException("maxReadSize must be positive");
        }
        // return immediately if nothing to do
        if (maxReadSize == 0) {
            return "";
        }
        // Flow: reader > rawBuffer > Stringbuffer
        Reader reader = new InputStreamReader(stream, "UTF-8");
        char[] rawBuffer = new char[maxReadSize];
        StringBuffer buffer = new StringBuffer();
        while(true) {
            // read at most size of rawBuffer
            int readSize = reader.read(rawBuffer);
            Log.d(TAG, "read" + readSize);
            // test for end of stream
            if (readSize == -1) {
                Log.d(TAG, "end of stream");
                break;
            }
            // add read data to output buffer
            // TODO: check why the example cuts at maxReadSize instead of readSize
            Log.d(TAG, "append" + readSize);
            buffer.append(rawBuffer, 0, readSize);
        }
        // finally return the converted text
        return buffer.toString();
    }
}
