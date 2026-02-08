package com.satya.oms.driver;


import io.aeron.driver.MediaDriver;

public class MediaDriverMain {
    public static void main(String[] args) {
        // Launch standalone Media Driver
        try (MediaDriver driver = MediaDriver.launch()) {
            System.out.println("Aeron Media Driver started. Press Ctrl+C to stop.");
            Thread.currentThread().join(); // keep it running
        } catch (InterruptedException e) {
            System.out.println("Media Driver interrupted.");
        }
    }
}
