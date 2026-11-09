package io.github.jqssun.airplay.bridge;

/** JNI signatures for the pinned upstream native engine. */
public final class NativeBridge {
    static { System.loadLibrary("airplay_native"); }
    public native long nativeInit(Object callback, byte[] hwAddr, String name, String keyFile, boolean nohold, boolean requirePin);
    public native int nativeStart(long handle, int port);
    public native void nativeStop(long handle);
    public native void nativeDestroy(long handle);
    public native void nativeSetDisplaySize(long handle, int w, int h, int fps);
    public native void nativeSetPlist(long handle, String key, int value);
    public native void nativeSetH265Enabled(long handle, boolean enabled);
    public native void nativeSetCodecs(long handle, boolean alac, boolean aac);
    public native void nativeSetHlsEnabled(long handle, boolean enabled);
    public native void nativeSetLang(long handle, String lang);
    public native void nativeSetFeature(long handle, int bit, boolean enabled);
    public native void nativeUpdatePlaybackInfo(long handle, float position, float duration, float rate, boolean readyToPlay);
    public native java.util.Map<String,String> nativeGetRaopTxtRecords(long handle);
    public native java.util.Map<String,String> nativeGetAirplayTxtRecords(long handle);
    public native String nativeGetRaopServiceName(long handle);
    public native String nativeGetServerName(long handle);
    public native void nativeSetDefaultStreamValues(int sampleRate, int framesPerBurst);
    public native boolean nativeServerAudioConfigure(long handle, int cushionMs, int percentilePct, int oboeBufferFrames, boolean forceSwAlac, boolean forceSwAac, boolean realtimePriority, boolean lowLatency, boolean benchmarkLog);
    public native boolean nativeServerAudioStart(long handle);
    public native void nativeServerAudioStop(long handle);
    public native void nativeServerAudioSetClientGain(long handle, float gain);
    public native void nativeServerAudioFormat(long handle, int ct, int spf);
    public native boolean nativeServerAudioDebug(long handle, java.nio.ByteBuffer buf);
}
