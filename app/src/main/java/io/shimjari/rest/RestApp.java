package io.shimjari.rest;

import android.app.Application;

public final class RestApp extends Application {
    private RestController controller;
    public synchronized RestController controller() {
        if (controller == null) controller = new RestController(this);
        return controller;
    }
}
