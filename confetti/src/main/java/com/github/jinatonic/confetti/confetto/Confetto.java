package com.github.jinatonic.confetti.confetto;

import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.view.animation.Interpolator;

/**
 * Abstract class that represents a single confetto on the screen. This class holds all of the
 * internal states for the confetto to help it animate.
 *
 * <p>All of the configured states are in milliseconds, e.g. pixels per millisecond for velocity.
 */
public abstract class Confetto {
    private static final int MAX_ALPHA = 255;

    private final Matrix matrix = new Matrix();
    private final Paint workPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    // Configured coordinate states
    private long initialDelay;
    private float initialX, initialY, initialVelocityX, initialVelocityY,
            accelerationX, accelerationY;
    private Float targetVelocityX, targetVelocityY;
    private Long millisToReachTargetVelocityX, millisToReachTargetVelocityY;

    // Configured rotation states
    private float initialRotation, initialRotationalVelocity, rotationalAcceleration;
    private Float targetRotationalVelocity;
    private Long millisToReachTargetRotationalVelocity;

    // Configured animation states
    private long ttl;
    private Interpolator fadeOutInterpolator;

    private float millisToReachBound;

    // Current draw states
    private float currentX, currentY;
    private float currentRotation;
    // alpha is [0, 255]
    private int alpha;
    private boolean startedAnimation, terminated;

    /**
     * This method should be called after all of the confetto's state variables are configured
     * and before the confetto gets animated.
     *
     * @param bound the space in which the confetto can display in.
     */
    public void prepare(Rect bound) {
        millisToReachTargetVelocityX = computeMillisToReachTarget(targetVelocityX,
                initialVelocityX, accelerationX);
        millisToReachTargetVelocityY = computeMillisToReachTarget(targetVelocityY,
                initialVelocityY, accelerationY);
        millisToReachTargetRotationalVelocity = computeMillisToReachTarget(targetRotationalVelocity,
                initialRotationalVelocity, rotationalAcceleration);

        // Compute how long it would take to reach x/y bounds or reach TTL.
        millisToReachBound = ttl >= 0 ? ttl : Long.MAX_VALUE;
        final long timeToReachXBound = computeBound(initialX, initialVelocityX, accelerationX,
                millisToReachTargetVelocityX, targetVelocityX, bound.left, bound.right);
        millisToReachBound = Math.min(timeToReachXBound, millisToReachBound);
        final long timeToReachYBound = computeBound(initialY, initialVelocityY, accelerationY,
                millisToReachTargetVelocityY, targetVelocityY, bound.top, bound.bottom);
        millisToReachBound = Math.min(timeToReachYBound, millisToReachBound);

        configurePaint(workPaint);
    }

    // Visible for testing
    protected static Long computeMillisToReachTarget(Float targetVelocity, float initialVelocity,
            float acceleration) {
        if (targetVelocity != null) {
            if (acceleration != 0f) {
                final long time = (long) ((targetVelocity - initialVelocity) / acceleration);
                return time > 0 ? time : 0;
            } else {
                if (targetVelocity < initialVelocity) {
                    return 0L;
                } else {
                    return null;
                }
            }
        } else {
            return null;
        }
    }

    // Visible for testing
    protected static long computeBound(float initialPos, float velocity, float acceleration,
            Long targetTime, Float targetVelocity, int minBound, int maxBound) {
        if (acceleration != 0) {
            // non-zero acceleration
            final int bound = acceleration > 0 ? maxBound : minBound;

            if (targetTime == null || targetTime < 0) {
                // https://www.wolframalpha.com/input/
                // ?i=solve+for+t+in+(d+%3D+x+%2B+v+*+t+%2B+0.5+*+a+*+t+*+t)

                final double tmp = Math.sqrt(
                        2 * acceleration * bound - 2 * acceleration * initialPos
                                + velocity * velocity);

                final double firstTime = (-tmp - velocity) / acceleration;
                if (firstTime > 0) {
                    return (long) firstTime;
                }

                final double secondTime = (tmp - velocity) / acceleration;
                if (secondTime > 0) {
                    return (long) secondTime;
                }

                return Long.MAX_VALUE;
            } else {
                // d = x + v * tm + 0.5 * a * tm * tm + tv * (t - tm)
                // d - x - v * tm - 0.5 * a * tm * tm = tv * t - tv * tm
                // d - x - v * tm - 0.5 * a * tm * tm + tv * tm = tv * t
                // t = (d - x - v * tm - 0.5 * a * tm * tm + tv * tm) / tv

                final double time =
                        (bound - initialPos - velocity * targetTime -
                                0.5 * acceleration * targetTime * targetTime +
                                targetVelocity * targetTime) /
                        targetVelocity;

                return time > 0 ? (long) time : Long.MAX_VALUE;
            }
        } else {
            float actualVelocity = targetTime == null ? velocity : targetVelocity;
            final int bound = actualVelocity > 0 ? maxBound : minBound;
            if (actualVelocity != 0) {
                final double time = (bound - initialPos) / actualVelocity;
                return time > 0 ? (long) time : Long.MAX_VALUE;
            } else {
                return Long.MAX_VALUE;
            }
        }
    }

