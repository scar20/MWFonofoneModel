

#include <jni.h>
#include <cinttypes>
#include <android/log.h>
#include <cstring>
#include <string>
#include <sys/types.h>
#include <android/asset_manager.h>
#include <android/asset_manager_jni.h>

#include <sndfile.hh>
#include <samplerate.h>

#define MODULE_NAME  "MWFon-SNDFILE-APP"

#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, MODULE_NAME, __VA_ARGS__)

static int32_t nativeSampleRate;


const char *
sfe_codec_name (int format) {
    switch (format & SF_FORMAT_SUBMASK) {
        case SF_FORMAT_PCM_S8 :
            return "signed 8 bit PCM";
        case SF_FORMAT_PCM_16 :
            return "16 bit PCM";
        case SF_FORMAT_PCM_24 :
            return "24 bit PCM";
        case SF_FORMAT_PCM_32 :
            return "32 bit PCM";
        case SF_FORMAT_PCM_U8 :
            return "unsigned 8 bit PCM";
        case SF_FORMAT_FLOAT :
            return "32 bit float";
        case 0:
            return "??? format ???";
    }
}

const char *
sfe_file_type (int format) {
    switch (format & SF_FORMAT_TYPEMASK) {
        case SF_FORMAT_WAV :
            return "WAV file";
        case SF_FORMAT_AIFF :
            return "AIFF file";
        case SF_FORMAT_AU :
            return "AU file";
        case SF_FORMAT_RAW :
            return "RAW file";
        case SF_FORMAT_FLAC :
            return "FLAC file";
        case 0:
            return "??? file_type ???";
    }
}

extern "C"
JNIEXPORT void JNICALL
Java_com_scarette_mwfonofonemodel_Repository_setNativeSampleRate(JNIEnv *env, jclass thiz, jint sample_rate) {
    nativeSampleRate = sample_rate;
    LOGD("nativeSampleRate : %d\n", nativeSampleRate);
}

extern "C"
JNIEXPORT jshortArray JNICALL
Java_com_scarette_mwfonofonemodel_Repository_getShortBufferFromFile(JNIEnv *env, jclass clazz, jstring path) {
    const char *cpath = env->GetStringUTFChars(path, nullptr);
    SndfileHandle file;
    file = SndfileHandle(cpath);
    int channels = file.channels();
    int64_t frames = file.frames();
    int length = channels * frames;
    short buf[length];
    file.read(buf, length);
    LOGD("File Name : %s\n", cpath);
    LOGD("Sample Rate : %d\n", file.samplerate());
    LOGD("Channels : %d\n", file.channels());
    LOGD("Frames   : %d\n", (int) file.frames());

    jshortArray ret = env->NewShortArray(length);
    env->SetShortArrayRegion(ret, 0, length, (jshort *) buf);
    env->ReleaseStringUTFChars(path, cpath);

    return ret;
}


