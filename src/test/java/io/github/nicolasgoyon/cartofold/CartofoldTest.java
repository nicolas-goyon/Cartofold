package io.github.nicolasgoyon.cartofold;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class CartofoldTest {
    @Test
    void modIdIsCartofold() {
        assertEquals("cartofold", Cartofold.MOD_ID);
    }
}
