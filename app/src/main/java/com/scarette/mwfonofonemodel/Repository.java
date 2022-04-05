package com.scarette.mwfonofonemodel;

import android.app.Application;
import android.content.Context;
import android.content.res.AssetManager;
import android.media.AudioManager;
import android.os.Build;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Vector;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReferenceArray;

public class Repository {

    private static final String DEBUG_TAG = "MWFon-Repository";
    private static int NATIVE_SAMPLE_RATE;


    private static int getRecommendedSampleRate( Context context ) {
        String SR_CHECK = null;

        // API level 17 available ?  Use the sample rate provided by AudioManager.getProperty(PROPERTY_OUTPUT_SAMPLE_RATE)
        // to prevent the buffers from taking a detour through the system resampler
        if ( Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 ) {
            SR_CHECK = ((AudioManager) context.getSystemService( Context.AUDIO_SERVICE )).getProperty( AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE );
        }
        return ( SR_CHECK != null ) ? Integer.parseInt( SR_CHECK ) : 48000;
    }

//    private final SoundItemDao soundItemDao;
//    private final DirectoryItemDao directoryItemDao;
//    private final LiveData<List<SoundItem>> internalSoundList;
//    private final LiveData<List<SoundItem>> userSoundList;
//    private final LiveData<List<SoundItem>> favoritesList;
//
//    private final LiveData<List<DirectoryItem>> directoryList;
//    private DirectoryItem userDirectory;

    private static File filesDirectory;
    public static File getFileDirectory() { return filesDirectory; }
    private String tempDir;

    private static final int NUMBER_OF_THREADS = 16;
    static final ExecutorService installExecutor =
            Executors.newFixedThreadPool(NUMBER_OF_THREADS);

    static int tempFileCount = 0;
    static int jobCount = 0;


    public interface SetUserDirectoryCB {
//        void setUserDirectory(DirectoryItem directory);
    }
    // for testing
    private static long start;

    // "Singletonized" Repository
    private static Repository repositoryInstance = null;
    Repository() { }

    public static Repository getInstance() { // initialized from MainActivity, called by viewmodel
        if (repositoryInstance == null) repositoryInstance = new Repository();
        return repositoryInstance;
    }

    public void init(Application application) { // this will be called from MainActivity

        /////////////////////////////////////////////////////////
        // for when we'll find where we have put many deprecated asyncTask:
        // Executors.newSingleThreadScheduledExecutor().execute { //here }
        /////////////////////////////////////////////////////////

        NATIVE_SAMPLE_RATE = getRecommendedSampleRate( application.getApplicationContext() );
        setNativeSampleRate(NATIVE_SAMPLE_RATE);

        filesDirectory = application.getFilesDir();
        File f = new File(filesDirectory, "sound bank");
        tempDir = application.getCacheDir().getAbsolutePath();

        if (f.exists()) {
            boolean deleted = deleteDirectory(f);
            Log.d(DEBUG_TAG,"directories deleted: " + deleted);
        }
        // if directory list is empty the database have not been populated yet
        // populate the database and create user directory on local storage
        if (!f.exists()) {
//            MainActivity.callbackInterface.onInstall();
            Log.d(DEBUG_TAG,"list is empty, populate");
            populateDefaultFiles(application);
        }

    }


