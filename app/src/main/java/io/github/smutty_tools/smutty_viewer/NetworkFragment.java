package io.github.smutty_tools.smutty_viewer;

import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.content.Context;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.util.Log;

/**
 * Implementation of headless Fragment that runs an AsyncTask to fetch data from the network.
 */
public class NetworkFragment extends Fragment {

    public static final String TAG = "NetworkFragment";

    private static final String URL_KEY = "UrlKey";

    private DownloadCallbackInterface mCallback;
    private DownloadTask mDownloadTask;
    private String mUrlString;

    /**
     * Static initializer for NetworkFragment that sets the URL
     * of the host it will be downloading from.
     *
     * It also registers the fragment in the fragment manager
     */
    public static NetworkFragment getInstance(FragmentManager fragmentManager, String url) {
        Log.d(TAG, "factory");
        NetworkFragment networkFragment = new NetworkFragment();
        Bundle args = new Bundle();
        args.putString(URL_KEY, url);
        networkFragment.setArguments(args);
        fragmentManager.beginTransaction().add(networkFragment, TAG).commit();
        return networkFragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate");
        mUrlString = getArguments().getString(URL_KEY);
    }

    @Override
    public void onAttach(Context context) {
        Log.d(TAG, "onAttach");
        super.onAttach(context);
        // host activity will hand callbacks from task
        mCallback = (DownloadCallbackInterface) context;
    }

    @Override
    public void onDetach() {
        Log.d(TAG, "onAttach");
        // clear reference to host Activity to avoid memory leak
        mCallback = null;
        super.onDetach();
    }

    @Override
    public void onDestroy() {
        // Cancel download task when Fragment is destroyed
        cancelDownload();
        Log.d(TAG, "onDestroy");
        super.onDestroy();
    }

    /**
     * Start nonblocking execution of DownloadTask
     */
    public void startDownload() {
        cancelDownload();
        Log.d(TAG, "startDownload");
        mDownloadTask = new DownloadTask(mCallback);
        mDownloadTask.execute(mUrlString);
    }

    public void cancelDownload() {
        Log.d(TAG, "cancelDownload");
        if (mDownloadTask != null) {
            mDownloadTask.cancel(true); // may interrupt if running
            mDownloadTask = null;
        }
    }
}
