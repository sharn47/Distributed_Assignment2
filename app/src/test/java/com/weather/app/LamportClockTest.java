package com.weather.app;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;



class LamportClockTest {



    @Test

    void testClockInitialValue() {

        LamportClock clock = new LamportClock();

        assertEquals(0, clock.getClock());

    }



    @Test

    void testClockIncrement() {

        LamportClock clock = new LamportClock();

        clock.tick();

        assertEquals(1, clock.getClock());

    }



    @Test

    void testClockUpdateWithHigherValue() {

        LamportClock clock = new LamportClock();

        clock.update(5);

        assertEquals(6, clock.getClock());

    }



    @Test

    void testClockUpdateWithSmallerValue() {

        LamportClock clock = new LamportClock();

        clock.update(2);

        clock.update(1);

        assertEquals(4, clock.getClock()); // Clock shouldn't decrease

    }

}