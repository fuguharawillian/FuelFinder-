package com.fuelfinder;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;

import static org.mockito.Mockito.mockStatic;

class FuelFinderApplicationTest {

    @Test
    void startsSpringApplicationWithItsApplicationClass() {
        try (var springApplication = mockStatic(SpringApplication.class)) {
            FuelFinderApplication.main(new String[]{"--test"});

            springApplication.verify(() ->
                    SpringApplication.run(FuelFinderApplication.class, new String[]{"--test"}));
        }
    }
}
