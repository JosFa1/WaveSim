package wavesim;

public class WaveGenerator {


    public float amplitude;
    public float time = 0;
    WaveGenerator(float amplitude)
    {
        amplitude = this.amplitude;
    }


    /**
     * Gives the Height at a point based on the status of the wave.
     * 
     * @param point The pixel difference from the center of the window. So 0 is the center of the screen, positive numbers to the right and negetive numbers to the left.
     * 
     * @return The distance from the bottom of the window that the wave is at.
     */
    float HeightAtPoint(float point)
    {
        float height = (float) (amplitude*Math.sin(point));
        return height;
    }



}