    private void populateDefaultFiles(Application application) {

        start = System.currentTimeMillis();

        String rootDir = "sound bank";
        String rootDir2 = "sound bank2";
//        String userDir = "user sounds";
        AssetManager am = application.getAssets();
        String destPath = filesDirectory.getPath() + File.separator + rootDir;
        Log.d(DEBUG_TAG,"destPath: " + destPath);

        MainActivity.installCallback.onInstall();

        Log.d(DEBUG_TAG,"dialog called");
        try {
            copyAssets(am, rootDir, destPath);
        } catch (IOException e){
            Log.d(DEBUG_TAG,"Exeption!");
            e.printStackTrace();
        }


        // create user directory
//        Log.d(DEBUG_TAG,"creating user dir");
//        String userpath = filesDirectory.getPath() + File.separator + userDir;
//        File user = new File(userpath);
//        if (!user.exists()) {
//            if (user.mkdirs()) {
//                Log.d(DEBUG_TAG,"user Directory is created!");
//            } else {
//                Log.d(DEBUG_TAG,"Failed to create user directory!");
//            }
//        }


        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            awaitTerminationAfterShutdown(installExecutor);
            long total = System.currentTimeMillis() - start;
            Log.d(DEBUG_TAG,"!!! populate files finished in " + total + "ms");
            FileUtil.setUpSample();
            MainActivity.installCallback.onInstallFinished();
            Log.d(DEBUG_TAG,"!!! populate files finished");
            executor.shutdown();
        });


 //       Log.d(DEBUG_TAG,"populate files finished");
    }

    public void awaitTerminationAfterShutdown(ExecutorService threadPool) {
        threadPool.shutdown();
        Log.d(DEBUG_TAG,"threadPool.shutdown() waiting for threads to finish");
        try {
            Log.d(DEBUG_TAG,"entering threadPool.awaitTermination try block");
            if (!threadPool.awaitTermination(120, TimeUnit.SECONDS)) {
                threadPool.shutdownNow();
                Log.d(DEBUG_TAG,"!threadPool.awaitTermination expired threadPool.shutdownNow()");
            }
        } catch (InterruptedException ex) {
            threadPool.shutdownNow();
            Thread.currentThread().interrupt();
            Log.d(DEBUG_TAG,"threadPool InterruptedException : threads interrupted");
        }
        Log.d(DEBUG_TAG,"threadPool.shutdown() threads effectively finished");
    }

    private void copyAssets(AssetManager am, String source, String dest) throws IOException {

        final String[] list = am.list(source);

        for (final String name : list) {
            final String sourcepath = source + File.separator + name;
            final String destpath = dest + File.separator + name;

            AtomicReferenceArray<String> sublist = new AtomicReferenceArray<>(am.list(sourcepath));
            if (sublist.length() != 0) { // its a directory
                // create directory and recurse
                Log.d(DEBUG_TAG,"create directory: " + destpath);
                // name minus the first three digits characters
                String dirdestpath = dest + File.separator + name.substring(3);
                createDirectory(dirdestpath);
                copyAssets(am, sourcepath, dirdestpath);

            } else { // assuming its a file but possible to be an empty directory - find reliable test
                File f = new File(sourcepath);
                if (!f.isDirectory()) { // reliable enough?
                    Log.d(DEBUG_TAG," AIE! ");
                    // Used to give every temp file a unique name
                    String scount = String.valueOf(tempFileCount);
                    tempFileCount++;

                    installExecutor.execute(() -> {
                        // update the install message with <jobCount>/<tempFileCount>
                        MainActivity.installCallback.onItemUpdate(++jobCount + "/" + tempFileCount);
                        // call native method
                        installFilesFromAssets(am, tempDir, sourcepath, destpath, scount);
                    });
                }
            }
        }

    }

    private static void createDirectory(String directoryPath) {
        File file = new File(directoryPath);
        if (!file.exists()) {
            if (file.mkdirs()) {
                Log.d(DEBUG_TAG,"Directory is created!");
            } else {
                Log.d(DEBUG_TAG,"Failed to create directory!");
            }
        }
    }

    private static boolean deleteDirectory(File directoryToBeDeleted) {
        File[] allContents = directoryToBeDeleted.listFiles();
        if (allContents != null) {
            for (File file : allContents) {
                deleteDirectory(file);
            }
        }
        return directoryToBeDeleted.delete();
    }

    // public convenience method, more friendly name already used elsewhere
    public static short[] getAudioSample(String path) {
        return getShortBufferFromFile(path);
    }

    // JNI method

    private native static void setNativeSampleRate(int sampleRate);

    private native static short [] getShortBufferFromFile(String path);

    private native static void installFilesFromAssets(AssetManager am, String tempDir, String source, String destPath, String count);


    public static class FileUtil {

        private static String LOG_TAG = "MWFon-Rep::FileUtil"; // logcat identifier

        public static Vector<short[]> shortBuffers = new Vector<>();
        public static Vector<String> filePaths = new Vector<>();


        public static void setUpSample() {
            String rootDir = "sound bank";
            String sourcePath = filesDirectory.getPath() + File.separator + rootDir;
            File dir = new File(sourcePath);
            Log.d(DEBUG_TAG,"setUpSample() sourcePath: " + sourcePath);
            String[] list = dir.list();
            Log.d(DEBUG_TAG,"setUpSample() dir.list: " + Arrays.toString(list));
            int count = 0;
            shortBuffers.clear();
            filePaths.clear();
            for (String name : list) {
                String filePath = sourcePath + File.separator + name;
                filePaths.add(filePath);
                short[] buf;
                buf = getAudioSample(filePath);
                shortBuffers.add(buf);
            }
    //        for (short[] b : shortBuffers)
    //            Log.d(LOG_TAG, "shortBuffers: " + b.length);
        }

    }
}
