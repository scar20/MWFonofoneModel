package com.scarette.mwfonofonemodel;

import android.app.Application;
import android.content.res.AssetManager;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import java.util.Vector;

import nl.igorski.mwengine.core.JavaUtilities;
import nl.igorski.mwengine.core.SampleManager;

public class FileUtil {

    private static String LOG_TAG = "MWENGINE_FILE"; // logcat identifier

    public static Vector<short[]> shortBuffers = new Vector<>();
    public static Vector<String> filePaths = new Vector<>();

    public static void installAssets(Application application) {
        String rootDir = "samples";
        String assetsSource = "samples";
        AssetManager manager = application.getAssets();
        String destRoot = application.getFilesDir().getPath() + File.separator + rootDir;

        File file = new File(destRoot);
        if (!file.exists()) {
            Log.d(LOG_TAG,"!!!!!!! File do not exist - installing files on user directory");

            try {
                String[] list = manager.list(assetsSource);
                createDirectory(destRoot);

                for (String name : list) {
                    String sourcePath = assetsSource + File.separator + name;
                    String destPath = destRoot + File.separator + name;
//                    Log.d(LOG_TAG, "files to copy: " + name);
                    createCopy(manager, sourcePath, destPath);
                }

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
//        else Log.d(LOG_TAG,"Files exist - nothing to do");
        setUpSample(application);
    }

    private static void createDirectory(String directoryPath) {
        File file = new File(directoryPath);
        if (!file.exists()) {
            if (file.mkdirs()) {
//                Log.d(LOG_TAG,"Directory is created!");
            } else {
//                Log.d(LOG_TAG,"Failed to create directory!");
            }
        }
    }

    private static void createCopy(AssetManager am, String source, String dest) throws IOException {
        InputStream in = am.open(source);
        OutputStream out = new FileOutputStream(dest);

        byte[] buffer = new byte[1024];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
        in.close();
        out.close();
    }

    public static void setUpSample(Application application) {
        String rootDir = "samples";
        String sourcePath = application.getFilesDir().getPath() + File.separator + rootDir;
        File dir = new File(sourcePath);
        String[] list = dir.list();
        int count = 0;
        shortBuffers.clear();
        for (String name : list) {
//            Log.d(LOG_TAG, "file in userDir: " + name);
//            String key = "00" + count++;
//            JavaUtilities.createSampleFromFile(key, sourcePath + File.separator + name);
            String filePath = sourcePath + File.separator + name;
            filePaths.add(filePath);
            File f = new File(sourcePath + File.separator + name);
            short[] buf;
            try {
                buf = getAudioSample(f);
                shortBuffers.add(buf);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
//        for (short[] b : shortBuffers)
//            Log.d(LOG_TAG, "shortBuffers: " + b.length);
    }

    private static short[] getAudioSample(File f) throws IOException {
        byte[] data;
        int size = (int) f.length();// - 44; // length minus header length
        data = new byte[size];
        FileInputStream fis = new FileInputStream(f);
        try {
            int read = fis.read(data, 0, size);
//                if (read < size) {
//                    int remain = size - read;
//                    while (remain > 0) {
//                        read = fis.read(tmpBuff, 0, remain);
//                        System.arraycopy(tmpBuff, 0, bytes, size - remain, read);
//                        remain -= read;
//                    }
//                }
        } catch (IOException e) {
            throw e;
        } finally {
            fis.close();
        }

        ShortBuffer sb = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer();
        short[] samples = new short[sb.limit()];
        sb.get(samples);
        return samples;
    }

}