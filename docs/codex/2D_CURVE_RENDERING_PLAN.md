# 2D Parametric Curve Rendering Plan

Status: proposal for the next WaveSim drawing experiment.

## Goal

Let WaveSim draw one or more moving 2D curves from a mathematical description.
Keep the curve math independent from pixels and Vulkan so the same model can
eventually draw sine waves, circles, loops, and other parametric curves.

## Curve model

Use a parameter `u` to travel along a curve and a separate `timeSeconds` value
to animate it. A small interface could look like this:

```java
interface ParametricCurve2D {
    Vector2 pointAt(double u, double timeSeconds);
    double startParameter();
    double endParameter();
}
```

`Vector2` contains world-space `x` and `y` values. A curve returns one 2D point
for each parameter value. Since `x` can repeat as `u` changes, the curve can
still pass through several `y` values at the same `x`. For example, the circle

```text
r(u) = (cx + R cos(u), cy + R sin(u)),  0 <= u <= 2π
```

has two different points above and below its center for many of the same
horizontal coordinates. If several distinct paths are needed, pass a list of
curves to the renderer.

A traveling sine wave can use

```text
x(u, t) = u
y(u, t) = A sin(k u - ω t + φ)
```

Here `A` is amplitude, `k` is wave number, `ω` is angular frequency, and `φ` is
phase. `u` selects a point along the wave; `t` advances the animation. A static
curve can ignore `timeSeconds`.

Parametric curves are the recommended starting point. An implicit curve such as
`F(x, y) = 0` can describe shapes like circles too, but drawing a general
implicit equation needs a separate contour-finding algorithm. That can wait
until a specific example needs it.

## Data flow

1. `WaveSim` measures elapsed time once per frame with `System.nanoTime()` and
   passes the same `timeSeconds` to every curve sample in that frame.
2. A curve sampler evaluates `pointAt(u, timeSeconds)` over the curve's bounded
   parameter range and returns ordered world-space points.
3. A 2D view maps world coordinates to pixels. Use the window center as the
   origin, positive `x` to the right, positive `y` upward, and one scale value
   for both axes so circles do not become ellipses.
4. A drawing layer joins the screen-space points into paths, then draws those
   paths into the existing transparent overlay image.
5. Keep the current Vulkan texture upload and FPS overlay for the first version.
   This gets curve behavior working before adding a second Vulkan pipeline.

Taking one time snapshot per frame matters: the current `HeightAtPoint` method
reads the clock while each screen column is sampled, so the left and right
sides of one drawn wave can be evaluated at slightly different times. The new
curve model should not read the clock itself or know about window pixels.

## Sampling and drawing

Start with uniform parameter steps and a modest, fixed sample count. That is
easy to inspect and is enough to draw the first sine wave and a circle. Convert
each sample through the view transform, then use an antialiased `Path2D` stroke
instead of drawing disconnected pixel rectangles. If an area under a wave is
wanted, build a separate closed fill path to the chosen baseline and draw the
wave stroke over it.

Do not join across invalid points (`NaN` or infinity). Later, improve the
sampler by subdividing a segment when its midpoint is too far from the straight
line between its endpoints in screen pixels. This gives smooth curves more
points when zoomed in and saves work on flat sections. Add explicit clipping
and discontinuity handling when curves with asymptotes are introduced.

## Suggested stages

1. **Represent curves:** add `Vector2` and `ParametricCurve2D`; rewrite the
   traveling wave so `u` and `timeSeconds` are inputs and the result is `(x,y)`.
2. **Sample and map:** add a sampler and a small 2D view transform with a
   controllable center and scale.
3. **Draw a path:** make the current overlay draw a sine wave as an antialiased
   connected path. Check a circle too, to confirm that the renderer is not
   assuming one `y` for each `x`.
4. **Support several curves:** pass a list of curves, with simple color and
   stroke settings; keep the FPS overlay above them.
5. **Extend only when useful:** add axes/grid, pan/zoom, adaptive sampling, and
   then consider a Vulkan vertex-buffer renderer if profiling shows the current
   full-window texture upload is too slow or GPU-native curves become a learning
   goal.

The first version can define curves directly in Java. An expression editor,
implicit-curve renderer, and general-purpose plotting UI are separate features
and do not need to block drawing waves.

## Current project fit

The current prototype already builds a full-window `BufferedImage` in
`VulkanRenderer.createOverlayImage()`, draws the wave and FPS text into it, and
uploads it through the existing sampled-texture path. The first implementation
can replace the per-column `HeightAtPoint` drawing with sampled paths while
leaving the Vulkan setup alone. Existing uncommitted edits in `WaveGenerator`,
`VulkanRenderer`, and `FpsCounter` are in progress and should be preserved when
this plan is implemented.
