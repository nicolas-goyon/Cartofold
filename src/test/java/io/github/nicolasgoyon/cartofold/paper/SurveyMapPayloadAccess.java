package io.github.nicolasgoyon.cartofold.paper;

import io.github.nicolasgoyon.cartofold.network.SurveyMapPayloadTestAccess;

/** Gives the tests of this package the compression of the pixels of a payload. */
final class SurveyMapPayloadAccess {
    private SurveyMapPayloadAccess() {
    }

    static byte[] roundTrip(byte[] colors) {
        return SurveyMapPayloadTestAccess.roundTrip(colors);
    }
}
