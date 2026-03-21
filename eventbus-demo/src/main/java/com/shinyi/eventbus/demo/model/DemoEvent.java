package com.shinyi.eventbus.demo.model;

import lombok.Data;
import java.io.Serializable;
import java.time.Instant;

@Data
public class DemoEvent implements Serializable {
    private static final long serialVersionUID = 1L;

    private long sequence;
    private String message;
    private long timestamp;

    public DemoEvent() {
    }

    public DemoEvent(long sequence, String message) {
        this.sequence = sequence;
        this.message = message;
        this.timestamp = Instant.now().toEpochMilli();
    }

    public static DemoEvent of(long seq, String msg) {
        return new DemoEvent(seq, msg);
    }
}
