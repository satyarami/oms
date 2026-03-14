package com.satya.oms.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Configuration manager for OMS application settings.
 */
public class OMSConfig {
    private static final Logger logger = LoggerFactory.getLogger(OMSConfig.class);

    private static final String CONFIG_FILE = "oms.properties";
    private static final Properties properties = new Properties();

    // Default values
    public static final String DEFAULT_AERON_CHANNEL = "aeron:ipc?term-length=64k";
    public static final int DEFAULT_STREAM_ID = 1001;
    public static final int DEFAULT_DISRUPTOR_RING_SIZE = 1024;
    public static final int DEFAULT_MAX_ORDER_QUANTITY = 1000;
    public static final long DEFAULT_SUBSCRIBER_POLL_TIMEOUT_MS = 1;

    static {
        loadProperties();
    }

    private static void loadProperties() {
        try (InputStream input = OMSConfig.class.getClassLoader().getResourceAsStream(CONFIG_FILE)) {
            if (input != null) {
                properties.load(input);
                logger.info("Loaded configuration from {}", CONFIG_FILE);
            } else {
                logger.warn("Configuration file {} not found, using defaults", CONFIG_FILE);
            }
        } catch (IOException e) {
            logger.error("Error loading configuration file {}", CONFIG_FILE, e);
        }
    }

    public static String getAeronChannel() {
        return properties.getProperty("aeron.channel", DEFAULT_AERON_CHANNEL);
    }

    public static int getStreamId() {
        return Integer.parseInt(properties.getProperty("aeron.stream.id", String.valueOf(DEFAULT_STREAM_ID)));
    }

    public static int getDisruptorRingSize() {
        return Integer.parseInt(properties.getProperty("disruptor.ring.size", String.valueOf(DEFAULT_DISRUPTOR_RING_SIZE)));
    }

    public static int getMaxOrderQuantity() {
        return Integer.parseInt(properties.getProperty("order.max.quantity", String.valueOf(DEFAULT_MAX_ORDER_QUANTITY)));
    }

    public static long getSubscriberPollTimeoutMs() {
        return Long.parseLong(properties.getProperty("subscriber.poll.timeout.ms", String.valueOf(DEFAULT_SUBSCRIBER_POLL_TIMEOUT_MS)));
    }
}