package tools.descartes.teastore.registryclient.util;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.LocalDateTime;

public class AvailabilityTimer {

    private final int failureSeconds;
    private final int frameOffset;

    public AvailabilityTimer(int failureSeconds) {
        if (failureSeconds < 0 || failureSeconds > 10) {
            throw new IllegalArgumentException("Value for failureSeconds has to be between 0 (no failure) and 10 (maximum failure), provided value was: " + failureSeconds);
        }
        int setFrameOffset = 0;
        this.failureSeconds = failureSeconds;
        try {
            setFrameOffset = (int) Math.abs(InetAddress.getLocalHost().toString().hashCode() % 10);
        } catch (UnknownHostException e) {
            // ignore
        }
        this.frameOffset = setFrameOffset;
    }

    public boolean isDown() {
        LocalDateTime now = LocalDateTime.now();
        int currentSecond = (now.getSecond() + this.frameOffset) % 10 ;

        int startOutage = (10 - failureSeconds);

        return currentSecond >= startOutage;
    }
}
