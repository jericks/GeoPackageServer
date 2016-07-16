package org.cugos.geopackageserver

import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication

@SpringBootApplication
class App {
    static void main(String[] args) {
        System.setProperty("org.geotools.referencing.forceXY", "true")
        SpringApplication.run(App, args)
    }
}
