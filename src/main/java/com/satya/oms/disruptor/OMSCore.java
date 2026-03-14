package com.satya.oms.disruptor;

import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import com.lmax.disruptor.BlockingWaitStrategy;
import org.agrona.DirectBuffer;

import java.util.concurrent.Executors;

import com.satya.oms.config.OMSConfig;

public class OMSCore {
    private final Disruptor<OrderEvent> disruptor;

    public OMSCore() {
        disruptor = new Disruptor<>(
                new OrderEventFactory(),
                OMSConfig.getDisruptorRingSize(), // ring buffer size from config
                Executors.defaultThreadFactory(),
                ProducerType.MULTI,
                new BlockingWaitStrategy()
        );

        disruptor.handleEventsWith(new OrderEventHandler());
        disruptor.start();
    }

    public void publish(DirectBuffer buffer, int offset) {
        disruptor.getRingBuffer().publishEvent((event, sequence) -> event.setBuffer(buffer, offset));
    }
}