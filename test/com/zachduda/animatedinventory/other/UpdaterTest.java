package com.zachduda.animatedinventory.other;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class UpdaterTest {

    @ParameterizedTest(name = "{0} newer than {1}: {2}")
    @CsvSource(delimiter = '|', textBlock = """
            8.0.1          | 8.0.0          | true
            8.1            | 8.0.9          | true
            9.0.0          | 8.99.99        | true
            8.10.0         | 8.9.0          | true
            v8.0.1         | 8.0.0          | true
            8.0.0          | 8.0.0          | false
            8.0            | 8.0.0          | false
            8.0.0          | 8.0            | false
            V8.0.0         | 8.0.0          | false
            8.0.0          | 8.0.1          | false
            7.9.9          | 8.0.0          | false
            8.0.0          | 8.0.0-SNAPSHOT | false
            ' 8.0.1 '      | 8.0.0          | true
            nightly        | 8.0.0          | true
            nightly        | nightly        | false
            NIGHTLY        | nightly        | false
            99999999999999 | 8.0.0          | true
            """)
    void comparesVersionsByTheirNumbers(String latest, String current, boolean newer) {
        assertEquals(newer, Updater.isNewer(latest, current));
    }
}
