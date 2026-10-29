package io.github.nicolasgoyon.cartofold.network;

/** Gives the tests the compression of the pixels of a survey map payload. */
public final class SurveyMapPayloadTestAccess {
    private SurveyMapPayloadTestAccess() {
    }

    /** Compresses and decompresses pixels. */
    public static byte[] roundTrip(byte[] colors) {
        return SurveyMapPayload.decompress(SurveyMapPayload.compress(colors));
    }
}