extern "C"
JNIEXPORT void JNICALL
Java_com_scarette_mwfonofonemodel_Repository_installFilesFromAssets(JNIEnv *env, jclass clazz, jobject am,
                                                    jstring temp_path, jstring source_path,
                                                    jstring dest_path,
                                                    jstring jcount) {

    const char *cpath = env->GetStringUTFChars(source_path, nullptr);
    const char *count = env->GetStringUTFChars(jcount, nullptr);

    LOGD("!! !! !! source_path !! !! !!   : %s\n", cpath);

    // use asset manager to open asset by filename
    AAssetManager *mgr = AAssetManager_fromJava(env, am);

    if (mgr == nullptr)
        return;

    AAsset *asset = AAssetManager_open(mgr, cpath, AASSET_MODE_UNKNOWN);

    if (asset == nullptr)
        return;

//    std::vector<char> buffer;
    float *sfbuf;

    off64_t length = AAsset_getLength64(asset);
    off64_t remaining = AAsset_getRemainingLength64(asset);
    size_t Mb = 1000 * 1024; // read assets in one megabyte chunks
    size_t currChunk;
//    buffer.reserve(length);

    // TODO: hackaroni. Prior to Android NDK 26 we could read an asset directly
    // as a ByteArray (well, we still can) and read the WAV data from it (in WaveReader, this
    // now fails...) for now we do the wasteful thing by creating a temporary file...

    std::string tempFolder = env->GetStringUTFChars(temp_path, nullptr);
    std::string tempFile = tempFolder + "/tmp" + count;
    FILE *tmp = fopen(tempFile.c_str(), "w");

    bool readUsingTempFile = (tmp != nullptr);
    LOGD("!! !! !! readUsingTempFile !! !! !!   : %d\n", readUsingTempFile);
    LOGD("!! !! !! tempFile : %s\n", tempFile.c_str());

    int nb_read = 0;

    while (remaining != 0) {
        //set proper size for our next chunk
        if (remaining >= Mb)
            currChunk = Mb;
        else
            currChunk = remaining;

        char chunk[currChunk];

        // read next chunk and append to temporary buffer

        if ((nb_read = AAsset_read(asset, chunk, currChunk)) > 0) {

            if (!readUsingTempFile) {
                //                buffer.insert(buffer.end(), chunk, chunk + currChunk);
            }
            else {
                fwrite(chunk, nb_read, 1, tmp);
            }

            remaining = AAsset_getRemainingLength64(asset);
        }
    }
    AAsset_close(asset);
    env->ReleaseStringUTFChars(source_path, cpath);

    const char *coutpath = env->GetStringUTFChars(dest_path, nullptr);

    // if input file samplerate not native, convert before writing to output file,
    // otherwise just write to new output file

    SF_INFO sfi_info;
    SF_INFO sfo_info;
    sfi_info.format = 0; //SF_FORMAT_WAV | SF_FORMAT_FLOAT;

    SRC_DATA src_data;

    SNDFILE *sf; // = sf_open(tempFile.c_str(), SFM_RDWR, &sfi_info);
    if (readUsingTempFile) {
        fclose(tmp);
        sf = sf_open(tempFile.c_str(), SFM_READ, &sfi_info);
        LOGD("open file: %s\n", tempFile.c_str());
        LOGD("error?   : %s\n", sf_strerror(sf));
//        LOGD("Format   : %s  codec: %s\n", sfe_file_type(sfi_info.format), sfe_codec_name(sfi_info.format));
//        LOGD("Sample Rate : %d\n", sfi_info.samplerate);
//        LOGD("Channels : %d\n", sfi_info.channels);
//        LOGD("Frames   : %ld\n", (long) sfi_info.frames);
        off64_t sflength = sfi_info.channels * sfi_info.frames;
        sfbuf = static_cast<float *>(malloc(sizeof(float) * sflength));
        sf_read_float(sf, sfbuf, sflength);
        sf_close(sf);
//        LOGD("Frames after closed  : %ld\n", (long) sfi_info.frames);

        // prepare output file
        // try to create file with standard FILE interface
        FILE *f = fopen(coutpath, "w");
//        fclose(f);
        sfo_info.format = SF_FORMAT_WAV | SF_FORMAT_PCM_16;
        sfo_info.channels = sfi_info.channels;
        sfo_info.samplerate = nativeSampleRate;
//        sfo_info.frames = sfi_info.frames;
//        LOGD("Frames copied to sfo  : %ld\n", (long) sfo_info.frames);
        sf = sf_open(coutpath, SFM_WRITE, &sfo_info);
        LOGD("!!! output file error? %s!!!\n", sf_strerror(sf));
        LOGD("!!! output file path %s!!!\n", coutpath);
        if (sfi_info.samplerate != nativeSampleRate) {
            LOGD("!!! File is *not* native sample rate !!!\n");
            const char *version = src_get_version();
//            LOGD("LibSampleRate version : %s\n", version);
            off64_t olength = static_cast<long>(((double) nativeSampleRate / (double) sfi_info.samplerate) *
                                                (double) sflength); // new output length if resampled
//            LOGD("!!! converted output length: %d\n", (int) olength);
            float *convbuf; // output buffer with new output length
            convbuf = static_cast<float *>(malloc(sizeof(float) * olength));
//            LOGD("!!! float convbuf created: %d\n", (int) olength);
            src_data.data_in = sfbuf;
            src_data.data_out = convbuf;
            src_data.input_frames = (long) sflength;
            src_data.output_frames = (long) olength;
            src_data.src_ratio = double(nativeSampleRate) / double(sfi_info.samplerate);  // output / input
//            src_simple(&src_data, SRC_SINC_BEST_QUALITY, sfi_info.channels); // 8t 90063ms, 12t 85592ms, 16t 81786ms, 24t 82228ms, 32t 82777ms, 64t 80864ms
            src_simple(&src_data, SRC_SINC_MEDIUM_QUALITY, sfi_info.channels); // 8t 18979ms, 16t 18551ms, 32t 17666ms
//            src_simple(&src_data, SRC_SINC_FASTEST, sfi_info.channels);  // 8047ms

//            LOGD("src_data.input_frames_used : %d output_frames_gen : %d\n",
//                 (int) src_data.input_frames_used, (int) src_data.output_frames_gen);

            // write converted output file
//            LOGD("!!! Write converted file !!!\n");
            sf_count_t numframe = sf_writef_float(sf, convbuf, src_data.output_frames_gen);
            LOGD("!!! %ld frames_gen %lld sample writen !!!\n", src_data.output_frames_gen, numframe);
            free(convbuf);

        } else {
            LOGD("!!! File is native sample rate !!!\n");
//            LOGD("!!! sfi_info.frame %lld  !!!\n", sfi_info.frames);
            sf_writef_float(sf, sfbuf, sfi_info.frames);
            free(sfbuf);
//            LOGD("!!! %lld sample writen !!!\n", sfi_info.frames);
        }
        sf_close(sf);
        LOGD("!!! file #  %s finished copying!!!\n", count);
        SF_INFO info_check;
        info_check.format = 0;
        sf = sf_open(coutpath, SFM_READ, &info_check);
        const char *formatcheck = sfe_codec_name(info_check.format);
        const char *filecheck = sfe_file_type(info_check.format);
        LOGD("!!! CHECK file #%s %s !!!\n", count, coutpath);
        LOGD("!!! CHECK error?   : %s\n", sf_strerror(sf));
        LOGD("!!! CHECK Format   : %s  codec : %s\n", filecheck, formatcheck);
        LOGD("!!! CHECK Sample Rate : %d\n", info_check.samplerate);
//        LOGD("Channels : %d\n", info_check.channels);
//        LOGD("Frames   : %ld\n", (long) sfi_info.frames);
        sf_close(sf);
    }

//    LOGD("!!! ReleaseStringUTFChars !!!\n");
//    env->ReleaseStringUTFChars(temp_path, tempFolder.c_str());
    remove(tempFile.c_str());
    LOGD("!!! job %s finished !!!\n", count);
    env->ReleaseStringUTFChars(jcount, count);
//    LOGD("!!! jcount released !!!\n");
}
