#include <jni.h>
#include <android/log.h>
#include <cstring>

#define LOG_TAG "MuPDF"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// Forward declarations
extern "C" {
    JNIEXPORT jboolean JNICALL Java_com_pdfreader_core_pdfengine_MuPdfEngine_nativeInitialize(JNIEnv* env, jclass clazz);
    JNIEXPORT jlong JNICALL Java_com_pdfreader_core_pdfengine_MuPdfEngine_nativeOpenDocument(JNIEnv* env, jobject obj, jstring path);
    JNIEXPORT jint JNICALL Java_com_pdfreader_core_pdfengine_MuPdfEngine_nativeCloseDocument(JNIEnv* env, jobject obj, jlong docHandle);
    JNIEXPORT jint JNICALL Java_com_pdfreader_core_pdfengine_MuPdfEngine_nativeGetPageCount(JNIEnv* env, jobject obj, jlong docHandle);
}

/**
 * Initialize MuPDF engine
 */
JNIEXPORT jboolean JNICALL Java_com_pdfreader_core_pdfengine_MuPdfEngine_nativeInitialize(JNIEnv* env, jclass clazz) {
    LOGI("MuPDF JNI bridge initialized");
    return JNI_TRUE;
}

/**
 * Open a PDF document
 */
JNIEXPORT jlong JNICALL Java_com_pdfreader_core_pdfengine_MuPdfEngine_nativeOpenDocument(JNIEnv* env, jobject obj, jstring path) {
    const char* pathStr = env->GetStringUTFChars(path, nullptr);
    LOGI("Opening PDF: %s", pathStr);
    
    // TODO: Implement MuPDF document opening
    // fz_document *doc = fz_open_document(ctx, pathStr);
    
    env->ReleaseStringUTFChars(path, pathStr);
    
    // Return dummy handle for now
    return 0;
}

/**
 * Close a PDF document
 */
JNIEXPORT jint JNICALL Java_com_pdfreader_core_pdfengine_MuPdfEngine_nativeCloseDocument(JNIEnv* env, jobject obj, jlong docHandle) {
    LOGI("Closing document handle: %lld", docHandle);
    
    // TODO: Implement MuPDF document closing
    // fz_drop_document(ctx, doc);
    
    return 0;
}

/**
 * Get page count
 */
JNIEXPORT jint JNICALL Java_com_pdfreader_core_pdfengine_MuPdfEngine_nativeGetPageCount(JNIEnv* env, jobject obj, jlong docHandle) {
    LOGI("Getting page count for document: %lld", docHandle);
    
    // TODO: Implement MuPDF page count retrieval
    // int pages = fz_count_pages(ctx, doc);
    
    return 0;
}
