package com.nhancv.facemask.fps;

import java.util.Timer;
import java.util.TimerTask;

public class StableFps {

    private FpsTimerTask fpsTimerTask;
    private Timer timer;
    private int FPS;
    private boolean isStarted;

    public StableFps() {
        this(1);
    }

    public StableFps(int fps) {
        timer = new Timer();
        fpsTimerTask = new FpsTimerTask();
        FPS = fps;
        timer.scheduleAtFixedRate(fpsTimerTask, 0, 1000/FPS);
    }


    public void start(FpsListener fpsListener) {
        isStarted = true;
        fpsTimerTask.setFpsListener(fpsListener);
        fpsTimerTask.run();
    }

    public void stop() {
        isStarted = false;
        fpsTimerTask.cancel();
        timer.cancel();
    }

    public boolean isStarted() {
        return isStarted;
    }

    private class FpsTimerTask extends TimerTask {
        private FpsListener fpsListener;

        public void setFpsListener (FpsListener fpsListener) {
            this.fpsListener = fpsListener;
        }

        @Override
        public void run() {
            if(fpsListener != null) {
                fpsListener.update(FPS);
            }

        }
    }

}
