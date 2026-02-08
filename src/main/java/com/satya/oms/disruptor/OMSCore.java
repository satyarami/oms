
package com.satya.oms.disruptor;

import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import com.lmax.disruptor.BlockingWaitStrategy;
import org.agrona.DirectBuffer;

import java.util.concurrent.Executors;

public class OMSCore {
    private final Disruptor<OrderEvent> disruptor;

    public OMSCore() {
        disruptor = new Disruptor<>(
                new OrderEventFactory(),
                1024, // ring buffer size, power of 2
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
