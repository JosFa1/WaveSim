package wavesim;

/**
 * Measures how many frames are rendered per second.
 *
 * This class deliberately knows nothing about Vulkan. It only counts calls to
 * recordFrame() and returns a new text string twice per second. Keeping timing
 * separate makes it easier to reuse or replace later.
 */
public final class FpsCounter {
    private static final double UPDATE_INTERVAL_SECONDS = 0.5;

    private long startTime = System.nanoTime();
    private int frameCount;

    /**
     * Records one completed frame.
     *
     * @return a string such as "FPS: 144" when a new measurement is ready;
     *         otherwise {@code null}
     */
    public String recordFrame() {
        frameCount++;

        long now = System.nanoTime();
        double elapsedSeconds = (now - startTime) / 1_000_000_000.0;

        if (elapsedSeconds < UPDATE_INTERVAL_SECONDS) {
            return null;
        }

        int framesPerSecond = (int) (frameCount / elapsedSeconds);

        // Start a new measurement window.
        startTime = now;
        frameCount = 0;

        return "FPS: " + framesPerSecond;
    }
}
