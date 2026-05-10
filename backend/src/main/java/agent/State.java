package agent;

import java.util.concurrent.atomic.AtomicBoolean;

public class State {
            int iteration = 0;
            String finalContent = null;
            final AtomicBoolean done = new AtomicBoolean(false);
        }