    /**
     * Reset this confetto object's internal states so that it can be re-used.
     */
    public void reset() {
        initialDelay = 0;
        initialX = initialY = 0f;
        initialVelocityX = initialVelocityY = 0f;
        accelerationX = accelerationY = 0f;
        targetVelocityX = targetVelocityY = null;
        millisToReachTargetVelocityX = millisToReachTargetVelocityY = null;

        initialRotation = 0f;
        initialRotationalVelocity = 0f;
        rotationalAcceleration = 0f;
        targetRotationalVelocity = null;
        millisToReachTargetRotationalVelocity = null;

        ttl = 0;
        fadeOutInterpolator = null;

        millisToReachBound = 0f;

        currentX = currentY = 0f;
        currentRotation = 0f;
        alpha = MAX_ALPHA;
        startedAnimation = false;
        terminated = false;
    }

    /**
     * Hook to configure the global paint states before any animation happens.
     *
     * @param paint the paint object that will be used to perform all draw operations.
     */
    protected void configurePaint(Paint paint) {
        paint.setAlpha(alpha);
    }

    /**
     * Update the confetto internal state based on the provided passed time.
     *
     * @param passedTime time since the beginning of the animation.
     * @return whether this particular confetto has terminated.
     */
    public boolean applyUpdate(long passedTime) {
        final long animatedTime = passedTime - initialDelay;
        startedAnimation = animatedTime >= 0;

        if (startedAnimation && !terminated) {
            currentX = computeDistance(animatedTime, initialX, initialVelocityX, accelerationX,
                    millisToReachTargetVelocityX, targetVelocityX);
            currentY = computeDistance(animatedTime, initialY, initialVelocityY, accelerationY,
                    millisToReachTargetVelocityY, targetVelocityY);
            currentRotation = computeDistance(animatedTime, initialRotation,
                    initialRotationalVelocity, rotationalAcceleration,
                    millisToReachTargetRotationalVelocity, targetRotationalVelocity);

            if (fadeOutInterpolator != null) {
                final float interpolatedTime =
                        fadeOutInterpolator.getInterpolation(animatedTime / millisToReachBound);
                alpha = (int) (interpolatedTime * MAX_ALPHA);
            } else {
                alpha = MAX_ALPHA;
            }

            terminated = animatedTime >= millisToReachBound;
        }

        return terminated;
    }

    private float computeDistance(long t, float xi, float vi, float ai, Long targetTime,
            Float vTarget) {
        if (targetTime == null || t < targetTime) {
            // distance covered with linear acceleration
            // distance = xi + vi * t + 1/2 * a * t^2
            return xi + vi * t + 0.5f * ai * t * t;
        } else {
            // distance covered with linear acceleration + distance covered with max velocity
            // distance = xi + vi * targetTime + 1/2 * a * targetTime^2
            //     + (t - targetTime) * vTarget;
            return xi + vi * targetTime + 0.5f * ai * targetTime * targetTime
                    + (t - targetTime) * vTarget;
        }
    }

    /**
     * Primary method for rendering this confetto on the canvas.
     *
     * @param canvas the canvas to draw on.
     */
    public void draw(Canvas canvas) {
        if (startedAnimation && !terminated) {
            matrix.reset();
            workPaint.setAlpha(alpha);
            drawInternal(canvas, matrix, workPaint, currentX, currentY, currentRotation);
        }
    }

    /**
     * Subclasses need to override this method to optimize for the way to draw the appropriate
     * confetto on the canvas.
     *
     * @param canvas the canvas to draw on.
     * @param matrix an identity matrix to use for draw manipulations.
     * @param paint the paint to perform canvas draw operations on. This paint has already been
     *   configured via {@link #configurePaint(Paint)}.
     * @param x the x position of the confetto relative to the canvas.
     * @param y the y position of the confetto relative to the canvas.
     * @param rotation the rotation (in degrees) to draw the confetto.
     */
    protected abstract void drawInternal(Canvas canvas, Matrix matrix, Paint paint, float x,
            float y, float rotation);


    /**
     * Helper methods to set all of the necessary values for the confetto.
     */

    public void setInitialDelay(long val) {
        this.initialDelay = val;
    }

    public void setInitialX(float val) {
        this.initialX = val;
    }

    public void setInitialY(float val) {
        this.initialY = val;
    }

    public void setInitialVelocityX(float val) {
        this.initialVelocityX = val;
    }

    public void setInitialVelocityY(float val) {
        this.initialVelocityY = val;
    }

    public void setAccelerationX(float val) {
        this.accelerationX = val;
    }

    public void setAccelerationY(float val) {
        this.accelerationY = val;
    }

    public void setTargetVelocityX(Float val) {
        this.targetVelocityX = val;
    }

    public void setTargetVelocityY(Float val) {
        this.targetVelocityY = val;
    }

    public void setInitialRotation(float val) {
        this.initialRotation = val;
    }

    public void setInitialRotationalVelocity(float val) {
        this.initialRotationalVelocity = val;
    }

    public void setRotationalAcceleration(float val) {
        this.rotationalAcceleration = val;
    }

    public void setTargetRotationalVelocity(Float val) {
        this.targetRotationalVelocity = val;
    }

    public void setTTL(long val) {
        this.ttl = val;
    }

    public void setFadeOut(Interpolator fadeOutInterpolator) {
        this.fadeOutInterpolator = fadeOutInterpolator;
    }
}
